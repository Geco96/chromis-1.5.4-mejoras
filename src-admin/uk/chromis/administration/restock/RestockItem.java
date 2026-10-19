package uk.chromis.administration.restock;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import uk.chromis.pos.restock.RestockStore;
import uk.chromis.pos.restock.RestockStore.Status;

/** One row of the restock screen. */
public final class RestockItem {

    private static final DecimalFormat NUMBER = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final RestockStore.Row row;
    private final BooleanProperty selected = new SimpleBooleanProperty();
    private final DoubleProperty order = new SimpleDoubleProperty();
    private final ObjectProperty<Status> status = new SimpleObjectProperty<>();
    private double carry;
    private double onOrder;
    private String carryFrom;

    public RestockItem(RestockStore.Row row) {
        this.row = row;
        refresh();
        order.set(row.suggested());
        selected.set(needsAttention());
    }

    public RestockStore.Row getRow() {
        return row;
    }

    public String getReference() {
        return row.reference;
    }

    public String getName() {
        return row.name;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public DoubleProperty orderProperty() {
        return order;
    }

    public ObjectProperty<Status> statusProperty() {
        return status;
    }

    public Status getStatus() {
        return status.get();
    }

    /** Shortage of earlier orders still to be ordered. */
    public double getCarry() {
        return carry;
    }

    public String getCarryFrom() {
        return carryFrom;
    }

    /** Orders at least the shortage and marks the product for the order. */
    public void setCarry(double units, String fromOrders) {
        carry = units;
        carryFrom = fromOrders;
        if (units > 0) {
            order.set(Math.max(order.get(), units));
            selected.set(true);
        }
    }

    /** Already ordered and not received: it is not ordered again. */
    public double getOnOrder() {
        return onOrder;
    }

    public void setOnOrder(double units) {
        onOrder = units;
        if (units > 0) {
            order.set(Math.max(row.suggested() - units, 0));
            selected.set(order.get() > 0);
        }
    }

    public boolean needsAttention() {
        Status s = status.get();
        return s == Status.AGOTADO || s == Status.BAJO || s == Status.RESERVA;
    }

    /** After the thresholds change: new status and suggested quantity. */
    public void refresh() {
        status.set(row.status());
    }

    public static String format(double value) {
        return NUMBER.format(value);
    }

    /** Accepts "12", "12.5" and "12,5"; blank is 0. Null when it is not a number. */
    public static Double parse(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
