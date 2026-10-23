import java.sql.*;
import java.util.Arrays;
import java.util.List;
import uk.chromis.pos.restock.RestockStore;
import uk.chromis.pos.restock.RestockStore.Status;

/**
 * Prueba manual de RestockStore contra una base Chromis 1.5.4 (la que crea
 * dbcreator.jar) que tenga un producto con referencia ARROZ y la ubicación
 * MainStore. Deja el stock y los umbrales de ARROZ como estaban.
 *
 *   java -cp dist/chromispos.jar:tests tests/RestockTest.java [url usuario contraseña]
 */
public class RestockTest {
    static int fails = 0;
    static void check(String what, Object got, Object exp) {
        boolean ok = (exp == null) ? got == null : exp.equals(got);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    static String one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
    static void exec(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
    static RestockStore.Row row(Connection c, String id) throws SQLException {
        List<RestockStore.Row> rows = RestockStore.load(c, "MainStore", Arrays.asList(id));
        return rows.isEmpty() ? null : rows.get(0);
    }
    public static void main(String[] a) throws Exception {
        String url = a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos";
        Connection c = DriverManager.getConnection(url, a.length > 1 ? a[1] : "chromis", a.length > 2 ? a[2] : "chromis");
        String guid = one(c, "SELECT guid FROM siteguid");
        String id = one(c, "SELECT id FROM products WHERE reference = 'ARROZ'");
        String units = one(c, "SELECT units FROM stockcurrent WHERE product = '" + id + "' AND location = 'MainStore'");
        RestockStore.createTables(c);
        RestockStore.createTables(c); // dos veces no falla
        try {
            exec(c, "DELETE FROM stocklevel WHERE product = '" + id + "'");
            exec(c, "DELETE FROM restock_reserve WHERE product = '" + id + "'");

            check("clasificar 0", RestockStore.classify(0, 5, 10), Status.AGOTADO);
            check("clasificar negativo", RestockStore.classify(-2, 5, 10), Status.AGOTADO);
            check("clasificar 5 (= mínimo)", RestockStore.classify(5, 5, 10), Status.BAJO);
            check("clasificar 10 (= reserva)", RestockStore.classify(10, 5, 10), Status.RESERVA);
            check("clasificar 11", RestockStore.classify(11, 5, 10), Status.ACEPTABLE);
            check("clasificar sin reserva", RestockStore.classify(8, 5, 0), Status.ACEPTABLE);
            check("sugerido 50 - 8", RestockStore.suggested(8, 50), 42.0);
            check("sugerido 10 - 3.5", RestockStore.suggested(3.5, 10), 6.0);
            check("sugerido sobre el máximo", RestockStore.suggested(60, 50), 0.0);
            check("sugerido sin máximo", RestockStore.suggested(3, 0), 0.0);
            check("validar correcto", RestockStore.validate(5, 10, 50), null);
            check("validar reserva < mínimo", RestockStore.validate(5, 3, 50) != null, true);
            check("validar máximo < reserva", RestockStore.validate(5, 10, 8) != null, true);
            check("validar solo mínimo", RestockStore.validate(5, 0, 0), null);

            check("sin umbrales", row(c, id).status(), Status.SIN_UMBRAL);
            RestockStore.saveThresholds(c, "MainStore", guid, id, 5, 10, 50);
            check("stocklevel creado", one(c, "SELECT CONCAT(stocksecurity, '/', stockmaximum, '/', siteguid) FROM stocklevel WHERE product = '" + id + "'"),
                    "5.000/50.000/" + guid);
            check("reserva guardada", one(c, "SELECT reserve FROM restock_reserve WHERE product = '" + id + "'"), "10.000");
            RestockStore.saveThresholds(c, "MainStore", guid, id, 4, 0, 40);
            check("se actualiza, no se duplica", one(c, "SELECT COUNT(*) FROM stocklevel WHERE product = '" + id + "'"), "1");
            check("reserva 0 la quita", one(c, "SELECT COUNT(*) FROM restock_reserve WHERE product = '" + id + "'"), "0");
            RestockStore.saveThresholds(c, "MainStore", guid, id, 5, 10, 50);

            exec(c, "UPDATE stockcurrent SET units = 8 WHERE product = '" + id + "' AND location = 'MainStore'");
            RestockStore.Row r = row(c, id);
            check("stock 8", r.status(), Status.RESERVA);
            check("pedir con stock 8", r.suggested(), 42.0);
            exec(c, "UPDATE stockcurrent SET units = 3 WHERE product = '" + id + "' AND location = 'MainStore'");
            check("stock 3", row(c, id).status(), Status.BAJO);
            exec(c, "UPDATE stockcurrent SET units = 0 WHERE product = '" + id + "' AND location = 'MainStore'");
            check("stock 0", row(c, id).status(), Status.AGOTADO);
            exec(c, "UPDATE stockcurrent SET units = 20 WHERE product = '" + id + "' AND location = 'MainStore'");
            check("stock 20", row(c, id).status(), Status.ACEPTABLE);
            check("otra ubicación no cuenta", RestockStore.load(c, "OtraUbicacion", Arrays.asList(id)).get(0).hasThresholds(), false);
            check("lista vacía", RestockStore.load(c, "MainStore", Arrays.asList()).size(), 0);

            int all = RestockStore.load(c, "MainStore", null).size();
            int managed = Integer.parseInt(one(c, "SELECT COUNT(*) FROM products WHERE managestock = 1 AND COALESCE(isservice,0) = 0 AND COALESCE(systemobject,0) = 0"));
            check("todos los productos con control de stock", all, managed);

            RestockStore.Settings s = RestockStore.loadSettings(c);
            check("avisos activos por omisión", s.posAlerts, true);
            s.alertReserve = false;
            RestockStore.saveSettings(c, s);
            check("guardar avisos", RestockStore.loadSettings(c).alertReserve, false);
            s.alertReserve = true;
            RestockStore.saveSettings(c, s);
        } finally {
            exec(c, "DELETE FROM stocklevel WHERE product = '" + id + "'");
            exec(c, "DELETE FROM restock_reserve WHERE product = '" + id + "'");
            if (units != null) {
                exec(c, "UPDATE stockcurrent SET units = " + units + " WHERE product = '" + id + "' AND location = 'MainStore'");
            }
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
