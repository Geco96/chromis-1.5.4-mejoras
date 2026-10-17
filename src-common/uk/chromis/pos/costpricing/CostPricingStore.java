package uk.chromis.pos.costpricing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Storage for cost-plus pricing, shared by Chromis POS and Chromis Admin.
 *
 * Kept in tables of its own so the original Chromis tables are not altered:
 *   costpricing_customers (customer, markup)  markup in percent, e.g. 7.5
 *   costpricing_excluded  (product)           products that keep their normal price
 */
public final class CostPricingStore {

    private CostPricingStore() {
    }

    public static void createTables(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS costpricing_customers ("
                    + "customer VARCHAR(255) NOT NULL PRIMARY KEY, "
                    + "markup DOUBLE NOT NULL)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS costpricing_excluded ("
                    + "product VARCHAR(255) NOT NULL PRIMARY KEY)");
        }
    }

    /** Markup in percent, or null if the customer pays normal prices. */
    public static Double getMarkup(Connection con, String customerId) throws SQLException {
        createTables(con);
        try (PreparedStatement ps = con.prepareStatement("SELECT markup FROM costpricing_customers WHERE customer = ?")) {
            ps.setString(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : null;
            }
        }
    }

    /** Saves the markup; null removes cost-plus pricing for the customer. */
    public static void setMarkup(Connection con, String customerId, Double markup) throws SQLException {
        createTables(con);
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM costpricing_customers WHERE customer = ?")) {
            ps.setString(1, customerId);
            ps.executeUpdate();
        }
        if (markup != null) {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO costpricing_customers (customer, markup) VALUES (?, ?)")) {
                ps.setString(1, customerId);
                ps.setDouble(2, markup);
                ps.executeUpdate();
            }
        }
    }

    public static boolean isExcluded(Connection con, String productId) throws SQLException {
        createTables(con);
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM costpricing_excluded WHERE product = ?")) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void setExcluded(Connection con, String productId, boolean excluded) throws SQLException {
        createTables(con);
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM costpricing_excluded WHERE product = ?")) {
            ps.setString(1, productId);
            ps.executeUpdate();
        }
        if (excluded) {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO costpricing_excluded (product) VALUES (?)")) {
                ps.setString(1, productId);
                ps.executeUpdate();
            }
        }
    }

    public static double price(double cost, double markup) {
        return Math.rint(cost * (1.0 + markup / 100.0) * 100.0) / 100.0;
    }
}
