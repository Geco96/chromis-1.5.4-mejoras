import java.sql.*;
import uk.chromis.administration.products.ConsonantCode;
import uk.chromis.pos.util.BarcodeValidator;

/**
 * Prueba de "Generar código" (consonantes del nombre como Code 128).
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
        check("ejemplo", ConsonantCode.fromName("ASERRIN BOLSA NEGRO"), "SRRNBLSNGR");
        check("minúsculas, acentos, Ñ y números", ConsonantCode.fromName("Piñata Azúcar 1kg"), "PNTZCRKG");
        check("Y es consonante", ConsonantCode.fromName("Yeso blanco"), "YSBLNC");
        check("solo vocales", ConsonantCode.fromName(" aeiou "), "");
        check("nulo", ConsonantCode.fromName(null), "");
        check("Chromis lo guarda como", BarcodeValidator.BarcodeValidate("SRRNBLSNGR"), "CODE128");
        check("EAN-13 es código de tienda", ConsonantCode.isRetailBarcode("7501234567893"), true);
        check("generado no lo es", ConsonantCode.isRetailBarcode("SRRNBLSNGR"), false);

        Connection c = DriverManager.getConnection(a.length > 0 ? a[0] : "jdbc:mysql://localhost:3306/chromispos",
                a.length > 1 ? a[1] : "chromis", a.length > 2 ? a[2] : "chromis");
        String id;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT id, code FROM products WHERE code IS NOT NULL AND code <> '' LIMIT 1")) {
            rs.next();
            id = rs.getString(1);
            String code = rs.getString(2);
            check("código libre se queda igual", ConsonantCode.unique(c, "ZZQXWVTRPLKJ", null), "ZZQXWVTRPLKJ");
            check("código de otro producto lleva número", ConsonantCode.unique(c, code, "otro"), code + "2");
            check("su propio código no cuenta", ConsonantCode.unique(c, code, id), code);
        }
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
