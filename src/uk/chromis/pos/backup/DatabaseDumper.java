package uk.chromis.pos.backup;

import java.io.IOException;
import java.io.Writer;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a MySQL/MariaDB database as an SQL script that can be restored with the
 * mysql command line client: tables with their data, views, triggers and
 * stored routines. Data is read inside one consistent snapshot.
 */
public class DatabaseDumper {

    private static final int MAX_STATEMENT_CHARS = 1_000_000;

    private final Connection con;
    private final List<String> warnings = new ArrayList<>();

    public DatabaseDumper(Connection con) {
        this.con = con;
    }

    /** Objects that could not be included, e.g. routines the user has no rights to read. */
    public List<String> getWarnings() {
        return warnings;
    }

    public void dump(Writer out) throws SQLException, IOException {
        String database = scalar("SELECT DATABASE()");
        out.write("-- Respaldo de la base de datos '" + database + "' generado por Chromis POS\n");
        out.write("-- Restaurar con: mysql -u USUARIO -p NOMBRE_BASE < archivo.sql\n\n");
        out.write("SET NAMES utf8mb4;\n");
        out.write("SET FOREIGN_KEY_CHECKS=0;\n");
        out.write("SET UNIQUE_CHECKS=0;\n");
        out.write("SET SQL_MODE='NO_AUTO_VALUE_ON_ZERO';\n");
        out.write("SET AUTOCOMMIT=0;\n\n");

        boolean autoCommit = con.getAutoCommit();
        try (Statement st = con.createStatement()) {
            st.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ");
            con.setAutoCommit(false);
            st.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT");

            List<String> tables = new ArrayList<>();
            List<String> views = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SHOW FULL TABLES")) {
                while (rs.next()) {
                    if ("VIEW".equalsIgnoreCase(rs.getString(2))) {
                        views.add(rs.getString(1));
                    } else {
                        tables.add(rs.getString(1));
                    }
                }
            }

            for (String table : tables) {
                dumpTable(table, out);
            }
            for (String view : views) {
                out.write("DROP VIEW IF EXISTS " + quoteName(view) + ";\n");
                out.write(withoutDefiner(showCreate("SHOW CREATE VIEW " + quoteName(view), 2)) + ";\n\n");
            }
            out.write("COMMIT;\n\n");

            dumpTriggers(out);
            dumpRoutines("PROCEDURE", database, out);
            dumpRoutines("FUNCTION", database, out);
        } finally {
            con.commit();
            con.setAutoCommit(autoCommit);
        }

        out.write("SET FOREIGN_KEY_CHECKS=1;\n");
        out.write("SET UNIQUE_CHECKS=1;\n");
        out.write("-- Fin del respaldo\n");
        out.flush();
    }

    private void dumpTable(String table, Writer out) throws SQLException, IOException {
        out.write("--\n-- Tabla " + table + "\n--\n");
        out.write("DROP TABLE IF EXISTS " + quoteName(table) + ";\n");
        out.write(showCreate("SHOW CREATE TABLE " + quoteName(table), 2) + ";\n\n");

        try (Statement st = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            // Stream rows instead of loading the whole table in memory
            st.setFetchSize(Integer.MIN_VALUE);
            try (ResultSet rs = st.executeQuery("SELECT * FROM " + quoteName(table))) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                StringBuilder header = new StringBuilder("INSERT INTO ").append(quoteName(table)).append(" (");
                for (int i = 1; i <= cols; i++) {
                    header.append(i > 1 ? ", " : "").append(quoteName(md.getColumnName(i)));
                }
                header.append(") VALUES\n");

                StringBuilder stmt = new StringBuilder();
                while (rs.next()) {
                    StringBuilder row = new StringBuilder("(");
                    for (int i = 1; i <= cols; i++) {
                        if (i > 1) {
                            row.append(',');
                        }
                        appendValue(row, rs, i, md.getColumnType(i));
                    }
                    row.append(')');

                    if (stmt.length() > 0 && stmt.length() + row.length() > MAX_STATEMENT_CHARS) {
                        out.write(stmt.append(";\n").toString());
                        stmt.setLength(0);
                    }
                    stmt.append(stmt.length() == 0 ? header : ",\n").append(row);
                }
                if (stmt.length() > 0) {
                    out.write(stmt.append(";\n").toString());
                }
            }
        }
        out.write("\n");
    }

    private void dumpTriggers(Writer out) throws SQLException, IOException {
        List<String> triggers = new ArrayList<>();
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("SHOW TRIGGERS")) {
            while (rs.next()) {
                triggers.add(rs.getString("Trigger"));
            }
        }
        if (triggers.isEmpty()) {
            return;
        }
        out.write("DELIMITER ;;\n");
        for (String trigger : triggers) {
            writeDefinition(out, "TRIGGER", trigger, showCreate("SHOW CREATE TRIGGER " + quoteName(trigger), 3));
        }
        out.write("DELIMITER ;\n\n");
    }

    private void dumpRoutines(String type, String database, Writer out) throws SQLException, IOException {
        List<String> names = new ArrayList<>();
        try (Statement st = con.createStatement();
                ResultSet rs = st.executeQuery("SHOW " + type + " STATUS WHERE Db = " + quoteValue(database))) {
            while (rs.next()) {
                names.add(rs.getString("Name"));
            }
        }
        if (names.isEmpty()) {
            return;
        }
        out.write("DELIMITER ;;\n");
        for (String name : names) {
            writeDefinition(out, type, name, showCreate("SHOW CREATE " + type + " " + quoteName(name), 3));
        }
        out.write("DELIMITER ;\n\n");
    }

    private void writeDefinition(Writer out, String type, String name, String definition) throws IOException {
        if (definition == null) {
            // MySQL hides the body when the user lacks rights on an object created by someone else
            warnings.add(type + " " + name);
            out.write("-- AVISO: no se pudo leer la definición de " + type + " " + name + " (permisos insuficientes)\n");
            return;
        }
        out.write("DROP " + type + " IF EXISTS " + quoteName(name) + ";;\n");
        out.write(withoutDefiner(definition) + ";;\n");
    }

    private void appendValue(StringBuilder sb, ResultSet rs, int col, int type) throws SQLException {
        switch (type) {
            case Types.BIT:
            case Types.BOOLEAN: {
                boolean value = rs.getBoolean(col);
                sb.append(rs.wasNull() ? "NULL" : (value ? "1" : "0"));
                break;
            }
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.REAL:
            case Types.FLOAT:
            case Types.DOUBLE:
            case Types.DECIMAL:
            case Types.NUMERIC: {
                String value = rs.getString(col);
                sb.append(value == null ? "NULL" : value);
                break;
            }
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
            case Types.BLOB: {
                byte[] value = rs.getBytes(col);
                if (value == null) {
                    sb.append("NULL");
                } else if (value.length == 0) {
                    sb.append("''");
                } else {
                    sb.append("0x");
                    for (byte b : value) {
                        sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
                    }
                }
                break;
            }
            default: {
                String value = rs.getString(col);
                sb.append(value == null ? "NULL" : quoteValue(value));
            }
        }
    }

    private String scalar(String sql) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private String showCreate(String sql, int column) throws SQLException {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (!rs.next()) {
                throw new SQLException("Sin resultado para: " + sql);
            }
            return rs.getString(column);
        }
    }

    // The DEFINER user may not exist on the server where the backup is restored
    static String withoutDefiner(String sql) {
        return sql.replaceFirst("DEFINER=(`[^`]*`|\\S+)@(`[^`]*`|\\S+)\\s+", "");
    }

    static String quoteName(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    static String quoteValue(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2).append('\'');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\0':
                    sb.append("\\0");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\u001a':
                    sb.append("\\Z");
                    break;
                case '\'':
                    sb.append("\\'");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.append('\'').toString();
    }
}
