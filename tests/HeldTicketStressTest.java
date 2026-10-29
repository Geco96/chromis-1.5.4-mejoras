import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Writer;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import uk.chromis.pos.backup.DatabaseDumper;
import uk.chromis.pos.heldtickets.HeldTicketArchive;
import uk.chromis.pos.sales.HeldTicketWatcher;
import uk.chromis.pos.ticket.TicketInfo;
import uk.chromis.pos.ticket.TicketLineInfo;

/**
 * Varias cajas a la vez con las ventas en espera, con los triggers de copia,
 * el vigilante de cada caja y un respaldo corriendo: ninguna operación de
 * Chromis sobre sharedtickets debe fallar, ninguna venta se pierde ni se
 * duplica, y una venta solo la abre la caja que la quitó de la lista (como
 * hace ahora JTicketsBagShared).
 *
 *   java -cp dist/chromispos.jar tests/HeldTicketStressTest.java [url usuario contraseña]
 *   (necesita la configuración de una caja en la carpeta y pantalla, como HeldTicketArchiveTest)
 */
public class HeldTicketStressTest {
    static String url, user, pass;
    static final int TILLS = 6, ROUNDS = 150;
    static final Set<String> expected = ConcurrentHashMap.newKeySet();
    static final List<String> created = Collections.synchronizedList(new ArrayList<>());
    static final AtomicInteger errors = new AtomicInteger(), races = new AtomicInteger(), opens = new AtomicInteger(),
            charged = new AtomicInteger(), binned = new AtomicInteger(), saved = new AtomicInteger(), backups = new AtomicInteger();
    static volatile boolean done;
    static byte[] withLines, empty;
    static String templateId, emptyId;

    static Connection connect() throws SQLException { return DriverManager.getConnection(url, user, pass); }

    static byte[] bytes(TicketInfo t) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream o = new ObjectOutputStream(out)) { o.writeObject(t); }
        return out.toByteArray();
    }

    static void insert(Connection c, String id, byte[] content) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO sharedtickets (id, name, content, pickupid, appuser) VALUES (?, ?, ?, 0, 'Estres')")) {
            ps.setString(1, id); ps.setString(2, "ESTRES - " + id.substring(0, 8)); ps.setBytes(3, content);
            ps.executeUpdate();
        }
    }

    static int delete(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM sharedtickets WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate();
        }
    }

    static void till(int n) {
        Random r = new Random(n);
        try (Connection c = connect()) {
            for (int i = 0; i < ROUNDS; i++) {
                try {
                    int op = r.nextInt(10);
                    if (op < 3 || expected.isEmpty()) {
                        // Poner en espera una venta nueva
                        String id = UUID.randomUUID().toString();
                        created.add(id);
                        insert(c, id, HeldTicketArchive.replace(withLines, templateId, UUID.randomUUID().toString()));
                        expected.add(id);
                    } else if (op < 9) {
                        // Abrir una de la lista: solo si esta caja la quitó (como JTicketsBagShared ahora)
                        List<String> ids = new ArrayList<>(expected);
                        String id = ids.get(r.nextInt(ids.size()));
                        String content;
                        try (PreparedStatement ps = c.prepareStatement("SELECT content FROM sharedtickets WHERE id = ?")) {
                            ps.setString(1, id);
                            try (ResultSet rs = ps.executeQuery()) { content = rs.next() ? "ok" : null; }
                        }
                        if (content == null || delete(c, id) == 0) {
                            races.incrementAndGet();
                            continue;
                        }
                        expected.remove(id);
                        opens.incrementAndGet();
                        Thread.sleep(r.nextInt(20));
                        int what = r.nextInt(10);
                        if (what < 6) {
                            insert(c, id, HeldTicketArchive.replace(withLines, templateId, UUID.randomUUID().toString()));
                            expected.add(id);
                            saved.incrementAndGet();
                        } else if (what < 8) {
                            charged.incrementAndGet();
                        } else {
                            binned.incrementAndGet();
                        }
                    } else {
                        // Pasar a otra venta con el ticket vacío: Chromis lo guarda y lo borra
                        String id = UUID.randomUUID().toString();
                        insert(c, id, HeldTicketArchive.replace(empty, emptyId, UUID.randomUUID().toString()));
                        delete(c, id);
                    }
                } catch (SQLException ex) {
                    errors.incrementAndGet();
                    System.out.println("ERROR caja " + n + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            errors.incrementAndGet();
            ex.printStackTrace();
        }
    }

    public static void main(String[] a) throws Exception {
        url = a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos";
        user = a.length > 1 ? a[1] : "chromis";
        pass = a.length > 2 ? a[2] : "chromis";
        TicketInfo t = new TicketInfo();
        for (String p : new String[]{"Arroz", "Frijol", "Aceite"}) {
            TicketLineInfo l = new TicketLineInfo();
            l.setProperty("product.name", p); l.setMultiply(1); l.setPrice(10);
            t.addLine(l);
        }
        withLines = bytes(t);
        templateId = t.getId();
        TicketInfo e = new TicketInfo();
        empty = bytes(e);
        emptyId = e.getId();
        int fails = 0;
        try (Connection c = connect()) {
            String problem = HeldTicketArchive.ensure(c);
            if (problem != null) throw new IllegalStateException(problem);
            int before = Integer.parseInt(one(c, "SELECT COUNT(*) FROM sharedtickets"));

            ExecutorService pool = Executors.newFixedThreadPool(TILLS + 3);
            // El vigilante de dos cajas, cada 50 ms en vez de cada minuto
            for (int w = 0; w < 2; w++) {
                pool.execute(() -> {
                    try (Connection wc = connect()) {
                        while (!done) {
                            for (Object[] p : HeldTicketArchive.pending(wc, 200)) HeldTicketWatcher.read(wc, (Long) p[0], (byte[]) p[1]);
                            HeldTicketArchive.purge(wc);
                            HeldTicketArchive.list(wc, 1, true);
                            HeldTicketArchive.listSignature(wc);
                            List<String> ids = new ArrayList<>(created);
                            if (!ids.isEmpty()) HeldTicketArchive.stillOpen(wc, ids.get(ids.size() - 1));
                            Thread.sleep(50);
                        }
                    } catch (Exception ex) { errors.incrementAndGet(); System.out.println("ERROR vigilante: " + ex); }
                });
            }
            // Un respaldo tras otro mientras tanto
            pool.execute(() -> {
                try (Connection bc = connect()) {
                    while (!done) {
                        new DatabaseDumper(bc).dump(Writer.nullWriter());
                        backups.incrementAndGet();
                    }
                } catch (Exception ex) { errors.incrementAndGet(); System.out.println("ERROR respaldo: " + ex); }
            });
            List<Future<?>> tills = new ArrayList<>();
            long t0 = System.currentTimeMillis();
            for (int n = 0; n < TILLS; n++) { final int k = n; tills.add(pool.submit(() -> till(k))); }
            for (Future<?> f : tills) f.get();
            done = true;
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.MINUTES);

            Set<String> inList = new HashSet<>();
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id FROM sharedtickets WHERE appuser = 'Estres'")) {
                while (rs.next()) inList.add(rs.getString(1));
            }
            System.out.println("cajas " + TILLS + " x " + ROUNDS + " en " + (System.currentTimeMillis() - t0) / 1000 + " s: abiertas " + opens
                    + ", guardadas otra vez " + saved + ", cobradas " + charged + ", al bote " + binned + ", ya la tenía otra caja " + races
                    + ", respaldos completos " + backups);
            boolean ok = errors.get() == 0;
            System.out.println((ok ? "OK   " : "FAIL ") + "ninguna operación falló -> " + errors.get());
            if (!ok) fails++;
            ok = inList.equals(expected);
            System.out.println((ok ? "OK   " : "FAIL ") + "la lista es exactamente la esperada (sin perdidas ni duplicadas) -> " + inList.size() + " / " + expected.size());
            if (!ok) fails++;
            ok = backups.get() > 0;
            System.out.println((ok ? "OK   " : "FAIL ") + "hubo respaldos durante la prueba -> " + backups.get());
            if (!ok) fails++;
            ok = Integer.parseInt(one(c, "SELECT COUNT(*) FROM sharedtickets")) == before + inList.size();
            System.out.println((ok ? "OK   " : "FAIL ") + "las demás ventas en espera no se tocaron");
            if (!ok) fails++;
            // Cada venta abierta y no vuelta a guardar tiene su copia (las cobradas aquí no tienen ticket, siguen como perdidas)
            int lost = charged.get() + binned.get();
            // (las de tickets vacíos que el vigilante no alcanzó a descartar al terminar la prueba no cuentan)
            String withProducts = "d.name LIKE 'ESTRES - %' AND d.id NOT IN (SELECT id FROM sharedtickets) AND LOCATE('TicketLineInfo', d.content) > 0";
            int copies = Integer.parseInt(one(c, "SELECT COUNT(DISTINCT d.id) FROM " + HeldTicketArchive.TABLE + " d WHERE " + withProducts));
            System.out.println("     (copias de tickets vacíos aún sin descartar: " + one(c, "SELECT COUNT(*) FROM " + HeldTicketArchive.TABLE
                    + " d WHERE d.name LIKE 'ESTRES - %' AND LOCATE('TicketLineInfo', d.content) = 0") + ")");
            ok = copies == lost;
            System.out.println((ok ? "OK   " : "FAIL ") + "una copia por cada venta que salió de la lista -> " + copies + " / " + lost);
            if (!ok) fails++;
        } finally {
            try (Connection c = connect(); Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM sharedtickets WHERE appuser = 'Estres'");
                st.executeUpdate("DELETE FROM " + HeldTicketArchive.TABLE + " WHERE name LIKE 'ESTRES - %'");
            }
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }

    static String one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { return rs.next() ? rs.getString(1) : null; }
    }
}
