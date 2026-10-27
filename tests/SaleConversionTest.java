import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.util.*;
import java.util.Date;
import uk.chromis.pos.delivery.DeliveryStatusStore;
import uk.chromis.pos.delivery.DeliveryStore;
import uk.chromis.pos.delivery.SaleConversion;

/**
 * Prueba de "Convertir venta en entrega" (caja, Entregas y Gestor usan la
 * misma lógica) contra una base Chromis 1.5.4 con las tablas de entregas.
 * Crea sus propias ventas (tickets 997001…); las ventas quedan en la base
 * (Chromis no permite borrarlas) y se quitan sus datos de entrega.
 *
 *   java -cp dist/admin.jar tests/SaleConversionTest.java [url usuario contraseña]
 */
public class SaleConversionTest {
    static int fails = 0;
    static String money, admin;
    static int nextTicket;
    static final List<String> sales = new ArrayList<>();
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
    /** A charged sale (or refund) with ticket properties as Chromis writes them. */
    static int sale(Connection c, String customer, int type, double total) throws Exception {
        String id = UUID.randomUUID().toString();
        int number = nextTicket++;
        Properties p = new Properties();
        p.setProperty("ticket.note", "nota original");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        p.storeToXML(out, "Chromis POS", "UTF-8");
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO receipts (id, money, datenew, attributes, person) VALUES (?, ?, NOW(), ?, ?)")) {
            ps.setString(1, id); ps.setString(2, money); ps.setBytes(3, out.toByteArray()); ps.setString(4, admin);
            ps.executeUpdate();
        }
        exec(c, "INSERT INTO tickets (id, tickettype, ticketid, person, customer) VALUES ('" + id + "', " + type + ", " + number + ", '"
                + admin + "', " + (customer == null ? "NULL" : "'" + customer + "'") + ")");
        exec(c, "INSERT INTO payments (id, receipt, payment, total) VALUES ('" + UUID.randomUUID() + "', '" + id + "', 'cash', " + total + ")");
        sales.add(id);
        return number;
    }
    static Properties props(Connection c, String ticketId) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT attributes FROM receipts WHERE id = '" + ticketId + "'")) {
            rs.next();
            Properties p = new Properties();
            p.loadFromXML(new ByteArrayInputStream(rs.getBytes(1)));
            return p;
        }
    }
    public static void main(String[] a) throws Exception {
        Connection c = DriverManager.getConnection(a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos",
                a.length > 1 ? a[1] : "chromis", a.length > 2 ? a[2] : "chromis");
        money = one(c, "SELECT money FROM closedcash ORDER BY datestart DESC LIMIT 1");
        admin = one(c, "SELECT id FROM people WHERE name = 'Administrator'");
        nextTicket = Integer.parseInt(one(c, "SELECT GREATEST(COALESCE(MAX(ticketid), 0), 997000) FROM tickets")) + 1;
        DeliveryStatusStore.createTables(c);
        String cust = "prueba-conv-" + UUID.randomUUID().toString().substring(0, 8);
        exec(c, "INSERT INTO customers (id, taxid, name, address, address2, city, postal, phone) VALUES ('" + cust
                + "', '" + cust + "', 'Cliente Conversión', 'Av. Juárez 10', 'Centro', 'Toluca', '50000', '7221234567')");
        String sinDir = "prueba-conv-" + UUID.randomUUID().toString().substring(0, 8);
        exec(c, "INSERT INTO customers (id, taxid, name) VALUES ('" + sinDir + "', '" + sinDir + "', 'Cliente Sin Dirección')");
        try {
            int n1 = sale(c, cust, 0, 150.0);
            SaleConversion.Sale s = SaleConversion.find(c, n1);
            check("encuentra la venta", s != null && s.ticketNumber == n1, true);
            check("total", s.total, 150.0);
            check("se puede convertir", SaleConversion.problem(s, new Date()), null);
            SaleConversion.Data d = SaleConversion.Data.fromCustomer(s);
            check("dirección del cliente", d.address1 + " / " + d.address2 + " / " + d.address3 + " / " + d.postcode + " / " + d.phone,
                    "Av. Juárez 10 / Centro / Toluca / 50000 / 7221234567");
            d.comments = "Portón verde";
            SaleConversion.convert(c, s, d, admin, "Administrator", "CAJA", "caja");
            check("datos de entrega guardados", one(c, "SELECT CONCAT(name, '|', addressline1, '|', comments, '|', delivered + 0) FROM customer_delivery WHERE id = '" + s.ticketId + "'"),
                    "Cliente Conversión|Av. Juárez 10|Portón verde|0");
            Properties p = props(c, s.ticketId);
            check("ticket marcado como entrega", p.getProperty(DeliveryStore.ORDER_TYPE), "entrega");
            check("conserva sus otras propiedades", p.getProperty("ticket.note"), "nota original");
            check("historial", one(c, "SELECT CONCAT(from_status, '>', to_status, '|', role, '|', user_name) FROM delivery_events WHERE ticket = '" + s.ticketId + "'"),
                    "mostrador>confirmado|CAJA|Administrator");
            check("aparece en Entregas", DeliveryStore.load(c, false, 0).stream().anyMatch(x -> x.ticketId.equals(s.ticketId)), true);
            DeliveryStatusStore.Entry e = DeliveryStatusStore.get(c, s.ticketId);
            check("aparece en el Gestor como confirmada", e == null ? null : e.status, DeliveryStatusStore.PENDING);
            check("no cambió el pago", one(c, "SELECT SUM(total) FROM payments WHERE receipt = '" + s.ticketId + "'"), "150.000");

            SaleConversion.Sale again = SaleConversion.find(c, n1);
            check("ya es entrega", SaleConversion.problem(again, new Date()), "El ticket " + n1 + " ya es una entrega.");
            String msg = null;
            try { SaleConversion.convert(c, s, d, admin, "Administrator", "CAJA", "caja"); } catch (SQLException ex) { msg = ex.getMessage(); }
            check("no se convierte dos veces", msg, "El ticket " + n1 + " ya es una entrega.");
            check("autocommit restaurado", c.getAutoCommit(), true);

            check("no existe", SaleConversion.problem(SaleConversion.find(c, 999999999), new Date()), "No hay ninguna venta con ese número de ticket.");
            int dev = sale(c, cust, 1, -20.0);
            check("devolución", SaleConversion.problem(SaleConversion.find(c, dev), new Date()), "El ticket " + dev + " es una devolución.");
            int n2 = sale(c, null, 0, 30.0);
            check("sin cliente", SaleConversion.problem(SaleConversion.find(c, n2), new Date()).startsWith("El ticket " + n2 + " no tiene cliente"), true);
            int n3 = sale(c, cust, 0, 40.0);
            Date in8days = new Date(System.currentTimeMillis() + 8 * 86_400_000L);
            check("venta vieja", SaleConversion.problem(SaleConversion.find(c, n3), in8days).contains("más de 7 días"), true);

            SaleConversion.Data empty = SaleConversion.Data.fromCustomer(SaleConversion.find(c, n3));
            empty.address1 = " ";
            msg = null;
            try { SaleConversion.convert(c, SaleConversion.find(c, n3), empty, admin, "Administrator", "ADMIN", null); } catch (SQLException ex) { msg = ex.getMessage(); }
            check("sin dirección no se guarda", msg, "Escribe la dirección de entrega.");
            check("nada a medias", one(c, "SELECT COUNT(*) FROM customer_delivery WHERE id = (SELECT id FROM tickets WHERE ticketid = " + n3 + " AND tickettype = 0)"), "0");

            // Cliente sin dirección: se guarda la escrita en su ficha
            int n4 = sale(c, sinDir, 0, 80.0);
            SaleConversion.Sale s4 = SaleConversion.find(c, n4);
            SaleConversion.Data d4 = SaleConversion.Data.fromCustomer(s4);
            check("cliente sin dirección", d4.address1, "");
            d4.address1 = "Calle 5 de Mayo 22"; d4.address3 = "Metepec"; d4.phone = "7229998877"; d4.saveToCustomer = true;
            SaleConversion.convert(c, s4, d4, admin, "Administrator", "DISPATCHER", "Admin, Gestor de entregas");
            check("dirección guardada en el cliente", one(c, "SELECT CONCAT(address, '|', city, '|', phone) FROM customers WHERE id = '" + sinDir + "'"),
                    "Calle 5 de Mayo 22|Metepec|7229998877");
            check("venta sin propiedades previas", props(c, s4.ticketId).getProperty(DeliveryStore.ORDER_TYPE), "entrega");
        } finally {
            for (String s : sales) {
                exec(c, "DELETE FROM delivery_events WHERE ticket = '" + s + "'");
                exec(c, "DELETE FROM delivery_status WHERE ticket = '" + s + "'");
                exec(c, "DELETE FROM customer_delivery WHERE id = '" + s + "'");
            }
            for (String cu : new String[]{cust, sinDir}) {
                try { exec(c, "DELETE FROM customers WHERE id = '" + cu + "' AND id NOT IN (SELECT customer FROM tickets WHERE customer IS NOT NULL)"); }
                catch (SQLException ex) { /* Chromis puede impedir borrar clientes */ }
            }
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
