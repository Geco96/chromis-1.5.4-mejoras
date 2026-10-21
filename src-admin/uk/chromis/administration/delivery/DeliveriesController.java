package uk.chromis.administration.delivery;

import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.AnchorPane;
import uk.chromis.administration.receiving.PurchaseOrderStore;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.pos.delivery.DeliveryStore;
import uk.chromis.pos.settings.PosSettings;

/**
 * Delivery sales: the settings of the Mostrador / Entrega button and the
 * list of deliveries, marked as delivered here.
 */
public class DeliveriesController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(DeliveriesController.class.getName());
    private static final int DELIVERED_DAYS = 7;

    @FXML
    public AnchorPane apAnchor;
    @FXML
    public CheckBox chkButton;
    @FXML
    public CheckBox chkAsk;
    @FXML
    public CheckBox chkDelivered;
    @FXML
    public Label lblSummary;
    @FXML
    public Label lblMessage;
    @FXML
    public TableView<Row> table;
    @FXML
    public TableColumn<Row, Boolean> colDelivered;
    @FXML
    public TableColumn<Row, Number> colTicket;
    @FXML
    public TableColumn<Row, String> colSaleDate;
    @FXML
    public TableColumn<Row, String> colDeliveryDate;
    @FXML
    public TableColumn<Row, String> colCustomer;
    @FXML
    public TableColumn<Row, String> colName;
    @FXML
    public TableColumn<Row, String> colAddress;
    @FXML
    public TableColumn<Row, String> colPhone;
    @FXML
    public TableColumn<Row, String> colComments;
    @FXML
    public TableColumn<Row, String> colTotal;

    private final ObservableList<Row> rows = FXCollections.observableArrayList();

    /** One delivery; the check box writes customer_delivery.delivered. */
    public final class Row {

        final DeliveryStore.Delivery d;
        final BooleanProperty delivered;

        Row(DeliveryStore.Delivery d) {
            this.d = d;
            delivered = new SimpleBooleanProperty(d.delivered);
            delivered.addListener((obs, o, n) -> save(this, n));
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        SimpleDateFormat dateTime = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy");
        table.setItems(rows);
        table.setPlaceholder(new Label("No hay entregas pendientes."));
        colDelivered.setCellValueFactory(c -> c.getValue().delivered);
        colDelivered.setCellFactory(CheckBoxTableCell.forTableColumn(colDelivered));
        colDelivered.setEditable(true);
        colTicket.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().d.ticketNumber));
        colSaleDate.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().d.saleDate == null ? "" : dateTime.format(c.getValue().d.saleDate)));
        colDeliveryDate.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().d.deliveryDate == null ? "" : date.format(c.getValue().d.deliveryDate)));
        colCustomer.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().d.customerName));
        colName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().d.name));
        colAddress.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().d.address()));
        colPhone.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().d.phone));
        colComments.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().d.comments));
        colTotal.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(PurchaseOrderStore.money(c.getValue().d.total)));
        reloadData();
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }

    public void reloadData() {
        lblMessage.setText("");
        try {
            Connection con = connection();
            chkButton.setSelected(!"0".equals(PosSettings.get(con, PosSettings.DELIVERY_BUTTON, "1")));
            chkAsk.setSelected(!"0".equals(PosSettings.get(con, PosSettings.DELIVERY_ASK, "1")));
            chkAsk.setDisable(!chkButton.isSelected());
        } catch (SQLException ex) {
            error("No se pudo leer la configuración de entregas", ex);
        }
        loadRows();
    }

    private void loadRows() {
        try {
            List<DeliveryStore.Delivery> list = DeliveryStore.load(connection(), chkDelivered.isSelected(), DELIVERED_DAYS);
            rows.clear();
            for (DeliveryStore.Delivery d : list) {
                rows.add(new Row(d));
            }
        } catch (SQLException ex) {
            error("No se pudieron leer las entregas", ex);
        }
        updateSummary();
    }

    private void updateSummary() {
        long pending = rows.stream().filter(r -> !r.delivered.get()).count();
        lblSummary.setText(pending + (pending == 1 ? " entrega pendiente" : " entregas pendientes"));
    }

    private void save(Row row, boolean delivered) {
        try {
            DeliveryStore.setDelivered(connection(), row.d.ticketId, delivered);
            row.d.delivered = delivered;
            lblMessage.setText("");
        } catch (SQLException ex) {
            error("No se pudo guardar la entrega", ex);
        }
        updateSummary();
    }

    @FXML
    public void refresh() {
        loadRows();
    }

    @FXML
    public void saveSettings() {
        chkAsk.setDisable(!chkButton.isSelected());
        try {
            Connection con = connection();
            PosSettings.put(con, PosSettings.DELIVERY_BUTTON, chkButton.isSelected() ? "1" : "0");
            PosSettings.put(con, PosSettings.DELIVERY_ASK, chkAsk.isSelected() ? "1" : "0");
            lblMessage.setText("");
        } catch (SQLException ex) {
            error("No se pudo guardar la configuración", ex);
        }
    }

    private void error(String message, Throwable ex) {
        LOGGER.log(Level.WARNING, message, ex);
        lblMessage.setText(message + ": " + ex.getMessage());
    }
}
