import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.json.*;
import uk.chromis.administration.delivery.MapServer;
import uk.chromis.pos.delivery.DeliveryStatusStore;
import uk.chromis.pos.delivery.DeliveryStatusStore.Role;
import uk.chromis.pos.delivery.GeoStore;
import uk.chromis.pos.settings.PosSettings;

/**
 * Prueba del servidor local del mapa de entregas (Admin → Gestor de entregas):
 * solo responde con la clave de la sesión y dirigido a 127.0.0.1, el
 * despachador no dibuja cuadrantes, y lo que se guarda llega a la base.
 *
 *   java -cp dist/admin.jar tests/MapServerTest.java [url usuario contraseña] [--esperar]
 *
 * Con --esperar deja el servidor abierto con datos de prueba (para probar la
 * página en un navegador), escribe la dirección en mapa-url.txt y espera a
 * que se borre ese archivo para limpiar.
 */
public class MapServerTest {
    static int fails = 0;
    static void check(String what, Object got, Object exp) {
        boolean ok = Objects.equals(got, exp);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    static String one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { return rs.next() ? rs.getString(1) : null; }
    }
    static void exec(Connection c, String sql) throws SQLException { try (Statement st = c.createStatement()) { st.executeUpdate(sql); } }

    static final HttpClient http = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
    static String base, key;
    static HttpResponse<String> get(String path, String clave) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (clave != null) b.header("X-Clave", clave);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    static HttpResponse<String> post(String path, String clave, JSONObject body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Content-Type", "application/json");
        if (clave != null) b.header("X-Clave", clave);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
    static String raw(int port, String host, String path) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\nX-Clave: " + key + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new BufferedReader(new InputStreamReader(s.getInputStream())).readLine();
        }
    }

    public static void main(String[] a) throws Exception {
        List<String> args = new ArrayList<>(Arrays.asList(a));
        boolean wait = args.remove("--esperar");
        String url = args.size() > 0 ? args.get(0) : "jdbc:mysql://localhost:3306/chromispos";
        String user = args.size() > 1 ? args.get(1) : "chromis";
        String pass = args.size() > 2 ? args.get(2) : "chromis";
        Connection c = DriverManager.getConnection(url, user, pass);
        String guid = one(c, "SELECT guid FROM siteguid");
        String money = one(c, "SELECT money FROM closedcash ORDER BY datestart DESC LIMIT 1");
        String admin = one(c, "SELECT id FROM people WHERE name = 'Administrator'");
        String manager = one(c, "SELECT id FROM people WHERE name = 'Manager'");
        String employee = one(c, "SELECT id FROM people WHERE name = 'Employee'");
        DeliveryStatusStore.createTables(c);
        PosSettings.createTable(c);
        String oldKey = PosSettings.get(c, "maps.apikey", null);
        PosSettings.put(c, "maps.apikey", "CLAVE-DE-PRUEBA");
        int port = 47999;
        List<String> customers = new ArrayList<>(), sales = new ArrayList<>();
        int lastZone = Integer.parseInt(one(c, "SELECT COALESCE(MAX(id), 0) FROM delivery_zones"));
        try {
            // Datos: dos clientes (uno sin ubicar) con una entrega cada uno
            String run = UUID.randomUUID().toString().substring(0, 6);
            String[] names = {"Papelería Mapa Centro " + run, "Papelería Mapa Sin Pin " + run};
            int ticket = Integer.parseInt(one(c, "SELECT GREATEST(COALESCE(MAX(ticketid), 0), 996000) FROM tickets")) + 1;
            for (String n : names) {
                String id = UUID.randomUUID().toString();
                exec(c, "INSERT INTO customers (id, taxid, name, address, siteguid) VALUES ('" + id + "', 'MAPA-" + id.substring(0, 8) + "', '" + n + "', 'Calle Prueba 1', '" + guid + "')");
                customers.add(id);
                String s = UUID.randomUUID().toString();
                exec(c, "INSERT INTO receipts (id, money, datenew, person) VALUES ('" + s + "', '" + money + "', NOW(), '" + admin + "')");
                exec(c, "INSERT INTO tickets (id, tickettype, ticketid, person, customer) VALUES ('" + s + "', 0, " + (ticket++) + ", '" + admin + "', '" + id + "')");
                exec(c, "INSERT INTO payments (id, receipt, payment, total) VALUES ('" + UUID.randomUUID() + "', '" + s + "', 'cash', 120)");
                exec(c, "INSERT INTO customer_delivery (id, name, addressline1, deliverydate, delivered, comments) VALUES ('" + s + "', 'Recibe', 'Calle Prueba 1', NOW(), 0, 'Tocar fuerte')");
                sales.add(s);
            }
            GeoStore.save(c, customers.get(0), 19.43, -99.13, GeoStore.CAPTIVE, GeoStore.SOURCE_ADMIN, "prueba");

            int[] changes = {0};
            String address = MapServer.start(url, user, pass, new MapServer.User(manager, "Manager", Role.DISPATCHER), () -> changes[0]++, port);
            base = "http://127.0.0.1:" + port;
            key = address.substring(address.indexOf("k=") + 2);
            check("dirección local", address.startsWith("http://127.0.0.1:" + port + "/?k="), true);
            check("clave larga", key.length(), 48);

            // ---------- Seguridad ----------
            check("página sin clave", get("/", null).statusCode(), 403);
            check("página con otra clave", get("/?k=otra", null).statusCode(), 403);
            HttpResponse<String> page = get("/?k=" + key, null);
            check("página con la clave", page.statusCode(), 200);
            check("la página trae la clave de la sesión", page.body().contains("'" + key + "'") && !page.body().contains("__CLAVE__"), true);
            check("datos sin clave", get("/api/datos", null).statusCode(), 403);
            check("datos con otra clave", get("/api/datos", "x").statusCode(), 403);
            check("otra dirección (otra página web)", raw(port, "evil.example:" + port, "/api/datos"), "HTTP/1.1 403 Error");
            check("localhost sí", raw(port, "localhost:" + port, "/api/datos"), "HTTP/1.1 200 OK");
            check("ruta que no existe", get("/api/nada", key).statusCode(), 400);
            boolean outside;
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(InetAddress.getLocalHost(), port), 1000);
                outside = !InetAddress.getLocalHost().isLoopbackAddress();
            } catch (IOException ex) {
                outside = false;
            }
            check("no se puede entrar desde la red", outside, false);

            // ---------- Datos ----------
            JSONObject d = new JSONObject(get("/api/datos", key).body());
            check("papel", d.getString("rol"), "dispatcher");
            check("clave de Google", d.getString("clave_mapa"), "CLAVE-DE-PRUEBA");
            JSONObject e0 = find(d.getJSONArray("entregas"), "ticket", sales.get(0));
            check("entrega con ubicación", e0 != null && e0.getDouble("lat") == 19.43, true);
            check("indicaciones", e0.getString("indicaciones"), "Tocar fuerte");
            check("entrega sin ubicación", find(d.getJSONArray("entregas"), "ticket", sales.get(1)).isNull("lat"), true);
            check("cliente sin ubicar en la lista", find(d.getJSONArray("clientes"), "id", customers.get(1)).isNull("lat"), true);

            // ---------- Permisos ----------
            JSONObject zone = new JSONObject().put("nombre", "Prueba Mapa").put("color", "#2a9d8f")
                    .put("poligono", new JSONArray("[[19.42,-99.14],[19.44,-99.14],[19.44,-99.12],[19.42,-99.12]]"));
            HttpResponse<String> r = post("/api/cuadrante", key, zone);
            check("despachador no dibuja cuadrantes", r.statusCode() + " " + new JSONObject(r.body()).getString("error"), "400 Solo el administrador dibuja y cambia cuadrantes.");
            r = post("/api/ubicacion", key, new JSONObject().put("cliente", customers.get(1)).put("lat", 19.435).put("lng", -99.125).put("estado", "prospecto"));
            check("despachador ubica un cliente", r.statusCode(), 200);
            GeoStore.Geo g = GeoStore.get(c, customers.get(1));
            check("ubicación guardada", g.lat + "," + g.lng + " " + g.status + " " + g.source + " " + g.updatedBy, "19.435,-99.125 prospecto admin Manager");
            check("Admin se entera del cambio", changes[0], 1);
            r = post("/api/ubicacion", key, new JSONObject().put("cliente", customers.get(1)).put("lat", 0).put("lng", 0));
            check("0,0 rechazado", r.statusCode() + " " + new JSONObject(r.body()).getString("error"), "400 Las coordenadas 0, 0 no son una ubicación real.");
            r = post("/api/ubicacion", key, new JSONObject().put("cliente", "no-existe").put("lat", 19.4).put("lng", -99.1));
            check("cliente inexistente", r.statusCode(), 400);
            r = post("/api/ubicacion", key, new JSONObject().put("cliente", customers.get(1)).put("lat", 19.4).put("lng", -99.1).put("estado", "raro"));
            check("estado inválido", r.statusCode(), 400);
            r = post("/api/ubicacion", key, new JSONObject().put("cliente", customers.get(1)).put("estado", "cautivo"));
            check("sin coordenadas no borra la ubicación", r.statusCode(), 400);
            check("lo rechazado no cambió nada", GeoStore.get(c, customers.get(1)).lat, 19.435);

            // ---------- Administrador ----------
            String adminAddress = MapServer.start(url, user, pass, new MapServer.User(admin, "Administrator", Role.ADMIN), () -> changes[0]++, port);
            check("otro usuario: otra clave", adminAddress.equals(address), false);
            check("la clave anterior ya no sirve", get("/api/datos", key).statusCode(), 403);
            key = adminAddress.substring(adminAddress.indexOf("k=") + 2);
            check("papel admin", new JSONObject(get("/api/datos", key).body()).getString("rol"), "admin");
            r = post("/api/cuadrante", key, new JSONObject().put("nombre", "Mala").put("poligono", new JSONArray("[[1,1],[2,2]]")));
            check("cuadrante de 2 esquinas", r.statusCode() + " " + new JSONObject(r.body()).getString("error"), "400 El cuadrante necesita al menos 3 esquinas.");
            r = post("/api/cuadrante", key, zone);
            check("admin dibuja", r.statusCode(), 200);
            d = new JSONObject(r.body());
            JSONObject z = find(d.getJSONArray("cuadrantes"), "nombre", "Prueba Mapa");
            check("cuadrante guardado", z != null && z.getJSONArray("poligono").length() == 4 && z.getString("color").equals("#2A9D8F"), true);
            check("las entregas dentro quedan en el cuadrante", find(d.getJSONArray("entregas"), "ticket", sales.get(0)).getString("cuadrante")
                    + " / " + find(d.getJSONArray("entregas"), "ticket", sales.get(1)).getString("cuadrante"), "Prueba Mapa / Prueba Mapa");
            int zid = z.getInt("id");
            r = post("/api/cuadrante", key, new JSONObject().put("id", zid).put("nombre", "Prueba Mapa Norte")
                    .put("poligono", new JSONArray("[[19.432,-99.14],[19.44,-99.14],[19.44,-99.12],[19.432,-99.12]]")));
            d = new JSONObject(r.body());
            check("cambiar forma y nombre", find(d.getJSONArray("cuadrantes"), "id", zid).getString("nombre"), "Prueba Mapa Norte");
            check("la que quedó fuera sale del cuadrante", find(d.getJSONArray("entregas"), "ticket", sales.get(0)).getString("cuadrante"), "");
            exec(c, "UPDATE delivery_zones SET driver = '" + employee + "' WHERE id = " + zid);
            d = new JSONObject(get("/api/datos", key).body());
            check("repartidor del cuadrante", find(d.getJSONArray("entregas"), "ticket", sales.get(1)).getString("repartidor"), "Employee");

            if (wait) {
                File f = new File("mapa-url.txt");
                try (Writer w = new FileWriter(f)) { w.write(adminAddress + "\n" + run); }
                System.out.println("Servidor abierto: " + adminAddress + " (borra mapa-url.txt para terminar)");
                while (f.exists()) Thread.sleep(500);
                d = new JSONObject(get("/api/datos", key).body());
                System.out.println("DATOS-AL-TERMINAR " + d.toString());
            }

            r = post("/api/cuadrante/desactivar", key, new JSONObject().put("id", zid));
            check("quitar cuadrante", find(new JSONObject(r.body()).getJSONArray("cuadrantes"), "id", zid), null);
            check("se conserva desactivado", one(c, "SELECT active FROM delivery_zones WHERE id = " + zid), "0");
            MapServer.stop();
            boolean closed;
            try (Socket s = new Socket("127.0.0.1", port)) { closed = false; } catch (IOException ex) { closed = true; }
            check("al cerrar, el puerto se libera", closed, true);
        } finally {
            MapServer.stop();
            for (String s : sales) {
                exec(c, "DELETE FROM delivery_events WHERE ticket = '" + s + "'");
                exec(c, "DELETE FROM delivery_status WHERE ticket = '" + s + "'");
                exec(c, "DELETE FROM customer_delivery WHERE id = '" + s + "'");
            }
            exec(c, "DELETE FROM delivery_zones WHERE id > " + lastZone);
            for (String cu : customers) {
                exec(c, "DELETE FROM customer_geo WHERE customer = '" + cu + "'");
                // Chromis no deja borrar clientes con ventas: se desactivan
                exec(c, "UPDATE customers SET active = 0 WHERE id = '" + cu + "'");
            }
            if (oldKey == null) exec(c, "DELETE FROM pos_settings WHERE name = 'maps.apikey'");
            else PosSettings.put(c, "maps.apikey", oldKey);
        }
        System.out.println(fails == 0 ? "\nTODO BIEN" : "\nFALLAS: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }

    static JSONObject find(JSONArray a, String field, Object value) {
        for (int i = 0; i < a.length(); i++) {
            Object v = a.getJSONObject(i).opt(field);
            if (v != null && String.valueOf(v).equals(String.valueOf(value))) return a.getJSONObject(i);
        }
        return null;
    }
}
