import java.lang.reflect.Method;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.sql.*;
import java.util.*;
import uk.chromis.pos.backup.BackupService;

/**
 * Respaldo local + copia a otra carpeta (Google Drive). Si la carpeta de
 * copia no está disponible el respaldo local se hace igual y la copia queda
 * pendiente; al aparecer la carpeta se copian los pendientes. La caja avisa
 * una sola vez si la copia lleva más de 24 h atrasada.
 *
 *   java -cp dist/chromispos.jar tests/BackupCopyTest.java CARPETA [url usuario contraseña]
 */
public class BackupCopyTest {
    static int fails = 0;
    static void check(String what, Object got, Object exp) {
        boolean ok = Objects.equals(got, exp);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    static List<String> zips(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (var s = Files.list(dir)) {
            List<String> l = new ArrayList<>();
            s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".zip")).sorted().forEach(l::add);
            return l;
        }
    }
    static void exec(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) { st.executeUpdate(sql); }
    }
    public static void main(String[] a) throws Exception {
        Path base = Paths.get(a[0]);
        Path local = base.resolve("local"), drive = base.resolve("drive");
        Files.createDirectories(local);
        String url = a.length > 1 ? a[1] : "jdbc:mysql://localhost:3306/chromispos?zeroDateTimeBehavior=convert_To_NULL";
        String user = a.length > 2 ? a[2] : "chromis", pass = a.length > 3 ? a[3] : "chromis";
        Connection c = DriverManager.getConnection(url, user, pass);
        BackupService.Settings before = BackupService.loadSettings(c);
        List<String> warnings = new ArrayList<>();
        BackupService caja = new BackupService(url, user, pass, "CAJA1", warnings::add);
        Method check = BackupService.class.getDeclaredMethod("check");
        check.setAccessible(true);
        try {
            try (Statement q = c.createStatement(); ResultSet rs = q.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'backup_settings' "
                    + "AND column_name IN ('copy_folder', 'copy_set', 'last_copy', 'copy_status')")) {
                rs.next();
                check("columnas nuevas agregadas a la tabla existente", rs.getInt(1), 4);
            }
            BackupService.Settings s = BackupService.loadSettings(c);
            s.enabled = true; s.terminal = "CAJA1"; s.folder = local.toString(); s.intervalHours = 24; s.keepCount = 2;
            s.copyFolder = drive.toString(); // todavía no existe: como G: sin Google Drive abierto
            BackupService.saveSettings(c, s);
            check("al configurar la copia se marca desde cuándo", BackupService.loadSettings(c).copySet != null, true);

            BackupService.requestBackup(c);
            check.invoke(caja);
            check("respaldo local hecho", zips(local).size(), 1);
            BackupService.Settings st = BackupService.loadSettings(c);
            check("el respaldo es correcto", st.lastStatus.startsWith("Correcto"), true);
            check("copia pendiente", st.copyStatus.startsWith(BackupService.COPY_PENDING), true);
            check("motivo", st.copyStatus.contains("no está disponible"), true);
            check("sin aviso todavía (menos de 24 h)", warnings.size(), 0);

            // Aparece la carpeta (se abrió Google Drive): la siguiente revisión copia
            Files.createDirectories(drive);
            check.invoke(caja);
            check("copiado al aparecer la carpeta", zips(drive), zips(local));
            st = BackupService.loadSettings(c);
            check("copia correcta", st.copyStatus.startsWith("Correcta"), true);
            check("fecha de última copia", st.lastCopy != null, true);

            // Varios respaldos: en la copia también se conservan solo 2
            for (int i = 0; i < 2; i++) {
                Thread.sleep(1100); // nombres con otro segundo
                BackupService.requestBackup(c);
                check.invoke(caja);
            }
            check("local conserva 2", zips(local).size(), 2);
            check("copia conserva 2 y son los mismos", zips(drive), zips(local));

            // Copia atrasada más de 24 h: un solo aviso aunque revise varias veces
            Path moved = base.resolve("drive-fuera");
            Files.move(drive, moved);
            Thread.sleep(1100);
            BackupService.requestBackup(c);
            check.invoke(caja);
            long old = System.currentTimeMillis() - 30L * 3_600_000L;
            for (String z : zips(local)) Files.setLastModifiedTime(local.resolve(z), FileTime.fromMillis(old));
            exec(c, "UPDATE backup_settings SET last_copy = NOW() - INTERVAL 40 HOUR, copy_set = NOW() - INTERVAL 50 HOUR WHERE id = 1");
            check.invoke(caja);
            check.invoke(caja);
            check.invoke(caja);
            check("un solo aviso", warnings.size(), 1);
            check("el aviso explica", warnings.isEmpty() ? "" : warnings.get(0).substring(0, 52), "Los respaldos se están guardando en la PC, pero llev");
            check("respaldo local sigue correcto", BackupService.loadSettings(c).lastStatus.startsWith("Correcto"), true);

            // Vuelve la carpeta: se pone al día y deja de estar pendiente
            Files.move(moved, drive);
            check.invoke(caja);
            check("al día otra vez", zips(drive), zips(local));
            check("estado correcto", BackupService.loadSettings(c).copyStatus.startsWith("Correcta"), true);

            // Una carpeta de copia recién configurada no avisa por respaldos viejos
            warnings.clear();
            s = BackupService.loadSettings(c);
            s.copyFolder = base.resolve("otra-que-no-existe").toString();
            BackupService.saveSettings(c, s);
            check.invoke(caja);
            check("carpeta nueva sin aviso por respaldos viejos", warnings.size(), 0);

            // Respaldo desde Admin en la carpeta local configurada: también copia
            s.copyFolder = drive.toString();
            BackupService.saveSettings(c, s);
            BackupService admin = new BackupService(url, user, pass, null, null);
            Thread.sleep(1100);
            Path f = admin.backupNow(local, 2);
            check("Admin también copia", Files.exists(drive.resolve(f.getFileName())), true);

            // Sin carpeta de copia todo funciona como antes
            s.copyFolder = "";
            BackupService.saveSettings(c, s);
            check("sin copia: estado vacío", BackupService.loadSettings(c).copyStatus, null);
        } finally {
            BackupService.saveSettings(c, before);
            exec(c, "UPDATE backup_settings SET run_now = 0 WHERE id = 1");
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
