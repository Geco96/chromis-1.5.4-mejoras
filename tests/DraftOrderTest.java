import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import uk.chromis.administration.receiving.PurchaseOrderStore;
import uk.chromis.pos.restock.DraftOrders;
import uk.chromis.pos.restock.RestockStore;
import uk.chromis.pos.settings.PosSettings;

/**
 * Prueba manual del pedido tentativo diario contra una base Chromis 1.5.4
 * (la que crea dbcreator.jar) con la ubicación MainStore. Crea sus propios
 * productos (referencias TENT-*) y proveedores de prueba, y al terminar borra
 * los borradores y ajustes que creó; los pedidos de prueba quedan cancelados
 * (Chromis no permite borrarlos) y los productos, con stock y umbrales en cero.
 *
 *   java -cp dist/admin.jar tests/DraftOrderTest.java [url usuario contraseña]
 */
public class DraftOrderTest {
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
    static String guid;

    /** Product TENT-<ref> with "manage stock", the given stock and thresholds. */
    static String product(Connection c, String ref, double stock, double min, double reserve, double max) throws SQLException {
        String id = one(c, "SELECT id FROM products WHERE reference = 'TENT-" + ref + "'");
        if (id == null) {
            id = UUID.randomUUID().toString();
            exec(c, "INSERT INTO products (id, reference, code, name, category, taxcat, pricebuy, pricesell, managestock, siteguid) "
                    + "VALUES ('" + id + "', 'TENT-" + ref + "', 'TENT" + ref + "', 'Prueba tentativo " + ref + "', "
                    + "(SELECT id FROM categories WHERE name = 'General Products'), "
                    + "(SELECT id FROM taxcategories WHERE name = 'Tax Standard'), 10, 15, 1, '" + guid + "')");
        }
        exec(c, "DELETE FROM stockcurrent WHERE product = '" + id + "'");
        exec(c, "INSERT INTO stockcurrent (location, product, units, siteguid) VALUES ('MainStore', '" + id + "', " + stock + ", '" + guid + "')");
        RestockStore.saveThresholds(c, "MainStore", guid, id, min, reserve, max);
        return id;
    }
    static String supplier(Connection c, String name) throws SQLException {
        String id = one(c, "SELECT id FROM suppliers WHERE suppliername = '" + name + "'");
        if (id == null) {
            id = UUID.randomUUID().toString();
            exec(c, "INSERT INTO suppliers (id, suppliername, siteguid) VALUES ('" + id + "', '" + name + "', '" + guid + "')");
        }
        return id;
    }
    static DraftOrders.Draft draftOf(List<DraftOrders.Draft> drafts, String supplierId) {
        for (DraftOrders.Draft d : drafts) if (d.supplierId.equals(supplierId)) return d;
        return null;
    }
    static DraftOrders.Line lineOf(DraftOrders.Draft d, String productId) {
        if (d == null) return null;
        for (DraftOrders.Line l : d.lines) if (l.productId.equals(productId)) return l;
        return null;
    }
    static Double qty(DraftOrders.Draft d, String productId) {
        DraftOrders.Line l = lineOf(d, productId);
        return l == null ? null : l.qty;
    }

    public static void main(String[] a) throws Exception {
        Connection c = DriverManager.getConnection(a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos",
                a.length > 1 ? a[1] : "chromis", a.length > 2 ? a[2] : "chromis");
        guid = one(c, "SELECT guid FROM siteguid");
        String admin = one(c, "SELECT id FROM people WHERE name = 'Administrator'");
        DraftOrders.createTables(c);
        DraftOrders.createTables(c); // dos veces no falla
        PurchaseOrderStore.createTables(c);
        String draftsBefore = one(c, "SELECT COUNT(*) FROM restock_drafts");
        Map<String, String> settingsBefore = new HashMap<>();
        for (String k : new String[]{DraftOrders.ENABLED, DraftOrders.TIME, DraftOrders.TERMINAL, DraftOrders.LAST_RUN,
                DraftOrders.LAST_STATUS, DraftOrders.LAST_FAILURE}) {
            settingsBefore.put(k, PosSettings.get(c, k, null));
        }
        List<String> orders = new ArrayList<>();
        String[] products = new String[5];
        try {
            // ---------- Horario ----------
            LocalTime at = LocalTime.of(16, 30);
            LocalDateTime d21 = LocalDateTime.of(2026, 10, 21, 0, 0);
            check("hora 16:30", DraftOrders.parseTime("16:30"), at);
            check("hora 9:05", DraftOrders.parseTime(" 9:05 "), LocalTime.of(9, 5));
            check("hora inválida", DraftOrders.parseTime("4:30 pm"), null);
            check("hora 25:00 inválida", DraftOrders.parseTime("25:00"), null);
            check("antes de las 16:30 la última fue ayer", DraftOrders.lastScheduled(d21.withHour(10), at), d21.minusDays(1).withHour(16).withMinute(30));
            check("a las 16:30 la última es hoy", DraftOrders.lastScheduled(d21.withHour(16).withMinute(30), at), d21.withHour(16).withMinute(30));
            check("armado ayer 16:30, hoy 10:00: no toca", DraftOrders.isDue(d21.minusDays(1).withHour(16).withMinute(30), d21.withHour(10), at), false);
            check("armado ayer 16:30, hoy 16:31: toca", DraftOrders.isDue(d21.minusDays(1).withHour(16).withMinute(30), d21.withHour(16).withMinute(31), at), true);
            check("armado hoy 16:31, hoy 18:00: no toca", DraftOrders.isDue(d21.withHour(16).withMinute(31), d21.withHour(18), at), false);
            check("caja apagada a las 16:30, se prende al día siguiente 9:00: toca", DraftOrders.isDue(d21.minusDays(1).withHour(12), d21.withHour(9), at), true);
            check("nunca armado: no toca", DraftOrders.isDue(null, d21.withHour(17), at), false);

            // ---------- Datos ----------
            String papelera = supplier(c, "Papelera Prueba");
            String otro = supplier(c, "Otro Proveedor Prueba");
            String generic = PurchaseOrderStore.genericSupplier(c, guid);
            String A = products[0] = product(c, "A", 2, 5, 0, 20);   // bajo: pedir 18, proveedor general
            String B = products[1] = product(c, "B", 0, 3, 0, 10);   // agotado: pedir 10, proveedor del producto
            String C = products[2] = product(c, "C", 8, 5, 10, 30);  // reserva: 22 menos 15 en camino = 7, último proveedor
            String D = products[3] = product(c, "D", 50, 5, 0, 30);  // normal: no va
            String E = products[4] = product(c, "E", 10, 2, 0, 0);   // normal sin máximo, pero faltaron 4
            exec(c, "DELETE FROM productsuppliers WHERE productid IN ('" + String.join("','", products) + "')");
            // Pedidos pendientes de corridas anteriores (o hechos a mano) de los productos de prueba no cuentan
            exec(c, "UPDATE supplierorderlines SET orderbalance = 0 WHERE productid IN ('" + String.join("','", products) + "')");
            exec(c, "UPDATE order_carryover SET to_order = 'x' WHERE to_order IS NULL AND product IN ('" + String.join("','", products) + "')");
            exec(c, "INSERT INTO productsuppliers (id, productid, supplierid, stockcode, packcost, siteguid) VALUES ('"
                    + UUID.randomUUID() + "', '" + B + "', '" + papelera + "', 'PP-B', 9.5, '" + guid + "')");
            PurchaseOrderStore.Order pending = PurchaseOrderStore.createOrder(c, guid, otro, admin,
                    List.of(new PurchaseOrderStore.NewLine(C, 15)));
            orders.add(pending.id);
            exec(c, "INSERT INTO order_carryover (product, units, from_order, from_ponumber) VALUES ('" + E + "', 4, 'x', 'PED-PRUEBA')");

            check("proveedor del producto", DraftOrders.supplierFor(c, B, guid), papelera);
            check("proveedor del último pedido", DraftOrders.supplierFor(c, C, guid), otro);
            check("sin proveedor: proveedor general", DraftOrders.supplierFor(c, A, guid), generic);

            // ---------- Armar ----------
            DraftOrders.Result r = DraftOrders.generate(c, guid);
            List<DraftOrders.Draft> open = DraftOrders.loadOpen(c);
            DraftOrders.Draft dGeneric = draftOf(open, generic), dPapelera = draftOf(open, papelera), dOtro = draftOf(open, otro);
            check("borrador del proveedor general", dGeneric != null, true);
            check("borrador de Papelera", dPapelera != null, true);
            check("borrador de Otro proveedor", dOtro != null, true);
            check("A bajo: máximo − stock", qty(dGeneric, A), 18.0);
            check("B agotado: con su proveedor", qty(dPapelera, B), 10.0);
            check("C reserva: menos lo que está en camino", qty(dOtro, C), 7.0);
            check("C muestra lo que está en camino", lineOf(dOtro, C).onOrder, 15.0);
            check("D normal no va", qty(dGeneric, D) == null && qty(dPapelera, D) == null && qty(dOtro, D) == null, true);
            check("E va por lo que faltó", qty(dGeneric, E), 4.0);
            check("E dice de qué pedido faltó", lineOf(dGeneric, E).carryFrom, "PED-PRUEBA");
            check("A con su stock y estado actuales", lineOf(dGeneric, A).getStatus(), RestockStore.Status.BAJO);

            // ---------- Volver a armar reemplaza ----------
            int openBefore = open.size();
            DraftOrders.Result r2 = DraftOrders.generate(c, guid);
            check("se reemplazan los borradores sin enviar", r2.replaced, openBefore);
            check("los borradores anteriores quedan como reemplazados", one(c, "SELECT status FROM restock_drafts WHERE id = '" + dGeneric.id + "'"), DraftOrders.REPLACED);
            check("el borrador reemplazado ya no está abierto", DraftOrders.isOpen(c, dGeneric.id), false);
            open = DraftOrders.loadOpen(c);
            dGeneric = draftOf(open, generic); dPapelera = draftOf(open, papelera); dOtro = draftOf(open, otro);

            // ---------- Editar ----------
            DraftOrders.setQuantity(c, dGeneric.id, A, 25);
            DraftOrders.setQuantity(c, dGeneric.id, D, 5);   // agregar uno que no venía
            DraftOrders.removeLine(c, dGeneric.id, E);
            DraftOrders.setQuantity(c, dOtro.id, C, 0);      // en 0 no se pide
            open = DraftOrders.loadOpen(c);
            dGeneric = draftOf(open, generic); dOtro = draftOf(open, otro);
            check("cantidad cambiada a mano", qty(dGeneric, A), 25.0);
            check("se conserva lo sugerido", lineOf(dGeneric, A).suggested, 18.0);
            check("producto agregado", qty(dGeneric, D), 5.0);
            check("producto quitado", qty(dGeneric, E), null);
            check("en 0 no cuenta como producto a pedir", dOtro.productCount(), 0);

            DraftOrders.Summary s = DraftOrders.summary(c);
            check("resumen: productos a pedir de los borradores de prueba (A, D, B)", s.products >= 3, true);

            // ---------- Descartar ----------
            check("descartar", DraftOrders.discard(c, dOtro.id, "Administrator"), true);
            check("descartar dos veces no hace nada", DraftOrders.discard(c, dOtro.id, "Administrator"), false);
            check("quién lo descartó", one(c, "SELECT decided_by FROM restock_drafts WHERE id = '" + dOtro.id + "'"), "Administrator");

            // ---------- Enviar ----------
            List<PurchaseOrderStore.NewLine> lines = new ArrayList<>();
            List<String> ours = Arrays.asList(products);
            for (DraftOrders.Line l : dGeneric.lines) if (l.qty > 0 && ours.contains(l.productId)) lines.add(new PurchaseOrderStore.NewLine(l.productId, l.qty));
            PurchaseOrderStore.Order sent = PurchaseOrderStore.createOrder(c, guid, dGeneric.supplierId, admin, lines);
            orders.add(sent.id);
            check("enviar", DraftOrders.markSent(c, dGeneric.id, "Administrator", sent.id, sent.ponumber), true);
            check("enviado con su folio", one(c, "SELECT ponumber FROM restock_drafts WHERE id = '" + dGeneric.id + "'"), sent.ponumber);
            check("no se puede enviar dos veces", DraftOrders.markSent(c, dGeneric.id, "Administrator", sent.id, sent.ponumber), false);
            check("el pedido tiene A y D", sent.lines.size(), 2);
            check("el envío ya cuenta como en camino", PurchaseOrderStore.onOrder(c).get(A), 25.0);

            // ---------- Horario con la base ----------
            exec(c, "DELETE FROM pos_settings WHERE name LIKE 'restock.draft.%'");
            LocalDateTime now = LocalDateTime.of(2026, 10, 21, 16, 31);
            check("primera vez: no arma, empieza a contar", DraftOrders.runIfDue(c, "CAJA1", now), null);
            check("primera vez: guarda la hora", PosSettings.get(c, DraftOrders.LAST_RUN, null), now.toString());
            PosSettings.put(c, DraftOrders.LAST_RUN, now.minusDays(1).withHour(10).toString());
            PosSettings.put(c, DraftOrders.TERMINAL, "CAJA2");
            check("otra caja designada: no arma", DraftOrders.runIfDue(c, "CAJA1", now), null);
            PosSettings.put(c, DraftOrders.TERMINAL, "caja1");
            PosSettings.put(c, DraftOrders.ENABLED, "0");
            check("apagado: no arma", DraftOrders.runIfDue(c, "CAJA1", now), null);
            PosSettings.put(c, DraftOrders.ENABLED, "1");
            DraftOrders.Result due = DraftOrders.runIfDue(c, "CAJA1", now);
            check("a las 16:31 arma (caja designada sin importar mayúsculas)", due != null && due.drafts > 0, true);
            check("guarda cuándo se armó", PosSettings.get(c, DraftOrders.LAST_RUN, null), now.toString());
            check("estado del último", PosSettings.get(c, DraftOrders.LAST_STATUS, "").contains("CAJA1"), true);
            check("un minuto después no vuelve a armar", DraftOrders.runIfDue(c, "CAJA1", now.plusMinutes(1)), null);
            PosSettings.put(c, DraftOrders.TIME, "18:00");
            check("con hora 18:00, a las 17:00 no arma", DraftOrders.runIfDue(c, "CAJA1", now.withHour(17)), null);
            check("con hora 18:00, a las 18:00 arma", DraftOrders.runIfDue(c, "CAJA1", now.withHour(18).withMinute(0)) != null, true);
            PosSettings.put(c, DraftOrders.TERMINAL, "");
            check("sin caja designada arma cualquiera", DraftOrders.runIfDue(c, "OTRA", now.plusDays(1).withHour(18).withMinute(5)) != null, true);
        } finally {
            exec(c, "DELETE FROM restock_draft_lines WHERE draft IN (SELECT id FROM restock_drafts WHERE location = 'MainStore' "
                    + "AND created >= NOW() - INTERVAL 1 HOUR)");
            exec(c, "DELETE FROM restock_drafts WHERE location = 'MainStore' AND created >= NOW() - INTERVAL 1 HOUR");
            for (String o : orders) {
                exec(c, "UPDATE order_carryover SET to_order = NULL WHERE to_order = '" + o + "'");
                // Chromis no deja borrar pedidos: quedan cancelados y sin saldo
                exec(c, "UPDATE supplierorderlines SET orderbalance = 0 WHERE orderid = '" + o + "'");
                exec(c, "UPDATE supplierorders SET status = 'Cancelled' WHERE id = '" + o + "'");
            }
            exec(c, "DELETE FROM order_carryover WHERE from_ponumber = 'PED-PRUEBA'");
            for (String p : products) {
                if (p != null) {
                    exec(c, "UPDATE stockcurrent SET units = 0 WHERE product = '" + p + "'");
                    RestockStore.saveThresholds(c, "MainStore", guid, p, 0, 0, 0);
                }
            }
            exec(c, "DELETE FROM pos_settings WHERE name LIKE 'restock.draft.%'");
            for (Map.Entry<String, String> e : settingsBefore.entrySet()) {
                if (e.getValue() != null) PosSettings.put(c, e.getKey(), e.getValue());
            }
        }
        check("borradores al terminar como al empezar", one(c, "SELECT COUNT(*) FROM restock_drafts"), draftsBefore);
        System.out.println(fails == 0 ? "\nTODO BIEN" : "\nFALLAS: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
