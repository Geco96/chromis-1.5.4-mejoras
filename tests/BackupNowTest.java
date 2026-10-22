import java.lang.reflect.Method;
import java.nio.file.*;
import java.sql.*;
import uk.chromis.pos.backup.BackupService;

/**
 * Respaldo desde Admin con una petición pendiente a la caja: Admin respalda,
 * cancela la petición y la caja ya no hace un segundo respaldo. Con otro
 * respaldo en curso, Admin avisa en lugar de empezar otro.
 *
 *   java -cp dist/chromispos.jar tests/BackupNowTest.java CARPETA [url usuario contraseña]
 */
public class BackupNowTest {
    static int fails = 0;
    static void check(String what, Object got, Object exp) {
        boolean ok = java.util.Objects.equals(got, exp);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    static long zips(Path dir) throws Exception {
        try (var s = Files.list(dir)) { return s.filter(p -> p.toString().endsWith(".zip")).count(); }
    }
    public static void main(String[] a) throws Exception {
        Path dir = Paths.get(a[0]);
        String url = a.length > 1 ? a[1] : "jdbc:mysql://localhost:3306/chromispos?zeroDateTimeBehavior=convert_To_NULL";
        String user = a.length > 2 ? a[2] : "chromis", pass = a.length > 3 ? a[3] : "chromis";
        Connection c = DriverManager.getConnection(url, user, pass);
        BackupService.Settings before = BackupService.loadSettings(c);
        try {
            BackupService.Settings s = BackupService.loadSettings(c);
            s.enabled = true; s.terminal = "CAJA1"; s.folder = dir.toString(); s.intervalHours = 24; s.keepCount = 10;
            BackupService.saveSettings(c, s);
            BackupService.requestBackup(c);
            check("petición pendiente", BackupService.loadSettings(c).runNow, true);

            BackupService admin = new BackupService(url, user, pass, null, null);
            Path f = admin.backupNow(dir, 10);
            check("Admin respaldó", Files.exists(f), true);
            BackupService.Settings after = BackupService.loadSettings(c);
            check("petición cancelada", after.runNow, false);
            check("estado", after.lastStatus.startsWith("Correcto (desde Admin)"), true);

            long n = zips(dir);
            BackupService caja = new BackupService(url, user, pass, "CAJA1", null);
            Method check = BackupService.class.getDeclaredMethod("check");
            check.setAccessible(true);
            check.invoke(caja);
            check("la caja no repite el respaldo", zips(dir), n);

            try (Connection other = DriverManager.getConnection(url, user, pass);
                    Statement st = other.createStatement()) {
                st.executeQuery("SELECT GET_LOCK('chromis_backup', 0)").close();
                String msg = null;
                try {
                    admin.backupNow(dir, 10);
                } catch (Exception ex) {
                    msg = ex.getMessage();
                }
                check("con otro respaldo en curso avisa", msg != null && msg.contains("Otra terminal"), true);
                st.execute("DO RELEASE_LOCK('chromis_backup')");
            }
        } finally {
            BackupService.saveSettings(c, before);
            try (Statement st = c.createStatement()) { st.executeUpdate("UPDATE backup_settings SET run_now = 0 WHERE id = 1"); }
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
