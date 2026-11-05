package uk.chromis.pos.search;

/**
 * Fila ligera de producto para la busqueda (sin imagen ni precios por nivel).
 * El producto completo se carga solo cuando se elige.
 */
public class ProductoBusqueda {

    private final String id;
    private final String reference;
    private final String code;
    private final String name;
    private final Double priceSell;
    private final Double priceSellInc;
    private final Boolean variablePrice;

    public ProductoBusqueda(String id, String reference, String code, String name,
                            Double priceSell, Double priceSellInc, Boolean variablePrice) {
        this.id = id;
        this.reference = reference;
        this.code = code;
        this.name = name;
        this.priceSell = priceSell;
        this.priceSellInc = priceSellInc;
        this.variablePrice = variablePrice;
    }

    public String getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Double getPriceSell() {
        return priceSell;
    }

    public Double getPriceSellInc() {
        return priceSellInc;
    }

    public boolean isVariablePrice() {
        return variablePrice != null && variablePrice;
    }

    @Override
    public String toString() {
        return name;
    }
}
