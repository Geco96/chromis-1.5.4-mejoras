package uk.chromis.administration.restock;

import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import uk.chromis.administration.DatabaseLogic;
import uk.chromis.administration.main.Start;
import uk.chromis.administration.receiving.NumberCell;
import uk.chromis.administration.receiving.PurchaseOrderStore;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.globals.Company;
import uk.chromis.pos.restock.DraftOrders;
import uk.chromis.pos.restock.RestockStore;
import uk.chromis.pos.restock.RestockStore.Status;
import uk.chromis.pos.settings.PosSettings;

/**
 * Daily draft order: when it is built, and the review of each draft (one per
 * supplier) before it is sent. Sending turns the draft into a normal supplier
 * order (folio PED-…) with its PDF, exactly as Reposición → Generar PDF.
 */
public class DraftOrderController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(DraftOrderController.class.getName());
    private static final SimpleDateFormat WHEN = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    @FXML
    public AnchorPane apAnchor;
    @FXML
    public CheckBox chkEnabled;
    @FXML
    public TextField txtTime;
    @FXML
    public ComboBox<String> cmbTerminal;
    @FXML
    public Label lblLastRun;
    @FXML
    public ComboBox<DraftOrders.Draft> cmbDraft;
    @FXML
    public TableView<DraftOrders.Line> table;
    @FXML
    public TableColumn<DraftOrders.Line, String> colReference;
    @FXML
    public TableColumn<DraftOrders.Line, String> colName;
    @FXML
    public TableColumn<DraftOrders.Line, Number> colCurrent;
    @FXML
    public TableColumn<DraftOrders.Line, Number> colMinimum;
    @FXML
    public TableColumn<DraftOrders.Line, Number> colMaximum;
    @FXML
    public TableColumn<DraftOrders.Line, Status> colStatus;
    @FXML
    public TableColumn<DraftOrders.Line, String> colOnOrder;
    @FXML
    public TableColumn<DraftOrders.Line, String> colCarry;
    @FXML
    public TableColumn<DraftOrders.Line, Number> colSuggested;
    @FXML
    public TableColumn<DraftOrders.Line, Number> colQty;
    @FXML
    public TableColumn<DraftOrders.Line, DraftOrders.Line> colRemove;
    @FXML
    public TextField txtAdd;
    @FXML
    public Label lblSummary;
    @FXML
    public Button btnDiscard;
    @FXML
    public Button btnSend;
    @FXML
    public Label lblMessage;

    private final ObservableList<DraftOrders.Line> lines = FXCollections.observableArrayList();
    private final Map<String, Double> costs = new HashMap<>();
    private String siteGuid;
    private boolean manyLocations;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        siteGuid = DatabaseLogic.getInstance().getSiteGUID();
        buildTable();
        cmbDraft.setCellFactory(l -> new DraftCell());
        cmbDraft.setButtonCell(new DraftCell());
        cmbDraft.valueProperty().addListener((obs, o, n) -> showDraft(n));
        reloadData();
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }

    public void reloadData() {
        lblMessage.setText("");
        try {
            Connection con = connection();
            DraftOrders.createTables(con);
            PurchaseOrderStore.createTables(con);
            chkEnabled.setSelected("1".equals(PosSettings.get(con, DraftOrders.ENABLED, "1")));
            LocalTime at = DraftOrders.parseTime(PosSettings.get(con, DraftOrders.TIME, null));
            txtTime.setText(DraftOrders.formatTime(at == null ? DraftOrders.DEFAULT_TIME : at));
            manyLocations = countLocations(con) > 1;
            cmbTerminal.getItems().clear();
            try (Statement st = con.createStatement();
                    ResultSet rs = st.executeQuery("SELECT terminal_name FROM terminals ORDER BY terminal_name")) {
                while (rs.next()) {
                    cmbTerminal.getItems().add(rs.getString(1));
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "No se pudo leer la lista de terminales", ex);
            }
            cmbTerminal.getEditor().setText(PosSettings.get(con, DraftOrders.TERMINAL, ""));
            String status = PosSettings.get(con, DraftOrders.LAST_STATUS, "");
            lblLastRun.setText(status.isEmpty() ? "Todavía no se ha armado ninguno." : "Último: " + status);
        } catch (SQLException ex) {
            showError("No se pudo leer la configuración del pedido tentativo", ex);
        }
        loadDrafts(null);
    }

    private void loadDrafts(String keepId) {
        String selected = keepId != null ? keepId : cmbDraft.getValue() == null ? null : cmbDraft.getValue().id;
        List<DraftOrders.Draft> drafts;
        try {
            drafts = DraftOrders.loadOpen(connection());
            loadCosts(drafts);
        } catch (SQLException ex) {
            showError("No se pudieron leer los pedidos tentativos", ex);
            drafts = new ArrayList<>();
        }
        cmbDraft.getItems().setAll(drafts);
        DraftOrders.Draft pick = drafts.isEmpty() ? null : drafts.get(0);
        for (DraftOrders.Draft d : drafts) {
            if (d.id.equals(selected)) {
                pick = d;
            }
        }
        cmbDraft.setValue(pick);
        showDraft(pick);
    }

    private void loadCosts(List<DraftOrders.Draft> drafts) throws SQLException {
        costs.clear();
        try (PreparedStatement ps = connection().prepareStatement("SELECT pricebuy FROM products WHERE id = ?")) {
            for (DraftOrders.Draft d : drafts) {
                for (DraftOrders.Line l : d.lines) {
                    if (!costs.containsKey(l.productId)) {
                        ps.setString(1, l.productId);
                        try (ResultSet rs = ps.executeQuery()) {
                            costs.put(l.productId, rs.next() ? rs.getDouble(1) : 0.0);
                        }
                    }
                }
            }
        }
    }

    private void showDraft(DraftOrders.Draft d) {
        lines.setAll(d == null ? new ArrayList<>() : d.lines);
        boolean none = d == null;
        btnSend.setDisable(none);
        btnDiscard.setDisable(none);
        txtAdd.setDisable(none);
        table.setPlaceholder(new Label(cmbDraft.getItems().isEmpty()
                ? "No hay pedido tentativo pendiente. Se arma solo a la hora indicada, o con \"Volver a armar ahora\"."
                : "Este pedido no tiene productos."));
        updateSummary();
    }

    private void updateSummary() {
        int products = 0;
        double units = 0;
        double total = 0;
        for (DraftOrders.Line l : lines) {
            if (l.qty > 0) {
                products++;
                units += l.qty;
                total += l.qty * costs.getOrDefault(l.productId, 0.0);
            }
        }
        lblSummary.setText(products + (products == 1 ? " producto · " : " productos · ") + RestockItem.format(units)
                + " unidades · costo estimado " + PurchaseOrderStore.money(total));
    }

    // ---------------- Table ----------------
    private void buildTable() {
        table.setItems(lines);
        colReference.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getReference()));
        colName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getName()));
        colCurrent.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().row.current));
        colCurrent.setCellFactory(c -> numberText());
        colMinimum.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().row.minimum));
        colMinimum.setCellFactory(c -> numberText());
        colMaximum.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().row.maximum));
        colMaximum.setCellFactory(c -> numberText());
        colSuggested.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().suggested));
        colSuggested.setCellFactory(c -> numberText());
        colOnOrder.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                c.getValue().onOrder > 0 ? RestockItem.format(c.getValue().onOrder) : ""));
        colCarry.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().carry > 0
                ? RestockItem.format(c.getValue().carry)
                + (c.getValue().carryFrom == null ? "" : " (" + c.getValue().carryFrom + ")") : ""));
        colCarry.setCellFactory(c -> new TableCell<DraftOrders.Line, String>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setStyle(empty || value == null || value.isEmpty() ? "" : "-fx-text-fill: #b00020; -fx-font-weight: bold;");
            }
        });
        colStatus.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getStatus()));
        colStatus.setCellFactory(c -> new TableCell<DraftOrders.Line, Status>() {
            @Override
            protected void updateItem(Status status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(status.getLabel());
                setAlignment(Pos.CENTER);
                setStyle("-fx-font-weight: bold; -fx-background-color: " + color(status) + ";");
            }
        });
        colQty.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().qty));
        colQty.setCellFactory(c -> new NumberCell<>(l -> l.qty, this::saveQuantity, RestockItem::format,
                l -> true, this::error));
        colRemove.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colRemove.setCellFactory(c -> new TableCell<DraftOrders.Line, DraftOrders.Line>() {
            private final Button button = new Button("Quitar");

            {
                button.setOnAction(e -> {
                    DraftOrders.Line l = getItem();
                    if (l != null) {
                        removeLine(l);
                    }
                });
            }

            @Override
            protected void updateItem(DraftOrders.Line item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty || item == null ? null : button);
            }
        });
    }

    private static TableCell<DraftOrders.Line, Number> numberText() {
        return new TableCell<DraftOrders.Line, Number>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : RestockItem.format(value.doubleValue()));
            }
        };
    }

    private static String color(Status status) {
        switch (status) {
            case AGOTADO:
                return "#d9d9d9";
            case BAJO:
                return "#ffcccc";
            case RESERVA:
                return "#ffffcc";
            case ACEPTABLE:
                return "#ccffcc";
            default:
                return "transparent";
        }
    }

    // ---------------- Editing ----------------
    private DraftOrders.Draft current() {
        return cmbDraft.getValue();
    }

    /** The draft is still open; otherwise reloads and says why. */
    private boolean stillOpen(DraftOrders.Draft d) throws SQLException {
        if (DraftOrders.isOpen(connection(), d.id)) {
            return true;
        }
        alert(AlertType.WARNING, "Este pedido tentativo ya se envió, se descartó o se reemplazó por uno más nuevo.");
        loadDrafts(null);
        return false;
    }

    private boolean saveQuantity(DraftOrders.Line line, double qty) {
        DraftOrders.Draft d = current();
        if (d == null) {
            return false;
        }
        try {
            if (!stillOpen(d)) {
                return false;
            }
            DraftOrders.setQuantity(connection(), d.id, line.productId, qty);
        } catch (SQLException ex) {
            showError("No se pudo guardar la cantidad", ex);
            return false;
        }
        line.qty = qty;
        lblMessage.setText("");
        updateSummary();
        return true;
    }

    private void removeLine(DraftOrders.Line line) {
        DraftOrders.Draft d = current();
        if (d == null) {
            return;
        }
        try {
            if (!stillOpen(d)) {
                return;
            }
            DraftOrders.removeLine(connection(), d.id, line.productId);
        } catch (SQLException ex) {
            showError("No se pudo quitar el producto", ex);
            return;
        }
        d.lines.remove(line);
        lines.remove(line);
        updateSummary();
    }

    @FXML
    public void addProduct() {
        DraftOrders.Draft d = current();
        String code = txtAdd.getText() == null ? "" : txtAdd.getText().trim();
        if (d == null || code.isEmpty()) {
            return;
        }
        try {
            Connection con = connection();
            if (!stillOpen(d)) {
                return;
            }
            String productId = PurchaseOrderStore.findProduct(con, code);
            if (productId == null) {
                error("No hay ningún producto con el código o referencia \"" + code + "\".");
                return;
            }
            for (DraftOrders.Line l : d.lines) {
                if (l.productId.equals(productId)) {
                    error(l.getName() + " ya está en el pedido: cambia su cantidad en la columna Pedir.");
                    txtAdd.clear();
                    return;
                }
            }
            List<RestockStore.Row> rows = RestockStore.load(con, d.location, List.of(productId));
            if (rows.isEmpty()) {
                error("Ese producto no tiene \"Controlar stock\"; actívalo en Productos para pedirlo aquí.");
                return;
            }
            RestockStore.Row r = rows.get(0);
            double qty = Math.max(r.suggested() - PurchaseOrderStore.onOrder(con).getOrDefault(productId, 0.0), 1);
            DraftOrders.setQuantity(con, d.id, productId, qty);
            txtAdd.clear();
            info(r.name + " agregado con " + RestockItem.format(qty) + ".");
            loadDrafts(d.id);
        } catch (SQLException ex) {
            showError("No se pudo agregar el producto", ex);
        }
    }

    // ---------------- Settings and generation ----------------
    @FXML
    public void saveSettings() {
        LocalTime at = DraftOrders.parseTime(txtTime.getText());
        if (at == null) {
            alert(AlertType.WARNING, "Escribe la hora en formato de 24 horas, por ejemplo 16:30.");
            return;
        }
        String terminal = cmbTerminal.getEditor().getText() == null ? "" : cmbTerminal.getEditor().getText().trim();
        try {
            Connection con = connection();
            PosSettings.put(con, DraftOrders.ENABLED, chkEnabled.isSelected() ? "1" : "0");
            PosSettings.put(con, DraftOrders.TIME, DraftOrders.formatTime(at));
            PosSettings.put(con, DraftOrders.TERMINAL, terminal);
            txtTime.setText(DraftOrders.formatTime(at));
            info(chkEnabled.isSelected()
                    ? "Guardado. Se armará todos los días a las " + DraftOrders.formatTime(at)
                    + (terminal.isEmpty() ? " en la primera caja abierta." : " en la caja " + terminal + ".")
                    : "Guardado. El pedido tentativo diario está apagado.");
        } catch (SQLException ex) {
            showError("No se pudo guardar la configuración", ex);
        }
    }

    @FXML
    public void refresh() {
        reloadData();
    }

    @FXML
    public void generateNow() {
        int open = cmbDraft.getItems().size();
        if (open > 0 && !confirm("Se reemplazarán los " + open + (open == 1 ? " pedido tentativo" : " pedidos tentativos")
                + " sin enviar, con los cambios que les hayas hecho, por uno nuevo con el stock de este momento.\n\n¿Continuar?")) {
            return;
        }
        try {
            Connection con = connection();
            DraftOrders.Result r = DraftOrders.generate(con, siteGuid);
            PosSettings.put(con, DraftOrders.LAST_STATUS, new SimpleDateFormat("dd/MM HH:mm").format(new Date())
                    + " · a mano en Admin (" + Start.getActiveUser() + ") · " + r.describe());
        } catch (SQLException ex) {
            showError("No se pudo armar el pedido tentativo", ex);
            return;
        }
        reloadData();
    }

    // ---------------- Discard and send ----------------
    @FXML
    public void discard() {
        DraftOrders.Draft d = current();
        if (d == null || !confirm("¿Descartar el pedido tentativo de " + d.supplierName
                + "? No se pedirá nada; mañana se arma uno nuevo.")) {
            return;
        }
        try {
            if (!DraftOrders.discard(connection(), d.id, Start.getActiveUser())) {
                alert(AlertType.WARNING, "Este pedido tentativo ya se había enviado, descartado o reemplazado.");
            }
        } catch (SQLException ex) {
            showError("No se pudo descartar", ex);
        }
        loadDrafts(null);
    }

    @FXML
    public void send() {
        table.requestFocus(); // commits a cell that is still being typed
        DraftOrders.Draft d = current();
        if (d == null) {
            return;
        }
        List<DraftOrders.Line> toOrder = new ArrayList<>();
        double units = 0;
        double total = 0;
        for (DraftOrders.Line l : d.lines) {
            if (l.qty > 0) {
                toOrder.add(l);
                units += l.qty;
                total += l.qty * costs.getOrDefault(l.productId, 0.0);
            }
        }
        if (toOrder.isEmpty()) {
            alert(AlertType.INFORMATION, "No hay ningún producto con cantidad a pedir mayor que 0.");
            return;
        }
        try {
            if (!stillOpen(d)) {
                return;
            }
        } catch (SQLException ex) {
            showError("No se pudo revisar el pedido", ex);
            return;
        }
        // Ordered from Reposición (or another draft) after this draft was built
        List<String> alreadyOrdered = new ArrayList<>();
        try {
            Map<String, Double> onOrder = PurchaseOrderStore.onOrder(connection());
            for (DraftOrders.Line l : toOrder) {
                if (onOrder.getOrDefault(l.productId, 0.0) > 0) {
                    alreadyOrdered.add(l.getName() + " (" + RestockItem.format(onOrder.get(l.productId)) + " en camino)");
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo revisar lo que ya está en camino", ex);
        }
        String warning = alreadyOrdered.isEmpty() ? "" : "\n\nOjo, estos ya tienen un pedido en camino:\n"
                + String.join("\n", alreadyOrdered.subList(0, Math.min(alreadyOrdered.size(), 6)))
                + (alreadyOrdered.size() > 6 ? "\ny " + (alreadyOrdered.size() - 6) + " más" : "");
        if (!confirm("¿Enviar el pedido a " + d.supplierName + "?\n\n"
                + toOrder.size() + (toOrder.size() == 1 ? " producto, " : " productos, ") + RestockItem.format(units)
                + " unidades.\nCosto estimado: " + PurchaseOrderStore.money(total) + "\n\n"
                + "Se guardará como pedido de Chromis con su folio y se generará el PDF para mandarlo al proveedor."
                + warning)) {
            return;
        }
        File file = choosePdf();
        if (file == null) {
            return;
        }
        PurchaseOrderStore.Order order;
        try {
            Connection con = connection();
            List<PurchaseOrderStore.NewLine> newLines = new ArrayList<>();
            for (DraftOrders.Line l : toOrder) {
                newLines.add(new PurchaseOrderStore.NewLine(l.productId, l.qty));
            }
            order = PurchaseOrderStore.createOrder(con, siteGuid, d.supplierId, Start.getActiveUserId(), newLines);
            if (!DraftOrders.markSent(con, d.id, Start.getActiveUser(), order.id, order.ponumber)) {
                LOGGER.warning("El pedido tentativo " + d.id + " cambió mientras se enviaba como " + order.ponumber);
            }
        } catch (SQLException ex) {
            showError("No se pudo guardar el pedido", ex);
            return;
        }
        Map<String, Double> orderCosts = new HashMap<>();
        for (PurchaseOrderStore.Line l : order.lines) {
            orderCosts.put(l.productId, l.orderCost);
        }
        List<RestockPdf.Line> pdfLines = new ArrayList<>();
        for (DraftOrders.Line l : toOrder) {
            RestockPdf.Line p = new RestockPdf.Line();
            p.reference = l.getReference();
            p.name = l.getName();
            p.current = l.row.current;
            p.minimum = l.row.minimum;
            p.maximum = l.row.maximum;
            p.qty = l.qty;
            p.status = l.getStatus();
            p.cost = orderCosts.getOrDefault(l.productId, 0.0);
            if (l.carry > 0 && l.carryFrom != null) {
                p.note = "Incluye " + RestockItem.format(l.carry) + " que faltaron en " + l.carryFrom;
            }
            pdfLines.add(p);
        }
        try {
            String location = manyLocations ? d.locationName : null;
            RestockPdf.write(file, pdfLines, Company.NAME, location, Start.getActiveUser(), order.ponumber, d.supplierName);
            RestockController.rememberPdfFolder(file);
        } catch (Exception ex) {
            showError("El pedido " + order.ponumber + " quedó guardado, pero no se pudo generar el PDF. "
                    + "Puedes reimprimirlo desde Recepción de mercancía", ex);
            loadDrafts(null);
            return;
        }
        loadDrafts(null);
        info("Pedido " + order.ponumber + " enviado a " + d.supplierName + " (" + order.lines.size()
                + " productos). Se recibe en Recepción de mercancía.");
        RestockController.openFile(file, () -> alert(AlertType.INFORMATION, "Pedido guardado en:\n" + file.getAbsolutePath()));
    }

    private File choosePdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar pedido");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName("Pedido_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf");
        File folder = RestockController.pdfFolder();
        if (folder.isDirectory()) {
            chooser.setInitialDirectory(folder);
        }
        File file = chooser.showSaveDialog(apAnchor.getScene().getWindow());
        if (file != null && !file.getName().toLowerCase().endsWith(".pdf")) {
            file = new File(file.getParentFile(), file.getName() + ".pdf");
        }
        return file;
    }

    private static int countLocations(Connection con) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM locations")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ---------------- Helpers ----------------
    private static final Color ERROR = Color.web("#b00020");
    private static final Color OK = Color.web("#1b5e20");

    private void info(String message) {
        lblMessage.setTextFill(OK);
        lblMessage.setText(message);
    }

    private void error(String message) {
        lblMessage.setTextFill(ERROR);
        lblMessage.setText(message);
    }

    /** Shows each draft as "Supplier — N productos · built on". */
    private final class DraftCell extends ListCell<DraftOrders.Draft> {

        @Override
        protected void updateItem(DraftOrders.Draft d, boolean empty) {
            super.updateItem(d, empty);
            if (empty || d == null) {
                setText(cmbDraft.getItems().isEmpty() ? "No hay pedido tentativo pendiente" : null);
                return;
            }
            int n = d.productCount();
            setText(d.supplierName + " — " + n + (n == 1 ? " producto" : " productos")
                    + (manyLocations ? " · " + d.locationName : "")
                    + " · armado el " + (d.created == null ? "" : WHEN.format(d.created)));
        }
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        alert.setTitle("Pedido tentativo");
        alert.getDialogPane().setPrefWidth(560);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        if (apAnchor.getScene() != null) {
            alert.initOwner(apAnchor.getScene().getWindow());
        }
        Optional<ButtonType> r = alert.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
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
            alert.setTitle("Pedido tentativo");
            if (apAnchor.getScene() != null) {
                alert.initOwner(apAnchor.getScene().getWindow());
            }
            alert.showAndWait();
        });
    }
}
