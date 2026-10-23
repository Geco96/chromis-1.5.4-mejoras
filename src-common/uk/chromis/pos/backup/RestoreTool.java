package uk.chromis.pos.backup;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * "Restaurar respaldo de Chromis": loads a backup (.zip of the scheduled
 * backups, or a .sql) into a database, replacing the dbcreator restore, which
 * hands the file to mysql.exe and can stay "Restoring database" forever.
 *
 * <ul>
 * <li>Reads the .zip directly; mysql.exe is not needed.</li>
 * <li>Checks the backup is complete before deleting anything.</li>
 * <li>Before replacing the database, shows who has it open (Chromis POS,
 * Admin, the monitor...) and offers to close those connections; it never
 * waits more than 20 s for a lock.</li>
 * <li>Real progress, cancel button, and a check at the end.</li>
 * </ul>
 *
 * Runs on its own (restaurar.jar, double click) so it works on a PC where
 * Chromis cannot start yet. The connection is read from
 * chromisposconfig.properties next to the jar when there is one.
 */
public final class RestoreTool {

    private static final int LOCK_WAIT_SECONDS = 20;

    private final JFrame frame = new JFrame("Restaurar respaldo de Chromis");
    private final JTextField txtServer = new JTextField("localhost", 18);
    private final JTextField txtPort = new JTextField("3306", 6);
    private final JTextField txtUser = new JTextField("root", 12);
    private final JPasswordField txtPassword = new JPasswordField(12);
    private final JTextField txtDatabase = new JTextField("chromispos", 18);
    private final JTextField txtFile = new JTextField(34);
    private final JButton btnFile = new JButton("Elegir...");
    private final JButton btnRestore = new JButton("Restaurar");
    private final JButton btnCancel = new JButton("Cancelar");
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JLabel lblStatus = new JLabel(" ");
    private final JTextArea log = new JTextArea(9, 60);
    private volatile boolean cancel;
    private String configSource;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            // default look
        }
        SwingUtilities.invokeLater(() -> new RestoreTool().show());
    }

    private void show() {
        loadConfig();
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;
        row = add(form, g, row, "Servidor", txtServer, new JLabel("Puerto"), txtPort);
        row = add(form, g, row, "Usuario", txtUser, new JLabel("Contraseña"), txtPassword);
        row = add(form, g, row, "Base de datos", txtDatabase, null, null);
        row = add(form, g, row, "Respaldo (.zip o .sql)", txtFile, btnFile, null);
        JLabel help = new JLabel("<html>La base se <b>borra</b> y se carga con el respaldo. Antes cierra Chromis POS, "
                + "Chromis Admin y el monitor que la usen." + (configSource == null ? "" : "<br>Conexión tomada de " + configSource)
                + "</html>");
        g.gridx = 0;
        g.gridy = row++;
        g.gridwidth = 4;
        form.add(help, g);
        g.gridy = row++;
        form.add(bar, g);
        g.gridy = row;
        form.add(lblStatus, g);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JPanel buttons = new JPanel();
        buttons.add(btnRestore);
        buttons.add(btnCancel);
        btnCancel.setEnabled(false);
        btnFile.addActionListener(e -> chooseFile());
        btnRestore.addActionListener(e -> start());
        btnCancel.addActionListener(e -> {
            cancel = true;
            status("Cancelando al terminar la instrucción en curso...");
        });

        frame.setLayout(new BorderLayout());
        frame.add(form, BorderLayout.NORTH);
        frame.add(new JScrollPane(log), BorderLayout.CENTER);
        frame.add(buttons, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setMinimumSize(new Dimension(640, 420));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static int add(JPanel p, GridBagConstraints g, int row, String label, java.awt.Component a,
            java.awt.Component b, java.awt.Component c) {
        g.gridwidth = 1;
        g.gridy = row;
        g.gridx = 0;
        g.weightx = 0;
        p.add(new JLabel(label), g);
        g.gridx = 1;
        g.weightx = 1;
        p.add(a, g);
        g.weightx = 0;
        if (b != null) {
            g.gridx = 2;
            p.add(b, g);
        }
        if (c != null) {
            g.gridx = 3;
            p.add(c, g);
        }
        return row + 1;
    }

    // ---------------- Connection from chromisposconfig.properties ----------------

    private void loadConfig() {
        List<File> places = new ArrayList<>();
        try {
            File jar = new File(RestoreTool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            places.add(new File(jar.getParentFile(), "chromisposconfig.properties"));
        } catch (Exception ex) {
            // unknown location
        }
        places.add(new File(System.getProperty("user.dir"), "chromisposconfig.properties"));
        places.add(new File(System.getProperty("user.home"), "chromisposconfig.properties"));
        for (File f : places) {
            if (!f.isFile()) {
                continue;
            }
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
            } catch (IOException ex) {
                continue;
            }
            set(txtServer, p.getProperty("database.server"));
            set(txtPort, p.getProperty("database.port"));
            set(txtDatabase, p.getProperty("database.name"));
            set(txtUser, p.getProperty("database.user"));
            String pass = p.getProperty("database.password", "");
            if (pass.startsWith("crypt:")) {
                pass = decrypt(txtUser.getText(), pass.substring(6));
            }
            txtPassword.setText(pass);
            configSource = f.getAbsolutePath();
            return;
        }
    }

    private static void set(JTextField f, String v) {
        if (v != null && !v.trim().isEmpty()) {
            f.setText(v.trim());
        }
    }

    /** Chromis keeps the password encrypted; the class comes from Chromis (bundled in restaurar.jar). */
    public static String decrypt(String user, String value) {
        try {
            Class<?> c = Class.forName("uk.chromis.pos.util.AltEncrypter");
            Object enc = c.getConstructor(String.class).newInstance("cypherkey" + user);
            Method m = c.getMethod("decrypt", String.class);
            Object r = m.invoke(enc, value);
            return r == null ? "" : r.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private void chooseFile() {
        JFileChooser fc = new JFileChooser(txtFile.getText().isEmpty() ? null : new File(txtFile.getText()).getParentFile());
        fc.setFileFilter(new FileNameExtensionFilter("Respaldo de Chromis (.zip, .sql)", "zip", "sql"));
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            txtFile.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    // ---------------- Restore ----------------

    private void start() {
        File file = new File(txtFile.getText().trim());
        String db = txtDatabase.getText().trim();
        if (!file.isFile()) {
            JOptionPane.showMessageDialog(frame, "Elige el archivo del respaldo (.zip o .sql).");
            return;
        }
        if (!db.matches("[A-Za-z0-9_\\-$]+")) {
            JOptionPane.showMessageDialog(frame, "El nombre de la base solo puede tener letras, números, - y _.");
            return;
        }
        cancel = false;
        enable(false);
        log.setText("");
        Thread t = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                run(file, db, t0);
            } catch (SqlRestore.Cancelled ex) {
                done("Cancelado. La base \"" + db + "\" quedó INCOMPLETA: vuelve a restaurar antes de usarla.", true);
            } catch (SqlRestore.Failure ex) {
                done("No se terminó: " + ex.getMessage() + "\nLa base \"" + db + "\" quedó INCOMPLETA.", true);
            } catch (Abort ex) {
                done(ex.getMessage(), ex.error);
            } catch (Exception ex) {
                done("No se pudo: " + ex.getMessage(), true);
            }
        }, "restaurar");
        t.setDaemon(true);
        t.start();
    }

    /** Stops the restore with a message (nothing was changed unless said). */
    private static final class Abort extends Exception {

        final boolean error;

        Abort(String message, boolean error) {
            super(message);
            this.error = error;
        }
    }

    private void run(File file, String db, long t0) throws Exception {
        // 1. The backup is complete
        status("Revisando el respaldo...");
        long total = size(file);
        boolean complete;
        try (InputStream in = open(file)) {
            complete = SqlRestore.check(in, progress(total, "Revisando el respaldo"));
        }
        say("Respaldo: " + file.getName() + (total > 0 ? " (" + (total / (1024 * 1024)) + " MB de SQL)" : ""));
        if (!complete && !ask("El archivo no termina como un respaldo completo de Chromis: puede estar cortado, o "
                + "ser de otro programa (HeidiSQL, mysqldump).\nSi está cortado, la base quedaría a medias.\n\n¿Cargarlo de todos modos?")) {
            throw new Abort("No se cambió nada.", false);
        }

        String url = "jdbc:mysql://" + txtServer.getText().trim() + ":" + txtPort.getText().trim() + "/";
        String params = "?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true";
        String user = txtUser.getText().trim();
        String pass = new String(txtPassword.getPassword());
        String quoted = "`" + db + "`";

        status("Conectando con el servidor...");
        try (Connection c = DriverManager.getConnection(url + params, user, pass); Statement st = c.createStatement()) {
            // 2. Nobody has the database open
            List<String> open = connections(c, db);
            if (!open.isEmpty()) {
                int r = choice("Hay " + open.size() + (open.size() == 1 ? " conexión abierta" : " conexiones abiertas")
                        + " a la base \"" + db + "\":\n\n" + String.join("\n", open)
                        + "\n\nCierra Chromis POS, Admin y el monitor que la usen. También puedo cerrar esas conexiones.",
                        "Cerrarlas y continuar", "Ya las cerré, revisar otra vez", "Cancelar");
                if (r == 2 || r < 0) {
                    throw new Abort("No se cambió nada.", false);
                }
                if (r == 0) {
                    kill(c, db);
                }
                open = connections(c, db);
                if (!open.isEmpty()) {
                    throw new Abort("La base \"" + db + "\" sigue abierta por:\n" + String.join("\n", open)
                            + "\nCierra esos programas y vuelve a intentar. No se cambió nada.", true);
                }
            }
            // 3. Confirm replacing it
            int tables = tableCount(c, db);
            if (tables > 0 && !ask("La base \"" + db + "\" de " + txtServer.getText().trim() + " tiene " + tables
                    + (tables == 1 ? " tabla" : " tablas") + ".\nSe BORRARÁ y se cargará el respaldo.\n\n¿Continuar?")) {
                throw new Abort("No se cambió nada.", false);
            }
            status("Preparando la base...");
            st.execute("SET SESSION lock_wait_timeout = " + LOCK_WAIT_SECONDS);
            try {
                st.execute("DROP DATABASE IF EXISTS " + quoted);
            } catch (SQLException ex) {
                if (ex.getErrorCode() == 1205) {
                    throw new Abort("La base \"" + db + "\" está en uso y no se pudo borrar en " + LOCK_WAIT_SECONDS
                            + " s:\n" + String.join("\n", connections(c, db))
                            + "\nCierra esos programas y vuelve a intentar. No se cambió nada.", true);
                }
                throw ex;
            }
            st.execute("CREATE DATABASE " + quoted + " CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            say("Base \"" + db + "\" creada de nuevo.");
        }

        // 4. Load
        long loadStart = System.currentTimeMillis();
        SqlRestore.Result r;
        try (Connection c = DriverManager.getConnection(url + db + params, user, pass); InputStream in = open(file)) {
            try (Statement st = c.createStatement()) {
                st.execute("SET SESSION lock_wait_timeout = " + LOCK_WAIT_SECONDS);
            }
            r = SqlRestore.restore(c, in, progress(total, "Cargando"));
            try (Statement st = c.createStatement()) {
                st.execute("COMMIT");
            }
        }

        // 5. Check
        String version = "";
        int tables;
        try (Connection c = DriverManager.getConnection(url + db + params, user, pass)) {
            tables = tableCount(c, db);
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT version FROM applications LIMIT 1")) {
                if (rs.next()) {
                    version = rs.getString(1);
                }
            } catch (SQLException ex) {
                version = "";
            }
        }
        long s = (System.currentTimeMillis() - loadStart) / 1000;
        done("Listo: " + r.statements + " instrucciones en " + (s / 60) + ":" + String.format("%02d", s % 60) + ", "
                + tables + " tablas en \"" + db + "\"" + (version.isEmpty() ? " (no parece una base de Chromis)"
                : ", Chromis " + version) + ".\nYa se puede abrir Chromis con esta base.", version.isEmpty());
    }

    private static long size(File file) throws IOException {
        if (file.getName().toLowerCase().endsWith(".zip")) {
            try (ZipFile z = new ZipFile(file)) {
                return sqlEntry(z).getSize();
            }
        }
        return file.length();
    }

    private static ZipEntry sqlEntry(ZipFile z) throws IOException {
        java.util.Enumeration<? extends ZipEntry> e = z.entries();
        while (e.hasMoreElements()) {
            ZipEntry x = e.nextElement();
            if (!x.isDirectory() && x.getName().toLowerCase().endsWith(".sql")) {
                return x;
            }
        }
        throw new IOException("El .zip no tiene un archivo .sql adentro.");
    }

    /** The SQL of the backup, from the .zip or the .sql file. */
    public static InputStream open(File file) throws IOException {
        if (!file.getName().toLowerCase().endsWith(".zip")) {
            return new BufferedInputStream(new FileInputStream(file), 64 * 1024);
        }
        ZipFile z = new ZipFile(file);
        ZipEntry e;
        try {
            e = sqlEntry(z);
        } catch (IOException ex) {
            z.close();
            throw ex;
        }
        return new BufferedInputStream(new java.io.FilterInputStream(z.getInputStream(e)) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    z.close();
                }
            }
        }, 64 * 1024);
    }

    /** Other connections using [db]: "id  user@host  (what it does)". */
    public static List<String> connections(Connection c, String db) throws SQLException {
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id, user, host, command, time FROM information_schema.processlist "
                + "WHERE db = ? AND id <> CONNECTION_ID()")) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add("  " + rs.getLong(1) + "  " + rs.getString(2) + "@" + rs.getString(3) + "  ("
                            + rs.getString(4) + ", " + rs.getLong(5) + " s)");
                }
            }
        }
        return list;
    }

    private void kill(Connection c, String db) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM information_schema.processlist WHERE db = ? AND id <> CONNECTION_ID()")) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        try (Statement st = c.createStatement()) {
            for (long id : ids) {
                try {
                    st.execute("KILL " + id);
                    say("Conexión " + id + " cerrada.");
                } catch (SQLException ex) {
                    say("No se pudo cerrar la conexión " + id + ": " + ex.getMessage());
                }
            }
        }
    }

    public static int tableCount(Connection c, String db) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?")) {
            ps.setString(1, db);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private SqlRestore.Progress progress(long total, String what) {
        return new SqlRestore.Progress() {
            @Override
            public void progress(long bytes, int statements) {
                SwingUtilities.invokeLater(() -> {
                    if (total > 0) {
                        bar.setIndeterminate(false);
                        bar.setValue((int) Math.min(1000, bytes * 1000 / total));
                        lblStatus.setText(what + ": " + Math.min(100, bytes * 100 / total) + " %"
                                + (statements > 0 ? "  (" + statements + " instrucciones)" : ""));
                    } else {
                        bar.setIndeterminate(true);
                        lblStatus.setText(what + ": " + (bytes / (1024 * 1024)) + " MB");
                    }
                });
            }

            @Override
            public boolean cancelled() {
                return cancel;
            }
        };
    }

    // ---------------- Screen ----------------

    private void enable(boolean on) {
        SwingUtilities.invokeLater(() -> {
            for (java.awt.Component c : new java.awt.Component[]{txtServer, txtPort, txtUser, txtPassword, txtDatabase, txtFile,
                btnFile, btnRestore}) {
                c.setEnabled(on);
            }
            btnCancel.setEnabled(!on);
            bar.setIndeterminate(!on);
            if (on) {
                bar.setIndeterminate(false);
            }
        });
    }

    private void status(String s) {
        SwingUtilities.invokeLater(() -> lblStatus.setText(s));
    }

    private void say(String s) {
        SwingUtilities.invokeLater(() -> log.append(s + "\n"));
    }

    private void done(String message, boolean error) {
        say(message);
        enable(true);
        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(error ? "No se terminó." : "Listo.");
            if (!error) {
                bar.setValue(1000);
            }
            JOptionPane.showMessageDialog(frame, message, "Restaurar respaldo",
                    error ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private boolean ask(String question) throws Exception {
        return choice(question, "Sí", "No") == 0;
    }

    private int choice(String message, String... options) throws Exception {
        AtomicReference<Integer> r = new AtomicReference<>(-1);
        SwingUtilities.invokeAndWait(() -> r.set(JOptionPane.showOptionDialog(frame, message, "Restaurar respaldo",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[options.length - 1])));
        return r.get();
    }
}
