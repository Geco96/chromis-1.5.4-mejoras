import java.sql.*;
import java.util.*;
import java.util.Date;
import uk.chromis.pos.panels.CashCloser;

/**
 * Prueba del corte de caja en una sola transacción contra una base Chromis
 * 1.5.4. Usa cortes de un host de prueba "PRUEBA-CORTE-…" (quedan en la base,
 * cerrados, porque Chromis no permite borrar cortes) y la primera terminal,
 * cuyo corte activo se deja como estaba.
 *
 *   java -cp dist/chromispos.jar tests/CashCloseTest.java [url usuario contraseña]
 */
public class CashCloseTest {
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
    static String cash(Connection c, String host, int seq, boolean open) throws SQLException {
        String id = UUID.randomUUID().toString();
        exec(c, "INSERT INTO closedcash (money, host, hostsequence, datestart, dateend) VALUES ('" + id + "', '" + host + "', "
                + seq + ", NOW() - INTERVAL 1 HOUR, " + (open ? "NULL" : "NOW()") + ")");
        return id;
    }
    public static void main(String[] a) throws Exception {
        Connection c = DriverManager.getConnection(a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos",
                a.length > 1 ? a[1] : "chromis", a.length > 2 ? a[2] : "chromis");
        String host = "PRUEBA-CORTE-" + (System.currentTimeMillis() % 100000);
        String key = one(c, "SELECT terminal_key FROM terminals ORDER BY terminal_name LIMIT 1");
        String activeBefore = one(c, "SELECT active_cash FROM terminals WHERE terminal_key='" + key + "'");
        try {
            check("el error original: Integer 360 == Integer 360", Integer.valueOf(360) == Integer.valueOf(360), false);

            String a1 = cash(c, host, 360, true);
            exec(c, "UPDATE terminals SET active_cash = '" + a1 + "' WHERE terminal_key = '" + key + "'");

            // Corte normal
            CashCloser.Result r1 = CashCloser.close(c, a1, host, key, new Date(), 2);
            check("corte cerrado", one(c, "SELECT dateend IS NOT NULL FROM closedcash WHERE money='" + a1 + "'"), "1");
            check("nosales guardado", one(c, "SELECT nosales FROM closedcash WHERE money='" + a1 + "'"), "2");
            check("nuevo corte 361", r1.sequence, 361);
            check("nuevo corte abierto", one(c, "SELECT dateend IS NULL FROM closedcash WHERE money='" + r1.money + "'"), "1");
            check("la terminal apunta al nuevo", one(c, "SELECT active_cash FROM terminals WHERE terminal_key='" + key + "'"), r1.money);

            // Otro proceso ya tomó el 362: Chromis original fallaba con número repetido
            cash(c, host, 362, false);
            CashCloser.Result r2 = CashCloser.close(c, r1.money, host, key, new Date(), 0);
            check("toma el siguiente libre (363)", r2.sequence, 363);
            check("361 cerrado", one(c, "SELECT dateend IS NOT NULL FROM closedcash WHERE money='" + r1.money + "'"), "1");

            // Falla a la mitad (el corte nuevo no se puede guardar): no cambia nada
            String longHost = host + "-NOMBRE-DEMASIADO-LARGO-PARA-LA-COLUMNA-HOST-XXXXXXXX";
            boolean failed = false;
            try {
                CashCloser.close(c, r2.money, longHost, key, new Date(), 0);
            } catch (SQLException ex) {
                failed = true;
            }
            check("la falla se reporta", failed, true);
            check("el corte sigue abierto (se deshizo el cierre)", one(c, "SELECT dateend IS NULL FROM closedcash WHERE money='" + r2.money + "'"), "1");
            check("la terminal sigue en su corte", one(c, "SELECT active_cash FROM terminals WHERE terminal_key='" + key + "'"), r2.money);
            check("no se creó ningún corte", one(c, "SELECT COUNT(*) FROM closedcash WHERE host='" + longHost.substring(0, 50) + "'"), "0");
            check("autocommit restaurado", c.getAutoCommit(), true);

            // Reintento después de la falla: funciona
            CashCloser.Result r3 = CashCloser.close(c, r2.money, host, key, new Date(), 0);
            check("reintento 364", r3.sequence, 364);

            // Caja apuntando a un corte que no existe o ya cerrado
            check("corte abierto: no se toca", CashCloser.ensureOpenCash(c, r3.money, host, key), null);
            CashCloser.Result e1 = CashCloser.ensureOpenCash(c, "no-existe", host, key);
            check("corte inexistente: abre el 365", e1 == null ? null : e1.sequence, 365);
            CashCloser.Result e2 = CashCloser.ensureOpenCash(c, r1.money, host, key);
            check("corte cerrado: abre el 366", e2 == null ? null : e2.sequence, 366);

            // Corte que quedó abierto por un error (no es el activo de ninguna terminal)
            String left = cash(c, host, 400, true);
            exec(c, "UPDATE terminals SET active_cash = '" + e2.money + "' WHERE terminal_key = '" + key + "'");
            Date end = CashCloser.lastActivity(c, left);
            check("sin ventas: se cierra a su hora de inicio", Math.abs(end.getTime()
                    - Timestamp.valueOf(one(c, "SELECT datestart FROM closedcash WHERE money='" + left + "'")).getTime()) < 1000, true);
            check("cerrar el que quedó abierto", CashCloser.closeLeftOpen(c, left, end), true);
            check("el activo no se puede cerrar así", CashCloser.closeLeftOpen(c, e2.money, new Date()), false);
            check("el activo sigue abierto", one(c, "SELECT dateend IS NULL FROM closedcash WHERE money='" + e2.money + "'"), "1");

            check("secuencias sin repetir", one(c, "SELECT COUNT(*) - COUNT(DISTINCT hostsequence) FROM closedcash WHERE host='" + host + "'"), "0");
        } finally {
            // Los cortes de prueba no se pueden borrar: se dejan cerrados
            exec(c, "UPDATE closedcash SET dateend = NOW() WHERE host LIKE 'PRUEBA-CORTE-%' AND dateend IS NULL");
            exec(c, "UPDATE terminals SET active_cash = " + (activeBefore == null ? "NULL" : "'" + activeBefore + "'")
                    + " WHERE terminal_key = '" + key + "'");
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
