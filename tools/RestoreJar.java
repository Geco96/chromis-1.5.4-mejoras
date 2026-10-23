import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Builds dist/restaurar.jar, the backup restorer that runs on its own (double
 * click): uk.chromis.pos.backup.RestoreTool and SqlRestore, the MySQL JDBC
 * driver from Chromis Admin, and the Chromis class that decrypts the password
 * of chromisposconfig.properties.
 *
 * Usage: java tools/RestoreJar.java <classes-dir> <chromispos.jar> <admin.jar> <output.jar>
 */
public class RestoreJar {

    public static void main(String[] args) throws IOException {
        Path classes = Paths.get(args[0]);
        Manifest mf = new Manifest();
        mf.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mf.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "uk.chromis.pos.backup.RestoreTool");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(Paths.get(args[3])), mf)) {
            Path pkg = classes.resolve("uk/chromis/pos/backup");
            try (Stream<Path> s = Files.list(pkg)) {
                for (Path p : (Iterable<Path>) s::iterator) {
                    String n = p.getFileName().toString();
                    if (n.startsWith("SqlRestore") || n.startsWith("RestoreTool")) {
                        add(out, "uk/chromis/pos/backup/" + n, Files.readAllBytes(p));
                    }
                }
            }
            copy(out, args[1], e -> e.equals("uk/chromis/pos/util/AltEncrypter.class") || e.equals("uk/chromis/pos/util/StringUtils.class"));
            copy(out, args[2], e -> e.startsWith("com/mysql/") || e.equals("META-INF/services/java.sql.Driver"));
        }
    }

    interface Filter {
        boolean take(String name);
    }

    private static void copy(JarOutputStream out, String jar, Filter f) throws IOException {
        try (ZipFile z = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> e = z.entries();
            while (e.hasMoreElements()) {
                ZipEntry x = e.nextElement();
                if (!x.isDirectory() && f.take(x.getName())) {
                    try (InputStream in = z.getInputStream(x)) {
                        add(out, x.getName(), in.readAllBytes());
                    }
                }
            }
        }
    }

    private static void add(JarOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(data);
        out.closeEntry();
    }
}
