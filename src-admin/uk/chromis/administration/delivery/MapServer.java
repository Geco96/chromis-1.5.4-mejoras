package uk.chromis.administration.delivery;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import uk.chromis.pos.delivery.DeliveryStatusStore;
import uk.chromis.pos.delivery.GeoStore;
import uk.chromis.pos.delivery.ZoneStore;
import uk.chromis.pos.settings.PosSettings;

/**
 * Small web server for the delivery map, only on this PC (127.0.0.1): Chromis
 * Admin opens the map in the PC's browser, where Google Maps works fully,
 * instead of inside Admin. Each Admin session has its own random key; without
 * it nothing is answered, and requests that do not come addressed to
 * 127.0.0.1 / localhost are refused (another web page cannot use it).
 *
 * <p>
 * The map shows deliveries, zones and customers; the admin draws zones, the
 * office places and moves customer pins. Everything is saved in the Chromis
 * database with its own connection.</p>
 */
public final class MapServer {

    private static final Logger LOGGER = Logger.getLogger(MapServer.class.getName());
    public static final int DEFAULT_PORT = 47123;
    private static final int MAX_BODY = 1 << 20;

    /** Who uses the map: name and id of the Admin user and its role. */
    public static final class User {

        public final String id;
        public final String name;
        public final DeliveryStatusStore.Role role;

        public User(String id, String name, DeliveryStatusStore.Role role) {
            this.id = id;
            this.name = name;
            this.role = role;
        }
    }

    private static MapServer running;

    private final String url;
    private final String dbUser;
    private final String dbPassword;
    private final User user;
    private final Runnable onChange;
    private final String token;
    private final ServerSocket socket;
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "mapa-entregas");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;

    private MapServer(String url, String dbUser, String dbPassword, User user, Runnable onChange, int port) throws IOException {
        this.url = url;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        this.user = user;
        this.onChange = onChange;
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        this.token = sb.toString();
        this.socket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
        Thread t = new Thread(this::acceptLoop, "mapa-entregas-escucha");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Starts the server (or reuses the one already running for the same user)
     * and returns the address to open in the browser.
     */
    public static synchronized String start(String url, String dbUser, String dbPassword, User user, Runnable onChange, int port)
            throws IOException {
        if (running != null && running.socket.getLocalPort() == port && running.user.id.equals(user.id)
                && running.user.role == user.role) {
            return running.address();
        }
        stop();
        try {
            running = new MapServer(url, dbUser, dbPassword, user, onChange, port);
        } catch (java.net.BindException ex) {
            throw new IOException("El puerto " + port + " de esta PC está ocupado. ¿Hay otro Chromis Admin abierto? "
                    + "Ciérralo o cambia el puerto del mapa.", ex);
        }
        return running.address();
    }

    public static synchronized void stop() {
        if (running != null) {
            try {
                running.socket.close();
            } catch (IOException ex) {
                // closing anyway
            }
            running.pool.shutdownNow();
            running = null;
        }
    }

    public String address() {
        return "http://127.0.0.1:" + socket.getLocalPort() + "/?k=" + token;
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try {
                Socket s = socket.accept();
                pool.execute(() -> handle(s));
            } catch (IOException ex) {
                if (!socket.isClosed()) {
                    LOGGER.log(Level.WARNING, "Mapa de entregas", ex);
                }
            }
        }
    }

    // ---------------- HTTP ----------------

    private static final class Request {

        String method;
        String path;
        final Map<String, String> query = new LinkedHashMap<>();
        final Map<String, String> headers = new LinkedHashMap<>();
        String body = "";
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(15000);
            InputStream in = new BufferedInputStream(sock.getInputStream());
            OutputStream out = sock.getOutputStream();
            Request r = read(in);
            if (r == null) {
                return;
            }
            String host = r.headers.getOrDefault("host", "");
            int port = socket.getLocalPort();
            if (!host.equals("127.0.0.1:" + port) && !host.equals("localhost:" + port)) {
                send(out, 403, "text/plain", "Solo desde esta PC.");
                return;
            }
            if (r.path.equals("/")) {
                if (!token.equals(r.query.get("k"))) {
                    send(out, 403, "text/plain; charset=utf-8", "Esta dirección ya no es válida: abre el mapa otra vez desde Chromis Admin.");
                    return;
                }
                send(out, 200, "text/html; charset=utf-8", page());
                return;
            }
            if (!r.path.startsWith("/api/")) {
                send(out, 404, "text/plain", "No existe.");
                return;
            }
            if (!token.equals(r.headers.get("x-clave"))) {
                send(out, 403, "application/json", new JSONObject().put("error",
                        "La sesión del mapa terminó: ábrelo otra vez desde Chromis Admin.").toString());
                return;
            }
            JSONObject result;
            try {
                result = api(r);
                send(out, 200, "application/json; charset=utf-8", result.toString());
            } catch (Refused ex) {
                send(out, 400, "application/json; charset=utf-8", new JSONObject().put("error", ex.getMessage()).toString());
            } catch (SQLException | RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Mapa de entregas: " + r.path, ex);
                send(out, 500, "application/json; charset=utf-8",
                        new JSONObject().put("error", "No se pudo guardar: " + ex.getMessage()).toString());
            }
        } catch (SocketTimeoutException ex) {
            // browser kept the connection open; nothing to do
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "Mapa de entregas", ex);
        }
    }

    private static Request read(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] parts = line.split(" ");
        if (parts.length < 2) {
            return null;
        }
        Request r = new Request();
        r.method = parts[0];
        String target = parts[1];
        int q = target.indexOf('?');
        r.path = q < 0 ? target : target.substring(0, q);
        if (q >= 0) {
            for (String kv : target.substring(q + 1).split("&")) {
                int e = kv.indexOf('=');
                if (e > 0) {
                    r.query.put(URLDecoder.decode(kv.substring(0, e), StandardCharsets.UTF_8),
                            URLDecoder.decode(kv.substring(e + 1), StandardCharsets.UTF_8));
                }
            }
        }
        String h;
        while ((h = readLine(in)) != null && !h.isEmpty()) {
            int c = h.indexOf(':');
            if (c > 0) {
                r.headers.put(h.substring(0, c).trim().toLowerCase(Locale.ROOT), h.substring(c + 1).trim());
            }
        }
        int length = 0;
        try {
            length = Integer.parseInt(r.headers.getOrDefault("content-length", "0"));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (length < 0 || length > MAX_BODY) {
            return null;
        }
        byte[] body = in.readNBytes(length);
        r.body = new String(body, StandardCharsets.UTF_8);
        return r;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                b.write(c);
            }
            if (b.size() > 8192) {
                return null;
            }
        }
        if (c == -1 && b.size() == 0) {
            return null;
        }
        return b.toString(StandardCharsets.UTF_8);
    }

    private static void send(OutputStream out, int status, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + status + (status == 200 ? " OK" : " Error") + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + b.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Referrer-Policy: same-origin\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(b);
        out.flush();
    }

    private String page() throws IOException {
        try (InputStream in = MapServer.class.getResourceAsStream("/uk/chromis/administration/delivery/mapa.html")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("__CLAVE__", token);
        }
    }

    // ---------------- API ----------------

    private static final class Refused extends Exception {

        Refused(String message) {
            super(message);
        }
    }

    private synchronized Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(5)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                    // replaced below
                }
            }
            connection = DriverManager.getConnection(url, dbUser, dbPassword);
        }
        return connection;
    }

    private synchronized JSONObject api(Request r) throws SQLException, Refused {
        Connection con = connection();
        JSONObject in = r.body.isEmpty() ? new JSONObject() : new JSONObject(r.body);
        switch (r.method + " " + r.path) {
            case "GET /api/datos":
                return data(con);
            case "POST /api/cuadrante":
                saveZone(con, in);
                break;
            case "POST /api/cuadrante/desactivar":
                admin();
                ZoneStore.deactivate(con, in.getInt("id"));
                break;
            case "POST /api/ubicacion":
                saveLocation(con, in);
                break;
            default:
                throw new Refused("No existe: " + r.method + " " + r.path);
        }
        if (onChange != null) {
            onChange.run();
        }
        return data(con);
    }

    private void admin() throws Refused {
        if (user.role != DeliveryStatusStore.Role.ADMIN) {
            throw new Refused("Solo el administrador dibuja y cambia cuadrantes.");
        }
    }

    private void saveZone(Connection con, JSONObject in) throws SQLException, Refused {
        admin();
        int id = in.optInt("id", 0);
        ZoneStore.Zone z = null;
        if (id != 0) {
            for (ZoneStore.Zone x : ZoneStore.load(con, true)) {
                if (x.id == id) {
                    z = x;
                }
            }
            if (z == null) {
                throw new Refused("Ese cuadrante ya no existe.");
            }
        } else {
            z = new ZoneStore.Zone();
        }
        if (in.has("nombre")) {
            z.name = in.getString("nombre");
        }
        if (in.has("color")) {
            z.color = in.getString("color");
        }
        if (in.has("poligono")) {
            List<double[]> poly = new ArrayList<>();
            JSONArray a = in.getJSONArray("poligono");
            for (int i = 0; i < a.length(); i++) {
                JSONArray p = a.getJSONArray(i);
                poly.add(new double[]{p.getDouble(0), p.getDouble(1)});
            }
            z.polygon = poly;
        }
        try {
            ZoneStore.save(con, z);
        } catch (SQLException ex) {
            if (ex.getSQLState() == null) {
                throw new Refused(ex.getMessage());   // validation message
            }
            throw ex;
        }
    }

    private void saveLocation(Connection con, JSONObject in) throws SQLException, Refused {
        String customer = in.getString("cliente");
        if (!in.has("lat") || !in.has("lng")) {
            throw new Refused("Falta la ubicación (para quitarla se manda vacía a propósito).");
        }
        Double lat = in.isNull("lat") ? null : in.getDouble("lat");
        Double lng = in.isNull("lng") ? null : in.getDouble("lng");
        String problem = GeoStore.validate(lat, lng);
        if (problem != null) {
            throw new Refused(problem);
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM customers WHERE id = ?")) {
            ps.setString(1, customer);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new Refused("Ese cliente ya no existe.");
                }
            }
        }
        GeoStore.Geo old = GeoStore.get(con, customer);
        String status = in.optString("estado", old == null ? GeoStore.CAPTIVE : old.status);
        if (!List.of(GeoStore.PROSPECT, GeoStore.CAPTIVE, GeoStore.PENDING_VISIT).contains(status)) {
            throw new Refused("Estado de cliente no válido.");
        }
        GeoStore.save(con, customer, lat, lng, status, GeoStore.SOURCE_ADMIN, user.name);
    }

    private JSONObject data(Connection con) throws SQLException {
        GeoStore.createTable(con);
        ZoneStore.createTable(con);
        JSONObject o = new JSONObject();
        o.put("usuario", user.name);
        o.put("rol", user.role.name().toLowerCase(Locale.ROOT));
        o.put("clave_mapa", PosSettings.get(con, DeliveryManagerController.MAPS_KEY, ""));
        o.put("centro", PosSettings.get(con, DeliveryManagerController.MAPS_CENTER, ""));

        Map<String, String> people = new LinkedHashMap<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT id, name FROM people ORDER BY name")) {
            while (rs.next()) {
                people.put(rs.getString(1), rs.getString(2));
            }
        }

        JSONArray zones = new JSONArray();
        for (ZoneStore.Zone z : ZoneStore.load(con, false)) {
            JSONArray poly = new JSONArray();
            for (double[] p : z.polygon) {
                poly.put(new JSONArray().put(p[0]).put(p[1]));
            }
            zones.put(new JSONObject().put("id", z.id).put("nombre", z.name).put("color", z.color).put("poligono", poly)
                    .put("repartidor", name(people, z.driver)).put("despachador", name(people, z.dispatcher)));
        }
        o.put("cuadrantes", zones);

        JSONArray deliveries = new JSONArray();
        for (DeliveryStatusStore.Entry e : DeliveryStatusStore.load(con, 0)) {
            deliveries.put(new JSONObject().put("ticket", e.ticketId).put("numero", e.ticketNumber)
                    .put("cliente", e.customerId == null ? JSONObject.NULL : e.customerId)
                    .put("nombre", e.customerName != null ? e.customerName : e.receiver != null ? e.receiver : "")
                    .put("direccion", e.address).put("telefono", e.phone == null ? "" : e.phone)
                    .put("indicaciones", e.instructions == null ? "" : e.instructions)
                    .put("estado", e.status).put("estado_texto", DeliveryStatusStore.label(e.status)).put("total", e.total)
                    .put("lat", e.lat == null ? JSONObject.NULL : e.lat).put("lng", e.lng == null ? JSONObject.NULL : e.lng)
                    .put("cuadrante", e.zone == null ? "" : e.zone.name).put("repartidor", name(people, e.driver())));
        }
        o.put("entregas", deliveries);

        JSONArray customers = new JSONArray();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT c.id, c.name, c.address, c.city, c.phone, g.lat, g.lng, g.status, g.source FROM customers c "
                + "LEFT JOIN customer_geo g ON g.customer = c.id WHERE c.active = 1 ORDER BY c.name")) {
            while (rs.next()) {
                String address = rs.getString(3) == null ? "" : rs.getString(3);
                if (rs.getString(4) != null && !rs.getString(4).isEmpty()) {
                    address += (address.isEmpty() ? "" : ", ") + rs.getString(4);
                }
                customers.put(new JSONObject().put("id", rs.getString(1)).put("nombre", rs.getString(2)).put("direccion", address)
                        .put("telefono", rs.getString(5) == null ? "" : rs.getString(5))
                        .put("lat", rs.getObject(6) == null ? JSONObject.NULL : rs.getDouble(6))
                        .put("lng", rs.getObject(7) == null ? JSONObject.NULL : rs.getDouble(7))
                        .put("estado", rs.getString(8) == null ? GeoStore.CAPTIVE : rs.getString(8)));
            }
        }
        o.put("clientes", customers);
        return o;
    }

    private static Object name(Map<String, String> people, String id) {
        return id == null ? "" : people.getOrDefault(id, "");
    }
}
