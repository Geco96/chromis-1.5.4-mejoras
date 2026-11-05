package uk.chromis.pos.search;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Etiquetas de producto y sinonimos de busqueda, compartidos por Chromis POS,
 * Chromis Admin y Chromis Android (mismas tablas que DataLogicEtiquetas de la
 * tableta). Tablas propias, para no alterar las originales de Chromis:
 *
 *   product_tags    (product, tag)   palabras extra con que se busca un producto
 *   search_synonyms (id, words)      grupos de palabras equivalentes: "lampara, luz, foco"
 *
 * Se crean al primer guardado; si no existen, leer devuelve vacio.
 */
public final class SearchTagsStore {

    private static final Logger LOG = Logger.getLogger(SearchTagsStore.class.getName());

    public static final int MAX_TAG = 100;
    public static final int MAX_GROUP = 1000;

    /** Un grupo de sinonimos. */
    public static class Group {
        public String id;
        public String words;

        public Group(String id, String words) {
            this.id = id;
            this.words = words;
        }

        @Override
        public String toString() {
            return words;
        }
    }

    private SearchTagsStore() {
    }

    public static void createTables(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS product_tags ("
                    + "product VARCHAR(255) NOT NULL, tag VARCHAR(" + MAX_TAG + ") NOT NULL, "
                    + "PRIMARY KEY (product, tag))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS search_synonyms ("
                    + "id VARCHAR(255) NOT NULL PRIMARY KEY, words VARCHAR(" + MAX_GROUP + ") NOT NULL)");
        }
    }

    /** "foco, Bombilla , foco" -> [foco, Bombilla] (sin repetidas ni vacias). */
    public static List<String> split(String text) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> r = new ArrayList<>();
        if (text == null) {
            return r;
        }
        for (String t : text.split("[,;\\n]")) {
            String x = t.trim().replaceAll("\\s+", " ");
            if (x.isEmpty()) {
                continue;
            }
            if (x.length() > MAX_TAG) {
                x = x.substring(0, MAX_TAG).trim();
            }
            if (seen.add(BusquedaInteligente.normalizar(x))) {
                r.add(x);
            }
        }
        return r;
    }

    // ---- etiquetas

    public static List<String> tags(Connection con, String product) {
        List<String> r = new ArrayList<>();
        if (product == null) {
            return r;
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT tag FROM product_tags WHERE product = ? ORDER BY tag")) {
            ps.setString(1, product);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    r.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.FINE, "product_tags", e);   // sin la tabla: sin etiquetas
        }
        return r;
    }

    public static Map<String, List<String>> allTags(Connection con) {
        Map<String, List<String>> r = new HashMap<>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT product, tag FROM product_tags")) {
            while (rs.next()) {
                r.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
            }
        } catch (SQLException e) {
            LOG.log(Level.FINE, "product_tags", e);
        }
        return r;
    }

    /** Reemplaza las etiquetas del producto. */
    public static void saveTags(Connection con, String product, List<String> tags) throws SQLException {
        List<String> clean = split(tags == null ? "" : String.join(",", tags));
        if (clean.isEmpty() && tags(con, product).isEmpty()) {
            return;   // nada que guardar: ni siquiera crear la tabla
        }
        createTables(con);
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM product_tags WHERE product = ?")) {
            ps.setString(1, product);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement("INSERT INTO product_tags (product, tag) VALUES (?, ?)")) {
            for (String t : clean) {
                ps.setString(1, product);
                ps.setString(2, t);
                ps.executeUpdate();
            }
        }
        SearchIndex.invalidate();
    }

    // ---- sinonimos

    public static List<Group> groups(Connection con) {
        List<Group> r = new ArrayList<>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, words FROM search_synonyms ORDER BY words")) {
            while (rs.next()) {
                r.add(new Group(rs.getString(1), rs.getString(2)));
            }
        } catch (SQLException e) {
            LOG.log(Level.FINE, "search_synonyms", e);
        }
        return r;
    }

    /** Problema con el grupo escrito, o null si sirve. */
    public static String validateGroup(String words) {
        List<String> ws = split(words);
        if (ws.size() < 2) {
            return "Escribe al menos dos palabras separadas por coma, por ejemplo: lámpara, luz, foco";
        }
        if (String.join(", ", ws).length() > MAX_GROUP) {
            return "El grupo es muy largo: divídelo en dos.";
        }
        return null;
    }

    /** Alta (id null) o cambio. */
    public static void saveGroup(Connection con, Group g) throws SQLException {
        String problem = validateGroup(g.words);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        String text = String.join(", ", split(g.words));
        createTables(con);
        if (g.id == null) {
            g.id = UUID.randomUUID().toString();
            try (PreparedStatement ps = con.prepareStatement("INSERT INTO search_synonyms (id, words) VALUES (?, ?)")) {
                ps.setString(1, g.id);
                ps.setString(2, text);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = con.prepareStatement("UPDATE search_synonyms SET words = ? WHERE id = ?")) {
                ps.setString(1, text);
                ps.setString(2, g.id);
                ps.executeUpdate();
            }
        }
        g.words = text;
        SearchIndex.invalidate();
    }

    public static void deleteGroup(Connection con, String id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM search_synonyms WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.FINE, "search_synonyms", e);
        }
        SearchIndex.invalidate();
    }
}
