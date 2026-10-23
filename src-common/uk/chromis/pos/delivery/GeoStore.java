package uk.chromis.pos.delivery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Location on the map and commercial status of each customer, in table
 * customer_geo (customers itself is not changed). The source says who set the
 * pin last: "web" (imported from the old map web), "admin" or "app".
 */
public final class GeoStore {

    public static final String PROSPECT = "prospecto";
    public static final String CAPTIVE = "cautivo";
    public static final String PENDING_VISIT = "pendiente_visita";

    public static final String SOURCE_WEB = "web";
    public static final String SOURCE_ADMIN = "admin";
    public static final String SOURCE_APP = "app";

    private GeoStore() {
    }

    public static final class Geo {

        public String customer;
        public Double lat;
        public Double lng;
        public String status;
        public String source;
        public Timestamp updated;
        public String updatedBy;

        public boolean located() {
            return lat != null && lng != null;
        }
    }

    public static void createTable(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS customer_geo ("
                    + "customer VARCHAR(40) NOT NULL PRIMARY KEY, "
                    + "lat DECIMAL(10,7) NULL, "
                    + "lng DECIMAL(10,7) NULL, "
                    + "status VARCHAR(20) NOT NULL DEFAULT 'cautivo', "
                    + "source VARCHAR(10) NOT NULL DEFAULT 'admin', "
                    + "updated DATETIME NOT NULL, "
                    + "updated_by VARCHAR(50) NULL)");
        }
    }

    /** Problem with the coordinates, or null if they are valid (both null = no location). */
    public static String validate(Double lat, Double lng) {
        if (lat == null && lng == null) {
            return null;
        }
        if (lat == null || lng == null) {
            return "Faltan la latitud o la longitud.";
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return "Las coordenadas no son válidas.";
        }
        if (lat == 0 && lng == 0) {
            return "Las coordenadas 0, 0 no son una ubicación real.";
        }
        return null;
    }

    public static Geo get(Connection con, String customer) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT customer, lat, lng, status, source, updated, updated_by FROM customer_geo WHERE customer = ?")) {
            ps.setString(1, customer);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    /** All customers with a geo row, by customer id. */
    public static Map<String, Geo> all(Connection con) throws SQLException {
        Map<String, Geo> map = new LinkedHashMap<>();
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT customer, lat, lng, status, source, updated, updated_by FROM customer_geo")) {
            while (rs.next()) {
                Geo g = read(rs);
                map.put(g.customer, g);
            }
        }
        return map;
    }

    private static Geo read(ResultSet rs) throws SQLException {
        Geo g = new Geo();
        g.customer = rs.getString(1);
        g.lat = rs.getObject(2) == null ? null : rs.getDouble(2);
        g.lng = rs.getObject(3) == null ? null : rs.getDouble(3);
        g.status = rs.getString(4);
        g.source = rs.getString(5);
        g.updated = rs.getTimestamp(6);
        g.updatedBy = rs.getString(7);
        return g;
    }

    /** Saves the location (null, null removes it) and status of a customer. */
    public static void save(Connection con, String customer, Double lat, Double lng, String status, String source,
            String user) throws SQLException {
        String problem = validate(lat, lng);
        if (problem != null) {
            throw new SQLException(problem);
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO customer_geo "
                + "(customer, lat, lng, status, source, updated, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE lat = VALUES(lat), lng = VALUES(lng), status = VALUES(status), "
                + "source = VALUES(source), updated = VALUES(updated), updated_by = VALUES(updated_by)")) {
            ps.setString(1, customer);
            ps.setObject(2, lat);
            ps.setObject(3, lng);
            ps.setString(4, status == null ? CAPTIVE : status);
            ps.setString(5, source);
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            ps.setString(7, user == null ? null : (user.length() > 50 ? user.substring(0, 50) : user));
            ps.executeUpdate();
        }
    }
}
