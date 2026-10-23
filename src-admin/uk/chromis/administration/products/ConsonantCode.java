package uk.chromis.administration.products;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;

/**
 * Code 128 for products without a printed barcode: the consonants and digits
 * of the product name, cut to a maximum length (8 by default, set in Admin),
 * e.g. "ASERRIN BOLSA NEGRO" gives SRRNBLSN and "BOLSA 5KG" gives BLS5KG.
 * Accents are dropped (Ñ becomes N) so the code only uses A-Z and 0-9, which
 * every scanner and label printer handles.
 */
public final class ConsonantCode {

    public static final int DEFAULT_LENGTH = 8;
    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 12;

    private ConsonantCode() {
    }

    /** Consonants and digits of the text in upper case, cut to the default length; empty if there are none. */
    public static String fromName(String text) {
        return fromName(text, DEFAULT_LENGTH);
    }

    /** Consonants and digits of the text in upper case, cut to maxLength; empty if there are none. */
    public static String fromName(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
        StringBuilder sb = new StringBuilder();
        for (char c : plain.toCharArray()) {
            if ((c >= 'A' && c <= 'Z' && "AEIOU".indexOf(c) < 0) || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        int max = clampLength(maxLength);
        return sb.length() > max ? sb.substring(0, max) : sb.toString();
    }

    /** The length setting within 4..12; the default when it is not a number. */
    public static int clampLength(int length) {
        return Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
    }

    public static int parseLength(String text) {
        try {
            return clampLength(Integer.parseInt(text.trim()));
        } catch (RuntimeException ex) {
            return DEFAULT_LENGTH;
        }
    }

    /** True for codes that already look like a retail barcode (EAN-8/13, UPC-A/E). */
    public static boolean isRetailBarcode(String code) {
        return code != null && code.trim().matches("\\d{6}|\\d{8}|\\d{12}|\\d{13}|\\d{14}");
    }

    /**
     * The code, or the code ending in 2, 3... when another product already
     * uses it (as main code or as an extra barcode). The end of the code is
     * cut when needed so it stays within maxLength characters.
     */
    public static String unique(Connection con, String code, String productId) throws SQLException {
        return unique(con, code, productId, MAX_LENGTH);
    }

    public static String unique(Connection con, String code, String productId, int maxLength) throws SQLException {
        int max = Math.max(clampLength(maxLength), code.length());
        String candidate = code;
        for (int n = 2; inUse(con, candidate, productId); n++) {
            String suffix = String.valueOf(n);
            String base = code.length() + suffix.length() > max ? code.substring(0, max - suffix.length()) : code;
            candidate = base + suffix;
        }
        return candidate;
    }

    static boolean inUse(Connection con, String code, String productId) throws SQLException {
        String exclude = productId == null ? "" : productId;
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM products WHERE code = ? AND id <> ? "
                + "UNION SELECT 1 FROM barcodes WHERE barcode = ? AND productid <> ? LIMIT 1")) {
            ps.setString(1, code);
            ps.setString(2, exclude);
            ps.setString(3, code);
            ps.setString(4, exclude);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
