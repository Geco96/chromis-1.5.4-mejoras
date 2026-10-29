package uk.chromis.pos.sales;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import uk.chromis.pos.heldtickets.HeldTicketArchive;
import uk.chromis.pos.ticket.TicketInfo;
import uk.chromis.pos.ticket.TicketLineInfo;

/**
 * At the till: keeps the copies of held tickets that left the list (see
 * HeldTicketArchive) readable for Chromis Admin, which cannot open a ticket.
 * Every minute it reads the new copies (ticket, customer, lines, total),
 * drops the empty ones, keeps only the summary of charged ones, and marks the
 * held ticket this till has open, so Admin can tell it is open here and not
 * lost. It also refreshes the held tickets button when the list changed
 * elsewhere (a restored ticket, or one held at another till), which Chromis
 * only did after a click. Own connection and thread, so it never touches the
 * sale; nothing it does can stop the till.
 */
public final class HeldTicketWatcher {

    private static final Logger LOGGER = Logger.getLogger(HeldTicketWatcher.class.getName());
    private static final int BATCH = 200;
    private static HeldTicketWatcher instance;
    private static ScheduledExecutorService timer;
    /** Refreshes the held tickets button of the sales panel (on the Swing thread). */
    private static volatile Runnable refresher;
    /** Id of the held ticket on screen at this till. */
    private static volatile Supplier<String> current;
    private String lastList;
    private boolean ready;

    private final String url;
    private final String user;
    private final String password;
    private Connection connection;

    private HeldTicketWatcher(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Starts once per Chromis POS (the sales panel can be created more than once). */
    public static synchronized void start(String url, String user, String password, Runnable refreshButton,
            Supplier<String> currentHeldTicket) {
        refresher = refreshButton;
        current = currentHeldTicket;
        if (instance != null) {
            return;
        }
        instance = new HeldTicketWatcher(url, user, password);
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ventas-en-espera-borradas");
            t.setDaemon(true);
            return t;
        });
        timer.scheduleWithFixedDelay(instance::run, 0, 1, TimeUnit.MINUTES);
    }

    /**
     * A held ticket was just opened at this till: noted (on the watcher's own
     * thread and connection) so Admin shows where it is.
     */
    public static void opened(String id, String userName, String terminal) {
        ScheduledExecutorService t = timer;
        HeldTicketWatcher w = instance;
        if (t == null || w == null) {
            return;
        }
        t.execute(() -> {
            try {
                HeldTicketArchive.opened(w.connection(), id, userName, terminal);
            } catch (Throwable ex) {
                LOGGER.log(Level.FINE, "No se pudo anotar quién abrió la venta en espera", ex);
            }
        });
    }

    private Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(5)) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ex) {
                    // replaced below
                }
            }
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    private void run() {
        try {
            Connection con = connection();
            if (!ready) {
                // Tried again every minute until it works (a backup in progress makes it give up quickly)
                String problem = HeldTicketArchive.ensure(con);
                ready = problem == null;
                if (!ready) {
                    LOGGER.warning(problem);
                }
            }
            List<Object[]> pending = HeldTicketArchive.pending(con, BATCH);
            for (Object[] p : pending) {
                read(con, (Long) p[0], (byte[]) p[1]);
            }
            HeldTicketArchive.purge(con);
            Supplier<String> c = current;
            String open = c == null ? null : c.get();
            if (open != null) {
                HeldTicketArchive.stillOpen(con, open);
            }
            String list = HeldTicketArchive.listSignature(con);
            if (lastList != null && !lastList.equals(list) && refresher != null) {
                SwingUtilities.invokeLater(refresher);
            }
            lastList = list;
        } catch (SQLException ex) {
            // The table may not exist (no rights to create it): nothing to do
            LOGGER.log(Level.FINE, "Ventas en espera borradas", ex);
        } catch (Throwable ex) {
            // Anything else is logged and the next minute runs again (an uncaught error would stop it for good)
            LOGGER.log(Level.WARNING, "Ventas en espera borradas", ex);
        }
    }

    /** Reads one copy (public for the tests). */
    public static void read(Connection con, long archiveId, byte[] content) throws SQLException {
        TicketInfo t;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(content))) {
            t = (TicketInfo) in.readObject();
        } catch (Exception | LinkageError ex) {
            LOGGER.log(Level.WARNING, "No se pudo leer la venta en espera " + archiveId, ex);
            HeldTicketArchive.unreadable(con, archiveId);
            return;
        }
        StringBuilder text = new StringBuilder();
        double total = 0;
        for (TicketLineInfo l : t.getLines()) {
            double u = l.getMultiply();
            text.append(u == Math.rint(u) ? String.valueOf((long) u) : String.valueOf(u))
                    .append(" x ").append(l.getProductName() == null ? "" : l.getProductName())
                    .append(String.format(java.util.Locale.ROOT, "  $%.2f", l.getValue())).append('\n');
            total += l.getValue();
        }
        String customer = null;
        try {
            customer = t.getCustomer() == null ? null : t.getCustomer().getName();
        } catch (RuntimeException ex) {
            // only for display
        }
        HeldTicketArchive.summarize(con, archiveId, t.getId(), customer, total, t.getLinesCount(), text.toString().trim());
    }
}
