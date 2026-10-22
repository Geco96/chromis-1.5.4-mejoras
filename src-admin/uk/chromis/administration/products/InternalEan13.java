package uk.chromis.administration.products;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * EAN-13 codes for internal use: prefix 20 (GS1 numbers 200-299 are
 * reserved for use inside the store, so they never match a manufacturer's
 * product), a 10-digit sequence and the check digit, e.g. 2000000000015,
 * 2000000000022... Chromis recognises them as EAN-13 and label printers and
 * scanners read them like any retail barcode.
 */
public final class InternalEan13 {

    public static final String PREFIX = "20";

    private InternalEan13() {
    }

    /** Check digit of the first 12 digits. */
    public static int checkDigit(String first12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = first12.charAt(i) - '0';
            sum += i % 2 == 0 ? d : d * 3;
        }
        return (10 - sum % 10) % 10;
    }

    public static String of(long sequence) {
        String first12 = PREFIX + String.format("%010d", sequence);
        return first12 + checkDigit(first12);
    }

    public static boolean isValid(String code) {
        return code != null && code.matches("\\d{13}") && checkDigit(code) == code.charAt(12) - '0';
    }

    /** True for an internal code made here (prefix 20 and valid check digit). */
    public static boolean isInternal(String code) {
        return isValid(code) && code.startsWith(PREFIX);
    }

    /**
     * The next free internal code: one more than the highest one used by the
     * other products (as main code or extra barcode), skipping any code that
     * is taken. The product's own codes do not count, so making its code again
     * does not waste a number.
     */
    public static String next(Connection con, String productId) throws SQLException {
        long max = 0;
        String exclude = productId == null ? "" : productId;
        try (PreparedStatement ps = con.prepareStatement("SELECT code FROM products WHERE code LIKE '20___________' AND id <> ? "
                + "UNION SELECT barcode FROM barcodes WHERE barcode LIKE '20___________' AND productid <> ?")) {
            ps.setString(1, exclude);
            ps.setString(2, exclude);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String c = rs.getString(1);
                    if (isInternal(c)) {
                        max = Math.max(max, Long.parseLong(c.substring(2, 12)));
                    }
                }
            }
        }
        String code = of(max + 1);
        for (long n = max + 2; ConsonantCode.inUse(con, code, productId); n++) {
            code = of(n);
        }
        return code;
    }
}
