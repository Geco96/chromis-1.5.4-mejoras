import java.io.File;
import java.sql.*;
import java.util.*;
import uk.chromis.administration.receiving.PurchaseOrderStore;
import uk.chromis.administration.receiving.PurchaseOrderStore.Line;
import uk.chromis.administration.receiving.PurchaseOrderStore.Order;
import uk.chromis.administration.receiving.ReceiptPdf;
import uk.chromis.administration.restock.RestockPdf;

/**
 * Prueba manual de pedido → recepción → autorización de costos contra una base
 * Chromis 1.5.4 con ARROZ, BOLSA y ESPIRAL en MainStore. Genera el PDF del
 * pedido y el contrarecibo en la carpeta indicada. Devuelve stock, costo y
 * precio a como estaban; los pedidos quedan registrados (Chromis no permite
 * borrarlos) y el segundo pedido queda cancelado. Los faltantes que ya
 * estaban pendientes siguen pendientes.
 *
 *   java -cp dist/admin.jar tests/ReceivingTest.java CARPETA_PDF [url usuario contraseña]
 */
public class ReceivingTest {
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
    static double units(Connection c, String product) throws SQLException {
        String u = one(c, "SELECT units FROM stockcurrent WHERE product='" + product + "' AND location='MainStore'");
        return u == null ? 0 : Double.parseDouble(u);
    }
    static Line line(Order o, String productId) {
        for (Line l : o.lines) if (productId.equals(l.productId)) return l;
        return null;
    }
    public static void main(String[] a) throws Exception {
        String dir = a[0];
        Connection c = DriverManager.getConnection(a.length > 1 ? a[1] : "jdbc:mysql://localhost:3306/chromispos",
                a.length > 2 ? a[2] : "chromis", a.length > 3 ? a[3] : "chromis");
        String guid = one(c, "SELECT guid FROM siteguid");
        String admin = one(c, "SELECT id FROM people WHERE name='Administrator'");
        String arroz = one(c, "SELECT id FROM products WHERE reference='ARROZ'");
        String bolsa = one(c, "SELECT id FROM products WHERE reference='BOLSA'");
        String espiral = one(c, "SELECT id FROM products WHERE reference='ESPIRAL'");
        double sArroz = units(c, arroz), sBolsa = units(c, bolsa), sEspiral = units(c, espiral);
        String costArroz = one(c, "SELECT pricebuy FROM products WHERE id='" + arroz + "'");
        String priceArroz = one(c, "SELECT pricesell FROM products WHERE id='" + arroz + "'");
        String diary = one(c, "SELECT COALESCE(MAX(sequenceid),0) FROM stockdiary");
        String carryMax = null;
        String second = null;
        PurchaseOrderStore.createTables(c);
        PurchaseOrderStore.createTables(c);
        try {
            exec(c, "UPDATE products SET pricebuy = 20, pricesell = 30 WHERE id='" + arroz + "'");
            carryMax = one(c, "SELECT COALESCE(MAX(id),0) FROM order_carryover");
            exec(c, "CREATE TEMPORARY TABLE carry_before SELECT id FROM order_carryover WHERE to_order IS NULL");
            exec(c, "UPDATE order_carryover SET to_order = 'prueba-pausa' WHERE to_order IS NULL");
            exec(c, "UPDATE price_changes SET status='RECHAZADO' WHERE status='PENDIENTE'");

            String gen = PurchaseOrderStore.genericSupplier(c, guid);
            check("proveedor genérico una sola vez", PurchaseOrderStore.genericSupplier(c, guid), gen);
            check("lista de proveedores empieza con el genérico",
                    PurchaseOrderStore.suppliers(c, guid).keySet().iterator().next(), PurchaseOrderStore.GENERIC_SUPPLIER);

            // ---- Pedido ----
            String expected = PurchaseOrderStore.nextOrderNumber(c);
            Order o = PurchaseOrderStore.createOrder(c, guid, gen, admin, Arrays.asList(
                    new PurchaseOrderStore.NewLine(arroz, 10), new PurchaseOrderStore.NewLine(bolsa, 5),
                    new PurchaseOrderStore.NewLine(espiral, 0)));
            check("folio", o.ponumber, expected);
            check("folio con formato PED-000000", o.ponumber.matches("PED-\\d{6}"), true);
            check("renglones (cantidad 0 no se pide)", o.lines.size(), 2);
            check("costo esperado = costo del producto", line(o, arroz).orderCost, 20.0);
            check("pendiente", o.status, PurchaseOrderStore.PENDING);
            check("guarda el id del usuario (pantalla Orders)", one(c, "SELECT user FROM supplierorders WHERE id='" + o.id + "'"), admin);
            check("muestra su nombre", o.user, "Administrator");
            check("aparece en pendientes", PurchaseOrderStore.openOrders(c).get(o.ponumber), o.id);
            int num = Integer.parseInt(o.ponumber.substring(4));
            check("escaneo exacto", PurchaseOrderStore.findOrder(c, o.ponumber), o.id);
            check("minúsculas", PurchaseOrderStore.findOrder(c, o.ponumber.toLowerCase()), o.id);
            check("solo el número", PurchaseOrderStore.findOrder(c, String.valueOf(num)), o.id);
            check("PED- sin ceros", PurchaseOrderStore.findOrder(c, "PED-" + num), o.id);
            check("no existe", PurchaseOrderStore.findOrder(c, "PED-999999"), null);
            check("lo recibido empieza igual a lo pedido", line(o, arroz).received, 10.0);

            List<RestockPdf.Line> pdfLines = new ArrayList<>();
            for (Line l : o.lines) {
                RestockPdf.Line p = new RestockPdf.Line();
                p.reference = l.reference; p.name = l.name; p.qty = l.ordered; p.cost = l.orderCost;
                p.current = 2.0; p.minimum = 5.0; p.maximum = 12.0;
                pdfLines.add(p);
            }
            pdfLines.get(0).note = "Incluye 3 que faltaron en PED-000001";
            File pedido = new File(dir, "Pedido_" + o.ponumber + ".pdf");
            RestockPdf.write(pedido, pdfLines, "Tlapalería Prueba", null, "Prueba", o.ponumber, PurchaseOrderStore.GENERIC_SUPPLIER);
            check("PDF del pedido", pedido.length() > 1000, true);

            // ---- Recepción con diferencias ----
            Order r = PurchaseOrderStore.loadOrder(c, PurchaseOrderStore.findOrder(c, o.ponumber));
            line(r, arroz).received = 7;          // faltan 3
            line(r, arroz).receivedCost = 22;     // el costo subió
            line(r, bolsa).received = 6;          // sobra 1
            Line extra = PurchaseOrderStore.extraLine(c, espiral);
            extra.received = 2;                   // no venía en el pedido
            r.lines.add(extra);
            check("observación faltante", line(r, arroz).observation(), "Falta 3 · Costo sube");
            check("observación sobrante", line(r, bolsa).observation(), "Sobra 1");
            check("observación extra", extra.observation(), "No venía en el pedido");
            PurchaseOrderStore.ReceiptResult res = PurchaseOrderStore.receive(c, r, "MainStore", guid,
                    admin, "Prueba", "FAC-123");
            check("faltantes", res.shortages, 1);
            check("sobrantes + extras", res.extras, 2);
            check("cambios de costo", res.priceChanges, 1);
            check("unidades recibidas", res.receivedUnits, 15.0);
            check("stock arroz +7", units(c, arroz) - sArroz, 7.0);
            check("stock bolsa +6", units(c, bolsa) - sBolsa, 6.0);
            check("stock espiral +2", units(c, espiral) - sEspiral, 2.0);
            check("diario motivo 9", one(c, "SELECT COUNT(*) FROM stockdiary WHERE reason=9 AND orderid='" + o.id
                    + "' AND reference='Pedido " + o.ponumber + "'"), "3");
            check("costo NO cambia sin autorizar", one(c, "SELECT pricebuy FROM products WHERE id='" + arroz + "'"), "20.00");
            check("estado con faltantes", one(c, "SELECT status FROM supplierorders WHERE id='" + o.id + "'"), PurchaseOrderStore.SHORT);
            check("factura", one(c, "SELECT invoiceno FROM supplierorders WHERE id='" + o.id + "'"), "FAC-123");
            check("saldo del renglón", one(c, "SELECT orderbalance FROM supplierorderlines WHERE orderid='" + o.id
                    + "' AND productid='" + arroz + "'"), "3.000");
            check("renglón extra agregado", one(c, "SELECT COUNT(*) FROM supplierorderlines WHERE orderid='" + o.id + "'"), "3");
            check("receiptedorderlines (pantalla Orders)", one(c, "SELECT COUNT(*) FROM receiptedorderlines WHERE orderid='" + o.id + "'"), "3");
            check("ya no está pendiente", PurchaseOrderStore.openOrders(c).containsKey(o.ponumber), false);
            boolean twice = false;
            try {
                PurchaseOrderStore.receive(c, r, "MainStore", guid, null, "Prueba", "");
            } catch (SQLException ex) {
                twice = ex.getMessage().contains("ya fue recibido");
            }
            check("no se puede recibir dos veces", twice, true);
            check("stock sin cambios tras el intento", units(c, arroz) - sArroz, 7.0);

            Order back = PurchaseOrderStore.loadOrder(c, o.id);
            check("recibido guardado", line(back, arroz).received, 7.0);
            check("costo factura guardado", line(back, arroz).receivedCost, 22.0);
            File contra = new File(dir, "Contrarecibo_" + o.ponumber + ".pdf");
            ReceiptPdf.write(contra, back, "Tlapalería Prueba", "Prueba");
            check("contrarecibo", contra.length() > 1000, true);

            // ---- Faltante pasa al siguiente pedido ----
            Map<String, uk.chromis.pos.restock.PendingOrders.Carry> carry = PurchaseOrderStore.carryover(c);
            check("faltante de arroz", carry.get(arroz) == null ? null : carry.get(arroz).units, 3.0);
            check("de qué pedido", carry.get(arroz) == null ? null : carry.get(arroz).describe(), o.ponumber);
            check("bolsa sin faltante", carry.containsKey(bolsa), false);
            Order o2 = PurchaseOrderStore.createOrder(c, guid, gen, admin,
                    Arrays.asList(new PurchaseOrderStore.NewLine(arroz, 3)));
            second = o2.id;
            check("folio siguiente", o2.ponumber, String.format("PED-%06d", num + 1));
            check("faltante ya se pidió", PurchaseOrderStore.carryover(c).containsKey(arroz), false);
            check("ligado al nuevo pedido", one(c, "SELECT to_order FROM order_carryover WHERE from_order='" + o.id + "'"), o2.id);

            // ---- Autorización ----
            check("precio sugerido conserva margen", PurchaseOrderStore.suggestedPrice(20, 22, 30), 33.0);
            check("sin costo previo no sugiere", PurchaseOrderStore.suggestedPrice(0, 22, 30), 30.0);
            List<PurchaseOrderStore.PriceChange> pend = PurchaseOrderStore.pendingPriceChanges(c);
            check("1 pendiente", pend.size(), 1);
            PurchaseOrderStore.PriceChange pc = pend.get(0);
            check("producto", pc.productId, arroz);
            check("costos", pc.oldCost + " → " + pc.newCost, "20.0 → 22.0");
            check("sugerido", pc.suggestedPrice, 33.0);
            check("variación", Math.round(pc.variation()), 10L);
            check("autoriza", PurchaseOrderStore.approve(c, pc.id, 34.5, "Gerente"), true);
            check("no se autoriza dos veces", PurchaseOrderStore.approve(c, pc.id, 40.0, "Gerente"), false);
            check("costo nuevo", one(c, "SELECT pricebuy FROM products WHERE id='" + arroz + "'"), "22.00");
            check("venta nueva", one(c, "SELECT pricesell FROM products WHERE id='" + arroz + "'"), "34.50");
            check("quién autorizó", one(c, "SELECT CONCAT(status,'/',decided_by) FROM price_changes WHERE id=" + pc.id), "AUTORIZADO/Gerente");
            check("ya no hay pendientes", PurchaseOrderStore.countPendingPriceChanges(c), 0);

            // Solo costo, sin tocar la venta; y rechazo
            exec(c, "INSERT INTO price_changes (product, old_cost, new_cost, old_price, suggested_price, status) "
                    + "VALUES ('" + bolsa + "', 1, 1.5, 2, 3, 'PENDIENTE'), ('" + espiral + "', 2, 3, 5, 7.5, 'PENDIENTE')");
            pend = PurchaseOrderStore.pendingPriceChanges(c);
            String bolsaPrice = one(c, "SELECT pricesell FROM products WHERE id='" + bolsa + "'");
            String bolsaCost = one(c, "SELECT pricebuy FROM products WHERE id='" + bolsa + "'");
            String espiralCost = one(c, "SELECT pricebuy FROM products WHERE id='" + espiral + "'");
            check("solo costo", PurchaseOrderStore.approve(c, pend.get(0).id, null, "Gerente"), true);
            check("venta de bolsa intacta", one(c, "SELECT pricesell FROM products WHERE id='" + bolsa + "'"), bolsaPrice);
            check("costo de bolsa", one(c, "SELECT pricebuy FROM products WHERE id='" + bolsa + "'"), "1.50");
            check("rechazo", PurchaseOrderStore.reject(c, pend.get(1).id, "Gerente"), true);
            check("espiral intacto", one(c, "SELECT pricebuy FROM products WHERE id='" + espiral + "'"), espiralCost);
            exec(c, "UPDATE products SET pricebuy = " + bolsaCost + " WHERE id='" + bolsa + "'");

            // Un costo nuevo sustituye al pendiente anterior del mismo producto
            Order o3 = PurchaseOrderStore.loadOrder(c, o2.id);
            line(o3, arroz).receivedCost = 25; // pricebuy ya es 22
            exec(c, "INSERT INTO price_changes (product, old_cost, new_cost, old_price, suggested_price, status) "
                    + "VALUES ('" + arroz + "', 22, 24, 34.5, 37.64, 'PENDIENTE')");
            PurchaseOrderStore.receive(c, o3, "MainStore", guid, null, "Prueba", "");
            check("solo queda el último costo", PurchaseOrderStore.pendingPriceChanges(c).size(), 1);
            check("es el de 25", PurchaseOrderStore.pendingPriceChanges(c).get(0).newCost, 25.0);
            check("recibido completo", one(c, "SELECT status FROM supplierorders WHERE id='" + o2.id + "'"), PurchaseOrderStore.RECEIVED);
            second = null;
        } finally {
            exec(c, "UPDATE price_changes SET status='RECHAZADO' WHERE status='PENDIENTE'");
            if (second != null) exec(c, "UPDATE supplierorders SET status='Cancelled' WHERE id='" + second + "'");
            if (carryMax != null) {
                exec(c, "UPDATE order_carryover SET to_order='prueba' WHERE id > " + carryMax + " AND to_order IS NULL");
                // Shortages that were pending before the test stay pending
                exec(c, "UPDATE order_carryover SET to_order = NULL WHERE id IN (SELECT id FROM carry_before)");
            }
            exec(c, "UPDATE stockcurrent SET units=" + sArroz + " WHERE product='" + arroz + "' AND location='MainStore'");
            exec(c, "UPDATE stockcurrent SET units=" + sBolsa + " WHERE product='" + bolsa + "' AND location='MainStore'");
            exec(c, "UPDATE stockcurrent SET units=" + sEspiral + " WHERE product='" + espiral + "' AND location='MainStore'");
            exec(c, "UPDATE products SET pricebuy=" + costArroz + ", pricesell=" + priceArroz + " WHERE id='" + arroz + "'");
        }
        check("stock restaurado", units(c, arroz), sArroz);
        System.out.println("Movimientos de diario de la prueba: " + one(c, "SELECT COUNT(*) FROM stockdiary WHERE sequenceid > " + diary));
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
