package uk.chromis.administration.audit;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import uk.chromis.administration.main.Start;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.pos.audit.PickAuditStore;
import uk.chromis.pos.audit.PickAuditStore.Audit;
import uk.chromis.pos.audit.PickAuditStore.Line;
import uk.chromis.pos.audit.PickAuditStore.Order;

/**
 * Auditor de salida: the goods of a sale are scanned against its ticket
 * before they leave (what the separate AuditorPickPack program did). Built in
 * code; every scan is saved at once in the database (PickAuditStore).
 */
public class PickAuditController {

    private static final Logger LOGGER = Logger.getLogger(PickAuditController.class.getName());
    private static final ButtonType CANCEL = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
    private static final String GREEN = "#C8F7C5", YELLOW = "#FFF6B3", ORANGE = "#FFC266", RED = "#FFE0E0";

    private final AnchorPane root = new AnchorPane();
    private final TextField txtOrder = new TextField();
    private final TextField txtScan = new TextField();
    private final Label lblNumber = new Label("PEDIDO: ---");
    private final Label lblCustomer = new Label("Cliente: —");
    private final Label lblAddress = new Label("Dirección: —");
    private final Label lblKind = new Label();
    private final Label lblStarted = new Label();
    private final Label lblBanner = new Label();
    private final Label lblProgress = new Label();
    private final TableView<Line> scanned = new TableView<>();
    private final TableView<Line> expected = new TableView<>();
    private final ObservableList<Line> scannedRows = FXCollections.observableArrayList();
    private final ObservableList<Line> expectedRows = FXCollections.observableArrayList();
    private final TableView<Audit> history = new TableView<>();
    private final DatePicker historyFrom = new DatePicker(LocalDate.now().minusDays(7));
    private final PauseTransition bannerTimer = new PauseTransition(Duration.seconds(3));
    private final Button btnMissing = new Button("⚠ Reportar faltante");
    private final Button btnBulk = new Button("Granel (F2)");
    private final Button btnFinish = new Button("Validar y finalizar pedido");
    private final Button btnDiscard = new Button("Descartar auditoría");
    private Audit audit;

    public PickAuditController() {
        build();
        clear();
    }

    public Parent root() {
        return root;
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }

    /** Called each time the menu entry is opened. */
    public void reloadData() {
        loadHistory();
        Platform.runLater(() -> (audit == null ? txtOrder : txtScan).requestFocus());
    }

    // ---------------- Screen ----------------

    private void build() {
        root.setMinSize(1020, 700);
        Label title = new Label("Auditor de salida");
        title.setFont(Font.font("System", FontWeight.BOLD, FontPosture.ITALIC, 14));
        AnchorPane.setTopAnchor(title, 57.0);
        AnchorPane.setLeftAnchor(title, 10.0);

        TabPane tabs = new TabPane(new Tab("Auditar pedido", buildAudit()), new Tab("Historial", buildHistory()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            if (b.intValue() == 1) {
                loadHistory();
            }
        });
        AnchorPane.setTopAnchor(tabs, 82.0);
        AnchorPane.setLeftAnchor(tabs, 15.0);
        AnchorPane.setRightAnchor(tabs, 15.0);
        AnchorPane.setBottomAnchor(tabs, 10.0);
        root.getChildren().addAll(title, tabs);
        root.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F2) {
                bulk();
                e.consume();
            }
        });
    }

    private VBox buildAudit() {
        Label lblOrder = new Label("Pedido:");
        lblOrder.setStyle("-fx-font-weight: bold;");
        txtOrder.setPromptText("Número de ticket o código del ticket, y Enter");
        txtOrder.setPrefColumnCount(28);
        txtOrder.setOnAction(e -> load());
        Button btnLoad = new Button("Cargar");
        btnLoad.setOnAction(e -> load());
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        Label who = new Label("Auditor: " + Start.getActiveUser());
        who.setTextFill(javafx.scene.paint.Color.web("#555555"));
        HBox top = new HBox(10, lblOrder, txtOrder, btnLoad, grow, who);
        top.setAlignment(Pos.CENTER_LEFT);

        lblNumber.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #00b3ff;");
        lblCustomer.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");
        lblAddress.setStyle("-fx-font-size: 15px; -fx-text-fill: white;");
        lblStarted.setStyle("-fx-font-size: 12px; -fx-text-fill: #cccccc;");
        VBox details = new VBox(2, lblCustomer, lblAddress, lblStarted);
        Region grow2 = new Region();
        HBox.setHgrow(grow2, Priority.ALWAYS);
        lblProgress.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        HBox info = new HBox(18, lblNumber, details, grow2, lblKind, lblProgress);
        info.setAlignment(Pos.CENTER_LEFT);
        info.setPadding(new Insets(8, 14, 8, 14));
        info.setStyle("-fx-background-color: #2d2d2d; -fx-background-radius: 5;");

        buildTables();
        VBox left = new VBox(4, header("ARTÍCULOS ESCANEADOS"), scanned);
        VBox right = new VBox(4, header("CONTENIDO DE LA VENTA (META)"), expected);
        VBox.setVgrow(scanned, Priority.ALWAYS);
        VBox.setVgrow(expected, Priority.ALWAYS);
        SplitPane split = new SplitPane(left, right);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.45);

        lblBanner.setMaxWidth(Double.MAX_VALUE);
        lblBanner.setAlignment(Pos.CENTER);
        lblBanner.setMinHeight(34);
        lblBanner.setWrapText(true);
        bannerTimer.setOnFinished(e -> banner(null, null));

        Label lblScan = new Label("Escanee (Enter vacío para finalizar, F2 para granel):");
        lblScan.setStyle("-fx-font-weight: bold;");
        txtScan.setStyle("-fx-font-size: 16px; -fx-border-color: #0078d7; -fx-border-width: 2;");
        txtScan.setOnAction(e -> scan());
        HBox.setHgrow(txtScan, Priority.ALWAYS);
        HBox scanRow = new HBox(10, lblScan, txtScan);
        scanRow.setAlignment(Pos.CENTER_LEFT);

        btnMissing.setStyle("-fx-background-color: #ff9800; -fx-font-size: 14px; -fx-font-weight: bold;");
        btnMissing.setOnAction(e -> missing());
        btnBulk.setStyle("-fx-font-size: 14px;");
        btnBulk.setOnAction(e -> bulk());
        btnFinish.setStyle("-fx-background-color: #4CAF50; -fx-font-size: 15px; -fx-font-weight: bold;");
        btnFinish.setOnAction(e -> finish());
        btnDiscard.setOnAction(e -> discard());
        for (Button b : new Button[]{btnMissing, btnBulk, btnFinish}) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setPrefHeight(42);
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        btnDiscard.setPrefHeight(42);
        HBox buttons = new HBox(10, btnMissing, btnBulk, btnFinish, btnDiscard);

        VBox box = new VBox(8, top, info, split, lblBanner, scanRow, buttons);
        VBox.setVgrow(split, Priority.ALWAYS);
        box.setPadding(new Insets(8, 0, 0, 0));
        return box;
    }

    private static Label header(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        return l;
    }

    private void buildTables() {
        scanned.setItems(scannedRows);
        scanned.setPlaceholder(new Label("Todavía no se escanea nada."));
        scanned.getColumns().add(column("Código", 120, l -> l.code));
        scanned.getColumns().add(column("Descripción", 230, l -> l.name));
        scanned.getColumns().add(column("Escaneado", 85, l -> PickAuditStore.qty(l.scanned)));
        scanned.getColumns().add(column("Faltante", 75, l -> l.missing() > 0 ? PickAuditStore.qty(l.missing()) : ""));
        scanned.setRowFactory(t -> coloredRow(l -> l.complete() ? GREEN : null));
        scanned.setFocusTraversable(false);

        expected.setItems(expectedRows);
        expected.setPlaceholder(new Label("Carga un pedido: escanea o escribe el número del ticket."));
        expected.getColumns().add(column("Código", 120, l -> l.code));
        expected.getColumns().add(column("Descripción", 230, l -> l.name));
        expected.getColumns().add(column("Cantidad", 75, l -> PickAuditStore.qty(l.expected)));
        expected.getColumns().add(column("Escaneado", 85, l -> PickAuditStore.qty(l.scanned)));
        expected.getColumns().add(column("Estado", 150, l -> l.complete() ? "Completo"
                : l.justified ? "Faltante justificado" : l.scanned > 0 ? "Parcial" : "Pendiente"));
        expected.setRowFactory(t -> coloredRow(l -> l.complete() ? GREEN : l.justified ? ORANGE : l.scanned > 0 ? YELLOW : RED));
        expected.setFocusTraversable(false);
    }

    /** Row painted by its state; black text so it reads on any color. */
    private static TableRow<Line> coloredRow(Function<Line, String> color) {
        return new TableRow<Line>() {
            @Override
            protected void updateItem(Line l, boolean empty) {
                super.updateItem(l, empty);
                String c = empty || l == null ? null : color.apply(l);
                // selected: a blue frame drawn as a second background layer (a border would move the text)
                setStyle(c == null ? "" : (isSelected() ? "-fx-background-color: #0078d7, " + c + "; -fx-background-insets: 0, 2;"
                        : "-fx-background-color: " + c + ";") + " -fx-text-background-color: black;");
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(selected);
                updateItem(getItem(), isEmpty());
            }
        };
    }

    private static <T> TableColumn<T, Object> column(String title, double width, Function<T, Object> value) {
        TableColumn<T, Object> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(value.apply(c.getValue())));
        return col;
    }

    private VBox buildHistory() {
        SimpleDateFormat when = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        history.setPlaceholder(new Label("No hay auditorías en esas fechas."));
        history.getColumns().add(column("Inicio", 125, a -> a.started == null ? "" : when.format(a.started)));
        history.getColumns().add(column("Ticket", 70, a -> a.order == null ? "" : a.order.number));
        history.getColumns().add(column("Cliente", 200, a -> a.order == null || a.order.customer == null ? "Venta de mostrador" : a.order.customer));
        history.getColumns().add(column("Tipo", 85, a -> a.order != null && a.order.delivery ? "Entrega" : "Mostrador"));
        history.getColumns().add(column("Auditó", 120, a -> a.closedBy != null ? a.closedBy : a.startedBy));
        history.getColumns().add(column("Estado", 150, a -> PickAuditStore.OPEN.equals(a.status) ? "Abierta (sin terminar)"
                : PickAuditStore.RESULT_OK.equals(a.result) ? "Cuadrado" : "Con faltantes"));
        history.getColumns().add(column("Faltó", 250, PickAuditController::missingText));
        history.getColumns().add(column("Cierre", 125, a -> a.closed == null ? "" : when.format(a.closed)));
        history.setRowFactory(t -> {
            TableRow<Audit> row = new TableRow<Audit>() {
                @Override
                protected void updateItem(Audit a, boolean empty) {
                    super.updateItem(a, empty);
                    setStyle(empty || a == null ? "" : PickAuditStore.OPEN.equals(a.status) ? "-fx-background-color: #FFF6B3;"
                            : PickAuditStore.RESULT_MISSING.equals(a.result) ? "-fx-background-color: #FFE5C2;" : "");
                }
            };
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    showDetail(row.getItem());
                }
            });
            return row;
        });
        historyFrom.setConverter(new javafx.util.converter.LocalDateStringConverter(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"), null));
        historyFrom.valueProperty().addListener((o, a, b) -> loadHistory());
        Button refresh = new Button("Actualizar");
        refresh.setOnAction(e -> loadHistory());
        Label help = new Label("Doble clic en una auditoría para ver sus productos. Las abiertas se retoman cargando su pedido.");
        help.setTextFill(javafx.scene.paint.Color.web("#555555"));
        HBox top = new HBox(10, new Label("Desde:"), historyFrom, refresh, help);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, top, history);
        VBox.setVgrow(history, Priority.ALWAYS);
        box.setPadding(new Insets(8, 0, 0, 0));
        return box;
    }

    private static String missingText(Audit a) {
        StringBuilder sb = new StringBuilder();
        for (Line l : a.lines) {
            if (!l.complete()) {
                sb.append(sb.length() > 0 ? "; " : "").append(l.name).append(" (").append(PickAuditStore.qty(l.missing())).append(")");
            }
        }
        return sb.toString();
    }

    // ---------------- Actions ----------------

    private void load() {
        String id = txtOrder.getText().trim();
        if (id.isEmpty()) {
            return;
        }
        try {
            Connection con = connection();
            PickAuditStore.createTables(con);
            Order o = PickAuditStore.find(con, id);
            if (o == null) {
                error("No se encontró la venta " + id + ".");
                txtOrder.selectAll();
                return;
            }
            if (audit != null && audit.order != null && !audit.order.ticketId.equals(o.ticketId)) {
                info("El pedido #" + audit.order.number + " quedó pendiente: se retoma al cargarlo otra vez.");
            }
            Integer open = PickAuditStore.openId(con, o.ticketId);
            if (open == null) {
                Audit last = PickAuditStore.lastClosed(con, o.ticketId);
                if (last != null && !confirm("Esta venta ya se auditó",
                        "El pedido #" + o.number + " ya lo auditó " + last.closedBy + " el "
                        + new SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm").format(last.closed)
                        + (PickAuditStore.RESULT_OK.equals(last.result) ? " (cuadrado)." : " (con faltantes: " + missingText(last) + ").")
                        + "\n\n¿Auditarla otra vez desde cero?")) {
                    txtOrder.selectAll();
                    return;
                }
            }
            audit = PickAuditStore.open(con, o, Start.getActiveUserId(), Start.getActiveUser());
            txtOrder.clear();
            show();
            if (open != null) {
                info("Se retomó la auditoría que " + audit.startedBy + " dejó sin terminar.");
            }
            Tone.ok();
            txtScan.requestFocus();
        } catch (PickAuditStore.Refused ex) {
            error(ex.getMessage());
        } catch (SQLException ex) {
            failure("No se pudo cargar el pedido", ex);
        }
    }

    private void scan() {
        String code = txtScan.getText().trim();
        txtScan.clear();
        if (audit == null) {
            error("Carga un pedido primero.");
            txtOrder.requestFocus();
            return;
        }
        if (code.isEmpty()) {
            finish();
            return;
        }
        add(code, null);
    }

    private void add(String code, Double units) {
        try {
            Line l = PickAuditStore.scan(connection(), audit.id, code, units);
            reload(l.productId);
            Tone.ok();
            ok(l.name + ": " + PickAuditStore.qty(l.scanned) + " de " + PickAuditStore.qty(l.expected));
        } catch (PickAuditStore.Refused ex) {
            error(ex.getMessage());
        } catch (SQLException ex) {
            failure("No se pudo guardar el escaneo", ex);
        }
    }

    /** F2: a quantity typed by hand (bulk goods, or a pack counted by eye). */
    private void bulk() {
        if (audit == null) {
            error("Carga un pedido primero.");
            return;
        }
        Line selected = expected.getSelectionModel().getSelectedItem();
        TextField code = new TextField(selected == null ? "" : selected.code);
        code.setPromptText("Escanea o escribe el código");
        TextField qty = new TextField(selected == null ? "1" : PickAuditStore.qty(selected.missing()));
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.addRow(0, new Label("Código"), code);
        g.addRow(1, new Label("Cantidad a sumar"), qty);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Entrada manual (granel)");
        d.setHeaderText(null);
        d.getDialogPane().setContent(g);
        ButtonType ok = new ButtonType("Sumar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, CANCEL);
        Platform.runLater(() -> (selected == null ? code : qty).requestFocus());
        if (d.showAndWait().orElse(CANCEL) == ok) {
            Double units;
            try {
                units = Double.valueOf(qty.getText().trim().replace(',', '.'));
            } catch (NumberFormatException ex) {
                units = 0.0;
            }
            if (units <= 0) {
                error("La cantidad debe ser mayor a 0.");
            } else {
                add(code.getText(), units);
            }
        }
        txtScan.requestFocus();
    }

    /** Justifies (or takes back) the missing units of the product selected in the META table. */
    private void missing() {
        if (audit == null) {
            error("Carga un pedido primero.");
            return;
        }
        Line l = expected.getSelectionModel().getSelectedItem();
        if (l == null) {
            error("Selecciona en la tabla de la derecha (META) el artículo que falta.");
            return;
        }
        try {
            if (l.justified) {
                if (confirm("Quitar faltante", "¿Quitar la justificación de " + l.name + "?")) {
                    PickAuditStore.justify(connection(), audit.id, l.productId, false);
                    reload(l.productId);
                }
            } else if (confirm("Justificar faltante", "¿Justificar que faltan " + PickAuditStore.qty(l.missing()) + " de " + l.name
                    + "?\nSe podrá cerrar el pedido sin esa mercancía (no hay existencia).")) {
                PickAuditStore.justify(connection(), audit.id, l.productId, true);
                reload(l.productId);
                info("Faltante justificado: " + l.name + ".");
            }
        } catch (PickAuditStore.Refused ex) {
            error(ex.getMessage());
        } catch (SQLException ex) {
            failure("No se pudo guardar el faltante", ex);
        }
        txtScan.requestFocus();
    }

    private void finish() {
        if (audit == null) {
            return;
        }
        try {
            int number = audit.order == null ? 0 : audit.order.number;
            PickAuditStore.Closed r = PickAuditStore.close(connection(), audit.id, Start.getActiveUserId(), Start.getActiveUser());
            Tone.done();
            StringBuilder msg = new StringBuilder(r.justified.isEmpty() ? "Pedido cuadrado perfectamente."
                    : "Pedido finalizado con FALTANTES JUSTIFICADOS:");
            for (Line l : r.justified) {
                msg.append("\n- ").append(l.name).append(" (faltó ").append(PickAuditStore.qty(l.missing())).append(")");
            }
            if (r.delivery != null) {
                msg.append("\n\n").append(r.delivery);
            }
            clear();
            ok("Pedido #" + number + " auditado.");
            Alert a = new Alert(r.justified.isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING, msg.toString(), ButtonType.OK);
            a.setTitle("Auditoría terminada");
            a.setHeaderText("Pedido #" + number);
            a.showAndWait();
            txtOrder.requestFocus();
        } catch (PickAuditStore.Refused ex) {
            error(ex.getMessage());
            Alert a = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
            a.setTitle("No se puede finalizar");
            a.setHeaderText(null);
            a.showAndWait();
            txtScan.requestFocus();
        } catch (SQLException ex) {
            failure("No se pudo cerrar la auditoría", ex);
        }
    }

    private void discard() {
        if (audit == null) {
            return;
        }
        if (!confirm("Descartar auditoría", "¿Descartar la auditoría del pedido #" + (audit.order == null ? "" : audit.order.number)
                + "? Se pierde lo escaneado.")) {
            return;
        }
        try {
            PickAuditStore.discard(connection(), audit.id);
            clear();
            info("Auditoría descartada.");
        } catch (SQLException ex) {
            failure("No se pudo descartar", ex);
        }
    }

    // ---------------- Display ----------------

    private void reload(String select) throws SQLException {
        audit = PickAuditStore.get(connection(), audit.id);
        show();
        for (Line l : expectedRows) {
            if (l.productId.equals(select)) {
                expected.getSelectionModel().select(l);
                expected.scrollTo(l);
            }
        }
        for (Line l : scannedRows) {
            if (l.productId.equals(select)) {
                scanned.scrollTo(l);
            }
        }
    }

    private void show() {
        Order o = audit.order;
        lblNumber.setText("PEDIDO: #" + (o == null ? "?" : o.number));
        lblCustomer.setText("Cliente: " + (o == null || o.customer == null ? "Venta de mostrador" : o.customer));
        lblAddress.setText("Dirección: " + (o == null || o.address == null ? "—" : o.address));
        lblKind.setText(o != null && o.delivery ? "ENTREGA A DOMICILIO" : "MOSTRADOR");
        lblKind.setStyle("-fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12; -fx-text-fill: white; "
                + "-fx-background-color: " + (o != null && o.delivery ? "#1E6FC8" : "#777777") + ";");
        lblStarted.setText("Iniciada por " + audit.startedBy + " · " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(audit.started));
        expectedRows.setAll(audit.lines);
        scannedRows.setAll(audit.lines.stream().filter(l -> l.scanned > 0).toArray(Line[]::new));
        long ready = audit.lines.stream().filter(Line::ready).count();
        lblProgress.setText(ready + " / " + audit.lines.size() + " listos");
        setButtons(true);
    }

    private void clear() {
        audit = null;
        expectedRows.clear();
        scannedRows.clear();
        lblNumber.setText("PEDIDO: ---");
        lblCustomer.setText("Cliente: —");
        lblAddress.setText("Dirección: —");
        lblKind.setText("");
        lblKind.setStyle("");
        lblStarted.setText("");
        lblProgress.setText("");
        setButtons(false);
        Platform.runLater(txtOrder::requestFocus);
    }

    private void setButtons(boolean on) {
        for (Button b : new Button[]{btnMissing, btnBulk, btnFinish, btnDiscard}) {
            b.setDisable(!on);
        }
        txtScan.setDisable(!on);
    }

    private void loadHistory() {
        try {
            LocalDate from = historyFrom.getValue() == null ? LocalDate.now().minusDays(7) : historyFrom.getValue();
            List<Audit> list = PickAuditStore.history(connection(), Timestamp.valueOf(from.atStartOfDay()), 500);
            history.setItems(FXCollections.observableArrayList(list));
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "historial de auditorías", ex);
        }
    }

    private void showDetail(Audit a) {
        StringBuilder sb = new StringBuilder();
        for (Line l : a.lines) {
            sb.append(String.format("%-14s %-40s %8s de %-8s %s%n", l.code, l.name.length() > 40 ? l.name.substring(0, 40) : l.name,
                    PickAuditStore.qty(l.scanned), PickAuditStore.qty(l.expected),
                    l.complete() ? "" : l.justified ? "FALTANTE JUSTIFICADO" : "FALTA"));
        }
        TextArea t = new TextArea(sb.toString());
        t.setEditable(false);
        t.setStyle("-fx-font-family: monospace;");
        t.setPrefSize(760, 320);
        Alert d = new Alert(Alert.AlertType.INFORMATION);
        d.setTitle("Auditoría");
        d.setHeaderText("Pedido #" + (a.order == null ? "" : a.order.number) + " · "
                + (a.order == null || a.order.customer == null ? "Venta de mostrador" : a.order.customer)
                + (a.closedBy != null ? " · auditó " + a.closedBy : " · sin terminar"));
        d.getDialogPane().setContent(t);
        d.showAndWait();
    }

    private void banner(String text, String color) {
        lblBanner.setText(text == null ? "" : text);
        lblBanner.setStyle(text == null ? "" : "-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: black; "
                + "-fx-background-color: " + color + "; -fx-background-radius: 4; -fx-padding: 3 8;");
        bannerTimer.stop();
        if (text != null) {
            bannerTimer.playFromStart();
        }
    }

    private void ok(String text) {
        banner("✅ " + text, "#4CAF50");
    }

    private void info(String text) {
        banner("ℹ " + text, "#90CAF9");
    }

    private void error(String text) {
        Tone.error();
        banner("❌ " + text, "#ff4747");
    }

    private void failure(String what, Exception ex) {
        LOGGER.log(Level.WARNING, what, ex);
        error(what + ": " + ex.getMessage());
    }

    private static boolean confirm(String title, String text) {
        ButtonType yes = new ButtonType("Sí", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("No", ButtonBar.ButtonData.NO);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "", yes, no);
        a.setTitle(title);
        a.setHeaderText(null);
        Label body = new Label(text);
        body.setWrapText(true);
        body.setMaxWidth(480);
        a.getDialogPane().setContent(body);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        return a.showAndWait().orElse(no) == yes;
    }
}
