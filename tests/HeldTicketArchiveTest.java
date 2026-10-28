import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.sql.*;
import java.util.*;
import uk.chromis.pos.heldtickets.HeldTicketArchive;
import uk.chromis.pos.heldtickets.HeldTicketArchive.Entry;
import uk.chromis.pos.heldtickets.HeldTicketArchive.How;
import uk.chromis.pos.sales.HeldTicketWatcher;
import uk.chromis.pos.ticket.TicketInfo;
import uk.chromis.pos.ticket.TicketLineInfo;

/**
 * Copia de las ventas en espera borradas (sharedtickets) contra una base
 * Chromis 1.5.4: el trigger guarda la copia, se quita al volver a guardarse,
 * la caja la lee, se descartan las vacías y las cobradas, quién la borró sale
 * de lineremoved, y restaurar la regresa a la lista.
 *
 *   java -cp dist/chromispos.jar tests/HeldTicketArchiveTest.java [url usuario contraseña]
 */
public class HeldTicketArchiveTest {
    static int fails = 0;
    static Connection c;
    static final List<String> shared = new ArrayList<>();
    static final List<String> tickets = new ArrayList<>();

    static void check(String what, Object got, Object exp) {
        boolean ok = Objects.equals(got, exp);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    static String one(String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
    static void exec(String sql) throws SQLException {
        try (Statement st = c.createStatement()) { st.executeUpdate(sql); }
    }
    static TicketInfo ticket(String... products) {
        TicketInfo t = new TicketInfo();
        for (String p : products) {
            TicketLineInfo l = new TicketLineInfo();
            l.setProperty("product.name", p);
            l.setMultiply(2);
            l.setPrice(10);
            t.addLine(l);
        }
        return t;
    }
    static byte[] bytes(TicketInfo t) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream o = new ObjectOutputStream(out)) { o.writeObject(t); }
        return out.toByteArray();
    }
    /** Pone en espera como lo hace Chromis (insertSharedTicket). */
    static String hold(String name, String user, TicketInfo t) throws Exception {
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO sharedtickets (id, name, content, pickupid, appuser, pickupbarcode) VALUES (?, ?, ?, 0, ?, ?)")) {
            ps.setString(1, id); ps.setString(2, name); ps.setBytes(3, bytes(t)); ps.setString(4, user); ps.setString(5, "P" + t.getId().substring(24));
            ps.executeUpdate();
        }
        shared.add(id);
        return id;
    }
    /** Lo que escribe Chromis (Audit.itemRemoved) por cada línea de una venta en espera borrada. */
    static void audit(TicketInfo t, String reason, String user, String terminal) throws SQLException {
        for (TicketLineInfo l : t.getLines()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO lineremoved (id, name, terminalid, ticketid, description, productid, productname, units, value) VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString()); ps.setString(2, user); ps.setString(3, terminal); ps.setString(4, t.getId());
                ps.setString(5, reason); ps.setString(6, l.getProductName()); ps.setDouble(7, l.getMultiply()); ps.setDouble(8, l.getPrice());
                ps.executeUpdate();
            }
        }
        tickets.add(t.getId());
    }
    static void readPending() throws Exception {
        for (Object[] p : HeldTicketArchive.pending(c, 500)) HeldTicketWatcher.read(c, (Long) p[0], (byte[]) p[1]);
    }
    static Entry find(String id) throws SQLException {
        for (Entry e : HeldTicketArchive.list(c, 7)) if (e.id.equals(id)) return e;
        return null;
    }
    static int copies(String id) throws SQLException {
        return Integer.parseInt(one("SELECT COUNT(*) FROM " + HeldTicketArchive.TABLE + " WHERE id = '" + id + "'"));
    }

    public static void main(String[] a) throws Exception {
        c = DriverManager.getConnection(a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos",
                a.length > 1 ? a[1] : "chromis", a.length > 2 ? a[2] : "chromis");
        try {
            check("tabla y triggers", HeldTicketArchive.ensure(c), null);
            check("dos veces no falla", HeldTicketArchive.ensure(c), null);

            TicketInfo ta = ticket("Arroz 1kg", "Frijol 1kg");
            String a1 = hold("CLIENTE A - prueba", "Carlos", ta);
            check("en espera no hay copia", copies(a1), 0);
            // La cajera la abre: Chromis la quita de la lista mientras está abierta
            exec("DELETE FROM sharedtickets WHERE id = '" + a1 + "'");
            check("al abrirla queda copia", copies(a1), 1);
            // y al pasar a otra venta la vuelve a guardar
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO sharedtickets (id, name, content, pickupid, appuser) VALUES (?, 'CLIENTE A - prueba', ?, 0, 'Carlos')")) {
                ps.setString(1, a1); ps.setBytes(2, bytes(ta)); ps.executeUpdate();
            }
            check("al volver a guardarla se quita la copia", copies(a1), 0);

            TicketInfo tb = ticket();
            String b1 = hold("VACIA - prueba", "Carlos", tb);
            TicketInfo tc = ticket("Aceite");
            String c1 = hold("CLIENTE C - prueba", "Isela", tc);
            TicketInfo td = ticket("Azúcar", "Sal", "Café");
            String d1 = hold("CLIENTE D - prueba", "Isela", td);

            // Borrar todas: Chromis audita cada línea y después borra la tabla completa (de prueba: solo las nuestras)
            audit(ta, "Shared Ticket Removed", "Jimena", "CAJA2");
            audit(tb, "Shared Ticket Removed", "Jimena", "CAJA2");
            exec("DELETE FROM sharedtickets WHERE id IN ('" + a1 + "', '" + b1 + "')");
            // La D se borró con el bote después de abrirla; la C se abrió y Chromis se cerró
            exec("DELETE FROM sharedtickets WHERE id = '" + d1 + "'");
            audit(td, "Full Ticket Removed", "Isela", "CAJA1");
            exec("DELETE FROM sharedtickets WHERE id = '" + c1 + "'");
            check("copias guardadas", copies(a1) + copies(b1) + copies(c1) + copies(d1), 4);
            check("quién borró en la base", one("SELECT db_user FROM " + HeldTicketArchive.TABLE + " WHERE id = '" + a1 + "'") != null, true);

            check("número de ticket sin abrirla", HeldTicketArchive.ticketIdOf(bytes(ta)), ta.getId());
            check("sabe que tiene productos", HeldTicketArchive.hasLines(bytes(ta)) + " / " + HeldTicketArchive.hasLines(bytes(tb)), "true / false");
            // Sin ninguna caja que la lea (la caja tiene el jar anterior): Admin igual sabe quién y cómo
            Entry pa = find(a1);
            check("antes de leerla, pendiente", pa == null ? null : pa.summarized, HeldTicketArchive.PENDING);
            check("sin leer: borrar todas por", pa.how + " " + pa.deletedBy + " en " + pa.terminal, How.DELETE_ALL + " Jimena en CAJA2");
            check("sin leer: productos de lo registrado", pa.fromAudit + " | " + pa.lineCount + " | " + pa.lines, "true | 2 | 2 x Arroz 1kg  $20.00\n2 x Frijol 1kg  $20.00");
            check("sin leer: la vacía ya no aparece", copies(b1), 0);
            Entry pc = find(c1);
            check("sin leer: sin registro de borrado", pc.how + " " + pc.fromAudit, How.NOT_SAVED + " false");
            readPending();
            check("la vacía se descarta", copies(b1), 0);
            Entry ea = find(a1);
            check("leída: ticket", ea.ticketId, ta.getId());
            check("leída: productos", ea.lineCount + " | " + ea.lines, "2 | 2 x Arroz 1kg  $20.00\n2 x Frijol 1kg  $20.00");
            check("leída: total", ea.total, 40.0);
            check("borrar todas", ea.how, How.DELETE_ALL);
            check("quién y dónde", ea.deletedBy + " en " + ea.terminal, "Jimena en CAJA2");
            check("capturada por", ea.appUser, "Carlos");
            Entry ed = find(d1);
            check("bote", ed.how + " " + ed.deletedBy, How.BIN + " Isela");
            Entry ec = find(c1);
            check("sin registro: se abrió y no se guardó", ec.how, How.NOT_SAVED);

            // La C sí se cobró después en otra caja: ya no está perdida
            String money = one("SELECT money FROM closedcash ORDER BY datestart DESC LIMIT 1");
            String admin = one("SELECT id FROM people WHERE name = 'Administrator'");
            int number = Integer.parseInt(one("SELECT GREATEST(COALESCE(MAX(ticketid), 0), 998000) FROM tickets")) + 1;
            exec("INSERT INTO receipts (id, money, datenew, person) VALUES ('" + tc.getId() + "', '" + money + "', NOW(), '" + admin + "')");
            exec("INSERT INTO tickets (id, tickettype, ticketid, person) VALUES ('" + tc.getId() + "', 0, " + number + ", '" + admin + "')");
            check("cobrada: ya no aparece", find(c1), null);
            HeldTicketArchive.purge(c);
            check("cobrada: se descarta la copia", copies(c1), 0);

            // Restaurar
            String sig = HeldTicketArchive.listSignature(c);
            HeldTicketArchive.restore(c, ea.archiveId);
            check("restaurada: otra vez en espera", one("SELECT COUNT(*) FROM sharedtickets WHERE id = '" + a1 + "'"), "1");
            check("la caja nota el cambio en la lista", HeldTicketArchive.listSignature(c).equals(sig), false);
            check("restaurada: mismo contenido", Arrays.equals(bytesOf(a1), bytes(ta)), true);
            check("restaurada: se quita de las borradas", copies(a1), 0);
            String msg = null;
            try { HeldTicketArchive.restore(c, ea.archiveId); } catch (SQLException ex) { msg = ex.getMessage(); }
            check("no se restaura dos veces", msg != null && msg.startsWith("Esa venta ya no está"), true);
            check("solo quedan las perdidas", find(d1) != null && find(a1) == null, true);
            // La restaurada se abre y Chromis se cierra: el borrado anterior (borrar todas) ya no cuenta
            exec("UPDATE lineremoved SET removeddate = removeddate - INTERVAL 2 MINUTE WHERE ticketid = '" + ta.getId() + "'");
            exec("DELETE FROM sharedtickets WHERE id = '" + a1 + "'");
            readPending();
            Entry again = find(a1);
            check("perdida otra vez: sin registro de borrado", again == null ? null : again.how + " " + again.deletedBy, How.NOT_SAVED + " null");
        } finally {
            for (String s : shared) {
                exec("DELETE FROM sharedtickets WHERE id = '" + s + "'");
                exec("DELETE FROM " + HeldTicketArchive.TABLE + " WHERE id = '" + s + "'");
            }
            for (String t : tickets) {
                try { exec("DELETE FROM lineremoved WHERE ticketid = '" + t + "'"); } catch (SQLException ex) { /* protegida */ }
            }
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
    static byte[] bytesOf(String id) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT content FROM sharedtickets WHERE id = '" + id + "'")) {
            rs.next();
            return rs.getBytes(1);
        }
    }
}
