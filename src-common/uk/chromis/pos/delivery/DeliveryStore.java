package uk.chromis.pos.delivery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Sales marked "Entrega" (delivery). They use Chromis' own delivery data:
 * table customer_delivery, one row per ticket with the same id as the ticket,
 * which the POS writes when the ticket is saved and the ticket template
 * prints (Printer.Delivery). The kind of order is also kept as the ticket
 * property tipo_pedido ("mostrador" / "entrega") in receipts.attributes.
 */
public final class DeliveryStore {

    /** Ticket property with the kind of order. */
    public static final String ORDER_TYPE = "tipo_pedido";
    public static final String COUNTER = "mostrador";
    public static final String DELIVERY = "entrega";

    /** Column sizes of customer_delivery. */
    public static final int NAME_SIZE = 100;
    public static final int ADDRESS_SIZE = 50;
    public static final int POSTCODE_SIZE = 10;
    public static final int PHONE_SIZE = 15;
    public static final int COMMENTS_SIZE = 200;

    private DeliveryStore() {
    }

    public static final class Delivery {

        public String ticketId;
        public int ticketNumber;
        public Timestamp saleDate;
        public String customerId;
        public String customerName;
        public String name;
        public String address1;
        public String address2;
        public String address3;
        public String postcode;
        public String phone;
        public Timestamp deliveryDate;
        public String comments;
        public boolean delivered;
        public double total;

        /** Address in one line, without the empty parts. */
        public String address() {
            StringBuilder sb = new StringBuilder();
            for (String part : new String[]{address1, address2, address3, postcode}) {
                if (part != null && !part.trim().isEmpty()) {
                    sb.append(sb.length() == 0 ? "" : ", ").append(part.trim());
                }
            }
            return sb.toString();
        }
    }

    /** Text cut to the size of its column (null stays null). */
    public static String fit(String text, int size) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        return t.length() > size ? t.substring(0, size) : t;
    }

    /**
     * Delivery sales, the oldest delivery date first. Pending ones only, or
     * also the delivered ones of the last days.
     */
    public static List<Delivery> load(Connection con, boolean includeDelivered, int deliveredDays) throws SQLException {
        List<Delivery> list = new ArrayList<>();
        String sql = "SELECT cd.id, t.ticketid, r.datenew, t.customer, c.name, cd.name, cd.addressline1, cd.addressline2, "
                + "cd.addressline3, cd.postcode, cd.phone, cd.deliverydate, cd.comments, cd.delivered, "
                + "(SELECT COALESCE(SUM(p.total), 0) FROM payments p WHERE p.receipt = r.id) "
                + "FROM customer_delivery cd JOIN tickets t ON t.id = cd.id JOIN receipts r ON r.id = t.id "
                + "LEFT JOIN customers c ON c.id = t.customer "
                + "WHERE t.tickettype = 0 AND (cd.delivered = 0"
                + (includeDelivered ? " OR cd.deliverydate >= ?" : "") + ") "
                + "ORDER BY cd.delivered, cd.deliverydate, t.ticketid";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            if (includeDelivered) {
                ps.setTimestamp(1, new Timestamp(System.currentTimeMillis() - deliveredDays * 86_400_000L));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Delivery d = new Delivery();
                    d.ticketId = rs.getString(1);
                    d.ticketNumber = rs.getInt(2);
                    d.saleDate = rs.getTimestamp(3);
                    d.customerId = rs.getString(4);
                    d.customerName = rs.getString(5);
                    d.name = rs.getString(6);
                    d.address1 = rs.getString(7);
                    d.address2 = rs.getString(8);
                    d.address3 = rs.getString(9);
                    d.postcode = rs.getString(10);
                    d.phone = rs.getString(11);
                    d.deliveryDate = rs.getTimestamp(12);
                    d.comments = rs.getString(13);
                    d.delivered = rs.getBoolean(14);
                    d.total = rs.getDouble(15);
                    list.add(d);
                }
            }
        }
        return list;
    }

    public static boolean setDelivered(Connection con, String ticketId, boolean delivered) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE customer_delivery SET delivered = ? WHERE id = ?")) {
            ps.setBoolean(1, delivered);
            ps.setString(2, ticketId);
            return ps.executeUpdate() > 0;
        }
    }
}
