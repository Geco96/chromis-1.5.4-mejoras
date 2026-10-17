package uk.chromis.pos.sales;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import uk.chromis.data.loader.Session;

/**
 * Cost-plus pricing per customer: selected customers pay the product cost
 * (pricebuy) plus a percentage instead of the sell price.
 *
 * Data lives in two tables of its own so the original Chromis tables are not
 * altered:
 *   costpricing_customers (customer, markup)  markup in percent, e.g. 7.5
 *   costpricing_excluded  (product)           products that keep their normal price
 */
public final class CostPricing {

    private static final Logger LOGGER = Logger.getLogger(CostPricing.class.getName());

    private final Session session;
    private boolean tablesChecked = false;
    private String cachedCustomer = null;
    private Double cachedMarkup = null;
    private final Map<String, Boolean> excludedCache = new HashMap<>();

    public CostPricing(Session session) {
        this.session = session;
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

    private void ensureTables() throws SQLException {
        if (!tablesChecked) {
            createTables(session.getConnection());
            tablesChecked = true;
        }
    }

    /** Forgets cached values so changes made in Admin are picked up. */
    public void reset() {
        cachedCustomer = null;
        cachedMarkup = null;
        excludedCache.clear();
    }

    /** Markup in percent for the customer, or null if the customer has no cost-plus pricing. */
    public Double getMarkup(String customerId) {
        if (customerId == null) {
            return null;
        }
        if (customerId.equals(cachedCustomer)) {
            return cachedMarkup;
        }
        Double markup = null;
        try {
            ensureTables();
            try (PreparedStatement ps = session.getConnection().prepareStatement(
                    "SELECT markup FROM costpricing_customers WHERE customer = ?")) {
                ps.setString(1, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        markup = rs.getDouble(1);
                    }
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo leer el margen sobre costo del cliente", ex);
        }
        cachedCustomer = customerId;
        cachedMarkup = markup;
        return markup;
    }

    public boolean isExcluded(String productId) {
        if (productId == null) {
            return true;
        }
        Boolean excluded = excludedCache.get(productId);
        if (excluded == null) {
            excluded = false;
            try {
                ensureTables();
                try (PreparedStatement ps = session.getConnection().prepareStatement(
                        "SELECT 1 FROM costpricing_excluded WHERE product = ?")) {
                    ps.setString(1, productId);
                    try (ResultSet rs = ps.executeQuery()) {
                        excluded = rs.next();
                    }
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "No se pudo leer la exclusión de precio sobre costo", ex);
            }
            excludedCache.put(productId, excluded);
        }
        return excluded;
    }

    public static double price(double cost, double markup) {
        return Math.rint(cost * (1.0 + markup / 100.0) * 100.0) / 100.0;
    }
}
