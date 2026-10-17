package uk.chromis.pos.sales;

import java.awt.Color;
import java.awt.Component;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import uk.chromis.pos.restock.RestockStore;
import uk.chromis.pos.restock.RestockStore.Status;
import uk.chromis.pos.ticket.TicketInfo;
import uk.chromis.pos.ticket.TicketLineInfo;

/**
 * Restock warnings on the sales screen: after each sale, products that went
 * down to reserve, low or out of stock are shown in a small notice that does
 * not take the focus, so the scanner keeps typing into the ticket.
 *
 * Works on its own connection and thread so a slow query never delays the
 * sale, and never shares the POS connection while it is saving a ticket.
 */
public final class RestockAlerts {

    private static final Logger LOGGER = Logger.getLogger(RestockAlerts.class.getName());
    private static final int MAX_NAMES = 4;

    private final String url;
    private final String user;
    private final String password;
    private final String location;
    private final Component owner;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "avisos-reposicion");
        t.setDaemon(true);
        return t;
    });
    private Connection connection;
    private static boolean startupShown = false;

    public RestockAlerts(String url, String user, String password, String location, Component owner) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.location = location;
        this.owner = owner;
        worker.execute(this::startup);
    }

    /** Call after the ticket was saved, with the lines it had. */
    public void afterSale(TicketInfo ticket) {
        if (ticket == null || ticket.isRefund()) {
            return;
        }
        // Units taken out of stock by this ticket, per product
        Map<String, Double> sold = new LinkedHashMap<>();
        for (TicketLineInfo line : ticket.getLines()) {
            if (line.getProductID() != null && line.getManageStock() && !line.isProductService()) {
                sold.merge(line.getProductID(), line.getMultiply(), Double::sum);
            }
        }
        if (!sold.isEmpty()) {
            worker.execute(() -> checkSold(sold));
        }
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

    private void startup() {
        try {
            Connection con = connection();
            RestockStore.createTables(con);
            synchronized (RestockAlerts.class) {
                // The sales panel can be created more than once; summarize once per POS start
                if (startupShown) {
                    return;
                }
                startupShown = true;
            }
            if (!RestockStore.loadSettings(con).posAlerts) {
                return;
            }
            int out = 0;
            int low = 0;
            for (RestockStore.Row r : RestockStore.load(con, location, null)) {
                Status s = r.status();
                if (s == Status.AGOTADO) {
                    out++;
                } else if (s == Status.BAJO) {
                    low++;
                }
            }
            if (out + low > 0) {
                List<String> parts = new ArrayList<>();
                if (out > 0) {
                    parts.add(out + (out == 1 ? " producto agotado" : " productos agotados"));
                }
                if (low > 0) {
                    parts.add(low + (low == 1 ? " producto bajo el mínimo" : " productos bajo el mínimo"));
                }
                show("REPOSICIÓN", String.join(" y ", parts) + ".\nRevisa Chromis Admin → Reposición.",
                        RestockToast.colorFor(out > 0 ? Status.AGOTADO : Status.BAJO));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo revisar el stock al iniciar", ex);
        }
    }

    private void checkSold(Map<String, Double> sold) {
        try {
            Connection con = connection();
            RestockStore.Settings settings = RestockStore.loadSettings(con);
            if (!settings.posAlerts) {
                return;
            }
            Map<Status, List<String>> alerts = new LinkedHashMap<>();
            for (RestockStore.Row r : RestockStore.load(con, location, sold.keySet())) {
                if (!r.hasThresholds()) {
                    continue;
                }
                Status after = r.status();
                Status before = RestockStore.classify(r.current + sold.get(r.productId), r.minimum, r.reserve);
                if (after.severity() >= before.severity() || after == Status.ACEPTABLE) {
                    continue;
                }
                if (after == Status.RESERVA && !settings.alertReserve) {
                    continue;
                }
                alerts.computeIfAbsent(after, k -> new ArrayList<>()).add(r.name);
            }
            for (Map.Entry<Status, List<String>> e : alerts.entrySet()) {
                show(title(e.getKey(), e.getValue().size()), names(e.getValue()), RestockToast.colorFor(e.getKey()));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "No se pudo revisar el stock después de la venta", ex);
        }
    }

    private static String title(Status status, int count) {
        switch (status) {
            case AGOTADO:
                return count == 1 ? "SE AGOTÓ" : "SE AGOTARON";
            case BAJO:
                return "BAJO EL MÍNIMO";
            default:
                return "EN RESERVA";
        }
    }

    private static String names(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size() && i < MAX_NAMES; i++) {
            sb.append(i == 0 ? "" : "\n").append(names.get(i));
        }
        if (names.size() > MAX_NAMES) {
            sb.append("\ny ").append(names.size() - MAX_NAMES).append(" más");
        }
        return sb.toString();
    }

    private void show(String title, String message, Color color) {
        SwingUtilities.invokeLater(() -> RestockToast.show(owner, title, message, color));
    }
}
