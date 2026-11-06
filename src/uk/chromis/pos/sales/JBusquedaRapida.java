package uk.chromis.pos.sales;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.Connection;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import uk.chromis.data.loader.SessionFactory;
import uk.chromis.format.Formats;
import uk.chromis.globals.SystemProperty;
import uk.chromis.pos.datalogic.DataLogicSales;
import uk.chromis.pos.search.ProductoBusqueda;
import uk.chromis.pos.search.SearchIndex;
import uk.chromis.pos.ticket.ProductInfoExt;

/**
 * Buscador rapido de la caja (misma busqueda que la venta de Chromis Android):
 * filtra mientras se escribe por codigo, referencia, codigos alternos,
 * nombre y etiquetas; entiende acentos, plurales, sinonimos y errores de dedo
 * ("Tal vez quisiste decir: ...") y, con pocos resultados, muestra productos
 * parecidos. Flechas para moverse, Enter agrega, Esc cierra. El buscador
 * clasico de Chromis sigue a un boton de distancia.
 */
public class JBusquedaRapida extends JDialog {

    private static final Logger LOGGER = Logger.getLogger(JBusquedaRapida.class.getName());
    private static final int MIN_CHARS = 2;
    private static final int DEBOUNCE_MS = 200;
    private static final Color BLUE = new Color(0x1F5FBF);
    private static final Color SUGGEST_BG = new Color(0xE3F2FD);

    private final DataLogicSales dlSales;
    private final String siteGuid;
    private final JTextField txt = new JTextField();
    private final JLabel lblInfo = new JLabel(" ");
    private final JLabel lblSuggest = new JLabel();
    private final JPanel pnlSuggest = new JPanel(new BorderLayout());
    private final DefaultListModel<Object> model = new DefaultListModel<>();
    private final JList<Object> list = new JList<>(model);
    private final Timer timer;
    private String suggestion;
    private ProductInfoExt selected;
    private boolean classic;

    private JBusquedaRapida(Window owner, DataLogicSales dlSales, String siteGuid) {
        super(owner, "Buscar producto", ModalityType.APPLICATION_MODAL);
        this.dlSales = dlSales;
        this.siteGuid = siteGuid;
        this.timer = new Timer(DEBOUNCE_MS, e -> search());
        this.timer.setRepeats(false);
        build();
    }

    /**
     * Abre el buscador. Devuelve el producto elegido, o null. Si se pide el
     * buscador clasico, devuelve null y [classicRequested] queda en true.
     */
    public static Outcome show(Component parent, DataLogicSales dlSales, String siteGuid, String initialText) {
        Window w = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JBusquedaRapida d = new JBusquedaRapida(w, dlSales, siteGuid);
        if (initialText != null && !initialText.trim().isEmpty()) {
            d.txt.setText(initialText.trim());
            d.search();
        }
        d.setLocationRelativeTo(w);
        d.setVisible(true);
        return new Outcome(d.selected, d.classic);
    }

    /** Resultado del buscador. */
    public static class Outcome {
        public final ProductInfoExt product;
        public final boolean classicRequested;

        Outcome(ProductInfoExt product, boolean classicRequested) {
            this.product = product;
            this.classicRequested = classicRequested;
        }
    }

    private void build() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        JLabel hint = new JLabel("Escribe código, referencia o nombre (sin acentos, en plural o con errores también lo encuentra)");
        hint.setFont(hint.getFont().deriveFont(12f));
        hint.setForeground(Color.GRAY);
        txt.setFont(txt.getFont().deriveFont(Font.PLAIN, 22f));
        txt.setPreferredSize(new Dimension(640, 42));
        top.add(hint, BorderLayout.NORTH);
        top.add(txt, BorderLayout.CENTER);

        // "Tal vez quisiste decir"
        pnlSuggest.setBackground(SUGGEST_BG);
        pnlSuggest.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        lblSuggest.setFont(lblSuggest.getFont().deriveFont(16f));
        lblSuggest.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pnlSuggest.add(lblSuggest, BorderLayout.CENTER);
        pnlSuggest.setVisible(false);
        pnlSuggest.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter useSuggestion = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                applySuggestion();
            }
        };
        pnlSuggest.addMouseListener(useSuggestion);
        lblSuggest.addMouseListener(useSuggestion);
        lblInfo.setFont(lblInfo.getFont().deriveFont(13f));
        lblInfo.setForeground(Color.DARK_GRAY);
        JPanel info = new JPanel(new BorderLayout(0, 6));
        info.add(pnlSuggest, BorderLayout.NORTH);
        info.add(lblInfo, BorderLayout.SOUTH);
        top.add(info, BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.setFixedCellHeight(-1);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 2) {
                    choose();
                }
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    choose();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(760, 420));
        root.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnClassic = new JButton("Buscador clásico...");
        btnClassic.addActionListener(e -> {
            classic = true;
            dispose();
        });
        JButton btnCancel = new JButton("Cancelar");
        btnCancel.addActionListener(e -> dispose());
        JButton btnAdd = new JButton("Agregar");
        btnAdd.setFont(btnAdd.getFont().deriveFont(Font.BOLD));
        btnAdd.addActionListener(e -> choose());
        buttons.add(btnClassic);
        buttons.add(btnCancel);
        buttons.add(btnAdd);
        root.add(buttons, BorderLayout.SOUTH);

        txt.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                timer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                timer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                timer.restart();
            }
        });
        txt.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN:
                        move(1);
                        e.consume();
                        break;
                    case KeyEvent.VK_UP:
                        move(-1);
                        e.consume();
                        break;
                    case KeyEvent.VK_ENTER:
                        e.consume();
                        timer.stop();
                        if (list.getSelectedValue() == null) {
                            search();   // Enter antes de que termine la espera: buscar ya
                        }
                        choose();
                        break;
                    default:
                }
            }
        });
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(640, 420));
        // El indice se arma al abrir (no al teclear la primera letra)
        SwingUtilities.invokeLater(() -> {
            try {
                SearchIndex.index(connection(), siteGuid);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "indice de busqueda", ex);
            }
        });
    }

    private static Connection connection() throws Exception {
        return SessionFactory.getSession().getConnection();
    }

    private void search() {
        String q = txt.getText().trim();
        model.clear();
        suggestion = null;
        pnlSuggest.setVisible(false);
        if (q.isEmpty()) {
            lblInfo.setText(" ");
            return;
        }
        if (q.length() < MIN_CHARS) {
            lblInfo.setText("Escribe al menos " + MIN_CHARS + " caracteres...");
            return;
        }
        SearchIndex.Result r;
        try {
            r = SearchIndex.search(connection(), q, siteGuid);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "busqueda", ex);
            lblInfo.setText("No se pudo buscar: " + ex.getMessage());
            return;
        }
        for (ProductoBusqueda p : r.results) {
            model.addElement(p);
        }
        if (!r.similar.isEmpty()) {
            model.addElement(r.results.isEmpty() ? "Productos parecidos" : "También se parecen");
            for (ProductoBusqueda p : r.similar) {
                model.addElement(p);
            }
        }
        if (r.didYouMean != null) {
            suggestion = r.didYouMean;
            lblSuggest.setText("<html>Tal vez quisiste decir:&nbsp; <b><i>" + escape(r.didYouMean) + "</i></b></html>");
            pnlSuggest.setVisible(true);
        }
        if (r.results.isEmpty() && r.similar.isEmpty()) {
            lblInfo.setText("Sin resultados para \"" + q + "\"");
        } else if (r.results.isEmpty()) {
            lblInfo.setText("Nada con \"" + q + "\" exacto; mira los parecidos");
        } else if (r.results.size() >= SearchIndex.LIMIT) {
            lblInfo.setText("Primeros " + r.results.size() + " resultados — escribe más para afinar");
        } else {
            lblInfo.setText(r.results.size() == 1 ? "1 resultado" : r.results.size() + " resultados");
        }
        int first = firstProduct();
        if (first >= 0) {
            list.setSelectedIndex(first);
            list.ensureIndexIsVisible(first);
        }
    }

    private void applySuggestion() {
        if (suggestion == null) {
            return;
        }
        txt.setText(suggestion);
        timer.stop();
        search();
        txt.requestFocusInWindow();
    }

    private int firstProduct() {
        for (int i = 0; i < model.size(); ++i) {
            if (model.get(i) instanceof ProductoBusqueda) {
                return i;
            }
        }
        return -1;
    }

    private void move(int delta) {
        int n = model.size();
        if (n == 0) {
            return;
        }
        int i = list.getSelectedIndex();
        for (int k = 0; k < n; ++k) {
            i = i + delta;
            if (i < 0 || i >= n) {
                return;
            }
            if (model.get(i) instanceof ProductoBusqueda) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private void choose() {
        Object v = list.getSelectedValue();
        if (!(v instanceof ProductoBusqueda)) {
            int first = firstProduct();
            if (first < 0) {
                return;
            }
            v = model.get(first);
        }
        try {
            ProductInfoExt p = dlSales.getProductInfo(((ProductoBusqueda) v).getId());
            if (p == null) {
                lblInfo.setText("El producto ya no existe");
                return;
            }
            selected = p;
            dispose();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "cargar producto", ex);
            lblInfo.setText("No se pudo cargar el producto: " + ex.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Renglones: producto (nombre, ref/código y precio) o título de sección. */
    private static class Renderer implements ListCellRenderer<Object> {
        private final JPanel row = new JPanel(new BorderLayout(12, 0));
        private final JLabel name = new JLabel();
        private final JLabel detail = new JLabel();
        private final JLabel price = new JLabel();
        private final JLabel header = new JLabel();

        Renderer() {
            JPanel texts = new JPanel(new BorderLayout());
            texts.setOpaque(false);
            name.setFont(name.getFont().deriveFont(Font.BOLD, 16f));
            detail.setFont(detail.getFont().deriveFont(12f));
            detail.setForeground(Color.GRAY);
            price.setFont(price.getFont().deriveFont(Font.BOLD, 16f));
            texts.add(name, BorderLayout.NORTH);
            texts.add(detail, BorderLayout.SOUTH);
            row.add(texts, BorderLayout.CENTER);
            row.add(price, BorderLayout.EAST);
            row.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xE0E0E0)),
                    BorderFactory.createEmptyBorder(7, 10, 7, 10)));
            header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
            header.setForeground(BLUE);
            header.setBorder(BorderFactory.createEmptyBorder(12, 10, 4, 10));
        }

        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean isSelected, boolean hasFocus) {
            if (!(value instanceof ProductoBusqueda)) {
                header.setText(String.valueOf(value).toUpperCase());
                return header;
            }
            ProductoBusqueda p = (ProductoBusqueda) value;
            name.setText(p.getName() == null ? "(sin nombre)" : p.getName());
            StringBuilder d = new StringBuilder();
            if (p.getReference() != null && !p.getReference().trim().isEmpty()) {
                d.append("Ref: ").append(p.getReference());
            }
            if (p.getCode() != null && !p.getCode().trim().isEmpty()) {
                if (d.length() > 0) {
                    d.append("   ·   ");
                }
                d.append("Cód: ").append(p.getCode());
            }
            detail.setText(d.length() == 0 ? " " : d.toString());
            Double v = SystemProperty.TAXINCLUDED ? p.getPriceSellInc() : p.getPriceSell();
            price.setText(p.isVariablePrice() ? "Precio libre" : Formats.CURRENCY.formatValue(v == null ? 0.0 : v));
            Color bg = isSelected ? l.getSelectionBackground() : l.getBackground();
            Color fg = isSelected ? l.getSelectionForeground() : l.getForeground();
            row.setBackground(bg);
            name.setForeground(fg);
            price.setForeground(fg);
            return row;
        }
    }
}
