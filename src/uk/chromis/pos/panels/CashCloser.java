package uk.chromis.pos.panels;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Closing the cash in one transaction. Chromis closed the current cash, then
 * switched the terminal to a new one, then inserted it, each step on its own:
 * when the insert failed (e.g. a repeated sequence number) the old cash was
 * already closed and the terminal pointed to a cash that did not exist, and
 * when the first step failed the new cash was opened anyway, leaving the
 * sales of the old one outside every cash report. Here either everything is
 * done or nothing changes.
 */
public final class CashCloser {

    private static final Logger LOGGER = Logger.getLogger(CashCloser.class.getName());
    /** Errors of the cash close, kept so they can be read later. */
    public static final String LOG_FILE = "chromis-cortes.log";

    public static final class Result {

        public final String money;
        public final int sequence;
        public final Date start;

        Result(String money, int sequence, Date start) {
            this.money = money;
            this.sequence = sequence;
            this.start = start;
        }
    }

    private CashCloser() {
    }

    /**
     * Closes the cash (when it is still open) and opens the next one for the
     * host, with the next free sequence number read from the database, and
     * points the terminal to it.
     */
    public static Result close(Connection con, String money, String host, String terminalKey, Date now, Integer noSales)
            throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            boolean exists = false;
            boolean open = false;
            try (PreparedStatement ps = con.prepareStatement("SELECT dateend FROM closedcash WHERE money = ? FOR UPDATE")) {
                ps.setString(1, money);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        exists = true;
                        open = rs.getTimestamp(1) == null;
                    }
                }
            }
            if (open) {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE closedcash SET dateend = ?, nosales = ? WHERE money = ? AND dateend IS NULL")) {
                    ps.setTimestamp(1, new Timestamp(now.getTime()));
                    ps.setInt(2, noSales == null ? 0 : noSales);
                    ps.setString(3, money);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("El corte actual no se pudo marcar como cerrado");
                    }
                }
            } else if (exists) {
                LOGGER.log(Level.INFO, "El corte {0} ya estaba cerrado; solo se abre uno nuevo", money);
            }
            Result result = open(con, host, terminalKey, now);
            con.commit();
            return result;
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    /** Inserts the next cash of the host and points the terminal to it (inside the caller's transaction). */
    private static Result open(Connection con, String host, String terminalKey, Date now) throws SQLException {
        int sequence;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(MAX(hostsequence), 0) + 1 FROM closedcash WHERE host = ? FOR UPDATE")) {
            ps.setString(1, host);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                sequence = rs.getInt(1);
            }
        }
        String money = UUID.randomUUID().toString();
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO closedcash (money, host, hostsequence, datestart, dateend) VALUES (?, ?, ?, ?, NULL)")) {
            ps.setString(1, money);
            ps.setString(2, host);
            ps.setInt(3, sequence);
            ps.setTimestamp(4, new Timestamp(now.getTime()));
            ps.executeUpdate();
        }
        if (terminalKey != null) {
            try (PreparedStatement ps = con.prepareStatement("UPDATE terminals SET active_cash = ? WHERE terminal_key = ?")) {
                ps.setString(1, money);
                ps.setString(2, terminalKey);
                ps.executeUpdate();
            }
        }
        return new Result(money, sequence, now);
    }

    /**
     * Makes sure the terminal's cash exists and is open. Chromis creates a
     * new cash at start-up with a sequence number already taken and hides the
     * error, leaving the terminal on a cash that is not in the database (no
     * sale could be saved). Returns the new cash, or null when nothing was
     * wrong.
     */
    public static Result ensureOpenCash(Connection con, String money, String host, String terminalKey) throws SQLException {
        if (money != null) {
            try (PreparedStatement ps = con.prepareStatement("SELECT dateend FROM closedcash WHERE money = ?")) {
                ps.setString(1, money);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getTimestamp(1) == null) {
                        return null;
                    }
                }
            }
        }
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            Result result = open(con, host, terminalKey, new Date());
            con.commit();
            return result;
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    /** Last sale of a cash, or its start when it has none: the end given to a cash left open by an error. */
    public static Date lastActivity(Connection con, String money) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COALESCE((SELECT MAX(datenew) FROM receipts WHERE money = ?), "
                + "(SELECT datestart FROM closedcash WHERE money = ?))")) {
            ps.setString(1, money);
            ps.setString(2, money);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getTimestamp(1) != null ? new Date(rs.getTimestamp(1).getTime()) : new Date();
            }
        }
    }

    /** Closes a cash that was left open (not the active cash of any terminal). */
    public static boolean closeLeftOpen(Connection con, String money, Date end) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE closedcash SET dateend = ? WHERE money = ? AND dateend IS NULL "
                + "AND NOT EXISTS (SELECT 1 FROM terminals t WHERE t.active_cash = ?)")) {
            ps.setTimestamp(1, new Timestamp(end.getTime()));
            ps.setString(2, money);
            ps.setString(3, money);
            return ps.executeUpdate() == 1;
        }
    }

    /** Appends the error to chromis-cortes.log in the user's folder, so it can be read after the fact. */
    public static Path log(String what, Throwable error) {
        LOGGER.log(Level.SEVERE, what, error);
        Path file = Paths.get(System.getProperty("user.home"), LOG_FILE);
        try (PrintWriter out = new PrintWriter(new FileWriter(file.toFile(), true))) {
            out.println("==== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " " + what);
            if (error != null) {
                error.printStackTrace(out);
            }
            out.println();
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "No se pudo escribir " + file, ex);
        }
        return file;
    }
}
