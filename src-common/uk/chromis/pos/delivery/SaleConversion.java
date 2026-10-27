package uk.chromis.pos.delivery;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Turns a sale already charged as a counter sale into a delivery, from the
 * till, from Entregas or from the delivery manager. It only adds what a sale
 * marked "Entrega" at the till has: its row in customer_delivery and the
 * ticket property tipo_pedido = entrega. The sale itself (lines, payments,
 * cash, stock) is not touched. The change is kept in delivery_events.
 */
public final class SaleConversion {

    /** Sales older than this many days are not converted (a wrong ticket number is more likely). */
    public static final int MAX_DAYS = 7;

    private SaleConversion() {
    }

    /** A sale found by its ticket number. */
    public static final class Sale {

        public String ticketId;
        public int ticketNumber;
        public Timestamp date;
        public boolean refund;
        public boolean alreadyDelivery;
        public double total;
        public String customerId;
        public String customerName;
        public String customerAddress;
        public String customerAddress2;
        public String customerCity;
        public String customerRegion;
        public String customerPostal;
        public String customerPhone;
        public String customerPhone2;
        public final List<String> lines = new ArrayList<>();

        /** Short description: number, date, customer and total. */
        public String summary() {
            return "Ticket " + ticketNumber + " · " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(date)
                    + " · " + (customerName == null ? "sin cliente" : customerName)
                    + " · $" + String.format(java.util.Locale.ROOT, "%,.2f", total);
        }
    }

    /** The data of the delivery, as the till asks for them. */
    public static final class Data {

        public String name;
        public String address1;
        public String address2;
        public String address3;
        public String postcode;
        public String phone;
        public Date deliveryDate;
        public String comments;
        /** The customer has no address: keep the typed one in Customers too. */
        public boolean saveToCustomer;

        /** Filled in from the customer of the sale, as the till does. */
        public static Data fromCustomer(Sale s) {
            Data d = new Data();
            d.name = text(s.customerName);
            d.address1 = text(s.customerAddress);
            String a2 = text(s.customerAddress2);
            // The map web stores the coordinates there; they are of no use on paper
            d.address2 = a2.startsWith("GPS ") ? "" : a2;
            String city = text(s.customerCity);
            String region = text(s.customerRegion);
            d.address3 = region.isEmpty() || region.equalsIgnoreCase(city) ? city : city.isEmpty() ? region : city + ", " + region;
            d.postcode = text(s.customerPostal);
            String phone = text(s.customerPhone);
            d.phone = phone.isEmpty() ? text(s.customerPhone2) : phone;
            d.deliveryDate = new Date();
            d.comments = "";
            return d;
        }
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }

    /** The newest sale (not refund) with that ticket number, else the newest refund; null if none. */
    public static Sale find(Connection con, int ticketNumber) throws SQLException {
        Sale s = null;
        try (PreparedStatement ps = con.prepareStatement("SELECT t.id, t.ticketid, r.datenew, t.tickettype, "
                + "(SELECT COUNT(*) FROM customer_delivery cd WHERE cd.id = t.id), "
                + "(SELECT COALESCE(SUM(p.total), 0) FROM payments p WHERE p.receipt = r.id), "
                + "t.customer, c.name, c.address, c.address2, c.city, c.region, c.postal, c.phone, c.phone2 "
                + "FROM tickets t JOIN receipts r ON r.id = t.id LEFT JOIN customers c ON c.id = t.customer "
                + "WHERE t.ticketid = ? ORDER BY t.tickettype, r.datenew DESC LIMIT 1")) {
            ps.setInt(1, ticketNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s = new Sale();
                    s.ticketId = rs.getString(1);
                    s.ticketNumber = rs.getInt(2);
                    s.date = rs.getTimestamp(3);
                    s.refund = rs.getInt(4) != 0;
                    s.alreadyDelivery = rs.getInt(5) > 0;
                    s.total = rs.getDouble(6);
                    s.customerId = rs.getString(7);
                    s.customerName = rs.getString(8);
                    s.customerAddress = rs.getString(9);
                    s.customerAddress2 = rs.getString(10);
                    s.customerCity = rs.getString(11);
                    s.customerRegion = rs.getString(12);
                    s.customerPostal = rs.getString(13);
                    s.customerPhone = rs.getString(14);
                    s.customerPhone2 = rs.getString(15);
                }
            }
        }
        if (s == null) {
            return null;
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT l.units, COALESCE(p.name, '') FROM ticketlines l "
                + "LEFT JOIN products p ON p.id = l.product WHERE l.ticket = ? ORDER BY l.line")) {
            ps.setString(1, s.ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double u = rs.getDouble(1);
                    String units = u == Math.rint(u) ? String.valueOf((long) u) : String.valueOf(u);
                    s.lines.add(units + " x " + rs.getString(2));
                }
            }
        }
        return s;
    }

    /** Why the sale cannot become a delivery, or null if it can. */
    public static String problem(Sale s, Date now) {
        if (s == null) {
            return "No hay ninguna venta con ese número de ticket.";
        }
        if (s.refund) {
            return "El ticket " + s.ticketNumber + " es una devolución.";
        }
        if (s.alreadyDelivery) {
            return "El ticket " + s.ticketNumber + " ya es una entrega.";
        }
        if (now.getTime() - s.date.getTime() > MAX_DAYS * 86_400_000L) {
            return "El ticket " + s.ticketNumber + " es de hace más de " + MAX_DAYS + " días; solo se convierten ventas recientes.";
        }
        if (s.customerId == null) {
            return "El ticket " + s.ticketNumber + " no tiene cliente. Las entregas se reparten por cliente: "
                    + "asígnale uno a la venta (Customers) o vuelve a cobrarla con cliente.";
        }
        return null;
    }

    /** What is wrong with the typed data, or null. */
    public static String invalid(Data d) {
        if (d.address1 == null || d.address1.trim().isEmpty()) {
            return "Escribe la dirección de entrega.";
        }
        if (d.deliveryDate == null) {
            return "Escribe la fecha de entrega.";
        }
        return null;
    }

    /**
     * Makes the sale a delivery. role is who did it for the history (ADMIN,
     * DISPATCHER or CAJA). Fails if the sale became a delivery meanwhile.
     */
    public static void convert(Connection con, Sale s, Data d, String userId, String userName, String role, String from)
            throws SQLException {
        String problem = invalid(d);
        if (problem != null) {
            throw new SQLException(problem);
        }
        DeliveryStatusStore.createTables(con);
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            byte[] attributes;
            try (PreparedStatement ps = con.prepareStatement("SELECT r.attributes, t.tickettype, "
                    + "(SELECT COUNT(*) FROM customer_delivery cd WHERE cd.id = r.id) FROM receipts r "
                    + "JOIN tickets t ON t.id = r.id WHERE r.id = ? FOR UPDATE")) {
                ps.setString(1, s.ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("La venta ya no existe.");
                    }
                    if (rs.getInt(2) != 0) {
                        throw new SQLException("El ticket es una devolución.");
                    }
                    if (rs.getInt(3) > 0) {
                        throw new SQLException("El ticket " + s.ticketNumber + " ya es una entrega.");
                    }
                    attributes = rs.getBytes(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO customer_delivery (id, name, addressline1, "
                    + "addressline2, addressline3, postcode, phone, deliverydate, delivered, comments) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?)")) {
                ps.setString(1, s.ticketId);
                ps.setString(2, DeliveryStore.fit(d.name, DeliveryStore.NAME_SIZE));
                ps.setString(3, DeliveryStore.fit(d.address1, DeliveryStore.ADDRESS_SIZE));
                ps.setString(4, DeliveryStore.fit(d.address2, DeliveryStore.ADDRESS_SIZE));
                ps.setString(5, DeliveryStore.fit(d.address3, DeliveryStore.ADDRESS_SIZE));
                ps.setString(6, DeliveryStore.fit(d.postcode, DeliveryStore.POSTCODE_SIZE));
                ps.setString(7, DeliveryStore.fit(d.phone, DeliveryStore.PHONE_SIZE));
                ps.setTimestamp(8, new Timestamp(d.deliveryDate.getTime()));
                ps.setString(9, DeliveryStore.fit(d.comments, DeliveryStore.COMMENTS_SIZE));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE receipts SET attributes = ? WHERE id = ?")) {
                ps.setBytes(1, withOrderType(attributes));
                ps.setString(2, s.ticketId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO delivery_events (op_id, ticket, from_status, "
                    + "to_status, notes, user_id, user_name, role, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, s.ticketId);
                ps.setString(3, DeliveryStore.COUNTER);
                ps.setString(4, DeliveryStatusStore.PENDING);
                ps.setString(5, "Venta de mostrador convertida en entrega" + (from == null ? "" : " (" + from + ")"));
                ps.setString(6, userId);
                ps.setString(7, userName == null ? null : DeliveryStore.fit(userName, 50));
                ps.setString(8, role);
                ps.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
                ps.executeUpdate();
            }
            if (d.saveToCustomer && s.customerId != null) {
                DeliveryStore.fillCustomerAddress(con, s.customerId, d.address1, d.address2, d.address3, d.postcode, d.phone);
            }
            con.commit();
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    /** The ticket properties (receipts.attributes, XML as Chromis writes them) with tipo_pedido = entrega. */
    static byte[] withOrderType(byte[] attributes) throws SQLException {
        Properties p = new Properties();
        try {
            if (attributes != null && attributes.length > 0) {
                p.loadFromXML(new ByteArrayInputStream(attributes));
            }
            p.setProperty(DeliveryStore.ORDER_TYPE, DeliveryStore.DELIVERY);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            p.storeToXML(out, "Chromis POS", "UTF-8");
            return out.toByteArray();
        } catch (IOException ex) {
            throw new SQLException("No se pudieron leer los datos del ticket", ex);
        }
    }
}
