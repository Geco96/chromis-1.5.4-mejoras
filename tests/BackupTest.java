import java.nio.file.*;
import java.sql.*;
import uk.chromis.pos.backup.BackupService;
public class BackupTest {
    public static void main(String[] a) throws Exception {
        String url = "jdbc:mysql://localhost:3306/bk_src?zeroDateTimeBehavior=convert_To_NULL";
        Path out = Paths.get(a[0]);
        // create 3 old fake backups to test retention
        for (String n : new String[]{"20200101-000000","20200102-000000","20200103-000000"})
            Files.write(out.resolve("chromis-respaldo-" + n + ".zip"), new byte[]{1});
        Files.write(out.resolve("otro-archivo.zip"), new byte[]{1});
        BackupService svc = new BackupService(url, "chromis", "chromis", "CAJA1", m -> System.out.println("LISTENER: " + m));
        Path f = svc.backupTo(out, 2);
        System.out.println("archivo: " + f + " bytes=" + Files.size(f) + " avisos=" + svc.getLastWarnings());
    }
}
