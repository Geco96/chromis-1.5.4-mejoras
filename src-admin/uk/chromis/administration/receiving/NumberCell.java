package uk.chromis.administration.receiving;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;

/**
 * Table cell with a text field that is always visible and commits on Enter
 * or when it loses the focus, as in the restock screen.
 */
public final class NumberCell<S> extends TableCell<S, Number> {

    private final TextField field = new TextField();
    private final Function<S, Double> getter;
    private final BiPredicate<S, Double> setter;
    private final Function<Double, String> format;
    private final Predicate<S> editable;
    private final Consumer<String> error;
    private S editing;

    public NumberCell(Function<S, Double> getter, BiPredicate<S, Double> setter, Function<Double, String> format,
            Predicate<S> editable, Consumer<String> error) {
        this.getter = getter;
        this.setter = setter;
        this.format = format;
        this.editable = editable;
        this.error = error;
        field.setAlignment(Pos.CENTER_RIGHT);
        field.setOnAction(e -> commit());
        field.focusedProperty().addListener((obs, was, is) -> {
            if (is) {
                editing = currentItem();
                Platform.runLater(field::selectAll);
            } else {
                commit();
            }
        });
    }

    private S currentItem() {
        return getTableRow() == null ? null : getTableRow().getItem();
    }

    private void commit() {
        S item = editing != null ? editing : currentItem();
        if (item == null) {
            return;
        }
        Double value = parse(field.getText());
        double old = getter.apply(item);
        if (value == null || value < 0) {
            error.accept("\"" + field.getText() + "\" no es una cantidad válida.");
            value = null;
        } else if (value != old && !setter.test(item, value)) {
            value = null;
        }
        if (item == currentItem()) {
            field.setText(format.apply(value == null ? old : value));
        }
        if (!field.isFocused()) {
            editing = null;
        }
    }

    @Override
    protected void updateItem(Number value, boolean empty) {
        super.updateItem(value, empty);
        S item = currentItem();
        if (empty || value == null || item == null) {
            setGraphic(null);
            setText(null);
        } else if (!editable.test(item)) {
            setGraphic(null);
            setText(format.apply(value.doubleValue()));
            setAlignment(Pos.CENTER_RIGHT);
        } else {
            setText(null);
            if (!field.isFocused()) {
                field.setText(format.apply(value.doubleValue()));
            }
            setGraphic(field);
        }
    }

    /** Accepts "12", "12.5", "12,5", "1,234" and "$1,234.50"; blank is 0. Null when it is not a number. */
    static Double parse(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        String t = text.trim().replace("$", "").replace(" ", "");
        if ((t.contains(",") && t.contains(".")) || t.matches("\\d{1,3}(,\\d{3})+")) {
            t = t.replace(",", ""); // 1,234.50 or 1,234: comma separates thousands
        } else {
            t = t.replace(',', '.');
        }
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
