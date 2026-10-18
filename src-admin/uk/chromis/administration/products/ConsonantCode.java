package uk.chromis.administration.products;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;

/**
 * Code 128 for products without a printed barcode: the consonants of the
 * product name, e.g. "ASERRIN BOLSA NEGRO" gives SRRNBLSNGR. Accents are
 * dropped (Ñ becomes N) so the code only uses plain A-Z, which every scanner
 * and label printer handles.
 */
public final class ConsonantCode {

    private ConsonantCode() {
    }

    /** Consonants of the text in upper case; empty if there are none. */
    public static String fromName(String text) {
        if (text == null) {
            return "";
        }
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase();
        StringBuilder sb = new StringBuilder();
        for (char c : plain.toCharArray()) {
            if (c >= 'A' && c <= 'Z' && "AEIOU".indexOf(c) < 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** True for codes that already look like a retail barcode (EAN-8/13, UPC-A/E). */
    public static boolean isRetailBarcode(String code) {
        return code != null && code.trim().matches("\\d{6}|\\d{8}|\\d{12}|\\d{13}|\\d{14}");
    }

    /**
     * The code, or the code followed by 2, 3... when another product already
     * uses it (as main code or as an extra barcode).
     */
    public static String unique(Connection con, String code, String productId) throws SQLException {
        String candidate = code;
        for (int n = 2; inUse(con, candidate, productId); n++) {
            candidate = code + n;
        }
        return candidate;
    }

    private static boolean inUse(Connection con, String code, String productId) throws SQLException {
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
