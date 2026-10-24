package uk.chromis.pos.audit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import uk.chromis.pos.delivery.DeliveryStatusStore;

/**
 * Auditor de salida (pick &amp; pack): before the goods of a sale leave, they
 * are scanned against the lines of the ticket.
 *
 * - One audit per ticket at a time (pick_audit.open_ticket is unique while
 *   it is open). Every scan is saved at once with a guarded UPDATE: a closed
 *   window or a second PC continues where it was, and nothing goes over
 *   what was sold.
 * - Codes: product code, reference, or an extra barcode of the product
 *   (table barcodes; a box barcode counts its sellingqty units).
 * - A missing product can be justified (no stock); the audit closes when
 *   every line is complete or justified.
 * - Closing a delivery that is still "por surtir" marks it as "preparada" in
 *   the delivery manager, with who audited it and what was missing.
 */
public final class PickAuditStore {

    /** Permission (dbpermissions / roles) and Admin menu access name. */
    public static final String PERMISSION = "PickAudit";
    public static final String PERMISSION_NAME = "Auditor de salida";

    public static final String OPEN = "abierta";
    public static final String CLOSED = "cerrada";
    public static final String RESULT_OK = "completo";
    public static final String RESULT_MISSING = "faltantes";

    private static final double EPS = 0.0005;

    private PickAuditStore() {
    }

    /** Refused operation; the message is for the operator. */
    public static final class Refused extends Exception {
        public Refused(String message) {
            super(message);
        }
    }

    /** A sale found by its number, id or pickup code. */
    public static final class Order {
        public String ticketId;
        public int number;
        public Timestamp date;
        public String customer;
        public String address;
        public boolean delivery;
    }

    public static final class Line {
        public String productId;
        public String code;
        public String name;
        public double expected;
        public double scanned;
        public boolean justified;

        public double missing() {
            return Math.max(0, expected - scanned);
        }

        public boolean complete() {
            return scanned >= expected - EPS;
        }

        public boolean ready() {
            return complete() || justified;
        }
    }

    public static final class Audit {
        public int id;
        public Order order;
        public String status;
        public String startedBy;
        public Timestamp started;
        public String closedBy;
        public Timestamp closed;
        public String result;
        public final List<Line> lines = new ArrayList<>();

        public Line line(String productId) {
            for (Line l : lines) {
                if (l.productId.equals(productId)) {
                    return l;
                }
            }
            return null;
        }
    }

    /** What a close did. */
    public static final class Closed {
        public final List<Line> justified = new ArrayList<>();
        /** Message of the delivery manager, null when the sale is not a delivery. */
        public String delivery;
    }

    // ---------------- Tables ----------------

    public static void createTables(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS pick_audit ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "ticket VARCHAR(40) NOT NULL, "
                    + "ticketid INT NOT NULL, "
                    + "open_ticket VARCHAR(40) NULL, "
                    + "status VARCHAR(12) NOT NULL, "
                    + "result VARCHAR(12) NULL, "
                    + "started DATETIME NOT NULL, "
                    + "started_by VARCHAR(40) NULL, "
                    + "started_by_name VARCHAR(50) NULL, "
                    + "closed DATETIME NULL, "
                    + "closed_by VARCHAR(40) NULL, "
                    + "closed_by_name VARCHAR(50) NULL, "
                    + "UNIQUE KEY pick_audit_open (open_ticket), "
                    + "KEY pick_audit_ticket (ticket), "
                    + "KEY pick_audit_started (started))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS pick_audit_lines ("
                    + "audit INT NOT NULL, "
                    + "product VARCHAR(40) NOT NULL, "
                    + "code VARCHAR(60) NULL, "
                    + "name VARCHAR(120) NULL, "
                    + "expected DECIMAL(12,3) NOT NULL, "
                    + "scanned DECIMAL(12,3) NOT NULL DEFAULT 0, "
                    + "justified TINYINT(1) NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (audit, product))");
        }
    }

    /** Adds the permission to dbpermissions so it can be given in roles. A failure is ignored. */
    public static void ensurePermission(Connection con) {
        try {
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM dbpermissions WHERE classname = ?")) {
                ps.setString(1, PERMISSION);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        return;
                    }
                }
            }
            String site = null;
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT guid FROM siteguid")) {
                if (rs.next()) {
                    site = rs.getString(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO dbpermissions "
                    + "(classname, section, displayname, description, rightslevel, userdefined" + (site != null ? ", siteguid" : "")
                    + ") VALUES (?, ?, ?, ?, 1, 1" + (site != null ? ", ?" : "") + ")")) {
                ps.setString(1, PERMISSION);
                ps.setString(2, "##lbl.administration");
                ps.setString(3, PERMISSION_NAME);
                ps.setString(4, "Escanear la mercancía de una venta antes de que salga");
                if (site != null) {
                    ps.setString(5, site);
                }
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            // without the row the permission just cannot be picked in roles
        }
    }

    // ---------------- Orders ----------------

    private static final String ORDER_SQL = "SELECT t.id, t.ticketid, r.datenew, c.name, c.address, c.address2, "
            + "cd.id, cd.name, cd.addressline1, cd.addressline2, cd.addressline3 "
            + "FROM tickets t JOIN receipts r ON r.id = t.id LEFT JOIN customers c ON c.id = t.customer "
            + "LEFT JOIN customer_delivery cd ON cd.id = t.id WHERE t.tickettype = 0 AND ";

    /**
     * The sale with that ticket number, ticket id (the code of the printed
     * ticket) or pickup code. Only sales: a refund with the same number is
     * never taken. Null if there is none.
     */
    public static Order find(Connection con, String identifier) throws SQLException {
        String id = identifier == null ? "" : identifier.trim();
        if (id.isEmpty()) {
            return null;
        }
        if (id.matches("\\d{1,9}")) {
            Order o = order(con, "t.ticketid = ?", Integer.valueOf(id));
            if (o != null) {
                return o;
            }
        }
        Order o = order(con, "t.id = ?", id);
        if (o != null) {
            return o;
        }
        // pickup codes of Chromis versions that keep them in a table
        try (PreparedStatement ps = con.prepareStatement("SELECT ticket FROM pickup WHERE code = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return order(con, "t.id = ?", rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            if (!"42S02".equals(ex.getSQLState())) {
                throw ex;
            }
        }
        return null;
    }

    private static Order order(Connection con, String where, Object value) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(ORDER_SQL + where + " ORDER BY r.datenew DESC LIMIT 1")) {
            ps.setObject(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Order o = new Order();
                o.ticketId = rs.getString(1);
                o.number = rs.getInt(2);
                o.date = rs.getTimestamp(3);
                o.customer = rs.getString(4);
                o.delivery = rs.getString(7) != null;
                if (o.delivery) {
                    if (o.customer == null) {
                        o.customer = rs.getString(8);
                    }
                    o.address = join(rs.getString(9), rs.getString(10), rs.getString(11));
                } else {
                    o.address = join(rs.getString(5), rs.getString(6));
                }
                return o;
            }
        }
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.trim().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(p.trim());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * What has to leave: the products of the sale, minus what was refunded.
     * Services and system lines (delivery charge) are not goods.
     */
    public static List<Line> expected(Connection con, String ticketId) throws SQLException {
        Map<String, Line> lines = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT p.id, p.code, p.name, tl.units - COALESCE(tl.refundqty, 0) "
                + "FROM ticketlines tl JOIN products p ON p.id = tl.product WHERE tl.ticket = ? "
                + "AND COALESCE(p.isservice, 0) = 0 AND COALESCE(p.systemobject, 0) = 0 ORDER BY tl.line")) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double units = rs.getDouble(4);
                    if (units <= EPS) {
                        continue;
                    }
                    Line l = lines.get(rs.getString(1));
                    if (l == null) {
                        l = new Line();
                        l.productId = rs.getString(1);
                        l.code = rs.getString(2) == null || rs.getString(2).isEmpty() ? l.productId : rs.getString(2);
                        l.name = rs.getString(3);
                        lines.put(l.productId, l);
                    }
                    l.expected += units;
                }
            }
        }
        return new ArrayList<>(lines.values());
    }

    // ---------------- Audits ----------------

    /** Last closed audit of the ticket, or null. */
    public static Audit lastClosed(Connection con, String ticketId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM pick_audit WHERE ticket = ? AND status = ? "
                + "ORDER BY closed DESC, id DESC LIMIT 1")) {
            ps.setString(1, ticketId);
            ps.setString(2, CLOSED);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? get(con, rs.getInt(1)) : null;
            }
        }
    }

    /**
     * The open audit of the order (continues where it was) or a new one with
     * the lines of the sale. Refused when the sale has no goods.
     */
    public static Audit open(Connection con, Order order, String userId, String userName) throws SQLException, Refused {
        createTables(con);
        Integer open = openId(con, order.ticketId);
        if (open != null) {
            return get(con, open);
        }
        List<Line> lines = expected(con, order.ticketId);
        if (lines.isEmpty()) {
            throw new Refused("La venta #" + order.number + " no tiene mercancía que surtir.");
        }
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        int id;
        try {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO pick_audit (ticket, ticketid, open_ticket, status, "
                    + "started, started_by, started_by_name) VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, order.ticketId);
                ps.setInt(2, order.number);
                ps.setString(3, order.ticketId);
                ps.setString(4, OPEN);
                ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                ps.setString(6, userId);
                ps.setString(7, cut(userName, 50));
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    id = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO pick_audit_lines (audit, product, code, name, expected) "
                    + "VALUES (?, ?, ?, ?, ?)")) {
                for (Line l : lines) {
                    ps.setInt(1, id);
                    ps.setString(2, l.productId);
                    ps.setString(3, cut(l.code, 60));
                    ps.setString(4, cut(l.name, 120));
                    ps.setDouble(5, l.expected);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            con.commit();
        } catch (SQLException ex) {
            con.rollback();
            // another PC opened it at the same moment: use that one
            Integer other = openId(con, order.ticketId);
            if (other != null) {
                return get(con, other);
            }
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
        return get(con, id);
    }

    /** Id of the open audit of the ticket, or null. */
    public static Integer openId(Connection con, String ticketId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM pick_audit WHERE open_ticket = ?")) {
            ps.setString(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    public static Audit get(Connection con, int id) throws SQLException {
        Audit a = new Audit();
        String ticket;
        try (PreparedStatement ps = con.prepareStatement("SELECT id, ticket, status, started_by_name, started, closed_by_name, "
                + "closed, result FROM pick_audit WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                a.id = rs.getInt(1);
                ticket = rs.getString(2);
                a.status = rs.getString(3);
                a.startedBy = rs.getString(4);
                a.started = rs.getTimestamp(5);
                a.closedBy = rs.getString(6);
                a.closed = rs.getTimestamp(7);
                a.result = rs.getString(8);
            }
        }
        a.order = order(con, "t.id = ?", ticket);
        try (PreparedStatement ps = con.prepareStatement("SELECT product, code, name, expected, scanned, justified "
                + "FROM pick_audit_lines WHERE audit = ? ORDER BY name")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Line l = new Line();
                    l.productId = rs.getString(1);
                    l.code = rs.getString(2);
                    l.name = rs.getString(3);
                    l.expected = rs.getDouble(4);
                    l.scanned = rs.getDouble(5);
                    l.justified = rs.getBoolean(6);
                    a.lines.add(l);
                }
            }
        }
        return a;
    }

    /**
     * Products a scanned code stands for, with the units each scan counts
     * (1, or the sellingqty of an extra barcode such as a box).
     */
    public static Map<String, Double> resolve(Connection con, String code) throws SQLException {
        Map<String, Double> found = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM products WHERE code = ? OR reference = ?")) {
            ps.setString(1, code);
            ps.setString(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found.put(rs.getString(1), 1.0);
                }
            }
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT productid, sellingqty FROM barcodes WHERE barcode = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double q = rs.getDouble(2);
                    found.putIfAbsent(rs.getString(1), q > EPS ? q : 1.0);
                }
            }
        } catch (SQLException ex) {
            if (!"42S02".equals(ex.getSQLState())) {
                throw ex;
            }
        }
        // a product without code is listed by its id
        found.putIfAbsent(code, 1.0);
        return found;
    }

    /**
     * Adds a scan: [units] times the code (null = 1 scan; the units of a
     * box barcode multiply). Refused when the code is not in the order or it
     * would go over what was sold. Returns the line as it is now.
     */
    public static Line scan(Connection con, int auditId, String code, Double units) throws SQLException, Refused {
        String c = code == null ? "" : code.trim();
        if (c.isEmpty()) {
            throw new Refused("Escanea o escribe un código.");
        }
        if (units != null && units <= EPS) {
            throw new Refused("La cantidad debe ser mayor a 0.");
        }
        Audit a = get(con, auditId);
        if (a == null || !OPEN.equals(a.status)) {
            throw new Refused("Esta auditoría ya está cerrada.");
        }
        Line line = null;
        double each = 1;
        for (Map.Entry<String, Double> e : resolve(con, c).entrySet()) {
            Line l = a.line(e.getKey());
            if (l != null) {
                line = l;
                each = e.getValue();
                break;
            }
        }
        if (line == null) {
            throw new Refused("El código " + c + " NO pertenece a este pedido.");
        }
        double add = (units == null ? 1 : units) * each;
        try (PreparedStatement ps = con.prepareStatement("UPDATE pick_audit_lines SET scanned = scanned + ?, justified = 0 "
                + "WHERE audit = ? AND product = ? AND scanned + ? <= expected + ? "
                + "AND EXISTS (SELECT 1 FROM pick_audit WHERE id = ? AND status = ?)")) {
            ps.setDouble(1, add);
            ps.setInt(2, auditId);
            ps.setString(3, line.productId);
            ps.setDouble(4, add);
            ps.setDouble(5, EPS);
            ps.setInt(6, auditId);
            ps.setString(7, OPEN);
            if (ps.executeUpdate() == 0) {
                Line now = get(con, auditId).line(line.productId);
                if (now.complete()) {
                    throw new Refused("Excedente: de " + now.name + " ya se escanearon las " + qty(now.expected) + ".");
                }
                throw new Refused("Excedente: de " + now.name + " solo faltan " + qty(now.missing()) + ".");
            }
        }
        return get(con, auditId).line(line.productId);
    }

    /** Marks (or unmarks) the missing units of a product as justified (no stock). */
    public static Line justify(Connection con, int auditId, String productId, boolean justified) throws SQLException, Refused {
        Audit a = get(con, auditId);
        Line l = a == null ? null : a.line(productId);
        if (l == null || !OPEN.equals(a.status)) {
            throw new Refused("Esta auditoría ya está cerrada.");
        }
        if (justified && l.complete()) {
            throw new Refused(l.name + " ya está completo.");
        }
        try (PreparedStatement ps = con.prepareStatement("UPDATE pick_audit_lines SET justified = ? WHERE audit = ? AND product = ?")) {
            ps.setBoolean(1, justified);
            ps.setInt(2, auditId);
            ps.setString(3, productId);
            ps.executeUpdate();
        }
        return get(con, auditId).line(productId);
    }

    /**
     * Closes the audit when every product is complete or justified (all or
     * nothing). A delivery still "por surtir" becomes "preparada".
     */
    public static Closed close(Connection con, int auditId, String userId, String userName) throws SQLException, Refused {
        Closed result = new Closed();
        Audit a;
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            try (PreparedStatement ps = con.prepareStatement("SELECT status FROM pick_audit WHERE id = ? FOR UPDATE")) {
                ps.setInt(1, auditId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !OPEN.equals(rs.getString(1))) {
                        throw new Refused("Esta auditoría ya está cerrada.");
                    }
                }
            }
            a = get(con, auditId);
            StringBuilder pending = new StringBuilder();
            for (Line l : a.lines) {
                if (!l.ready()) {
                    pending.append("\n- ").append(l.name).append(" (faltan ").append(qty(l.missing())).append(")");
                } else if (!l.complete()) {
                    result.justified.add(l);
                }
            }
            if (pending.length() > 0) {
                throw new Refused("Faltan productos por escanear o justificar:" + pending);
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE pick_audit SET status = ?, open_ticket = NULL, result = ?, "
                    + "closed = ?, closed_by = ?, closed_by_name = ? WHERE id = ?")) {
                ps.setString(1, CLOSED);
                ps.setString(2, result.justified.isEmpty() ? RESULT_OK : RESULT_MISSING);
                ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                ps.setString(4, userId);
                ps.setString(5, cut(userName, 50));
                ps.setInt(6, auditId);
                ps.executeUpdate();
            }
            con.commit();
        } catch (SQLException | Refused | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
        if (a.order != null && a.order.delivery) {
            result.delivery = markReady(con, a, result.justified, userId, userName);
        }
        return result;
    }

    /** Delivery manager: "por surtir" → "preparada" (same operation id if it is repeated). */
    private static String markReady(Connection con, Audit a, List<Line> justified, String userId, String userName) {
        try {
            DeliveryStatusStore.createTables(con);
            DeliveryStatusStore.Entry e = DeliveryStatusStore.get(con, a.order.ticketId);
            if (e == null || !DeliveryStatusStore.PENDING.equals(e.status)) {
                return e == null ? null : "La entrega ya estaba " + DeliveryStatusStore.label(e.status).toLowerCase() + ".";
            }
            DeliveryStatusStore.Change ch = new DeliveryStatusStore.Change();
            ch.opId = "auditoria-" + a.id;
            ch.ticketId = a.order.ticketId;
            ch.status = DeliveryStatusStore.READY;
            ch.role = DeliveryStatusStore.Role.ADMIN;
            ch.userId = userId;
            ch.userName = userName;
            StringBuilder notes = new StringBuilder("Auditada por ").append(userName);
            if (!justified.isEmpty()) {
                ch.issue = "faltante";
                notes.append(". Faltó:");
                for (Line l : justified) {
                    notes.append(' ').append(l.name).append(" (").append(qty(l.missing())).append(");");
                }
            }
            ch.notes = notes.toString();
            DeliveryStatusStore.apply(con, ch);
            return "La entrega quedó como Preparada.";
        } catch (SQLException | DeliveryStatusStore.Refused ex) {
            return "No se pudo marcar la entrega como Preparada: " + ex.getMessage();
        }
    }

    /** Throws away an open audit (what was scanned is lost). */
    public static void discard(Connection con, int auditId) throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM pick_audit WHERE id = ? AND status = ?")) {
                ps.setInt(1, auditId);
                ps.setString(2, OPEN);
                if (ps.executeUpdate() == 1) {
                    try (PreparedStatement del = con.prepareStatement("DELETE FROM pick_audit_lines WHERE audit = ?")) {
                        del.setInt(1, auditId);
                        del.executeUpdate();
                    }
                }
            }
            con.commit();
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    /** Audits started since [from], newest first (open ones too). */
    public static List<Audit> history(Connection con, Timestamp from, int limit) throws SQLException {
        createTables(con);
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM pick_audit WHERE started >= ? ORDER BY started DESC LIMIT ?")) {
            ps.setTimestamp(1, from);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        List<Audit> out = new ArrayList<>();
        for (int id : ids) {
            out.add(get(con, id));
        }
        return out;
    }

    /** 3, 2.5 */
    public static String qty(double q) {
        return Math.abs(q - Math.rint(q)) < EPS ? String.valueOf((long) Math.rint(q))
                : String.valueOf(Math.round(q * 1000) / 1000.0);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
