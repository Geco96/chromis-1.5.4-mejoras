package uk.chromis.administration.delivery;

import java.awt.Desktop;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.util.StringConverter;
import uk.chromis.administration.main.Start;
import uk.chromis.administration.receiving.PurchaseOrderStore;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.pos.delivery.DeliveryStatusStore;
import uk.chromis.pos.delivery.DeliveryStatusStore.Entry;
import uk.chromis.pos.delivery.DeliveryStatusStore.Role;
import uk.chromis.pos.delivery.ZoneStore;
import uk.chromis.pos.forms.AppConfig;
import uk.chromis.pos.settings.PosSettings;

/**
 * Delivery manager: the open deliveries with their status, zone, driver and
 * what the driver reported, the zones with their driver and dispatcher, and
 * the map (opened in the PC's browser, see MapServer).
 *
 * <p>
 * Users who can edit roles (permission UserRoles) are administrators; the
 * rest are dispatchers. Until drivers confirm from the phone, the office
 * records here what the driver reported (amount received and notes).</p>
 */
public class DeliveryManagerController {

    private static final Logger LOGGER = Logger.getLogger(DeliveryManagerController.class.getName());
    public static final String MAPS_KEY = "maps.apikey";
    public static final String MAPS_CENTER = "maps.center";
    public static final String MAPS_PORT = "maps.port";

    private static final ButtonType CANCEL = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

    private final AnchorPane root = new AnchorPane();
    /** Address of the map, selectable so it can be copied when the browser does not open. */
    private final TextField txtAddress = new TextField();
    private final TableView<Entry> table = new TableView<>();
    private final TableView<ZoneStore.Zone> zones = new TableView<>();
    private final ObservableList<Entry> rows = FXCollections.observableArrayList();
    private final ObservableList<ZoneStore.Zone> zoneRows = FXCollections.observableArrayList();
    private final CheckBox chkClosed = new CheckBox("Ver también las cerradas de hoy");
    private final Label lblSummary = new Label();
    private final Label lblMessage = new Label();
    private final HBox actions = new HBox(8);
    private final Map<String, String> people = new LinkedHashMap<>();
    private final Role role;

    public DeliveryManagerController() {
        role = Boolean.TRUE.equals(Start.hasPermission("UserRoles")) ? Role.ADMIN : Role.DISPATCHER;
        build();
        reloadData();
    }

    public Parent root() {
        return root;
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }

    // ---------------- Screen ----------------

    private void build() {
        root.setMinSize(1020, 700);
        root.setPrefWidth(1216);
        Label title = new Label("Gestor de entregas");
        title.setFont(Font.font("System", FontWeight.BOLD, FontPosture.ITALIC, 14));
        AnchorPane.setTopAnchor(title, 57.0);
        AnchorPane.setLeftAnchor(title, 10.0);

        Button btnMap = new Button("Abrir mapa");
        btnMap.setStyle("-fx-font-weight: bold;");
        btnMap.setOnAction(e -> openMap());
        Button btnRefresh = new Button("Actualizar");
        btnRefresh.setOnAction(e -> reloadData());
        chkClosed.setOnAction(e -> loadRows());
        Button btnSetup = new Button("Configurar mapa...");
        btnSetup.setOnAction(e -> configureMap());
        btnSetup.setDisable(role != Role.ADMIN);
        Button btnOld = new Button("Cerrar entregas viejas...");
        btnOld.setOnAction(e -> closeOld());
        btnOld.setDisable(role != Role.ADMIN);
        Label who = new Label(Start.getActiveUser() + " · " + (role == Role.ADMIN ? "administrador" : "despachador"));
        who.setTextFill(javafx.scene.paint.Color.web("#555555"));
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox top = new HBox(14, btnMap, btnRefresh, chkClosed, lblSummary, grow, who, btnOld, btnSetup);
        top.setAlignment(Pos.CENTER_LEFT);
        AnchorPane.setTopAnchor(top, 88.0);
        AnchorPane.setLeftAnchor(top, 15.0);
        AnchorPane.setRightAnchor(top, 15.0);

        buildDeliveries();
        buildZones();
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox deliveriesBox = new VBox(8, table, actions);
        VBox.setVgrow(table, Priority.ALWAYS);
        deliveriesBox.setPadding(new Insets(8, 0, 0, 0));
        Label zonesHelp = new Label("Los cuadrantes se dibujan en el mapa (solo el administrador). Aquí se elige quién reparte cada uno "
                + "y qué despachador lo maneja (vacío = cualquiera). El despachador puede cambiar el repartidor de sus cuadrantes.");
        zonesHelp.setWrapText(true);
        zonesHelp.setTextFill(javafx.scene.paint.Color.web("#555555"));
        VBox zonesBox = new VBox(8, zones, zonesHelp);
        VBox.setVgrow(zones, Priority.ALWAYS);
        zonesBox.setPadding(new Insets(8, 0, 0, 0));
        TabPane tabs = new TabPane(new Tab("Entregas", deliveriesBox), new Tab("Cuadrantes", zonesBox));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        AnchorPane.setTopAnchor(tabs, 122.0);
        AnchorPane.setLeftAnchor(tabs, 15.0);
        AnchorPane.setRightAnchor(tabs, 15.0);
        AnchorPane.setBottomAnchor(tabs, 40.0);

        lblMessage.setTextFill(javafx.scene.paint.Color.web("#b00020"));
        lblMessage.setWrapText(true);
        AnchorPane.setBottomAnchor(lblMessage, 12.0);
        AnchorPane.setLeftAnchor(lblMessage, 15.0);
        AnchorPane.setRightAnchor(lblMessage, 15.0);
        txtAddress.setEditable(false);
        txtAddress.setVisible(false);
        txtAddress.setPrefWidth(560);
        AnchorPane.setBottomAnchor(txtAddress, 8.0);
        AnchorPane.setRightAnchor(txtAddress, 15.0);
        root.getChildren().addAll(title, top, tabs, lblMessage, txtAddress);
    }

    private void buildDeliveries() {
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy");
        table.setItems(rows);
        table.setPlaceholder(new Label("No hay entregas abiertas. Se crean en la caja con el botón Mostrador / Entrega."));
        table.getColumns().add(column("Ticket", 55, e -> e.ticketNumber));
        table.getColumns().add(column("Entrega", 80, e -> e.deliveryDate == null ? "" : date.format(e.deliveryDate)));
        table.getColumns().add(column("Cliente", 150, e -> e.customerName != null ? e.customerName : e.receiver));
        table.getColumns().add(column("Dirección", 200, e -> e.address + (e.lat == null ? "  (sin ubicar)" : "")));
        table.getColumns().add(column("Cuadrante", 95, e -> e.zone == null ? "" : e.zone.name));
        table.getColumns().add(column("Repartidor", 100, e -> personName(e.driver())));
        TableColumn<Entry, Object> status = column("Estado", 105, e -> DeliveryStatusStore.label(e.status));
        status.setCellFactory(c -> new TableCell<Entry, Object>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                Entry e = empty || getTableRow() == null ? null : (Entry) getTableRow().getItem();
                setText(empty || item == null ? null : item.toString());
                setStyle(e == null ? "" : "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-color: " + color(e.status) + ";");
            }
        });
        table.getColumns().add(status);
        table.getColumns().add(column("Total", 75, e -> PurchaseOrderStore.money(e.total)));
        table.getColumns().add(column("Recibido", 75, e -> e.amountReceived == null ? "" : PurchaseOrderStore.money(e.amountReceived)));
        table.getColumns().add(column("Anotaciones", 220, e -> notes(e)));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refreshActions());
    }

    private void buildZones() {
        zones.setItems(zoneRows);
        zones.setPlaceholder(new Label("Todavía no hay cuadrantes: el administrador los dibuja en el mapa."));
        zones.setEditable(true);
        TableColumn<ZoneStore.Zone, Object> name = new TableColumn<>("Cuadrante");
        name.setPrefWidth(200);
        name.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().name));
        name.setCellFactory(c -> new TableCell<ZoneStore.Zone, Object>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                ZoneStore.Zone z = empty || getTableRow() == null ? null : (ZoneStore.Zone) getTableRow().getItem();
                setText(empty || item == null ? null : "■  " + item);
                setStyle(z == null ? "" : "-fx-text-fill: " + z.color + "; -fx-font-weight: bold;");
            }
        });
        zones.getColumns().add(name);
        zones.getColumns().add(personColumn("Repartidor", "Sin repartidor", true));
        zones.getColumns().add(personColumn("Despachador", "Cualquiera", false));
        TableColumn<ZoneStore.Zone, Object> count = new TableColumn<>("Entregas abiertas");
        count.setPrefWidth(130);
        count.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(
                rows.stream().filter(e -> e.zone != null && e.zone.id == c.getValue().id).count()));
        zones.getColumns().add(count);
    }

    /** Column with a person picker that saves at once. */
    private TableColumn<ZoneStore.Zone, Object> personColumn(String title, String none, boolean driver) {
        TableColumn<ZoneStore.Zone, Object> col = new TableColumn<>(title);
        col.setPrefWidth(220);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(driver ? c.getValue().driver : c.getValue().dispatcher));
        col.setCellFactory(c -> new TableCell<ZoneStore.Zone, Object>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                ZoneStore.Zone z = empty || getTableRow() == null ? null : (ZoneStore.Zone) getTableRow().getItem();
                if (z == null) {
                    setGraphic(null);
                    return;
                }
                ComboBox<String> box = new ComboBox<>();
                box.getItems().add("");
                box.getItems().addAll(people.keySet());
                box.setConverter(new StringConverter<String>() {
                    @Override
                    public String toString(String id) {
                        return id == null || id.isEmpty() ? none : personName(id);
                    }

                    @Override
                    public String fromString(String s) {
                        return s;
                    }
                });
                box.setValue(item == null ? "" : item.toString());
                box.setMaxWidth(Double.MAX_VALUE);
                box.setDisable(driver ? !canAssignDriver(z) : role != Role.ADMIN);
                box.setOnAction(e -> saveZoneStaff(z, driver, box.getValue()));
                setGraphic(box);
                setText(null);
            }
        });
        return col;
    }

    private static <T> TableColumn<T, Object> column(String title, double width, java.util.function.Function<T, Object> value) {
        TableColumn<T, Object> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(value.apply(c.getValue())));
        return col;
    }

    private static String color(String status) {
        switch (status) {
            case DeliveryStatusStore.PENDING:
                return "#F4A261";
            case DeliveryStatusStore.READY:
                return "#9C27B0";
            case DeliveryStatusStore.ON_ROUTE:
                return "#4A90E2";
            case DeliveryStatusStore.DONE:
                return "#2A9D8F";
            case DeliveryStatusStore.FAILED:
                return "#E63946";
            default:
                return "#999999";
        }
    }

    private static String notes(Entry e) {
        List<String> parts = new ArrayList<>();
        if (e.issue != null && !DeliveryStatusStore.ISSUE_NONE.equals(e.issue)) {
            parts.add(issueLabel(e.issue).toUpperCase());
        }
        if (e.notes != null && !e.notes.isEmpty()) {
            parts.add(e.notes);
        }
        if (parts.isEmpty() && e.instructions != null && !e.instructions.isEmpty()) {
            parts.add("Indicaciones: " + e.instructions);
        }
        return String.join(" · ", parts);
    }

    private static String issueLabel(String issue) {
        switch (issue == null ? "" : issue) {
            case "faltante":
                return "Faltó mercancía";
            case "rechazo":
                return "Rechazó mercancía";
            case "danado":
                return "Mercancía dañada";
            case "otro":
                return "Otra incidencia";
            default:
                return "Sin incidencias";
        }
    }

    private String personName(String id) {
        return id == null ? "" : people.getOrDefault(id, "?");
    }

    // ---------------- Data ----------------

    public final void reloadData() {
        lblMessage.setText("");
        txtAddress.setVisible(false);
        try (Statement st = connection().createStatement(); ResultSet rs = st.executeQuery(
                "SELECT id, name FROM people WHERE visible = 1 ORDER BY name")) {
            people.clear();
            while (rs.next()) {
                people.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException ex) {
            error("No se pudieron leer los usuarios", ex);
        }
        loadRows();
    }

    private void loadRows() {
        String selected = table.getSelectionModel().getSelectedItem() == null ? null
                : table.getSelectionModel().getSelectedItem().ticketId;
        try {
            Connection con = connection();
            List<Entry> list = DeliveryStatusStore.load(con, chkClosed.isSelected() ? 1 : 0);
            rows.setAll(list);
            zoneRows.setAll(ZoneStore.load(con, false));
        } catch (SQLException ex) {
            error("No se pudieron leer las entregas", ex);
        }
        long open = rows.stream().filter(e -> !DeliveryStatusStore.DONE.equals(e.status)
                && !DeliveryStatusStore.CANCELLED.equals(e.status)).count();
        long onRoute = rows.stream().filter(e -> DeliveryStatusStore.ON_ROUTE.equals(e.status)).count();
        long noPin = rows.stream().filter(e -> e.lat == null).count();
        lblSummary.setText(open + " abiertas · " + onRoute + " en ruta" + (noPin > 0 ? " · " + noPin + " sin ubicar" : ""));
        for (Entry e : rows) {
            if (e.ticketId.equals(selected)) {
                table.getSelectionModel().select(e);
            }
        }
        zones.refresh();
        refreshActions();
    }

    private boolean canAssignDriver(ZoneStore.Zone z) {
        return role == Role.ADMIN || z.dispatcher == null || z.dispatcher.equals(Start.getActiveUserId());
    }

    private void saveZoneStaff(ZoneStore.Zone z, boolean driver, String personId) {
        String value = personId == null || personId.isEmpty() ? null : personId;
        if (value == null ? (driver ? z.driver : z.dispatcher) == null : value.equals(driver ? z.driver : z.dispatcher)) {
            return;
        }
        try {
            if (driver) {
                z.driver = value;
            } else {
                z.dispatcher = value;
            }
            ZoneStore.save(connection(), z);
            lblMessage.setText("");
        } catch (SQLException ex) {
            error("No se pudo guardar el cuadrante", ex);
        }
        loadRows();
    }

    // ---------------- Actions ----------------

    private void refreshActions() {
        actions.getChildren().clear();
        Entry e = table.getSelectionModel().getSelectedItem();
        if (e == null) {
            Label l = new Label("Elige una entrega para cambiar su estado, su repartidor o ver su historial.");
            l.setTextFill(javafx.scene.paint.Color.web("#555555"));
            actions.getChildren().add(l);
            return;
        }
        Label l = new Label("#" + e.ticketNumber + ":");
        l.setStyle("-fx-font-weight: bold;");
        actions.getChildren().add(l);
        for (String to : DeliveryStatusStore.allowed(role, Start.getActiveUserId(), e)) {
            Button b = new Button(actionText(e.status, to));
            b.setStyle("-fx-base: " + color(to) + ";");
            b.setOnAction(ev -> change(e, to));
            actions.getChildren().add(b);
        }
        if (!DeliveryStatusStore.allowed(role, Start.getActiveUserId(), e).isEmpty() || role == Role.ADMIN) {
            Button driver = new Button("Cambiar repartidor...");
            driver.setOnAction(ev -> chooseDriver(e));
            actions.getChildren().add(driver);
        }
        Button history = new Button("Historial");
        history.setOnAction(ev -> showHistory(e));
        actions.getChildren().add(history);
    }

    static String actionText(String from, String to) {
        switch (to) {
            case DeliveryStatusStore.READY:
                return DeliveryStatusStore.ON_ROUTE.equals(from) ? "Regresar a preparada" : "Preparada";
            case DeliveryStatusStore.ON_ROUTE:
                return DeliveryStatusStore.DONE.equals(from) ? "Reabrir" : "Salió a ruta";
            case DeliveryStatusStore.DONE:
                return "✔ Completada...";
            case DeliveryStatusStore.FAILED:
                return "No entregada...";
            case DeliveryStatusStore.PENDING:
                return DeliveryStatusStore.FAILED.equals(from) ? "Reprogramar" : "Regresar a por surtir";
            case DeliveryStatusStore.CANCELLED:
                return "Cancelar entrega";
            default:
                return to;
        }
    }

    private void change(Entry e, String to) {
        DeliveryStatusStore.Change c = new DeliveryStatusStore.Change();
        c.opId = UUID.randomUUID().toString();
        c.ticketId = e.ticketId;
        c.status = to;
        c.role = role;
        c.userId = Start.getActiveUserId();
        c.userName = Start.getActiveUser();
        if (DeliveryStatusStore.DONE.equals(to) || DeliveryStatusStore.FAILED.equals(to)) {
            if (!report(e, c)) {
                return;
            }
        } else if (DeliveryStatusStore.CANCELLED.equals(to) || DeliveryStatusStore.DONE.equals(e.status)) {
            ButtonType yes = new ButtonType("Sí", ButtonBar.ButtonData.YES);
            ButtonType no = new ButtonType("No", ButtonBar.ButtonData.NO);
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, (DeliveryStatusStore.CANCELLED.equals(to)
                    ? "¿Cancelar la entrega del ticket #" : "¿Reabrir la entrega del ticket #") + e.ticketNumber + "?", yes, no);
            a.setHeaderText(null);
            if (a.showAndWait().orElse(no) != yes) {
                return;
            }
        }
        try {
            DeliveryStatusStore.apply(connection(), c);
            lblMessage.setText("");
        } catch (DeliveryStatusStore.Refused ex) {
            lblMessage.setText(ex.getMessage());
        } catch (SQLException ex) {
            error("No se pudo guardar el cambio (no se guardó nada)", ex);
        }
        loadRows();
    }

    /** What the driver reported: amount received, incident and notes. False if cancelled. */
    private boolean report(Entry e, DeliveryStatusStore.Change c) {
        boolean done = DeliveryStatusStore.DONE.equals(c.status);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(done ? "Entrega completada" : "No se entregó");
        d.setHeaderText("Ticket #" + e.ticketNumber + " · " + (e.customerName != null ? e.customerName : "") + " · total "
                + PurchaseOrderStore.money(e.total));
        TextField amount = new TextField(done ? String.format(java.util.Locale.ROOT, "%.2f", e.total) : "0");
        ComboBox<String> issue = new ComboBox<>(FXCollections.observableArrayList(DeliveryStatusStore.ISSUES));
        issue.setConverter(new StringConverter<String>() {
            @Override
            public String toString(String s) {
                return issueLabel(s);
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        issue.setValue(DeliveryStatusStore.ISSUE_NONE);
        TextArea notes = new TextArea();
        notes.setPrefRowCount(3);
        notes.setWrapText(true);
        notes.setPromptText(done ? "Ej.: faltaron 2 cajas de lápices" : "¿Por qué no se entregó? (cerrado, no lo quiso, dirección equivocada...)");
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.addRow(0, new Label("Monto recibido"), amount);
        g.addRow(1, new Label("Incidencia"), issue);
        g.addRow(2, new Label(done ? "Anotaciones" : "Motivo"), notes);
        Label err = new Label();
        err.setTextFill(javafx.scene.paint.Color.web("#b00020"));
        g.add(err, 0, 3, 2, 1);
        d.getDialogPane().setContent(g);
        ButtonType save = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(save, CANCEL);
        d.getDialogPane().lookupButton(save).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            String problem = null;
            Double value = null;
            try {
                value = Double.parseDouble(amount.getText().trim().replace(",", "").replace("$", ""));
                if (value < 0) {
                    problem = "El monto no puede ser negativo.";
                }
            } catch (NumberFormatException ex) {
                problem = done ? "Escribe cuánto se recibió (0 si no se cobró)." : null;
            }
            String text = notes.getText().trim();
            if (problem == null && !done && text.isEmpty()) {
                problem = "Escribe por qué no se entregó.";
            }
            if (problem == null && !DeliveryStatusStore.ISSUE_NONE.equals(issue.getValue()) && text.isEmpty()) {
                problem = "Describe la incidencia en las anotaciones.";
            }
            if (problem != null) {
                err.setText(problem);
                ev.consume();
                return;
            }
            c.amountReceived = value;
            c.issue = issue.getValue();
            c.notes = text.isEmpty() ? null : text;
        });
        Platform.runLater(amount::requestFocus);
        return d.showAndWait().orElse(CANCEL) == save;
    }

    private void chooseDriver(Entry e) {
        List<String> names = new ArrayList<>();
        String zoneDriver = e.zone == null || e.zone.driver == null ? "nadie" : personName(e.zone.driver);
        names.add("El del cuadrante (" + zoneDriver + ")");
        names.addAll(people.values());
        javafx.scene.control.ChoiceDialog<String> d = new javafx.scene.control.ChoiceDialog<>(
                e.driverOverride == null ? names.get(0) : personName(e.driverOverride), names);
        d.setTitle("Repartidor");
        d.setHeaderText("Repartidor solo para el ticket #" + e.ticketNumber);
        Optional<String> r = d.showAndWait();
        if (!r.isPresent()) {
            return;
        }
        String id = null;
        for (Map.Entry<String, String> p : people.entrySet()) {
            if (p.getValue().equals(r.get())) {
                id = p.getKey();
            }
        }
        try {
            DeliveryStatusStore.setDriver(connection(), e.ticketId, id, Start.getActiveUser());
        } catch (SQLException ex) {
            error("No se pudo cambiar el repartidor", ex);
        }
        loadRows();
    }

    private void showHistory(Entry e) {
        StringBuilder sb = new StringBuilder();
        try {
            for (String[] h : DeliveryStatusStore.history(connection(), e.ticketId)) {
                sb.append(h[0], 0, Math.min(16, h[0].length())).append("  ").append(DeliveryStatusStore.label(h[1]))
                        .append(" → ").append(DeliveryStatusStore.label(h[2])).append("  (").append(h[3]).append(")");
                if (h[4] != null) {
                    sb.append("  recibido ").append(PurchaseOrderStore.money(Double.parseDouble(h[4])));
                }
                if (h[5] != null && !DeliveryStatusStore.ISSUE_NONE.equals(h[5])) {
                    sb.append("  ").append(issueLabel(h[5]));
                }
                if (h[6] != null) {
                    sb.append("  “").append(h[6]).append("”");
                }
                sb.append('\n');
            }
        } catch (SQLException ex) {
            error("No se pudo leer el historial", ex);
            return;
        }
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Historial");
        a.setHeaderText("Ticket #" + e.ticketNumber + " · " + (e.customerName != null ? e.customerName : ""));
        TextArea t = new TextArea(sb.length() == 0 ? "Sin cambios todavía: está por surtir desde que se vendió." : sb.toString());
        t.setEditable(false);
        t.setWrapText(true);
        t.setPrefSize(640, 260);
        a.getDialogPane().setContent(t);
        a.showAndWait();
    }

    // ---------------- Map ----------------

    private void openMap() {
        try {
            Connection con = connection();
            if (PosSettings.get(con, MAPS_KEY, "").isEmpty()) {
                lblMessage.setText("Falta la clave de Google Maps: el administrador la pone en \"Configurar mapa...\".");
                if (role == Role.ADMIN) {
                    configureMap();
                }
                if (PosSettings.get(con, MAPS_KEY, "").isEmpty()) {
                    return;
                }
            }
            int port = MapServer.DEFAULT_PORT;
            try {
                port = Integer.parseInt(PosSettings.get(con, MAPS_PORT, String.valueOf(MapServer.DEFAULT_PORT)));
            } catch (NumberFormatException ex) {
                // default port
            }
            String address = MapServer.start(AppConfig.getDatabaseURL(), AppConfig.getDatabaseUser(),
                    AppConfig.getClearDatabasePassword(),
                    new MapServer.User(Start.getActiveUserId(), Start.getActiveUser(), role),
                    () -> Platform.runLater(this::loadRows), port);
            new Thread(() -> {
                try {
                    if (!openAppWindow(address)) {
                        Desktop.getDesktop().browse(new URI(address));
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        lblMessage.setText("No se pudo abrir el navegador. Copia esta dirección en Chrome o Vivaldi:");
                        txtAddress.setText(address);
                        txtAddress.setVisible(true);
                    });
                }
            }, "abrir-mapa").start();
            lblMessage.setText("");
        } catch (Exception ex) {
            error("No se pudo abrir el mapa", ex);
        }
    }

    /**
     * On Windows the map opens in its own window (Edge or Chrome in app mode:
     * no tabs or address bar, like part of Chromis). False = neither is
     * installed and the normal browser is used.
     */
    static boolean openAppWindow(String address) {
        if (!System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
            return false;
        }
        List<String> exes = new ArrayList<>();
        for (String env : new String[]{"ProgramFiles(x86)", "ProgramFiles", "LOCALAPPDATA"}) {
            String dir = System.getenv(env);
            if (dir != null) {
                exes.add(dir + "\\Microsoft\\Edge\\Application\\msedge.exe");
                exes.add(dir + "\\Google\\Chrome\\Application\\chrome.exe");
            }
        }
        for (String exe : exes) {
            if (new java.io.File(exe).isFile()) {
                try {
                    new ProcessBuilder(exe, "--app=" + address, "--window-size=1400,900").start();
                    return true;
                } catch (java.io.IOException ex) {
                    LOGGER.log(Level.INFO, "no se pudo abrir " + exe, ex);
                }
            }
        }
        return false;
    }

    // ---------------- Old deliveries ----------------

    /** Admin: closes as delivered the open deliveries sold before a date (old ones nobody marked). */
    private void closeOld() {
        if (role != Role.ADMIN) {
            return;
        }
        javafx.scene.control.DatePicker date = new javafx.scene.control.DatePicker(java.time.LocalDate.now().minusDays(7));
        Label count = new Label();
        Runnable recount = () -> {
            if (date.getValue() == null) {
                count.setText("");
                return;
            }
            try {
                int n = DeliveryStatusStore.countOld(connection(), startOf(date.getValue()));
                count.setText(n == 0 ? "No hay entregas abiertas vendidas antes de esa fecha."
                        : n + " entrega(s) abierta(s) vendida(s) antes de esa fecha.");
            } catch (SQLException ex) {
                count.setText("No se pudo contar: " + ex.getMessage());
            }
        };
        date.valueProperty().addListener((o, a, b) -> recount.run());
        recount.run();
        Label help = new Label("Se marcan como entregadas (completadas) sin monto recibido, con la nota \"Cerrada en bloque\" "
                + "y tu nombre en el historial de cada una. Sirve para las entregas viejas que nadie marcó.");
        help.setWrapText(true);
        help.setMaxWidth(480);
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.addRow(0, new Label("Vendidas antes del"), date);
        g.add(count, 0, 1, 2, 1);
        g.add(help, 0, 2, 2, 1);
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Cerrar entregas viejas");
        d.setHeaderText(null);
        d.getDialogPane().setContent(g);
        ButtonType ok = new ButtonType("Cerrar entregas", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, CANCEL);
        if (d.showAndWait().orElse(CANCEL) != ok || date.getValue() == null) {
            return;
        }
        try {
            java.sql.Timestamp before = startOf(date.getValue());
            int n = DeliveryStatusStore.countOld(connection(), before);
            if (n == 0) {
                lblMessage.setText("No había entregas abiertas anteriores a esa fecha.");
                return;
            }
            ButtonType yes = new ButtonType("Sí", ButtonBar.ButtonData.YES);
            ButtonType no = new ButtonType("No", ButtonBar.ButtonData.NO);
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "¿Cerrar " + n + " entrega(s) como entregadas?", yes, no);
            a.setHeaderText(null);
            if (a.showAndWait().orElse(no) != yes) {
                return;
            }
            int closed = DeliveryStatusStore.closeOld(connection(), before, Start.getActiveUserId(), Start.getActiveUser());
            loadRows();
            lblMessage.setText("Se cerraron " + closed + " entrega(s) anteriores al "
                    + date.getValue().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".");
        } catch (SQLException ex) {
            error("No se pudieron cerrar las entregas (no se cambió ninguna)", ex);
        }
    }

    private static java.sql.Timestamp startOf(java.time.LocalDate d) {
        return java.sql.Timestamp.valueOf(d.atStartOfDay());
    }

    private void configureMap() {
        try {
            Connection con = connection();
            TextField key = new TextField(PosSettings.get(con, MAPS_KEY, ""));
            key.setPrefColumnCount(40);
            TextField center = new TextField(PosSettings.get(con, MAPS_CENTER, ""));
            center.setPromptText("19.4326,-99.1332");
            TextField port = new TextField(PosSettings.get(con, MAPS_PORT, String.valueOf(MapServer.DEFAULT_PORT)));
            GridPane g = new GridPane();
            g.setHgap(10);
            g.setVgap(8);
            g.addRow(0, new Label("Clave de Google Maps"), key);
            g.addRow(1, new Label("Centro del mapa (lat,lng)"), center);
            g.addRow(2, new Label("Puerto local"), port);
            Label help = new Label("En Google Cloud, la clave necesita Maps JavaScript API y Geocoding API, restringida a sitios web "
                    + "http://127.0.0.1:" + port.getText().trim() + "/* (el mapa se abre solo en esta PC). "
                    + "El centro vacío toma la ubicación de un cliente.");
            help.setWrapText(true);
            help.setMaxWidth(560);
            g.add(help, 0, 3, 2, 1);
            Dialog<ButtonType> d = new Dialog<>();
            d.setTitle("Configurar mapa");
            d.setHeaderText(null);
            d.getDialogPane().setContent(g);
            ButtonType ok = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
            d.getDialogPane().getButtonTypes().addAll(ok, CANCEL);
            if (d.showAndWait().orElse(CANCEL) != ok) {
                return;
            }
            String c = center.getText().trim().replace(" ", "");
            if (!c.isEmpty() && !c.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
                lblMessage.setText("El centro debe ser latitud,longitud (por ejemplo 19.4326,-99.1332).");
                return;
            }
            String p = port.getText().trim();
            if (!p.matches("\\d{4,5}") || Integer.parseInt(p) > 65535) {
                lblMessage.setText("El puerto debe ser un número entre 1024 y 65535.");
                return;
            }
            PosSettings.put(con, MAPS_KEY, key.getText().trim());
            PosSettings.put(con, MAPS_CENTER, c);
            PosSettings.put(con, MAPS_PORT, p);
            lblMessage.setText("");
        } catch (SQLException ex) {
            error("No se pudo guardar la configuración del mapa", ex);
        }
    }

    private void error(String what, Exception ex) {
        LOGGER.log(Level.WARNING, what, ex);
        lblMessage.setText(what + ": " + ex.getMessage());
    }
}
