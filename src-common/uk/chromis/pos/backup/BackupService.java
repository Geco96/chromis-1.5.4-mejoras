package uk.chromis.pos.backup;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Periodic database backups. Settings are kept in the backup_settings table
 * (edited from Chromis Admin) so every terminal sees the same configuration;
 * only the terminal named there runs the backups, which writes a zipped SQL
 * script to the configured folder (local, network or a Google Drive synced
 * folder) and keeps the newest ones.
 */
public final class BackupService {

    private static final Logger LOGGER = Logger.getLogger(BackupService.class.getName());

    public static final String FILE_PREFIX = "chromis-respaldo-";
    public static final String FILE_SUFFIX = ".zip";
    private static final String STATUS_ERROR = "Error: ";
    private static final long CHECK_SECONDS = 60;
    private static final long RETRY_AFTER_FAILURE_MS = 30L * 60 * 1000;

    private static ScheduledExecutorService scheduler;

    public interface FailureListener {
        void backupFailed(String message);
    }

    public static final class Settings {
        public boolean enabled;
        public String terminal;
        public String folder;
        public int intervalHours = 24;
        public int keepCount = 30;
        public Timestamp lastBackup;
        public Timestamp lastAttempt;
        public String lastStatus;
        public boolean runNow;
    }

    private final String url;
    private final String user;
    private final String password;
    private final String terminal;
    private final FailureListener listener;
    private boolean lastRunFailed = false;
    private List<String> lastWarnings = new ArrayList<>();

    public BackupService(String url, String user, String password, String terminal, FailureListener listener) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.terminal = terminal;
        this.listener = listener;
    }

    /** Starts the periodic check once per application. */
    public static synchronized void start(String url, String user, String password, String terminal, FailureListener listener) {
        if (scheduler != null) {
            return;
        }
        BackupService service = new BackupService(url, user, password, terminal, listener);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chromis-respaldos");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        scheduler.scheduleWithFixedDelay(service::checkSafely, CHECK_SECONDS, CHECK_SECONDS, TimeUnit.SECONDS);
    }

    public static void createTable(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS backup_settings ("
                    + "id INT NOT NULL PRIMARY KEY, "
                    + "enabled TINYINT(1) NOT NULL DEFAULT 0, "
                    + "terminal VARCHAR(255) NULL, "
                    + "folder VARCHAR(1024) NULL, "
                    + "interval_hours INT NOT NULL DEFAULT 24, "
                    + "keep_count INT NOT NULL DEFAULT 30, "
                    + "last_backup DATETIME NULL, "
                    + "last_attempt DATETIME NULL, "
                    + "last_status VARCHAR(2000) NULL, "
                    + "run_now TINYINT(1) NOT NULL DEFAULT 0)");
            st.executeUpdate("INSERT IGNORE INTO backup_settings (id) VALUES (1)");
        }
    }

    public static Settings loadSettings(Connection con) throws SQLException {
        createTable(con);
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT enabled, terminal, folder, interval_hours, keep_count, "
                        + "last_backup, last_attempt, last_status, run_now FROM backup_settings WHERE id = 1")) {
            Settings s = new Settings();
            if (rs.next()) {
                s.enabled = rs.getBoolean(1);
                s.terminal = rs.getString(2);
                s.folder = rs.getString(3);
                s.intervalHours = rs.getInt(4);
                s.keepCount = rs.getInt(5);
                s.lastBackup = rs.getTimestamp(6);
                s.lastAttempt = rs.getTimestamp(7);
                s.lastStatus = rs.getString(8);
                s.runNow = rs.getBoolean(9);
            }
            return s;
        }
    }

    /** Saves the editable settings (used by Chromis Admin). */
    public static void saveSettings(Connection con, Settings s) throws SQLException {
        createTable(con);
        try (PreparedStatement ps = con.prepareStatement("UPDATE backup_settings SET enabled = ?, terminal = ?, folder = ?, "
                + "interval_hours = ?, keep_count = ? WHERE id = 1")) {
            ps.setBoolean(1, s.enabled);
            ps.setString(2, s.terminal);
            ps.setString(3, s.folder);
            ps.setInt(4, Math.max(1, s.intervalHours));
            ps.setInt(5, Math.max(1, s.keepCount));
            ps.executeUpdate();
        }
    }

    /** Asks the designated terminal to back up at its next check (within a minute). */
    public static void requestBackup(Connection con) throws SQLException {
        createTable(con);
        try (Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE backup_settings SET run_now = 1 WHERE id = 1");
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void checkSafely() {
        try {
            check();
        } catch (Throwable t) {
            // Never let an exception stop the scheduler
            LOGGER.log(Level.WARNING, "Error al revisar los respaldos programados", t);
        }
    }

    void check() throws SQLException {
        try (Connection con = connect()) {
            Settings s = loadSettings(con);
            if (!s.enabled || s.terminal == null || !s.terminal.equalsIgnoreCase(terminal)) {
                return;
            }
            long now = System.currentTimeMillis();
            boolean due = s.runNow
                    || s.lastBackup == null
                    || now - s.lastBackup.getTime() >= Math.max(1, s.intervalHours) * 3_600_000L;
            boolean recentFailure = s.lastAttempt != null
                    && s.lastStatus != null && s.lastStatus.startsWith(STATUS_ERROR)
                    && now - s.lastAttempt.getTime() < RETRY_AFTER_FAILURE_MS;
            if (!due || (recentFailure && !s.runNow)) {
                return;
            }
            runBackup(con, s);
        }
    }

    /**
     * Backup made by hand from Admin. It takes the same lock as the terminals
     * (so two backups never run at once), cancels a pending "back up now"
     * request, and when it goes to the configured folder it counts as the
     * last backup, so the terminal does not make another one right after.
     */
    public Path backupNow(Path folder, int keepCount) throws SQLException, IOException {
        try (Connection con = connect()) {
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT GET_LOCK('chromis_backup', 0)")) {
                if (!rs.next() || rs.getInt(1) != 1) {
                    throw new IOException("Otra terminal está haciendo un respaldo en este momento; espera a que termine.");
                }
            }
            try {
                Settings s = loadSettings(con);
                Path file = backupTo(folder, keepCount);
                boolean configuredFolder = s.folder != null && !s.folder.trim().isEmpty()
                        && Paths.get(s.folder.trim()).toAbsolutePath().normalize().equals(folder.toAbsolutePath().normalize());
                Timestamp now = new Timestamp(System.currentTimeMillis());
                if (configuredFolder) {
                    updateStatus(con, now, true, "Correcto (desde Admin): " + file.getFileName());
                } else {
                    try (Statement st = con.createStatement()) {
                        st.executeUpdate("UPDATE backup_settings SET run_now = 0 WHERE id = 1");
                    }
                }
                return file;
            } finally {
                try (Statement st = con.createStatement()) {
                    st.execute("DO RELEASE_LOCK('chromis_backup')");
                }
            }
        }
    }

    private void runBackup(Connection con, Settings s) throws SQLException {
        // Avoid two terminals backing up at the same time
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT GET_LOCK('chromis_backup', 0)")) {
            if (!rs.next() || rs.getInt(1) != 1) {
                return;
            }
        }
        Timestamp attempt = new Timestamp(System.currentTimeMillis());
        try {
            Path file = backupTo(Paths.get(s.folder == null ? "" : s.folder.trim()), s.keepCount);
            String status = "Correcto: " + file.getFileName();
            if (!lastWarnings.isEmpty()) {
                status += " (no se incluyeron por permisos: " + String.join(", ", lastWarnings) + ")";
            }
            updateStatus(con, attempt, true, status);
            lastRunFailed = false;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Falló el respaldo", ex);
            String message = STATUS_ERROR + (ex.getMessage() == null ? ex.toString() : ex.getMessage());
            updateStatus(con, attempt, false, message);
            if (!lastRunFailed && listener != null) {
                listener.backupFailed(message);
            }
            lastRunFailed = true;
        } finally {
            try (Statement st = con.createStatement()) {
                st.execute("DO RELEASE_LOCK('chromis_backup')");
            }
        }
    }

    private static void updateStatus(Connection con, Timestamp attempt, boolean ok, String status) throws SQLException {
        String sql = ok
                ? "UPDATE backup_settings SET last_backup = ?, last_attempt = ?, last_status = ?, run_now = 0 WHERE id = 1"
                : "UPDATE backup_settings SET last_attempt = ?, last_status = ?, run_now = 0 WHERE id = 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            if (ok) {
                ps.setTimestamp(i++, attempt);
            }
            ps.setTimestamp(i++, attempt);
            ps.setString(i, status.length() > 2000 ? status.substring(0, 2000) : status);
            ps.executeUpdate();
        }
    }

    public List<String> getLastWarnings() {
        return lastWarnings;
    }

    /** Writes a backup to the folder and removes the oldest ones beyond keepCount. */
    public Path backupTo(Path folder, int keepCount) throws SQLException, IOException {
        if (folder.toString().isEmpty()) {
            throw new IOException("No se configuró la carpeta de respaldos");
        }
        if (!Files.isDirectory(folder)) {
            throw new IOException("La carpeta de respaldos no existe o no está disponible: " + folder);
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        Path target = folder.resolve(FILE_PREFIX + stamp + FILE_SUFFIX);
        // Write to a temporary name first so a synced folder never uploads half a file
        Path temp = folder.resolve(FILE_PREFIX + stamp + ".tmp");
        try (Connection con = connect();
                ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temp))) {
            zip.putNextEntry(new ZipEntry("chromis-" + stamp + ".sql"));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
            DatabaseDumper dumper = new DatabaseDumper(con);
            dumper.dump(writer);
            lastWarnings = new ArrayList<>(dumper.getWarnings());
            writer.flush();
            zip.closeEntry();
        } catch (SQLException | IOException | RuntimeException ex) {
            Files.deleteIfExists(temp);
            throw ex;
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        removeOldBackups(folder, keepCount);
        return target;
    }

    static void removeOldBackups(Path folder, int keepCount) {
        if (keepCount <= 0) {
            return;
        }
        File[] files = folder.toFile().listFiles((dir, name) -> name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX));
        if (files == null || files.length <= keepCount) {
            return;
        }
        // Names carry the timestamp, so alphabetical order is chronological
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        for (int i = 0; i < files.length - keepCount; i++) {
            if (!files[i].delete()) {
                LOGGER.warning("No se pudo borrar el respaldo antiguo " + files[i]);
            }
        }
    }
}
