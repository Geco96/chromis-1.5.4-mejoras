# Agenda web ↔ Chromis POS 1.5.4

Panel de clientes (Flask + Supabase) con envío de clientes a la tabla `customers` de Chromis.

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
