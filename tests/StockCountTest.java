import java.io.InputStream;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import net.sf.jasperreports.engine.*;
import uk.chromis.administration.stockcount.StockCountStore;
import uk.chromis.pos.settings.PosSettings;

/**
 * Prueba manual de los conteos programados contra una base Chromis 1.5.4 con
 * ARROZ y BOLSA en MainStore. Genera los dos informes en PDF en la carpeta
 * indicada y deja el stock como estaba (el movimiento de ajuste queda en el
 * diario de stock, que Chromis no permite borrar).
 *
 *   java -cp dist/admin.jar tests/StockCountTest.java CARPETA_PDF [url usuario contraseña]
 */
public class StockCountTest {
    static int fails = 0;
    static void check(String what, Object got, Object exp) {
        boolean ok = Objects.equals(got, exp);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    static String one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
    static void exec(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) { st.executeUpdate(sql); }
    }
    static void pdf(Connection c, String res, Map<String, Object> params, String out) throws Exception {
        try (InputStream in = StockCountStore.class.getResourceAsStream(res)) {
            JasperReport jr = JasperCompileManager.compileReport(in);
            JasperPrint jp = JasperFillManager.fillReport(jr, params, c);
            JasperExportManager.exportReportToPdfFile(jp, out);
            System.out.println("PDF " + out + " (" + jp.getPages().size() + " pág.)");
        }
    }
    public static void main(String[] a) throws Exception {
        String dir = a[0];
        Connection c = DriverManager.getConnection(a.length > 1 ? a[1] : "jdbc:mysql://localhost:3306/chromispos",
                a.length > 2 ? a[2] : "chromis", a.length > 3 ? a[3] : "chromis");
        String guid = one(c, "SELECT guid FROM siteguid");
        String arroz = one(c, "SELECT id FROM products WHERE reference='ARROZ'");
        String bolsa = one(c, "SELECT id FROM products WHERE reference='BOLSA'");
        String uArroz = one(c, "SELECT units FROM stockcurrent WHERE product='" + arroz + "' AND location='MainStore'");
        String uBolsa = one(c, "SELECT units FROM stockcurrent WHERE product='" + bolsa + "' AND location='MainStore'");
        String diary = one(c, "SELECT COALESCE(MAX(sequenceid),0) FROM stockdiary");
        StockCountStore.createTables(c);
        StockCountStore.createTables(c);
        int plan = -1;
        try {
            exec(c, "UPDATE stockcurrent SET units = 10 WHERE product='" + arroz + "' AND location='MainStore'");
            exec(c, "UPDATE stockcurrent SET units = 3 WHERE product='" + bolsa + "' AND location='MainStore'");
            plan = StockCountStore.createPlan(c, "Prueba", LocalDate.of(2026, 10, 20), "MainStore", "test", false);
            check("agrega 2", StockCountStore.addProducts(c, plan, Arrays.asList(arroz, bolsa, arroz)), 2);
            check("buscar por código", StockCountStore.findProduct(c, "7501234567893"), arroz);
            check("buscar por barcodes", StockCountStore.findProduct(c, "7500000000031"), bolsa);
            check("no existe", StockCountStore.findProduct(c, "999"), null);

            StockCountStore.setCount(c, plan, arroz, 8.0, "MainStore");      // sistema 10 al contar
            exec(c, "UPDATE stockcurrent SET units = 7 WHERE product='" + arroz + "' AND location='MainStore'"); // venta de 3 después
            List<StockCountStore.Line> lines = StockCountStore.lines(c, plan, "MainStore");
            StockCountStore.Line la = lines.stream().filter(l -> l.productId.equals(arroz)).findFirst().get();
            check("sistema al contar", la.system(), 10.0);
            check("diferencia", la.difference(), -2.0);
            check("bolsa sin contar", lines.stream().filter(l -> l.productId.equals(bolsa)).findFirst().get().counted, null);

            Map<String, Object> p = new HashMap<>();
            p.put("PLAN_ID", plan); p.put("LOCATION", "MainStore"); p.put("COMPANY", "Mi Tienda");
            p.put("PLAN_NAME", "Prueba"); p.put("PLAN_DATE", "20/10/2026");
            pdf(c, "hoja_conteo.jrxml", p, dir + "/hoja_conteo.pdf");
            pdf(c, "resultado_conteo.jrxml", p, dir + "/resultado_conteo.pdf");

            check("ajustados", StockCountStore.closePlan(c, plan, "MainStore", guid, "test"), 1);
            check("stock final = 7 - 2", one(c, "SELECT units FROM stockcurrent WHERE product='" + arroz + "' AND location='MainStore'"), "5.000");
            check("bolsa no se toca", one(c, "SELECT units FROM stockcurrent WHERE product='" + bolsa + "' AND location='MainStore'"), "3.000");
            check("diario", one(c, "SELECT CONCAT(reason,'/',units,'/',reference) FROM stockdiary WHERE sequenceid > " + diary), "-7/-2.000/Conteo #" + plan);
            check("cerrado", one(c, "SELECT status FROM stockcount_plans WHERE id=" + plan), "CERRADO");
            try { StockCountStore.closePlan(c, plan, "MainStore", guid, "test"); check("no cierra dos veces", "cerró", "error"); }
            catch (SQLException ex) { check("no cierra dos veces", "error", "error"); }

            // Rotación: los nunca contados primero; arroz ya se contó
            PosSettings.put(c, StockCountStore.ROTATION_ENABLED, "1");
            PosSettings.put(c, StockCountStore.ROTATION_SIZE, "2");
            PosSettings.put(c, StockCountStore.ROTATION_DAYS, "7");
            PosSettings.put(c, StockCountStore.ROTATION_NEXT, "2026-10-18");
            check("no toca antes", StockCountStore.runRotationIfDue(c, "MainStore", "test", LocalDate.of(2026, 10, 17)), null);
            Integer rot = StockCountStore.runRotationIfDue(c, "MainStore", "test", LocalDate.of(2026, 10, 18));
            check("crea rotativo", rot != null, true);
            List<StockCountStore.Line> rl = StockCountStore.lines(c, rot, "MainStore");
            check("rotativo de 2", rl.size(), 2);
            check("nombre", one(c, "SELECT name FROM stockcount_plans WHERE id=" + rot), "Conteo rotativo 18/10/2026");
            check("arroz (ya contado) no entra", rl.stream().anyMatch(l -> l.productId.equals(arroz)), false);
            check("siguiente fecha", StockCountStore.loadRotation(c).next, LocalDate.of(2026, 10, 25));
            check("no repite el mismo día", StockCountStore.runRotationIfDue(c, "MainStore", "test", LocalDate.of(2026, 10, 18)), null);
            StockCountStore.cancelPlan(c, rot, "test");
            check("cancelado", one(c, "SELECT status FROM stockcount_plans WHERE id=" + rot), "CANCELADO");
        } finally {
            // Los movimientos del diario se quedan: Chromis no permite borrarlos (trigger delete_stock_diary)
            exec(c, "UPDATE stockcurrent SET units = " + uArroz + " WHERE product='" + arroz + "' AND location='MainStore'");
            exec(c, "UPDATE stockcurrent SET units = " + uBolsa + " WHERE product='" + bolsa + "' AND location='MainStore'");
            exec(c, "DELETE FROM stockcount_lines"); exec(c, "DELETE FROM stockcount_plans");
            exec(c, "DELETE FROM pos_settings WHERE name LIKE 'stockcount.%'");
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
