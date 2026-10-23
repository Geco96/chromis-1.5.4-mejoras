import java.sql.*;
import java.time.LocalDateTime;
import uk.chromis.pos.attendance.AutoAttendance;
import uk.chromis.pos.attendance.AutoAttendance.Result;
import uk.chromis.pos.settings.PosSettings;

/**
 * Prueba de la asistencia automática contra una base Chromis 1.5.4, con un
 * usuario de prueba (no toca los usuarios reales) y horas simuladas.
 *
 *   java -cp dist/chromispos.jar tests/AttendanceTest.java [url usuario contraseña]
 */
public class AttendanceTest {
    static int fails = 0;
    static final String U = "prueba-asistencia";
    static void check(String what, Object got, Object exp) {
        boolean ok = java.util.Objects.equals(got, exp);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    static Timestamp t(String s) { return Timestamp.valueOf(LocalDateTime.parse(s)); }
    static String one(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) { return rs.next() ? rs.getString(1) : null; }
    }
    static void exec(Connection c, String sql) throws SQLException { try (Statement st = c.createStatement()) { st.executeUpdate(sql); } }

    public static void main(String[] a) throws Exception {
        Connection c = DriverManager.getConnection(a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos",
                a.length > 1 ? a[1] : "chromis", a.length > 2 ? a[2] : "chromis");
        String[] keys = {PosSettings.ATTENDANCE_CHECKIN, PosSettings.ATTENDANCE_HOURS, PosSettings.ATTENDANCE_CHECKOUT};
        String[] saved = new String[3];
        for (int i = 0; i < 3; i++) saved[i] = PosSettings.get(c, keys[i], null);
        try {
            exec(c, "DELETE FROM shifts WHERE pplid = '" + U + "'");
            PosSettings.put(c, PosSettings.ATTENDANCE_CHECKIN, "0");
            PosSettings.put(c, PosSettings.ATTENDANCE_CHECKOUT, "0");
            check("entrada apagada", AutoAttendance.checkInIfDue(c, U, t("2026-10-20T08:00")), Result.NOTHING);

            PosSettings.put(c, PosSettings.ATTENDANCE_CHECKIN, "1");
            PosSettings.put(c, PosSettings.ATTENDANCE_HOURS, "6");
            check("primera entrada", AutoAttendance.checkInIfDue(c, U, t("2026-10-20T08:00")), Result.CHECKED_IN);
            check("volver a entrar no duplica", AutoAttendance.checkInIfDue(c, U, t("2026-10-20T10:30")), Result.NOTHING);
            check("salida apagada", AutoAttendance.checkOutIfEnabled(c, U, t("2026-10-20T14:00")), Result.NOTHING);

            PosSettings.put(c, PosSettings.ATTENDANCE_CHECKOUT, "1");
            check("salida al corte", AutoAttendance.checkOutIfEnabled(c, U, t("2026-10-20T14:00")), Result.CHECKED_OUT);
            check("turno guardado", one(c, "SELECT CONCAT(startshift,' / ',endshift) FROM shifts WHERE pplid='" + U + "'"),
                    "2026-10-20 08:00:00 / 2026-10-20 14:00:00");
            check("segundo corte sin entrada", AutoAttendance.checkOutIfEnabled(c, U, t("2026-10-20T14:05")), Result.NOTHING);
            check("entra 2 h después del corte: no", AutoAttendance.checkInIfDue(c, U, t("2026-10-20T16:00")), Result.NOTHING);
            check("entra al día siguiente: sí", AutoAttendance.checkInIfDue(c, U, t("2026-10-21T08:00")), Result.CHECKED_IN);

            // Olvidó el corte el 21; vendió por última vez a las 15:30; vuelve el 22
            String money = one(c, "SELECT money FROM closedcash LIMIT 1");
            exec(c, "INSERT INTO receipts (id, money, datenew, person) VALUES ('prueba-asist-1', '" + money + "', '2026-10-21 15:30:00', '" + U + "')");
            check("entrada abierta de ayer se cierra y abre nueva", AutoAttendance.checkInIfDue(c, U, t("2026-10-22T09:00")), Result.CHECKED_IN);
            check("ayer cerrada a la última venta", one(c, "SELECT endshift FROM shifts WHERE pplid='" + U + "' AND startshift='2026-10-21 08:00:00'"),
                    "2026-10-21 15:30:00");
            check("una sola abierta", one(c, "SELECT COUNT(*) FROM shifts WHERE pplid='" + U + "' AND endshift IS NULL"), "1");

            // Descanso abierto: el corte también lo cierra
            String shift = one(c, "SELECT id FROM shifts WHERE pplid='" + U + "' AND endshift IS NULL");
            String brk = one(c, "SELECT id FROM breaks LIMIT 1");
            if (brk != null) {
                exec(c, "INSERT INTO shift_breaks (id, shiftid, breakid, starttime) VALUES ('prueba-asist-b', '" + shift + "', '" + brk + "', '2026-10-22 13:00:00')");
                AutoAttendance.checkOutIfEnabled(c, U, t("2026-10-22T18:00"));
                check("descanso cerrado", one(c, "SELECT endtime FROM shift_breaks WHERE id='prueba-asist-b'"), "2026-10-22 18:00:00");
            } else {
                AutoAttendance.checkOutIfEnabled(c, U, t("2026-10-22T18:00"));
            }
            check("entra 5 h después del corte: no", AutoAttendance.checkInIfDue(c, U, t("2026-10-22T23:00")), Result.NOTHING);
        } finally {
            exec(c, "DELETE FROM shift_breaks WHERE id = 'prueba-asist-b'");
            exec(c, "DELETE FROM shifts WHERE pplid = '" + U + "'");
            exec(c, "DELETE FROM receipts WHERE id = 'prueba-asist-1'");
            for (int i = 0; i < 3; i++) {
                if (saved[i] == null) exec(c, "DELETE FROM pos_settings WHERE name = '" + keys[i] + "'");
                else PosSettings.put(c, keys[i], saved[i]);
            }
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
