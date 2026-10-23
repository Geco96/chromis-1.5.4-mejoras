# Chromis Enterprise (mayoreo) — diseño

Estado: **diseño acordado, sin código**. Rama `enterprise`, creada a partir de `mejoras`.
Tableta (`chromis-android`): rama `enterprise` después; no es prioridad.

## Objetivo

Dos planes juntos:
1. Instalar puntos de venta (Chromis) en tiendas, empezando por papelerías.
2. **Ofrecer** (nunca obligar) que las tiendas surtan con la distribuidora, que también usa Chromis.

El ciclo debe correr solo, con la menor intervención posible, pero **todo envío y toda confirmación
los revisa y aprueba una persona**.

## Ciclo

```
 Tienda                               Nube (Supabase)      Distribuidora (Chromis)
 Ventas ─► umbrales (mín/reserva/máx)
 Borrador diario a la hora HH:MM
 Revisión + "Enviar" (permiso Orders) ─► pedido ─────────► Pedidos de clientes
                                                          precio dominante (cliente / excepción)
                                                          "Confirmar" ─► venta en espera + apartado
                                      confirmación ◄────── cantidades y precios finales
                                                          Ruta en Google Maps
                                                          Repartidor ajusta y COBRA al entregar
                                                          ─► descuenta stock, libera apartado
 Recepción con folio, precargada ◄─── entrega cobrada ◄───
 faltantes ─► siguiente borrador · costos ─► Autorizar costos
```

## Lo que ya existe (rama `mejoras`) y se reutiliza

- Umbrales: `stocklevel` (mínimo/máximo) + `restock_reserve`; cuánto pedir = máximo − stock − en camino + faltantes.
- Pedido en `supplierorders` con folio Code 128; recepción; `order_carryover`; `price_changes`.
- Respaldos programados: modelo de "terminal designada + hora + último resultado".
- Precio sobre costo por cliente (`costpricing_customers`, `costpricing_excluded`) y precios por volumen (`tierpricing`).
- Tableta: ventas en espera (M15), cobro, mismas tablas de reposición.
- Monitor: sincronización con Supabase por sucursal (`productos_sync`).

## Decisiones

| Tema | Decisión |
|---|---|
| Sistema de la distribuidora | Chromis con las mejoras |
| Pedido tentativo | Diario, **16:30 por omisión, configurable** (`pos_settings`), sin depender del cierre de caja. Terminal designada; si estaba apagada, se arma al encender. Aviso de "borrador listo" a los usuarios con permiso de *Orders*. Un borrador por proveedor; el del día anterior sin revisar se reemplaza. |
| Envío | Nunca automático. Revisión, ajuste y "Enviar" con permiso de *Orders*; se registra quién y cuándo. |
| Proveedor por producto | Preferente de la tienda → distribuidora (si está suscrita, el producto está en el catálogo y hay disponible) → último proveedor. "Preferir distribuidora" se puede apagar. |
| Confirmación en la distribuidora | Siempre manual, línea por línea (completo / parcial / agotado). |
| Pago | **Al recibir.** Confirmar crea una venta en espera (`sharedtickets`) marcada Entrega + apartado de stock. El repartidor ajusta y cobra al entregar; ahí se descuenta stock. Cancelar libera el apartado. |
| Disponible | stock − apartado en el catálogo y las confirmaciones. |
| Venta en caja de lo apartado | **Solo se avisa**, no se bloquea. El aviso dice qué pedido lo tiene apartado. |
| Precio de lista | General, igual para todos. |
| Precio dominante | **Se configura por cliente**; excepciones por producto para ese cliente (ver abajo). |
| Costo | Nunca se publica; a cada tienda se le publica su precio ya calculado. |
| Precio que vale | El de la confirmación (el catálogo es referencia). |

## Precio dominante

Alrededor del 95 % de los clientes pagan el precio de lista; las excepciones son pocas. Por eso la
configuración es opcional: un cliente sin configuración paga lista (con volumen), y solo los pocos
especiales se configuran. Es de las últimas fases.

Opciones: **Lista**, **Por volumen** (`tierpricing`), **Sobre costo (+ %)**.

Prioridad al calcular el precio de un producto para una tienda:

1. **Excepción del cliente para ese producto** (si existe): su propio modo (y % si es sobre costo).
2. **Configuración del cliente**: modo dominante y %.
3. **Lista general** (con volumen, como todos).

Además:
- Se conserva `costpricing_excluded`: productos que nunca van sobre costo para nadie.
- Al confirmar un pedido, el encargado puede cambiar el precio de una línea; queda registrado.
- La caja de la distribuidora usa la misma regla, para que la tienda pague lo mismo en mostrador que por pedido.

Tablas nuevas propuestas (las originales no cambian, como en `mejoras`):

| Tabla | Uso |
|---|---|
| `pricing_customer (customer, mode, markup)` | Modo dominante por cliente (amplía `costpricing_customers`, que se migra). |
| `pricing_exception (customer, product, mode, markup)` | Excepciones por producto para un cliente. |
| `stock_reservado (order_id, product, location, units)` | Apartados por pedido confirmado. |
| `restock_drafts`, `restock_draft_lines` | Borradores diarios por proveedor. |
| `distributor_link (customer, tienda_id)` | Liga el cliente de la distribuidora con la cuenta de la tienda en la nube. |

## Gestor de entregas (web en Render, ampliado)

La web de clientes ya tiene roles **admin**, **despachador** (Centro de Distribución) y **repartidor**,
pines por estado, asignación de ruta y Ruta Óptima. Se amplía para los pedidos confirmados de la distribuidora.

**Cuadrantes**
- El admin dibuja los cuadrantes en el mapa (polígonos) y les pone nombre y color.
- Cada cliente cae en un cuadrante por sus coordenadas; al moverle el pin, se recalcula.
- El **despachador** asigna los cuadrantes a los repartidores; un repartidor puede tener **varios** cuadrantes.
  Los pedidos confirmados de los clientes de esos cuadrantes le llegan solos.
- Un cliente sin ubicar aparece en "sin ubicar" hasta que se le ponga el pin.

**Estados de una entrega**

```
Confirmado ─► Preparado ─► En ruta ─► Completado ─► Liquidado (en la caja)
                                   └─► No entregado (con nota) ─► reprogramar / cancelar (libera apartado)
```

| Rol | Qué hace |
|---|---|
| Admin | Dibuja los cuadrantes, usuarios, colores, ve todo. |
| Despachador | Asigna cuadrantes a repartidores, surte y marca **Preparado**, da la salida a ruta. |
| Repartidor | Solo la web en el celular, **sin Chromis**: ve su ruta con Ruta Óptima y marca **Completado** (o No entregado con nota). |

**Cobro y liquidación**: el repartidor no registra montos. Al regresar entrega el dinero y, en la caja de la
distribuidora, la **liquidación del repartidor** lista sus pedidos *Completados*: el cajero cierra cada venta en
espera y cuadra el efectivo. Ahí se descuenta el stock y se libera el apartado.

**Rechazos**: el repartidor no anota nada; trae de vuelta lo rechazado. El **cajero** lo revisa al liquidar,
quita o reduce esas líneas de la venta y cobra solo lo entregado. Lo devuelto regresa al stock disponible
(nunca se descontó) y, del lado de la tienda, aparece como faltante para su siguiente pedido.

**Límite de Google**: una ruta de Directions admite 25 paradas; un cuadrante con más se parte en tandas.

## Gestor de entregas dentro de Chromis (decisión del 22/10/2026)

Reemplaza a la web de Render para las entregas: todo vive en la base de Chromis de la distribuidora.
La web (`AgendaClientes`, rama `enterprise`) y el puente del monitor quedan archivados, sin publicar.

| Pieza | Dónde |
|---|---|
| Cuadrantes, asignación de repartidores, estados de cada entrega, ubicación de clientes | Chromis Admin: pantalla *Gestor de entregas*. El mapa de Google se abre en el navegador de la PC, servido por Admin solo en 127.0.0.1 (el visor web de JavaFX 11 es viejo para Google Maps y puede no venir en el Java instalado). Tablas nuevas en la base de Chromis. |
| Repartidor | App de Android de Chromis, pantalla *Mis entregas*, conectada a la base por la VPN (Tailscale). |
| Mapa | Google Maps (búsqueda de direcciones mexicanas y orden de paradas). La navegación la hace la app de Google Maps / Waze. OpenStreetMap solo como respaldo. |
| Clientes existentes en la web | Se importan una sola vez con sus ubicaciones. |

**Rol Repartidor** (por ahora): solo *Mis entregas* y **consulta de precios**. No vende, no cobra, no hace
pedidos ni cortes. **Vendedor de campo**: no existe todavía, pero el diseño lo deja previsto (captura de
clientes con GPS y pedidos como venta en espera).

**Confirmación de la entrega** (formulario rápido en *Mis entregas*):
- **Completado** o **No entregado**.
- **Monto cobrado / recibido** (lo que el cliente pagó al recibir, puede ser distinto al total).
- **Anotaciones**: faltó mercancía, rechazo, daño, etc.
- Todo queda consultable por el **despachador** en Admin (y sirve para la liquidación al regresar).

**Nada a medias** (como transacción bancaria):
- Ventas y cobros solo con conexión, en una transacción (InnoDB): se guarda todo o nada.
- La confirmación de entrega sí se puede hacer sin señal: se guarda en el celular como *pendiente de
  enviar* y se manda sola al volver la conexión.
- Cada confirmación lleva un identificador creado en el celular; al reintentar, el servidor no la
  aplica dos veces (si ya existe, se toma como hecha). Así un corte justo después de guardar no duplica nada.
- Seguridad: VPN limitada al puerto de la base, celulares que se pueden dar de baja, usuario de base
  con menos permisos que las cajas.

## Arquitectura

```
 Tienda (red local)                Nube (Supabase)                 Distribuidora
 BD Chromis ◄─ conector ─────────► catálogos por giro ◄────────── conector ─► BD Chromis
 Escritorio · Tableta · Monitor    suscripciones, pedidos,          Admin: Pedidos de clientes
                                   confirmaciones, entregas         Web (Render): mapa y rutas
```

- **Un conector por instalación**: se amplía `chromis-monitor` (ya sincroniza con Supabase) en lugar de otro programa.
  Si la BD vive en la tableta, la app de la tableta hace ese papel.
- **Acceso por giro**: cada tienda con su credencial; RLS solo deja leer catálogos suscritos y su propio precio.
  Sin suscripción (p. ej. abarrotes) la opción no aparece en ningún cliente.
- **Correspondencia de productos**: código de barras; si no, `productsuppliers.stockcode`. Lo que no exista en la tienda
  se muestra como "disponible en la distribuidora" con opción de darlo de alta.
- **Catálogo publicado**: solo activo, no retirado y disponible por encima del mínimo de seguridad; cada pocos minutos.

## Ya prototipado en la rama `ccr-f5ab2a7d-9n1yfk` (salió de `main`, no se reutiliza tal cual)

- Marca Mostrador / Entrega por scripts BeanShell → ya va nativo en `JPanelTicket` (fase 2).
- Sincronizador de clientes y entregas hacia Supabase y la web de rutas → se integra al conector.

## Avance

| Fase | Estado |
|---|---|
| 1. Pedido tentativo diario (16:30), revisión y envío | **Hecha.** Mejora 13 del README; envío como pedido de Chromis con PDF. |
| 2. Mostrador / Entrega en la caja | **Hecha.** Mejora 14 del README: botón en el ticket, pregunta al cobrar, datos de entrega desde el cliente en `customer_delivery`, pantalla Admin → Entregas. |
| 3. Gestor de entregas (cuadrantes, estados, liquidación) | **En curso dentro de Chromis.** Hechas las tablas (ubicaciones, cuadrantes, estados e historial con operación única) y la importación de clientes de la web (mejora 15), y la pantalla *Gestor de entregas* en Admin con el mapa en el navegador de la PC (mejora 16). *Mis entregas* y el rol Repartidor están en Chromis Android (`chromis-android`, rama `mejoras`): confirmación con monto, incidencia y notas, guardada en el celular sin señal y enviada con su identificador de operación; ruta en Google Maps; consulta de precios. Las reglas de `uk.chromis.pos.delivery` son las mismas en los dos (copia idéntica). Antes: **se rehace dentro de Chromis** (ver "Gestor de entregas dentro de Chromis"). La versión web quedó hecha y archivada, que llega con la fase 5 (hoy las entregas de la caja se cobran al vender). Web: `AgendaClientes`, rama `enterprise` (`ENTERPRISE.md`); puente: `chromis-monitor`, rama `enterprise` (`services/entregas_sync.py`). |
| 4. Nube y conectores (catálogo, pedido y confirmación) | Pendiente |
| 5. Pedidos de clientes en la distribuidora (apartados) | Pendiente |
| 6. Precio especial por cliente con excepciones | Pendiente |

## Pendiente de definir

- Clave de Google Maps para el mapa de entregas: Maps JavaScript API y Geocoding API, restringida a sitios web `http://127.0.0.1:47123/*`; se pone en Admin → Gestor de entregas → Configurar mapa.
