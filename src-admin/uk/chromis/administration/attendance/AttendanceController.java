package uk.chromis.administration.attendance;

import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.pos.settings.PosSettings;

/** Settings of automatic check in / check out, and the latest records. */
public class AttendanceController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(AttendanceController.class.getName());
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd/MM/yyyy HH:mm");

    @FXML public AnchorPane apAnchor;
    @FXML public CheckBox chkCheckIn;
    @FXML public TextField txtHours;
    @FXML public CheckBox chkCheckOut;
    @FXML public Label lblMessage;
    @FXML public TableView<String[]> table;
    @FXML public TableColumn<String[], String> colUser;
    @FXML public TableColumn<String[], String> colIn;
    @FXML public TableColumn<String[], String> colOut;
    @FXML public TableColumn<String[], String> colHours;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colUser.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()[0]));
        colIn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()[1]));
        colOut.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()[2]));
        colHours.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()[3]));
        table.setPlaceholder(new Label("Todavía no hay entradas registradas."));
        txtHours.textProperty().addListener((obs, o, n) -> {
            if (!n.matches("\\d{0,2}([.,]\\d{0,2})?")) {
                txtHours.setText(o);
            }
        });
        chkCheckIn.selectedProperty().addListener((obs, o, n) -> txtHours.setDisable(!n));
        reloadData();
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }

    public void reloadData() {
        lblMessage.setText("");
        try {
            Connection con = connection();
            chkCheckIn.setSelected("1".equals(PosSettings.get(con, PosSettings.ATTENDANCE_CHECKIN, "0")));
            txtHours.setText(PosSettings.get(con, PosSettings.ATTENDANCE_HOURS, "6"));
            txtHours.setDisable(!chkCheckIn.isSelected());
            chkCheckOut.setSelected("1".equals(PosSettings.get(con, PosSettings.ATTENDANCE_CHECKOUT, "0")));
            table.getItems().clear();
            try (Statement st = con.createStatement();
                    ResultSet rs = st.executeQuery("SELECT COALESCE(p.name, s.pplid), s.startshift, s.endshift "
                            + "FROM shifts s LEFT JOIN people p ON p.id = s.pplid ORDER BY s.startshift DESC LIMIT 200")) {
                while (rs.next()) {
                    Timestamp in = rs.getTimestamp(2);
                    Timestamp out = rs.getTimestamp(3);
                    String hours = out == null ? "abierta"
                            : String.format("%d:%02d", (out.getTime() - in.getTime()) / 3_600_000L,
                                    (out.getTime() - in.getTime()) / 60_000L % 60);
                    table.getItems().add(new String[]{rs.getString(1), DATE.format(in),
                        out == null ? "" : DATE.format(out), hours});
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo leer la asistencia", ex);
            lblMessage.setText("No se pudo leer la asistencia: " + ex.getMessage());
        }
    }

    @FXML
    public void refresh() {
        reloadData();
    }

    @FXML
    public void save() {
        String hours = txtHours.getText() == null || txtHours.getText().isBlank() ? "6" : txtHours.getText().trim().replace(',', '.');
        try {
            Connection con = connection();
            PosSettings.put(con, PosSettings.ATTENDANCE_CHECKIN, chkCheckIn.isSelected() ? "1" : "0");
            PosSettings.put(con, PosSettings.ATTENDANCE_HOURS, hours);
            PosSettings.put(con, PosSettings.ATTENDANCE_CHECKOUT, chkCheckOut.isSelected() ? "1" : "0");
            txtHours.setText(hours);
            lblMessage.setText("Guardado. Se aplica en las cajas desde el siguiente inicio de sesión o corte.");
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo guardar la asistencia", ex);
            lblMessage.setText("No se pudo guardar: " + ex.getMessage());
        }
    }
}
