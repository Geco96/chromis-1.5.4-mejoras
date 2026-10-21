package uk.chromis.pos.settings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Settings of the improvements shared by every terminal, as name/value pairs
 * in table pos_settings. Written by Chromis Admin, read by Chromis POS.
 */
public final class PosSettings {

    /** "1" to show the stock under the product buttons of the catalog. */
    public static final String CATALOG_STOCK = "catalog.stock.show";
    /** Colour of that text, as #RRGGBB. */
    public static final String CATALOG_STOCK_COLOR = "catalog.stock.color";
    public static final String DEFAULT_STOCK_COLOR = "#D00000";
    /** "1" to check users in on their first login after ATTENDANCE_HOURS without a check out. */
    public static final String ATTENDANCE_CHECKIN = "attendance.checkin.auto";
    public static final String ATTENDANCE_HOURS = "attendance.checkin.hours";
    /** "1" to check users out when they close the cash. */
    public static final String ATTENDANCE_CHECKOUT = "attendance.checkout.auto";

    /** Code made when a product is saved without one in Admin: "CODE128" or "EAN13". */
    public static final String PRODUCT_CODE_TYPE = "product.code.type";
    /** Maximum length of the Code 128 made from the product name. */
    public static final String PRODUCT_CODE_LENGTH = "product.code.maxlength";

    private PosSettings() {
    }

    public static void createTable(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS pos_settings ("
                    + "name VARCHAR(64) NOT NULL PRIMARY KEY, value VARCHAR(255))");
        }
    }

    public static String get(Connection con, String name, String defaultValue) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT value FROM pos_settings WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null ? rs.getString(1) : defaultValue;
            }
        } catch (SQLException ex) {
            if ("42S02".equals(ex.getSQLState())) {
                return defaultValue; // table not created yet
            }
            throw ex;
        }
    }

    public static void put(Connection con, String name, String value) throws SQLException {
        createTable(con);
        try (PreparedStatement ps = con.prepareStatement("REPLACE INTO pos_settings (name, value) VALUES (?, ?)")) {
            ps.setString(1, name);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    /** #RRGGBB, or the default when the text is not a colour. */
    public static String normalizeColor(String color) {
        if (color != null && color.trim().matches("#?[0-9a-fA-F]{6}")) {
            String c = color.trim();
            return (c.startsWith("#") ? c : "#" + c).toUpperCase();
        }
        return DEFAULT_STOCK_COLOR;
    }
}
