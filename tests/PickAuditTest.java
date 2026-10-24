import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import uk.chromis.pos.audit.PickAuditStore;
import uk.chromis.pos.audit.PickAuditStore.Audit;
import uk.chromis.pos.audit.PickAuditStore.Line;
import uk.chromis.pos.audit.PickAuditStore.Order;
import uk.chromis.pos.delivery.DeliveryStatusStore;

/**
 * Auditor de salida (uk.chromis.pos.audit.PickAuditStore) contra la base de
 * prueba: buscar la venta (no la devolución con el mismo número), escanear con
 * código, referencia y código de caja, granel con decimales, excedentes,
 * faltantes justificados, dos PCs a la vez y el paso a "Preparada" de la
 * entrega. Las ventas de prueba se quedan (Chromis no deja borrarlas).
 *
 *   java -cp dist/admin.jar tests/PickAuditTest.java [url usuario contraseña]
 */
public class PickAuditTest {
    static int fails = 0;
    static String url, dbUser, dbPass;

    static void check(String what, Object got, Object exp) {
        boolean ok = got == null ? exp == null : got.equals(exp);
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
    static Connection connect() throws SQLException { return DriverManager.getConnection(url, dbUser, dbPass); }

    static String guid, money, admin, run;
    static List<String> products = new ArrayList<>(), tickets = new ArrayList<>();
    static int nextTicket;

    static String product(Connection c, String name, String code, String ref) throws SQLException {
        String id = UUID.randomUUID().toString();
        exec(c, "INSERT INTO products (id, reference, code, name, category, taxcat, pricebuy, pricesell, siteguid) VALUES ('" + id + "', '"
                + ref + "', '" + code + "', '" + name + "', (SELECT id FROM categories WHERE name = 'General Products'), "
                + "(SELECT id FROM taxcategories WHERE name = 'Tax Standard'), 1, 2, '" + guid + "')");
        products.add(id);
        return id;
    }
    /** Sale (type 0) or refund (type 1) with [lines] = product, units, product, units... */
    static String ticket(Connection c, int type, int number, boolean delivery, Object... lines) throws SQLException {
        String id = UUID.randomUUID().toString();
        exec(c, "INSERT INTO receipts (id, money, datenew, person) VALUES ('" + id + "', '" + money + "', NOW(), '" + admin + "')");
        exec(c, "INSERT INTO tickets (id, tickettype, ticketid, person) VALUES ('" + id + "', " + type + ", " + number + ", '" + admin + "')");
        for (int i = 0; i < lines.length; i += 2) {
            exec(c, "INSERT INTO ticketlines (ticket, line, product, units, priceinc, priceexc, taxid) VALUES ('" + id + "', " + (i / 2)
                    + ", '" + lines[i] + "', " + lines[i + 1] + ", 2, 2, (SELECT id FROM taxes LIMIT 1))");
        }
        if (delivery) {
            exec(c, "INSERT INTO customer_delivery (id, name, addressline1, deliverydate, delivered) VALUES ('" + id
                    + "', 'Recibe Auditor', 'Calle Auditor 1', NOW(), 0)");
        }
        tickets.add(id);
        return id;
    }
    static String scan(Connection c, Audit a, String code, Double units) throws SQLException {
        try {
            Line l = PickAuditStore.scan(c, a.id, code, units);
            return "OK " + PickAuditStore.qty(l.scanned) + "/" + PickAuditStore.qty(l.expected);
        } catch (PickAuditStore.Refused r) {
            return "NO: " + r.getMessage();
        }
    }
    static String close(Connection c, Audit a) throws SQLException {
        try {
            PickAuditStore.Closed r = PickAuditStore.close(c, a.id, admin, "Administrator");
            return "CERRADA " + r.justified.size() + " faltantes / " + r.delivery;
        } catch (PickAuditStore.Refused r) {
            return "NO: " + r.getMessage();
        }
    }

    public static void main(String[] args) throws Exception {
        url = args.length > 0 ? args[0] : "jdbc:mysql://localhost:3306/chromispos";
        dbUser = args.length > 1 ? args[1] : "chromis";
        dbPass = args.length > 2 ? args[2] : "chromis";
        Connection c = connect();
        guid = one(c, "SELECT guid FROM siteguid");
        money = one(c, "SELECT money FROM closedcash ORDER BY datestart DESC LIMIT 1");
        admin = one(c, "SELECT id FROM people WHERE name = 'Administrator'");
        run = UUID.randomUUID().toString().substring(0, 6);
        nextTicket = Integer.parseInt(one(c, "SELECT GREATEST(COALESCE(MAX(ticketid), 0), 996000) FROM tickets")) + 1;
        PickAuditStore.createTables(c);
        PickAuditStore.createTables(c); // dos veces no falla
        PickAuditStore.ensurePermission(c);
        PickAuditStore.ensurePermission(c);
        check("permiso en Roles", one(c, "SELECT CONCAT(COUNT(*), ' ', MAX(displayname)) FROM dbpermissions WHERE classname = 'PickAudit'"), "1 Auditor de salida");
        try {
            String lapiz = product(c, "Lápiz auditor " + run, "AUD1" + run, "REF-AUD1" + run);
            String hilo = product(c, "Hilo granel auditor " + run, "AUD2" + run, "REF-AUD2" + run);
            String goma = product(c, "Goma auditor " + run, "AUD3" + run, "REF-AUD3" + run);
            exec(c, "INSERT INTO barcodes (id, productid, barcode, sellingqty, uomid, siteguid) VALUES ('" + UUID.randomUUID() + "', '"
                    + lapiz + "', 'CAJA" + run + "', 2, '000-each', '" + guid + "')");
            int n = nextTicket++;
            // venta: lápiz 3 (en dos renglones), hilo 2.5 kg, goma 1 de la que se devolvió 1, cargo de envío
            String venta = ticket(c, 0, n, true, lapiz, 2, hilo, 2.5, lapiz, 1, goma, 1, "DeliveryCharge", 1);
            exec(c, "UPDATE ticketlines SET refundqty = 1 WHERE ticket = '" + venta + "' AND product = '" + goma + "'");
            String devolucion = ticket(c, 1, n, false, lapiz, -1);

            // ---------- Buscar ----------
            Order o = PickAuditStore.find(c, String.valueOf(n));
            check("por número: la venta, no la devolución", o != null && o.ticketId.equals(venta), true);
            check("por el código del ticket", PickAuditStore.find(c, venta).number, n);
            check("la devolución no se audita", PickAuditStore.find(c, devolucion), null);
            check("número que no existe", PickAuditStore.find(c, "999999999"), null);
            check("es entrega con su dirección", o.delivery + " " + o.address, "true Calle Auditor 1");
            List<Line> meta = PickAuditStore.expected(c, venta);
            StringBuilder sb = new StringBuilder();
            for (Line l : meta) sb.append(l.name.split(" ")[0]).append('=').append(PickAuditStore.qty(l.expected)).append(' ');
            check("meta: renglones juntos, granel con decimales, sin devuelto ni cargo de envío", sb.toString().trim(), "Lápiz=3 Hilo=2.5");

            // ---------- Abrir y retomar ----------
            Audit a = PickAuditStore.open(c, o, admin, "Administrator");
            check("auditoría abierta", a.status + " " + a.lines.size(), "abierta 2");
            check("abrir otra vez la retoma", PickAuditStore.open(c, o, admin, "Administrator").id, a.id);

            // ---------- Escanear ----------
            check("código", scan(c, a, "AUD1" + run, null), "OK 1/3");
            check("referencia", scan(c, a, "REF-AUD1" + run, null), "OK 2/3");
            check("caja de 2 que se pasa", scan(c, a, "CAJA" + run, null).startsWith("NO: Excedente"), true);
            check("nada a medias: sigue en 2", PickAuditStore.get(c, a.id).line(lapiz).scanned, 2.0);
            check("código que no es del pedido", scan(c, a, "AUD3" + run, null).contains("NO pertenece"), true);
            check("código inexistente", scan(c, a, "XYZ" + run, null).contains("NO pertenece"), true);
            check("cantidad 0", scan(c, a, "AUD2" + run, 0.0), "NO: La cantidad debe ser mayor a 0.");
            check("granel a mano 1.75", scan(c, a, "AUD2" + run, 1.75), "OK 1.75/2.5");
            check("cerrar con faltantes sin justificar", close(c, a).contains("Lápiz auditor " + run + " (faltan 1)"), true);
            check("sigue abierta", PickAuditStore.get(c, a.id).status, "abierta");
            check("último lápiz", scan(c, a, "AUD1" + run, null), "OK 3/3");
            check("excedente", scan(c, a, "AUD1" + run, null).startsWith("NO: Excedente: de Lápiz"), true);
            try { PickAuditStore.justify(c, a.id, lapiz, true); check("justificar algo completo", false, true); }
            catch (PickAuditStore.Refused r) { check("justificar algo completo", r.getMessage().contains("ya está completo"), true); }
            check("justificar el hilo que falta", PickAuditStore.justify(c, a.id, hilo, true).justified, true);

            // ---------- Cerrar ----------
            check("cerrar", close(c, a), "CERRADA 1 faltantes / La entrega quedó como Preparada.");
            Audit cerrada = PickAuditStore.get(c, a.id);
            check("resultado", cerrada.status + " " + cerrada.result + " " + cerrada.closedBy, "cerrada faltantes Administrator");
            check("entrega preparada", DeliveryStatusStore.get(c, venta).status, "preparado");
            check("historial de la entrega", one(c, "SELECT CONCAT(op_id, ' ', issue, ' ', notes) FROM delivery_events WHERE ticket = '" + venta + "'"),
                    "auditoria-" + a.id + " faltante Auditada por Administrator. Faltó: Hilo granel auditor " + run + " (0.75);");
            check("cerrar dos veces", close(c, a), "NO: Esta auditoría ya está cerrada.");
            check("escanear cerrada", scan(c, a, "AUD1" + run, null), "NO: Esta auditoría ya está cerrada.");
            check("última auditoría del ticket", PickAuditStore.lastClosed(c, venta).id, a.id);
            Audit otra = PickAuditStore.open(c, o, admin, "Administrator");
            check("auditar de nuevo empieza de cero", otra.id != a.id && otra.line(lapiz).scanned == 0, true);
            PickAuditStore.discard(c, otra.id);
            check("descartar", PickAuditStore.get(c, otra.id), null);
            check("descartar no toca la cerrada", PickAuditStore.get(c, a.id).status, "cerrada");

            // ---------- Venta de mostrador, caja de 2, dos PCs a la vez ----------
            int m = nextTicket++;
            String mostrador = ticket(c, 0, m, false, lapiz, 3);
            Order om = PickAuditStore.find(c, String.valueOf(m));
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<Integer>> abiertas = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                abiertas.add(pool.submit(() -> { try (Connection k = connect()) { return PickAuditStore.open(k, om, admin, "Administrator").id; } }));
            }
            int id1 = abiertas.get(0).get(), id2 = abiertas.get(1).get();
            check("dos PCs abren la misma auditoría", id1 == id2, true);
            Audit b = PickAuditStore.get(c, id1);
            check("caja de 2", scan(c, b, "CAJA" + run, null), "OK 2/3");
            List<Future<String>> fut = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                fut.add(pool.submit(() -> { try (Connection k = connect()) { return scan(k, b, "AUD1" + run, null); } }));
            }
            List<String> res = new ArrayList<>();
            for (Future<String> f : fut) res.add(f.get().startsWith("OK") ? "OK" : "NO");
            pool.shutdown();
            Collections.sort(res);
            check("el último a la vez desde dos PCs: solo uno cuenta", res, List.of("NO", "OK"));
            check("no se pasa", PickAuditStore.get(c, b.id).line(lapiz).scanned, 3.0);
            check("mostrador cuadrado", close(c, b), "CERRADA 0 faltantes / null");
            check("resultado completo", PickAuditStore.get(c, b.id).result, "completo");
            check("historial", PickAuditStore.history(c, new java.sql.Timestamp(System.currentTimeMillis() - 3600_000), 50)
                    .stream().filter(x -> x.id == a.id || x.id == b.id).count(), 2L);

            // ---------- Venta sin mercancía ----------
            int s = nextTicket++;
            ticket(c, 0, s, false, "DeliveryCharge", 1);
            try { PickAuditStore.open(c, PickAuditStore.find(c, String.valueOf(s)), admin, "Administrator"); check("sin mercancía", false, true); }
            catch (PickAuditStore.Refused r) { check("sin mercancía", r.getMessage().contains("no tiene mercancía"), true); }
        } finally {
            for (String t : tickets) {
                exec(c, "DELETE FROM pick_audit_lines WHERE audit IN (SELECT id FROM pick_audit WHERE ticket = '" + t + "')");
                exec(c, "DELETE FROM pick_audit WHERE ticket = '" + t + "'");
                exec(c, "DELETE FROM delivery_events WHERE ticket = '" + t + "'");
                exec(c, "DELETE FROM delivery_status WHERE ticket = '" + t + "'");
                exec(c, "DELETE FROM customer_delivery WHERE id = '" + t + "'");
                // Chromis no deja borrar ventas (trigger): se quedan, como en las demás pruebas
            }
            for (String p : products) {
                exec(c, "DELETE FROM barcodes WHERE productid = '" + p + "'");
                try { exec(c, "DELETE FROM products WHERE id = '" + p + "'"); }
                catch (SQLException ex) { exec(c, "UPDATE products SET retired = 1, iscatalog = 0 WHERE id = '" + p + "'"); }
            }
        }
        System.out.println(fails == 0 ? "\nTODO BIEN" : "\nFALLAS: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
