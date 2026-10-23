# App del celular: captura de compras

La mayoría de las compras se hacen desde el celular (Mercado Libre, Amazon, transferencias, apps del banco…). Esta app permite registrarlas **en el momento**:

1. Tomas captura de pantalla del comprobante.
2. La abres en la app (o, en Android, la **compartes** directo a "Compras").
3. Un OCR que corre **dentro del celular** lee el texto y propone el **monto**, la **fecha** y la **tienda**. Si hay varios importes (subtotal, envío, total…) aparecen como botones para elegir con un toque.
4. Escribes el concepto, tocas la sucursal y guardas. Se va acumulando en una base de datos del propio celular.
5. Cuando quieras (diario, semanal), **Enviar a la computadora** genera un CSV que mandas por WhatsApp, correo o Drive y lo importas en la herramienta de la computadora (*Importar / conciliar*). No se duplica nada aunque lo envíes dos veces.

Las imágenes no salen del teléfono: el OCR es local (Tesseract) y funciona sin internet una vez cargada la app.

## Ponerla en el celular

La app necesita estar publicada en una dirección `https://` para poderse instalar. Como el repositorio es público, lo más simple es **GitHub Pages** (gratis):

1. En GitHub: *Settings → Pages → Build and deployment → Deploy from a branch*, elige la rama (por ejemplo `main`) y la carpeta `/ (root)`.
2. A los pocos minutos la app queda en:
   `https://geco96.github.io/chromis-1.5.4-mejoras/gestion-compras/movil/`
3. Ábrela en el celular e instálala:
   - **Android (Chrome)**: menú ⋮ → *Instalar app*. Ya instalada, al compartir una captura aparece **Compras** como destino.
   - **iPhone (Safari)**: *Compartir → Agregar a pantalla de inicio*. iOS no deja que las apps web reciban "Compartir", así que se abre la app y se elige la captura desde Fotos.

La herramienta de la computadora queda también publicada en `…/gestion-compras/`, pero puede seguir usándose localmente con doble clic.

## Mismas sucursales que en la computadora

En la computadora, *Negocios y reglas → Enviar negocios, cuentas y reglas al celular* descarga un archivo. Mándatelo al celular y ábrelo en la app (⚙︎ → *Cargar archivo de la computadora*). Así:

- las sucursales y cuentas son exactamente las mismas, y
- las **reglas** ("COSTCO → Sucursal Centro") también funcionan en el celular.

También se pueden agregar sucursales y cuentas a mano en el celular; al importar, la computadora las reconoce por nombre.

## Qué aprende la app

- Si una tienda ya la usaste antes, propone la misma sucursal y cuenta que la última vez.
- Reconoce tiendas comunes (Mercado Libre, Amazon, Walmart, Costco, OXXO, CFE, Uber…) y etiquetas como "Para:", "Comercio:", "Pagaste a".
- Los conceptos recientes aparecen como botones para no reescribirlos.

## Archivos

- `index.html`: la app (HTML + JavaScript, sin dependencias externas).
- `sw.js`: service worker (funciona sin internet y recibe las imágenes compartidas en Android).
- `manifest.webmanifest`, `icon*`: instalación como app.
- `vendor/tesseract/`: copia local del motor OCR y del modelo de español (~10 MB, se descarga una sola vez).
