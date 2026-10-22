package uk.chromis.pos.delivery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Delivery zones ("cuadrantes") drawn on the map: polygon, colour, the
 * dispatcher in charge (empty = any) and the driver who serves it. Dispatcher
 * and driver are Chromis users (people.id). The zone of a delivery is the one
 * that contains its customer's location.
 */
public final class ZoneStore {

    private ZoneStore() {
    }

    public static final class Zone {

        public int id;
        public String name;
        public String color = "#4A90E2";
        /** Corners as {lat, lng}. */
        public List<double[]> polygon = new ArrayList<>();
        public String dispatcher;
        public String driver;
        public boolean active = true;

        public boolean contains(double lat, double lng) {
            return ZoneStore.contains(polygon, lat, lng);
        }
    }

    public static void createTable(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS delivery_zones ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(60) NOT NULL, "
                    + "color VARCHAR(7) NOT NULL DEFAULT '#4A90E2', "
                    + "polygon TEXT NOT NULL, "
                    + "dispatcher VARCHAR(40) NULL, "
                    + "driver VARCHAR(40) NULL, "
                    + "active TINYINT(1) NOT NULL DEFAULT 1, "
                    + "created DATETIME NOT NULL)");
        }
    }

    // ---------------- Geometry ----------------

    /** Ray casting; good enough for zones inside a city. */
    public static boolean contains(List<double[]> polygon, double lat, double lng) {
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double yi = polygon.get(i)[0], xi = polygon.get(i)[1];
            double yj = polygon.get(j)[0], xj = polygon.get(j)[1];
            if ((yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** First active zone that contains the point, or null. */
    public static Zone zoneFor(List<Zone> zones, Double lat, Double lng) {
        if (lat == null || lng == null) {
            return null;
        }
        for (Zone z : zones) {
            if (z.active && z.polygon.size() >= 3 && z.contains(lat, lng)) {
                return z;
            }
        }
        return null;
    }

    /** Problem with the polygon, or null. */
    public static String validate(List<double[]> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return "El cuadrante necesita al menos 3 esquinas.";
        }
        for (double[] p : polygon) {
            if (p.length != 2 || p[0] < -90 || p[0] > 90 || p[1] < -180 || p[1] > 180) {
                return "Una esquina del cuadrante no tiene coordenadas válidas.";
            }
        }
        return null;
    }

    /** "lat,lng;lat,lng;..." — plain text so any client (Admin, Android) reads it without a JSON library. */
    public static String encode(List<double[]> polygon) {
        StringBuilder sb = new StringBuilder();
        for (double[] p : polygon) {
            sb.append(sb.length() == 0 ? "" : ";").append(String.format(Locale.ROOT, "%.7f,%.7f", p[0], p[1]));
        }
        return sb.toString();
    }

    public static List<double[]> decode(String text) {
        List<double[]> list = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return list;
        }
        for (String part : text.split(";")) {
            String[] ll = part.split(",");
            if (ll.length == 2) {
                try {
                    list.add(new double[]{Double.parseDouble(ll[0].trim()), Double.parseDouble(ll[1].trim())});
                } catch (NumberFormatException ex) {
                    // corner skipped
                }
            }
        }
        return list;
    }

    // ---------------- Data ----------------

    public static List<Zone> load(Connection con, boolean includeInactive) throws SQLException {
        List<Zone> zones = new ArrayList<>();
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, name, color, polygon, dispatcher, driver, active FROM delivery_zones"
                        + (includeInactive ? "" : " WHERE active = 1") + " ORDER BY name")) {
            while (rs.next()) {
                Zone z = new Zone();
                z.id = rs.getInt(1);
                z.name = rs.getString(2);
                z.color = rs.getString(3);
                z.polygon = decode(rs.getString(4));
                z.dispatcher = rs.getString(5);
                z.driver = rs.getString(6);
                z.active = rs.getBoolean(7);
                zones.add(z);
            }
        }
        return zones;
    }

    /** Inserts (id 0) or updates the zone; returns its id. */
    public static int save(Connection con, Zone z) throws SQLException {
        String problem = validate(z.polygon);
        if (problem == null && (z.name == null || z.name.trim().isEmpty())) {
            problem = "Ponle nombre al cuadrante.";
        }
        if (problem != null) {
            throw new SQLException(problem);
        }
        String color = z.color != null && z.color.matches("#[0-9a-fA-F]{6}") ? z.color.toUpperCase() : "#4A90E2";
        if (z.id == 0) {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO delivery_zones "
                    + "(name, color, polygon, dispatcher, driver, active, created) VALUES (?, ?, ?, ?, ?, 1, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, z.name.trim());
                ps.setString(2, color);
                ps.setString(3, encode(z.polygon));
                ps.setString(4, empty(z.dispatcher));
                ps.setString(5, empty(z.driver));
                ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    rs.next();
                    z.id = rs.getInt(1);
                }
            }
        } else {
            try (PreparedStatement ps = con.prepareStatement("UPDATE delivery_zones SET name = ?, color = ?, polygon = ?, "
                    + "dispatcher = ?, driver = ?, active = ? WHERE id = ?")) {
                ps.setString(1, z.name.trim());
                ps.setString(2, color);
                ps.setString(3, encode(z.polygon));
                ps.setString(4, empty(z.dispatcher));
                ps.setString(5, empty(z.driver));
                ps.setBoolean(6, z.active);
                ps.setInt(7, z.id);
                ps.executeUpdate();
            }
        }
        return z.id;
    }

    /** Zones are not deleted (deliveries keep their history): they are deactivated. */
    public static void deactivate(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE delivery_zones SET active = 0 WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private static String empty(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }
}
