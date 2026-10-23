package uk.chromis.pos.restock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What is already ordered from suppliers and not received yet, and the
 * shortages of received orders waiting for the next one. Shared by Chromis
 * POS (the daily draft order) and Admin (restock screen and receiving).
 */
public final class PendingOrders {

    public static final String GENERIC_SUPPLIER = "Proveedor general";

    /** Statuses of supplierorders while the goods have not (all) arrived. */
    public static final String PENDING = "Pending";
    public static final String PART_RECEIVED = "Received-Part order";

    private PendingOrders() {
    }

    /** Shortages of one product carried to the next order. */
    public static final class Carry {

        public double units;
        public final List<String> orders = new ArrayList<>();

        public String describe() {
            return String.join(", ", orders);
        }
    }

    /** Shortages not yet carried to a new order, by product. */
    public static Map<String, Carry> carryover(Connection con) throws SQLException {
        Map<String, Carry> map = new LinkedHashMap<>();
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT product, units, from_ponumber FROM order_carryover "
                        + "WHERE to_order IS NULL ORDER BY id")) {
            while (rs.next()) {
                Carry c = map.computeIfAbsent(rs.getString(1), k -> new Carry());
                c.units += rs.getDouble(2);
                String po = rs.getString(3);
                if (po != null && !c.orders.contains(po)) {
                    c.orders.add(po);
                }
            }
        } catch (SQLException ex) {
            if (!"42S02".equals(ex.getSQLState())) {
                throw ex;
            }
            // Nothing received with Recepción de mercancía yet: no table, no shortages
        }
        return map;
    }

    /** Units ordered and not received yet (orders still pending), by product. */
    public static Map<String, Double> onOrder(Connection con) throws SQLException {
        Map<String, Double> map = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT l.productid, SUM(l.orderbalance) FROM supplierorderlines l "
                + "JOIN supplierorders o ON o.id = l.orderid WHERE o.status IN (?, ?) GROUP BY l.productid")) {
            ps.setString(1, PENDING);
            ps.setString(2, PART_RECEIVED);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getDouble(2) > 0) {
                        map.put(rs.getString(1), rs.getDouble(2));
                    }
                }
            }
        }
        return map;
    }

    /** Id of the generic supplier, created the first time it is needed. */
    public static String genericSupplier(Connection con, String siteGuid) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM suppliers WHERE suppliername = ? AND siteguid = ?")) {
            ps.setString(1, GENERIC_SUPPLIER);
            ps.setString(2, siteGuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO suppliers (id, suppliername, comments, siteguid) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, GENERIC_SUPPLIER);
            ps.setString(3, "Creado por Reposición para los pedidos sin proveedor registrado");
            ps.setString(4, siteGuid);
            ps.executeUpdate();
        }
        return id;
    }
}
