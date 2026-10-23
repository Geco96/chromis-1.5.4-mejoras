package uk.chromis.pos.backup;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Loads a SQL script (a backup made by DatabaseDumper, or a mysqldump /
 * HeidiSQL export) through JDBC, the way the mysql client does, without
 * needing mysql.exe:
 * <ul>
 * <li>Statements are split on the current delimiter (";" or the one set with
 * DELIMITER), respecting '...', "..." and `...` (with \ escapes) and the
 * comments --, # and /* *&#47;. MySQL executable comments (/*!...) are sent
 * to the server as part of the statement, like the mysql client does.</li>
 * <li>The last line of a DatabaseDumper backup ("-- Fin del respaldo") tells
 * whether the file is complete; {@link #check} reads the whole file first so
 * a cut backup is refused before anything is deleted.</li>
 * <li>Progress counts what was already executed, not what the reader read
 * ahead.</li>
 * </ul>
 * Same rules as RestauraBD in Chromis for Android (chromis-android).
 */
public final class SqlRestore {

    /** Last line of a backup made by DatabaseDumper. */
    public static final String END = "-- Fin del respaldo";
    /** Last line of a backup made by Chromis for Android. */
    public static final String END_ANDROID = "-- FIN DEL RESPALDO";

    private SqlRestore() {
    }

    public interface Progress {

        /** [bytes] of the file already executed and [statements] run. */
        void progress(long bytes, int statements);

        boolean cancelled();
    }

    public static final class Result {

        public int statements;
        /** The file ended with the end line of a backup. */
        public boolean complete;
    }

    /** A statement of the file failed. */
    public static final class Failure extends SQLException {

        public final int number;
        public final String statement;

        Failure(int number, String statement, SQLException cause) {
            super("La instrucción " + number + " falló: " + cause.getMessage() + "\n" + summary(statement),
                    cause.getSQLState(), cause.getErrorCode(), cause);
            this.number = number;
            this.statement = statement;
        }
    }

    public static final class Cancelled extends IOException {

        Cancelled() {
            super("Cancelado");
        }
    }

    static String summary(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }

    static boolean isEnd(String line) {
        return line.equalsIgnoreCase(END) || line.equals(END_ANDROID);
    }

    interface Sink {

        void statement(String sql) throws SQLException, IOException;
    }

    /** Reads the whole file without running anything: true if it is a complete backup. */
    public static boolean check(InputStream in, Progress p) throws IOException {
        try {
            return split(in, sql -> {
            }, p, new int[1]);
        } catch (SQLException e) {
            throw new IOException(e);
        }
    }

    /** Runs every statement of [in] on [c]. */
    public static Result restore(Connection c, InputStream in, Progress p) throws SQLException, IOException {
        Result r = new Result();
        int[] count = new int[1];
        try (Statement st = c.createStatement()) {
            r.complete = split(in, sql -> {
                if (p != null && p.cancelled()) {
                    throw new Cancelled();
                }
                count[0]++;
                try {
                    st.execute(sql);
                } catch (SQLException e) {
                    throw new Failure(count[0], sql, e);
                }
            }, p, count);
        }
        r.statements = count[0];
        return r;
    }

    private static boolean split(InputStream in, Sink sink, Progress p, int[] count) throws IOException, SQLException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 64 * 1024);
        Splitter s = new Splitter(sink);
        long bytes = 0;
        long reported = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            s.line(line);
            bytes += line.getBytes(StandardCharsets.UTF_8).length + 1;
            if (p != null && bytes - reported >= 256 * 1024) {
                if (p.cancelled()) {
                    throw new Cancelled();
                }
                p.progress(bytes, count[0]);
                reported = bytes;
            }
        }
        s.finish();
        if (p != null) {
            p.progress(bytes, count[0]);
        }
        return s.end;
    }

    /** State machine of the splitter (like the mysql client). */
    static final class Splitter {

        private static final int NORMAL = 0;
        private static final int BLOCK = 'b';

        private final Sink sink;
        private final StringBuilder sb = new StringBuilder(64 * 1024);
        private String delimiter = ";";
        /** NORMAL, BLOCK or the open quote. */
        private int mode = NORMAL;
        boolean end;

        Splitter(Sink sink) {
            this.sink = sink;
        }

        private boolean blank() {
            for (int i = 0; i < sb.length(); i++) {
                if (!Character.isWhitespace(sb.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        void line(String t) throws SQLException, IOException {
            if (mode == NORMAL && blank()) {
                String l = t.trim();
                if (l.regionMatches(true, 0, "DELIMITER", 0, 9) && (l.length() == 9 || Character.isWhitespace(l.charAt(9)))) {
                    String d = l.substring(9).trim();
                    if (!d.isEmpty()) {
                        delimiter = d;
                    }
                    sb.setLength(0);
                    return;
                }
            }
            int n = t.length();
            int i = 0;
            while (i < n) {
                char x = t.charAt(i);
                if (mode == BLOCK) {
                    int close = t.indexOf("*/", i);
                    if (close < 0) {
                        i = n;
                    } else {
                        i = close + 2;
                        mode = NORMAL;
                    }
                    continue;
                }
                if (mode != NORMAL) {
                    sb.append(x);
                    if (x == '\\' && mode != '`' && i + 1 < n) {
                        sb.append(t.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (x == mode) {
                        mode = NORMAL;
                    }
                    i++;
                    continue;
                }
                if (x == '\'' || x == '"' || x == '`') {
                    mode = x;
                    sb.append(x);
                    i++;
                    continue;
                }
                if (x == '#' || (x == '-' && i + 1 < n && t.charAt(i + 1) == '-'
                        && (i + 2 >= n || Character.isWhitespace(t.charAt(i + 2))))) {
                    if (isEnd(t.substring(i).trim())) {
                        end = true;
                    }
                    break;
                }
                if (x == '/' && i + 1 < n && t.charAt(i + 1) == '*' && !(i + 2 < n && t.charAt(i + 2) == '!')) {
                    mode = BLOCK;
                    i += 2;
                    continue;
                }
                if (t.startsWith(delimiter, i)) {
                    emit();
                    i += delimiter.length();
                    continue;
                }
                sb.append(x);
                i++;
            }
            if (mode != BLOCK) {
                sb.append('\n');
            }
        }

        private void emit() throws SQLException, IOException {
            String sql = sb.toString().trim();
            sb.setLength(0);
            if (!sql.isEmpty()) {
                sink.statement(sql);
            }
        }

        void finish() throws SQLException, IOException {
            if (mode == NORMAL || mode == BLOCK) {
                emit();
            } else {
                throw new IOException("El archivo termina con un texto sin cerrar (" + (char) mode + ")");
            }
        }
    }
}
