import java.io.StringReader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import uk.chromis.pos.delivery.DeliveryStatusStore;
import uk.chromis.pos.delivery.DeliveryStatusStore.Change;
import uk.chromis.pos.delivery.DeliveryStatusStore.Entry;
import uk.chromis.pos.delivery.DeliveryStatusStore.Outcome;
import uk.chromis.pos.delivery.DeliveryStatusStore.Role;
import uk.chromis.pos.delivery.GeoStore;
import uk.chromis.pos.delivery.WebCustomerImport;
import uk.chromis.pos.delivery.ZoneStore;

/**
 * Prueba manual del gestor de entregas dentro de Chromis (ubicaciones,
 * cuadrantes, estados con confirmación del repartidor) y de la importación de
 * clientes de la web, contra una base Chromis 1.5.4 con una caja abierta y los
 * usuarios Administrator, Manager y Employee. Crea ventas de entrega de prueba
 * (Chromis no deja borrar ventas: quedan registradas) y al terminar borra sus
 * entregas, cuadrantes, ubicaciones y los clientes de prueba que pueda.
 *
 *   java -cp dist/admin.jar tests/DeliveryManagerTest.java [url usuario contraseña]
 */
public class DeliveryManagerTest {
    static int fails = 0;
    static String url, dbUser, dbPass;
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
    static Connection connect() throws SQLException { return DriverManager.getConnection(url, dbUser, dbPass); }

    static String guid, money, admin, manager, employee;
    static List<String> customers = new ArrayList<>(), sales = new ArrayList<>();
    static int nextTicket;

    static String customer(Connection c, String name) throws SQLException {
        String id = "prueba-cli-" + UUID.randomUUID().toString().substring(0, 8);
        exec(c, "INSERT INTO customers (id, taxid, name, siteguid) VALUES ('" + id + "', '" + id + "', '" + name + "', '" + guid + "')");
        customers.add(id);
        return id;
    }
    static String sale(Connection c, String customer, double total) throws SQLException {
        String id = UUID.randomUUID().toString();
        exec(c, "INSERT INTO receipts (id, money, datenew, person) VALUES ('" + id + "', '" + money + "', NOW(), '" + admin + "')");
        exec(c, "INSERT INTO tickets (id, tickettype, ticketid, person, customer) VALUES ('" + id + "', 0, " + (nextTicket++) + ", '"
                + admin + "', " + (customer == null ? "NULL" : "'" + customer + "'") + ")");
        exec(c, "INSERT INTO payments (id, receipt, payment, total) VALUES ('" + UUID.randomUUID() + "', '" + id + "', 'cash', " + total + ")");
        exec(c, "INSERT INTO customer_delivery (id, name, addressline1, addressline3, phone, deliverydate, delivered, comments) "
                + "VALUES ('" + id + "', 'Recibe', 'Calle 1', 'Ciudad', '555', NOW(), 0, 'Portón azul')");
        sales.add(id);
        return id;
    }
    static Change change(String ticket, String status, Role role, String user) {
        Change ch = new Change();
        ch.opId = UUID.randomUUID().toString();
        ch.ticketId = ticket; ch.status = status; ch.role = role; ch.userId = user; ch.userName = role + "-" + user.substring(0, 4);
        return ch;
    }
    static String apply(Connection c, Change ch) throws SQLException {
        try {
            return DeliveryStatusStore.apply(c, ch).name();
        } catch (DeliveryStatusStore.Refused r) {
            return "REFUSED: " + r.getMessage();
        }
    }
    static Entry entry(Connection c, String t) throws SQLException { return DeliveryStatusStore.get(c, t); }
    static String events(Connection c, String t) throws SQLException { return one(c, "SELECT COUNT(*) FROM delivery_events WHERE ticket = '" + t + "'"); }
    static String delivered(Connection c, String t) throws SQLException { return one(c, "SELECT delivered + 0 FROM customer_delivery WHERE id = '" + t + "'"); }

    public static void main(String[] a) throws Exception {
        url = a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos";
        dbUser = a.length > 1 ? a[1] : "chromis";
        dbPass = a.length > 2 ? a[2] : "chromis";
        Connection c = connect();
        guid = one(c, "SELECT guid FROM siteguid");
        money = one(c, "SELECT money FROM closedcash ORDER BY datestart DESC LIMIT 1");
        admin = one(c, "SELECT id FROM people WHERE name = 'Administrator'");
        manager = one(c, "SELECT id FROM people WHERE name = 'Manager'");
        employee = one(c, "SELECT id FROM people WHERE name = 'Employee'");
        nextTicket = Integer.parseInt(one(c, "SELECT GREATEST(COALESCE(MAX(ticketid), 0), 995000) FROM tickets")) + 1;
        DeliveryStatusStore.createTables(c);
        DeliveryStatusStore.createTables(c); // dos veces no falla
        List<Integer> zones = new ArrayList<>();
        try {
            // ---------- Ubicaciones ----------
            check("coordenadas válidas", GeoStore.validate(19.4, -99.0), null);
            check("sin ubicación es válido", GeoStore.validate(null, null), null);
            check("solo latitud", GeoStore.validate(19.4, null) != null, true);
            check("0,0 no es ubicación", GeoStore.validate(0.0, 0.0) != null, true);
            check("fuera de rango", GeoStore.validate(95.0, 10.0) != null, true);
            String norte = customer(c, "Papelería Prueba Norte");
            String sinPin = customer(c, "Papelería Prueba Sin Pin");
            GeoStore.save(c, norte, 19.55, -99.05, GeoStore.CAPTIVE, GeoStore.SOURCE_ADMIN, "Administrator");
            GeoStore.Geo g = GeoStore.get(c, norte);
            check("ubicación guardada", g.lat + "," + g.lng + " " + g.source, "19.55,-99.05 admin");
            GeoStore.save(c, norte, 19.551, -99.051, GeoStore.CAPTIVE, GeoStore.SOURCE_APP, "Employee");
            check("ubicación actualizada", GeoStore.get(c, norte).lat, 19.551);
            try { GeoStore.save(c, norte, 0.0, 0.0, null, GeoStore.SOURCE_APP, "x"); check("0,0 rechazado", false, true); }
            catch (SQLException ex) { check("0,0 rechazado", true, true); }

            // ---------- Cuadrantes ----------
            List<double[]> ele = List.of(new double[]{0, 0}, new double[]{0, 3}, new double[]{1, 3}, new double[]{1, 1}, new double[]{3, 1}, new double[]{3, 0});
            check("dentro de la L", ZoneStore.contains(ele, 0.5, 2.5), true);
            check("en el hueco de la L", ZoneStore.contains(ele, 2, 2), false);
            List<double[]> poly = List.of(new double[]{19.5, -99.1}, new double[]{19.6, -99.1}, new double[]{19.6, -99.0}, new double[]{19.5, -99.0});
            check("codificar y leer", ZoneStore.encode(ZoneStore.decode(ZoneStore.encode(poly))), ZoneStore.encode(poly));
            ZoneStore.Zone z = new ZoneStore.Zone();
            z.name = "Prueba Norte"; z.color = "#e63946"; z.polygon = new ArrayList<>(poly); z.driver = employee; z.dispatcher = manager;
            zones.add(ZoneStore.save(c, z));
            ZoneStore.Zone mala = new ZoneStore.Zone();
            mala.name = "Mala"; mala.polygon = List.of(new double[]{1, 1}, new double[]{2, 2});
            try { ZoneStore.save(c, mala); check("cuadrante de 2 puntos rechazado", false, true); }
            catch (SQLException ex) { check("cuadrante de 2 puntos rechazado", true, true); }
            ZoneStore.Zone leida = ZoneStore.load(c, false).stream().filter(x -> x.id == z.id).findFirst().get();
            check("color normalizado", leida.color, "#E63946");
            check("4 esquinas", leida.polygon.size(), 4);

            // ---------- Entregas ----------
            String s1 = sale(c, norte, 360);
            String s2 = sale(c, sinPin, 50);
            Entry e1 = entry(c, s1), e2 = entry(c, s2);
            check("estado inicial", e1.status, DeliveryStatusStore.PENDING);
            check("cuadrante por la ubicación del cliente", e1.zone == null ? null : e1.zone.name, "Prueba Norte");
            check("repartidor del cuadrante", employee.equals(e1.driver()), true);
            check("total de la venta", e1.total, 360.0);
            check("dirección", e1.address, "Calle 1, Ciudad");
            check("sin pin: sin cuadrante", e2.zone, null);
            check("aparece en la lista", DeliveryStatusStore.load(c, 0).stream().anyMatch(x -> x.ticketId.equals(s1)), true);
            check("repartidor no puede preparar", DeliveryStatusStore.allowed(Role.DRIVER, employee, e1), List.of());
            check("despachador del cuadrante", DeliveryStatusStore.allowed(Role.DISPATCHER, manager, e1), List.of("preparado", "cancelado"));
            check("otro despachador", DeliveryStatusStore.allowed(Role.DISPATCHER, admin, e1), List.of());

            Change prep = change(s1, "preparado", Role.DISPATCHER, manager);
            check("preparar", apply(c, prep), "APPLIED");
            check("reenviar la misma operación", apply(c, prep), "ALREADY_APPLIED");
            check("un solo movimiento en el historial", events(c, s1), "1");
            check("repartidor no da salida", apply(c, change(s1, "en_ruta", Role.DRIVER, employee)).startsWith("REFUSED"), true);
            check("salida a ruta", apply(c, change(s1, "en_ruta", Role.DISPATCHER, manager)), "APPLIED");
            check("otro repartidor no la toca", apply(c, change(s1, "completado", Role.DRIVER, manager)), "REFUSED: Esta entrega no es tuya.");

            Change sinMonto = change(s1, "completado", Role.DRIVER, employee);
            check("completar sin monto", apply(c, sinMonto).contains("cuánto se recibió"), true);
            check("nada a medias: sigue en ruta", entry(c, s1).status, "en_ruta");
            check("nada a medias: sin movimiento", events(c, s1), "2");
            check("nada a medias: no entregada en Chromis", delivered(c, s1), "0");

            Change done = change(s1, "completado", Role.DRIVER, employee);
            done.amountReceived = 300.0; done.issue = "faltante";
            check("incidencia sin anotación", apply(c, done).contains("Describe la incidencia"), true);
            done.notes = "Faltaron 2 cajas de lápices";
            check("completar con monto e incidencia", apply(c, done), "APPLIED");
            Entry hecho = entry(c, s1);
            check("estado completada", hecho.status, "completado");
            check("monto recibido", hecho.amountReceived, 300.0);
            check("incidencia", hecho.issue + " / " + hecho.notes, "faltante / Faltaron 2 cajas de lápices");
            check("quién confirmó", hecho.confirmedBy != null && hecho.confirmedAt != null, true);
            check("entregada en Chromis", delivered(c, s1), "1");
            check("el celular reenvía la confirmación", apply(c, done), "ALREADY_APPLIED");
            check("sigue un solo cierre", events(c, s1), "3");
            check("cerrada no sale en la lista", DeliveryStatusStore.load(c, 0).stream().anyMatch(x -> x.ticketId.equals(s1)), false);
            check("con días cerrados sí", DeliveryStatusStore.load(c, 1).stream().anyMatch(x -> x.ticketId.equals(s1)), true);
            check("despachador no reabre", apply(c, change(s1, "en_ruta", Role.DISPATCHER, manager)).startsWith("REFUSED"), true);
            check("admin reabre", apply(c, change(s1, "en_ruta", Role.ADMIN, admin)), "APPLIED");
            check("reabrir quita la entrega en Chromis", delivered(c, s1), "0");
            check("reabrir quita el monto", entry(c, s1).amountReceived, null);

            Change fallo = change(s1, "no_entregado", Role.DRIVER, employee);
            check("no entregada sin motivo", apply(c, fallo).contains("por qué"), true);
            fallo.notes = "Cerrado";
            check("no entregada", apply(c, fallo), "APPLIED");
            check("repartidor no reprograma", apply(c, change(s1, "confirmado", Role.DRIVER, employee)).startsWith("REFUSED"), true);
            check("reprogramar", apply(c, change(s1, "confirmado", Role.DISPATCHER, manager)), "APPLIED");
            check("historial completo", DeliveryStatusStore.history(c, s1).size(), 6);

            // Repartidor para una entrega sin cuadrante
            DeliveryStatusStore.setDriver(c, s2, employee, "Manager");
            check("repartidor asignado a mano", employee.equals(entry(c, s2).driver()), true);
            check("cambiarlo no cambia el estado", entry(c, s2).status, "confirmado");

            // Entregada a mano en Chromis (Admin → Entregas) sin estado: cuenta como completada
            exec(c, "UPDATE customer_delivery SET delivered = 1 WHERE id = '" + s2 + "'");
            check("entregada en Chromis = completada", entry(c, s2).status, "completado");
            exec(c, "UPDATE customer_delivery SET delivered = 0 WHERE id = '" + s2 + "'");

            // Dos envíos simultáneos de la misma operación (dos conexiones)
            apply(c, change(s2, "preparado", Role.ADMIN, admin));
            apply(c, change(s2, "en_ruta", Role.ADMIN, admin));
            Change doble = change(s2, "completado", Role.DRIVER, employee);
            doble.amountReceived = 50.0;
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<String>> fut = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                fut.add(pool.submit(() -> { try (Connection k = connect()) { return apply(k, doble); } }));
            }
            List<String> res = new ArrayList<>();
            for (Future<String> f : fut) res.add(f.get());
            Collections.sort(res);
            check("misma operación al mismo tiempo: se aplica una vez", res, List.of("ALREADY_APPLIED", "APPLIED"));
            check("un solo cierre en el historial", one(c, "SELECT COUNT(*) FROM delivery_events WHERE ticket = '" + s2 + "' AND to_status = 'completado'"), "1");
            // Dos operaciones distintas que chocan: la segunda ya no es válida
            apply(c, change(s2, "en_ruta", Role.ADMIN, admin));
            Change ca = change(s2, "completado", Role.DRIVER, employee); ca.amountReceived = 50.0;
            Change cb = change(s2, "no_entregado", Role.DRIVER, employee); cb.notes = "Cerrado";
            fut.clear();
            for (Change ch : List.of(ca, cb)) fut.add(pool.submit(() -> { try (Connection k = connect()) { return apply(k, ch); } }));
            res.clear();
            for (Future<String> f : fut) res.add(f.get());
            pool.shutdown();
            check("dos cierres distintos a la vez: uno gana, el otro se rechaza",
                    res.stream().filter("APPLIED"::equals).count() == 1 && res.stream().anyMatch(x -> x.startsWith("REFUSED")), true);
            Change sinId = change(s2, "preparado", Role.ADMIN, admin); sinId.opId = null;
            check("sin identificador de operación", apply(c, sinId).contains("identificador"), true);

            // ---------- Importar clientes de la web ----------
            String yaExiste = customer(c, "Papelería Prueba Ya Existe");
            String dup1 = customer(c, "Papelería Prueba Repetida");
            String dup2 = customer(c, "Papelería Prueba Repetida");
            String run = UUID.randomUUID().toString().substring(0, 6);
            String csv = "﻿id_cliente,nombre_comercial,telefono,direccion_completa,latitud,longitud,estado_logistico,estado_secundario,notas_internas,asignado_a\r\n"
                    + "w" + run + "1,\"Papelería Prueba \"\"La Nueva\"\"\",5550001,\"Av. Juárez 1, Col. Centro\",19.401,-98.99,nuevo,ninguno,\"Abre a las 9\nCierra a las 6\",rep@x.com\r\n"
                    + "w" + run + "2,papelería prueba ya existe ,5550002,Calle 2,19.402,-98.98,cautivo,pedido_activo,,\r\n"
                    + "w" + run + "3,Papelería Prueba Sin Pin Web,5550003,Calle 3,,,pendiente_visita,ninguno,,\r\n"
                    + "w" + run + "4,,5550004,Calle 4,19.4,-98.9,cautivo,ninguno,,\r\n"
                    + "w" + run + "5,Papelería Prueba Repetida,5550005,Calle 5,19.4,-98.9,cautivo,ninguno,,\r\n"
                    + "w" + run + "6,Papelería Prueba Coordenadas Malas,5550006,Calle 6,0,0,cautivo,ninguno,,\r\n";
            String antes = one(c, "SELECT COUNT(*) FROM customers");
            WebCustomerImport.Result pre = WebCustomerImport.run(c, new StringReader(csv), guid, "Administrator", true);
            check("vista previa: números", pre.created + " nuevos, " + pre.linked + " enlazados, " + pre.skipped.size() + " omitidas", "3 nuevos, 1 enlazados, 2 omitidas");
            check("vista previa no guarda nada", one(c, "SELECT COUNT(*) FROM customers"), antes);
            WebCustomerImport.Result r1 = WebCustomerImport.run(c, new StringReader(csv), guid, "Administrator", false);
            check("importar", r1.describe(), pre.describe());
            String nueva = one(c, "SELECT id FROM customers WHERE taxid = 'WEB-w" + run + "1'");
            if (nueva != null) customers.add(nueva);
            for (String t : new String[]{"3", "6"}) { String id = one(c, "SELECT id FROM customers WHERE taxid = 'WEB-w" + run + t + "'"); if (id != null) customers.add(id); }
            check("nombre con comillas", one(c, "SELECT name FROM customers WHERE id = '" + nueva + "'"), "Papelería Prueba \"La Nueva\"");
            check("notas de varias líneas", one(c, "SELECT notes FROM customers WHERE id = '" + nueva + "'"), "Abre a las 9\nCierra a las 6");
            check("dirección con coma", one(c, "SELECT address FROM customers WHERE id = '" + nueva + "'"), "Av. Juárez 1, Col. Centro");
            GeoStore.Geo gn = GeoStore.get(c, nueva);
            check("pin importado", gn.lat + "," + gn.lng + " " + gn.source + " " + gn.status, "19.401,-98.99 web prospecto");
            check("enlazado al cliente existente (sin duplicar)", GeoStore.get(c, yaExiste).lat, 19.402);
            check("sin pin: se crea sin ubicación", GeoStore.get(c, one(c, "SELECT id FROM customers WHERE taxid = 'WEB-w" + run + "3'")).located(), false);
            check("0,0 se importa sin ubicación", GeoStore.get(c, one(c, "SELECT id FROM customers WHERE taxid = 'WEB-w" + run + "6'")).located(), false);
            check("repetidos no se tocan", GeoStore.get(c, dup1) == null && GeoStore.get(c, dup2) == null, true);
            check("motivos de lo omitido", String.join(" | ", r1.skipped).contains("sin nombre") && String.join(" | ", r1.skipped).contains("hay 2 clientes"), true);

            // Pin movido en Chromis: la web ya no lo pisa
            GeoStore.save(c, nueva, 19.41, -98.95, GeoStore.CAPTIVE, GeoStore.SOURCE_ADMIN, "Administrator");
            String despues = one(c, "SELECT COUNT(*) FROM customers");
            WebCustomerImport.Result r2 = WebCustomerImport.run(c, new StringReader(csv), guid, "Administrator", false);
            check("reimportar no duplica", one(c, "SELECT COUNT(*) FROM customers"), despues);
            check("reimportar: ya importados", r2.updated, 3);
            check("pin movido en Chromis se respeta", GeoStore.get(c, nueva).lat, 19.41);

            try {
                WebCustomerImport.run(c, new StringReader("nombre,telefono\nA,1\n"), guid, "Administrator", false);
                check("archivo sin columnas de la web", false, true);
            } catch (SQLException ex) {
                check("archivo sin columnas de la web", ex.getMessage().contains("id_cliente"), true);
            }
        } finally {
            for (String s : sales) {
                exec(c, "DELETE FROM delivery_events WHERE ticket = '" + s + "'");
                exec(c, "DELETE FROM delivery_status WHERE ticket = '" + s + "'");
                exec(c, "DELETE FROM customer_delivery WHERE id = '" + s + "'");
            }
            for (int id : zones) exec(c, "DELETE FROM delivery_zones WHERE id = " + id);
            for (String cu : customers) {
                exec(c, "DELETE FROM customer_geo WHERE customer = '" + cu + "'");
                try { exec(c, "DELETE FROM customers WHERE id = '" + cu + "' AND id NOT IN (SELECT customer FROM tickets WHERE customer IS NOT NULL)"); }
                catch (SQLException ex) { /* Chromis puede impedir borrar clientes */ }
            }
        }
        System.out.println(fails == 0 ? "\nTODO BIEN" : "\nFALLAS: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
