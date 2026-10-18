package uk.chromis.administration.restock;

import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import uk.chromis.administration.DatabaseLogic;
import uk.chromis.administration.main.Start;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.globals.Company;
import uk.chromis.pos.restock.RestockStore;
import uk.chromis.pos.restock.RestockStore.Status;
import uk.chromis.pos.settings.PosSettings;

/**
 * Restock screen: stock against minimum / reserve / maximum, thresholds
 * edited in place, and the order PDF of the selected products.
 */
public class RestockController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(RestockController.class.getName());
    private static final Preferences PREFS = Preferences.userNodeForPackage(RestockController.class);

    @FXML
    public AnchorPane apAnchor;
    @FXML
    public TextField txtSearch;
    @FXML
    public CheckBox chkOut;
    @FXML
    public CheckBox chkLow;
    @FXML
    public CheckBox chkReserve;
    @FXML
    public CheckBox chkNormal;
    @FXML
    public CheckBox chkNoThreshold;
    @FXML
    public ComboBox<String> cmbLocation;
    @FXML
    public Button btnRefresh;
    @FXML
    public Button btnPdf;
    @FXML
    public TableView<RestockItem> table;
    @FXML
    public TableColumn<RestockItem, Boolean> colSelected;
    @FXML
    public TableColumn<RestockItem, String> colReference;
    @FXML
    public TableColumn<RestockItem, String> colName;
    @FXML
    public TableColumn<RestockItem, Number> colCurrent;
    @FXML
    public TableColumn<RestockItem, Number> colMinimum;
    @FXML
    public TableColumn<RestockItem, Number> colReserve;
    @FXML
    public TableColumn<RestockItem, Number> colMaximum;
    @FXML
    public TableColumn<RestockItem, Status> colStatus;
    @FXML
    public TableColumn<RestockItem, Number> colOrder;
    @FXML
    public Label lblSummary;
    @FXML
    public CheckBox chkPosAlerts;
    @FXML
    public CheckBox chkAlertReserve;
    @FXML
    public Label lblMessage;
    @FXML
    public CheckBox chkCatalogStock;
    @FXML
    public ColorPicker cpStockColor;

    private final ObservableList<RestockItem> items = FXCollections.observableArrayList();
    private final FilteredList<RestockItem> filtered = new FilteredList<>(items);
    private final Map<String, String> locations = new LinkedHashMap<>();
    private String siteGuid;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        siteGuid = DatabaseLogic.getInstance().getSiteGUID();
        buildTable();
        txtSearch.textProperty().addListener((obs, o, n) -> applyFilter());
        for (CheckBox chk : new CheckBox[]{chkOut, chkLow, chkReserve, chkNormal, chkNoThreshold}) {
            chk.selectedProperty().addListener((obs, o, n) -> applyFilter());
        }
        cmbLocation.valueProperty().addListener((obs, o, n) -> {
            if (o != null && n != null && !n.equals(o)) {
                loadItems();
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
            RestockStore.createTables(con);
            loadLocations(con);
            RestockStore.Settings s = RestockStore.loadSettings(con);
            chkPosAlerts.setSelected(s.posAlerts);
            chkAlertReserve.setSelected(s.alertReserve);
            chkAlertReserve.setDisable(!s.posAlerts);
            chkCatalogStock.setSelected("1".equals(PosSettings.get(con, PosSettings.CATALOG_STOCK, "0")));
            cpStockColor.setValue(Color.web(PosSettings.normalizeColor(
                    PosSettings.get(con, PosSettings.CATALOG_STOCK_COLOR, PosSettings.DEFAULT_STOCK_COLOR))));
            cpStockColor.setDisable(!chkCatalogStock.isSelected());
        } catch (SQLException ex) {
            showError("No se pudo leer la configuración de reposición", ex);
        }
        loadItems();
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
        // Most branches have a single location: then there is nothing to choose
        cmbLocation.setVisible(locations.size() > 1);
        cmbLocation.setManaged(locations.size() > 1);
    }

    private String locationId() {
        return cmbLocation.getValue() == null ? null : locations.get(cmbLocation.getValue());
    }

    private void loadItems() {
        lblMessage.setText("");
        String location = locationId();
        if (location == null) {
            items.clear();
            lblMessage.setText("No hay ubicaciones dadas de alta en Chromis.");
            return;
        }
        try {
            List<RestockItem> loaded = new ArrayList<>();
            for (RestockStore.Row r : RestockStore.load(connection(), location, null)) {
                RestockItem item = new RestockItem(r);
                item.selectedProperty().addListener((obs, o, n) -> updateSummary());
                loaded.add(item);
            }
            loaded.sort(Comparator.comparing(RestockItem::getStatus)
                    .thenComparing(i -> i.getName() == null ? "" : i.getName().toLowerCase()));
            items.setAll(loaded);
            // First time (nothing configured yet): show every product so thresholds can be entered
            if (loaded.stream().noneMatch(i -> i.getRow().hasThresholds())) {
                chkNoThreshold.setSelected(true);
            }
        } catch (SQLException ex) {
            showError("No se pudo leer el stock", ex);
        }
        applyFilter();
    }

    private void applyFilter() {
        String text = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        filtered.setPredicate(i -> {
            if (!text.isEmpty()) {
                // Searching finds the product whatever its status, to set its thresholds
                return (i.getName() != null && i.getName().toLowerCase().contains(text))
                        || (i.getReference() != null && i.getReference().toLowerCase().contains(text));
            }
            switch (i.getStatus()) {
                case AGOTADO:
                    if (!chkOut.isSelected()) {
                        return false;
                    }
                    break;
                case BAJO:
                    if (!chkLow.isSelected()) {
                        return false;
                    }
                    break;
                case RESERVA:
                    if (!chkReserve.isSelected()) {
                        return false;
                    }
                    break;
                case ACEPTABLE:
                    if (!chkNormal.isSelected()) {
                        return false;
                    }
                    break;
                default:
                    if (!chkNoThreshold.isSelected()) {
                        return false;
                    }
            }
            return true;
        });
        updateSummary();
    }

    private void updateSummary() {
        int out = 0;
        int low = 0;
        int reserve = 0;
        int selected = 0;
        for (RestockItem i : items) {
            switch (i.getStatus()) {
                case AGOTADO:
                    out++;
                    break;
                case BAJO:
                    low++;
                    break;
                case RESERVA:
                    reserve++;
                    break;
                default:
                    break;
            }
            if (i.selectedProperty().get()) {
                selected++;
            }
        }
        lblSummary.setText(out + " agotados · " + low + " bajos · " + reserve + " en reserva · "
                + selected + " marcados para el pedido");
    }

    // ---------------- Table ----------------
    private void buildTable() {
        SortedList<RestockItem> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setPlaceholder(new Label("No hay productos que mostrar. Revisa los filtros; solo aparecen productos con \"Controlar stock\"."));

        colSelected.setCellValueFactory(c -> c.getValue().selectedProperty());
        colSelected.setCellFactory(CheckBoxTableCell.forTableColumn(colSelected));
        colSelected.setEditable(true);
        colReference.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getReference()));
        colName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getName()));
        colCurrent.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getRow().current));
        colCurrent.setCellFactory(c -> new TableCell<RestockItem, Number>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : RestockItem.format(value.doubleValue()));
            }
        });

        colMinimum.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getRow().minimum));
        colMinimum.setCellFactory(c -> new NumberCell(i -> i.getRow().minimum, (i, v) -> saveThreshold(i, v, 0)));
        colReserve.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getRow().reserve));
        colReserve.setCellFactory(c -> new NumberCell(i -> i.getRow().reserve, (i, v) -> saveThreshold(i, v, 1)));
        colMaximum.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().getRow().maximum));
        colMaximum.setCellFactory(c -> new NumberCell(i -> i.getRow().maximum, (i, v) -> saveThreshold(i, v, 2)));
        colOrder.setCellValueFactory(c -> c.getValue().orderProperty());
        colOrder.setCellFactory(c -> new NumberCell(i -> i.orderProperty().get(), (i, v) -> {
            i.orderProperty().set(v);
            return true;
        }));

        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        colStatus.setCellFactory(c -> new TableCell<RestockItem, Status>() {
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

    /** which: 0 minimum, 1 reserve, 2 maximum. */
    private boolean saveThreshold(RestockItem item, double value, int which) {
        RestockStore.Row r = item.getRow();
        double min = which == 0 ? value : r.minimum;
        double reserve = which == 1 ? value : r.reserve;
        double max = which == 2 ? value : r.maximum;
        String problem = RestockStore.validate(min, reserve, max);
        if (problem != null) {
            lblMessage.setText(item.getName() + ": " + problem);
            return false;
        }
        try {
            RestockStore.saveThresholds(connection(), locationId(), siteGuid, r.productId, min, reserve, max);
        } catch (SQLException ex) {
            showError("No se pudieron guardar los umbrales", ex);
            return false;
        }
        double oldSuggested = r.suggested();
        r.minimum = min;
        r.reserve = reserve;
        r.maximum = max;
        item.refresh();
        // Keep a quantity typed by hand; otherwise follow the new maximum
        if (item.orderProperty().get() == oldSuggested) {
            item.orderProperty().set(r.suggested());
        }
        lblMessage.setText("");
        table.refresh();
        updateSummary();
        return true;
    }

    /** Text field that is always visible and commits on Enter or when it loses the focus. */
    private final class NumberCell extends TableCell<RestockItem, Number> {

        private final TextField field = new TextField();
        private final Function<RestockItem, Double> getter;
        private final BiFunction<RestockItem, Double, Boolean> setter;
        private RestockItem editing;

        NumberCell(Function<RestockItem, Double> getter, BiFunction<RestockItem, Double, Boolean> setter) {
            this.getter = getter;
            this.setter = setter;
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

        private RestockItem currentItem() {
            return getTableRow() == null ? null : getTableRow().getItem();
        }

        private void commit() {
            RestockItem item = editing != null ? editing : currentItem();
            if (item == null) {
                return;
            }
            Double value = RestockItem.parse(field.getText());
            double old = getter.apply(item);
            if (value == null) {
                lblMessage.setText("\"" + field.getText() + "\" no es un número.");
            } else if (value != old && !setter.apply(item, value)) {
                value = null;
            }
            if (item == currentItem()) {
                field.setText(RestockItem.format(value == null ? old : value));
            }
            if (!field.isFocused()) {
                editing = null;
            }
            updateSummary();
        }

        @Override
        protected void updateItem(Number value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || value == null) {
                setGraphic(null);
            } else {
                if (!field.isFocused()) {
                    field.setText(RestockItem.format(value.doubleValue()));
                }
                setGraphic(field);
            }
        }
    }

    // ---------------- Actions ----------------
    @FXML
    public void refresh() {
        reloadData();
    }

    @FXML
    public void selectAttention() {
        for (RestockItem i : items) {
            i.selectedProperty().set(i.needsAttention());
        }
        updateSummary();
    }

    @FXML
    public void selectNone() {
        for (RestockItem i : items) {
            i.selectedProperty().set(false);
        }
        updateSummary();
    }

    @FXML
    public void saveSettings() {
        RestockStore.Settings s = new RestockStore.Settings();
        s.posAlerts = chkPosAlerts.isSelected();
        s.alertReserve = chkAlertReserve.isSelected();
        chkAlertReserve.setDisable(!s.posAlerts);
        try {
            RestockStore.saveSettings(connection(), s);
            lblMessage.setText("");
        } catch (SQLException ex) {
            showError("No se pudo guardar la configuración de avisos", ex);
        }
    }

    @FXML
    public void saveCatalogStock() {
        Color c = cpStockColor.getValue() == null ? Color.web(PosSettings.DEFAULT_STOCK_COLOR) : cpStockColor.getValue();
        String hex = String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
        cpStockColor.setDisable(!chkCatalogStock.isSelected());
        try {
            Connection con = connection();
            PosSettings.put(con, PosSettings.CATALOG_STOCK, chkCatalogStock.isSelected() ? "1" : "0");
            PosSettings.put(con, PosSettings.CATALOG_STOCK_COLOR, hex);
            lblMessage.setText("");
        } catch (SQLException ex) {
            showError("No se pudo guardar la configuración del catálogo", ex);
        }
    }

    @FXML
    public void generatePdf() {
        table.requestFocus(); // commits a cell that is still being typed
        List<RestockItem> selected = new ArrayList<>();
        for (RestockItem i : items) {
            if (i.selectedProperty().get()) {
                selected.add(i);
            }
        }
        updateSummary();
        if (selected.isEmpty()) {
            alert(AlertType.INFORMATION, "Marca en la primera columna los productos que quieres pedir.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar pedido de reposición");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName("Pedido_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".pdf");
        File folder = new File(PREFS.get("pdfFolder", System.getProperty("user.home")));
        if (folder.isDirectory()) {
            chooser.setInitialDirectory(folder);
        }
        File file = chooser.showSaveDialog(apAnchor.getScene().getWindow());
        if (file == null) {
            return;
        }
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            file = new File(file.getParentFile(), file.getName() + ".pdf");
        }
        try {
            String location = locations.size() > 1 ? cmbLocation.getValue() : null;
            RestockPdf.write(file, selected, Company.NAME, location, Start.getActiveUser());
            if (file.getParentFile() != null) {
                PREFS.put("pdfFolder", file.getParentFile().getAbsolutePath());
            }
        } catch (Exception ex) {
            showError("No se pudo generar el PDF", ex);
            return;
        }
        openFile(file);
    }

    private void openFile(File file) {
        Thread t = new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(file);
                    return;
                }
            } catch (Exception ex) {
                LOGGER.log(Level.INFO, "No se pudo abrir el PDF", ex);
            }
            alert(AlertType.INFORMATION, "Pedido guardado en:\n" + file.getAbsolutePath());
        }, "abrir-pdf");
        t.setDaemon(true);
        t.start();
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
            alert.setTitle("Reposición");
            if (apAnchor.getScene() != null) {
                alert.initOwner(apAnchor.getScene().getWindow());
            }
            alert.showAndWait();
        });
    }
}
