package uk.chromis.administration.heldtickets;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import java.util.function.Function;
import uk.chromis.administration.base.MenuPanelInterface;
import uk.chromis.administration.main.Start;
import uk.chromis.administration.main.StartController;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.pos.heldtickets.HeldTicketArchive;
import uk.chromis.pos.heldtickets.HeldTicketArchive.Entry;

/**
 * Admin → Utilities → Ventas en espera borradas: held tickets that left the
 * list without being charged (deleted one by one, with "borrar todas", or lost
 * when Chromis closed with one open), who deleted them and how, and a button
 * to put them back in the held tickets of every till.
 */
public class DeletedHeldTickets implements MenuPanelInterface {

    private static final Logger LOGGER = Logger.getLogger(DeletedHeldTickets.class.getName());
    private static final String ALL_USERS = "Todos los usuarios";
    private static final long UNREAD_WARNING_MS = 5 * 60_000L;
    private static AnchorPane root;
    private static DeletedHeldTickets panel;
    public StringProperty jobClass = new SimpleStringProperty();

    private final TableView<Entry> table = new TableView<>();
    private final ComboBox<String> cmbDays = new ComboBox<>(FXCollections.observableArrayList(
            "Últimos 7 días", "Últimos 30 días", "Últimos " + HeldTicketArchive.KEEP_DAYS + " días"));
    private final ComboBox<String> cmbUser = new ComboBox<>();
    private final Label lblSummary = new Label();
    private final Label lblMessage = new Label();
    private final TextArea txtDetail = new TextArea();
    private final Button btnRestore = new Button("Restaurar en ventas en espera");
    private List<Entry> rows = List.of();

    @Override
    public void showPanel() throws Exception {
        StartController.jobClass.bind(jobClass);
        if (panel == null) {
            panel = this;
            root = new AnchorPane();
            root.setMinSize(0, 0);
            root.setPrefHeight(720);
            build();
        }
        jobClass.setValue(getClass().getName());
        panel.reloadData();
        Start.INTERNALANCHOR.getChildren().clear();
        Start.INTERNALANCHOR.getChildren().add(root);
        // As wide as the window, so the table and the buttons always fit
        AnchorPane.setTopAnchor(root, 0.0);
        AnchorPane.setLeftAnchor(root, 0.0);
        AnchorPane.setRightAnchor(root, 0.0);
    }

    @Override
    public void processDataChecks() {
        // Nothing is edited here
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }

    private void build() {
        Label title = new Label("Ventas en espera borradas");
        title.setFont(Font.font("System", FontWeight.BOLD, FontPosture.ITALIC, 14));
        AnchorPane.setTopAnchor(title, 57.0);
        AnchorPane.setLeftAnchor(title, 10.0);

        cmbDays.getSelectionModel().select(1);
        cmbDays.setOnAction(e -> reloadData());
        cmbUser.setOnAction(e -> fill());
        cmbUser.setPrefWidth(200);
        Button btnRefresh = new Button("Actualizar");
        btnRefresh.setOnAction(e -> reloadData());
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        btnRestore.setStyle("-fx-font-weight: bold;");
        btnRestore.setDisable(true);
        btnRestore.setOnAction(e -> restore());
        HBox top = new HBox(12, cmbDays, cmbUser, btnRefresh, lblSummary, grow, btnRestore);
        top.setAlignment(Pos.CENTER_LEFT);
        AnchorPane.setTopAnchor(top, 88.0);
        AnchorPane.setLeftAnchor(top, 15.0);
        AnchorPane.setRightAnchor(top, 15.0);

        SimpleDateFormat when = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        table.getColumns().add(column("Borrada", 115, e -> when.format(e.deletedAt)));
        table.getColumns().add(column("Venta en espera", 230, e -> e.name));
        table.getColumns().add(column("Cliente", 130, e -> e.customer == null ? "" : e.customer));
        table.getColumns().add(column("Productos", 70, e -> known(e) ? String.valueOf(e.lineCount) : ""));
        TableColumn<Entry, String> total = column("Total", 75,
                e -> known(e) ? String.format(Locale.ROOT, "%,.2f", e.total) : "");
        total.setStyle("-fx-alignment: CENTER-RIGHT;");
        table.getColumns().add(total);
        table.getColumns().add(column("La capturó", 95, e -> e.appUser == null ? "" : e.appUser));
        table.getColumns().add(column("Cómo se borró", 210, e -> e.how.label));
        table.getColumns().add(column("La borró", 95, e -> e.deletedBy == null ? "" : e.deletedBy));
        table.getColumns().add(column("Equipo", 100, DeletedHeldTickets::place));
        table.setPlaceholder(new Label("No hay ventas en espera borradas en esos días."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, e) -> showDetail(e));
        AnchorPane.setTopAnchor(table, 122.0);
        AnchorPane.setLeftAnchor(table, 15.0);
        AnchorPane.setRightAnchor(table, 15.0);
        AnchorPane.setBottomAnchor(table, 230.0);

        txtDetail.setEditable(false);
        txtDetail.setWrapText(true);
        txtDetail.setPrefHeight(130);
        AnchorPane.setBottomAnchor(txtDetail, 94.0);
        AnchorPane.setLeftAnchor(txtDetail, 15.0);
        AnchorPane.setRightAnchor(txtDetail, 15.0);

        Label help = new Label("Aquí queda una copia de cada venta en espera que salió de la lista sin cobrarse: borrada con el bote, "
                + "con \"borrar todas\" (clic derecho en el botón de ventas en espera o F10) o perdida porque Chromis se cerró con ella "
                + "abierta. Restaurar la regresa a las ventas en espera de todas las cajas. Las copias se guardan "
                + HeldTicketArchive.KEEP_DAYS + " días; las ventas que sí se cobraron no aparecen.");
        help.setWrapText(true);
        help.setTextFill(javafx.scene.paint.Color.web("#555555"));
        AnchorPane.setBottomAnchor(help, 34.0);
        AnchorPane.setLeftAnchor(help, 15.0);
        AnchorPane.setRightAnchor(help, 15.0);

        lblMessage.setWrapText(true);
        AnchorPane.setBottomAnchor(lblMessage, 10.0);
        AnchorPane.setLeftAnchor(lblMessage, 15.0);
        AnchorPane.setRightAnchor(lblMessage, 15.0);
        root.getChildren().addAll(title, top, table, txtDetail, help, lblMessage);
    }

    private static TableColumn<Entry, String> column(String name, double width, Function<Entry, String> value) {
        TableColumn<Entry, String> c = new TableColumn<>(name);
        c.setPrefWidth(width);
        c.setCellValueFactory(d -> new SimpleStringProperty(value.apply(d.getValue())));
        return c;
    }

    /** The PC that deleted it: the Chromis terminal, else the host the database saw. */
    private static String place(Entry e) {
        if (e.terminal != null && !e.terminal.isEmpty()) {
            return e.terminal;
        }
        if (e.dbUser != null && e.dbUser.contains("@")) {
            return e.dbUser.substring(e.dbUser.indexOf('@') + 1);
        }
        return "";
    }

    private void reloadData() {
        message("", false);
        try {
            Connection con = connection();
            String problem = HeldTicketArchive.ensure(con);
            if (problem != null) {
                message(problem + "\nPide al administrador de la base que dé permiso de crear triggers al usuario de Chromis.", true);
            }
            int days = cmbDays.getSelectionModel().getSelectedIndex() == 0 ? 7
                    : cmbDays.getSelectionModel().getSelectedIndex() == 1 ? 30 : HeldTicketArchive.KEEP_DAYS;
            rows = HeldTicketArchive.list(con, days);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Ventas en espera borradas", ex);
            message("No se pudieron leer las ventas en espera borradas: " + ex.getMessage(), true);
            rows = List.of();
        }
        String selected = cmbUser.getValue();
        TreeSet<String> users = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Entry e : rows) {
            if (e.appUser != null && !e.appUser.isEmpty()) {
                users.add(e.appUser);
            }
            if (e.deletedBy != null && !e.deletedBy.isEmpty()) {
                users.add(e.deletedBy);
            }
        }
        cmbUser.getItems().setAll(ALL_USERS);
        cmbUser.getItems().addAll(users);
        cmbUser.setValue(selected != null && cmbUser.getItems().contains(selected) ? selected : ALL_USERS);
        fill();
    }

    /** Rows of the chosen user: the one who made the held ticket or the one who deleted it. */
    private void fill() {
        String u = cmbUser.getValue();
        List<Entry> shown = u == null || ALL_USERS.equals(u) ? rows
                : rows.stream().filter(e -> u.equalsIgnoreCase(e.appUser) || u.equalsIgnoreCase(e.deletedBy)).collect(Collectors.toList());
        table.getItems().setAll(shown);
        double sum = shown.stream().mapToDouble(e -> e.total).sum();
        lblSummary.setText(shown.size() + (shown.size() == 1 ? " venta" : " ventas")
                + (shown.isEmpty() ? "" : String.format(Locale.ROOT, " · $%,.2f", sum)));
        showDetail(table.getSelectionModel().getSelectedItem());
    }

    private void showDetail(Entry e) {
        btnRestore.setDisable(e == null || e.backInList);
        if (e == null) {
            txtDetail.setText("Elige una venta para ver sus productos.");
            return;
        }
        StringBuilder s = new StringBuilder();
        s.append(e.name).append('\n');
        if (e.how == HeldTicketArchive.How.NOT_SAVED) {
            s.append("Chromis no registró que alguien la borrara: se abrió y no se volvió a guardar, o sigue abierta en una caja.\n");
        } else {
            s.append(e.how.text).append(e.deletedBy == null ? "" : " por " + e.deletedBy)
                    .append(e.terminal == null ? "" : " en " + e.terminal).append(".\n");
        }
        if (e.summarized == HeldTicketArchive.READ) {
            s.append('\n').append(e.lines == null ? "" : e.lines);
        } else if (e.fromAudit) {
            s.append("\nProductos registrados por Chromis al borrarla (precio sin impuestos):\n").append(e.lines);
        } else if (e.summarized == HeldTicketArchive.UNREADABLE) {
            s.append("\nLa caja no pudo leer sus productos; se puede restaurar igual.");
        } else if (System.currentTimeMillis() - e.deletedAt.getTime() > UNREAD_WARNING_MS) {
            s.append("\nNinguna caja ha leído sus productos todavía. Las cajas con el chromispos.jar nuevo los leen cada minuto: "
                    + "revisa que todas lo tengan. Se puede restaurar igual.");
        } else {
            s.append("\nSus productos aparecen cuando alguna caja con Chromis POS abierto la lea (en un minuto); se puede restaurar igual.");
        }
        if (e.backInList) {
            s.append("\n\nYa está otra vez en las ventas en espera.");
        }
        txtDetail.setText(s.toString());
    }

    /** Lines and total are known: read by a till, or audited by Chromis. */
    private static boolean known(Entry e) {
        return e.summarized == HeldTicketArchive.READ || e.fromAudit;
    }

    private void restore() {
        Entry e = table.getSelectionModel().getSelectedItem();
        if (e == null) {
            return;
        }
        String text = "Se regresará a las ventas en espera de todas las cajas:\n\n" + e.name
                + (known(e) ? String.format(Locale.ROOT, "\n%d %s, $%,.2f", e.lineCount, e.lineCount == 1 ? "producto" : "productos", e.total) : "");
        if (e.how == HeldTicketArchive.How.NOT_SAVED) {
            text += "\n\nChromis no registró que alguien la borrara. Si alguna caja la tiene abierta en este momento, "
                    + "no la restaures: al guardarla la caja la regresa sola.";
        }
        Alert ask = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.OK, ButtonType.CANCEL);
        ask.setTitle("Restaurar venta en espera");
        ask.setHeaderText("¿Restaurar esta venta en espera?");
        ask.initOwner(root.getScene().getWindow());
        ask.getDialogPane().setPrefWidth(560);
        ask.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        ask.setResizable(true);
        Optional<ButtonType> r = ask.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) {
            return;
        }
        try {
            HeldTicketArchive.restore(connection(), e.archiveId);
            LOGGER.log(Level.INFO, "{0} restauró la venta en espera {1}", new Object[]{Start.getActiveUser(), e.name});
            reloadData();
            message("Listo: \"" + e.name + "\" está otra vez en las ventas en espera. En las cajas aparece en menos de un minuto (o al tocar el carrito).", false);
        } catch (SQLException ex) {
            message("No se pudo restaurar: " + ex.getMessage(), true);
        }
    }

    private void message(String text, boolean error) {
        lblMessage.setText(text);
        lblMessage.setTextFill(javafx.scene.paint.Color.web(error ? "#b00020" : "#1b5e20"));
    }
}
