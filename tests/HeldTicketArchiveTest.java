import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
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
        for (Entry e : HeldTicketArchive.list(c, 7, false)) if (e.id.equals(id)) return e;
        return null;
    }
    /** Con las cobradas. */
    static Entry findAll(String id) throws SQLException {
        for (Entry e : HeldTicketArchive.list(c, 7, true)) if (e.id.equals(id)) return e;
        return null;
    }
    static String trigger(String name) throws SQLException {
        return one("SELECT action_statement FROM information_schema.triggers WHERE trigger_schema = DATABASE() AND trigger_name = '" + name + "'");
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
            check("cobrada: no aparece entre las perdidas", find(c1), null);
            HeldTicketArchive.purge(c);
            check("cobrada: queda solo el resumen", copies(c1) + " " + one("SELECT content IS NULL FROM " + HeldTicketArchive.TABLE + " WHERE id = '" + c1 + "'"), "1 1");
            Entry cc = findAll(c1);
            check("cobrada: en qué ticket y quién", cc == null ? null : cc.how + " " + cc.chargedTicket + " " + cc.chargedBy + " " + cc.lineCount, How.CHARGED + " " + number + " Administrator 1");
            check("cobrada: no se restaura", cc.restorable(), false);
            String cmsg = null;
            try { HeldTicketArchive.restore(c, cc.archiveId); } catch (SQLException ex) { cmsg = ex.getMessage(); }
            check("cobrada: restaurar explica", cmsg != null && cmsg.contains("se cobró"), true);

            // Restaurar: vuelve como venta en espera nueva (otro id y otro número de ticket adentro)
            String sig = HeldTicketArchive.listSignature(c);
            String r1 = HeldTicketArchive.restore(c, ea.archiveId);
            shared.add(r1);
            check("restaurada: otra vez en espera, con otro id", !r1.equals(a1) && "1".equals(one("SELECT COUNT(*) FROM sharedtickets WHERE id = '" + r1 + "'")), true);
            check("restaurada: nombre", one("SELECT name FROM sharedtickets WHERE id = '" + r1 + "'"), "CLIENTE A - prueba" + HeldTicketArchive.RESTORED);
            check("la caja nota el cambio en la lista", HeldTicketArchive.listSignature(c).equals(sig), false);
            TicketInfo back;
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytesOf(r1)))) { back = (TicketInfo) in.readObject(); }
            check("restaurada: se abre con sus productos", back.getLinesCount() + " " + back.getLine(0).getProductName(), "2 Arroz 1kg");
            check("restaurada: otro número de ticket", !back.getId().equals(ta.getId()) && back.getId().equals(HeldTicketArchive.ticketIdOf(bytesOf(r1))), true);
            check("restaurada: código de recogida", one("SELECT pickupbarcode FROM sharedtickets WHERE id = '" + r1 + "'"), "P" + back.getId().substring(24));
            check("restaurada: se quita de las borradas", copies(a1), 0);
            // La caja que la tenía abierta la guarda: ya no choca con la restaurada
            String err = null;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO sharedtickets (id, name, content, pickupid, appuser) VALUES (?, 'CLIENTE A - prueba', ?, 0, 'Carlos')")) {
                ps.setString(1, a1); ps.setBytes(2, bytes(ta)); ps.executeUpdate();
            } catch (SQLException ex) { err = ex.getMessage(); }
            check("la caja guarda la suya sin error", err, null);
            exec("DELETE FROM sharedtickets WHERE id = '" + a1 + "'");
            exec("DELETE FROM " + HeldTicketArchive.TABLE + " WHERE id = '" + a1 + "'");
            String msg = null;
            try { HeldTicketArchive.restore(c, ea.archiveId); } catch (SQLException ex) { msg = ex.getMessage(); }
            check("no se restaura dos veces", msg != null && msg.startsWith("Esa venta ya no está"), true);
            // La restaurada se abre y Chromis se cierra: sin registro de borrado
            exec("DELETE FROM sharedtickets WHERE id = '" + r1 + "'");
            readPending();
            Entry again = find(r1);
            check("perdida otra vez: sin registro de borrado", again == null ? null : again.how + " " + again.deletedBy, How.NOT_SAVED + " null");

            // Le quitan los productos uno por uno y la caja pasa a otra venta: Chromis guarda el ticket vacío y lo borra
            TicketInfo te = ticket("Cuaderno", "Lápiz");
            String e1 = hold("CLIENTE E - prueba", "Laura", te);
            exec("DELETE FROM sharedtickets WHERE id = '" + e1 + "'");
            audit(te, "Single Item Removed", "Laura", "CAJA3");
            TicketInfo emptied = ticket();
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO sharedtickets (id, name, content, pickupid, appuser) VALUES (?, 'CLIENTE E - prueba', ?, 0, 'Laura')")) {
                ps.setString(1, e1); ps.setBytes(2, bytesWithId(emptied, te.getId())); ps.executeUpdate();
            }
            check("vacía guardada: la copia con productos se queda", copies(e1), 1);
            exec("DELETE FROM sharedtickets WHERE id = '" + e1 + "'");
            Entry ee = find(e1);
            check("vaciada: se ve la de productos", ee == null ? null : ee.lineCount + " " + ee.how + " " + ee.deletedBy + " en " + ee.terminal, "2 " + How.EMPTIED + " Laura en CAJA3");
            readPending();
            Entry ee2 = find(e1);
            check("vaciada: la caja la lee", ee2 == null ? null : ee2.summarized + " " + ee2.lineCount, HeldTicketArchive.READ + " 2");

            // Abierta en una caja: Admin dice dónde y no deja restaurarla; la caja la marca cada minuto
            TicketInfo tf = ticket("Tijeras");
            String f1 = hold("CLIENTE F - prueba", "Liz", tf);
            exec("DELETE FROM sharedtickets WHERE id = '" + f1 + "'");
            HeldTicketArchive.opened(c, f1, "Laura", "CAJA2");
            Entry ef = find(f1);
            check("abierta: dónde y quién", ef == null ? null : ef.how + " " + ef.openedBy + " " + ef.openedTerminal + " " + ef.restorable(), How.OPEN + " Laura CAJA2 false");
            exec("UPDATE " + HeldTicketArchive.TABLE + " SET seen_open_at = NOW() - INTERVAL 10 MINUTE WHERE id = '" + f1 + "'");
            Entry ef2 = find(f1);
            check("la caja ya no la tiene: perdida, con dónde se abrió", ef2.how + " " + ef2.openedTerminal + " " + ef2.restorable(), How.NOT_SAVED + " CAJA2 true");
            HeldTicketArchive.stillOpen(c, f1);
            check("la caja la sigue teniendo", find(f1).how, How.OPEN);
            HeldTicketArchive.stillOpen(c, UUID.randomUUID().toString());

            // La firma de la lista cambia aunque el número de ventas sea el mismo
            String g1 = hold("CLIENTE G - prueba", "Liz", ticket("Goma"));
            String sigA = HeldTicketArchive.listSignature(c);
            exec("DELETE FROM sharedtickets WHERE id = '" + g1 + "'");
            String g2 = hold("CLIENTE G2 - prueba", "Liz", ticket("Goma"));
            check("firma: mismo número, otras ventas", HeldTicketArchive.listSignature(c).equals(sigA), false);

            // Los triggers nunca hacen fallar a Chromis: sin la tabla de copias, borrar y guardar ventas en espera sigue funcionando
            check("triggers con resguardo", trigger("sharedtickets_guardar_borrada").contains("SQLEXCEPTION") && trigger("sharedtickets_quitar_borrada").contains("SQLEXCEPTION"), true);
            exec("RENAME TABLE " + HeldTicketArchive.TABLE + " TO " + HeldTicketArchive.TABLE + "_x");
            String terr = null;
            try {
                exec("DELETE FROM sharedtickets WHERE id = '" + g2 + "'");
                hold("CLIENTE H - prueba", "Liz", ticket("Regla"));
            } catch (SQLException ex) { terr = ex.getMessage(); }
            finally { exec("RENAME TABLE " + HeldTicketArchive.TABLE + "_x TO " + HeldTicketArchive.TABLE); }
            check("sin la tabla de copias, Chromis no falla", terr, null);

            // Triggers y tabla de versiones anteriores: se actualizan solos
            exec("DROP TRIGGER sharedtickets_guardar_borrada");
            exec("CREATE TRIGGER sharedtickets_guardar_borrada BEFORE DELETE ON sharedtickets FOR EACH ROW INSERT INTO " + HeldTicketArchive.TABLE
                    + " (id, name, content, pickupid, appuser, loyaltycard, pickupbarcode, deliveryinfo, deleted_at, db_user) VALUES (OLD.id, OLD.name, "
                    + "OLD.content, OLD.pickupid, OLD.appuser, OLD.loyaltycard, OLD.pickupbarcode, OLD.deliveryinfo, NOW(), USER())");
            exec("DROP TRIGGER sharedtickets_quitar_borrada");
            exec("CREATE TRIGGER sharedtickets_quitar_borrada AFTER INSERT ON sharedtickets FOR EACH ROW DELETE FROM " + HeldTicketArchive.TABLE + " WHERE id = NEW.id");
            exec("ALTER TABLE " + HeldTicketArchive.TABLE + " DROP COLUMN opened_by, DROP COLUMN opened_terminal, DROP COLUMN seen_open_at");
            check("actualiza versiones anteriores", HeldTicketArchive.ensure(c), null);
            check("triggers nuevos", trigger("sharedtickets_guardar_borrada").contains("SQLEXCEPTION") && trigger("sharedtickets_quitar_borrada").contains("TicketLineInfo"), true);
            check("columnas nuevas", one("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '" + HeldTicketArchive.TABLE + "' AND column_name IN ('opened_by', 'opened_terminal', 'seen_open_at')"), "3");

            // Un respaldo en curso no deja cambiar los triggers: se rinde en segundos (no deja esperando a las cajas) y lo vuelve a intentar
            exec("DROP TRIGGER sharedtickets_quitar_borrada");
            String waitBefore = one("SELECT @@SESSION.lock_wait_timeout");
            try (Connection backup = DriverManager.getConnection(a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos",
                    a.length > 1 ? a[1] : "chromis", a.length > 2 ? a[2] : "chromis")) {
                backup.setAutoCommit(false);
                try (Statement st = backup.createStatement()) {
                    st.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");
                    st.executeQuery("SELECT COUNT(*) FROM sharedtickets").close();
                }
                long t0 = System.currentTimeMillis();
                String busy = HeldTicketArchive.ensure(c);
                long secs = (System.currentTimeMillis() - t0) / 1000;
                check("con respaldo en curso: se rinde rápido", busy != null && busy.startsWith("Un respaldo en curso") && secs < 10, true);
                check("deja el tiempo de espera como estaba", one("SELECT @@SESSION.lock_wait_timeout"), waitBefore);
                backup.commit();
            }
            check("después del respaldo sí", HeldTicketArchive.ensure(c) + " " + (trigger("sharedtickets_quitar_borrada") != null), "null true");
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
    /** El ticket con el número de otro (el mismo ticket después de quitarle los productos). */
    static byte[] bytesWithId(TicketInfo t, String id) throws Exception {
        return HeldTicketArchive.replace(bytes(t), t.getId(), id);
    }
    static byte[] bytesOf(String id) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT content FROM sharedtickets WHERE id = '" + id + "'")) {
            rs.next();
            return rs.getBytes(1);
        }
    }
}
