import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Builds a patched jar: copies every entry of the original jar except the
 * classes that were recompiled (including their inner classes), then adds the
 * recompiled classes.
 *
 * Usage: java tools/PatchJar.java <original.jar> <classes-dir> <output.jar>
 */
public class PatchJar {

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Uso: java tools/PatchJar.java <original.jar> <dir-clases> <salida.jar>");
            System.exit(1);
        }
        Path original = Paths.get(args[0]);
        Path classes = Paths.get(args[1]);
        Path output = Paths.get(args[2]);

        List<Path> newFiles;
        try (Stream<Path> walk = Files.walk(classes)) {
            newFiles = walk.filter(Files::isRegularFile).collect(Collectors.toList());
        }

        // Top-level class names replaced by the build, e.g. uk/chromis/pos/sales/JPanelTicket
        Set<String> replacedOuter = new HashSet<>();
        Set<String> newEntries = new HashSet<>();
        for (Path p : newFiles) {
            String name = classes.relativize(p).toString().replace('\\', '/');
            newEntries.add(name);
            if (name.endsWith(".class")) {
                String base = name.substring(0, name.length() - 6);
                int dollar = base.indexOf('$', base.lastIndexOf('/') + 1);
                replacedOuter.add(dollar >= 0 ? base.substring(0, dollar) : base);
            }
        }

        Files.createDirectories(output.toAbsolutePath().getParent());
        int kept = 0;
        int removed = 0;
        try (ZipFile in = new ZipFile(original.toFile());
                ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(output))) {
            for (Enumeration<? extends ZipEntry> e = in.entries(); e.hasMoreElements();) {
                ZipEntry entry = e.nextElement();
                String name = entry.getName();
                if (newEntries.contains(name) || isReplacedClass(name, replacedOuter)) {
                    removed++;
                    continue;
                }
                out.putNextEntry(new ZipEntry(name));
                try (InputStream is = in.getInputStream(entry)) {
                    is.transferTo(out);
                }
                out.closeEntry();
                kept++;
            }
            for (Path p : newFiles) {
                String name = classes.relativize(p).toString().replace('\\', '/');
                out.putNextEntry(new ZipEntry(name));
                Files.copy(p, out);
                out.closeEntry();
            }
        }
        System.out.println("Entradas originales conservadas: " + kept);
        System.out.println("Entradas reemplazadas: " + removed);
        System.out.println("Entradas nuevas/compiladas: " + newFiles.size());
        System.out.println("Generado: " + output);
    }

    private static boolean isReplacedClass(String name, Set<String> replacedOuter) {
        if (!name.endsWith(".class")) {
            return false;
        }
        String base = name.substring(0, name.length() - 6);
        int dollar = base.indexOf('$', base.lastIndexOf('/') + 1);
        return replacedOuter.contains(dollar >= 0 ? base.substring(0, dollar) : base);
    }
}
