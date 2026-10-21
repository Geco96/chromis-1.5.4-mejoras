# Agenda web ↔ Chromis POS 1.5.4

Panel de clientes (Flask + Supabase) con envío de clientes a la tabla `customers` de Chromis.

## Arquitectura
```
Navegador ──► Render (Flask) ──► Supabase ◄── sync_local.py (PC del POS) ──► MySQL de Chromis
```
Render está en internet y Chromis en la red local, así que Render **no** se conecta a Chromis.
El puente es `sync_local.py`, que corre en la PC del punto de venta.

## Instalación en la PC del POS
1. Instalar Python 3 y copiar esta carpeta `web/`.
2. `pip install -r requirements-pos.txt`
3. Editar `sincronizar_chromis.bat` con las claves y doble clic. Se sincroniza cada 5 minutos.
   (Para arrancarlo con Windows: acceso directo al .bat en `shell:startup`.)
4. Prueba única: `python sync_local.py --una-vez`

## Render
Sin cambios: `requirements.txt`, comando `gunicorn app:app`, variables `SUPABASE_URL` y `SUPABASE_KEY`.
El botón de sincronizar del panel solo funciona si además defines `CHROMIS_DB_URL` en Render
y la base de Chromis es accesible desde internet (no recomendado); en ese caso agrega
`PyMySQL` a `requirements.txt`.

## Google Maps
La clave ya no está escrita en `index.html`: define `GOOGLE_MAPS_API_KEY` en Render.
(También `FLASK_SECRET_KEY`.)

## Variables de entorno
- `SUPABASE_URL`, `SUPABASE_KEY`: igual que antes.
- `CHROMIS_DB_URL`: base de Chromis, p. ej. `mysql://usuario:clave@192.168.1.10:3306/chromispos`
  o `postgresql://usuario:clave@host:5432/chromispos`.

## Uso
Admin → ⚙️ → sección **Chromis POS** → *Sincronizar con Chromis*.

## Correspondencia de campos
| Web (`clientes`) | Chromis (`customers`) |
|---|---|
| `id_cliente` | `id` = `web-<id>`, `taxid` = `WEB<id>` |
| `nombre_comercial` | `name` (50 car.) |
| `telefono` | `phone` (20 car.) |
| `direccion_completa` | `address` (100 car.) |
| `latitud`, `longitud` | `address2` = `GPS lat,lng` |
| `notas_internas` + `asignado_a` | `notes` (255 car.) |

Sincronizar varias veces actualiza los mismos registros (no duplica). Los clientes dados de alta
directamente en el POS no se modifican. El `siteguid` se toma de la tabla `siteguid` de Chromis.
