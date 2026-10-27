package uk.chromis.pos.sales;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import uk.chromis.pos.delivery.SaleConversion;

/**
 * At the till: a sale already charged as a counter sale becomes a delivery.
 * The cashier types or scans the ticket number, checks the sale and the
 * delivery data (filled in from the customer) and confirms.
 */
public final class ConvertSaleDialog extends JDialog {

    private static final Font FONT = new Font("SansSerif", Font.PLAIN, 16);
    private static final Font BIG = new Font("SansSerif", Font.BOLD, 18);
    private static final String DATE_PATTERN = "dd/MM/yyyy";

    private final Connection con;
    private final String userId;
    private final String userName;
    private final JTextField txtTicket = field(10);
    private final JLabel lblSale = new JLabel(" ");
    private final JLabel lblProblem = new JLabel(" ");
    private final JList<String> lines = new JList<>();
    private final JTextField txtName = field(30);
    private final JTextField txtAddress1 = field(30);
    private final JTextField txtAddress2 = field(30);
    private final JTextField txtAddress3 = field(30);
    private final JTextField txtPostcode = field(30);
    private final JTextField txtPhone = field(30);
    private final JTextField txtDate = field(30);
    private final JTextArea txtComments = new JTextArea(3, 30);
    private final JCheckBox chkSave = new JCheckBox("Guardar esta dirección en el cliente");
    private final JPanel form = new JPanel(new GridBagLayout());
    private final JButton btnConvert = new JButton("Convertir en entrega");
    private SaleConversion.Sale sale;
    private boolean converted;

    private ConvertSaleDialog(Window owner, Connection con, String userId, String userName) {
        super(owner, "Convertir venta en entrega", ModalityType.APPLICATION_MODAL);
        this.con = con;
        this.userId = userId;
        this.userName = userName;
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        JLabel help = new JLabel("<html>Una venta ya cobrada como mostrador pasa a ser entrega a domicilio. "
                + "No cambia la venta (importes, pagos, corte ni inventario).</html>");
        help.setFont(FONT);
        top.add(help, BorderLayout.NORTH);
        JPanel search = new JPanel(new BorderLayout(8, 0));
        JLabel lblTicket = new JLabel("Número de ticket:");
        lblTicket.setFont(BIG);
        JButton btnFind = new JButton("Buscar");
        btnFind.setFont(BIG);
        btnFind.addActionListener(e -> find());
        txtTicket.addActionListener(e -> find());
        search.add(lblTicket, BorderLayout.WEST);
        search.add(txtTicket, BorderLayout.CENTER);
        search.add(btnFind, BorderLayout.EAST);
        top.add(search, BorderLayout.CENTER);
        JPanel info = new JPanel(new BorderLayout(0, 4));
        lblSale.setFont(BIG);
        lblProblem.setFont(FONT);
        lblProblem.setForeground(new Color(0xB0, 0x00, 0x20));
        info.add(lblSale, BorderLayout.NORTH);
        lines.setFont(FONT);
        lines.setVisibleRowCount(4);
        info.add(new JScrollPane(lines), BorderLayout.CENTER);
        info.add(lblProblem, BorderLayout.SOUTH);
        top.add(info, BorderLayout.SOUTH);
        content.add(top, BorderLayout.NORTH);

        txtComments.setFont(FONT);
        txtComments.setLineWrap(true);
        txtComments.setWrapStyleWord(true);
        chkSave.setFont(FONT);
        int row = 0;
        addRow("Recibe", txtName, row++);
        addRow("Dirección", txtAddress1, row++);
        addRow("Colonia / referencias", txtAddress2, row++);
        addRow("Ciudad", txtAddress3, row++);
        addRow("C.P.", txtPostcode, row++);
        addRow("Teléfono", txtPhone, row++);
        addRow("Fecha de entrega (dd/mm/aaaa)", txtDate, row++);
        addRow("Indicaciones para el repartidor", new JScrollPane(txtComments), row++);
        addRow("", chkSave, row);
        content.add(form, BorderLayout.CENTER);
        setFormEnabled(false);

        JButton cancel = new JButton("Cancelar");
        cancel.setFont(BIG);
        btnConvert.setFont(BIG);
        cancel.addActionListener(e -> dispose());
        btnConvert.addActionListener(e -> convert());
        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.add(cancel);
        buttons.add(btnConvert);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(txtTicket::requestFocusInWindow);
    }

    /** Shows the dialog; true when a sale was converted. */
    public static boolean show(Component owner, Connection con, String userId, String userName) {
        Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        ConvertSaleDialog d = new ConvertSaleDialog(w, con, userId, userName);
        d.setVisible(true);
        return d.converted;
    }

    private static JTextField field(int columns) {
        JTextField f = new JTextField(columns);
        f.setFont(FONT);
        return f;
    }

    private void addRow(String label, JComponent field, int row) {
        JLabel l = new JLabel(label.isEmpty() ? "" : label + ":");
        l.setFont(FONT);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.LINE_END;
        c.insets = new Insets(3, 0, 3, 8);
        form.add(l, c);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.gridy = row;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(3, 0, 3, 0);
        form.add(field, c);
    }

    private void setFormEnabled(boolean enabled) {
        for (Component c : form.getComponents()) {
            c.setEnabled(enabled);
        }
        txtComments.setEnabled(enabled);
        btnConvert.setEnabled(enabled);
    }

    private void find() {
        sale = null;
        setFormEnabled(false);
        lblSale.setText(" ");
        lblProblem.setText(" ");
        lines.setListData(new String[0]);
        int number;
        try {
            number = Integer.parseInt(txtTicket.getText().trim());
        } catch (NumberFormatException ex) {
            lblProblem.setText("Escribe el número de ticket (solo números).");
            return;
        }
        try {
            SaleConversion.Sale s = SaleConversion.find(con, number);
            if (s != null) {
                lblSale.setText(s.summary());
                lines.setListData(s.lines.toArray(new String[0]));
            }
            String problem = SaleConversion.problem(s, new Date());
            if (problem != null) {
                lblProblem.setText("<html>" + problem + "</html>");
                pack();
                return;
            }
            sale = s;
            SaleConversion.Data d = SaleConversion.Data.fromCustomer(s);
            txtName.setText(d.name);
            txtAddress1.setText(d.address1);
            txtAddress2.setText(d.address2);
            txtAddress3.setText(d.address3);
            txtPostcode.setText(d.postcode);
            txtPhone.setText(d.phone);
            txtDate.setText(new SimpleDateFormat(DATE_PATTERN).format(new Date()));
            txtComments.setText("");
            boolean noAddress = s.customerAddress == null || s.customerAddress.trim().isEmpty();
            chkSave.setVisible(noAddress);
            chkSave.setSelected(noAddress);
            setFormEnabled(true);
            pack();
            SwingUtilities.invokeLater(txtAddress1.getText().isEmpty() ? txtAddress1::requestFocusInWindow
                    : txtComments::requestFocusInWindow);
        } catch (SQLException ex) {
            lblProblem.setText("No se pudo buscar la venta: " + ex.getMessage());
        }
    }

    private void convert() {
        if (sale == null) {
            return;
        }
        SaleConversion.Data d = new SaleConversion.Data();
        d.name = txtName.getText();
        d.address1 = txtAddress1.getText();
        d.address2 = txtAddress2.getText();
        d.address3 = txtAddress3.getText();
        d.postcode = txtPostcode.getText();
        d.phone = txtPhone.getText();
        d.comments = txtComments.getText();
        d.saveToCustomer = chkSave.isVisible() && chkSave.isSelected();
        try {
            SimpleDateFormat f = new SimpleDateFormat(DATE_PATTERN);
            f.setLenient(false);
            d.deliveryDate = f.parse(txtDate.getText().trim());
        } catch (ParseException ex) {
            lblProblem.setText("La fecha de entrega va como día/mes/año, por ejemplo "
                    + new SimpleDateFormat(DATE_PATTERN).format(new Date()) + ".");
            txtDate.requestFocusInWindow();
            return;
        }
        String problem = SaleConversion.invalid(d);
        if (problem != null) {
            lblProblem.setText(problem);
            txtAddress1.requestFocusInWindow();
            return;
        }
        try {
            SaleConversion.convert(con, sale, d, userId, userName, "CAJA", "caja");
            converted = true;
            dispose();
        } catch (SQLException ex) {
            lblProblem.setText("No se pudo convertir: " + ex.getMessage());
        }
    }
}
