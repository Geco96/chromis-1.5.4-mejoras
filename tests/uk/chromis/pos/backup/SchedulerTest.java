package uk.chromis.pos.backup;
import java.nio.file.*;
import java.sql.*;
public class SchedulerTest {
    static String url = "jdbc:mysql://localhost:3306/bk_src?zeroDateTimeBehavior=convert_To_NULL";
    static int fails = 0, listened = 0;
    static Connection c;
    static void sql(String s) throws Exception { try (Statement st = c.createStatement()) { st.execute(s); } }
    static long count(Path d) throws Exception { try (var s = Files.list(d)) { return s.filter(p -> p.getFileName().toString().endsWith(".zip")).count(); } }
    static void check(String what, Object got, Object exp) {
        boolean ok = exp.equals(got); if (!ok) fails++;
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
    }
    public static void main(String[] a) throws Exception {
        Path dir = Paths.get(a[0]);
        c = DriverManager.getConnection(url, "chromis", "chromis");
        sql("DROP TABLE IF EXISTS backup_settings");
        BackupService caja1 = new BackupService(url, "chromis", "chromis", "CAJA1", m -> { listened++; System.out.println("   aviso al cajero: " + m); });
        BackupService caja2 = new BackupService(url, "chromis", "chromis", "CAJA2", m -> listened++);
        caja1.check();
        check("desactivado: no respalda", count(dir), 0L);
        sql("UPDATE backup_settings SET enabled=1, terminal='caja1', folder='" + dir.toAbsolutePath() + "', interval_hours=24, keep_count=5");
        caja2.check();
        check("otra terminal: no respalda", count(dir), 0L);
        caja1.check();
        check("terminal designada (mayúsculas indistintas): respalda", count(dir), 1L);
        caja1.check();
        check("dentro del intervalo: no repite", count(dir), 1L);
        Thread.sleep(1100);
        sql("UPDATE backup_settings SET run_now=1");
        caja1.check();
        check("respaldar ahora: respalda", count(dir), 2L);
        check("bandera run_now se limpia", BackupService.loadSettings(c).runNow, false);
        check("estado", BackupService.loadSettings(c).lastStatus.startsWith("Correcto"), true);
        sql("UPDATE backup_settings SET last_backup = NOW() - INTERVAL 25 HOUR, folder='/no/existe'");
        caja1.check();
        BackupService.Settings s = BackupService.loadSettings(c);
        check("carpeta inexistente: estado de error", s.lastStatus.startsWith("Error"), true);
        System.out.println("   estado guardado: " + s.lastStatus);
        check("aviso al cajero una vez", listened, 1);
        caja1.check();
        check("tras fallo espera 30 min antes de reintentar (no avisa de nuevo)", listened, 1);
        sql("UPDATE backup_settings SET run_now=1");
        caja1.check();
        check("respaldar ahora reintenta pero no repite el aviso", listened, 1);
        System.exit(fails);
    }
}
