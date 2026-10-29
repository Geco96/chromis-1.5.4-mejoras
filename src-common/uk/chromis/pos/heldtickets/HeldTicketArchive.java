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
 * leaves the list, so it can be put back and so it is known where it went.
 *
 * Chromis deletes held tickets in several places: "borrar todas", the bin of
 * an opened held ticket, and also when a held ticket is opened (it is out of
 * the list of every till until the cashier moves on and it is saved again; a
 * crash at that moment lost it). A trigger keeps a copy on every delete; when
 * the same held ticket is saved again with its products its copy goes away.
 * The till fills in what the ticket contains (only Chromis POS can read it),
 * which till opened it and whether it is still open there; charged ones keep
 * only that summary.
 *
 * The triggers never make Chromis fail: if the copy cannot be written, the
 * held ticket is deleted or saved as without them.
 */
public final class HeldTicketArchive {

    public static final String TABLE = "sharedtickets_deleted";
    static final String TRIGGER_DELETE = "sharedtickets_guardar_borrada";
    static final String TRIGGER_INSERT = "sharedtickets_quitar_borrada";
    /** Class of the lines of a ticket: its name is in the content only when the ticket has lines. */
    static final String LINE_CLASS = "uk.chromis.pos.ticket.TicketLineInfo";
    /** Copies are kept this many days. */
    public static final int KEEP_DAYS = 60;
    /** A till marks the held ticket it has open every minute; older than this, it is not open anymore. */
    public static final int OPEN_MINUTES = 3;
    /** Seconds a change of the tables waits for a backup in progress before giving up (the tills wait meanwhile). */
    static final int DDL_WAIT_SECONDS = 5;

    /** summarized: not read yet by a till, read, or unreadable. */
    public static final int PENDING = 0;
    public static final int READ = 1;
    public static final int UNREADABLE = 2;

    /** Where the held ticket went. */
    public enum How {
        DELETE_ALL("Borrar todas (clic derecho / F10)", "Borrar todas (clic derecho en el botón de ventas en espera o F10)"),
        BIN("Con el bote de basura", "Borrada con el bote de basura"),
        EMPTIED("Le quitaron los productos", "Se abrió y le quitaron los productos uno por uno"),
        OPEN("Abierta en una caja", "Está abierta en una caja; vuelve a la lista cuando la cajera pasa a otra venta o sale"),
        NOT_SAVED("Sin registro de borrado", "Se abrió y no se volvió a guardar (Chromis se cerró o se fue la luz con ella abierta)"),
        CHARGED("Cobrada", "Se cobró");

        /** Short, for a table column. */
        public final String label;
        public final String text;

        How(String label, String text) {
            this.label = label;
            this.text = text;
        }
    }

    /** One held ticket that left the list. */
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
        /** Chromis user and terminal that opened it (tills with this version). */
        public String openedBy;
        public String openedTerminal;
        /** It is in the held tickets list again. */
        public boolean backInList;
        /** No till has read it yet: lines and total come from what Chromis audited, if anything. */
        public boolean fromAudit;
        /** Charged: ticket number, who and when. */
        public Integer chargedTicket;
        public String chargedBy;
        public Timestamp chargedAt;

        /** It can be put back in the list (not open in a till, not charged, its content kept). */
        public boolean restorable() {
            return how != How.OPEN && how != How.CHARGED && !backInList;
        }
    }

    private HeldTicketArchive() {
    }

    /**
     * Creates the table, its columns and the triggers when missing or older.
     * Returns null, or why the copies cannot be kept (e.g. the database user
     * may not create triggers, or a backup was running: then it is tried
     * again later).
     */
    public static String ensure(Connection con) {
        Integer wait = null;
        try (Statement st = con.createStatement()) {
            // A change of a table waits for a backup in progress and every till waits behind it: not for long
            try (ResultSet rs = st.executeQuery("SELECT @@SESSION.lock_wait_timeout")) {
                wait = rs.next() ? rs.getInt(1) : null;
            }
            st.execute("SET SESSION lock_wait_timeout = " + DDL_WAIT_SECONDS);
            if (!hasTable(con)) {
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
                        + "opened_by VARCHAR(100), "
                        + "opened_terminal VARCHAR(100), "
                        + "seen_open_at TIMESTAMP NULL DEFAULT NULL, "
                        + "KEY idx_id (id), KEY idx_ticket (ticket_id), KEY idx_deleted (deleted_at)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            } else if (!hasColumn(con, "seen_open_at")) {
                st.execute("ALTER TABLE " + TABLE + " ADD COLUMN opened_by VARCHAR(100), ADD COLUMN opened_terminal VARCHAR(100), "
                        + "ADD COLUMN seen_open_at TIMESTAMP NULL DEFAULT NULL");
            }
            // The copy is written whatever happens to it: an error there must never stop Chromis from deleting
            // or saving a held ticket. Earlier versions of the triggers had no such guard; they are replaced.
            String body = triggerBody(con, TRIGGER_DELETE);
            if (body == null || !body.contains("SQLEXCEPTION")) {
                replaceTrigger(st, body != null, TRIGGER_DELETE, "BEFORE DELETE ON sharedtickets FOR EACH ROW BEGIN "
                        + "DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END; "
                        + "INSERT INTO " + TABLE + " (id, name, content, pickupid, appuser, loyaltycard, pickupbarcode, "
                        + "deliveryinfo, deleted_at, db_user) VALUES (OLD.id, OLD.name, OLD.content, OLD.pickupid, OLD.appuser, "
                        + "OLD.loyaltycard, OLD.pickupbarcode, OLD.deliveryinfo, NOW(), LEFT(USER(), 100)); END");
            }
            // The copy goes away when the ticket is held again, but not when what comes back is empty (the products
            // were removed one by one): Chromis saves it and deletes it right away, and the copy with the products
            // must stay.
            body = triggerBody(con, TRIGGER_INSERT);
            if (body == null || !body.contains("SQLEXCEPTION") || !body.contains("TicketLineInfo")) {
                replaceTrigger(st, body != null, TRIGGER_INSERT, "AFTER INSERT ON sharedtickets FOR EACH ROW BEGIN "
                        + "DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END; "
                        + "IF LOCATE('" + LINE_CLASS + "', NEW.content) > 0 THEN "
                        + "DELETE FROM " + TABLE + " WHERE id = NEW.id; END IF; END");
            }
            return null;
        } catch (SQLException ex) {
            if (ex.getErrorCode() == 1205) {
                return "Un respaldo en curso no dejó preparar la copia de las ventas en espera; se intenta de nuevo en un momento.";
            }
            return "No se pudo activar la copia de las ventas en espera borradas: " + ex.getMessage();
        } finally {
            if (wait != null) {
                try (Statement st = con.createStatement()) {
                    st.execute("SET SESSION lock_wait_timeout = " + wait);
                } catch (SQLException ex) {
                    // the session keeps the short wait; harmless
                }
            }
        }
    }

    /** Replaces a trigger in one step (MariaDB); where that is not understood, drops and creates it. */
    private static void replaceTrigger(Statement st, boolean exists, String name, String definition) throws SQLException {
        if (!exists) {
            st.execute("CREATE TRIGGER " + name + " " + definition);
            return;
        }
        try {
            st.execute("CREATE OR REPLACE TRIGGER " + name + " " + definition);
        } catch (SQLException ex) {
            if (ex.getErrorCode() != 1064) {
                throw ex;
            }
            st.execute("DROP TRIGGER " + name);
            st.execute("CREATE TRIGGER " + name + " " + definition);
        }
    }

    private static boolean hasTable(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = ?")) {
            ps.setString(1, TABLE);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean hasColumn(Connection con, String column) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            ps.setString(1, TABLE);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
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
        return triggerBody(con, name) != null;
    }

    /** Copies the till has not read yet: archive id and content. */
    public static List<Object[]> pending(Connection con, int max) throws SQLException {
        List<Object[]> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT archive_id, content FROM " + TABLE
                + " WHERE summarized = " + PENDING + " AND content IS NOT NULL ORDER BY archive_id LIMIT ?")) {
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

    /** The till that just opened this held ticket (its copy was made by the trigger a moment ago). */
    public static void opened(Connection con, String id, String user, String terminal) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE " + TABLE + " SET opened_by = ?, opened_terminal = ?, "
                + "seen_open_at = NOW() WHERE id = ? ORDER BY archive_id DESC LIMIT 1")) {
            ps.setString(1, cut(user, 100));
            ps.setString(2, cut(terminal, 100));
            ps.setString(3, id);
            ps.executeUpdate();
        }
    }

    /** The till still has this held ticket open (every minute); nothing happens if it is not one. */
    public static void stillOpen(Connection con, String id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE " + TABLE + " SET seen_open_at = NOW() "
                + "WHERE id = ? ORDER BY archive_id DESC LIMIT 1")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Copies older than KEEP_DAYS go away, and charged ones keep only their
     * summary (they cannot be put back). Only this table is locked: the
     * tickets are read without locks, so a sale is never held up.
     */
    public static void purge(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("DELETE FROM " + TABLE + " WHERE deleted_at < NOW() - INTERVAL " + KEEP_DAYS + " DAY");
        }
        List<Long> charged = new ArrayList<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SELECT d.archive_id FROM " + TABLE + " d "
                + "WHERE d.content IS NOT NULL AND d.summarized <> " + PENDING + " AND d.ticket_id IS NOT NULL "
                + "AND EXISTS (SELECT 1 FROM tickets t WHERE t.id = d.ticket_id)")) {
            while (rs.next()) {
                charged.add(rs.getLong(1));
            }
        }
        for (Long a : charged) {
            try (PreparedStatement ps = con.prepareStatement("UPDATE " + TABLE + " SET content = NULL, deliveryinfo = NULL "
                    + "WHERE archive_id = ?")) {
                ps.setLong(1, a);
                ps.executeUpdate();
            }
        }
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
     * every time the cashier moves on); empty ones are dropped.
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
        try (Statement st = con.createStatement()) {
            st.executeUpdate("DELETE FROM " + TABLE + " WHERE deleted_at < NOW() - INTERVAL " + KEEP_DAYS + " DAY");
        }
    }

    /**
     * Held tickets that left the list in the last [days] days, newest first
     * (only the last copy of each); charged ones only if asked.
     */
    public static List<Entry> list(Connection con, int days, boolean withCharged) throws SQLException {
        identify(con);
        List<Entry> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("SELECT d.archive_id, d.id, d.name, d.appuser, d.deleted_at, d.db_user, "
                + "d.summarized, d.ticket_id, d.customer, d.total, d.line_count, d.line_text, "
                // In the list again: the same held ticket (its ticket number is inside the content)
                + "CASE WHEN d.ticket_id IS NULL THEN (SELECT COUNT(*) FROM sharedtickets s WHERE s.id = d.id) "
                + "ELSE (SELECT COUNT(*) FROM sharedtickets s WHERE LOCATE(d.ticket_id, s.content) > 0) END, "
                + "d.opened_by, d.opened_terminal, d.seen_open_at >= NOW() - INTERVAL " + OPEN_MINUTES + " MINUTE, "
                + "t.ticketid, p.name, r.datenew, d.content IS NULL "
                + "FROM " + TABLE + " d "
                + "LEFT JOIN tickets t ON t.id = d.ticket_id LEFT JOIN receipts r ON r.id = t.id LEFT JOIN people p ON p.id = t.person "
                + "WHERE d.deleted_at >= NOW() - INTERVAL ? DAY "
                + "AND d.archive_id = (SELECT MAX(x.archive_id) FROM " + TABLE + " x WHERE x.id = d.id) "
                + (withCharged ? "" : "AND t.id IS NULL ")
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
                    e.openedBy = rs.getString(14);
                    e.openedTerminal = rs.getString(15);
                    boolean openNow = rs.getBoolean(16);
                    int number = rs.getInt(17);
                    e.chargedTicket = rs.wasNull() ? null : number;
                    e.chargedBy = rs.getString(18);
                    e.chargedAt = rs.getTimestamp(19);
                    boolean noContent = rs.getBoolean(20);
                    if (e.summarized == READ && e.lineCount == 0) {
                        continue;
                    }
                    e.how = e.chargedTicket != null ? How.CHARGED : openNow ? How.OPEN : How.NOT_SAVED;
                    if (e.backInList && e.how != How.CHARGED) {
                        // Saved again (also under another id, when its own was taken): it is not lost
                        continue;
                    }
                    if (noContent && e.how != How.CHARGED) {
                        continue;
                    }
                    list.add(e);
                }
            }
        }
        for (Entry e : list) {
            if (e.ticketId == null || e.how == How.OPEN) {
                continue;
            }
            // Chromis writes each line of a held ticket it deletes in lineremoved, with the user and the terminal:
            // "borrar todas" just before the delete, the bin after the ticket was opened (the copy is made when it is
            // opened). Older lines are from an earlier deletion of the same ticket (it was restored since).
            if (e.how == How.NOT_SAVED) {
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
            if (e.summarized != READ && (e.how == How.DELETE_ALL || e.how == How.BIN || e.how == How.EMPTIED)) {
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
        if (content == null) {
            throw new SQLException("Esa venta se cobró; no se puede regresar a las ventas en espera.");
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

    /** Changes whenever a held ticket is added or removed (no length limit, unlike GROUP_CONCAT). */
    public static String listSignature(Connection con) throws SQLException {
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*), COALESCE(SUM(CRC32(id)), 0), COALESCE(BIT_XOR(CRC32(id)), 0) FROM sharedtickets")) {
            return rs.next() ? rs.getInt(1) + ":" + rs.getString(2) + ":" + rs.getString(3) : "";
        }
    }

    static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
