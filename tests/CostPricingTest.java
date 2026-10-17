import java.sql.*;
import uk.chromis.data.loader.Session;
import uk.chromis.pos.sales.CostPricing;
public class CostPricingTest {
    static int fails = 0;
    static void check(String what, Object got, Object exp) {
        boolean ok = (exp == null) ? got == null : exp.equals(got);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    public static void main(String[] a) throws Exception {
        Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/chromistest", "chromis", "chromis");
        try (Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS costpricing_customers");
            st.execute("DROP TABLE IF EXISTS costpricing_excluded");
        }
        Session s = new Session(c);
        CostPricing cp = new CostPricing(s);
        check("cliente sin margen (crea tablas)", cp.getMarkup("C1"), null);
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO costpricing_customers VALUES ('C1', 7.5)");
            st.execute("INSERT INTO costpricing_excluded VALUES ('P2')");
        }
        check("cache hasta reset", cp.getMarkup("C1"), null);
        cp.reset();
        check("margen C1 tras reset", cp.getMarkup("C1"), 7.5);
        check("producto P1 incluido", cp.isExcluded("P1"), false);
        check("producto P2 excluido", cp.isExcluded("P2"), true);
        check("precio 100 + 7.5%", CostPricing.price(100.0, 7.5), 107.5);
        check("precio 12.34 + 7.5%", CostPricing.price(12.34, 7.5), 13.27);
        check("cliente null", cp.getMarkup(null), null);
        System.exit(fails);
    }
}
