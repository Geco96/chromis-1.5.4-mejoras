package uk.chromis.pos.delivery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Delivery manager: the state of each delivery sale (Chromis' customer_delivery,
 * same id as the ticket) and what the driver confirmed.
 *
 * <pre>
 * Por surtir → Preparada → En ruta → Completada
 *                                  → No entregada → Reprogramar (Por surtir) / Cancelada
 * </pre>
 *
 * Every change is one transaction, all or nothing: the status, the confirmation
 * (amount received, issue, notes), customer_delivery.delivered and a row in
 * delivery_events. Each change carries an operation id made by whoever sends
 * it (the phone makes it before sending); applying the same id again does
 * nothing, so a phone that resends after losing the signal never applies a
 * confirmation twice.
 */
public final class DeliveryStatusStore {

    public static final String PENDING = "confirmado";
    public static final String READY = "preparado";
    public static final String ON_ROUTE = "en_ruta";
    public static final String DONE = "completado";
    public static final String FAILED = "no_entregado";
    public static final String CANCELLED = "cancelado";
    // Arrays.asList (no List.of): this class is also used by Chromis for Android 8+
    public static final List<String> STATUSES = Collections.unmodifiableList(Arrays.asList(PENDING, READY, ON_ROUTE, DONE, FAILED, CANCELLED));

    /** Issue reported with the delivery. */
    public static final String ISSUE_NONE = "ninguno";
    public static final List<String> ISSUES = Collections.unmodifiableList(Arrays.asList(ISSUE_NONE, "faltante", "rechazo", "danado", "otro"));

    public enum Role {
        ADMIN, DISPATCHER, DRIVER
    }

    /** current status → new status → who can do it (the driver only on his own deliveries). */
    private static final Map<String, Map<String, Set<Role>>> TRANSITIONS = new LinkedHashMap<>();

    static {
        Set<Role> office = EnumSet.of(Role.ADMIN, Role.DISPATCHER);
        Set<Role> anyone = EnumSet.allOf(Role.class);
        rule(PENDING, READY, office);
        rule(PENDING, CANCELLED, office);
        rule(READY, ON_ROUTE, office);
        rule(READY, PENDING, office);
        rule(ON_ROUTE, DONE, anyone);
        rule(ON_ROUTE, FAILED, anyone);
        rule(ON_ROUTE, READY, office);
        rule(FAILED, PENDING, office);
        rule(FAILED, CANCELLED, office);
        rule(DONE, ON_ROUTE, EnumSet.of(Role.ADMIN));
    }

    private static void rule(String from, String to, Set<Role> roles) {
        TRANSITIONS.computeIfAbsent(from, k -> new LinkedHashMap<>()).put(to, roles);
    }

    private DeliveryStatusStore() {
    }

    // ---------------- Data ----------------

    /** One delivery sale with its location, zone, driver and confirmation. */
    public static final class Entry {

        public String ticketId;
        public int ticketNumber;
        public Timestamp saleDate;
        public Timestamp deliveryDate;
        public String customerId;
        public String customerName;
        public String receiver;
        public String address;
        public String phone;
        public String instructions;
        public double total;
        public String status;
        public Double amountReceived;
        public String issue;
        public String notes;
        public Timestamp confirmedAt;
        public String confirmedBy;
        public Double lat;
        public Double lng;
        public ZoneStore.Zone zone;
        /** Driver chosen for this delivery only (else the zone's). */
        public String driverOverride;

        public String driver() {
            return driverOverride != null ? driverOverride : zone == null ? null : zone.driver;
        }
    }

    /** A change sent by Admin or by the driver's phone. */
    public static final class Change {

        /** Made by the sender once per change (a UUID); resending it is harmless. */
        public String opId;
        public String ticketId;
        public String status;
        public Double amountReceived;
        public String issue = ISSUE_NONE;
        public String notes;
        public Role role;
        /** people.id of who makes the change. */
        public String userId;
        public String userName;
    }

    public enum Outcome {
        APPLIED, ALREADY_APPLIED
    }

    /** A change that was refused, with the reason for the user. */
    public static final class Refused extends Exception {

        public Refused(String message) {
            super(message);
        }
    }

    public static void createTables(Connection con) throws SQLException {
        GeoStore.createTable(con);
        ZoneStore.createTable(con);
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS delivery_status ("
                    + "ticket VARCHAR(40) NOT NULL PRIMARY KEY, "
                    + "status VARCHAR(15) NOT NULL, "
                    + "driver VARCHAR(40) NULL, "
                    + "amount_received DECIMAL(12,2) NULL, "
                    + "issue VARCHAR(15) NULL, "
                    + "notes VARCHAR(500) NULL, "
                    + "confirmed_at DATETIME NULL, "
                    + "confirmed_by VARCHAR(50) NULL, "
                    + "updated DATETIME NOT NULL, "
                    + "updated_by VARCHAR(50) NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS delivery_events ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "op_id VARCHAR(40) NOT NULL, "
                    + "ticket VARCHAR(40) NOT NULL, "
                    + "from_status VARCHAR(15) NOT NULL, "
                    + "to_status VARCHAR(15) NOT NULL, "
                    + "amount_received DECIMAL(12,2) NULL, "
                    + "issue VARCHAR(15) NULL, "
                    + "notes VARCHAR(500) NULL, "
                    + "user_id VARCHAR(40) NULL, "
                    + "user_name VARCHAR(50) NULL, "
                    + "role VARCHAR(12) NOT NULL, "
                    + "at DATETIME NOT NULL, "
                    + "UNIQUE KEY uq_delivery_op (op_id), KEY idx_delivery_events_ticket (ticket))");
        }
    }

    /** Marked delivered in Chromis (Admin → Entregas) counts as completed whatever the stored status says. */
    static String status(String stored, boolean delivered) {
        if (delivered && !CANCELLED.equals(stored)) {
            return DONE;
        }
        return stored != null ? stored : PENDING;
    }

    /** Statuses the role can move a delivery to (for the driver, only if it is his). */
    public static List<String> allowed(Role role, String userId, Entry e) {
        if (role == Role.DRIVER && (userId == null || !userId.equals(e.driver()))) {
            return Collections.emptyList();
        }
        if (role == Role.DISPATCHER && e.zone != null && e.zone.dispatcher != null && !e.zone.dispatcher.equals(userId)) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, Set<Role>> t : TRANSITIONS.getOrDefault(e.status, Collections.emptyMap()).entrySet()) {
            if (t.getValue().contains(role)) {
                list.add(t.getKey());
            }
        }
        return list;
    }

    /** status() in SQL. */
    private static final String STATUS_SQL = "IF(cd.delivered = 1 AND COALESCE(s.status, '') <> 'cancelado', 'completado', "
            + "COALESCE(s.status, 'confirmado'))";

    private static final String SELECT = "SELECT cd.id, t.ticketid, r.datenew, cd.deliverydate, t.customer, c.name, "
            + "cd.name, cd.addressline1, cd.addressline2, cd.addressline3, cd.postcode, cd.phone, cd.comments, "
            + "(SELECT COALESCE(SUM(p.total), 0) FROM payments p WHERE p.receipt = r.id), "
            + "s.status, cd.delivered, s.amount_received, s.issue, s.notes, s.confirmed_at, s.confirmed_by, s.driver, "
            + "g.lat, g.lng "
            + "FROM customer_delivery cd JOIN tickets t ON t.id = cd.id JOIN receipts r ON r.id = t.id "
            + "LEFT JOIN customers c ON c.id = t.customer "
            + "LEFT JOIN delivery_status s ON s.ticket = cd.id "
            + "LEFT JOIN customer_geo g ON g.customer = t.customer "
            + "WHERE t.tickettype = 0 ";

    /**
     * Open deliveries (pending, ready, on route, failed) and the ones closed
     * in the last closedDays days (0 = none).
     */
    public static List<Entry> load(Connection con, int closedDays) throws SQLException {
        createTables(con);
        List<ZoneStore.Zone> zones = ZoneStore.load(con, false);
        String sql = SELECT + "AND (" + STATUS_SQL + " "
                + "IN ('confirmado', 'preparado', 'en_ruta', 'no_entregado')"
                + (closedDays > 0 ? " OR COALESCE(s.updated, r.datenew) >= ?" : "") + ") "
                + "ORDER BY cd.deliverydate, t.ticketid";
        List<Entry> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (closedDays > 0) {
                ps.setTimestamp(1, new Timestamp(System.currentTimeMillis() - closedDays * 86_400_000L));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(read(rs, zones));
                }
            }
        }
        return list;
    }

    /**
     * Deliveries of one driver (his zone's or assigned to him only): the ones
     * on route, the ones still being prepared (to know what comes next) and
     * the ones he closed today. On route first, then the rest.
     */
    public static List<Entry> forDriver(Connection con, String driverId) throws SQLException {
        List<Entry> mine = new ArrayList<>();
        long today = startOfToday();
        for (Entry e : load(con, 1)) {
            if (driverId == null || !driverId.equals(e.driver())) {
                continue;
            }
            boolean closed = DONE.equals(e.status) || FAILED.equals(e.status) || CANCELLED.equals(e.status);
            if (closed && (e.confirmedAt == null || e.confirmedAt.getTime() < today) && !FAILED.equals(e.status)) {
                continue;
            }
            mine.add(e);
        }
        mine.sort((a, b) -> Integer.compare(order(a.status), order(b.status)));
        return mine;
    }

    private static int order(String status) {
        switch (status) {
            case ON_ROUTE:
                return 0;
            case READY:
                return 1;
            case PENDING:
                return 2;
            case FAILED:
                return 3;
            default:
                return 4;
        }
    }

    private static long startOfToday() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** One delivery, or null if the ticket is not a delivery sale. */
    public static Entry get(Connection con, String ticketId) throws SQLException {
        createTables(con);
        return find(con, ticketId);
    }

    /** Like get() but without creating tables: inside a transaction any DDL would commit it halfway. */
    private static Entry find(Connection con, String ticketId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SELECT + "AND cd.id = ?")) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs, ZoneStore.load(con, false)) : null;
            }
        }
    }

    private static Entry read(ResultSet rs, List<ZoneStore.Zone> zones) throws SQLException {
        Entry e = new Entry();
        e.ticketId = rs.getString(1);
        e.ticketNumber = rs.getInt(2);
        e.saleDate = rs.getTimestamp(3);
        e.deliveryDate = rs.getTimestamp(4);
        e.customerId = rs.getString(5);
        e.customerName = rs.getString(6);
        e.receiver = rs.getString(7);
        StringBuilder address = new StringBuilder();
        for (int i = 8; i <= 11; i++) {
            String part = rs.getString(i);
            if (part != null && !part.trim().isEmpty()) {
                address.append(address.length() == 0 ? "" : ", ").append(part.trim());
            }
        }
        e.address = address.toString();
        e.phone = rs.getString(12);
        e.instructions = rs.getString(13);
        e.total = rs.getDouble(14);
        e.status = status(rs.getString(15), rs.getBoolean(16));
        e.amountReceived = rs.getObject(17) == null ? null : rs.getDouble(17);
        e.issue = rs.getString(18);
        e.notes = rs.getString(19);
        e.confirmedAt = rs.getTimestamp(20);
        e.confirmedBy = rs.getString(21);
        e.driverOverride = rs.getString(22);
        e.lat = rs.getObject(23) == null ? null : rs.getDouble(23);
        e.lng = rs.getObject(24) == null ? null : rs.getDouble(24);
        e.zone = ZoneStore.zoneFor(zones, e.lat, e.lng);
        return e;
    }

    // ---------------- Changes ----------------

    /**
     * Applies a change in one transaction. ALREADY_APPLIED means that operation
     * id was applied before (the sender can treat it as done).
     */
    public static Outcome apply(Connection con, Change c) throws SQLException, Refused {
        if (c.opId == null || c.opId.trim().isEmpty() || c.opId.length() > 40) {
            throw new Refused("Falta el identificador de la operación.");
        }
        if (!STATUSES.contains(c.status)) {
            throw new Refused("Estado no válido: " + c.status);
        }
        String issue = c.issue == null || c.issue.isEmpty() ? ISSUE_NONE : c.issue;
        if (!ISSUES.contains(issue)) {
            throw new Refused("Incidencia no válida: " + issue);
        }
        String notes = c.notes == null || c.notes.trim().isEmpty() ? null : c.notes.trim();
        if (notes != null && notes.length() > 500) {
            notes = notes.substring(0, 500);
        }
        createTables(con);
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            // Lock the delivery first, so two changes at the same time are done one after the
            // other and everything below (also the operation check) sees what the other one saved
            boolean delivered;
            try (PreparedStatement ps = con.prepareStatement("SELECT delivered FROM customer_delivery WHERE id = ? FOR UPDATE")) {
                ps.setString(1, c.ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new Refused("Esa venta no es una entrega.");
                    }
                    delivered = rs.getBoolean(1);
                }
            }
            String stored = null;
            try (PreparedStatement ps = con.prepareStatement("SELECT status FROM delivery_status WHERE ticket = ? FOR UPDATE")) {
                ps.setString(1, c.ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stored = rs.getString(1);
                    }
                }
            }
            if (exists(con, c.opId)) {
                con.commit();
                return Outcome.ALREADY_APPLIED;
            }
            Entry e = find(con, c.ticketId);
            e.status = status(stored, delivered);
            if (!allowed(c.role, c.userId, e).contains(c.status)) {
                throw new Refused(c.role == Role.DRIVER && !String.valueOf(c.userId).equals(e.driver())
                        ? "Esta entrega no es tuya."
                        : "No se puede pasar de '" + label(e.status) + "' a '" + label(c.status) + "'.");
            }
            if (DONE.equals(c.status)) {
                if (c.amountReceived == null || c.amountReceived < 0) {
                    throw new Refused("Escribe cuánto se recibió (0 si no se cobró).");
                }
            }
            if (FAILED.equals(c.status) && notes == null) {
                throw new Refused("Escribe por qué no se entregó.");
            }
            if (!ISSUE_NONE.equals(issue) && notes == null) {
                throw new Refused("Describe la incidencia en las anotaciones.");
            }
            boolean confirmation = DONE.equals(c.status) || FAILED.equals(c.status);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO delivery_status "
                    + "(ticket, status, amount_received, issue, notes, confirmed_at, confirmed_by, updated, updated_by) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE status = VALUES(status), "
                    + "amount_received = VALUES(amount_received), issue = VALUES(issue), notes = VALUES(notes), "
                    + "confirmed_at = VALUES(confirmed_at), confirmed_by = VALUES(confirmed_by), "
                    + "updated = VALUES(updated), updated_by = VALUES(updated_by)")) {
                ps.setString(1, c.ticketId);
                ps.setString(2, c.status);
                ps.setObject(3, DONE.equals(c.status) ? c.amountReceived : null);
                ps.setString(4, confirmation || CANCELLED.equals(c.status) ? issue : null);
                ps.setString(5, confirmation || CANCELLED.equals(c.status) ? notes : null);
                ps.setTimestamp(6, confirmation ? now : null);
                ps.setString(7, confirmation ? cut(c.userName, 50) : null);
                ps.setTimestamp(8, now);
                ps.setString(9, cut(c.userName, 50));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE customer_delivery SET delivered = ? WHERE id = ?")) {
                ps.setBoolean(1, DONE.equals(c.status));
                ps.setString(2, c.ticketId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO delivery_events (op_id, ticket, from_status, "
                    + "to_status, amount_received, issue, notes, user_id, user_name, role, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, c.opId);
                ps.setString(2, c.ticketId);
                ps.setString(3, e.status);
                ps.setString(4, c.status);
                ps.setObject(5, DONE.equals(c.status) ? c.amountReceived : null);
                ps.setString(6, issue);
                ps.setString(7, notes);
                ps.setString(8, c.userId);
                ps.setString(9, cut(c.userName, 50));
                ps.setString(10, c.role.name());
                ps.setTimestamp(11, now);
                ps.executeUpdate();
            }
            con.commit();
            return Outcome.APPLIED;
        } catch (SQLException ex) {
            con.rollback();
            // The same operation arrived twice at the same time: the other one applied it
            // (duplicate key 1062; every MySQL / MariaDB driver reports it this way)
            if ((ex instanceof SQLIntegrityConstraintViolationException || ex.getErrorCode() == 1062) && exists(con, c.opId)) {
                return Outcome.ALREADY_APPLIED;
            }
            throw ex;
        } catch (Refused | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    /** Driver for this delivery only (null = the zone's driver). */
    public static void setDriver(Connection con, String ticketId, String driverId, String userName) throws SQLException {
        createTables(con);
        Entry e = get(con, ticketId);
        if (e == null) {
            throw new SQLException("Esa venta no es una entrega.");
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO delivery_status (ticket, status, driver, updated, updated_by) "
                + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE driver = VALUES(driver), updated = VALUES(updated), "
                + "updated_by = VALUES(updated_by)")) {
            ps.setString(1, ticketId);
            ps.setString(2, e.status);
            ps.setString(3, driverId == null || driverId.isEmpty() ? null : driverId);
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.setString(5, cut(userName, 50));
            ps.executeUpdate();
        }
    }

    /** History of a delivery, oldest first: {at, from, to, user, amount, issue, notes}. */
    public static List<String[]> history(Connection con, String ticketId) throws SQLException {
        createTables(con);
        List<String[]> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT at, from_status, to_status, user_name, amount_received, "
                + "issue, notes FROM delivery_events WHERE ticket = ? ORDER BY id")) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7)});
                }
            }
        }
        return list;
    }

    public static String label(String status) {
        switch (status == null ? "" : status) {
            case PENDING:
                return "Por surtir";
            case READY:
                return "Preparada";
            case ON_ROUTE:
                return "En ruta";
            case DONE:
                return "Completada";
            case FAILED:
                return "No entregada";
            case CANCELLED:
                return "Cancelada";
            default:
                return String.valueOf(status);
        }
    }

    /** Current read (not the transaction snapshot): sees an operation another connection just committed. */
    private static boolean exists(Connection con, String opId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM delivery_events WHERE op_id = ? LOCK IN SHARE MODE")) {
            ps.setString(1, opId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
