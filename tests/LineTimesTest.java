import java.text.SimpleDateFormat;
import java.util.*;
import uk.chromis.pos.sales.LineTimes;
import uk.chromis.pos.ticket.TicketInfo;
import uk.chromis.pos.ticket.TicketLineInfo;

/**
 * Hora en que se agregó cada producto a una venta y en que cambió su cantidad.
 *
 *   java -cp dist/chromispos.jar tests/LineTimesTest.java
 *   (necesita la configuración de una caja en la carpeta y pantalla, como HeldTicketArchiveTest)
 */
public class LineTimesTest {
    static int fails = 0;
    static void check(String what, Object got, Object exp) {
        boolean ok = Objects.equals(got, exp);
        System.out.println((ok ? "OK   " : "FAIL ") + what + " -> " + got);
        if (!ok) fails++;
    }
    static Date at(String s) throws Exception { return new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(s); }
    static TicketLineInfo line(String name, double q) {
        TicketLineInfo l = new TicketLineInfo();
        l.setProperty("product.name", name);
        l.setMultiply(q);
        l.setPrice(10);
        return l;
    }

    public static void main(String[] a) throws Exception {
        TicketLineInfo l = line("Cuaderno", 2);
        check("sin datos, sin texto", LineTimes.label(l), "");
        LineTimes.stamp(l, "Laura", at("2026-10-25 14:32"));
        check("al agregarlo", LineTimes.label(l), "Agregado 25/10/26 14:32 Laura");
        LineTimes.stamp(l, "Carlos", at("2026-10-26 09:00"));
        check("mostrarlo otra vez no lo cambia", LineTimes.label(l), "Agregado 25/10/26 14:32 Laura");
        l.setMultiply(5);
        LineTimes.stamp(l, "Carlos", at("2026-11-01 09:10"));
        check("cambio de cantidad", LineTimes.label(l), "Agregado 25/10/26 14:32 Laura · cambió de 2 a 5 el 01/11/26 09:10 Carlos");
        l.setMultiply(5.5);
        LineTimes.stamp(l, "Liz", at("2026-11-02 10:00"));
        TicketLineInfo same = line("Goma", 1);
        LineTimes.stamp(same, "Laura", at("2026-11-01 08:00"));
        same.setMultiply(3);
        LineTimes.stamp(same, "Laura", at("2026-11-01 08:30"));
        check("misma persona no se repite", LineTimes.label(same), "Agregado 01/11/26 08:00 Laura · cambió de 1 a 3 el 01/11/26 08:30");
        check("granel", LineTimes.label(l), "Agregado 25/10/26 14:32 Laura · cambió de 5 a 5.5 el 02/11/26 10:00 Liz");
        check("historial completo", l.getProperty(LineTimes.HISTORY),
                "25/10/26 14:32 Laura: agregó 2\n01/11/26 09:10 Carlos: de 2 a 5\n02/11/26 10:00 Liz: de 5 a 5.5");


        // Venta guardada antes de esta versión: no se inventa la hora, pero sí se ven los cambios desde ahora
        TicketInfo old = new TicketInfo();
        TicketLineInfo o = line("Lápiz", 3);
        old.addLine(o);
        LineTimes.adopt(old);
        LineTimes.stamp(o, "Laura", at("2026-11-01 08:00"));
        check("línea vieja: sin hora inventada", LineTimes.label(o), "");
        o.setMultiply(4);
        LineTimes.stamp(o, "Laura", at("2026-11-01 08:05"));
        check("línea vieja: el cambio sí", LineTimes.label(o), "cambió de 3 a 4 el 01/11/26 08:05 Laura");

        // En la pantalla: debajo del nombre, en la plantilla HTML de Chromis (sin dejar una línea vacía en medio)
        String cell = "<html> Cuaderno  \n      <br>";
        check("con la plantilla de Chromis", LineTimes.withLabel(cell, l),
                "<html> Cuaderno<br><font size=\"-2\">Agregado 25/10/26 14:32 Laura · cambió de 5 a 5.5 el 02/11/26 10:00 Liz</font>");
        check("con notas de la línea", LineTimes.withLabel("<html>Cuaderno <br>*sin hojas* <br>", l).startsWith("<html>Cuaderno <br>*sin hojas*<br><font"), true);
        check("columna sin HTML", LineTimes.withLabel("Tijeras & más", line("x", 1)), "Tijeras & más");
        TicketLineInfo t = line("Tijeras", 1);
        LineTimes.stamp(t, "Ana <admin>", at("2026-11-01 08:00"));
        check("texto sin HTML se escapa", LineTimes.withLabel("Tijeras & <más>", t),
                "<html>Tijeras &amp; &lt;más&gt;<br><font size=\"-2\">Agregado 01/11/26 08:00 Ana &lt;admin&gt;</font>");
        System.out.println(fails == 0 ? "TODO BIEN" : fails + " FALLAS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
