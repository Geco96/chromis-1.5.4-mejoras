import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.zip.*;
import uk.chromis.pos.backup.RestoreTool;
import uk.chromis.pos.backup.SqlRestore;

/**
 * Prueba del restaurador (dist/restaurar.jar) con un respaldo del escritorio:
 * lo arma con DatabaseDumper (como los respaldos programados, en .zip), lo
 * carga en otra base solo con restaurar.jar en el classpath (sin mysql.exe ni
 * Chromis) y compara tablas, filas, triggers, vistas y rutinas con la
 * original. También: respaldo cortado, base abierta por otra conexión y
 * contraseña cifrada de chromisposconfig.properties.
 *
 * 1) Armar el respaldo (necesita Chromis):
 *    java -cp dist/admin.jar tests/RestoreToolTest.java armar <respaldo.zip>
 * 2) Probar (solo restaurar.jar):
 *    java -cp dist/restaurar.jar tests/RestoreToolTest.java probar <respaldo.zip> [config]
 */
public class RestoreToolTest {
    static int fails = 0;
    static void check(String what, Object got, Object exp) {
        boolean ok = Objects.equals(got, exp);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    static final String SRV = "jdbc:mysql://localhost:3306/", P = "?useSSL=false&allowPublicKeyRetrieval=true";
    static final String ORIG = "chromispos", COPIA = "chromispos_restaurada";

    public static void main(String[] a) throws Exception {
        if (a[0].equals("armar")) {
            try (Connection c = DriverManager.getConnection(SRV + ORIG + P, "chromis", "chromis");
                    ZipOutputStream z = new ZipOutputStream(new FileOutputStream(a[1]))) {
                z.putNextEntry(new ZipEntry("chromis-20261023-120000.sql"));
                Writer w = new OutputStreamWriter(z, StandardCharsets.UTF_8);
                Class.forName("uk.chromis.pos.backup.DatabaseDumper").getMethod("dump", Writer.class)
                        .invoke(Class.forName("uk.chromis.pos.backup.DatabaseDumper").getConstructor(Connection.class).newInstance(c), w);
                w.flush();
                z.closeEntry();
            }
            System.out.println("Respaldo armado: " + a[1]);
            return;
        }
        File zip = new File(a[1]);
        long[] avance = {0, 0};
        SqlRestore.Progress p = new SqlRestore.Progress() {
            public void progress(long bytes, int st) { avance[0]++; avance[1] = bytes; }
            public boolean cancelled() { return false; }
        };
        try (InputStream in = RestoreTool.open(zip)) {
            check("respaldo del escritorio en .zip: completo", SqlRestore.check(in, p), true);
        }

        // Respaldo cortado a la mitad
        File cortado = File.createTempFile("cortado", ".sql");
        try (InputStream in = RestoreTool.open(zip); OutputStream out = new FileOutputStream(cortado)) {
            byte[] b = in.readAllBytes();
            out.write(b, 0, b.length / 2);
        }
        try (InputStream in = RestoreTool.open(cortado)) {
            check("respaldo cortado: incompleto", SqlRestore.check(in, null), false);
        }
        cortado.delete();

        try (Connection srv = DriverManager.getConnection(SRV + P, "chromis", "chromis"); Statement st = srv.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + COPIA);
            st.execute("CREATE DATABASE " + COPIA + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            // Otra conexión con la base abierta (como Chromis): se ve en la lista
            Connection chromis = DriverManager.getConnection(SRV + COPIA + P, "chromis", "chromis");
            chromis.setAutoCommit(false);
            chromis.createStatement().executeQuery("SELECT 1").close();
            List<String> abiertas = RestoreTool.connections(srv, COPIA);
            check("ve la conexión abierta de la base", abiertas.size(), 1);
            check("con usuario y equipo", abiertas.get(0).contains("chromis@"), true);
            chromis.close();
            check("al cerrarla ya no aparece", RestoreTool.connections(srv, COPIA).size(), 0);
        }

        avance[0] = 0;
        long t0 = System.currentTimeMillis();
        SqlRestore.Result r;
        try (Connection c = DriverManager.getConnection(SRV + COPIA + P, "chromis", "chromis"); InputStream in = RestoreTool.open(zip)) {
            r = SqlRestore.restore(c, in, p);
            c.createStatement().execute("COMMIT");
        }
        System.out.println("     cargado en " + (System.currentTimeMillis() - t0) + " ms, " + r.statements + " instrucciones, " + avance[0] + " avisos de avance");
        check("cargado completo", r.complete, true);
        check("el avance llega al final", avance[1] > 0, true);

        try (Connection c = DriverManager.getConnection(SRV + P, "chromis", "chromis")) {
            check("mismas tablas", RestoreTool.tableCount(c, COPIA), RestoreTool.tableCount(c, ORIG));
            for (String q : new String[]{
                "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema = '%s'",
                "SELECT COUNT(*) FROM information_schema.views WHERE table_schema = '%s'",
                "SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = '%s'",
                "SELECT COUNT(*) FROM %s.products", "SELECT COUNT(*) FROM %s.tickets", "SELECT COUNT(*) FROM %s.ticketlines",
                "SELECT COUNT(*) FROM %s.people", "SELECT COUNT(*) FROM %s.customers", "SELECT version FROM %s.applications",
                "SELECT SUM(units) FROM %s.stockcurrent", "SELECT GROUP_CONCAT(name ORDER BY name) FROM %s.products"}) {
                check(q.replace("%s", "…"), one(c, String.format(q, COPIA)), one(c, String.format(q, ORIG)));
            }
            // La base restaurada funciona: los triggers de Chromis siguen protegiendo los datos
            try {
                c.createStatement().execute("DELETE FROM " + COPIA + ".payments LIMIT 1");
                check("trigger de Chromis activo en la copia", "borró", "rechazado");
            } catch (SQLException ex) {
                check("trigger de Chromis activo en la copia", "rechazado", "rechazado");
            }
            c.createStatement().execute("DROP DATABASE " + COPIA);
        }

        // Contraseña cifrada de chromisposconfig.properties (la clase viene de Chromis dentro del jar)
        if (a.length > 2) {
            Properties cfg = new Properties();
            try (InputStream in = new FileInputStream(a[2])) { cfg.load(in); }
            String pass = cfg.getProperty("database.password", "");
            check("contraseña cifrada de la configuración", pass.startsWith("crypt:")
                    ? RestoreTool.decrypt(cfg.getProperty("database.user"), pass.substring(6)) : pass, "chromis");
        }
        System.out.println(fails == 0 ? "\nTODO BIEN" : "\nFALLAS: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    static String one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { return rs.next() ? rs.getString(1) : null; }
    }
}
