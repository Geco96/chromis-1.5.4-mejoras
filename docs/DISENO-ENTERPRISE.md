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
| Pedido tentativo | Diario, a hora programada, sin depender del cierre de caja. Terminal designada; si estaba apagada, se arma al encender. Un borrador por proveedor; el del día anterior sin revisar se reemplaza. |
| Envío | Nunca automático. Revisión, ajuste y "Enviar" con permiso de *Orders*; se registra quién y cuándo. |
| Proveedor por producto | Preferente de la tienda → distribuidora (si está suscrita, el producto está en el catálogo y hay disponible) → último proveedor. "Preferir distribuidora" se puede apagar. |
| Confirmación en la distribuidora | Siempre manual, línea por línea (completo / parcial / agotado). |
| Pago | **Al recibir.** Confirmar crea una venta en espera (`sharedtickets`) marcada Entrega + apartado de stock. El repartidor ajusta y cobra al entregar; ahí se descuenta stock. Cancelar libera el apartado. |
| Disponible | stock − apartado (catálogo, confirmaciones y, de preferencia, cajas de la distribuidora). |
| Precio de lista | General, igual para todos. |
| Precio dominante | **Se configura por cliente**; excepciones por producto para ese cliente (ver abajo). |
| Costo | Nunca se publica; a cada tienda se le publica su precio ya calculado. |
| Precio que vale | El de la confirmación (el catálogo es referencia). |

## Precio dominante

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

- Marca Mostrador / Entrega por scripts BeanShell → en `enterprise` va nativo en `JPanelTicket`.
- Sincronizador de clientes y entregas hacia Supabase y la web de rutas → se integra al conector.

## Pendiente de definir

- Hora por omisión del borrador diario y quién recibe el aviso.
- Si las cajas de la distribuidora deben bloquear o solo avisar al vender lo apartado.
- Clave nueva de Google Maps (la anterior se desactivó).
