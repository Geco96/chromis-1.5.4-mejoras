/*
 * Mejora (misma que Chromis Android M41): busqueda de productos "que perdona".
 *
 * Complementa a la consulta SQL de DataLogicSearch (que exige que cada palabra
 * aparezca tal cual en el nombre). Trabaja en memoria sobre un indice ligero
 * del catalogo (id, nombre, codigo, referencia y etiquetas) y entiende:
 *
 *   - mayusculas, acentos y la ene: "lampara" encuentra "Lámpara", "pina" "Piña"
 *   - singular y plural: "lamparas" = "lampara", "luces" = "luz", "panales" = "pañal"
 *   - palabras a medio escribir: "lamp" encuentra "Lámpara"
 *   - etiquetas del producto (tabla product_tags): palabras extra con que la
 *     gente lo busca aunque no esten en el nombre ("foco", "bombilla"...)
 *   - sinonimos de la tienda (tabla search_synonyms): grupos de palabras que
 *     valen lo mismo para todos los productos ("lampara, luz, foco")
 *   - errores de dedo: "lampra", "lapmara" -> "Tal vez quisiste decir: lámpara"
 *   - nombre parecido pero no igual: "Lámparas led luz fría" muestra
 *     "Luces led luz fría" entre los parecidos (coinciden 3 de 4 palabras)
 *
 * Java puro, sin BD (se puede probar en la JVM). Linaje Chromis POS, GPLv3.
 */
package uk.chromis.pos.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BusquedaInteligente {

    /** Palabras que no ayudan a encontrar nada (no se exigen en la busqueda). */
    private static final Set<String> VACIAS = new HashSet<String>();
    static {
        for (String w : new String[]{"de", "del", "la", "el", "los", "las", "y", "con", "para", "en", "a", "al", "un", "una", "por", "sin"}) {
            VACIAS.add(w);
        }
    }

    /** Un producto del indice. */
    public static class Entrada {
        public final ProductoBusqueda info;
        /** Raices de las palabras del nombre (normalizadas). */
        final String[] nombre;
        /** Palabras normalizadas sin quitar plural (para "a medio escribir"). */
        final String[] nombreCompleto;
        final String[] etiquetas;
        final String[] etiquetasCompletas;
        final String codigo;
        final String referencia;
        /** Nombre normalizado completo, para premiar "empieza por". */
        final String nombreNorm;

        Entrada(ProductoBusqueda info, List<String> tags) {
            this.info = info;
            List<String> n = palabras(info.getName());
            this.nombreCompleto = n.toArray(new String[0]);
            this.nombre = raices(n);
            List<String> t = new ArrayList<String>();
            if (tags != null) {
                for (String tag : tags) {
                    t.addAll(palabras(tag));
                }
            }
            this.etiquetasCompletas = t.toArray(new String[0]);
            this.etiquetas = raices(t);
            this.codigo = info.getCode() == null ? "" : info.getCode().toLowerCase();
            this.referencia = info.getReference() == null ? "" : normalizar(info.getReference());
            this.nombreNorm = String.join(" ", n);
        }
    }

    /** Resultado completo de una busqueda. */
    public static class Resultado {
        /** Productos que tienen todas las palabras (con acentos, plurales, etiquetas y sinonimos). */
        public final List<ProductoBusqueda> encontrados = new ArrayList<ProductoBusqueda>();
        /** Productos que se parecen (les falta una palabra o tienen errores de dedo). */
        public final List<ProductoBusqueda> parecidos = new ArrayList<ProductoBusqueda>();
        /** Texto corregido ("Tal vez quisiste decir"), o null si no hay correccion. */
        public String quisoDecir;
    }

    private final List<Entrada> entradas = new ArrayList<Entrada>();
    /** raiz -> grupo de raices equivalentes (sinonimos). */
    private final Map<String, Set<String>> sinonimos = new HashMap<String, Set<String>>();
    /** raiz -> cuantas veces aparece (nombres y etiquetas). */
    private final Map<String, Integer> vocabulario = new HashMap<String, Integer>();
    /** raiz -> palabra como se escribio en el catalogo (con acentos), la mas comun. */
    private final Map<String, String> forma = new HashMap<String, String>();
    private final Map<String, Map<String, Integer>> formas = new HashMap<String, Map<String, Integer>>();

    public final long creado = System.currentTimeMillis();

    /**
     * @param productos  catalogo ligero
     * @param tags       id de producto -> etiquetas (puede ser null)
     * @param grupos     cada elemento es un grupo de sinonimos ("lampara, luz, foco")
     */
    public BusquedaInteligente(List<ProductoBusqueda> productos, Map<String, List<String>> tags, List<String> grupos) {
        for (ProductoBusqueda p : productos) {
            Entrada e = new Entrada(p, tags == null ? null : tags.get(p.getId()));
            this.entradas.add(e);
            this.contar(p.getName());
            if (tags != null && tags.get(p.getId()) != null) {
                for (String t : tags.get(p.getId())) {
                    this.contar(t);
                }
            }
        }
        for (Map.Entry<String, Map<String, Integer>> f : this.formas.entrySet()) {
            String mejor = null;
            int n = -1;
            for (Map.Entry<String, Integer> c : f.getValue().entrySet()) {
                if (c.getValue() > n) {
                    mejor = c.getKey();
                    n = c.getValue();
                }
            }
            this.forma.put(f.getKey(), mejor);
        }
        this.formas.clear();
        if (grupos != null) {
            for (String g : grupos) {
                Set<String> grupo = new LinkedHashSet<String>();
                for (String w : g.split("[,;\\n]")) {
                    List<String> ps = palabras(w);
                    // un sinonimo de varias palabras ("foco ahorrador") se toma por su primera palabra
                    if (!ps.isEmpty()) {
                        grupo.add(raiz(ps.get(0)));
                    }
                }
                if (grupo.size() < 2) {
                    continue;
                }
                for (String w : grupo) {
                    Set<String> ya = this.sinonimos.get(w);
                    if (ya == null) {
                        this.sinonimos.put(w, new LinkedHashSet<String>(grupo));
                    } else {
                        ya.addAll(grupo);
                    }
                }
            }
        }
    }

    public int tamano() {
        return this.entradas.size();
    }

    private void contar(String texto) {
        if (texto == null) {
            return;
        }
        for (String original : texto.split("[^\\p{L}\\p{N}]+")) {
            String n = normalizar(original);
            if (n.length() < 2 || esNumero(n)) {
                continue;
            }
            String r = raiz(n);
            Integer c = this.vocabulario.get(r);
            this.vocabulario.put(r, c == null ? 1 : c + 1);
            Map<String, Integer> fs = this.formas.get(r);
            if (fs == null) {
                fs = new HashMap<String, Integer>();
                this.formas.put(r, fs);
            }
            String o = original.toLowerCase();
            Integer k = fs.get(o);
            fs.put(o, k == null ? 1 : k + 1);
        }
    }

    // ------------------------------------------------------------------
    // Texto
    // ------------------------------------------------------------------

    /** minusculas, sin acentos ni dieresis, la ene como n; lo que no es letra o numero queda como espacio. */
    public static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String d = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(d.length());
        for (int i = 0; i < d.length(); ++i) {
            char c = d.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) {
                continue;
            }
            sb.append(Character.isLetterOrDigit(c) ? c : ' ');
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    /** Palabras normalizadas de un texto. */
    public static List<String> palabras(String s) {
        List<String> r = new ArrayList<String>();
        String n = normalizar(s);
        if (n.isEmpty()) {
            return r;
        }
        for (String w : n.split(" ")) {
            if (!w.isEmpty()) {
                r.add(w);
            }
        }
        return r;
    }

    /**
     * Singular aproximado en espanol, igual para lo que se escribe y para el
     * catalogo: luces -> luz, panales -> panal, colores -> color, lamparas -> lampara.
     */
    public static String raiz(String w) {
        if (w.length() <= 3 || esNumero(w) || !Character.isLetter(w.charAt(w.length() - 1))) {
            return w;
        }
        if (w.endsWith("ces") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "z";
        }
        if (w.endsWith("es") && w.length() > 4) {
            char c = w.charAt(w.length() - 3);
            if ("rlndjy".indexOf(c) >= 0) {
                return w.substring(0, w.length() - 2);
            }
        }
        if (w.endsWith("s") && !w.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    private static String[] raices(List<String> ws) {
        String[] r = new String[ws.size()];
        for (int i = 0; i < r.length; ++i) {
            r[i] = raiz(ws.get(i));
        }
        return r;
    }

    private static boolean esNumero(String w) {
        for (int i = 0; i < w.length(); ++i) {
            if (!Character.isDigit(w.charAt(i))) {
                return false;
            }
        }
        return !w.isEmpty();
    }

    /** Distancia de edicion con transposicion (Damerau, version "optimal string alignment"). */
    public static int distancia(String a, String b, int max) {
        int n = a.length();
        int m = b.length();
        if (Math.abs(n - m) > max) {
            return max + 1;
        }
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; ++i) {
            d[i][0] = i;
        }
        for (int j = 0; j <= m; ++j) {
            d[0][j] = j;
        }
        for (int i = 1; i <= n; ++i) {
            int minFila = Integer.MAX_VALUE;
            for (int j = 1; j <= m; ++j) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + costo);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
                minFila = Math.min(minFila, v);
            }
            if (minFila > max) {
                return max + 1;
            }
        }
        return d[n][m];
    }

    /** Errores de dedo que se aceptan segun lo largo de la palabra. */
    static int tolerancia(String w) {
        int n = w.length();
        if (n < 4) {
            return 0;
        }
        return n < 7 ? 1 : 2;
    }

    // ------------------------------------------------------------------
    // Busqueda
    // ------------------------------------------------------------------

    /** Una palabra de la busqueda, con sus equivalentes. */
    private static class Termino {
        final String escrito;   // normalizada tal cual
        final String raiz;
        final boolean ultima;   // la que se esta escribiendo: vale como prefijo
        final Set<String> equivalentes = new LinkedHashSet<String>();
        final boolean numero;

        Termino(String escrito, boolean ultima, Map<String, Set<String>> sin) {
            this.escrito = escrito;
            this.raiz = raiz(escrito);
            this.ultima = ultima;
            this.numero = esNumero(escrito);
            Set<String> g = sin.get(this.raiz);
            if (g != null) {
                for (String w : g) {
                    if (!w.equals(this.raiz)) {
                        this.equivalentes.add(w);
                    }
                }
            }
        }
    }

    /**
     * Puntos de una palabra de la busqueda en una lista de palabras del
     * producto: 0 = no esta.
     */
    private static int puntos(Termino t, String[] raices, String[] completas) {
        int mejor = 0;
        for (int i = 0; i < raices.length; ++i) {
            String r = raices[i];
            String c = completas[i];
            int p = 0;
            if (r.equals(t.raiz) || c.equals(t.escrito)) {
                p = 10;
            } else if (t.escrito.length() >= 2 && c.startsWith(t.escrito)) {
                // "lamp" -> lampara; en palabras completas tambien (coca -> cocacola)
                p = t.ultima ? 8 : 6;
            } else if (t.raiz.length() >= 3 && r.startsWith(t.raiz)) {
                p = 6;
            } else if (t.escrito.length() >= 4 && c.contains(t.escrito)) {
                p = 4;
            } else if (!t.equivalentes.isEmpty() && t.equivalentes.contains(r)) {
                p = 7;
            }
            if (p > mejor) {
                mejor = p;
            }
        }
        return mejor;
    }

    private int puntosEntrada(Termino t, Entrada e) {
        int p = puntos(t, e.nombre, e.nombreCompleto);
        int pe = puntos(t, e.etiquetas, e.etiquetasCompletas) - 1;   // la etiqueta vale poquito menos que el nombre
        p = Math.max(p, pe);
        if (p < 10 && t.escrito.length() >= 3) {
            if (!e.codigo.isEmpty() && e.codigo.contains(t.escrito)) {
                p = Math.max(p, 9);
            }
            if (!e.referencia.isEmpty() && e.referencia.contains(t.escrito)) {
                p = Math.max(p, 9);
            }
        }
        return Math.max(p, 0);
    }

    /** Como puntosEntrada pero aceptando errores de dedo (para los parecidos). */
    private static int puntosAprox(Termino t, Entrada e) {
        int tol = tolerancia(t.raiz);
        if (tol == 0) {
            return 0;
        }
        int mejor = 0;
        for (String[] lista : new String[][]{e.nombre, e.etiquetas}) {
            for (String r : lista) {
                int d = distancia(t.raiz, r, tol);
                if (d <= tol) {
                    mejor = Math.max(mejor, d == 1 ? 5 : 3);
                } else if (t.ultima && t.raiz.length() >= 5 && r.length() > t.raiz.length()
                        && distancia(t.raiz, r.substring(0, t.raiz.length()), tol) <= tol) {
                    mejor = Math.max(mejor, 3);   // a medio escribir y con error: "lapm" -> lampara
                }
            }
        }
        return mejor;
    }

    private static class Puntaje implements Comparable<Puntaje> {
        final Entrada e;
        final double p;

        Puntaje(Entrada e, double p) {
            this.e = e;
            this.p = p;
        }

        @Override
        public int compareTo(Puntaje o) {
            int c = Double.compare(o.p, this.p);
            if (c != 0) {
                return c;
            }
            String a = this.e.info.getName() == null ? "" : this.e.info.getName();
            String b = o.e.info.getName() == null ? "" : o.e.info.getName();
            return a.compareToIgnoreCase(b);
        }
    }

    public Resultado buscar(String texto, int limite) {
        Resultado res = new Resultado();
        List<String> ws = palabras(texto);
        List<Termino> ts = new ArrayList<Termino>();
        for (int i = 0; i < ws.size() && ts.size() < 8; ++i) {
            String w = ws.get(i);
            // las palabras vacias no se exigen, salvo que sea lo unico escrito
            if (VACIAS.contains(w) && ws.size() > 1) {
                continue;
            }
            ts.add(new Termino(w, i == ws.size() - 1, this.sinonimos));
        }
        if (ts.isEmpty()) {
            return res;
        }
        String q = String.join(" ", ws);

        List<Puntaje> todas = new ArrayList<Puntaje>();
        List<Puntaje> casi = new ArrayList<Puntaje>();
        for (Entrada e : this.entradas) {
            int suma = 0;
            int faltan = 0;
            int arreglados = 0;   // palabras que faltan pero estan con un error de dedo
            double aprox = 0;
            for (Termino t : ts) {
                int p = this.puntosEntrada(t, e);
                if (p == 0) {
                    ++faltan;
                    int a = t.numero ? 0 : puntosAprox(t, e);
                    if (a > 0) {
                        ++arreglados;
                        aprox += a;
                    }
                }
                suma += p;
            }
            if (faltan == 0) {
                double p = suma;
                if (e.nombreNorm.startsWith(q)) {
                    p += 5;
                }
                todas.add(new Puntaje(e, p));
                continue;
            }
            // Parecido: con 1 o 2 palabras, alguna (con o sin error de dedo);
            // con 3 o mas, todas menos una.
            int tienen = ts.size() - faltan + arreglados;
            int pide = ts.size() >= 3 ? ts.size() - 1 : 1;
            if (tienen >= pide && suma + aprox > 0) {
                casi.add(new Puntaje(e, suma + aprox - 3.0 * (faltan - arreglados)));
            }
        }
        Collections.sort(todas);
        for (int i = 0; i < todas.size() && i < limite; ++i) {
            res.encontrados.add(todas.get(i).e.info);
        }
        if (res.encontrados.size() < 5) {
            Collections.sort(casi);
            for (int i = 0; i < casi.size() && res.parecidos.size() < Math.min(limite, 15); ++i) {
                res.parecidos.add(casi.get(i).e.info);
            }
            res.quisoDecir = this.corregir(ws);
        }
        return res;
    }

    /**
     * "Tal vez quisiste decir": cambia cada palabra que no aparece en el
     * catalogo por la mas parecida que si aparece. null si no hay nada que cambiar.
     */
    public String corregir(List<String> ws) {
        List<String> salida = new ArrayList<String>();
        boolean cambio = false;
        for (int i = 0; i < ws.size(); ++i) {
            String w = ws.get(i);
            String r = raiz(w);
            boolean ultima = i == ws.size() - 1;
            if (esNumero(w) || VACIAS.contains(w) || this.vocabulario.containsKey(r) || this.sinonimos.containsKey(r)
                    || (ultima && this.hayPrefijo(w))) {
                salida.add(w);
                continue;
            }
            int tol = tolerancia(r);
            String mejor = null;
            int dMejor = tol + 1;
            int nMejor = 0;
            if (tol > 0) {
                for (Map.Entry<String, Integer> v : this.vocabulario.entrySet()) {
                    int d = distancia(r, v.getKey(), tol);
                    if (d < dMejor || (d == dMejor && d <= tol && v.getValue() > nMejor)) {
                        mejor = v.getKey();
                        dMejor = d;
                        nMejor = v.getValue();
                    }
                }
            }
            if (mejor != null && dMejor <= tol) {
                String f = this.forma.get(mejor);
                salida.add(f == null ? mejor : f);
                cambio = true;
            } else {
                salida.add(w);
            }
        }
        return cambio ? String.join(" ", salida) : null;
    }

    private boolean hayPrefijo(String w) {
        if (w.length() < 2) {
            return true;
        }
        for (String v : this.vocabulario.keySet()) {
            if (v.startsWith(w)) {
                return true;
            }
        }
        return false;
    }
}
