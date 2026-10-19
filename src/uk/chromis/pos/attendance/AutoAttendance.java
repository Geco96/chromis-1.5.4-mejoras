package uk.chromis.pos.attendance;

import java.awt.Color;
import java.awt.Component;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import uk.chromis.pos.sales.RestockToast;
import uk.chromis.pos.settings.PosSettings;

/**
 * Automatic check in / check out in Chromis' own attendance table (shifts),
 * so the "Check In / Check Out" screen and its reports keep working:
 * - check in on the first login after N hours without being checked in;
 * - check out when the user closes the cash.
 * Both are turned on or off in Chromis Admin → Asistencia automática.
 */
public final class AutoAttendance {

    public static final String CHECKIN_ENABLED = PosSettings.ATTENDANCE_CHECKIN;
    public static final String CHECKIN_HOURS = PosSettings.ATTENDANCE_HOURS;
    public static final String CHECKOUT_ENABLED = PosSettings.ATTENDANCE_CHECKOUT;
    public static final double DEFAULT_HOURS = 6;

    private static final Logger LOGGER = Logger.getLogger(AutoAttendance.class.getName());
    private static final Color CHECKIN_COLOR = new Color(0x2E, 0x7D, 0x32);
    private static final Color CHECKOUT_COLOR = new Color(0x15, 0x65, 0xC0);

    public enum Result {
        NOTHING, CHECKED_IN, CHECKED_OUT
    }

    private AutoAttendance() {
    }

    /** Called when a user opens the POS. */
    public static void onLogin(Connection con, String userId, Component owner) {
        try {
            if (checkInIfDue(con, userId, new Timestamp(System.currentTimeMillis())) == Result.CHECKED_IN) {
                notify(owner, "ENTRADA REGISTRADA", "Check in a las " + time(), CHECKIN_COLOR);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo registrar la entrada automática", ex);
        }
    }

    /** Called after the cash was closed. */
    public static void onCashClosed(Connection con, String userId, Component owner) {
        try {
            if (checkOutIfEnabled(con, userId, new Timestamp(System.currentTimeMillis())) == Result.CHECKED_OUT) {
                notify(owner, "SALIDA REGISTRADA", "Check out a las " + time(), CHECKOUT_COLOR);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo registrar la salida automática", ex);
        }
    }

    /**
     * Checks the user in when automatic check in is on, the user is not checked
     * in and the last check out was at least N hours ago (or never). A shift
     * left open since a previous day (no cash close) is first closed at the
     * user's last sale of that shift, or at its start when there was none.
     */
    public static Result checkInIfDue(Connection con, String userId, Timestamp now) throws SQLException {
        if (userId == null || !"1".equals(PosSettings.get(con, CHECKIN_ENABLED, "0"))) {
            return Result.NOTHING;
        }
        double hours = parseHours(PosSettings.get(con, CHECKIN_HOURS, String.valueOf(DEFAULT_HOURS)));

        String openId = null;
        Timestamp openStart = null;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id, startshift FROM shifts WHERE endshift IS NULL AND pplid = ? ORDER BY startshift DESC LIMIT 1")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    openId = rs.getString(1);
                    openStart = rs.getTimestamp(2);
                }
            }
        }
        if (openId != null) {
            LocalDate startDay = openStart.toLocalDateTime().toLocalDate();
            LocalDate today = now.toLocalDateTime().toLocalDate();
            if (!startDay.isBefore(today)) {
                return Result.NOTHING; // already checked in today
            }
            closeStaleShift(con, openId, userId, openStart);
        }

        Timestamp lastOut = null;
        try (PreparedStatement ps = con.prepareStatement("SELECT MAX(endshift) FROM shifts WHERE pplid = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    lastOut = rs.getTimestamp(1);
                }
            }
        }
        if (lastOut != null && now.getTime() - lastOut.getTime() < hours * 3_600_000L) {
            return Result.NOTHING;
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO shifts (id, startshift, pplid) VALUES (?, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setTimestamp(2, now);
            ps.setString(3, userId);
            ps.executeUpdate();
        }
        return Result.CHECKED_IN;
    }

    private static void closeStaleShift(Connection con, String shiftId, String userId, Timestamp start) throws SQLException {
        Timestamp end = start;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT MAX(datenew) FROM receipts WHERE person = ? AND datenew >= ? AND datenew < ?")) {
            ps.setString(1, userId);
            ps.setTimestamp(2, start);
            ps.setTimestamp(3, Timestamp.valueOf(start.toLocalDateTime().toLocalDate().plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getTimestamp(1) != null) {
                    end = rs.getTimestamp(1);
                }
            }
        }
        closeShift(con, shiftId, end);
    }

    /** Checks the user out when automatic check out is on and the user is checked in. */
    public static Result checkOutIfEnabled(Connection con, String userId, Timestamp now) throws SQLException {
        if (userId == null || !"1".equals(PosSettings.get(con, CHECKOUT_ENABLED, "0"))) {
            return Result.NOTHING;
        }
        String openId = null;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id FROM shifts WHERE endshift IS NULL AND pplid = ? ORDER BY startshift DESC LIMIT 1")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    openId = rs.getString(1);
                }
            }
        }
        if (openId == null) {
            return Result.NOTHING;
        }
        closeShift(con, openId, now);
        return Result.CHECKED_OUT;
    }

    /** Ends the shift and any break still open in it, as Chromis' own check out does. */
    private static void closeShift(Connection con, String shiftId, Timestamp end) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE shift_breaks SET endtime = ?, starttime = starttime WHERE endtime IS NULL AND shiftid = ?")) {
            ps.setTimestamp(1, end);
            ps.setString(2, shiftId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE shifts SET endshift = ?, startshift = startshift WHERE id = ?")) {
            ps.setTimestamp(1, end);
            ps.setString(2, shiftId);
            ps.executeUpdate();
        }
    }

    static double parseHours(String text) {
        try {
            double h = Double.parseDouble(text.trim().replace(',', '.'));
            return h >= 0 ? h : DEFAULT_HOURS;
        } catch (RuntimeException ex) {
            return DEFAULT_HOURS;
        }
    }

    private static String time() {
        return new SimpleDateFormat("HH:mm").format(new java.util.Date());
    }

    private static void notify(Component owner, String title, String message, Color color) {
        SwingUtilities.invokeLater(() -> RestockToast.show(owner, title, message, color));
    }
}
