package uk.chromis.pos.delivery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * One-time import of the customers captured in the old map web (Supabase
 * table "clientes", exported as CSV from Supabase's Table editor) into
 * Chromis: each one becomes (or is linked to) a Chromis customer and its pin
 * goes to customer_geo.
 *
 * <ul>
 * <li>A web customer is recognised by its account number WEB-&lt;id_cliente&gt;,
 * so importing the same file again updates instead of duplicating.</li>
 * <li>A Chromis customer with exactly the same name is linked instead of
 * creating another one.</li>
 * <li>A pin moved in Chromis after the import is not overwritten.</li>
 * <li>Everything in one transaction: the file is imported whole or not at
 * all. preview=true does the same and rolls back, to show the numbers.</li>
 * </ul>
 */
public final class WebCustomerImport {

    public static final String TAXID_PREFIX = "WEB-";

    private WebCustomerImport() {
    }

    public static final class Result {

        public int rows;
        public int created;
        public int linked;
        public int updated;
        public int located;
        public int withoutLocation;
        /** "line N: reason". */
        public final List<String> skipped = new ArrayList<>();

        public String describe() {
            return rows + " filas: " + created + " clientes nuevos, " + linked + " enlazados con clientes que ya existían, "
                    + updated + " ya importados antes; " + located + " con ubicación, " + withoutLocation
                    + " sin ubicación" + (skipped.isEmpty() ? "." : "; " + skipped.size() + " omitidas.");
        }
    }

    // ---------------- CSV ----------------

    /** RFC 4180: comma separated, fields in double quotes may hold commas, quotes ("") and line breaks. */
    public static List<List<String>> parseCsv(Reader in) throws IOException {
        if (!in.markSupported()) {
            in = new BufferedReader(in);
        }
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        int c;
        while ((c = in.read()) != -1) {
            char ch = (char) c;
            if (quoted) {
                if (ch == '"') {
                    in.mark(1);
                    int next = in.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        quoted = false;
                        if (next == -1) {
                            break;
                        }
                        in.reset();
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"' && field.length() == 0) {
                quoted = true;
                any = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
                any = true;
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r') {
                    in.mark(1);
                    if (in.read() != '\n') {
                        in.reset();
                    }
                }
                if (any || field.length() > 0) {
                    row.add(field.toString());
                    rows.add(row);
                }
                row = new ArrayList<>();
                field.setLength(0);
                any = false;
            } else {
                field.append(ch);
                any = true;
            }
        }
        if (any || field.length() > 0) {
            row.add(field.toString());
            rows.add(row);
        }
        if (!rows.isEmpty() && !rows.get(0).isEmpty() && rows.get(0).get(0).startsWith("﻿")) {
            rows.get(0).set(0, rows.get(0).get(0).substring(1));   // BOM de Excel
        }
        return rows;
    }

    // ---------------- Import ----------------

    public static Result run(Connection con, Reader csv, String siteGuid, String user, boolean preview)
            throws IOException, SQLException {
        List<List<String>> rows = parseCsv(csv);
        if (rows.isEmpty()) {
            throw new SQLException("El archivo está vacío.");
        }
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < rows.get(0).size(); i++) {
            col.put(rows.get(0).get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        for (String needed : new String[]{"id_cliente", "nombre_comercial"}) {
            if (!col.containsKey(needed)) {
                throw new SQLException("El archivo no tiene la columna \"" + needed
                        + "\". Exporta la tabla clientes de Supabase como CSV, sin cambiarle los encabezados.");
            }
        }
        GeoStore.createTable(con);   // DDL antes de la transacción
        Result r = new Result();
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            for (int line = 1; line < rows.size(); line++) {
                List<String> row = rows.get(line);
                r.rows++;
                String webId = value(row, col, "id_cliente");
                String name = value(row, col, "nombre_comercial");
                if (webId == null) {
                    r.skipped.add("línea " + (line + 1) + ": sin id_cliente");
                    continue;
                }
                if (name == null) {
                    r.skipped.add("línea " + (line + 1) + ": sin nombre");
                    continue;
                }
                Double lat = number(value(row, col, "latitud"));
                Double lng = number(value(row, col, "longitud"));
                if (GeoStore.validate(lat, lng) != null) {
                    lat = null;
                    lng = null;
                }
                String status = status(value(row, col, "estado_logistico"));
                String taxid = TAXID_PREFIX + webId;

                String customer = find(con, "SELECT id FROM customers WHERE taxid = ? AND siteguid = ?", taxid, siteGuid);
                if (customer != null) {
                    r.updated++;
                } else {
                    List<String> same = all(con, "SELECT id FROM customers WHERE LOWER(TRIM(name)) = LOWER(TRIM(?)) "
                            + "AND siteguid = ?", name, siteGuid);
                    if (same.size() == 1) {
                        customer = same.get(0);
                        r.linked++;
                    } else if (same.size() > 1) {
                        r.skipped.add("línea " + (line + 1) + ": hay " + same.size() + " clientes llamados \"" + name
                                + "\" en Chromis; ubícalo a mano");
                        continue;
                    } else {
                        customer = UUID.randomUUID().toString();
                        try (PreparedStatement ps = con.prepareStatement("INSERT INTO customers (id, customertype, taxid, "
                                + "name, maxdebt, address, phone, notes, active, curdate, siteguid) "
                                + "VALUES (?, 'account', ?, ?, 0, ?, ?, ?, 1, ?, ?)")) {
                            ps.setString(1, customer);
                            ps.setString(2, taxid);
                            ps.setString(3, DeliveryStore.fit(name, 50));
                            ps.setString(4, DeliveryStore.fit(value(row, col, "direccion_completa"), 100));
                            ps.setString(5, DeliveryStore.fit(value(row, col, "telefono"), 20));
                            ps.setString(6, DeliveryStore.fit(value(row, col, "notas_internas"), 255));
                            ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
                            ps.setString(8, siteGuid);
                            ps.executeUpdate();
                        }
                        r.created++;
                    }
                }
                GeoStore.Geo geo = GeoStore.get(con, customer);
                boolean mine = geo == null || GeoStore.SOURCE_WEB.equals(geo.source);
                if (mine) {
                    GeoStore.save(con, customer, lat, lng, status, GeoStore.SOURCE_WEB, user);
                }
                if ((mine ? lat : geo.lat) != null) {
                    r.located++;
                } else {
                    r.withoutLocation++;
                }
            }
            if (preview) {
                con.rollback();
            } else {
                con.commit();
            }
            return r;
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    /** Web status → customer_geo status. */
    static String status(String webStatus) {
        if ("nuevo".equalsIgnoreCase(webStatus)) {
            return GeoStore.PROSPECT;
        }
        if ("pendiente_visita".equalsIgnoreCase(webStatus)) {
            return GeoStore.PENDING_VISIT;
        }
        return GeoStore.CAPTIVE;
    }

    private static String value(List<String> row, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        if (i == null || i >= row.size()) {
            return null;
        }
        String v = row.get(i).trim();
        return v.isEmpty() || v.equalsIgnoreCase("null") ? null : v;
    }

    private static Double number(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String find(Connection con, String sql, String a, String b) throws SQLException {
        List<String> l = all(con, sql, a, b);
        return l.isEmpty() ? null : l.get(0);
    }

    private static List<String> all(Connection con, String sql, String a, String b) throws SQLException {
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, a);
            ps.setString(2, b);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
            }
        }
        return list;
    }
}
