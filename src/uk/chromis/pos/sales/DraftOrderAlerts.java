package uk.chromis.pos.sales;

import java.awt.Color;
import java.awt.Component;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import uk.chromis.pos.forms.AppUser;
import uk.chromis.pos.restock.DraftOrders;

/**
 * Daily draft order on the POS side. Every minute it builds the draft when it
 * is time (only one terminal does it; see DraftOrders.runIfDue) and, while
 * there is a draft waiting for review, tells the users with the Orders
 * permission once each, when they are on the sales screen.
 *
 * One instance per application, with its own connection and thread, like
 * the restock notices.
 */
public final class DraftOrderAlerts {

    private static final Logger LOGGER = Logger.getLogger(DraftOrderAlerts.class.getName());
    private static final long CHECK_SECONDS = 60;
    private static final String PERMISSION = "Orders";
    private static final Color COLOR = new Color(0x1E, 0x6F, 0xC8);

    private static DraftOrderAlerts instance;

    private final String url;
    private final String user;
    private final String password;
    private final String terminal;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pedido-tentativo");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private Connection connection;
    private volatile Component owner;
    private volatile Supplier<String> currentUser;
    /** Users already told about the drafts built at that moment. */
    private Timestamp notifiedFor;
    private final Set<String> notifiedUsers = new HashSet<>();

    private DraftOrderAlerts(String url, String user, String password, String terminal) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.terminal = terminal;
    }

    /** Starts the checks once per application. */
    public static synchronized DraftOrderAlerts start(String url, String user, String password, String terminal) {
        if (instance == null) {
            instance = new DraftOrderAlerts(url, user, password, terminal);
            instance.worker.scheduleWithFixedDelay(instance::checkSafely, 20, CHECK_SECONDS, TimeUnit.SECONDS);
        }
        return instance;
    }

    /** Call when the sales screen is shown: a user who just logged in is told at once. */
    public void activated(Component owner, Supplier<String> currentUser) {
        this.owner = owner;
        this.currentUser = currentUser;
        worker.execute(this::notifySafely);
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

    private void checkSafely() {
        try {
            DraftOrders.Result built = DraftOrders.runIfDue(connection(), terminal, LocalDateTime.now());
            if (built != null) {
                LOGGER.info("Pedido tentativo armado: " + built.describe());
            }
        } catch (Throwable t) {
            // Never let an exception stop the scheduler
            LOGGER.log(Level.WARNING, "No se pudo armar el pedido tentativo", t);
        }
        notifySafely();
    }

    private void notifySafely() {
        try {
            notifyUser();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "No se pudo revisar el pedido tentativo", t);
        }
    }

    private void notifyUser() throws SQLException {
        Component panel = owner;
        Supplier<String> who = currentUser;
        if (panel == null || who == null || !panel.isShowing()) {
            return;
        }
        DraftOrders.Summary s = DraftOrders.summary(connection());
        if (s.drafts == 0 || s.products == 0 || s.latest == null) {
            return;
        }
        String text = s.products + (s.products == 1 ? " producto" : " productos") + " para "
                + s.drafts + (s.drafts == 1 ? " proveedor" : " proveedores")
                + ".\nRevísalo y envíalo en Chromis Admin → Pedido tentativo.";
        SwingUtilities.invokeLater(() -> {
            String userId = who.get();
            // hasPermission reads the user logged in now
            if (userId == null || !panel.isShowing() || !AppUser.hasPermission(PERMISSION)) {
                return;
            }
            synchronized (notifiedUsers) {
                if (!s.latest.equals(notifiedFor)) {
                    notifiedFor = s.latest;
                    notifiedUsers.clear();
                }
                if (!notifiedUsers.add(userId)) {
                    return;
                }
            }
            RestockToast.show(panel, "PEDIDO TENTATIVO LISTO", text, COLOR);
        });
    }
}
