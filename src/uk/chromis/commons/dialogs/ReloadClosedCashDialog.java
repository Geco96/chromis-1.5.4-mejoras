package uk.chromis.commons.dialogs;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.jdesktop.swingx.JXDatePicker;
import uk.chromis.basic.BasicException;
import uk.chromis.custom.CustomColour;
import uk.chromis.custom.CustomJLabel;
import uk.chromis.custom.ExtendedJButton;
import uk.chromis.data.loader.SerializerReadClass;
import uk.chromis.data.loader.Session;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.data.loader.StaticSentence;
import uk.chromis.pos.forms.AppLocal;
import uk.chromis.pos.forms.ChromisFonts;
import uk.chromis.pos.panels.ClosedCashInfo;

/**
 * Picks a closed cash to reload: day, terminal and sequence. Replaces
 * Chromis' dialog, which compared the sequence numbers with == on Integer
 * objects and so never found a cash after number 127; it also only matched
 * the day the cash started and returned the picked day as its start. Here a
 * cash is listed on the day it started and on the day it was closed, and the
 * cashes left open by an error (not the active cash of any terminal) are
 * listed too, marked "sin cerrar".
 */
public class ReloadClosedCashDialog extends JDialog {

    private static final Logger LOGGER = Logger.getLogger(ReloadClosedCashDialog.class.getName());

    private final Font font = ChromisFonts.DEFAULTFONTBOLD;
    private final JPanel panel = new JPanel(new MigLayout("insets 10 0 0 10 ", "", ""));
    private final JPanel messagePanel = new JPanel(new MigLayout("insets 10 0 0 0 ", "[150:150:150] 5 [300:300:300] 10 [150:150:150] ", ""));
    private final JPanel btnPanel = new JPanel();
    private ExtendedJButton btnOK;
    private static int CHOICE = -1;
    private final JComboBox<String> host = new JComboBox<>();
    private final JComboBox<Entry> sequence = new JComboBox<>();
    private final JXDatePicker datePicker = new JXDatePicker(new Date());
    private List<ClosedCashInfo> closedData = new ArrayList<>();
    private final DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");

    /** One cash in the sequence list. */
    private static final class Entry {

        final ClosedCashInfo cash;
        final String label;

        Entry(ClosedCashInfo cash, Date day) {
            this.cash = cash;
            DateFormat time = new SimpleDateFormat("HH:mm");
            DateFormat dayTime = new SimpleDateFormat("dd/MM HH:mm");
            DateFormat dayOnly = new SimpleDateFormat("dd/MM/yyyy");
            String pickedDay = dayOnly.format(day);
            boolean sameDay = cash.getEndDate() != null
                    && dayOnly.format(cash.getStartDate()).equals(dayOnly.format(cash.getEndDate()));
            DateFormat f = sameDay && dayOnly.format(cash.getStartDate()).equals(pickedDay) ? time : dayTime;
            this.label = cash.getHostSequence() + "   (" + f.format(cash.getStartDate()) + " – "
                    + (cash.getEndDate() == null ? "sin cerrar" : f.format(cash.getEndDate())) + ")";
        }

        @Override
        public String toString() {
            return label;
        }
    }

    protected ReloadClosedCashDialog() {
        super(new JFrame());
        CHOICE = -1;
        try {
            reloadPane();
        } catch (BasicException ex) {
            LOGGER.log(Level.WARNING, "No se pudieron leer los cortes", ex);
        }
        pack();
    }

    protected void reloadPane() throws BasicException {
        setButtonPanel(new Dimension(100, 35));
        btnOK.setEnabled(false);
        Session session = SessionFactory.getSession();
        closedData = new StaticSentence(session,
                "select datestart, dateend, host, hostsequence, money from closedcash "
                + "where dateend is not null "
                + "or money not in (select active_cash from terminals where active_cash is not null) "
                + "order by datestart desc",
                null,
                new SerializerReadClass(ClosedCashInfo.class)).list();

        datePicker.setPreferredSize(new Dimension(300, 26));
        messagePanel.add(new CustomJLabel(AppLocal.getIntString("message.closedcashdate"), font), "align right");
        messagePanel.add(datePicker, "wrap");
        messagePanel.add(new CustomJLabel(AppLocal.getIntString("message.closedhost"), font), "align right");
        host.setPreferredSize(new Dimension(300, 25));
        messagePanel.add(host, "wrap");
        messagePanel.add(new CustomJLabel(AppLocal.getIntString("message.closesequence"), font), "align right");
        sequence.setPreferredSize(new Dimension(300, 25));
        messagePanel.add(sequence, "wrap");

        datePicker.addActionListener(e -> fillHosts());
        host.addActionListener(e -> fillSequences());
        sequence.addActionListener(e -> btnOK.setEnabled(sequence.getSelectedItem() != null));
        fillHosts();

        panel.add(messagePanel, "wrap");
        panel.add(btnPanel, "span,  align center, wrap");
        setResizable(false);
        setModal(true);
        panel.setBorder(BorderFactory.createLineBorder(CustomColour.getBorderColour(), 2));
        setTitle("Closed Cash Selector");
        getContentPane().add(panel);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    /** Cashes that started or ended on the picked day. */
    private List<ClosedCashInfo> ofPickedDay() {
        List<ClosedCashInfo> list = new ArrayList<>();
        Date day = datePicker.getDate();
        if (day == null) {
            return list;
        }
        String picked = dateFormat.format(day);
        for (ClosedCashInfo c : closedData) {
            if (picked.equals(dateFormat.format(c.getStartDate()))
                    || (c.getEndDate() != null && picked.equals(dateFormat.format(c.getEndDate())))) {
                list.add(c);
            }
        }
        return list;
    }

    private void fillHosts() {
        TreeSet<String> hosts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ClosedCashInfo c : ofPickedDay()) {
            hosts.add(c.getHost());
        }
        host.removeAllItems();
        hosts.forEach(host::addItem);
        fillSequences();
    }

    private void fillSequences() {
        sequence.removeAllItems();
        Object selectedHost = host.getSelectedItem();
        if (selectedHost != null) {
            List<ClosedCashInfo> list = ofPickedDay();
            list.removeIf(c -> !c.getHost().equalsIgnoreCase(selectedHost.toString()));
            list.sort(Comparator.comparing(ClosedCashInfo::getHostSequence));
            Date day = datePicker.getDate();
            for (ClosedCashInfo c : list) {
                sequence.addItem(new Entry(c, day));
            }
        }
        btnOK.setEnabled(sequence.getSelectedItem() != null);
    }

    private void setButtonPanel(Dimension dimension) {
        btnOK = new ExtendedJButton(AppLocal.getIntString("button.ok"), JAlertPane.OK);
        btnOK.setPreferredSize(dimension);
        btnOK.setFont(font);
        btnOK.addActionListener(e -> {
            CHOICE = ((ExtendedJButton) e.getSource()).getBtnChoice();
            dispose();
        });
        btnPanel.add(btnOK);

        ExtendedJButton btn = new ExtendedJButton(AppLocal.getIntString("button.cancel"), JAlertPane.CANCEL);
        btn.setPreferredSize(dimension);
        btn.setFont(font);
        btn.addActionListener(e -> {
            CHOICE = ((ExtendedJButton) e.getSource()).getBtnChoice();
            dispose();
        });
        btnPanel.add(btn);
    }

    protected int getChoice() {
        return CHOICE;
    }

    /** The picked cash with its own start and end (end is null for a cash left open). */
    protected ClosedCashInfo getClosedData() {
        Entry e = (Entry) sequence.getSelectedItem();
        if (e == null) {
            return null;
        }
        ClosedCashInfo c = e.cash;
        return new ClosedCashInfo(c.getStartDate(), c.getEndDate(), c.getHost(), c.getHostSequence(), c.getMoneyGuid());
    }
}
