import java.sql.*;
import uk.chromis.administration.products.ConsonantCode;
import uk.chromis.administration.products.InternalEan13;
import uk.chromis.pos.util.BarcodeValidator;

/**
 * Prueba de "Generar código": consonantes del nombre como Code 128 y EAN-13 interno.
 *
 *   java -cp dist/admin.jar tests/ConsonantCodeTest.java [url usuario contraseña]
 */
public class ConsonantCodeTest {
    static int fails = 0;
    static void check(String what, Object got, Object exp) {
        boolean ok = java.util.Objects.equals(got, exp);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    public static void main(String[] a) throws Exception {
        check("ejemplo (8 por omisión)", ConsonantCode.fromName("ASERRIN BOLSA NEGRO"), "SRRNBLSN");
        check("largo 12", ConsonantCode.fromName("ASERRIN BOLSA NEGRO", 12), "SRRNBLSNGR");
        check("largo 6", ConsonantCode.fromName("ASERRIN BOLSA NEGRO", 6), "SRRNBL");
        check("largo menor que 4 se sube a 4", ConsonantCode.fromName("ASERRIN BOLSA NEGRO", 2), "SRRN");
        check("largo mayor que 12 se baja a 12", ConsonantCode.fromName("CEMENTO GRIS PORTLAND 50 KG", 30), "CMNTGRSPRTLN");
        check("largo no numérico", ConsonantCode.parseLength("abc"), 8);
        check("minúsculas, acentos, Ñ y números", ConsonantCode.fromName("Piñata Azúcar 1kg"), "PNTZCR1K");
        check("conserva números", ConsonantCode.fromName("BOLSA 5KG"), "BLS5KG");
        check("máximo 8", ConsonantCode.fromName("CEMENTO GRIS PORTLAND 50 KG"), "CMNTGRSP");
        check("Y es consonante", ConsonantCode.fromName("Yeso blanco"), "YSBLNC");
        check("solo vocales", ConsonantCode.fromName(" aeiou "), "");
        check("nulo", ConsonantCode.fromName(null), "");
        check("Chromis lo guarda como", BarcodeValidator.BarcodeValidate("SRRNBLSN"), "CODE128");
        check("EAN-13 interno 1", InternalEan13.of(1), "2000000000015");
        check("EAN-13 interno 2", InternalEan13.of(2), "2000000000022");
        check("dígito verificador de un EAN real", InternalEan13.checkDigit("750123456789"), 3);
        check("Chromis lo reconoce como EAN-13", BarcodeValidator.BarcodeValidate(InternalEan13.of(57)), "EAN-13");
        check("interno", InternalEan13.isInternal("2000000000015"), true);
        check("de fábrica no es interno", InternalEan13.isInternal("7501234567893"), false);
        check("dígito equivocado", InternalEan13.isInternal("2000000000016"), false);
        check("EAN-13 es código de tienda", ConsonantCode.isRetailBarcode("7501234567893"), true);
        check("generado no lo es", ConsonantCode.isRetailBarcode("SRRNBLSNGR"), false);

        Connection c = DriverManager.getConnection(a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos",
                a.length > 1 ? a[1] : "chromis", a.length > 2 ? a[2] : "chromis");
        String id;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id, code FROM products WHERE code IS NOT NULL AND code <> '' AND CHAR_LENGTH(code) <= 10 LIMIT 1")) {
            rs.next();
            id = rs.getString(1);
            String code = rs.getString(2);
            check("código libre se queda igual", ConsonantCode.unique(c, "ZZQXWVTRPLKJ", null), "ZZQXWVTRPLKJ");
            check("sufijo dentro de 12", ConsonantCode.unique(c, "750123456789", "otro").length() <= 12, true);
            check("código de otro producto lleva número", ConsonantCode.unique(c, code, "otro"), code + "2");
            check("su propio código no cuenta", ConsonantCode.unique(c, code, id), code);
            check("sufijo dentro de 8", ConsonantCode.unique(c, code.length() >= 8 ? code.substring(0, 8) : code, "otro", 8).length() <= Math.max(8, code.length()), true);
            String first = InternalEan13.next(c, null);
            check("siguiente EAN-13 es interno", InternalEan13.isInternal(first), true);
            try (Statement up = c.createStatement()) {
                up.executeUpdate("UPDATE products SET code = '" + first + "' WHERE id = '" + id + "'");
                try {
                    String second = InternalEan13.next(c, null);
                    check("después del usado sigue el consecutivo", Long.parseLong(second.substring(2, 12)), Long.parseLong(first.substring(2, 12)) + 1);
                    check("el producto que ya lo tiene vuelve a recibir el mismo", InternalEan13.next(c, id), first);
                } finally {
                    up.executeUpdate("UPDATE products SET code = '" + code + "' WHERE id = '" + id + "'");
                }
            }
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
