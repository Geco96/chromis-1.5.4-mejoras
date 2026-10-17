package uk.chromis.pos.restock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Restock thresholds and stock status, shared by Chromis POS and Admin.
 *
 * Minimum and maximum are Chromis' own stock levels (table stocklevel:
 * stocksecurity / stockmaximum), so the product screen in Admin keeps working
 * with them. The "reserve" level, between minimum and maximum, lives in
 * restock_reserve. Only products with "manage stock" are considered, because
 * Chromis does not move the stock of the others.
 *
 * Call createTables() once before the other methods.
 */
public final class RestockStore {

    public enum Status {
        AGOTADO("Agotado"), BAJO("Bajo"), RESERVA("Reserva"), ACEPTABLE("Normal"), SIN_UMBRAL("Sin umbral");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /** Lower is worse; SIN_UMBRAL is never an alert. */
        public int severity() {
            switch (this) {
                case AGOTADO:
                    return 0;
                case BAJO:
                    return 1;
                case RESERVA:
                    return 2;
                default:
                    return 3;
            }
        }
    }

    /** One product of one location. Thresholds of 0 mean "not set". */
    public static final class Row {
        public String productId;
        public String reference;
        public String name;
        public double current;
        public double minimum;
        public double reserve;
        public double maximum;

        public boolean hasThresholds() {
            return minimum > 0 || reserve > 0 || maximum > 0;
        }

        public Status status() {
            return hasThresholds() ? classify(current, minimum, reserve) : Status.SIN_UMBRAL;
        }

        /** Units needed to get back to the maximum (whole units, never negative). */
        public double suggested() {
            return RestockStore.suggested(current, maximum);
        }
    }

    public static final class Settings {
        public boolean posAlerts = true;
        public boolean alertReserve = true;
    }

    private RestockStore() {
    }

    public static Status classify(double current, double minimum, double reserve) {
        if (current <= 0) {
            return Status.AGOTADO;
        }
        if (minimum > 0 && current <= minimum) {
            return Status.BAJO;
        }
        if (reserve > 0 && current <= reserve) {
            return Status.RESERVA;
        }
        return Status.ACEPTABLE;
    }

    public static double suggested(double current, double maximum) {
        if (maximum <= 0) {
            return 0;
        }
        return Math.max(0, Math.floor(maximum - current + 1e-9));
    }

    public static void createTables(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS restock_reserve ("
                    + "location VARCHAR(40) NOT NULL, product VARCHAR(40) NOT NULL, reserve DECIMAL(10,3) NOT NULL, "
                    + "PRIMARY KEY (location, product))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS restock_settings ("
                    + "id INT NOT NULL PRIMARY KEY, pos_alerts TINYINT(1) NOT NULL DEFAULT 1, "
                    + "alert_reserve TINYINT(1) NOT NULL DEFAULT 1)");
            st.executeUpdate("INSERT IGNORE INTO restock_settings (id, pos_alerts, alert_reserve) VALUES (1, 1, 1)");
        }
    }

    public static Settings loadSettings(Connection con) throws SQLException {
        Settings s = new Settings();
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT pos_alerts, alert_reserve FROM restock_settings WHERE id = 1")) {
            if (rs.next()) {
                s.posAlerts = rs.getBoolean(1);
                s.alertReserve = rs.getBoolean(2);
            }
        }
        return s;
    }

    public static void saveSettings(Connection con, Settings s) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE restock_settings SET pos_alerts = ?, alert_reserve = ? WHERE id = 1")) {
            ps.setBoolean(1, s.posAlerts);
            ps.setBoolean(2, s.alertReserve);
            ps.executeUpdate();
        }
    }

    /** Products with "manage stock" in the location; only the given ids when productIds is not null. */
    public static List<Row> load(Connection con, String location, Collection<String> productIds) throws SQLException {
        List<Row> rows = new ArrayList<>();
        if (productIds != null && productIds.isEmpty()) {
            return rows;
        }
        StringBuilder sql = new StringBuilder("SELECT p.id, p.reference, p.name, "
                + "(SELECT COALESCE(SUM(s.units), 0) FROM stockcurrent s WHERE s.product = p.id AND s.location = ?), "
                + "(SELECT MAX(l.stocksecurity) FROM stocklevel l WHERE l.product = p.id AND l.location = ?), "
                + "(SELECT MAX(r.reserve) FROM restock_reserve r WHERE r.product = p.id AND r.location = ?), "
                + "(SELECT MAX(l.stockmaximum) FROM stocklevel l WHERE l.product = p.id AND l.location = ?) "
                + "FROM products p WHERE p.managestock = 1 AND COALESCE(p.isservice, 0) = 0 "
                + "AND COALESCE(p.systemobject, 0) = 0");
        if (productIds != null) {
            sql.append(" AND p.id IN (");
            for (int i = 0; i < productIds.size(); i++) {
                sql.append(i == 0 ? "?" : ", ?");
            }
            sql.append(")");
        }
        sql.append(" ORDER BY p.name");
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int i = 1;
            for (int k = 0; k < 4; k++) {
                ps.setString(i++, location);
            }
            if (productIds != null) {
                for (String id : productIds) {
                    ps.setString(i++, id);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Row r = new Row();
                    r.productId = rs.getString(1);
                    r.reference = rs.getString(2);
                    r.name = rs.getString(3);
                    r.current = rs.getDouble(4);
                    r.minimum = rs.getDouble(5);
                    r.reserve = rs.getDouble(6);
                    r.maximum = rs.getDouble(7);
                    rows.add(r);
                }
            }
        }
        return rows;
    }

    /**
     * Saves the three thresholds of a product. Also makes sure the product has
     * a stockcurrent row: Admin's product screen only shows (and on save keeps)
     * the stock levels of products that have one.
     */
    public static void saveThresholds(Connection con, String location, String siteGuid, String productId,
            double minimum, double reserve, double maximum) throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT IGNORE INTO stockcurrent (location, product, units, siteguid) VALUES (?, ?, 0, ?)")) {
                ps.setString(1, location);
                ps.setString(2, productId);
                ps.setString(3, siteGuid);
                ps.executeUpdate();
            }
            int updated;
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE stocklevel SET stocksecurity = ?, stockmaximum = ? WHERE product = ? AND location = ?")) {
                ps.setDouble(1, minimum);
                ps.setDouble(2, maximum);
                ps.setString(3, productId);
                ps.setString(4, location);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO stocklevel "
                        + "(id, location, product, stocksecurity, stockmaximum, siteguid) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, location);
                    ps.setString(3, productId);
                    ps.setDouble(4, minimum);
                    ps.setDouble(5, maximum);
                    ps.setString(6, siteGuid);
                    ps.executeUpdate();
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM restock_reserve WHERE location = ? AND product = ?")) {
                ps.setString(1, location);
                ps.setString(2, productId);
                ps.executeUpdate();
            }
            if (reserve > 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO restock_reserve (location, product, reserve) VALUES (?, ?, ?)")) {
                    ps.setString(1, location);
                    ps.setString(2, productId);
                    ps.setDouble(3, reserve);
                    ps.executeUpdate();
                }
            }
            con.commit();
        } catch (SQLException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    /** Problem with the thresholds, or null if they are consistent. */
    public static String validate(double minimum, double reserve, double maximum) {
        if (minimum < 0 || reserve < 0 || maximum < 0) {
            return "Los umbrales no pueden ser negativos.";
        }
        if (reserve > 0 && minimum > 0 && reserve < minimum) {
            return "La reserva debe ser mayor o igual que el mínimo.";
        }
        double highest = Math.max(minimum, reserve);
        if (maximum > 0 && highest > maximum) {
            return "El máximo debe ser mayor o igual que el mínimo y la reserva.";
        }
        return null;
    }
}
