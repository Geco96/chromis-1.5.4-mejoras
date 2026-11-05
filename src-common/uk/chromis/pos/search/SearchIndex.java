package uk.chromis.pos.search;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Busqueda de productos para el buscador rapido de la caja (igual que la venta
 * de Chromis Android, DataLogicSearch.buscar):
 *
 *   1. SQL: codigo de barras, codigos alternos (tabla barcodes, si existe) y
 *      referencia que CONTIENEN el texto, o un nombre con todas las palabras.
 *   2. En memoria (BusquedaInteligente): acentos, plurales, etiquetas,
 *      sinonimos y errores de dedo; con pocos resultados, parecidos y
 *      "Tal vez quisiste decir".
 *
 * El indice en memoria se arma con una consulta ligera y se renueva cada
 * INDEX_MS (otras cajas dan altas) o al llamar invalidate().
 */
public final class SearchIndex {

    private static final Logger LOG = Logger.getLogger(SearchIndex.class.getName());

    public static final int LIMIT = 50;
    public static final long INDEX_MS = 5 * 60 * 1000L;
    private static final int MAX_WORDS = 6;

    private static volatile BusquedaInteligente index;
    private static volatile String indexSite;
    private static volatile Boolean barcodesTable;

    /** Lo que muestra el buscador. */
    public static class Result {
        public final List<ProductoBusqueda> results = new ArrayList<>();
        public final List<ProductoBusqueda> similar = new ArrayList<>();
        public String didYouMean;
    }

    private SearchIndex() {
    }

    public static void invalidate() {
        index = null;
    }

    public static Result search(Connection con, String text, String siteGuid) {
        Result b = new Result();
        String q = text == null ? "" : text.trim();
        if (q.isEmpty()) {
            return b;
        }
        Set<String> seen = new HashSet<>();
        try {
            for (ProductoBusqueda p : sql(con, q, siteGuid)) {
                if (seen.add(p.getId())) {
                    b.results.add(p);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "busqueda SQL", e);
        }
        BusquedaInteligente bi = index(con, siteGuid);
        if (bi == null) {
            return b;
        }
        BusquedaInteligente.Resultado r = bi.buscar(q, LIMIT);
        for (ProductoBusqueda p : r.encontrados) {
            if (b.results.size() >= LIMIT) {
                break;
            }
            if (seen.add(p.getId())) {
                b.results.add(p);
            }
        }
        if (b.results.size() < 5) {
            for (ProductoBusqueda p : r.parecidos) {
                if (seen.add(p.getId())) {
                    b.similar.add(p);
                }
            }
            if (r.quisoDecir != null && !BusquedaInteligente.normalizar(r.quisoDecir).equals(BusquedaInteligente.normalizar(q))
                    && !bi.buscar(r.quisoDecir, 1).encontrados.isEmpty()) {
                b.didYouMean = r.quisoDecir;
            }
        }
        return b;
    }

    /** El indice en memoria (lo arma si hace falta); null si no se pudo. */
    public static BusquedaInteligente index(Connection con, String siteGuid) {
        BusquedaInteligente bi = index;
        String site = siteGuid == null ? "" : siteGuid;
        if (bi != null && site.equals(indexSite) && System.currentTimeMillis() - bi.creado < INDEX_MS) {
            return bi;
        }
        try {
            List<ProductoBusqueda> products = new ArrayList<>();
            String sql = "SELECT p.id, p.reference, p.code, p.name, p.pricesell, p.pricesellinc, p.isvprice "
                    + "FROM products p WHERE p.retired = ?" + (siteGuid == null ? "" : " AND p.siteguid = ?");
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setBoolean(1, false);
                if (siteGuid != null) {
                    ps.setString(2, siteGuid);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        products.add(row(rs));
                    }
                }
            }
            List<String> groups = new ArrayList<>();
            for (SearchTagsStore.Group g : SearchTagsStore.groups(con)) {
                groups.add(g.words);
            }
            bi = new BusquedaInteligente(products, SearchTagsStore.allTags(con), groups);
            index = bi;
            indexSite = site;
            return bi;
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "indice de busqueda", t);
            return null;
        }
    }

    private static ProductoBusqueda row(ResultSet rs) throws SQLException {
        double v = rs.getDouble(5);
        Double sell = rs.wasNull() ? null : v;
        double vi = rs.getDouble(6);
        Double sellInc = rs.wasNull() ? null : vi;
        return new ProductoBusqueda(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                sell, sellInc, rs.getBoolean(7));
    }

    private static List<ProductoBusqueda> sql(Connection con, String q, String siteGuid) throws SQLException {
        if (!Boolean.FALSE.equals(barcodesTable)) {
            try {
                List<ProductoBusqueda> r = sql(con, q, siteGuid, true);
                barcodesTable = Boolean.TRUE;
                return r;
            } catch (SQLException e) {
                if (barcodesTable != null || e.getMessage() == null || !e.getMessage().toLowerCase().contains("barcodes")) {
                    throw e;
                }
                barcodesTable = Boolean.FALSE;
            }
        }
        return sql(con, q, siteGuid, false);
    }

    private static List<ProductoBusqueda> sql(Connection con, String q, String siteGuid, boolean withBarcodes) throws SQLException {
        String[] split = q.split("\\s+");
        int nWords = Math.min(split.length, MAX_WORDS);
        String like = "%" + escapeLike(q) + "%";
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.id, p.reference, p.code, p.name, p.pricesell, p.pricesellinc, p.isvprice FROM products p ");
        sql.append("WHERE p.retired = ? ");
        params.add(Boolean.FALSE);
        if (siteGuid != null) {
            sql.append("AND p.siteguid = ? ");
            params.add(siteGuid);
        }
        sql.append("AND (p.code LIKE ? OR p.reference LIKE ? ");
        params.add(like);
        params.add(like);
        if (withBarcodes) {
            sql.append("OR EXISTS (SELECT 1 FROM barcodes b WHERE b.productid = p.id AND b.barcode LIKE ?) ");
            params.add(like);
        }
        sql.append("OR (");
        for (int i = 0; i < nWords; ++i) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append("p.name LIKE ?");
            params.add("%" + escapeLike(split[i]) + "%");
        }
        sql.append(")) ORDER BY CASE WHEN p.code = ? OR p.reference = ? THEN 0 WHEN p.name LIKE ? THEN 1 ");
        params.add(q);
        params.add(q);
        params.add(escapeLike(q) + "%");
        sql.append("WHEN p.code LIKE ? THEN 2 ELSE 3 END, p.name");
        params.add("%" + escapeLike(q));
        List<ProductoBusqueda> r = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
            ps.setMaxRows(LIMIT);
            for (int i = 0; i < params.size(); ++i) {
                Object o = params.get(i);
                if (o instanceof Boolean) {
                    ps.setBoolean(i + 1, (Boolean) o);
                } else {
                    ps.setString(i + 1, (String) o);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    r.add(row(rs));
                }
            }
        }
        return r;
    }

    /** Escapa los comodines de LIKE (MySQL/MariaDB: escape por defecto '\'). */
    public static String escapeLike(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
