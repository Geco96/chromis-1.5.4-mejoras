package uk.chromis.pos.heldtickets;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copy of every held ticket (sharedtickets, the "carrito" of the till) that
 * leaves the list without being charged, so it can be put back.
 *
 * Chromis deletes held tickets in several places: "borrar todas" (right click
 * on the held tickets button or F10), the bin of an opened held ticket, and
 * also when a held ticket is opened (it is saved again when the cashier moves
 * on; a crash at that moment loses it). A trigger keeps a copy on every
 * delete; when the same held ticket is saved again its copy goes away. The
 * till fills in what the ticket contains (only Chromis POS can read it) and
 * drops copies of tickets that were charged or empty, so what stays is what
 * was really lost.
 */
public final class HeldTicketArchive {

    public static final String TABLE = "sharedtickets_deleted";
    static final String TRIGGER_DELETE = "sharedtickets_guardar_borrada";
    static final String TRIGGER_INSERT = "sharedtickets_quitar_borrada";
    /** Class of the lines of a ticket: its name is in the content only when the ticket has lines. */
    static final String LINE_CLASS = "uk.chromis.pos.ticket.TicketLineInfo";
    /** Copies are kept this many days. */
    public static final int KEEP_DAYS = 60;

    /** summarized: not read yet by a till, read, or unreadable. */
    public static final int PENDING = 0;
    public static final int READ = 1;
    public static final int UNREADABLE = 2;

    /** How the held ticket left the list, from what Chromis audits in lineremoved. */
    public enum How {
        DELETE_ALL("Borrar todas (clic derecho / F10)", "Borrar todas (clic derecho en el botón de ventas en espera o F10)"),
        BIN("Con el bote de basura", "Borrada con el bote de basura"),
        EMPTIED("Le quitaron los productos", "Se abrió y le quitaron los productos uno por uno"),
        NOT_SAVED("Sin registro de borrado", "Se abrió y no se volvió a guardar (cierre de Chromis o apagón), o sigue abierta en una caja");

        /** Short, for a table column. */
        public final String label;
        public final String text;

        How(String label, String text) {
            this.label = label;
            this.text = text;
        }
    }

    /** One deleted held ticket. */
    public static final class Entry {

        public long archiveId;
        public String id;
        public String name;
        public String appUser;
        public Timestamp deletedAt;
        /** Database user and PC (user@host) that deleted it. */
        public String dbUser;
        public int summarized;
        public String ticketId;
        public String customer;
        public double total;
        public int lineCount;
        public String lines;
        public How how;
        /** Chromis user and terminal that deleted it, when Chromis audited it. */
        public String deletedBy;
        public String terminal;
        /** A held ticket with the same id is in the list again. */
        public boolean backInList;
        /** No till has read it yet: lines and total come from what Chromis audited, if anything. */
        public boolean fromAudit;
    }

    private HeldTicketArchive() {
    }

    /**
     * Creates the table and the triggers when missing. Returns null, or why
     * the copies cannot be kept (e.g. the database user may not create
     * triggers).
     */
    public static String ensure(Connection con) {
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "archive_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "id VARCHAR(40) NOT NULL, "
                    + "name VARCHAR(255), "
                    + "content MEDIUMBLOB, "
                    + "pickupid INT NOT NULL DEFAULT 0, "
                    + "appuser VARCHAR(40), "
                    + "loyaltycard VARCHAR(50), "
                    + "pickupbarcode VARCHAR(20), "
                    + "deliveryinfo LONGBLOB, "
                    + "deleted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "db_user VARCHAR(100), "
                    + "summarized TINYINT NOT NULL DEFAULT 0, "
                    + "ticket_id VARCHAR(40), "
                    + "customer VARCHAR(255), "
                    + "total DOUBLE, "
                    + "line_count INT, "
                    + "line_text TEXT, "
                    + "KEY idx_id (id), KEY idx_ticket (ticket_id), KEY idx_deleted (deleted_at)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (SQLException ex) {
            return "No se pudo crear la tabla " + TABLE + ": " + ex.getMessage();
        }
        try (Statement st = con.createStatement()) {
            if (!hasTrigger(con, TRIGGER_DELETE)) {
                st.execute("CREATE TRIGGER " + TRIGGER_DELETE + " BEFORE DELETE ON sharedtickets FOR EACH ROW "
                        + "INSERT INTO " + TABLE + " (id, name, content, pickupid, appuser, loyaltycard, pickupbarcode, "
                        + "deliveryinfo, deleted_at, db_user) VALUES (OLD.id, OLD.name, OLD.content, OLD.pickupid, OLD.appuser, "
                        + "OLD.loyaltycard, OLD.pickupbarcode, OLD.deliveryinfo, NOW(), USER())");
            }
            // The copy goes away when the ticket is held again, but not when what comes back is empty (the products
            // were removed one by one): Chromis saves it and deletes it right away, and the copy with the products
            // must stay. The first version deleted it always; it is replaced.
            String body = triggerBody(con, TRIGGER_INSERT);
            if (body != null && !body.contains("TicketLineInfo")) {
                st.execute("DROP TRIGGER " + TRIGGER_INSERT);
                body = null;
            }
            if (body == null) {
                st.execute("CREATE TRIGGER " + TRIGGER_INSERT + " AFTER INSERT ON sharedtickets FOR EACH ROW "
                        + "BEGIN IF LOCATE('" + LINE_CLASS + "', NEW.content) > 0 THEN "
                        + "DELETE FROM " + TABLE + " WHERE id = NEW.id; END IF; END");
            }
        } catch (SQLException ex) {
            return "No se pudo activar la copia de las ventas en espera borradas: " + ex.getMessage();
        }
        return null;
    }

    /** Statement of a trigger of this database, or null if there is none. */
    static String triggerBody(Connection con, String name) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT action_statement FROM information_schema.triggers "
                + "WHERE trigger_schema = DATABASE() AND trigger_name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    static boolean hasTrigger(Connection con, String name) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM information_schema.triggers "
                + "WHERE trigger_schema = DATABASE() AND trigger_name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Copies the till has not read yet: archive id and content. */
    public static List<Object[]> pending(Connection con, int max) throws SQLException {
        List<Object[]> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT archive_id, content FROM " + TABLE
                + " WHERE summarized = " + PENDING + " ORDER BY archive_id LIMIT ?")) {
            ps.setInt(1, max);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Object[]{rs.getLong(1), rs.getBytes(2)});
                }
            }
        }
        return list;
    }

    /** What the till read from the copy. A ticket with no lines is not worth keeping. */
    public static void summarize(Connection con, long archiveId, String ticketId, String customer, double total,
            int lineCount, String lineText) throws SQLException {
        if (lineCount == 0) {
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + TABLE + " WHERE archive_id = ?")) {
                ps.setLong(1, archiveId);
                ps.executeUpdate();
            }
            return;
        }
        try (PreparedStatement ps = con.prepareStatement("UPDATE " + TABLE + " SET summarized = " + READ
                + ", ticket_id = ?, customer = ?, total = ?, line_count = ?, line_text = ? WHERE archive_id = ?")) {
            ps.setString(1, ticketId);
            ps.setString(2, cut(customer, 255));
            ps.setDouble(3, total);
            ps.setInt(4, lineCount);
            ps.setString(5, cut(lineText, 60000));
            ps.setLong(6, archiveId);
            ps.executeUpdate();
        }
    }

    public static void unreadable(Connection con, long archiveId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE " + TABLE + " SET summarized = " + UNREADABLE
                + " WHERE archive_id = ?")) {
            ps.setLong(1, archiveId);
            ps.executeUpdate();
        }
    }

    /** Drops copies of tickets that were charged after all, and copies older than KEEP_DAYS. Returns how many. */
    public static int purge(Connection con) throws SQLException {
        int n = 0;
        try (Statement st = con.createStatement()) {
            n += st.executeUpdate("DELETE d FROM " + TABLE + " d JOIN tickets t ON t.id = d.ticket_id");
            n += st.executeUpdate("DELETE FROM " + TABLE + " WHERE deleted_at < NOW() - INTERVAL " + KEEP_DAYS + " DAY");
        }
        return n;
    }

    private static final Pattern UUID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /**
     * Ticket id of a held ticket without opening it (only Chromis POS has the
     * class): TicketInfo writes its id first, so it is the first UUID of the
     * content. Null if there is none.
     */
    public static String ticketIdOf(byte[] content) {
        if (content == null) {
            return null;
        }
        Matcher m = UUID.matcher(new String(content, StandardCharsets.ISO_8859_1));
        return m.find() ? m.group() : null;
    }

    /** False for a ticket with no lines: the class of the lines only appears in the content when there is one. */
    public static boolean hasLines(byte[] content) {
        return content != null && new String(content, StandardCharsets.ISO_8859_1).contains(LINE_CLASS);
    }

    /**
     * What can be known of the copies no till has read yet: their ticket id,
     * and whether they were empty (Chromis saves and deletes an empty ticket
     * every time the cashier moves on) or charged; those are dropped.
     */
    public static void identify(Connection con) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT archive_id, content FROM " + TABLE
                + " WHERE summarized = " + PENDING + " AND ticket_id IS NULL")) {
            while (rs.next()) {
                rows.add(new Object[]{rs.getLong(1), rs.getBytes(2)});
            }
        }
        for (Object[] r : rows) {
            byte[] content = (byte[]) r[1];
            if (!hasLines(content)) {
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + TABLE + " WHERE archive_id = ?")) {
                    ps.setLong(1, (Long) r[0]);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = con.prepareStatement("UPDATE " + TABLE + " SET ticket_id = ? WHERE archive_id = ?")) {
                    ps.setString(1, ticketIdOf(content));
                    ps.setLong(2, (Long) r[0]);
                    ps.executeUpdate();
                }
            }
        }
        purge(con);
    }

    /** Deleted held tickets of the last [days] days, newest first (only the last copy of each). */
    public static List<Entry> list(Connection con, int days) throws SQLException {
        identify(con);
        List<Entry> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT d.archive_id, d.id, d.name, d.appuser, d.deleted_at, d.db_user, "
                + "d.summarized, d.ticket_id, d.customer, d.total, d.line_count, d.line_text, "
                + "(SELECT COUNT(*) FROM sharedtickets s WHERE s.id = d.id) "
                + "FROM " + TABLE + " d WHERE d.deleted_at >= NOW() - INTERVAL ? DAY "
                + "AND d.archive_id = (SELECT MAX(x.archive_id) FROM " + TABLE + " x WHERE x.id = d.id) "
                + "AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.id = d.ticket_id) "
                + "ORDER BY d.deleted_at DESC, d.archive_id DESC")) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Entry e = new Entry();
                    e.archiveId = rs.getLong(1);
                    e.id = rs.getString(2);
                    e.name = rs.getString(3);
                    e.appUser = rs.getString(4);
                    e.deletedAt = rs.getTimestamp(5);
                    e.dbUser = rs.getString(6);
                    e.summarized = rs.getInt(7);
                    e.ticketId = rs.getString(8);
                    e.customer = rs.getString(9);
                    e.total = rs.getDouble(10);
                    e.lineCount = rs.getInt(11);
                    e.lines = rs.getString(12);
                    e.backInList = rs.getInt(13) > 0;
                    if (e.summarized == READ && e.lineCount == 0) {
                        continue;
                    }
                    list.add(e);
                }
            }
        }
        for (Entry e : list) {
            e.how = How.NOT_SAVED;
            if (e.ticketId == null) {
                continue;
            }
            // Chromis writes each line of a held ticket it deletes in lineremoved, with the user and the terminal:
            // "borrar todas" just before the delete, the bin after the ticket was opened (the copy is made when it is
            // opened). Older lines are from an earlier deletion of the same ticket (it was restored since).
            try (PreparedStatement ps = con.prepareStatement("SELECT description, name, terminalid FROM lineremoved "
                    + "WHERE ticketid = ? AND description IN ('Shared Ticket Removed', 'Full Ticket Removed') "
                    + "AND removeddate >= ? - INTERVAL 1 MINUTE ORDER BY removeddate DESC LIMIT 1")) {
                ps.setString(1, e.ticketId);
                ps.setTimestamp(2, e.deletedAt);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        e.how = "Shared Ticket Removed".equals(rs.getString(1)) ? How.DELETE_ALL : How.BIN;
                        e.deletedBy = rs.getString(2);
                        e.terminal = rs.getString(3);
                    }
                }
            }
            if (e.how == How.NOT_SAVED) {
                try (PreparedStatement ps = con.prepareStatement("SELECT name, terminalid FROM lineremoved "
                        + "WHERE ticketid = ? AND description = 'Single Item Removed' AND removeddate >= ? - INTERVAL 1 MINUTE "
                        + "ORDER BY removeddate DESC LIMIT 1")) {
                    ps.setString(1, e.ticketId);
                    ps.setTimestamp(2, e.deletedAt);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            e.how = How.EMPTIED;
                            e.deletedBy = rs.getString(1);
                            e.terminal = rs.getString(2);
                        }
                    }
                }
            }
            if (e.summarized != READ && e.how != How.NOT_SAVED) {
                linesFromAudit(con, e);
            }
        }
        return list;
    }

    /** Lines and total of a copy no till has read, from the lines Chromis audited when it was deleted. */
    private static void linesFromAudit(Connection con, Entry e) throws SQLException {
        StringBuilder text = new StringBuilder();
        int count = 0;
        double total = 0;
        try (PreparedStatement ps = con.prepareStatement("SELECT productname, units, value FROM lineremoved "
                + "WHERE ticketid = ? AND description = ? AND removeddate >= ? - INTERVAL 1 MINUTE ORDER BY sequence")) {
            ps.setString(1, e.ticketId);
            ps.setString(2, e.how == How.DELETE_ALL ? "Shared Ticket Removed" : e.how == How.BIN ? "Full Ticket Removed" : "Single Item Removed");
            ps.setTimestamp(3, e.deletedAt);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double u = rs.getDouble(2);
                    double v = rs.getDouble(3) * u;
                    text.append(u == Math.rint(u) ? String.valueOf((long) u) : String.valueOf(u)).append(" x ")
                            .append(rs.getString(1) == null ? "" : rs.getString(1))
                            .append(String.format(java.util.Locale.ROOT, "  $%.2f", v)).append('\n');
                    total += v;
                    count++;
                }
            }
        }
        if (count > 0) {
            e.fromAudit = true;
            e.lineCount = count;
            e.total = total;
            e.lines = text.toString().trim();
        }
    }

    /**
     * Puts the copy back in the held tickets list of every till, as a new
     * held ticket (new id and new ticket number inside), so it never clashes
     * with the same ticket if a till still has it open: that till saves its
     * own version and both stay. Returns the new id.
     */
    public static String restore(Connection con, long archiveId) throws SQLException {
        String name;
        byte[] content;
        Object[] rest;
        byte[] delivery;
        try (PreparedStatement ps = con.prepareStatement("SELECT name, content, pickupid, appuser, loyaltycard, deliveryinfo "
                + "FROM " + TABLE + " WHERE archive_id = ?")) {
            ps.setLong(1, archiveId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Esa venta ya no está en las borradas (alguien la restauró o se volvió a guardar).");
                }
                name = rs.getString(1);
                content = rs.getBytes(2);
                rest = new Object[]{rs.getInt(3), rs.getString(4), rs.getString(5)};
                delivery = rs.getBytes(6);
            }
        }
        String oldTicket = ticketIdOf(content);
        String newTicket = java.util.UUID.randomUUID().toString();
        String newId = java.util.UUID.randomUUID().toString();
        String label = name == null ? RESTORED : name.endsWith(RESTORED) ? name : name + RESTORED;
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO sharedtickets (id, name, content, pickupid, appuser, "
                    + "loyaltycard, pickupbarcode, deliveryinfo) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, newId);
                ps.setString(2, cut(label, 255));
                ps.setBytes(3, replace(content, oldTicket, newTicket));
                ps.setObject(4, rest[0]);
                ps.setObject(5, rest[1]);
                ps.setObject(6, rest[2]);
                // Chromis builds the pickup barcode the same way from the ticket number
                ps.setString(7, "P" + newTicket.substring(24));
                ps.setBytes(8, replace(delivery, oldTicket, newTicket));
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + TABLE + " WHERE archive_id = ?")) {
                ps.setLong(1, archiveId);
                ps.executeUpdate();
            }
            con.commit();
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
        return newId;
    }

    /** Added to the name of a restored held ticket, so the cashier tells it apart. */
    public static final String RESTORED = " (restaurada)";

    /**
     * Every copy of [from] in [data] replaced by [to], of the same length (a
     * UUID), so the serialized ticket stays valid.
     */
    public static byte[] replace(byte[] data, String from, String to) {
        if (data == null || from == null || from.length() != to.length()) {
            return data;
        }
        byte[] f = from.getBytes(StandardCharsets.ISO_8859_1);
        byte[] t = to.getBytes(StandardCharsets.ISO_8859_1);
        byte[] out = data.clone();
        for (int i = 0; i <= out.length - f.length; i++) {
            boolean match = true;
            for (int j = 0; j < f.length && match; j++) {
                match = out[i + j] == f[j];
            }
            if (match) {
                System.arraycopy(t, 0, out, i, t.length);
                i += f.length - 1;
            }
        }
        return out;
    }

    /** Changes whenever a held ticket is added or removed. */
    public static String listSignature(Connection con) throws SQLException {
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*), COALESCE(MD5(GROUP_CONCAT(id ORDER BY id)), '') FROM sharedtickets")) {
            return rs.next() ? rs.getInt(1) + ":" + rs.getString(2) : "";
        }
    }

    static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
