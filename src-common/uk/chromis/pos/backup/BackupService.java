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
 * script to the configured local folder and keeps the newest ones. When a
 * copy folder is set (e.g. Google Drive for desktop) every backup is copied
 * there too; if it is not available the copy waits and is retried at every
 * check, so a backup is never lost because Drive was not running.
 */
public final class BackupService {

    private static final Logger LOGGER = Logger.getLogger(BackupService.class.getName());

    public static final String FILE_PREFIX = "chromis-respaldo-";
    public static final String FILE_SUFFIX = ".zip";
    private static final String STATUS_ERROR = "Error: ";
    private static final long CHECK_SECONDS = 60;
    private static final long RETRY_AFTER_FAILURE_MS = 30L * 60 * 1000;
    /** The terminal warns once when a backup has been waiting this long to be copied. */
    static final long COPY_LATE_MS = 24L * 3_600_000L;
    public static final String COPY_PENDING = "Pendiente: ";

    private static ScheduledExecutorService scheduler;

    /** Receives the full text of the warning to show at the till. */
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
        /** Second folder each backup is copied to (e.g. Google Drive); empty for none. */
        public String copyFolder;
        /** When the copy folder was set, so older backups never count as late. */
        public Timestamp copySet;
        /** Last time the copy folder had every local backup. */
        public Timestamp lastCopy;
        public String copyStatus;

        public boolean hasCopyFolder() {
            return copyFolder != null && !copyFolder.trim().isEmpty();
        }
    }

    private final String url;
    private final String user;
    private final String password;
    private final String terminal;
    private final FailureListener listener;
    private boolean lastRunFailed = false;
    private boolean copyLateWarned = false;
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
        // Columns added later: existing tables get them without losing anything
        // (checked first because MySQL has no ADD COLUMN IF NOT EXISTS)
        addColumn(con, "copy_folder", "VARCHAR(1024) NULL");
        addColumn(con, "copy_set", "DATETIME NULL");
        addColumn(con, "last_copy", "DATETIME NULL");
        addColumn(con, "copy_status", "VARCHAR(2000) NULL");
    }

    private static void addColumn(Connection con, String column, String definition) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'backup_settings' AND column_name = ?")) {
            ps.setString(1, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate("ALTER TABLE backup_settings ADD COLUMN " + column + " " + definition);
        }
    }

    public static Settings loadSettings(Connection con) throws SQLException {
        createTable(con);
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT enabled, terminal, folder, interval_hours, keep_count, "
                        + "last_backup, last_attempt, last_status, run_now, copy_folder, copy_set, last_copy, copy_status "
                        + "FROM backup_settings WHERE id = 1")) {
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
                s.copyFolder = rs.getString(10);
                s.copySet = rs.getTimestamp(11);
                s.lastCopy = rs.getTimestamp(12);
                s.copyStatus = rs.getString(13);
            }
            return s;
        }
    }

    /** Saves the editable settings (used by Chromis Admin). */
    public static void saveSettings(Connection con, Settings s) throws SQLException {
        Settings old = loadSettings(con);
        String copy = s.hasCopyFolder() ? s.copyFolder.trim() : null;
        boolean copyChanged = !java.util.Objects.equals(copy, old.hasCopyFolder() ? old.copyFolder.trim() : null);
        try (PreparedStatement ps = con.prepareStatement("UPDATE backup_settings SET enabled = ?, terminal = ?, folder = ?, "
                + "interval_hours = ?, keep_count = ?, copy_folder = ? WHERE id = 1")) {
            ps.setBoolean(1, s.enabled);
            ps.setString(2, s.terminal);
            ps.setString(3, s.folder);
            ps.setInt(4, Math.max(1, s.intervalHours));
            ps.setInt(5, Math.max(1, s.keepCount));
            ps.setString(6, copy);
            ps.executeUpdate();
        }
        if (copyChanged) {
            // A new copy folder starts from scratch: no "late" warning for older backups
            try (Statement st = con.createStatement()) {
                st.executeUpdate(copy == null
                        ? "UPDATE backup_settings SET copy_set = NULL, last_copy = NULL, copy_status = NULL WHERE id = 1"
                        : "UPDATE backup_settings SET copy_set = NOW(), last_copy = NULL, copy_status = NULL WHERE id = 1");
            }
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
            if (due && !(recentFailure && !s.runNow)) {
                runBackup(con, s);
                s = loadSettings(con);
            }
            syncCopies(con, s, true);
        }
    }

    /**
     * Copies to the copy folder the local backups it does not have yet, and
     * removes there the oldest beyond keepCount. A copy folder that is not
     * available (Drive not running) only leaves the copy pending; when
     * warn is set the listener hears once that a backup has waited more than
     * 24 hours.
     */
    void syncCopies(Connection con, Settings s, boolean warn) throws SQLException {
        if (!s.hasCopyFolder() || s.folder == null || s.folder.trim().isEmpty()) {
            return;
        }
        Path local = Paths.get(s.folder.trim());
        Path copy = Paths.get(s.copyFolder.trim());
        File[] backups = local.toFile().listFiles((dir, name) -> name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX));
        if (backups == null) {
            return; // the local folder itself is missing: the backup reports that
        }
        Arrays.sort(backups, (a, b) -> a.getName().compareTo(b.getName()));
        String problem = null;
        int copied = 0;
        if (!Files.isDirectory(copy)) {
            problem = "la carpeta no está disponible (¿Google Drive está abierto?)";
        } else {
            // Only the newest keepCount: older ones would be removed there right away
            int from = Math.max(0, backups.length - Math.max(1, s.keepCount));
            for (int i = from; i < backups.length && problem == null; i++) {
                Path target = copy.resolve(backups[i].getName());
                if (Files.exists(target)) {
                    continue;
                }
                Path temp = copy.resolve(backups[i].getName().replace(FILE_SUFFIX, ".tmp"));
                try {
                    Files.copy(backups[i].toPath(), temp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "No se pudo copiar el respaldo", ex);
                    problem = "no se pudo copiar " + backups[i].getName() + ": " + ex.getMessage();
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException ignored) {
                        // the next check tries again
                    }
                }
            }
            if (problem == null) {
                removeOldBackups(copy, s.keepCount);
            }
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (problem == null) {
            copyLateWarned = false;
            String status = "Correcta: " + (backups.length == 0 ? "no hay respaldos que copiar"
                    : backups[backups.length - 1].getName() + (copied > 0 ? " (copiados " + copied + ")" : ""));
            if (copied > 0 || s.lastCopy == null || !status.equals(s.copyStatus)) {
                updateCopyStatus(con, now, status);
            }
            return;
        }
        String status = COPY_PENDING + problem;
        if (!status.equals(s.copyStatus)) {
            updateCopyStatus(con, null, status);
        }
        long oldestWaiting = oldestNotCopied(backups, s);
        if (warn && !copyLateWarned && oldestWaiting > 0 && now.getTime() - oldestWaiting > COPY_LATE_MS && listener != null) {
            copyLateWarned = true;
            listener.backupFailed("Los respaldos se están guardando en la PC, pero llevan más de 24 horas sin copiarse a "
                    + copy + ".\n\nMotivo: " + problem);
        }
    }

    /** Time of the oldest local backup made after the last complete copy (and after the copy folder was set), or 0. */
    private static long oldestNotCopied(File[] backups, Settings s) {
        long since = Math.max(s.lastCopy == null ? 0 : s.lastCopy.getTime(), s.copySet == null ? 0 : s.copySet.getTime());
        for (File f : backups) {
            if (f.lastModified() > since) {
                return f.lastModified();
            }
        }
        return 0;
    }

    private static void updateCopyStatus(Connection con, Timestamp lastCopy, String status) throws SQLException {
        String sql = lastCopy == null
                ? "UPDATE backup_settings SET copy_status = ? WHERE id = 1"
                : "UPDATE backup_settings SET copy_status = ?, last_copy = ? WHERE id = 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, status.length() > 2000 ? status.substring(0, 2000) : status);
            if (lastCopy != null) {
                ps.setTimestamp(2, lastCopy);
            }
            ps.executeUpdate();
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
                    syncCopies(con, loadSettings(con), false);
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
                listener.backupFailed("No se pudo hacer el respaldo programado de la base de datos.\n\n" + message);
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
