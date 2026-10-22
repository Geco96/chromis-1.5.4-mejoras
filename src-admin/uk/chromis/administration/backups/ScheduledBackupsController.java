package uk.chromis.administration.backups;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.pos.backup.BackupService;
import uk.chromis.pos.forms.AppConfig;

/** Settings and status of the scheduled backups run by Chromis POS. */
public class ScheduledBackupsController implements Initializable {

    private static final Logger LOGGER = Logger.getLogger(ScheduledBackupsController.class.getName());
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    @FXML
    public AnchorPane apAnchor;
    @FXML
    public CheckBox chkEnabled;
    @FXML
    public ComboBox<String> cmbTerminal;
    @FXML
    public TextField txtFolder;
    @FXML
    public TextField txtCopyFolder;
    @FXML
    public TextField txtInterval;
    @FXML
    public TextField txtKeep;
    @FXML
    public Button btnSave;
    @FXML
    public Button btnRequest;
    @FXML
    public Button btnBackupHere;
    @FXML
    public Label lblLastBackup;
    @FXML
    public Label lblLastAttempt;
    @FXML
    public Label lblCopy;
    @FXML
    public Label lblWorking;
    @FXML
    public TextArea txtStatus;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        onlyDigits(txtInterval);
        onlyDigits(txtKeep);
        reloadData();
    }

    private static void onlyDigits(TextField field) {
        field.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!newValue.matches("\\d{0,4}")) {
                field.setText(oldValue);
            }
        });
    }

    private static Connection connection() throws SQLException {
        return SessionFactory.getInstance().getSession().getConnection();
    }

    public void reloadData() {
        try {
            Connection con = connection();
            cmbTerminal.getItems().clear();
            try (Statement st = con.createStatement();
                    ResultSet rs = st.executeQuery("SELECT terminal_name FROM terminals ORDER BY terminal_name")) {
                while (rs.next()) {
                    cmbTerminal.getItems().add(rs.getString(1));
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "No se pudo leer la lista de terminales", ex);
            }

            BackupService.Settings s = BackupService.loadSettings(con);
            chkEnabled.setSelected(s.enabled);
            cmbTerminal.setValue(s.terminal);
            txtFolder.setText(s.folder == null ? "" : s.folder);
            txtCopyFolder.setText(s.copyFolder == null ? "" : s.copyFolder);
            txtInterval.setText(String.valueOf(s.intervalHours));
            txtKeep.setText(String.valueOf(s.keepCount));
            showStatus(s);
        } catch (SQLException ex) {
            showError("No se pudo leer la configuración de respaldos", ex);
        }
    }

    private void showStatus(BackupService.Settings s) {
        lblLastBackup.setText(formatDate(s.lastBackup));
        lblLastAttempt.setText(formatDate(s.lastAttempt));
        String status = s.lastStatus == null ? "Todavía no se ha hecho ningún respaldo programado." : s.lastStatus;
        if (s.runNow) {
            status = "Respaldo solicitado; la terminal lo hará en el próximo minuto.\n" + status;
        }
        txtStatus.setText(status);
        lblCopy.setText(copyText(s));
        lblCopy.setStyle(s.hasCopyFolder() && s.copyStatus != null && s.copyStatus.startsWith(BackupService.COPY_PENDING)
                ? "-fx-text-fill: #b00020; -fx-font-weight: bold;" : "");
    }

    private static String copyText(BackupService.Settings s) {
        if (!s.hasCopyFolder()) {
            return "Sin copia (solo se guarda en la carpeta local).";
        }
        if (s.copyStatus == null) {
            return "Se copiará después del siguiente respaldo.";
        }
        if (s.copyStatus.startsWith(BackupService.COPY_PENDING)) {
            return "Pendiente: " + s.copyStatus.substring(BackupService.COPY_PENDING.length())
                    + (s.lastCopy == null ? "" : " · última copia completa " + formatDate(s.lastCopy));
        }
        return "Correcta (" + formatDate(s.lastCopy) + ") · " + s.copyStatus.replaceFirst("^Correcta: ", "");
    }

    private static String formatDate(Timestamp ts) {
        return ts == null ? "—" : DATE_FORMAT.format(ts);
    }

    private static int parseInt(String text, int defaultValue) {
        try {
            return text == null || text.isBlank() ? defaultValue : Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @FXML
    public void save() {
        BackupService.Settings s = new BackupService.Settings();
        s.enabled = chkEnabled.isSelected();
        s.terminal = cmbTerminal.getEditor().getText() == null ? null : cmbTerminal.getEditor().getText().trim();
        s.folder = txtFolder.getText() == null ? null : txtFolder.getText().trim();
        s.copyFolder = txtCopyFolder.getText() == null ? null : txtCopyFolder.getText().trim();
        if (s.copyFolder != null && !s.copyFolder.isEmpty() && s.folder != null
                && Paths.get(s.copyFolder).toAbsolutePath().normalize().equals(Paths.get(s.folder).toAbsolutePath().normalize())) {
            alert(AlertType.WARNING, "La carpeta de copia debe ser distinta de la carpeta local.");
            return;
        }
        s.intervalHours = Math.max(1, parseInt(txtInterval.getText(), 24));
        s.keepCount = Math.max(1, parseInt(txtKeep.getText(), 30));
        if (s.enabled && (s.terminal == null || s.terminal.isEmpty() || s.folder == null || s.folder.isEmpty())) {
            alert(AlertType.WARNING, "Para activar los respaldos indica la terminal y la carpeta de destino.");
            return;
        }
        try {
            BackupService.saveSettings(connection(), s);
            reloadData();
            alert(AlertType.INFORMATION, "Configuración de respaldos guardada.");
        } catch (SQLException ex) {
            showError("No se pudo guardar la configuración de respaldos", ex);
        }
    }

    @FXML
    public void refresh() {
        reloadData();
    }

    @FXML
    public void chooseFolder() {
        File folder = chooseDirectory("Carpeta de destino de los respaldos", txtFolder.getText());
        if (folder != null) {
            txtFolder.setText(folder.getAbsolutePath());
        }
    }

    @FXML
    public void chooseCopyFolder() {
        File folder = chooseDirectory("Carpeta de Google Drive para la copia", txtCopyFolder.getText());
        if (folder != null) {
            txtCopyFolder.setText(folder.getAbsolutePath());
        }
    }

    @FXML
    public void requestBackup() {
        try {
            BackupService.Settings s = BackupService.loadSettings(connection());
            if (!s.enabled) {
                alert(AlertType.WARNING, "Primero activa y guarda los respaldos automáticos.");
                return;
            }
            BackupService.requestBackup(connection());
            reloadData();
            alert(AlertType.INFORMATION, "Se pidió el respaldo a la terminal '" + s.terminal
                    + "'. Chromis POS debe estar abierto en ella; en un minuto pulsa 'Actualizar' para ver el resultado.");
        } catch (SQLException ex) {
            showError("No se pudo solicitar el respaldo", ex);
        }
    }

    @FXML
    public void backupHere() {
        File folder = chooseDirectory("Guardar respaldo en", txtFolder.getText());
        if (folder == null) {
            return;
        }
        int keep = Math.max(1, parseInt(txtKeep.getText(), 30));
        BackupService service = new BackupService(AppConfig.getDatabaseURL(), AppConfig.getDatabaseUser(),
                AppConfig.getClearDatabasePassword(), null, null);
        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return service.backupNow(Paths.get(folder.getAbsolutePath()), keep);
            }
        };
        setWorking(true);
        task.setOnSucceeded(e -> {
            setWorking(false);
            reloadData();
            String message = "Respaldo guardado en:\n" + task.getValue();
            if (!service.getLastWarnings().isEmpty()) {
                message += "\n\nNo se incluyeron por falta de permisos: " + String.join(", ", service.getLastWarnings());
            }
            try {
                BackupService.Settings s = BackupService.loadSettings(connection());
                if (s.hasCopyFolder() && s.folder != null
                        && Paths.get(s.folder.trim()).toAbsolutePath().normalize().equals(task.getValue().getParent().toAbsolutePath().normalize())) {
                    message += "\n\nCopia a Drive: " + copyText(s);
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.WARNING, "No se pudo leer el estado de la copia", ex);
            }
            alert(AlertType.INFORMATION, message);
        });
        task.setOnFailed(e -> {
            setWorking(false);
            showError("No se pudo hacer el respaldo", task.getException());
        });
        Thread thread = new Thread(task, "respaldo-admin");
        thread.setDaemon(true);
        thread.start();
    }

    private void setWorking(boolean working) {
        btnBackupHere.setDisable(working);
        btnRequest.setDisable(working);
        lblWorking.setText(working ? "Respaldando, espera por favor..." : "");
    }

    private File chooseDirectory(String title, String initial) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        if (initial != null && !initial.isBlank() && new File(initial).isDirectory()) {
            chooser.setInitialDirectory(new File(initial));
        }
        return chooser.showDialog(apAnchor.getScene().getWindow());
    }

    private void showError(String message, Throwable ex) {
        LOGGER.log(Level.WARNING, message, ex);
        alert(AlertType.ERROR, message + ":\n" + (ex == null ? "" : ex.getMessage()));
    }

    private void alert(AlertType type, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message);
            alert.setHeaderText(null);
            // Let long messages wrap instead of being cut with "..."
            alert.getDialogPane().setPrefWidth(560);
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.setTitle("Respaldos programados");
            if (apAnchor.getScene() != null) {
                alert.initOwner(apAnchor.getScene().getWindow());
            }
            alert.showAndWait();
        });
    }
}
