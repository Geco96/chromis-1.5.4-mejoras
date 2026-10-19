package uk.chromis.administration.receiving;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Supplier orders made from the restock screen and their receipt, stored in
 * Chromis' own supplierorders / supplierorderlines / receiptedorderlines so
 * the original Orders screen lists them too.
 * <ul>
 * <li>The order PDF carries only the order number (PED-000123) as a Code 128
 * barcode; the lines are read from the database when it is scanned.</li>
 * <li>Receiving adds the stock (stock diary reason 9, as Chromis does) but,
 * unlike Chromis, never changes the product cost: a different cost becomes a
 * price change waiting for authorization.</li>
 * <li>What did not arrive is kept as a shortage and carried to the next
 * order.</li>
 * </ul>
 */
public final class PurchaseOrderStore {

    public static final String GENERIC_SUPPLIER = "Proveedor general";
    public static final String PREFIX = "PED-";
    /** Stock diary reason Chromis uses for goods received from a supplier. */
    public static final int REASON_RECEIPT = 9;

    public static final String PENDING = "Pending";
    public static final String PART_RECEIVED = "Received-Part order";
    public static final String RECEIVED = "Received";
    /** Received with shortages: the rest of the order is cancelled and carried to the next one. */
    public static final String SHORT = "Balance Cancelled";

    public static final String PRICE_PENDING = "PENDIENTE";
    public static final String PRICE_APPROVED = "AUTORIZADO";
    public static final String PRICE_REJECTED = "RECHAZADO";
    public static final String PRICE_REPLACED = "SUSTITUIDO";

    private PurchaseOrderStore() {
    }

    public static void createTables(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS order_carryover ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "product VARCHAR(40) NOT NULL, "
                    + "units DECIMAL(12,3) NOT NULL, "
                    + "from_order VARCHAR(40) NOT NULL, "
                    + "from_ponumber VARCHAR(40), "
                    + "created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "to_order VARCHAR(40) NULL, "
                    + "KEY idx_carry_product (product), KEY idx_carry_to (to_order))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS price_changes ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "product VARCHAR(40) NOT NULL, "
                    + "orderid VARCHAR(40), "
                    + "ponumber VARCHAR(40), "
                    + "old_cost DECIMAL(12,4) NOT NULL, "
                    + "new_cost DECIMAL(12,4) NOT NULL, "
                    + "old_price DECIMAL(12,4) NOT NULL, "
                    + "suggested_price DECIMAL(12,4) NOT NULL, "
                    + "status VARCHAR(12) NOT NULL, "
                    + "created TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "created_by VARCHAR(50), "
                    + "decided TIMESTAMP NULL, "
                    + "decided_by VARCHAR(50), "
                    + "applied_price DECIMAL(12,4) NULL, "
                    + "KEY idx_price_status (status), KEY idx_price_product (product))");
        }
    }

    // ---------------- Suppliers ----------------

    /** Id of the generic supplier, created the first time it is needed. */
    public static String genericSupplier(Connection con, String siteGuid) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM suppliers WHERE suppliername = ? AND siteguid = ?")) {
            ps.setString(1, GENERIC_SUPPLIER);
            ps.setString(2, siteGuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        }
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO suppliers (id, suppliername, comments, siteguid) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, GENERIC_SUPPLIER);
            ps.setString(3, "Creado por Reposición para los pedidos sin proveedor registrado");
            ps.setString(4, siteGuid);
            ps.executeUpdate();
        }
        return id;
    }

    /** Active suppliers by name, the generic one first. */
    public static Map<String, String> suppliers(Connection con, String siteGuid) throws SQLException {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(GENERIC_SUPPLIER, genericSupplier(con, siteGuid));
        try (PreparedStatement ps = con.prepareStatement("SELECT suppliername, id FROM suppliers "
                + "WHERE siteguid = ? AND (accountactive IS NULL OR accountactive = 1) ORDER BY suppliername")) {
            ps.setString(1, siteGuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.putIfAbsent(rs.getString(1), rs.getString(2));
                }
            }
        }
        return map;
    }

    // ---------------- Shortages carried to the next order ----------------

    public static final class Carry {

        public double units;
        public final List<String> orders = new ArrayList<>();

        public String describe() {
            return String.join(", ", orders);
        }
    }

    /** Shortages not yet carried to a new order, by product. */
    public static Map<String, Carry> carryover(Connection con) throws SQLException {
        Map<String, Carry> map = new LinkedHashMap<>();
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT product, units, from_ponumber FROM order_carryover "
                        + "WHERE to_order IS NULL ORDER BY id")) {
            while (rs.next()) {
                Carry c = map.computeIfAbsent(rs.getString(1), k -> new Carry());
                c.units += rs.getDouble(2);
                String po = rs.getString(3);
                if (po != null && !c.orders.contains(po)) {
                    c.orders.add(po);
                }
            }
        }
        return map;
    }

    /** Units ordered and not received yet (orders still pending), by product. */
    public static Map<String, Double> onOrder(Connection con) throws SQLException {
        Map<String, Double> map = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT l.productid, SUM(l.orderbalance) FROM supplierorderlines l "
                + "JOIN supplierorders o ON o.id = l.orderid WHERE o.status IN (?, ?) GROUP BY l.productid")) {
            ps.setString(1, PENDING);
            ps.setString(2, PART_RECEIVED);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getDouble(2) > 0) {
                        map.put(rs.getString(1), rs.getDouble(2));
                    }
                }
            }
        }
        return map;
    }

    // ---------------- New order ----------------

    public static final class NewLine {

        public final String productId;
        public final double qty;

        public NewLine(String productId, double qty) {
            this.productId = productId;
            this.qty = qty;
        }
    }

    /** Next free order number: PED-000001, PED-000002... */
    public static String nextOrderNumber(Connection con) throws SQLException {
        long max = 0;
        try (PreparedStatement ps = con.prepareStatement("SELECT ponumber FROM supplierorders WHERE ponumber LIKE ?")) {
            ps.setString(1, PREFIX + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        max = Math.max(max, Long.parseLong(rs.getString(1).substring(PREFIX.length())));
                    } catch (RuntimeException ex) {
                        // a number typed by hand in the Orders screen
                    }
                }
            }
        }
        return String.format("%s%06d", PREFIX, max + 1);
    }

    /**
     * Saves the order (status Pending) with the product cost of each line as
     * the expected cost, and marks the pending shortages of those products as
     * carried to it. Returns the order with its number. userId is the id in
     * people: Chromis' Orders screen shows the user's name from it.
     */
    public static Order createOrder(Connection con, String siteGuid, String supplierId, String userId,
            List<NewLine> lines) throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            String orderId = UUID.randomUUID().toString();
            String ponumber = nextOrderNumber(con);
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO supplierorders "
                    + "(id, supplierid, ponumber, user, status, shipping, siteguid) VALUES (?, ?, ?, ?, ?, 0, ?)")) {
                ps.setString(1, orderId);
                ps.setString(2, supplierId);
                ps.setString(3, ponumber);
                ps.setString(4, userId == null ? "" : userId);
                ps.setString(5, PENDING);
                ps.setString(6, siteGuid);
                ps.executeUpdate();
            }
            int lineno = 1;
            for (NewLine l : lines) {
                if (l.qty <= 0) {
                    continue;
                }
                insertLine(con, orderId, lineno++, l.productId, l.qty, siteGuid);
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE order_carryover SET to_order = ? WHERE product = ? AND to_order IS NULL")) {
                    ps.setString(1, orderId);
                    ps.setString(2, l.productId);
                    ps.executeUpdate();
                }
            }
            con.commit();
            return loadOrder(con, orderId);
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    private static String insertLine(Connection con, String orderId, int lineno, String productId, double qty,
            String siteGuid) throws SQLException {
        String reference = productId;
        String name = "";
        double cost = 0;
        try (PreparedStatement ps = con.prepareStatement("SELECT reference, name, pricebuy FROM products WHERE id = ?")) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("El producto " + productId + " ya no existe");
                }
                reference = rs.getString(1) == null ? productId : rs.getString(1);
                name = rs.getString(2) == null ? "" : rs.getString(2);
                cost = rs.getDouble(3);
            }
        }
        String id = UUID.randomUUID().toString();
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO supplierorderlines (id, orderid, lineno, "
                + "stockcode, productid, productname, uomfactor, uomdescription, qty, orderbalance, qtyreceived, "
                + "taxrate, packcost, receivedcost, siteguid) VALUES (?, ?, ?, ?, ?, ?, 1, 'Unidad', ?, ?, 0, 0, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, orderId);
            ps.setInt(3, lineno);
            ps.setString(4, cut(reference, 40));
            ps.setString(5, productId);
            ps.setString(6, cut(name, 100));
            ps.setDouble(7, qty);
            ps.setDouble(8, qty);
            ps.setDouble(9, cost);
            ps.setDouble(10, cost);
            ps.setString(11, siteGuid);
            ps.executeUpdate();
        }
        return id;
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ---------------- Reading orders ----------------

    public static final class Order {

        public String id;
        public String ponumber;
        public String supplierId;
        public String supplierName;
        public String status;
        public Timestamp orderDate;
        public Timestamp receiptDate;
        public String user;
        public String invoiceNo;
        public final List<Line> lines = new ArrayList<>();

        public boolean isOpen() {
            return PENDING.equalsIgnoreCase(status) || PART_RECEIVED.equalsIgnoreCase(status);
        }

        public String statusLabel() {
            return statusLabel(status);
        }

        public static String statusLabel(String status) {
            if (status == null) {
                return "";
            }
            switch (status) {
                case PENDING:
                    return "Pendiente de recibir";
                case PART_RECEIVED:
                    return "Recibido en parte";
                case RECEIVED:
                    return "Recibido completo";
                case SHORT:
                    return "Recibido con faltantes";
                case "Cancelled":
                    return "Cancelado";
                default:
                    return status;
            }
        }
    }

    public static final class Line {

        public String lineId;      // null for a product that was not in the order
        public int lineno;
        public String productId;
        public String reference;
        public String name;
        public double ordered;
        public double orderCost;    // cost expected when the order was made
        public double received;
        public double receivedCost;
        public double currentCost;  // product cost now (pricebuy)
        public double currentPrice; // product sell price now

        /** Came without being ordered (not saved yet, or saved by a receipt with nothing ordered). */
        public boolean isExtra() {
            return lineId == null || ordered == 0;
        }

        /** Received minus ordered: negative is a shortage. */
        public double difference() {
            return round3(received - ordered);
        }

        public boolean costChanged() {
            return received > 0 && Math.abs(receivedCost - currentCost) > 0.005;
        }

        public String observation() {
            List<String> parts = new ArrayList<>();
            double d = difference();
            if (isExtra()) {
                parts.add("No venía en el pedido");
            } else if (d < 0) {
                parts.add("Falta " + format(-d));
            } else if (d > 0) {
                parts.add("Sobra " + format(d));
            }
            if (costChanged()) {
                parts.add(receivedCost > currentCost ? "Costo sube" : "Costo baja");
            }
            return parts.isEmpty() ? "Correcto" : String.join(" · ", parts);
        }
    }

    /**
     * Order id for what was scanned or typed: "PED-000123", "ped-123", "123"
     * or the number of an order made in the Orders screen. Null if none.
     */
    public static String findOrder(Connection con, String text) throws SQLException {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String t = text.trim().toUpperCase();
        List<String> candidates = new ArrayList<>();
        candidates.add(t);
        String digits = t.startsWith(PREFIX) ? t.substring(PREFIX.length()) : t;
        if (digits.matches("\\d{1,9}")) {
            candidates.add(String.format("%s%06d", PREFIX, Long.parseLong(digits)));
        }
        for (String c : candidates) {
            try (PreparedStatement ps = con.prepareStatement("SELECT id FROM supplierorders WHERE UPPER(ponumber) = ?")) {
                ps.setString(1, c);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                }
            }
        }
        return null;
    }

    /** Orders still waiting for the goods, newest first: ponumber → id. */
    public static Map<String, String> openOrders(Connection con) throws SQLException {
        Map<String, String> map = new LinkedHashMap<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT ponumber, id FROM supplierorders "
                + "WHERE status IN (?, ?) ORDER BY orderdate DESC, sequenceid DESC")) {
            ps.setString(1, PENDING);
            ps.setString(2, PART_RECEIVED);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return map;
    }

    /**
     * The order and its lines. For an open order the received quantity and
     * cost start as ordered, so "everything arrived" is one click; for a
     * received order they are what was received.
     */
    public static Order loadOrder(Connection con, String orderId) throws SQLException {
        Order o = null;
        try (PreparedStatement ps = con.prepareStatement("SELECT o.id, o.ponumber, o.supplierid, s.suppliername, "
                + "o.status, o.orderdate, o.receiptdate, COALESCE(u.name, o.user), o.invoiceno FROM supplierorders o "
                + "LEFT JOIN suppliers s ON s.id = o.supplierid AND s.siteguid = o.siteguid "
                + "LEFT JOIN people u ON u.id = o.user WHERE o.id = ?")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    o = new Order();
                    o.id = rs.getString(1);
                    o.ponumber = rs.getString(2);
                    o.supplierId = rs.getString(3);
                    o.supplierName = rs.getString(4);
                    o.status = rs.getString(5);
                    o.orderDate = rs.getTimestamp(6);
                    o.receiptDate = rs.getTimestamp(7);
                    o.user = rs.getString(8);
                    o.invoiceNo = rs.getString(9);
                }
            }
        }
        if (o == null) {
            return null;
        }
        boolean open = o.isOpen();
        try (PreparedStatement ps = con.prepareStatement("SELECT l.id, l.lineno, l.productid, l.stockcode, "
                + "COALESCE(p.name, l.productname), l.qty, l.packcost, l.orderbalance, l.qtyreceived, l.receivedcost, "
                + "COALESCE(p.pricebuy, 0), COALESCE(p.pricesell, 0), COALESCE(p.reference, l.stockcode) "
                + "FROM supplierorderlines l LEFT JOIN products p ON p.id = l.productid "
                + "WHERE l.orderid = ? ORDER BY l.lineno")) {
            ps.setString(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Line l = new Line();
                    l.lineId = rs.getString(1);
                    l.lineno = rs.getInt(2);
                    l.productId = rs.getString(3);
                    l.name = rs.getString(5);
                    l.ordered = rs.getDouble(6);
                    l.orderCost = rs.getDouble(7);
                    l.currentCost = rs.getDouble(11);
                    l.currentPrice = rs.getDouble(12);
                    l.reference = rs.getString(13);
                    if (open) {
                        // a part-received order (Orders screen) expects only its balance
                        l.received = PART_RECEIVED.equalsIgnoreCase(o.status) ? rs.getDouble(8) : l.ordered;
                        l.receivedCost = l.orderCost;
                    } else {
                        l.received = rs.getDouble(9);
                        l.receivedCost = rs.getDouble(10);
                    }
                    o.lines.add(l);
                }
            }
        }
        return o;
    }

    /** Product for a scanned code, reference or extra barcode, or null. */
    public static String findProduct(Connection con, String code) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM products WHERE code = ? OR reference = ? "
                + "UNION SELECT productid FROM barcodes WHERE barcode = ? LIMIT 1")) {
            ps.setString(1, code);
            ps.setString(2, code);
            ps.setString(3, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** A line for a product that came without being ordered. */
    public static Line extraLine(Connection con, String productId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT reference, name, pricebuy, pricesell FROM products WHERE id = ?")) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Line l = new Line();
                l.productId = productId;
                l.reference = rs.getString(1);
                l.name = rs.getString(2);
                l.orderCost = rs.getDouble(3);
                l.receivedCost = l.orderCost;
                l.currentCost = l.orderCost;
                l.currentPrice = rs.getDouble(4);
                return l;
            }
        }
    }

    // ---------------- Receiving ----------------

    public static final class ReceiptResult {

        public int shortages;
        public int extras;
        public int priceChanges;
        public double receivedUnits;

        public boolean exact() {
            return shortages == 0 && extras == 0 && priceChanges == 0;
        }
    }

    /**
     * Receives the order: stock in for what arrived, shortages carried to the
     * next order, cost differences saved for authorization, and the order
     * marked as received (with shortages or complete). All or nothing.
     */
    public static ReceiptResult receive(Connection con, Order order, String location, String siteGuid,
            String userId, String userName, String invoiceNo) throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            try (PreparedStatement ps = con.prepareStatement("SELECT status FROM supplierorders WHERE id = ? FOR UPDATE")) {
                ps.setString(1, order.id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("El pedido " + order.ponumber + " ya no existe");
                    }
                    String status = rs.getString(1);
                    if (!PENDING.equalsIgnoreCase(status) && !PART_RECEIVED.equalsIgnoreCase(status)) {
                        throw new SQLException("El pedido " + order.ponumber + " ya fue recibido o cancelado ("
                                + Order.statusLabel(status) + ")");
                    }
                }
            }
            String invoice = invoiceNo == null ? "" : cut(invoiceNo.trim(), 30);
            Timestamp now = new Timestamp(System.currentTimeMillis());
            ReceiptResult result = new ReceiptResult();
            int nextLine = 1;
            for (Line l : order.lines) {
                nextLine = Math.max(nextLine, l.lineno + 1);
            }
            for (Line l : order.lines) {
                if (l.received < 0) {
                    throw new SQLException(l.name + ": la cantidad recibida no puede ser negativa");
                }
                if (l.lineId == null) {
                    if (l.received <= 0) {
                        continue;
                    }
                    l.lineno = nextLine++;
                    l.lineId = insertLine(con, order.id, l.lineno, l.productId, 0, siteGuid);
                    l.ordered = 0;
                    result.extras++;
                } else if (l.difference() > 0) {
                    result.extras++;
                }
                double balance = Math.max(round3(l.ordered - l.received), 0);
                try (PreparedStatement ps = con.prepareStatement("UPDATE supplierorderlines SET receiptdate = ?, "
                        + "qtyreceived = ?, receivedcost = ?, orderbalance = ? WHERE id = ?")) {
                    ps.setTimestamp(1, now);
                    ps.setDouble(2, l.received);
                    ps.setDouble(3, l.receivedCost);
                    ps.setDouble(4, balance);
                    ps.setString(5, l.lineId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO receiptedorderlines (id, orderid, lineno, "
                        + "stockcode, productname, uomdescription, qty, orderbalance, qtyreceived, taxrate, receivedcost, "
                        + "receiptdate, fullorder, invoiceno, deliverycost, siteguid) "
                        + "VALUES (?, ?, ?, ?, ?, 'Unidad', ?, ?, ?, 0, ?, ?, ?, ?, 0, ?)")) {
                    ps.setString(1, l.lineId);
                    ps.setString(2, order.id);
                    ps.setInt(3, l.lineno);
                    ps.setString(4, cut(l.reference == null ? l.productId : l.reference, 40));
                    ps.setString(5, cut(l.name == null ? "" : l.name, 100));
                    ps.setDouble(6, l.ordered);
                    ps.setDouble(7, balance);
                    ps.setDouble(8, l.received);
                    ps.setDouble(9, l.receivedCost);
                    ps.setTimestamp(10, now);
                    ps.setBoolean(11, balance == 0);
                    ps.setString(12, invoice);
                    ps.setString(13, siteGuid);
                    ps.executeUpdate();
                }
                if (l.received > 0) {
                    addStock(con, l, order, location, siteGuid, userId);
                    result.receivedUnits += l.received;
                }
                if (balance > 0) {
                    try (PreparedStatement ps = con.prepareStatement("INSERT INTO order_carryover "
                            + "(product, units, from_order, from_ponumber) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, l.productId);
                        ps.setDouble(2, balance);
                        ps.setString(3, order.id);
                        ps.setString(4, order.ponumber);
                        ps.executeUpdate();
                    }
                    result.shortages++;
                }
                if (l.costChanged()) {
                    savePriceChange(con, l, order, userName);
                    result.priceChanges++;
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE supplierorders SET status = ?, receiptdate = ?, invoiceno = ? WHERE id = ?")) {
                ps.setString(1, result.shortages > 0 ? SHORT : RECEIVED);
                ps.setTimestamp(2, now);
                ps.setString(3, invoice);
                ps.setString(4, order.id);
                ps.executeUpdate();
            }
            con.commit();
            order.status = result.shortages > 0 ? SHORT : RECEIVED;
            order.receiptDate = now;
            order.invoiceNo = invoice;
            return result;
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    private static void addStock(Connection con, Line l, Order order, String location, String siteGuid, String userId)
            throws SQLException {
        int updated;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE stockcurrent SET units = units + ? WHERE product = ? AND location = ?")) {
            ps.setDouble(1, l.received);
            ps.setString(2, l.productId);
            ps.setString(3, location);
            updated = ps.executeUpdate();
        }
        if (updated == 0) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO stockcurrent (location, product, units, siteguid) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, location);
                ps.setString(2, l.productId);
                ps.setDouble(3, l.received);
                ps.setString(4, siteGuid);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO stockdiary (datenew, reason, location, product, "
                + "supplier, orderid, units, cost, price, appuser, siteguid, reference) "
                + "VALUES (NOW(), ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)")) {
            ps.setInt(1, REASON_RECEIPT);
            ps.setString(2, location);
            ps.setString(3, l.productId);
            ps.setString(4, order.supplierId);
            ps.setString(5, order.id);
            ps.setDouble(6, l.received);
            ps.setDouble(7, l.receivedCost);
            ps.setString(8, userId);
            ps.setString(9, siteGuid);
            ps.setString(10, "Pedido " + order.ponumber);
            ps.executeUpdate();
        }
    }

    private static void savePriceChange(Connection con, Line l, Order order, String userName) throws SQLException {
        // Only the latest cost of a product waits for authorization
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE price_changes SET status = ?, decided = NOW() WHERE product = ? AND status = ?")) {
            ps.setString(1, PRICE_REPLACED);
            ps.setString(2, l.productId);
            ps.setString(3, PRICE_PENDING);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO price_changes (product, orderid, ponumber, "
                + "old_cost, new_cost, old_price, suggested_price, status, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, l.productId);
            ps.setString(2, order.id);
            ps.setString(3, order.ponumber);
            ps.setDouble(4, l.currentCost);
            ps.setDouble(5, l.receivedCost);
            ps.setDouble(6, l.currentPrice);
            ps.setDouble(7, suggestedPrice(l.currentCost, l.receivedCost, l.currentPrice));
            ps.setString(8, PRICE_PENDING);
            ps.setString(9, userName);
            ps.executeUpdate();
        }
    }

    /** Sell price that keeps the current margin with the new cost, rounded to cents. */
    public static double suggestedPrice(double oldCost, double newCost, double price) {
        if (oldCost <= 0 || price <= 0) {
            return price;
        }
        return Math.round(price * newCost / oldCost * 100.0) / 100.0;
    }

    // ---------------- Price changes ----------------

    public static final class PriceChange {

        public int id;
        public String productId;
        public String reference;
        public String name;
        public String ponumber;
        public Timestamp created;
        public String createdBy;
        public double oldCost;
        public double newCost;
        public double currentCost;
        public double oldPrice;
        public double currentPrice;
        public double suggestedPrice;

        /** Cost change in percent. */
        public double variation() {
            return oldCost == 0 ? 0 : (newCost - oldCost) / oldCost * 100.0;
        }
    }

    public static List<PriceChange> pendingPriceChanges(Connection con) throws SQLException {
        List<PriceChange> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT c.id, c.product, p.reference, p.name, c.ponumber, "
                + "c.created, c.created_by, c.old_cost, c.new_cost, c.old_price, c.suggested_price, p.pricebuy, p.pricesell "
                + "FROM price_changes c JOIN products p ON p.id = c.product WHERE c.status = ? ORDER BY c.created, c.id")) {
            ps.setString(1, PRICE_PENDING);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PriceChange c = new PriceChange();
                    c.id = rs.getInt(1);
                    c.productId = rs.getString(2);
                    c.reference = rs.getString(3);
                    c.name = rs.getString(4);
                    c.ponumber = rs.getString(5);
                    c.created = rs.getTimestamp(6);
                    c.createdBy = rs.getString(7);
                    c.oldCost = rs.getDouble(8);
                    c.newCost = rs.getDouble(9);
                    c.oldPrice = rs.getDouble(10);
                    c.suggestedPrice = rs.getDouble(11);
                    c.currentCost = rs.getDouble(12);
                    c.currentPrice = rs.getDouble(13);
                    list.add(c);
                }
            }
        }
        return list;
    }

    public static int countPendingPriceChanges(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM price_changes WHERE status = ?")) {
            ps.setString(1, PRICE_PENDING);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Applies the new cost to the product and, when sellPrice is not null,
     * the new sell price too. Returns false if the change was already decided.
     */
    public static boolean approve(Connection con, int changeId, Double sellPrice, String user) throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            String product;
            double newCost;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT product, new_cost FROM price_changes WHERE id = ? AND status = ? FOR UPDATE")) {
                ps.setInt(1, changeId);
                ps.setString(2, PRICE_PENDING);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        return false;
                    }
                    product = rs.getString(1);
                    newCost = rs.getDouble(2);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(sellPrice == null
                    ? "UPDATE products SET pricebuy = ? WHERE id = ?"
                    : "UPDATE products SET pricebuy = ?, pricesell = ? WHERE id = ?")) {
                ps.setDouble(1, newCost);
                if (sellPrice == null) {
                    ps.setString(2, product);
                } else {
                    ps.setDouble(2, sellPrice);
                    ps.setString(3, product);
                }
                ps.executeUpdate();
            }
            decide(con, changeId, PRICE_APPROVED, sellPrice, user);
            con.commit();
            return true;
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    /** Leaves the product as it is. Returns false if the change was already decided. */
    public static boolean reject(Connection con, int changeId, String user) throws SQLException {
        return decide(con, changeId, PRICE_REJECTED, null, user);
    }

    private static boolean decide(Connection con, int changeId, String status, Double price, String user)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE price_changes SET status = ?, decided = NOW(), "
                + "decided_by = ?, applied_price = ? WHERE id = ? AND status = ?")) {
            ps.setString(1, status);
            ps.setString(2, user);
            if (price == null) {
                ps.setNull(3, java.sql.Types.DECIMAL);
            } else {
                ps.setDouble(3, price);
            }
            ps.setInt(4, changeId);
            ps.setString(5, PRICE_PENDING);
            return ps.executeUpdate() == 1;
        }
    }

    // ---------------- Helpers ----------------

    static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public static String format(double v) {
        double r = round3(v);
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }

    public static String money(double v) {
        return String.format(java.util.Locale.ROOT, "%,.2f", v);
    }

    /** Total of a set of lines at the given cost. */
    public static double total(Collection<Line> lines, boolean received) {
        double t = 0;
        for (Line l : lines) {
            t += received ? l.received * l.receivedCost : l.ordered * l.orderCost;
        }
        return t;
    }
}
