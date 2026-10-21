# Chromis POS 1.5.4 — Enterprise (mayoreo)

## Pedido de Mostrador o de Entrega

En el ticket aparece el botón **Mostrador / Entrega**.

- **Mostrador**: venta normal.
- **Entrega a domicilio**: pide que el ticket tenga cliente y deja capturar indicaciones para el
  repartidor. El ticket impreso dice `*** PEDIDO PARA ENTREGA ***`.
- Si no se marcó nada, al cobrar se pregunta (solo si hay cliente). Sin cliente queda como Mostrador.

El tipo se guarda como propiedad del ticket (`tipo_pedido`, `entrega_notas`) en `receipts.attributes`.
No se recompila Chromis: son scripts en la tabla `resources`.

## Del POS a la ruta de Google Maps

`web/sync_local.py` (en la PC del POS) lee los tickets de "Entrega" de los últimos 7 días y en Supabase:
1. Registra el pedido en la tabla `entregas`.
2. Pone al cliente en **Pedido Activo**, que es lo que usa el botón **Ruta Óptima** de la web.
3. Si el cliente se dio de alta en el POS y no existe en la web, lo crea sin coordenadas. En la web aparece
   el botón **⚠️ N sin ubicar**: al elegirlo se busca su dirección en el mapa (o se pone el pin a mano).

Cuando en la web el cliente deja de estar en "Pedido Activo", sus entregas quedan como `entregado`.

## Instalación
1. Respaldo de la base de Chromis.
2. En Supabase, ejecutar `supabase_entregas.sql`.
3. En la PC del POS, con Chromis cerrado:
   `pip install -r ../web/requirements-pos.txt` y luego `python instalar.py` (con `CHROMIS_DB_URL` definido).
4. Arrancar `web/sincronizar_chromis.bat`.

## Archivos
| Archivo | Para qué |
|---|---|
| `recursos/script.TipoPedido.txt` | Botón Mostrador / Entrega |
| `recursos/script.TipoPedido.Total.txt` | Evento `ticket.total`: pregunta si falta |
| `recursos/fragmento.Ticket.Buttons.xml` | Se agrega a `Ticket.Buttons` |
| `recursos/fragmento.Printer.Ticket.xml` | Se agrega a `Printer.Ticket` |
| `instalar.py` | Instala todo lo anterior en la base (no duplica) |
| `supabase_entregas.sql` | Columna `chromis_id` y tabla `entregas` |

Si ya usabas un evento `ticket.total` propio en `Ticket.Buttons`, revisa que no choque con este.
