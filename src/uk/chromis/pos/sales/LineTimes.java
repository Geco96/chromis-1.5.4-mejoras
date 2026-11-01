package uk.chromis.pos.sales;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import uk.chromis.pos.ticket.TicketInfo;
import uk.chromis.pos.ticket.TicketLineInfo;

/**
 * When each product was added to a sale and when its quantity changed, and
 * who did it. Held tickets that stay open for days work as an account per
 * customer: products are added over time, and this tells what was added when
 * (so nothing is added twice). It goes in the properties of the line, so a
 * held ticket keeps it and the charged sale keeps it in ticketlines; it is
 * shown on the sales screen only, never printed.
 */
public final class LineTimes {

    /** When the line was added (yyyy-MM-dd HH:mm:ss) and by whom. */
    public static final String ADDED = "hora.agregado";
    public static final String ADDED_BY = "hora.agregado.por";
    /** Quantity last seen, to notice a change. */
    public static final String QUANTITY = "hora.cantidad";
    /** Last change of quantity: when, by whom, and the quantity before. */
    public static final String CHANGED = "hora.cambio";
    public static final String CHANGED_BY = "hora.cambio.por";
    public static final String CHANGED_FROM = "hora.cambio.de";
    /** Every addition and change, oldest first: "dd/MM/yy HH:mm usuario: …". */
    public static final String HISTORY = "hora.historial";
    private static final int HISTORY_MAX = 2000;
    private static final String STORED = "yyyy-MM-dd HH:mm:ss";
    private static final String SHOWN = "dd/MM/yy HH:mm";

    private LineTimes() {
    }

    /**
     * Notes the time of a new line, or of a change of its quantity. Called
     * every time the sales screen shows the line, so every way of adding or
     * changing a product is covered.
     */
    public static void stamp(TicketLineInfo line, String user, Date now) {
        if (line == null) {
            return;
        }
        double quantity = line.getMultiply();
        String known = line.getProperty(QUANTITY);
        if (known == null) {
            if (line.getProperty(ADDED) == null) {
                line.setProperty(ADDED, new SimpleDateFormat(STORED).format(now));
                put(line, ADDED_BY, user);
                history(line, now, user, "agregó " + quantity(quantity));
            }
            line.setProperty(QUANTITY, quantity(quantity));
            return;
        }
        double before;
        try {
            before = Double.parseDouble(known);
        } catch (NumberFormatException ex) {
            before = Double.NaN;
        }
        if (Math.abs(before - quantity) > 1e-9 || Double.isNaN(before)) {
            line.setProperty(CHANGED, new SimpleDateFormat(STORED).format(now));
            put(line, CHANGED_BY, user);
            line.setProperty(CHANGED_FROM, known);
            line.setProperty(QUANTITY, quantity(quantity));
            history(line, now, user, "de " + known + " a " + quantity(quantity));
        }
    }

    /**
     * Lines of a ticket that was saved before this existed: their quantity is
     * taken as known, without a time (it is not known when they were added).
     */
    public static void adopt(TicketInfo ticket) {
        if (ticket == null) {
            return;
        }
        for (TicketLineInfo line : ticket.getLines()) {
            if (line.getProperty(QUANTITY) == null && line.getProperty(ADDED) == null) {
                line.setProperty(QUANTITY, quantity(line.getMultiply()));
            }
        }
    }

    /**
     * Short text for the sales screen, or "" when nothing is known, e.g.
     * "Agregado 25/10/26 14:32 Laura · cambió de 2 a 5 el 01/11/26 09:10 Carlos".
     */
    public static String label(TicketLineInfo line) {
        StringBuilder s = new StringBuilder();
        String added = shown(line.getProperty(ADDED));
        String addedBy = line.getProperty(ADDED_BY);
        if (added != null) {
            s.append("Agregado ").append(added);
            if (addedBy != null && !addedBy.isEmpty()) {
                s.append(' ').append(addedBy);
            }
        }
        String changed = shown(line.getProperty(CHANGED));
        if (changed != null) {
            if (s.length() > 0) {
                s.append(" · ");
            }
            s.append("cambió de ").append(line.getProperty(CHANGED_FROM)).append(" a ").append(line.getProperty(QUANTITY)).append(" el ").append(changed);
            String by = line.getProperty(CHANGED_BY);
            // the same person as above is not repeated
            if (by != null && !by.isEmpty() && !by.equals(added != null ? addedBy : null)) {
                s.append(' ').append(by);
            }
        }
        return s.toString();
    }

    /**
     * The value of the product column with the label as a small line under
     * it. The column already is HTML in Chromis (name, notes, attributes);
     * an empty last line is replaced, so the row height is enough.
     */
    public static String withLabel(String cell, TicketLineInfo line) {
        String label = line == null ? "" : label(line);
        if (label.isEmpty()) {
            return cell;
        }
        String small = "<font size=\"-2\">" + escape(label) + "</font>";
        String c = cell == null ? "" : cell;
        if (c.trim().toLowerCase(Locale.ROOT).startsWith("<html>")) {
            String body = c.replaceAll("(?is)(\\s*<br\\s*/?>\\s*)+$", "").replaceAll("(?is)\\s*</html>\\s*$", "");
            return body + "<br>" + small;
        }
        return "<html>" + escape(c) + "<br>" + small;
    }

    private static String shown(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            return new SimpleDateFormat(SHOWN).format(new SimpleDateFormat(STORED).parse(stored));
        } catch (ParseException ex) {
            return stored;
        }
    }

    private static void history(TicketLineInfo line, Date now, String user, String what) {
        String old = line.getProperty(HISTORY);
        String entry = new SimpleDateFormat(SHOWN).format(now) + (user == null || user.isEmpty() ? "" : " " + user) + ": " + what;
        String all = old == null || old.isEmpty() ? entry : old + "\n" + entry;
        if (all.length() > HISTORY_MAX) {
            // the oldest entries go first
            all = all.substring(all.length() - HISTORY_MAX);
            int nl = all.indexOf('\n');
            all = nl >= 0 ? all.substring(nl + 1) : all;
        }
        line.setProperty(HISTORY, all);
    }

    private static void put(TicketLineInfo line, String key, String value) {
        if (value == null || value.isEmpty()) {
            line.getProperties().remove(key);
        } else {
            line.setProperty(key, value);
        }
    }

    /** 2, 2.5, 0.125: as the cashier typed it. */
    static String quantity(double q) {
        if (q == Math.rint(q)) {
            return String.valueOf((long) q);
        }
        String s = String.format(Locale.ROOT, "%.3f", q);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
