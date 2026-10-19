package uk.chromis.pos.sales;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import uk.chromis.pos.restock.RestockStore.Status;

/**
 * Small notice in the bottom right corner of the POS window. It never takes
 * the keyboard focus (the barcode scanner types into the ticket), closes by
 * itself after a while and closes earlier when clicked.
 */
public final class RestockToast extends JWindow {

    private static final int WIDTH = 340;
    private static final int GAP = 6;
    private static final int SECONDS = 12;
    private static final List<RestockToast> ACTIVE = new ArrayList<>();

    private final Component owner;

    private RestockToast(Window parent, Component owner, String title, String message, Color color) {
        super(parent);
        this.owner = owner;
        setFocusableWindowState(false);
        setAlwaysOnTop(true);

        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 10, 1, 1, color),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 14f));
        content.add(lblTitle, BorderLayout.NORTH);

        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setFocusable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setFont(text.getFont().deriveFont(Font.PLAIN, 14f));
        content.add(text, BorderLayout.CENTER);

        MouseAdapter close = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dismiss();
            }
        };
        content.addMouseListener(close);
        text.addMouseListener(close);
        lblTitle.addMouseListener(close);

        setContentPane(content);
        setSize(WIDTH, 10);
        text.setSize(WIDTH - 32, Short.MAX_VALUE);
        setSize(WIDTH, getPreferredSize().height);

        Timer timer = new Timer(SECONDS * 1000, e -> dismiss());
        timer.setRepeats(false);
        timer.start();
    }

    static Color colorFor(Status status) {
        switch (status) {
            case AGOTADO:
                return new Color(0x55, 0x55, 0x55);
            case BAJO:
                return new Color(0xD9, 0x30, 0x25);
            default:
                return new Color(0xE8, 0xA3, 0x00);
        }
    }

    public static void show(Component owner, String title, String message, Color color) {
        Window parent = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        RestockToast toast = new RestockToast(parent, owner, title, message, color);
        ACTIVE.add(toast);
        layoutAll();
        toast.setVisible(true);
        Toolkit.getDefaultToolkit().beep();
    }

    private void dismiss() {
        if (ACTIVE.remove(this)) {
            dispose();
            layoutAll();
        }
    }

    private static void layoutAll() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Rectangle area = usableArea(ACTIVE.get(0).owner);
        int y = area.y + area.height - GAP;
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            RestockToast t = ACTIVE.get(i);
            y -= t.getHeight();
            t.setLocation(area.x + area.width - WIDTH - GAP, y);
            y -= GAP;
        }
    }

    /** Visible area of the POS window, or of its screen without the task bar. */
    private static Rectangle usableArea(Component owner) {
        Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        if (w != null && w.isShowing()) {
            return w.getBounds();
        }
        GraphicsConfiguration gc = w != null ? w.getGraphicsConfiguration() : null;
        if (gc == null) {
            Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            return screen;
        }
        Rectangle b = gc.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
    }
}
