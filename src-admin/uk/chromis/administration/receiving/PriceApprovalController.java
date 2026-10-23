package uk.chromis.administration.receiving;

import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import uk.chromis.administration.main.Start;
import uk.chromis.administration.receiving.PurchaseOrderStore.PriceChange;
import uk.chromis.data.loader.SessionFactory;

/** Cost changes found when receiving orders, applied only once someone authorizes them. */
public class PriceApprovalController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(PriceApprovalController.class.getName());

    @FXML
    public AnchorPane apAnchor;
    @FXML
    public TableView<Item> table;
    @FXML
    public TableColumn<Item, Boolean> colSelected;
    @FXML
    public TableColumn<Item, String> colDate;
    @FXML
    public TableColumn<Item, String> colOrder;
    @FXML
    public TableColumn<Item, String> colRef;
    @FXML
    public TableColumn<Item, String> colName;
    @FXML
    public TableColumn<Item, String> colOldCost;
    @FXML
    public TableColumn<Item, String> colNewCost;
    @FXML
    public TableColumn<Item, String> colVariation;
    @FXML
    public TableColumn<Item, String> colOldPrice;
    @FXML
    public TableColumn<Item, Boolean> colApplyPrice;
    @FXML
    public TableColumn<Item, Number> colNewPrice;
    @FXML
    public Button btnApprove;
    @FXML
    public Button btnReject;
    @FXML
    public Label lblSummary;
    @FXML
    public Label lblMessage;

    /** One pending change with what the user chose for it. */
    public static final class Item {

        final PriceChange change;
        final BooleanProperty selected = new SimpleBooleanProperty(true);
        final BooleanProperty applyPrice = new SimpleBooleanProperty(true);
        double newPrice;

        Item(PriceChange change) {
            this.change = change;
            this.newPrice = change.suggestedPrice;
        }
    }

    private final ObservableList<Item> items = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        buildTable();
        reloadData();
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }

    public void reloadData() {
        try {
            Connection con = connection();
            PurchaseOrderStore.createTables(con);
            List<Item> loaded = new ArrayList<>();
            for (PriceChange c : PurchaseOrderStore.pendingPriceChanges(con)) {
                Item i = new Item(c);
                i.selected.addListener((obs, o, n) -> updateSummary());
                loaded.add(i);
            }
            items.setAll(loaded);
        } catch (SQLException ex) {
            showError("No se pudieron leer los cambios de costo", ex);
        }
        updateSummary();
    }

    private void updateSummary() {
        long marked = items.stream().filter(i -> i.selected.get()).count();
        lblSummary.setText(items.size() + " pendiente(s) · " + marked + " marcado(s)");
        btnApprove.setDisable(marked == 0);
        btnReject.setDisable(marked == 0);
    }

    private void buildTable() {
        table.setItems(items);
        table.setPlaceholder(new Label("No hay cambios de costo por autorizar."));
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy");
        colSelected.setCellValueFactory(c -> c.getValue().selected);
        colSelected.setCellFactory(CheckBoxTableCell.forTableColumn(colSelected));
        colSelected.setEditable(true);
        colDate.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().change.created == null ? "" : date.format(c.getValue().change.created)));
        colOrder.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().change.ponumber));
        colRef.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().change.reference));
        colName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().change.name));
        colOldCost.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(PurchaseOrderStore.money(c.getValue().change.currentCost)));
        colNewCost.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(PurchaseOrderStore.money(c.getValue().change.newCost)));
        colVariation.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                String.format("%+.1f %%", c.getValue().change.variation())));
        colVariation.setCellFactory(c -> new TableCell<Item, String>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setStyle(empty || value == null ? "" : value.startsWith("+")
                        ? "-fx-text-fill: #b00020; -fx-font-weight: bold;" : "-fx-text-fill: #1b5e20; -fx-font-weight: bold;");
            }
        });
        colOldPrice.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(PurchaseOrderStore.money(c.getValue().change.currentPrice)));
        colApplyPrice.setCellValueFactory(c -> c.getValue().applyPrice);
        colApplyPrice.setCellFactory(CheckBoxTableCell.forTableColumn(colApplyPrice));
        colApplyPrice.setEditable(true);
        colNewPrice.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().newPrice));
        colNewPrice.setCellFactory(c -> new NumberCell<>(i -> i.newPrice, (i, v) -> {
            i.newPrice = v;
            i.applyPrice.set(true);
            lblMessage.setText("");
            return true;
        }, PurchaseOrderStore::money, i -> true, lblMessage::setText));
    }

    @FXML
    public void refresh() {
        lblMessage.setText("");
        reloadData();
    }

    @FXML
    public void selectAll() {
        items.forEach(i -> i.selected.set(true));
    }

    @FXML
    public void selectNone() {
        items.forEach(i -> i.selected.set(false));
    }

    private List<Item> marked() {
        List<Item> list = new ArrayList<>();
        for (Item i : items) {
            if (i.selected.get()) {
                list.add(i);
            }
        }
        return list;
    }

    @FXML
    public void approve() {
        table.requestFocus(); // commits a price that is still being typed
        Platform.runLater(() -> {
            List<Item> list = marked();
            if (list.isEmpty()) {
                return;
            }
            long withPrice = list.stream().filter(i -> i.applyPrice.get()).count();
            if (!ask("¿Autorizar " + list.size() + " cambio(s) de costo?\n\n"
                    + "Se cambiará el costo de " + list.size() + " producto(s)"
                    + (withPrice > 0 ? " y el precio de venta de " + withPrice : "") + ".")) {
                return;
            }
            int done = 0;
            try {
                Connection con = connection();
                for (Item i : list) {
                    if (PurchaseOrderStore.approve(con, i.change.id, i.applyPrice.get() ? i.newPrice : null, Start.getActiveUser())) {
                        done++;
                    }
                }
            } catch (SQLException ex) {
                showError("No se pudieron autorizar todos los cambios", ex);
            }
            reloadData();
            lblMessage.setText(done + " cambio(s) autorizado(s).");
        });
    }

    @FXML
    public void reject() {
        List<Item> list = marked();
        if (list.isEmpty() || !ask("¿Rechazar " + list.size() + " cambio(s) de costo?\n\nLos productos conservan su costo y precio actuales.")) {
            return;
        }
        int done = 0;
        try {
            Connection con = connection();
            for (Item i : list) {
                if (PurchaseOrderStore.reject(con, i.change.id, Start.getActiveUser())) {
                    done++;
                }
            }
        } catch (SQLException ex) {
            showError("No se pudieron rechazar todos los cambios", ex);
        }
        reloadData();
        lblMessage.setText(done + " cambio(s) rechazado(s).");
    }

    private boolean ask(String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle("Autorizar costos");
        alert.getDialogPane().setPrefWidth(520);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        if (apAnchor.getScene() != null) {
            alert.initOwner(apAnchor.getScene().getWindow());
        }
        Optional<ButtonType> r = alert.showAndWait();
        return r.isPresent() && r.get() == ButtonType.YES;
    }

    private void showError(String message, Throwable ex) {
        LOGGER.log(Level.WARNING, message, ex);
        Alert alert = new Alert(AlertType.ERROR, message + ":\n" + (ex == null ? "" : ex.getMessage()));
        alert.setHeaderText(null);
        alert.setTitle("Autorizar costos");
        if (apAnchor.getScene() != null) {
            alert.initOwner(apAnchor.getScene().getWindow());
        }
        alert.showAndWait();
    }
}
