package uk.chromis.administration.stockcount;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import uk.chromis.administration.DatabaseLogic;
import uk.chromis.administration.main.Start;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.globals.Company;
import uk.chromis.jasperviewer.JRViewerController;
import uk.chromis.pos.forms.LocalResource;
import uk.chromis.pos.settings.PosSettings;

/** Scheduled stock counts: plans, products, counts, reports and stock adjustment. */
public class StockCountController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(StockCountController.class.getName());
    private static final Preferences PREFS = Preferences.userNodeForPackage(StockCountController.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DecimalFormat QTY = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));

    @FXML public AnchorPane apAnchor;
    @FXML public TableView<StockCountStore.Plan> planTable;
    @FXML public TableColumn<StockCountStore.Plan, Integer> colPlanId;
    @FXML public TableColumn<StockCountStore.Plan, String> colPlanName;
    @FXML public TableColumn<StockCountStore.Plan, String> colPlanDate;
    @FXML public TableColumn<StockCountStore.Plan, String> colPlanStatus;
    @FXML public TableColumn<StockCountStore.Plan, String> colPlanProgress;
    @FXML public Button btnCancelPlan;
    @FXML public CheckBox chkRotation;
    @FXML public TextField txtRotationDays;
    @FXML public TextField txtRotationSize;
    @FXML public DatePicker dpRotationNext;
    @FXML public VBox detailPane;
    @FXML public TextField txtPlanName;
    @FXML public DatePicker dpPlanDate;
    @FXML public Button btnSavePlan;
    @FXML public Label lblPlanStatus;
    @FXML public TextField txtAdd;
    @FXML public Button btnAdd;
    @FXML public TextField txtCountCode;
    @FXML public TextField txtCountQty;
    @FXML public CheckBox chkSum;
    @FXML public TableView<StockCountStore.Line> lineTable;
    @FXML public TableColumn<StockCountStore.Line, String> colRef;
    @FXML public TableColumn<StockCountStore.Line, String> colCode;
    @FXML public TableColumn<StockCountStore.Line, String> colName;
    @FXML public TableColumn<StockCountStore.Line, String> colCategory;
    @FXML public TableColumn<StockCountStore.Line, String> colSystem;
    @FXML public TableColumn<StockCountStore.Line, Double> colCounted;
    @FXML public TableColumn<StockCountStore.Line, String> colDiff;
    @FXML public TableColumn<StockCountStore.Line, String> colDiffValue;
    @FXML public Label lblSummary;
    @FXML public Button btnSheet;
    @FXML public Button btnResult;
    @FXML public Button btnCustom;
    @FXML public Button btnRemove;
    @FXML public Button btnClose;
    @FXML public Label lblMessage;

    private final ObservableList<StockCountStore.Line> lines = FXCollections.observableArrayList();
    private String location;
    private String siteGuid;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        siteGuid = DatabaseLogic.getInstance().getSiteGUID();
        buildTables();
        onlyDigits(txtRotationDays);
        onlyDigits(txtRotationSize);
        planTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> showPlan(n));
        reloadData();
    }

    private static void onlyDigits(TextField field) {
        field.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d{0,5}")) {
                field.setText(o);
            }
        });
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }

    private static String user() {
        String u = Start.getActiveUser();
        return u == null ? "" : u;
    }

    public void reloadData() {
        lblMessage.setText("");
        Integer selected = selectedPlan() == null ? null : selectedPlan().id;
        try {
            Connection con = connection();
            StockCountStore.createTables(con);
            location = firstLocation(con);
            Integer created = StockCountStore.runRotationIfDue(con, location, user(), LocalDate.now());
            if (created != null) {
                selected = created;
                lblMessage.setText("Se creó el conteo rotativo #" + created + ".");
            }
            StockCountStore.Rotation r = StockCountStore.loadRotation(con);
            chkRotation.setSelected(r.enabled);
            txtRotationDays.setText(String.valueOf(r.days));
            txtRotationSize.setText(String.valueOf(r.size));
            dpRotationNext.setValue(r.next);
            planTable.getItems().setAll(StockCountStore.listPlans(con));
        } catch (SQLException ex) {
            showError("No se pudieron leer los conteos", ex);
        }
        StockCountStore.Plan toSelect = null;
        for (StockCountStore.Plan p : planTable.getItems()) {
            if (selected != null && p.id == selected) {
                toSelect = p;
            }
        }
        if (toSelect == null && !planTable.getItems().isEmpty()) {
            toSelect = planTable.getItems().get(0);
        }
        planTable.getSelectionModel().select(toSelect);
        showPlan(toSelect);
    }

    private static String firstLocation(Connection con) throws SQLException {
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT id FROM locations ORDER BY name LIMIT 1")) {
            return rs.next() ? rs.getString(1) : "0";
        }
    }

    private StockCountStore.Plan selectedPlan() {
        return planTable == null ? null : planTable.getSelectionModel().getSelectedItem();
    }

    // ---------------- Tables ----------------
    private void buildTables() {
        colPlanId.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().id));
        colPlanName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name));
        colPlanDate.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().date.format(DATE)));
        colPlanStatus.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(statusLabel(c.getValue().status)));
        colPlanProgress.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().counted + "/" + c.getValue().products));
        planTable.setPlaceholder(new Label("Sin conteos. Crea uno con \"Nuevo conteo\"."));

        colRef.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().reference));
        colCode.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().code));
        colName.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name));
        colCategory.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().category));
        colSystem.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(QTY.format(c.getValue().system())));
        colCounted.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().counted));
        colCounted.setCellFactory(c -> new CountCell());
        colDiff.setCellValueFactory(c -> {
            Double d = c.getValue().difference();
            return new ReadOnlyObjectWrapper<>(d == null ? "" : (d > 0 ? "+" : "") + QTY.format(d));
        });
        colDiffValue.setCellValueFactory(c -> {
            Double d = c.getValue().difference();
            return new ReadOnlyObjectWrapper<>(d == null ? "" : (d > 0 ? "+" : "") + MONEY.format(d * c.getValue().cost));
        });
        lineTable.setItems(lines);
        lineTable.setPlaceholder(new Label("Agrega productos escaneándolos o con \"Buscar y marcar...\"."));
        lineTable.setRowFactory(t -> new javafx.scene.control.TableRow<StockCountStore.Line>() {
            @Override
            protected void updateItem(StockCountStore.Line item, boolean empty) {
                super.updateItem(item, empty);
                Double d = empty || item == null ? null : item.difference();
                setStyle(d == null || d == 0 ? "" : d < 0 ? "-fx-background-color: #ffe0e0;" : "-fx-background-color: #e0f5e0;");
            }
        });
        lineTable.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                removeSelected();
            }
        });
    }

    private static String statusLabel(String status) {
        switch (status) {
            case StockCountStore.OPEN:
                return "Abierto";
            case StockCountStore.CLOSED:
                return "Cerrado";
            default:
                return "Cancelado";
        }
    }

    private void showPlan(StockCountStore.Plan plan) {
        boolean open = plan != null && plan.isOpen();
        detailPane.setDisable(plan == null);
        for (javafx.scene.Node n : new javafx.scene.Node[]{txtPlanName, dpPlanDate, btnSavePlan, txtAdd, btnAdd,
            txtCountCode, txtCountQty, chkSum, btnRemove, btnClose}) {
            n.setDisable(!open);
        }
        btnCancelPlan.setDisable(!open);
        lineTable.setEditable(open);
        if (plan == null) {
            lines.clear();
            txtPlanName.setText("");
            dpPlanDate.setValue(null);
            lblPlanStatus.setText("");
            lblSummary.setText("");
            return;
        }
        txtPlanName.setText(plan.name);
        dpPlanDate.setValue(plan.date);
        String status = "#" + plan.id + " · " + statusLabel(plan.status) + (plan.auto ? " · rotativo" : "");
        if (!plan.isOpen() && plan.closedAt != null) {
            status += " · " + plan.closedAt.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    + (plan.closedBy == null ? "" : " por " + plan.closedBy);
        }
        lblPlanStatus.setText(status);
        loadLines();
    }

    private void loadLines() {
        StockCountStore.Plan plan = selectedPlan();
        if (plan == null) {
            return;
        }
        try {
            lines.setAll(StockCountStore.lines(connection(), plan.id, plan.location));
        } catch (SQLException ex) {
            showError("No se pudieron leer los productos del conteo", ex);
        }
        updateSummary();
    }

    private void updateSummary() {
        int counted = 0;
        int withDiff = 0;
        double value = 0;
        for (StockCountStore.Line l : lines) {
            Double d = l.difference();
            if (l.counted != null) {
                counted++;
            }
            if (d != null && d != 0) {
                withDiff++;
                value += d * l.cost;
            }
        }
        lblSummary.setText(lines.size() + " productos · " + counted + " contados · " + (lines.size() - counted)
                + " sin contar · " + withDiff + " con diferencia · diferencia a costo " + (value > 0 ? "+" : "") + MONEY.format(value));
        StockCountStore.Plan plan = selectedPlan();
        if (plan != null) {
            plan.counted = counted;
            plan.products = lines.size();
            planTable.refresh();
        }
    }

    /** Editable count: Enter or leaving the cell saves it; empty clears it. */
    private final class CountCell extends TableCell<StockCountStore.Line, Double> {

        private final TextField field = new TextField();
        private StockCountStore.Line editing;

        CountCell() {
            field.setAlignment(Pos.CENTER_RIGHT);
            field.setOnAction(e -> commit());
            field.focusedProperty().addListener((obs, was, is) -> {
                if (is) {
                    editing = getTableRow() == null ? null : getTableRow().getItem();
                    Platform.runLater(field::selectAll);
                } else {
                    commit();
                }
            });
        }

        private void commit() {
            StockCountStore.Line item = editing;
            if (item == null || !lineTable.isEditable()) {
                return;
            }
            String text = field.getText() == null ? "" : field.getText().trim().replace(',', '.');
            Double value;
            try {
                value = text.isEmpty() ? null : Double.valueOf(text);
            } catch (NumberFormatException ex) {
                lblMessage.setText("\"" + text + "\" no es un número.");
                field.setText(item.counted == null ? "" : QTY.format(item.counted));
                return;
            }
            if (value != null && value < 0) {
                lblMessage.setText("El conteo no puede ser negativo.");
                field.setText(item.counted == null ? "" : QTY.format(item.counted));
                return;
            }
            if (value == null ? item.counted != null : !value.equals(item.counted)) {
                saveCount(item, value);
            }
            if (!field.isFocused()) {
                editing = null;
            }
        }

        @Override
        protected void updateItem(Double value, boolean empty) {
            super.updateItem(value, empty);
            if (empty) {
                setGraphic(null);
                return;
            }
            if (!field.isFocused()) {
                field.setText(value == null ? "" : QTY.format(value));
            }
            field.setEditable(lineTable.isEditable());
            setGraphic(field);
        }
    }

    private void saveCount(StockCountStore.Line line, Double value) {
        StockCountStore.Plan plan = selectedPlan();
        try {
            Connection con = connection();
            StockCountStore.setCount(con, plan.id, line.productId, value, plan.location);
            for (StockCountStore.Line fresh : StockCountStore.lines(con, plan.id, plan.location)) {
                if (fresh.productId.equals(line.productId)) {
                    line.counted = fresh.counted;
                    line.systemAtCount = fresh.systemAtCount;
                    line.current = fresh.current;
                    line.countedAt = fresh.countedAt;
                }
            }
            lblMessage.setText("");
        } catch (SQLException ex) {
            showError("No se pudo guardar el conteo", ex);
        }
        lineTable.refresh();
        updateSummary();
    }

    // ---------------- Plans ----------------
    @FXML
    public void refresh() {
        reloadData();
    }

    @FXML
    public void newPlan() {
        TextInputDialog dlg = new TextInputDialog("Conteo " + LocalDate.now().format(DATE));
        dlg.setTitle("Nuevo conteo");
        dlg.setHeaderText(null);
        dlg.setContentText("Nombre del conteo:");
        owner(dlg);
        dlg.showAndWait().ifPresent(name -> {
            if (name.isBlank()) {
                return;
            }
            try {
                int id = StockCountStore.createPlan(connection(), name.trim(), LocalDate.now(), location, user(), false);
                reloadData();
                selectPlan(id);
                lblMessage.setText("Conteo creado. Cambia la fecha si es para otro día y agrega los productos.");
                txtAdd.requestFocus();
            } catch (SQLException ex) {
                showError("No se pudo crear el conteo", ex);
            }
        });
    }

    private void selectPlan(int id) {
        for (StockCountStore.Plan p : planTable.getItems()) {
            if (p.id == id) {
                planTable.getSelectionModel().select(p);
            }
        }
    }

    @FXML
    public void savePlan() {
        StockCountStore.Plan plan = selectedPlan();
        if (plan == null || txtPlanName.getText().isBlank() || dpPlanDate.getValue() == null) {
            lblMessage.setText("Indica nombre y fecha.");
            return;
        }
        try {
            StockCountStore.updatePlan(connection(), plan.id, txtPlanName.getText().trim(), dpPlanDate.getValue());
            reloadData();
            lblMessage.setText("Conteo guardado.");
        } catch (SQLException ex) {
            showError("No se pudo guardar el conteo", ex);
        }
    }

    @FXML
    public void cancelPlan() {
        StockCountStore.Plan plan = selectedPlan();
        if (plan == null || !plan.isOpen()
                || !confirm("¿Cancelar el conteo \"" + plan.name + "\"? No se modifica el stock.")) {
            return;
        }
        try {
            StockCountStore.cancelPlan(connection(), plan.id, user());
            reloadData();
        } catch (SQLException ex) {
            showError("No se pudo cancelar el conteo", ex);
        }
    }

    @FXML
    public void closePlan() {
        StockCountStore.Plan plan = selectedPlan();
        if (plan == null || !plan.isOpen()) {
            return;
        }
        lineTable.requestFocus(); // saves a count still being typed
        long counted = lines.stream().filter(l -> l.counted != null).count();
        if (counted == 0) {
            alert(AlertType.INFORMATION, "Todavía no hay productos contados en este conteo.");
            return;
        }
        long changes = lines.stream().filter(l -> l.difference() != null && l.difference() != 0).count();
        String msg = "Se ajustará el stock de " + changes + " productos con diferencia (de " + counted + " contados).";
        if (counted < lines.size()) {
            msg += "\n" + (lines.size() - counted) + " productos sin contar no se modifican.";
        }
        msg += "\nLas ventas hechas después de contar cada producto se respetan.\n\n¿Cerrar el conteo y ajustar el stock?";
        if (!confirm(msg)) {
            return;
        }
        try {
            int adjusted = StockCountStore.closePlan(connection(), plan.id, plan.location, siteGuid, user());
            reloadData();
            selectPlan(plan.id);
            alert(AlertType.INFORMATION, "Conteo cerrado. Se ajustó el stock de " + adjusted
                    + " productos (movimientos \"Conteo #" + plan.id + "\" en el diario de stock).");
        } catch (SQLException ex) {
            showError("No se pudo cerrar el conteo; el stock no se modificó", ex);
        }
    }

    // ---------------- Products ----------------
    @FXML
    public void addFromField() {
        String text = txtAdd.getText() == null ? "" : txtAdd.getText().trim();
        if (text.isEmpty()) {
            openPicker();
            return;
        }
        try {
            String id = StockCountStore.findProduct(connection(), text);
            if (id != null) {
                addProducts(List.of(id));
                txtAdd.clear();
                return;
            }
        } catch (SQLException ex) {
            showError("No se pudo buscar el producto", ex);
            return;
        }
        openPicker();
    }

    private void addProducts(List<String> ids) {
        StockCountStore.Plan plan = selectedPlan();
        if (plan == null || ids.isEmpty()) {
            return;
        }
        try {
            int added = StockCountStore.addProducts(connection(), plan.id, ids);
            loadLines();
            lblMessage.setText(added == ids.size() ? added + " producto(s) agregado(s)."
                    : added + " agregado(s); " + (ids.size() - added) + " ya estaban en el conteo.");
        } catch (SQLException ex) {
            showError("No se pudieron agregar los productos", ex);
        }
    }

    @FXML
    public void removeSelected() {
        StockCountStore.Plan plan = selectedPlan();
        StockCountStore.Line line = lineTable.getSelectionModel().getSelectedItem();
        if (plan == null || !plan.isOpen() || line == null) {
            return;
        }
        try {
            StockCountStore.removeProduct(connection(), plan.id, line.productId);
            loadLines();
        } catch (SQLException ex) {
            showError("No se pudo quitar el producto", ex);
        }
    }

    @FXML
    public void countFromField() {
        StockCountStore.Plan plan = selectedPlan();
        String code = txtCountCode.getText() == null ? "" : txtCountCode.getText().trim();
        if (plan == null || !plan.isOpen() || code.isEmpty()) {
            txtCountCode.requestFocus();
            return;
        }
        double qty;
        try {
            qty = Double.parseDouble(txtCountQty.getText().trim().replace(',', '.'));
        } catch (RuntimeException ex) {
            lblMessage.setText("Cantidad inválida.");
            return;
        }
        try {
            String id = StockCountStore.findProduct(connection(), code);
            StockCountStore.Line line = null;
            for (StockCountStore.Line l : lines) {
                if (l.productId.equals(id)) {
                    line = l;
                }
            }
            if (id == null) {
                lblMessage.setText("No existe un producto con el código " + code + ".");
            } else if (line == null) {
                if (confirm("El producto con código " + code + " no está en este conteo. ¿Agregarlo y contarlo?")) {
                    addProducts(List.of(id));
                    for (StockCountStore.Line l : lines) {
                        if (l.productId.equals(id)) {
                            line = l;
                        }
                    }
                }
            }
            if (line != null) {
                double value = chkSum.isSelected() && line.counted != null ? line.counted + qty : qty;
                saveCount(line, value);
                lineTable.getSelectionModel().select(line);
                lineTable.scrollTo(line);
                lblMessage.setText(line.name + ": " + QTY.format(value));
            }
        } catch (SQLException ex) {
            showError("No se pudo capturar el conteo", ex);
        }
        txtCountCode.clear();
        txtCountQty.setText("1");
        txtCountCode.requestFocus();
    }

    /** Product chooser: search and tick the products to add. */
    @FXML
    public void openPicker() {
        StockCountStore.Plan plan = selectedPlan();
        if (plan == null || !plan.isOpen()) {
            return;
        }
        final class Item {
            final String id;
            final String reference;
            final String code;
            final String name;
            final String category;
            final BooleanProperty selected = new SimpleBooleanProperty();

            Item(ResultSet rs) throws SQLException {
                id = rs.getString(1);
                reference = rs.getString(2);
                code = rs.getString(3);
                name = rs.getString(4);
                category = rs.getString(5);
            }

            boolean matches(String text) {
                String t = text.toLowerCase();
                return t.isEmpty() || contains(reference, t) || contains(code, t) || contains(name, t) || contains(category, t);
            }

            private boolean contains(String s, String t) {
                return s != null && s.toLowerCase().contains(t);
            }
        }
        ObservableList<Item> items = FXCollections.observableArrayList();
        try (PreparedStatement ps = connection().prepareStatement("SELECT p.id, p.reference, p.code, p.name, c.name "
                + "FROM products p LEFT JOIN categories c ON c.id = p.category "
                + "WHERE p.managestock = 1 AND COALESCE(p.isservice, 0) = 0 AND COALESCE(p.systemobject, 0) = 0 "
                + "AND p.id NOT IN (SELECT product FROM stockcount_lines WHERE plan_id = ?) ORDER BY c.name, p.name")) {
            ps.setInt(1, plan.id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new Item(rs));
                }
            }
        } catch (SQLException ex) {
            showError("No se pudo leer la lista de productos", ex);
            return;
        }
        FilteredList<Item> filtered = new FilteredList<>(items);
        TextField search = new TextField(txtAdd.getText());
        search.setPromptText("Buscar por nombre, referencia, código o categoría");
        filtered.setPredicate(i -> i.matches(search.getText().trim()));
        search.textProperty().addListener((obs, o, n) -> filtered.setPredicate(i -> i.matches(n.trim())));

        TableView<Item> table = new TableView<>(filtered);
        table.setEditable(true);
        TableColumn<Item, Boolean> chk = new TableColumn<>("✔");
        chk.setCellValueFactory(c -> c.getValue().selected);
        chk.setCellFactory(CheckBoxTableCell.forTableColumn(chk));
        chk.setPrefWidth(40);
        TableColumn<Item, String> ref = new TableColumn<>("Referencia");
        ref.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().reference));
        ref.setPrefWidth(110);
        TableColumn<Item, String> code = new TableColumn<>("Código");
        code.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().code));
        code.setPrefWidth(120);
        TableColumn<Item, String> name = new TableColumn<>("Producto");
        name.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name));
        name.setPrefWidth(280);
        TableColumn<Item, String> cat = new TableColumn<>("Categoría");
        cat.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().category));
        cat.setPrefWidth(150);
        table.getColumns().add(chk);
        table.getColumns().add(ref);
        table.getColumns().add(code);
        table.getColumns().add(name);
        table.getColumns().add(cat);
        table.setPlaceholder(new Label("No hay productos (solo aparecen los que controlan stock y no están ya en el conteo)."));

        Label count = new Label();
        Runnable updateCount = () -> count.setText(items.stream().filter(i -> i.selected.get()).count() + " marcados");
        items.forEach(i -> i.selected.addListener((obs, o, n) -> updateCount.run()));
        updateCount.run();

        Button markShown = new Button("Marcar los mostrados");
        markShown.setOnAction(e -> filtered.forEach(i -> i.selected.set(true)));
        Button unmark = new Button("Desmarcar todos");
        unmark.setOnAction(e -> items.forEach(i -> i.selected.set(false)));
        Button ok = new Button("Agregar marcados");
        ok.setDefaultButton(true);
        Button cancel = new Button("Cancelar");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, markShown, unmark, count, spacer, ok, cancel);
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox root = new VBox(8, search, table, buttons);
        root.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);

        Stage stage = new Stage();
        stage.setTitle("Agregar productos al conteo");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (apAnchor.getScene() != null) {
            stage.initOwner(apAnchor.getScene().getWindow());
        }
        stage.setScene(new Scene(root, 760, 560));
        ok.setOnAction(e -> {
            List<String> ids = new ArrayList<>();
            items.forEach(i -> {
                if (i.selected.get()) {
                    ids.add(i.id);
                }
            });
            stage.close();
            addProducts(ids);
            txtAdd.clear();
        });
        cancel.setOnAction(e -> stage.close());
        Platform.runLater(search::requestFocus);
        stage.showAndWait();
    }

    // ---------------- Rotation ----------------
    @FXML
    public void saveRotation() {
        StockCountStore.Rotation r = new StockCountStore.Rotation();
        r.enabled = chkRotation.isSelected();
        r.days = Math.max(1, parse(txtRotationDays.getText(), 7));
        r.size = Math.max(1, parse(txtRotationSize.getText(), 100));
        r.next = dpRotationNext.getValue();
        if (r.enabled && r.next == null) {
            r.next = LocalDate.now();
        }
        try {
            StockCountStore.saveRotation(connection(), r);
            reloadData();
            if (lblMessage.getText().isEmpty()) {
                lblMessage.setText(r.enabled ? "Conteo rotativo guardado: próximo el " + r.next.format(DATE) + "."
                        : "Conteo rotativo desactivado.");
            }
        } catch (SQLException ex) {
            showError("No se pudo guardar el conteo rotativo", ex);
        }
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException ex) {
            return def;
        }
    }

    // ---------------- Reports ----------------
    @FXML
    public void showSheet() {
        runReport(getClass().getResourceAsStream("hoja_conteo.jrxml"), "Hoja de conteo");
    }

    @FXML
    public void showResult() {
        lineTable.requestFocus();
        runReport(getClass().getResourceAsStream("resultado_conteo.jrxml"), "Resultado del conteo");
    }

    /**
     * Any .jrxml the business already uses. It receives the parameters
     * PLAN_ID, LOCATION, COMPANY, PLAN_NAME, PLAN_DATE and PRODUCT_IDS (list
     * of the plan's product ids, for $X{IN, p.id, PRODUCT_IDS}).
     */
    @FXML
    public void showCustom() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Informe de Jasper (.jrxml) para los productos del conteo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Jasper Reports", "*.jrxml"));
        String last = PREFS.get("customReport", null);
        if (last != null && new File(last).getParentFile() != null && new File(last).getParentFile().isDirectory()) {
            chooser.setInitialDirectory(new File(last).getParentFile());
            chooser.setInitialFileName(new File(last).getName());
        } else if (new File(System.getProperty("user.dir"), "reports").isDirectory()) {
            chooser.setInitialDirectory(new File(System.getProperty("user.dir"), "reports"));
        }
        File file = chooser.showOpenDialog(apAnchor.getScene().getWindow());
        if (file == null) {
            return;
        }
        PREFS.put("customReport", file.getAbsolutePath());
        try {
            runReport(new FileInputStream(file), file.getName());
        } catch (java.io.FileNotFoundException ex) {
            showError("No se encontró el informe", ex);
        }
    }

    private void runReport(InputStream jrxml, String title) {
        StockCountStore.Plan plan = selectedPlan();
        if (plan == null || jrxml == null) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("PLAN_ID", plan.id);
        params.put("LOCATION", plan.location);
        params.put("COMPANY", Company.NAME == null ? "" : Company.NAME);
        params.put("PLAN_NAME", plan.name);
        params.put("PLAN_DATE", plan.date.format(DATE));
        List<String> ids = new ArrayList<>();
        lines.forEach(l -> ids.add(l.productId));
        params.put("PRODUCT_IDS", ids);
        ResourceBundle bundle = LocalResource.getResource("report_labels");
        if (bundle != null) {
            params.put("REPORT_RESOURCE_BUNDLE", bundle);
        }
        params.put("REPORT_LOCALE", new Locale("es", "MX"));
        try (InputStream in = jrxml) {
            JasperReport jr = JasperCompileManager.compileReport(in);
            JasperPrint jp = JasperFillManager.fillReport(jr, params, connection());
            showViewer(jp);
        } catch (Exception ex) {
            showError("No se pudo generar \"" + title + "\"", ex);
        }
    }

    /** Same viewer Chromis Admin uses for its reports (print and save to PDF). */
    private void showViewer(JasperPrint jp) throws java.io.IOException {
        Stage showStage = new Stage();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/uk/chromis/jasperviewer/JasperViewer.fxml"), LocalResource.resources);
        JRViewerController jasperControl = new JRViewerController();
        loader.setController(jasperControl);
        Scene scene = new Scene((Parent) loader.load(), 1100.0, 750.0);
        if (LocalResource.cssResource != null) {
            scene.getStylesheets().add(LocalResource.cssResource);
        }
        showStage.setScene(scene);
        showStage.initStyle(StageStyle.UNDECORATED);
        showStage.initModality(Modality.APPLICATION_MODAL);
        Stage current = (Stage) apAnchor.getScene().getWindow();
        showStage.setX(current.getX() + current.getWidth() / 2.0 - 550.0);
        showStage.setY(current.getY() + current.getHeight() / 2.0 - 375.0);
        showStage.setMinWidth(1100.0);
        jasperControl.setJasperPrint(jp);
        jasperControl.show();
        showStage.showAndWait();
    }

    // ---------------- Dialogs ----------------
    private void owner(javafx.scene.control.Dialog<?> dlg) {
        if (apAnchor.getScene() != null) {
            dlg.initOwner(apAnchor.getScene().getWindow());
        }
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle("Conteos programados");
        alert.getDialogPane().setPrefWidth(560);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        owner(alert);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void showError(String message, Throwable ex) {
        LOGGER.log(Level.WARNING, message, ex);
        alert(AlertType.ERROR, message + ":\n" + (ex == null ? "" : ex.getMessage()));
    }

    private void alert(AlertType type, String message) {
        Alert alert = new Alert(type, message);
        alert.setHeaderText(null);
        alert.setTitle("Conteos programados");
        alert.getDialogPane().setPrefWidth(560);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        owner(alert);
        alert.showAndWait();
    }
}
