import java.sql.*;
import java.util.*;
import uk.chromis.pos.delivery.DeliveryStore;

/**
 * Prueba manual de las entregas (DeliveryStore) contra una base Chromis 1.5.4
 * que ya tenga una caja abierta (closedcash) y el usuario Administrator. Crea
 * tres ventas de prueba (dos con su fila de customer_delivery) con números de
 * ticket nuevos. Chromis no permite borrar ventas: quedan registradas y al
 * terminar solo se quitan sus filas de entrega.
 *
 *   java -cp dist/admin.jar tests/DeliveryTest.java [url usuario contraseña]
 */
public class DeliveryTest {
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
    static void sale(Connection c, String id, int number, String money, String person, String customer, double total,
            boolean delivery, String deliveryDate) throws SQLException {
        exec(c, "INSERT INTO receipts (id, money, datenew, person) VALUES ('" + id + "', '" + money + "', NOW(), '" + person + "')");
        exec(c, "INSERT INTO tickets (id, tickettype, ticketid, person, customer) VALUES ('" + id + "', 0, " + number + ", '"
                + person + "', " + (customer == null ? "NULL" : "'" + customer + "'") + ")");
        exec(c, "INSERT INTO payments (id, receipt, payment, total) VALUES ('" + UUID.randomUUID() + "', '" + id + "', 'cash', " + total + ")");
        if (delivery) {
            exec(c, "INSERT INTO customer_delivery (id, name, addressline1, addressline2, addressline3, postcode, phone, "
                    + "deliverydate, delivered, comments) VALUES ('" + id + "', 'Recibe " + number + "', 'Calle 1', '', 'Ciudad', "
                    + "'56330', '555', '" + deliveryDate + "', 0, 'Portón azul')");
        }
    }
    static DeliveryStore.Delivery find(List<DeliveryStore.Delivery> list, String id) {
        for (DeliveryStore.Delivery d : list) if (d.ticketId.equals(id)) return d;
        return null;
    }

    public static void main(String[] a) throws Exception {
        Connection c = DriverManager.getConnection(a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos",
                a.length > 1 ? a[1] : "chromis", a.length > 2 ? a[2] : "chromis");
        String money = one(c, "SELECT money FROM closedcash ORDER BY datestart DESC LIMIT 1");
        String person = one(c, "SELECT id FROM people WHERE name = 'Administrator'");
        String customer = one(c, "SELECT id FROM customers LIMIT 1");
        if (money == null) {
            System.out.println("Hace falta una caja abierta (closedcash): abre Chromis POS una vez.");
            System.exit(2);
        }
        String run = UUID.randomUUID().toString().substring(0, 8);
        String s1 = "prueba-entrega-" + run + "-1", s2 = "prueba-entrega-" + run + "-2", s3 = "prueba-entrega-" + run + "-3";
        String[] ids = {s1, s2, s3};
        int n = Integer.parseInt(one(c, "SELECT GREATEST(COALESCE(MAX(ticketid), 0), 990000) FROM tickets")) + 1;
        try {
            check("cortar al tamaño", DeliveryStore.fit("  Av. Juárez 120  ", 6), "Av. Ju");
            check("cortar nulo", DeliveryStore.fit(null, 6), null);

            sale(c, s1, n, money, person, customer, 120.5, true, "2026-10-22 00:00:00");
            sale(c, s2, n + 1, money, person, null, 80, true, "2026-10-21 00:00:00");
            sale(c, s3, n + 2, money, person, customer, 50, false, null);

            List<DeliveryStore.Delivery> pending = DeliveryStore.load(c, false, 7);
            DeliveryStore.Delivery d1 = find(pending, s1), d2 = find(pending, s2);
            check("venta con entrega aparece", d1 != null, true);
            check("venta sin entrega no aparece", find(pending, s3), null);
            check("número de ticket", d1.ticketNumber, n);
            check("total de la venta", d1.total, 120.5);
            check("cliente del ticket", d1.customerId, customer);
            check("sin cliente también aparece", d2 != null && d2.customerId == null, true);
            check("dirección en una línea sin partes vacías", d1.address(), "Calle 1, Ciudad, 56330");
            check("indicaciones", d1.comments, "Portón azul");
            check("primero la que se entrega antes", pending.indexOf(d2) < pending.indexOf(d1), true);

            check("marcar entregada", DeliveryStore.setDelivered(c, s1, true), true);
            check("ya no está pendiente", find(DeliveryStore.load(c, false, 7), s1), null);
            check("con entregadas sí aparece", find(DeliveryStore.load(c, true, 7), s1) != null, true);
            check("y dice entregada", find(DeliveryStore.load(c, true, 7), s1).delivered, true);
            check("entregada hace más días no aparece", find(DeliveryStore.load(c, true, -30), s1), null);
            check("desmarcar", DeliveryStore.setDelivered(c, s1, false), true);
            check("vuelve a pendiente", find(DeliveryStore.load(c, false, 7), s1) != null, true);
            check("marcar una que no existe", DeliveryStore.setDelivered(c, "no-existe", true), false);

            // Dirección escrita al cobrar → ficha del cliente, solo en lo vacío
            String guid = one(c, "SELECT guid FROM siteguid");
            String vacio = "prueba-dir-" + run + "-1", lleno = "prueba-dir-" + run + "-2";
            exec(c, "INSERT INTO customers (id, taxid, name, siteguid) VALUES ('" + vacio + "', '" + vacio + "', 'Prueba sin dirección', '" + guid + "')");
            exec(c, "INSERT INTO customers (id, taxid, name, address, phone2, siteguid) VALUES ('" + lleno + "', '" + lleno
                    + "', 'Prueba con dirección', 'Calle Original 5', '5559999', '" + guid + "')");
            try {
                check("guardar en cliente sin dirección", DeliveryStore.fillCustomerAddress(c, vacio, "Av. Nueva 10", "Col. Centro",
                        "Chimalhuacán", "56330", "5551111"), true);
                check("la ficha queda llena", one(c, "SELECT CONCAT_WS(' | ', address, address2, city, postal, phone) FROM customers WHERE id = '" + vacio + "'"),
                        "Av. Nueva 10 | Col. Centro | Chimalhuacán | 56330 | 5551111");
                DeliveryStore.fillCustomerAddress(c, lleno, "Otra Calle 1", "", "Texcoco", "", "5552222");
                check("no reemplaza lo que ya tenía", one(c, "SELECT CONCAT_WS(' | ', address, city, COALESCE(phone, '-')) FROM customers WHERE id = '" + lleno + "'"),
                        "Calle Original 5 | Texcoco | -");
            } finally {
                exec(c, "DELETE FROM customers WHERE id IN ('" + vacio + "', '" + lleno + "')");
            }
        } finally {
            for (String id : ids) {
                exec(c, "DELETE FROM customer_delivery WHERE id = '" + id + "'");
            }
        }
        System.out.println(fails == 0 ? "\nTODO BIEN" : "\nFALLAS: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
