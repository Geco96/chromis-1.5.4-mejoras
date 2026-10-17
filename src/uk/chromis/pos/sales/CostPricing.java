package uk.chromis.pos.sales;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import uk.chromis.data.loader.Session;
import uk.chromis.pos.costpricing.CostPricingStore;

/**
 * Cost-plus pricing per customer for the sales screen: selected customers pay
 * the product cost (pricebuy) plus a percentage instead of the sell price.
 * Values are cached until reset() so the ticket is not slowed down by queries.
 */
public final class CostPricing {

    private static final Logger LOGGER = Logger.getLogger(CostPricing.class.getName());

    private final Session session;
    private String cachedCustomer = null;
    private Double cachedMarkup = null;
    private final Map<String, Boolean> excludedCache = new HashMap<>();

    public CostPricing(Session session) {
        this.session = session;
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
            markup = CostPricingStore.getMarkup(session.getConnection(), customerId);
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
                excluded = CostPricingStore.isExcluded(session.getConnection(), productId);
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "No se pudo leer la exclusión de precio sobre costo", ex);
            }
            excludedCache.put(productId, excluded);
        }
        return excluded;
    }

    public static double price(double cost, double markup) {
        return CostPricingStore.price(cost, markup);
    }
}
