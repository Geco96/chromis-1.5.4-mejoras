package uk.chromis.pos.restock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import uk.chromis.pos.restock.RestockStore.Status;
import uk.chromis.pos.settings.PosSettings;

/**
 * Daily draft order ("pedido tentativo"): at the configured time one draft
 * per supplier is built with every product that needs restocking, with the
 * same quantities as the restock screen (maximum − stock − on order, at least
 * the shortages of the last order). A person always reviews it and sends it
 * from Chromis Admin; nothing is ever sent on its own.
 *
 * Drafts live in restock_drafts / restock_draft_lines; the original Chromis
 * tables are not changed. A draft becomes a normal supplier order only when
 * it is sent. Drafts not reviewed are replaced by the next day's.
 */
public final class DraftOrders {

    public static final String DRAFT = "BORRADOR";
    public static final String SENT = "ENVIADO";
    public static final String REPLACED = "REEMPLAZADO";
    public static final String DISCARDED = "DESCARTADO";

    /** Settings in pos_settings. */
    public static final String ENABLED = "restock.draft.enabled";
    public static final String TIME = "restock.draft.time";
    /** Terminal that builds the draft; blank means whichever POS checks first. */
    public static final String TERMINAL = "restock.draft.terminal";
    public static final String LAST_RUN = "restock.draft.lastrun";
    public static final String LAST_STATUS = "restock.draft.status";
    public static final String LAST_FAILURE = "restock.draft.lastfailure";

    public static final LocalTime DEFAULT_TIME = LocalTime.of(16, 30);
    private static final long RETRY_AFTER_FAILURE_MINUTES = 30;
    private static final String LOCK = "chromis_restock_draft";
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private DraftOrders() {
    }

    // ---------------- Data ----------------

    /** One product of a draft, with its current stock and thresholds. */
    public static final class Line {

        public String productId;
        public double suggested;
        public double qty;
        public double carry;
        public String carryFrom;
        public double onOrder;
        /** Current stock and thresholds; null if the product no longer manages stock. */
        public RestockStore.Row row;

        public String getName() {
            return row == null ? productId : row.name;
        }

        public String getReference() {
            return row == null ? "" : row.reference;
        }

        public Status getStatus() {
            return row == null ? Status.SIN_UMBRAL : row.status();
        }
    }

    public static final class Draft {

        public String id;
        public String location;
        public String locationName;
        public String supplierId;
        public String supplierName;
        public Timestamp created;
        public String status;
        public final List<Line> lines = new ArrayList<>();

        /** Products that will be ordered (quantity above 0). */
        public int productCount() {
            int n = 0;
            for (Line l : lines) {
                if (l.qty > 0) {
                    n++;
                }
            }
            return n;
        }
    }

    /** What the last generation produced. */
    public static final class Result {

        public int drafts;
        public int products;
        public int replaced;

        public String describe() {
            if (drafts == 0) {
                return "ningún producto necesita pedido";
            }
            return products + (products == 1 ? " producto" : " productos") + " en " + drafts
                    + (drafts == 1 ? " pedido" : " pedidos");
        }
    }

    /** Open drafts, for the notices. */
    public static final class Summary {

        public int drafts;
        public int products;
        public Timestamp latest;
    }

    public static void createTables(Connection con) throws SQLException {
        RestockStore.createTables(con);
        PosSettings.createTable(con);
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS restock_drafts ("
                    + "id VARCHAR(40) NOT NULL PRIMARY KEY, "
                    + "location VARCHAR(40) NOT NULL, "
                    + "supplier VARCHAR(40) NOT NULL, "
                    + "created DATETIME NOT NULL, "
                    + "status VARCHAR(12) NOT NULL, "
                    + "decided DATETIME NULL, "
                    + "decided_by VARCHAR(50) NULL, "
                    + "order_id VARCHAR(40) NULL, "
                    + "ponumber VARCHAR(40) NULL, "
                    + "KEY idx_drafts_status (status, location))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS restock_draft_lines ("
                    + "draft VARCHAR(40) NOT NULL, "
                    + "product VARCHAR(40) NOT NULL, "
                    + "suggested DECIMAL(12,3) NOT NULL, "
                    + "qty DECIMAL(12,3) NOT NULL, "
                    + "carry DECIMAL(12,3) NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (draft, product))");
        }
    }

    // ---------------- Quantities ----------------

    /**
     * Units to order, as the restock screen proposes them: back to the
     * maximum minus what is already on its way, and at least what was missing
     * from the last order.
     */
    public static double quantity(RestockStore.Row row, double onOrder, double carry) {
        double qty = Math.max(row.suggested() - onOrder, 0);
        if (carry > 0) {
            qty = Math.max(qty, carry);
        }
        return qty;
    }

    /** Whether the product goes in the daily draft. */
    public static boolean needsOrder(RestockStore.Row row, double qty, double carry) {
        if (carry > 0) {
            return true;
        }
        Status s = row.status();
        return qty > 0 && (s == Status.AGOTADO || s == Status.BAJO || s == Status.RESERVA);
    }

    // ---------------- Building the drafts ----------------

    /**
     * Builds today's drafts for every location: one per supplier. The drafts
     * of a location that were still waiting for review are replaced.
     */
    public static Result generate(Connection con, String siteGuid) throws SQLException {
        createTables(con);
        if (siteGuid == null) {
            siteGuid = siteGuid(con);
        }
        Map<String, PendingOrders.Carry> carry = PendingOrders.carryover(con);
        Map<String, Double> onOrder = PendingOrders.onOrder(con);
        List<String> locations = new ArrayList<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT id FROM locations ORDER BY name")) {
            while (rs.next()) {
                locations.add(rs.getString(1));
            }
        }
        Result result = new Result();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Map<String, String> supplierCache = new HashMap<>();
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            for (String location : locations) {
                // supplier -> lines
                Map<String, List<Line>> bySupplier = new LinkedHashMap<>();
                for (RestockStore.Row r : RestockStore.load(con, location, null)) {
                    PendingOrders.Carry c = carry.get(r.productId);
                    double carried = c == null ? 0 : c.units;
                    double ordered = onOrder.getOrDefault(r.productId, 0.0);
                    double qty = quantity(r, ordered, carried);
                    if (!needsOrder(r, qty, carried)) {
                        continue;
                    }
                    Line l = new Line();
                    l.productId = r.productId;
                    l.suggested = qty;
                    l.qty = qty;
                    l.carry = carried;
                    String supplier = supplierCache.get(r.productId);
                    if (supplier == null) {
                        supplier = supplierFor(con, r.productId, siteGuid);
                        supplierCache.put(r.productId, supplier);
                    }
                    bySupplier.computeIfAbsent(supplier, k -> new ArrayList<>()).add(l);
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE restock_drafts SET status = ?, decided = ? WHERE location = ? AND status = ?")) {
                    ps.setString(1, REPLACED);
                    ps.setTimestamp(2, now);
                    ps.setString(3, location);
                    ps.setString(4, DRAFT);
                    result.replaced += ps.executeUpdate();
                }
                for (Map.Entry<String, List<Line>> e : bySupplier.entrySet()) {
                    String id = UUID.randomUUID().toString();
                    try (PreparedStatement ps = con.prepareStatement("INSERT INTO restock_drafts "
                            + "(id, location, supplier, created, status) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, id);
                        ps.setString(2, location);
                        ps.setString(3, e.getKey());
                        ps.setTimestamp(4, now);
                        ps.setString(5, DRAFT);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement("INSERT INTO restock_draft_lines "
                            + "(draft, product, suggested, qty, carry) VALUES (?, ?, ?, ?, ?)")) {
                        for (Line l : e.getValue()) {
                            ps.setString(1, id);
                            ps.setString(2, l.productId);
                            ps.setDouble(3, l.suggested);
                            ps.setDouble(4, l.qty);
                            ps.setDouble(5, l.carry);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    result.drafts++;
                    result.products += e.getValue().size();
                }
            }
            con.commit();
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
        return result;
    }

    /**
     * Supplier for a product: one registered for it in Admin (Products →
     * Suppliers; the one it was last ordered from if there are several), else
     * the supplier it was last ordered from, else the generic supplier.
     */
    public static String supplierFor(Connection con, String productId, String siteGuid) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT ps.supplierid FROM productsuppliers ps "
                + "JOIN suppliers s ON s.id = ps.supplierid "
                + "WHERE ps.productid = ? AND (s.accountactive IS NULL OR s.accountactive = 1) "
                + "ORDER BY (SELECT MAX(o.orderdate) FROM supplierorders o JOIN supplierorderlines l ON l.orderid = o.id "
                + "WHERE o.supplierid = ps.supplierid AND l.productid = ps.productid) DESC, s.suppliername LIMIT 1")) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT o.supplierid FROM supplierorderlines l "
                + "JOIN supplierorders o ON o.id = l.orderid JOIN suppliers s ON s.id = o.supplierid "
                + "WHERE l.productid = ? AND o.status <> 'Cancelled' AND (s.accountactive IS NULL OR s.accountactive = 1) "
                + "ORDER BY o.orderdate DESC, o.sequenceid DESC LIMIT 1")) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        return PendingOrders.genericSupplier(con, siteGuid);
    }

    private static String siteGuid(Connection con) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT guid FROM siteguid")) {
            if (!rs.next()) {
                throw new SQLException("La base no tiene siteguid");
            }
            return rs.getString(1);
        }
    }

    // ---------------- Reading and editing ----------------

    /** Drafts waiting for review, with current stock, thresholds and what is on its way. */
    public static List<Draft> loadOpen(Connection con) throws SQLException {
        createTables(con);
        List<Draft> drafts = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT d.id, d.location, COALESCE(lo.name, d.location), "
                + "d.supplier, COALESCE(s.suppliername, d.supplier), d.created, d.status FROM restock_drafts d "
                + "LEFT JOIN locations lo ON lo.id = d.location LEFT JOIN suppliers s ON s.id = d.supplier "
                + "WHERE d.status = ? ORDER BY lo.name, s.suppliername")) {
            ps.setString(1, DRAFT);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Draft d = new Draft();
                    d.id = rs.getString(1);
                    d.location = rs.getString(2);
                    d.locationName = rs.getString(3);
                    d.supplierId = rs.getString(4);
                    d.supplierName = rs.getString(5);
                    d.created = rs.getTimestamp(6);
                    d.status = rs.getString(7);
                    drafts.add(d);
                }
            }
        }
        Map<String, PendingOrders.Carry> carry = PendingOrders.carryover(con);
        Map<String, Double> onOrder = PendingOrders.onOrder(con);
        for (Draft d : drafts) {
            loadLines(con, d, carry, onOrder);
        }
        return drafts;
    }

    private static void loadLines(Connection con, Draft d, Map<String, PendingOrders.Carry> carry,
            Map<String, Double> onOrder) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT product, suggested, qty, carry FROM restock_draft_lines WHERE draft = ?")) {
            ps.setString(1, d.id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Line l = new Line();
                    l.productId = rs.getString(1);
                    l.suggested = rs.getDouble(2);
                    l.qty = rs.getDouble(3);
                    l.carry = rs.getDouble(4);
                    l.onOrder = onOrder.getOrDefault(l.productId, 0.0);
                    PendingOrders.Carry c = carry.get(l.productId);
                    l.carryFrom = c == null ? null : c.describe();
                    d.lines.add(l);
                }
            }
        }
        Map<String, Line> byProduct = new LinkedHashMap<>();
        for (Line l : d.lines) {
            byProduct.put(l.productId, l);
        }
        for (RestockStore.Row r : RestockStore.load(con, d.location, byProduct.keySet())) {
            byProduct.get(r.productId).row = r;
        }
        // A product deleted (or no longer managing stock) since the draft was built is left out
        d.lines.removeIf(l -> l.row == null);
        d.lines.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
    }

    public static boolean isOpen(Connection con, String draftId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT status FROM restock_drafts WHERE id = ?")) {
            ps.setString(1, draftId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && DRAFT.equals(rs.getString(1));
            }
        }
    }

    /** Sets the quantity of a product, adding it to the draft if it was not there. */
    public static void setQuantity(Connection con, String draftId, String productId, double qty) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE restock_draft_lines SET qty = ? WHERE draft = ? AND product = ?")) {
            ps.setDouble(1, Math.max(qty, 0));
            ps.setString(2, draftId);
            ps.setString(3, productId);
            if (ps.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO restock_draft_lines "
                + "(draft, product, suggested, qty, carry) VALUES (?, ?, 0, ?, 0)")) {
            ps.setString(1, draftId);
            ps.setString(2, productId);
            ps.setDouble(3, Math.max(qty, 0));
            ps.executeUpdate();
        }
    }

    public static void removeLine(Connection con, String draftId, String productId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM restock_draft_lines WHERE draft = ? AND product = ?")) {
            ps.setString(1, draftId);
            ps.setString(2, productId);
            ps.executeUpdate();
        }
    }

    /** False if the draft was already sent, discarded or replaced. */
    public static boolean discard(Connection con, String draftId, String user) throws SQLException {
        return decide(con, draftId, DISCARDED, user, null, null);
    }

    /** Records that the draft became the supplier order; false if it was no longer open. */
    public static boolean markSent(Connection con, String draftId, String user, String orderId, String ponumber)
            throws SQLException {
        return decide(con, draftId, SENT, user, orderId, ponumber);
    }

    private static boolean decide(Connection con, String draftId, String status, String user, String orderId,
            String ponumber) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE restock_drafts SET status = ?, decided = ?, "
                + "decided_by = ?, order_id = ?, ponumber = ? WHERE id = ? AND status = ?")) {
            ps.setString(1, status);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.setString(3, user == null ? null : (user.length() > 50 ? user.substring(0, 50) : user));
            ps.setString(4, orderId);
            ps.setString(5, ponumber);
            ps.setString(6, draftId);
            ps.setString(7, DRAFT);
            return ps.executeUpdate() > 0;
        }
    }

    /** Open drafts and products to order in them (products with quantity above 0). */
    public static Summary summary(Connection con) throws SQLException {
        Summary s = new Summary();
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(DISTINCT d.id), "
                + "COUNT(CASE WHEN l.qty > 0 THEN 1 END), MAX(d.created) FROM restock_drafts d "
                + "LEFT JOIN restock_draft_lines l ON l.draft = d.id WHERE d.status = ?")) {
            ps.setString(1, DRAFT);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s.drafts = rs.getInt(1);
                    s.products = rs.getInt(2);
                    s.latest = rs.getTimestamp(3);
                }
            }
        } catch (SQLException ex) {
            if (!"42S02".equals(ex.getSQLState())) {
                throw ex;
            }
        }
        return s;
    }

    // ---------------- Schedule ----------------

    /** "16:30", "9:05"; null when it is not a time of day. */
    public static LocalTime parseTime(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalTime.parse(text.trim(), HOUR);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public static String formatTime(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    /** The most recent moment the draft should have been built: today at the time, or yesterday if it is earlier. */
    public static LocalDateTime lastScheduled(LocalDateTime now, LocalTime at) {
        LocalDateTime today = now.toLocalDate().atTime(at);
        return now.isBefore(today) ? today.minusDays(1) : today;
    }

    /**
     * Whether the draft has to be built now. A terminal that was off at the
     * time builds it when it starts. Never built before: not due, the first
     * one is at the next scheduled time.
     */
    public static boolean isDue(LocalDateTime lastRun, LocalDateTime now, LocalTime at) {
        return lastRun != null && lastRun.isBefore(lastScheduled(now, at));
    }

    /**
     * Called every minute by each POS: builds the drafts if it is time and
     * this terminal is the one that builds them. Returns what was built, or
     * null if nothing was done.
     */
    public static Result runIfDue(Connection con, String terminal, LocalDateTime now) throws SQLException {
        if (!"1".equals(PosSettings.get(con, ENABLED, "1"))) {
            return null;
        }
        String designated = PosSettings.get(con, TERMINAL, "");
        if (!designated.trim().isEmpty() && !designated.trim().equalsIgnoreCase(terminal)) {
            return null;
        }
        LocalTime at = parseTime(PosSettings.get(con, TIME, null));
        if (at == null) {
            at = DEFAULT_TIME;
        }
        LocalDateTime lastRun = parseDateTime(PosSettings.get(con, LAST_RUN, null));
        if (lastRun == null) {
            // First time: start counting from now, so installing it does not build a draft at once
            PosSettings.put(con, LAST_RUN, now.toString());
            return null;
        }
        if (!isDue(lastRun, now, at)) {
            return null;
        }
        LocalDateTime failure = parseDateTime(PosSettings.get(con, LAST_FAILURE, null));
        if (failure != null && failure.plusMinutes(RETRY_AFTER_FAILURE_MINUTES).isAfter(now)) {
            return null;
        }
        // Several terminals check at the same time: only one builds the drafts
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT GET_LOCK('" + LOCK + "', 0)")) {
            if (!rs.next() || rs.getInt(1) != 1) {
                return null;
            }
        }
        try {
            lastRun = parseDateTime(PosSettings.get(con, LAST_RUN, null));
            if (!isDue(lastRun, now, at)) {
                return null; // another terminal just built them
            }
            Result result;
            try {
                result = generate(con, null);
            } catch (SQLException | RuntimeException ex) {
                PosSettings.put(con, LAST_FAILURE, now.toString());
                PosSettings.put(con, LAST_STATUS, cut(SHORT.format(now) + " · Error: " + ex.getMessage(), 255));
                throw ex;
            }
            PosSettings.put(con, LAST_RUN, now.toString());
            PosSettings.put(con, LAST_FAILURE, "");
            PosSettings.put(con, LAST_STATUS, cut(SHORT.format(now) + " · " + terminal + " · " + result.describe(), 255));
            return result;
        } finally {
            try (Statement st = con.createStatement()) {
                st.execute("DO RELEASE_LOCK('" + LOCK + "')");
            }
        }
    }

    private static LocalDateTime parseDateTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
