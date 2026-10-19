package uk.chromis.administration.receiving;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import uk.chromis.administration.DatabaseLogic;
import uk.chromis.administration.main.Start;
import uk.chromis.administration.receiving.PurchaseOrderStore.Line;
import uk.chromis.administration.receiving.PurchaseOrderStore.Order;
import uk.chromis.administration.restock.RestockController;
import uk.chromis.administration.restock.RestockPdf;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.globals.Company;
import uk.chromis.pos.restock.RestockStore;

/**
 * Receiving a supplier order: scan the barcode of the order PDF, then either
 * "everything arrived" (one click) or adjust quantities and costs line by line
 * (typing them or scanning each product). Confirming adds the stock, carries
 * the shortages to the next order, leaves cost changes for authorization and
 * prints the contrarecibo.
 */
public class ReceivingController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(ReceivingController.class.getName());

    @FXML
    public AnchorPane apAnchor;
    @FXML
    public TextField txtFolio;
    @FXML
    public ComboBox<String> cmbOpen;
    @FXML
    public ComboBox<String> cmbLocation;
    @FXML
    public Button btnReprintOrder;
    @FXML
    public Label lblOrder;
    @FXML
    public TextField txtScan;
    @FXML
    public TextField txtScanQty;
    @FXML
    public Button btnZero;
    @FXML
    public TableView<Line> table;
    @FXML
    public TableColumn<Line, String> colRef;
    @FXML
    public TableColumn<Line, String> colName;
    @FXML
    public TableColumn<Line, Number> colOrdered;
    @FXML
    public TableColumn<Line, Number> colReceived;
    @FXML
    public TableColumn<Line, String> colDiff;
    @FXML
    public TableColumn<Line, Number> colOrderCost;
    @FXML
    public TableColumn<Line, Number> colReceivedCost;
    @FXML
    public TableColumn<Line, Number> colAmount;
    @FXML
    public TableColumn<Line, String> colObservation;
    @FXML
    public TextField txtInvoice;
    @FXML
    public Label lblSummary;
    @FXML
    public Button btnAllOk;
    @FXML
    public Button btnConfirm;
    @FXML
    public Button btnReceipt;
    @FXML
    public Label lblPrices;
    @FXML
    public Label lblMessage;

    private final ObservableList<Line> lines = FXCollections.observableArrayList();
    private final Map<String, String> locations = new LinkedHashMap<>();
    private final Map<String, String> openOrders = new LinkedHashMap<>();
    private String siteGuid;
    private Order order;
    private boolean counting;
    private boolean loadingCombo;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        siteGuid = DatabaseLogic.getInstance().getSiteGUID();
        buildTable();
        cmbOpen.valueProperty().addListener((obs, o, n) -> {
            if (!loadingCombo && n != null && openOrders.containsKey(n)) {
                showOrder(openOrders.get(n));
            }
        });
        reloadData();
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }

    public void reloadData() {
        try {
            Connection con = connection();
            PurchaseOrderStore.createTables(con);
            loadLocations(con);
            loadOpenOrders(con);
            updatePrices(con);
        } catch (SQLException ex) {
            showError("No se pudieron leer los pedidos", ex);
        }
        if (order != null) {
            showOrder(order.id);
        } else {
            showNothing();
        }
        Platform.runLater(txtFolio::requestFocus);
    }

    private void loadLocations(Connection con) throws SQLException {
        String current = cmbLocation.getValue();
        locations.clear();
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, name FROM locations ORDER BY name")) {
            while (rs.next()) {
                locations.put(rs.getString(2), rs.getString(1));
            }
        }
        cmbLocation.getItems().setAll(locations.keySet());
        cmbLocation.setValue(current != null && locations.containsKey(current) ? current
                : locations.isEmpty() ? null : locations.keySet().iterator().next());
        cmbLocation.setVisible(locations.size() > 1);
        cmbLocation.setManaged(locations.size() > 1);
    }

    private void loadOpenOrders(Connection con) throws SQLException {
        loadingCombo = true;
        try {
            openOrders.clear();
            openOrders.putAll(PurchaseOrderStore.openOrders(con));
            cmbOpen.getItems().setAll(openOrders.keySet());
            cmbOpen.setValue(order != null && openOrders.containsKey(order.ponumber) ? order.ponumber : null);
            cmbOpen.setPromptText(openOrders.isEmpty() ? "(ninguno)" : openOrders.size() + " pendiente(s)");
        } finally {
            loadingCombo = false;
        }
    }

    private void updatePrices(Connection con) throws SQLException {
        int n = PurchaseOrderStore.countPendingPriceChanges(con);
        lblPrices.setText(n == 0 ? "" : n + " cambio(s) de costo esperan autorización (menú Autorizar costos)");
    }

    // ---------------- Showing an order ----------------

    private void showNothing() {
        order = null;
        lines.clear();
        counting = false;
        lblOrder.setText("Escanea el código de barras del PDF del pedido para ver lo que debe llegar.");
        txtInvoice.clear();
        updateState();
    }

    private void showOrder(String orderId) {
        warn("");
        try {
            Order o = PurchaseOrderStore.loadOrder(connection(), orderId);
            if (o == null) {
                warn("El pedido ya no existe.");
                showNothing();
                return;
            }
            order = o;
            counting = false;
            lines.setAll(o.lines);
            txtInvoice.setText(o.invoiceNo == null ? "" : o.invoiceNo);
            SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            lblOrder.setText("Pedido " + o.ponumber + "  ·  " + (o.supplierName == null ? "" : o.supplierName)
                    + "  ·  " + (o.orderDate == null ? "" : date.format(o.orderDate))
                    + (o.user == null || o.user.isEmpty() ? "" : "  ·  pidió " + o.user)
                    + "  ·  " + o.statusLabel()
                    + (o.receiptDate == null || o.isOpen() ? "" : " el " + date.format(o.receiptDate)));
            loadingCombo = true;
            cmbOpen.setValue(openOrders.containsKey(o.ponumber) ? o.ponumber : null);
            loadingCombo = false;
        } catch (SQLException ex) {
            showError("No se pudo leer el pedido", ex);
        }
        updateState();
        if (order != null && order.isOpen()) {
            Platform.runLater(txtScan::requestFocus);
        }
    }

    private void updateState() {
        boolean open = order != null && order.isOpen();
        boolean any = order != null;
        btnAllOk.setDisable(!open);
        btnConfirm.setDisable(!open);
        btnZero.setDisable(!open);
        txtScan.setDisable(!open);
        txtScanQty.setDisable(!open);
        txtInvoice.setDisable(!open);
        btnReceipt.setDisable(!any || open);
        btnReprintOrder.setDisable(!any);
        table.refresh();
        updateSummary();
    }

    private void updateSummary() {
        if (order == null) {
            lblSummary.setText("");
            return;
        }
        int shortages = 0;
        int extras = 0;
        int prices = 0;
        for (Line l : lines) {
            if (l.difference() < 0) {
                shortages++;
            } else if (l.isExtra() ? l.received > 0 : l.difference() > 0) {
                extras++;
            }
            if (l.costChanged()) {
                prices++;
            }
        }
        lblSummary.setText(lines.size() + " productos  ·  pedido $ " + PurchaseOrderStore.money(PurchaseOrderStore.total(lines, false))
                + "  ·  recibido $ " + PurchaseOrderStore.money(PurchaseOrderStore.total(lines, true))
                + (shortages + extras + prices == 0 ? "  ·  sin diferencias"
                : "  ·  " + shortages + " con faltante, " + extras + " con sobrante, " + prices + " con cambio de costo"));
    }

    // ---------------- Table ----------------

    private boolean editable(Line l) {
        return order != null && order.isOpen();
    }

    private void buildTable() {
        table.setItems(lines);
        table.setPlaceholder(new Label("Escanea el código del pedido o elígelo de la lista de pendientes."));
        colRef.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().reference));
        colName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name));
        colOrdered.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().ordered));
        colOrdered.setCellFactory(c -> textCell(v -> PurchaseOrderStore.format(v)));
        colReceived.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().received));
        colReceived.setCellFactory(c -> new NumberCell<>(l -> l.received, (l, v) -> {
            l.received = v;
            changed();
            return true;
        }, PurchaseOrderStore::format, this::editable, this::warn));
        colDiff.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(diffText(c.getValue())));
        colOrderCost.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().orderCost));
        colOrderCost.setCellFactory(c -> textCell(PurchaseOrderStore::money));
        colReceivedCost.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().receivedCost));
        colReceivedCost.setCellFactory(c -> new NumberCell<>(l -> l.receivedCost, (l, v) -> {
            l.receivedCost = v;
            changed();
            return true;
        }, PurchaseOrderStore::money, this::editable, this::warn));
        colAmount.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().received * c.getValue().receivedCost));
        colAmount.setCellFactory(c -> textCell(PurchaseOrderStore::money));
        colObservation.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().observation()));

        table.setRowFactory(t -> new TableRow<Line>() {
            @Override
            protected void updateItem(Line l, boolean empty) {
                super.updateItem(l, empty);
                if (empty || l == null) {
                    setStyle("");
                } else if (l.difference() < 0) {
                    setStyle("-fx-background-color: #ffd9d9;");
                } else if (l.isExtra() || l.difference() > 0) {
                    setStyle("-fx-background-color: #d9ecff;");
                } else if (l.costChanged()) {
                    setStyle("-fx-background-color: #fff5c8;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private static String diffText(Line l) {
        double d = l.difference();
        return d == 0 ? "" : (d > 0 ? "+" : "") + PurchaseOrderStore.format(d);
    }

    private static TableCell<Line, Number> textCell(java.util.function.Function<Double, String> format) {
        return new TableCell<Line, Number>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : format.apply(value.doubleValue()));
            }
        };
    }

    private void changed() {
        warn("");
        // refresh after the cell has finished committing
        Platform.runLater(() -> {
            table.refresh();
            updateSummary();
        });
    }

    // ---------------- Actions ----------------

    @FXML
    public void refresh() {
        reloadData();
    }

    @FXML
    public void loadFromField() {
        String text = txtFolio.getText();
        txtFolio.clear();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        try {
            String id = PurchaseOrderStore.findOrder(connection(), text);
            if (id == null) {
                warn("No hay ningún pedido con el número \"" + text.trim() + "\".");
                return;
            }
            showOrder(id);
        } catch (SQLException ex) {
            showError("No se pudo buscar el pedido", ex);
        }
    }

    @FXML
    public void countFromZero() {
        if (order == null || !order.isOpen()) {
            return;
        }
        lines.removeIf(Line::isExtra);
        for (Line l : lines) {
            l.received = 0;
        }
        order.lines.clear();
        order.lines.addAll(lines);
        counting = true;
        note("Lo recibido quedó en 0: escanea cada producto que llegó.");
        table.refresh();
        updateSummary();
        txtScan.requestFocus();
    }

    @FXML
    public void scanProduct() {
        if (order == null || !order.isOpen()) {
            return;
        }
        String code = txtScan.getText() == null ? "" : txtScan.getText().trim();
        txtScan.clear();
        if (code.isEmpty()) {
            return;
        }
        Double qty = NumberCell.parse(txtScanQty.getText());
        if (qty == null || qty <= 0) {
            warn("La cantidad debe ser mayor que 0.");
            return;
        }
        try {
            Connection con = connection();
            String productId = PurchaseOrderStore.findProduct(con, code);
            if (productId == null) {
                warn("No existe ningún producto con el código \"" + code + "\".");
                return;
            }
            if (!counting) {
                countFromZero();
            }
            Line line = null;
            for (Line l : lines) {
                if (productId.equals(l.productId)) {
                    line = l;
                    break;
                }
            }
            String suffix = "";
            if (line == null) {
                line = PurchaseOrderStore.extraLine(con, productId);
                lines.add(line);
                order.lines.add(line);
                suffix = " (no venía en el pedido)";
            }
            line.received = PurchaseOrderStore.round3(line.received + qty);
            note(line.name + ": " + PurchaseOrderStore.format(line.received) + " recibido(s)" + suffix);
            table.getSelectionModel().select(line);
            table.scrollTo(line);
            table.refresh();
            updateSummary();
        } catch (SQLException ex) {
            showError("No se pudo buscar el producto", ex);
        }
        txtScanQty.setText("1");
        txtScan.requestFocus();
    }

    @FXML
    public void receiveAll() {
        if (order == null || !order.isOpen()) {
            return;
        }
        if (!ask("¿Llegó exactamente lo del pedido " + order.ponumber + "?\n\n"
                + "Se dará entrada al stock de los " + order.lines.stream().filter(l -> !l.isExtra()).count()
                + " productos con las cantidades y costos del pedido.")) {
            return;
        }
        lines.removeIf(Line::isExtra);
        for (Line l : lines) {
            l.received = l.ordered;
            l.receivedCost = l.orderCost;
        }
        order.lines.clear();
        order.lines.addAll(lines);
        save();
    }

    @FXML
    public void confirm() {
        if (order == null || !order.isOpen()) {
            return;
        }
        table.requestFocus(); // commits a cell that is still being typed
        Platform.runLater(() -> {
            long shortages = lines.stream().filter(l -> l.difference() < 0).count();
            long extras = lines.stream().filter(l -> l.isExtra() ? l.received > 0 : l.difference() > 0).count();
            long prices = lines.stream().filter(Line::costChanged).count();
            StringBuilder sb = new StringBuilder("¿Confirmas la recepción del pedido " + order.ponumber + "?\n\n");
            if (shortages + extras + prices == 0) {
                sb.append("No hay diferencias con el pedido.\n");
            }
            if (shortages > 0) {
                sb.append("• ").append(shortages).append(" producto(s) con faltante: se agregarán al siguiente pedido.\n");
            }
            if (extras > 0) {
                sb.append("• ").append(extras).append(" producto(s) con sobrante o que no venían en el pedido: entran al stock.\n");
            }
            if (prices > 0) {
                sb.append("• ").append(prices).append(" producto(s) con otro costo: el costo NO cambia hasta que se autorice.\n");
            }
            sb.append("\nSe generará el contrarecibo en PDF.");
            if (ask(sb.toString())) {
                save();
            }
        });
    }

    private void save() {
        String location = cmbLocation.getValue() == null ? null : locations.get(cmbLocation.getValue());
        if (location == null) {
            alert(AlertType.ERROR, "No hay ubicaciones dadas de alta en Chromis.");
            return;
        }
        PurchaseOrderStore.ReceiptResult result;
        try {
            result = PurchaseOrderStore.receive(connection(), order, location, siteGuid, Start.getActiveUserId(),
                    Start.getActiveUser(), txtInvoice.getText());
        } catch (SQLException ex) {
            showError("No se pudo registrar la recepción; no se cambió nada", ex);
            return;
        }
        String id = order.id;
        File pdf = writeReceipt();
        try {
            Connection con = connection();
            loadOpenOrders(con);
            updatePrices(con);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo actualizar la lista de pedidos", ex);
        }
        showOrder(id);
        StringBuilder sb = new StringBuilder("Pedido " + order.ponumber + " recibido: entraron "
                + PurchaseOrderStore.format(result.receivedUnits) + " unidades al stock.");
        if (result.shortages > 0) {
            sb.append(" ").append(result.shortages).append(" faltante(s) pasan al siguiente pedido.");
        }
        if (result.priceChanges > 0) {
            sb.append(" ").append(result.priceChanges).append(" cambio(s) de costo por autorizar.");
        }
        if (pdf != null) {
            sb.append(" Contrarecibo: ").append(pdf.getAbsolutePath());
        }
        note(sb.toString());
        Platform.runLater(txtFolio::requestFocus);
    }

    private File writeReceipt() {
        File folder = RestockController.pdfFolder();
        if (!folder.isDirectory()) {
            folder = new File(System.getProperty("user.home"));
        }
        File file = new File(folder, "Contrarecibo_" + order.ponumber + ".pdf");
        try {
            ReceiptPdf.write(file, order, Company.NAME, Start.getActiveUser());
        } catch (Exception ex) {
            showError("La recepción quedó registrada, pero no se pudo generar el contrarecibo", ex);
            return null;
        }
        RestockController.openFile(file, () -> { });
        return file;
    }

    @FXML
    public void reprintReceipt() {
        if (order == null || order.isOpen()) {
            return;
        }
        File file = writeReceipt();
        if (file != null) {
            note("Contrarecibo: " + file.getAbsolutePath());
        }
    }

    @FXML
    public void reprintOrder() {
        if (order == null) {
            return;
        }
        String location = cmbLocation.getValue() == null ? null : locations.get(cmbLocation.getValue());
        Map<String, RestockStore.Row> rows = new HashMap<>();
        try {
            List<String> ids = new ArrayList<>();
            for (Line l : order.lines) {
                if (!l.isExtra()) {
                    ids.add(l.productId);
                }
            }
            if (location != null) {
                for (RestockStore.Row r : RestockStore.load(connection(), location, ids)) {
                    rows.put(r.productId, r);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo leer el stock para el pedido", ex);
        }
        List<RestockPdf.Line> pdfLines = new ArrayList<>();
        for (Line l : order.lines) {
            if (l.isExtra()) {
                continue;
            }
            RestockPdf.Line p = new RestockPdf.Line();
            p.reference = l.reference;
            p.name = l.name;
            p.qty = l.ordered;
            p.cost = l.orderCost;
            RestockStore.Row r = rows.get(l.productId);
            if (r != null) {
                p.current = r.current;
                p.minimum = r.minimum;
                p.maximum = r.maximum;
                p.status = r.status();
            }
            pdfLines.add(p);
        }
        File folder = RestockController.pdfFolder();
        File file = new File(folder.isDirectory() ? folder : new File(System.getProperty("user.home")),
                "Pedido_" + order.ponumber + ".pdf");
        try {
            RestockPdf.write(file, pdfLines, Company.NAME, locations.size() > 1 ? cmbLocation.getValue() : null,
                    order.user, order.ponumber, order.supplierName);
        } catch (Exception ex) {
            showError("No se pudo generar el PDF del pedido", ex);
            return;
        }
        note("Pedido: " + file.getAbsolutePath());
        RestockController.openFile(file, () -> { });
    }

    // ---------------- Dialogs ----------------

    private void note(String text) {
        lblMessage.setStyle("-fx-text-fill: #1b5e20;");
        lblMessage.setText(text);
    }

    private void warn(String text) {
        lblMessage.setStyle("");
        lblMessage.setText(text);
    }

    private boolean ask(String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle("Recepción de mercancía");
        alert.getDialogPane().setPrefWidth(560);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        if (apAnchor.getScene() != null) {
            alert.initOwner(apAnchor.getScene().getWindow());
        }
        Optional<ButtonType> r = alert.showAndWait();
        return r.isPresent() && r.get() == ButtonType.YES;
    }

    private void showError(String message, Throwable ex) {
        LOGGER.log(Level.WARNING, message, ex);
        alert(AlertType.ERROR, message + ":\n" + (ex == null ? "" : ex.getMessage()));
    }

    private void alert(AlertType type, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message);
            alert.setHeaderText(null);
            alert.getDialogPane().setPrefWidth(560);
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.setTitle("Recepción de mercancía");
            if (apAnchor.getScene() != null) {
                alert.initOwner(apAnchor.getScene().getWindow());
            }
            alert.showAndWait();
        });
    }
}
