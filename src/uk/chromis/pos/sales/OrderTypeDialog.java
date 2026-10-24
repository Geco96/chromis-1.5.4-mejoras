package uk.chromis.pos.sales;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import uk.chromis.pos.customers.CustomerInfoExt;
import uk.chromis.pos.delivery.DeliveryStore;

/**
 * Asks whether the ticket is a counter sale or a delivery. For a delivery it
 * shows Chromis' delivery data (the ones Printer.Delivery prints and
 * customer_delivery keeps) filled in from the customer, so the cashier only
 * checks them and adds the instructions for the driver.
 */
public final class OrderTypeDialog extends JDialog {

    private static final Font FONT = new Font("SansSerif", Font.PLAIN, 16);
    private static final Font BIG = new Font("SansSerif", Font.BOLD, 18);
    private static final Color COUNTER_COLOR = new Color(0x55, 0x55, 0x55);
    private static final Color DELIVERY_COLOR = new Color(0x1E, 0x6F, 0xC8);
    private static final String DATE_PATTERN = "dd/MM/yyyy";

    /** What was chosen; null from show() when the cashier cancels. */
    public static final class Result {

        public boolean delivery;
        public CustomerDeliveryInfo info;
        /** The customer had no address and the cashier chose to keep the typed one in Customers. */
        public boolean saveToCustomer;
    }

    private final JToggleButton btnCounter = new JToggleButton("MOSTRADOR");
    private final JToggleButton btnDelivery = new JToggleButton("ENTREGA A DOMICILIO");
    private final JTextField txtName = field();
    private final JTextField txtAddress1 = field();
    private final JTextField txtAddress2 = field();
    private final JTextField txtAddress3 = field();
    private final JTextField txtPostcode = field();
    private final JTextField txtPhone = field();
    private final JTextField txtDate = field();
    private final JTextArea txtComments = new JTextArea(3, 30);
    private final JPanel details = new JPanel(new GridBagLayout());
    private final boolean hasCustomer;
    /** The customer record has no address: offer to save the one typed here. */
    private final boolean customerWithoutAddress;
    private final String customerName;
    private Result result;

    private OrderTypeDialog(Window owner, String ticketId, CustomerInfoExt customer, CustomerDeliveryInfo current,
            boolean delivery) {
        super(owner, "Tipo de pedido", ModalityType.APPLICATION_MODAL);
        hasCustomer = customer != null;
        customerWithoutAddress = customer != null && text(customer.getAddress()).isEmpty();
        customerName = customer == null ? "" : text(customer.getName());
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        ButtonGroup group = new ButtonGroup();
        group.add(btnCounter);
        group.add(btnDelivery);
        JPanel choice = new JPanel(new GridLayout(1, 2, 12, 0));
        for (JToggleButton b : new JToggleButton[]{btnCounter, btnDelivery}) {
            b.setFont(BIG);
            b.setFocusPainted(false);
            b.setPreferredSize(new Dimension(240, 64));
            b.addActionListener(e -> updateState());
            choice.add(b);
        }
        content.add(choice, BorderLayout.NORTH);

        txtComments.setFont(FONT);
        txtComments.setLineWrap(true);
        txtComments.setWrapStyleWord(true);
        int row = 0;
        if (customer != null) {
            JLabel lbl = new JLabel("Cliente: " + customer.getName());
            lbl.setFont(BIG);
            add(details, lbl, row++);
        }
        addRow("Recibe", txtName, row++);
        addRow("Dirección", txtAddress1, row++);
        addRow("Colonia / referencias", txtAddress2, row++);
        addRow("Ciudad", txtAddress3, row++);
        addRow("C.P.", txtPostcode, row++);
        addRow("Teléfono", txtPhone, row++);
        addRow("Fecha de entrega (dd/mm/aaaa)", txtDate, row++);
        JScrollPane comments = new JScrollPane(txtComments);
        addRow("Indicaciones para el repartidor", comments, row);
        content.add(details, BorderLayout.CENTER);

        JButton ok = new JButton("Aceptar");
        JButton cancel = new JButton("Cancelar");
        ok.setFont(BIG);
        cancel.setFont(BIG);
        ok.addActionListener(e -> accept(ticketId));
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.add(cancel);
        buttons.add(ok);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(ok);

        fill(customer, current);
        (delivery ? btnDelivery : btnCounter).setSelected(true);
        updateState();
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the dialog. current is the delivery data the ticket already has
     * (null if none); delivery says which option starts selected.
     */
    public static Result show(Component owner, String ticketId, CustomerInfoExt customer, CustomerDeliveryInfo current,
            boolean delivery) {
        Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        OrderTypeDialog d = new OrderTypeDialog(w, ticketId, customer, current, delivery);
        d.setVisible(true);
        return d.result;
    }

    private static JTextField field() {
        JTextField f = new JTextField(30);
        f.setFont(FONT);
        return f;
    }

    private void addRow(String label, JComponent field, int row) {
        JLabel l = new JLabel(label + ":");
        l.setFont(FONT);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.LINE_END;
        c.insets = new Insets(3, 0, 3, 8);
        details.add(l, c);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(3, 0, 3, 0);
        details.add(field, c);
    }

    private static void add(JPanel panel, JComponent comp, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(comp, c);
    }

    private void fill(CustomerInfoExt customer, CustomerDeliveryInfo current) {
        if (current != null) {
            txtName.setText(text(current.fetchName()));
            txtAddress1.setText(text(current.fetchAddressLine1()));
            txtAddress2.setText(text(current.fetchAddressLine2()));
            txtAddress3.setText(text(current.fetchAddressLine3()));
            txtPostcode.setText(text(current.fetchPostCode()));
            txtPhone.setText(text(current.fetchPhone()));
            txtComments.setText(text(current.fetchComments()));
            Date when = current.fetchDeliveryDate();
            txtDate.setText(new SimpleDateFormat(DATE_PATTERN).format(when == null ? new Date() : when));
            return;
        }
        txtDate.setText(new SimpleDateFormat(DATE_PATTERN).format(new Date()));
        if (customer == null) {
            return;
        }
        txtName.setText(text(customer.getName()));
        txtAddress1.setText(text(customer.getAddress()));
        String address2 = text(customer.getAddress2());
        // The map web stores the coordinates there; they are of no use on paper
        txtAddress2.setText(address2.startsWith("GPS ") ? "" : address2);
        String city = text(customer.getCity());
        String region = text(customer.getRegion());
        txtAddress3.setText(region.isEmpty() || region.equalsIgnoreCase(city) ? city
                : city.isEmpty() ? region : city + ", " + region);
        txtPostcode.setText(text(customer.getPostal()));
        String phone = text(customer.getPhone());
        txtPhone.setText(phone.isEmpty() ? text(customer.getPhone2()) : phone);
    }

    private static String text(String s) {
        return s == null ? "" : s.trim();
    }

    private void updateState() {
        boolean delivery = btnDelivery.isSelected();
        btnDelivery.setForeground(delivery ? DELIVERY_COLOR : Color.DARK_GRAY);
        btnCounter.setForeground(delivery ? Color.DARK_GRAY : COUNTER_COLOR);
        for (Component c : details.getComponents()) {
            c.setEnabled(delivery);
        }
        txtComments.setEnabled(delivery);
        if (delivery) {
            SwingUtilities.invokeLater(txtComments::requestFocusInWindow);
        }
    }

    private void accept(String ticketId) {
        Result r = new Result();
        r.delivery = btnDelivery.isSelected();
        if (r.delivery) {
            if (!hasCustomer) {
                // The delivery is placed on the route by its customer
                JOptionPane.showMessageDialog(this, "Para una entrega primero asigna el cliente al ticket (botón Cliente).",
                        "Tipo de pedido", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (txtAddress1.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Escribe la dirección de entrega.", "Tipo de pedido",
                        JOptionPane.WARNING_MESSAGE);
                txtAddress1.requestFocusInWindow();
                return;
            }
            Date when;
            try {
                SimpleDateFormat f = new SimpleDateFormat(DATE_PATTERN);
                f.setLenient(false);
                when = f.parse(txtDate.getText().trim());
            } catch (ParseException ex) {
                JOptionPane.showMessageDialog(this, "La fecha de entrega va como día/mes/año, por ejemplo "
                        + new SimpleDateFormat(DATE_PATTERN).format(new Date()) + ".", "Tipo de pedido",
                        JOptionPane.WARNING_MESSAGE);
                txtDate.requestFocusInWindow();
                return;
            }
            r.info = new CustomerDeliveryInfo(ticketId,
                    DeliveryStore.fit(txtName.getText(), DeliveryStore.NAME_SIZE),
                    DeliveryStore.fit(txtAddress1.getText(), DeliveryStore.ADDRESS_SIZE),
                    DeliveryStore.fit(txtAddress2.getText(), DeliveryStore.ADDRESS_SIZE),
                    DeliveryStore.fit(txtAddress3.getText(), DeliveryStore.ADDRESS_SIZE),
                    DeliveryStore.fit(txtPostcode.getText(), DeliveryStore.POSTCODE_SIZE),
                    DeliveryStore.fit(txtPhone.getText(), DeliveryStore.PHONE_SIZE),
                    when, false,
                    DeliveryStore.fit(txtComments.getText(), DeliveryStore.COMMENTS_SIZE));
            if (customerWithoutAddress) {
                Object[] options = {"Sí, guardarla", "No"};
                r.saveToCustomer = JOptionPane.showOptionDialog(this,
                        customerName + " no tiene dirección en su ficha (Customers).\n"
                        + "¿Guardar esta dirección en el cliente para las próximas ventas?",
                        "Dirección del cliente", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                        options, options[0]) == 0;
            }
        }
        result = r;
        dispose();
    }
}
