package uk.chromis.administration.stockcount;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import uk.chromis.pos.settings.PosSettings;

/**
 * Scheduled stock counts: a plan has a date and a list of products; each count
 * keeps the stock the system had when it was typed, so sales made during the
 * count are not lost when the plan is closed and the stock adjusted.
 */
public final class StockCountStore {

    public static final String OPEN = "PROGRAMADO";
    public static final String CLOSED = "CERRADO";
    public static final String CANCELLED = "CANCELADO";

    public static final String ROTATION_ENABLED = "stockcount.rotation.enabled";
    public static final String ROTATION_SIZE = "stockcount.rotation.size";
    public static final String ROTATION_DAYS = "stockcount.rotation.days";
    public static final String ROTATION_NEXT = "stockcount.rotation.next";
    public static final String CUSTOM_REPORT = "stockcount.report.custom";

    /** Stock diary reasons used by Chromis for stock changes. */
    static final int REASON_IN = 7;
    static final int REASON_OUT = -7;

    private static final String STOCK =
            "(select coalesce(sum(s.units), 0) from stockcurrent s where s.product = p.id and s.location = ?)";
    private static final String COST =
            "(case when coalesce(p.averagecost, 0) = 0 then p.pricebuy else p.averagecost end)";

    public static final class Plan {
        public int id;
        public String name;
        public LocalDate date;
        public String location;
        public String status;
        public boolean auto;
        public String createdBy;
        public Timestamp closedAt;
        public String closedBy;
        public int products;
        public int counted;

        public boolean isOpen() {
            return OPEN.equals(status);
        }
    }

    public static final class Line {
        public String productId;
        public String reference;
        public String code;
        public String name;
        public String category;
        public double current;
        public Double counted;
        public Double systemAtCount;
        public Timestamp countedAt;
        public Double adjusted;
        public double cost;

        /** System stock the count is compared with. */
        public double system() {
            return systemAtCount != null ? systemAtCount : current;
        }

        public Double difference() {
            return counted == null ? null : counted - system();
        }
    }

    public static final class Rotation {
        public boolean enabled;
        public int size = 100;
        public int days = 7;
        public LocalDate next;
    }

    private StockCountStore() {
    }

    public static void createTables(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS stockcount_plans ("
                    + "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(120) NOT NULL, "
                    + "scheduled DATE NOT NULL, location VARCHAR(40) NOT NULL, status VARCHAR(20) NOT NULL, "
                    + "auto TINYINT(1) NOT NULL DEFAULT 0, created_by VARCHAR(255), "
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "closed_at DATETIME NULL, closed_by VARCHAR(255))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS stockcount_lines ("
                    + "plan_id INT NOT NULL, product VARCHAR(40) NOT NULL, counted DECIMAL(12,3) NULL, "
                    + "system_qty DECIMAL(12,3) NULL, counted_at DATETIME NULL, adjusted DECIMAL(12,3) NULL, "
                    + "PRIMARY KEY (plan_id, product))");
        }
        PosSettings.createTable(con);
    }

    // ---------------- Plans ----------------
    public static List<Plan> listPlans(Connection con) throws SQLException {
        List<Plan> plans = new ArrayList<>();
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT p.id, p.name, p.scheduled, p.location, p.status, p.auto, "
                        + "p.created_by, p.closed_at, p.closed_by, "
                        + "(SELECT COUNT(*) FROM stockcount_lines l WHERE l.plan_id = p.id), "
                        + "(SELECT COUNT(*) FROM stockcount_lines l WHERE l.plan_id = p.id AND l.counted IS NOT NULL) "
                        + "FROM stockcount_plans p "
                        + "ORDER BY CASE p.status WHEN 'PROGRAMADO' THEN 0 ELSE 1 END, p.scheduled DESC, p.id DESC")) {
            while (rs.next()) {
                Plan p = new Plan();
                p.id = rs.getInt(1);
                p.name = rs.getString(2);
                p.date = rs.getDate(3).toLocalDate();
                p.location = rs.getString(4);
                p.status = rs.getString(5);
                p.auto = rs.getBoolean(6);
                p.createdBy = rs.getString(7);
                p.closedAt = rs.getTimestamp(8);
                p.closedBy = rs.getString(9);
                p.products = rs.getInt(10);
                p.counted = rs.getInt(11);
                plans.add(p);
            }
        }
        return plans;
    }

    public static int createPlan(Connection con, String name, LocalDate date, String location, String user, boolean auto)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO stockcount_plans "
                + "(name, scheduled, location, status, auto, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setDate(2, Date.valueOf(date));
            ps.setString(3, location);
            ps.setString(4, OPEN);
            ps.setBoolean(5, auto);
            ps.setString(6, user);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static void updatePlan(Connection con, int planId, String name, LocalDate date) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE stockcount_plans SET name = ?, scheduled = ? WHERE id = ? AND status = ?")) {
            ps.setString(1, name);
            ps.setDate(2, Date.valueOf(date));
            ps.setInt(3, planId);
            ps.setString(4, OPEN);
            ps.executeUpdate();
        }
    }

    public static void cancelPlan(Connection con, int planId, String user) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE stockcount_plans SET status = ?, closed_at = NOW(), "
                + "closed_by = ? WHERE id = ? AND status = ?")) {
            ps.setString(1, CANCELLED);
            ps.setString(2, user);
            ps.setInt(3, planId);
            ps.setString(4, OPEN);
            ps.executeUpdate();
        }
    }

    public static int addProducts(Connection con, int planId, Collection<String> productIds) throws SQLException {
        int added = 0;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT IGNORE INTO stockcount_lines (plan_id, product) VALUES (?, ?)")) {
            for (String id : productIds) {
                ps.setInt(1, planId);
                ps.setString(2, id);
                added += ps.executeUpdate();
            }
        }
        return added;
    }

    public static void removeProduct(Connection con, int planId, String productId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "DELETE FROM stockcount_lines WHERE plan_id = ? AND product = ?")) {
            ps.setInt(1, planId);
            ps.setString(2, productId);
            ps.executeUpdate();
        }
    }

    // ---------------- Lines ----------------
    public static List<Line> lines(Connection con, int planId, String location) throws SQLException {
        List<Line> lines = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT p.id, p.reference, p.code, p.name, c.name, "
                + STOCK + ", l.counted, l.system_qty, l.counted_at, l.adjusted, " + COST + " "
                + "FROM stockcount_lines l JOIN products p ON p.id = l.product "
                + "LEFT JOIN categories c ON c.id = p.category "
                + "WHERE l.plan_id = ? ORDER BY c.name, p.name")) {
            ps.setString(1, location);
            ps.setInt(2, planId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Line l = new Line();
                    l.productId = rs.getString(1);
                    l.reference = rs.getString(2);
                    l.code = rs.getString(3);
                    l.name = rs.getString(4);
                    l.category = rs.getString(5);
                    l.current = rs.getDouble(6);
                    l.counted = nullableDouble(rs, 7);
                    l.systemAtCount = nullableDouble(rs, 8);
                    l.countedAt = rs.getTimestamp(9);
                    l.adjusted = nullableDouble(rs, 10);
                    l.cost = rs.getDouble(11);
                    lines.add(l);
                }
            }
        }
        return lines;
    }

    private static Double nullableDouble(ResultSet rs, int col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    /**
     * Saves a count (null clears it). The stock the system has right now is
     * kept with it: the adjustment is counted minus that, so sales made later
     * still count.
     */
    public static void setCount(Connection con, int planId, String productId, Double counted, String location)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE stockcount_lines l "
                + "SET l.counted = ?, l.counted_at = CASE WHEN ? IS NULL THEN NULL ELSE NOW() END, "
                + "l.system_qty = CASE WHEN ? IS NULL THEN NULL ELSE (SELECT coalesce(sum(s.units), 0) "
                + "FROM stockcurrent s WHERE s.product = l.product AND s.location = ?) END "
                + "WHERE l.plan_id = ? AND l.product = ?")) {
            for (int i = 1; i <= 3; i++) {
                if (counted == null) {
                    ps.setNull(i, Types.DECIMAL);
                } else {
                    ps.setDouble(i, counted);
                }
            }
            ps.setString(4, location);
            ps.setInt(5, planId);
            ps.setString(6, productId);
            ps.executeUpdate();
        }
    }

    /** Product id for a barcode, code or reference; null if none. */
    public static String findProduct(Connection con, String code) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM products WHERE code = ? OR reference = ? "
                + "UNION SELECT productid FROM barcodes WHERE barcode = ? LIMIT 1")) {
            ps.setString(1, code);
            ps.setString(2, code);
            ps.setString(3, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /**
     * Applies the counts: for every counted product the stock moves by
     * (counted - system stock when counted), with a stock diary entry. Products
     * not counted are left as they are. Returns how many were adjusted.
     */
    public static int closePlan(Connection con, int planId, String location, String siteGuid, String user)
            throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            List<Line> lines = lines(con, planId, location);
            int adjusted = 0;
            for (Line l : lines) {
                if (l.counted == null) {
                    continue;
                }
                double delta = Math.round((l.counted - l.system()) * 1000.0) / 1000.0;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE stockcount_lines SET adjusted = ? WHERE plan_id = ? AND product = ?")) {
                    ps.setDouble(1, delta);
                    ps.setInt(2, planId);
                    ps.setString(3, l.productId);
                    ps.executeUpdate();
                }
                if (delta == 0) {
                    continue;
                }
                int updated;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE stockcurrent SET units = units + ? WHERE product = ? AND location = ?")) {
                    ps.setDouble(1, delta);
                    ps.setString(2, l.productId);
                    ps.setString(3, location);
                    updated = ps.executeUpdate();
                }
                if (updated == 0) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO stockcurrent (location, product, units, siteguid) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, location);
                        ps.setString(2, l.productId);
                        ps.setDouble(3, delta);
                        ps.setString(4, siteGuid);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO stockdiary (datenew, reason, location, "
                        + "product, units, cost, price, appuser, siteguid, reference) VALUES (NOW(), ?, ?, ?, ?, ?, 0, ?, ?, ?)")) {
                    ps.setInt(1, delta > 0 ? REASON_IN : REASON_OUT);
                    ps.setString(2, location);
                    ps.setString(3, l.productId);
                    ps.setDouble(4, delta);
                    ps.setDouble(5, l.cost);
                    ps.setString(6, user);
                    ps.setString(7, siteGuid);
                    ps.setString(8, "Conteo #" + planId);
                    ps.executeUpdate();
                }
                adjusted++;
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE stockcount_plans SET status = ?, "
                    + "closed_at = NOW(), closed_by = ? WHERE id = ? AND status = ?")) {
                ps.setString(1, CLOSED);
                ps.setString(2, user);
                ps.setInt(3, planId);
                ps.setString(4, OPEN);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("El conteo ya no está abierto (¿lo cerró otra persona?).");
                }
            }
            con.commit();
            return adjusted;
        } catch (SQLException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    // ---------------- Rotation ----------------
    public static Rotation loadRotation(Connection con) throws SQLException {
        Rotation r = new Rotation();
        r.enabled = "1".equals(PosSettings.get(con, ROTATION_ENABLED, "0"));
        r.size = parseInt(PosSettings.get(con, ROTATION_SIZE, "100"), 100);
        r.days = parseInt(PosSettings.get(con, ROTATION_DAYS, "7"), 7);
        String next = PosSettings.get(con, ROTATION_NEXT, null);
        try {
            r.next = next == null ? null : LocalDate.parse(next);
        } catch (RuntimeException ex) {
            r.next = null;
        }
        return r;
    }

    public static void saveRotation(Connection con, Rotation r) throws SQLException {
        PosSettings.put(con, ROTATION_ENABLED, r.enabled ? "1" : "0");
        PosSettings.put(con, ROTATION_SIZE, String.valueOf(r.size));
        PosSettings.put(con, ROTATION_DAYS, String.valueOf(r.days));
        PosSettings.put(con, ROTATION_NEXT, r.next == null ? "" : r.next.toString());
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException ex) {
            return def;
        }
    }

    /**
     * Products with "manage stock" that were counted the longest time ago
     * (never counted first), leaving out those already in an open plan.
     */
    public static List<String> leastRecentlyCounted(Connection con, int limit) throws SQLException {
        List<String> ids = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT p.id FROM products p "
                + "LEFT JOIN (SELECT l.product, MAX(l.counted_at) AS last FROM stockcount_lines l "
                + "JOIN stockcount_plans pl ON pl.id = l.plan_id WHERE pl.status = 'CERRADO' AND l.counted IS NOT NULL "
                + "GROUP BY l.product) c ON c.product = p.id "
                + "WHERE p.managestock = 1 AND COALESCE(p.isservice, 0) = 0 AND COALESCE(p.systemobject, 0) = 0 "
                + "AND p.id NOT IN (SELECT l.product FROM stockcount_lines l JOIN stockcount_plans pl ON pl.id = l.plan_id "
                + "WHERE pl.status = 'PROGRAMADO') "
                + "ORDER BY c.last IS NOT NULL, c.last, p.name LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }
        }
        return ids;
    }

    /**
     * Creates the rotating count when it is due (one per call). Returns the
     * new plan id, or null when nothing was due.
     */
    public static Integer runRotationIfDue(Connection con, String location, String user, LocalDate today)
            throws SQLException {
        Rotation r = loadRotation(con);
        if (!r.enabled || r.next == null || r.next.isAfter(today) || r.size <= 0) {
            return null;
        }
        List<String> ids = leastRecentlyCounted(con, r.size);
        LocalDate date = r.next;
        r.next = today.plusDays(Math.max(1, r.days));
        saveRotation(con, r);
        if (ids.isEmpty()) {
            return null;
        }
        int id = createPlan(con, "Conteo rotativo " + date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                date, location, user, true);
        addProducts(con, id, ids);
        return id;
    }
}
