# Descargas: Chromis 1.5.4 con mejoras

Archivos compilados de la rama `mejoras` (commit `2a02bee`), listos para instalar.
Las instrucciones completas están en el README de la rama `mejoras`.

| Archivo | Dónde va | SHA-256 |
|---|---|---|
| `chromispos.jar` | Carpeta de instalación de Chromis POS, en cada caja | `fb1f3da699ff9cdbbbce304816ac5e871e24d63e8d43dda9faa34c127eec5fd9` |
| `admin.jar` | Carpeta de instalación de Chromis Admin | `e8d4d554e919b5e67f53287d42b15f8cbc0672da0a9d3c5ab2150dd00c5c48e1` |
| `restaurar.jar` | Carpeta de instalación de Chromis (donde está `chromisposconfig.properties`); se abre con doble clic para restaurar un respaldo | `5562c4cd7101a6f5a83f3a40b462d67690697a0b6967a146d0b0638a1cfe7efe` |

## Novedades de esta versión (también en `enterprise/`)

- **Ventas en espera entre varias cajas** (corregido de Chromis original): si
  otra caja abría la venta elegida un momento antes, la caja guardaba su
  ticket duplicado y la venta de la otra caja se perdía ("Duplicate entry");
  dos cajas podían abrir la misma venta a la vez; y si guardar fallaba, la
  venta en pantalla se perdía. Ahora solo una caja abre cada venta, nada se
  duplica y una venta que no se pudo guardar se queda en pantalla.
- En **Ventas en espera borradas** se ve dónde está cada una: *abierta ahora*
  en tal caja y por quién (mientras una caja la tiene abierta no aparece en
  las demás), *perdida*, o *cobrada* en tal ticket.
- **"Borrar todas" desactivado**: el clic derecho en el botón de ventas en
  espera y F10 ya no borran las ventas en espera de todas las cajas; solo
  muestran un aviso. Para quitar una, se abre y se borra con el bote.
- **Ventas en espera borradas** (Admin → Utilities): queda una copia de cada
  venta en espera que sale de la lista sin cobrarse (borrada con el bote, con
  *borrar todas* —clic derecho en el botón de ventas en espera o F10— o perdida
  porque Chromis se cerró con ella abierta). Se ve quién la capturó, cómo y
  quién la borró, y se puede **restaurar** a las ventas en espera de todas las
  cajas, como venta nueva marcada "(restaurada)", así que ya no choca con la
  misma venta si sigue abierta en una caja. También guarda las ventas a las
  que les quitaron los productos uno por uno. Las copias se guardan 60 días.
- **Corte de caja seguro**: cerrar el corte y abrir el siguiente se hace en una
  sola operación; si algo falla no cambia nada y se puede reintentar. Los
  errores quedan en `chromis-cortes.log` (carpeta del usuario de Windows).
- **Recargar día** funciona con cualquier número de corte (antes solo hasta el
  127) y muestra los cortes que quedaron "sin cerrar" para cerrarlos e imprimirlos.

Hay que actualizar `chromispos.jar` **en todas las cajas** y `admin.jar`.

## Instalar (en cada PC)

1. Cierra Chromis POS / Admin.
2. En la carpeta de instalación, renombra el jar actual agregando `.original`
   (por ejemplo `chromispos.jar` → `chromispos.jar.original`).
3. Copia aquí el jar nuevo con el mismo nombre.
4. Abre Chromis. Las tablas nuevas se crean solas.

Para volver atrás: borra el jar nuevo y quita `.original` al anterior.
Recomendado: probar primero en una sola caja.

## Restaurar un respaldo

Copia `restaurar.jar` a la carpeta de instalación de Chromis y ábrelo con doble
clic (como el `dbcreator.jar`). Toma la conexión de `chromisposconfig.properties`;
elige el `.zip` del respaldo (no hace falta descomprimirlo) y da **Restaurar**.
Antes de borrar la base revisa que el respaldo esté completo y muestra los
programas que la tienen abierta (Chromis POS, Admin, monitor), con opción de
cerrarlos. No necesita `mysql.exe`.

## Enterprise (carpeta `enterprise/`)

Compilados de la rama `enterprise` (commit `8b389d3`): todo lo de `mejoras` más
**Mostrador / Entrega** en la caja, **Pedido tentativo** diario y el **Gestor de
entregas** (Admin → Customers: estados, cuadrantes, repartidores y mapa de
Google en su propia ventana, pines sugeridos por la dirección y cierre de
entregas viejas) y el **Auditor de salida** (Admin → Customers: escanear la
mercancía de una venta antes de que salga).

**Nuevo en esta versión:** una venta ya cobrada como mostrador se puede
**convertir en entrega a domicilio** después, desde la caja (botón MOSTRADOR →
*Convertir una venta ya cobrada...*), desde Admin → Customers → Entregas o desde
el Gestor de entregas: se escribe o escanea el número de ticket, se revisan los
datos de entrega y se confirma. La venta, el pago, el corte y el inventario no
cambian. Solo ventas con cliente, de los últimos 7 días.

Es para la distribuidora; en las tiendas se
usan los de arriba.

| Archivo | Dónde va | SHA-256 |
|---|---|---|
| `enterprise/chromispos.jar` | Cajas de la distribuidora | `8c099a534ae72cac9774580e218f8717bad717a45c2b92d82b5d97fe4743d09e` |
| `enterprise/admin.jar` | Chromis Admin de la distribuidora | `96d9ae305f27b258f2ecc781d5202d733d738b641e71881bb33f7cfda2e1ab2d` |
| `enterprise/restaurar.jar` | Igual que el de arriba (no cambia) | `2c398e3dee6e9aa6ef4832b6e697651f80e13ffd537743a7b52419e883eb07f0` |

Se instalan igual (renombrar el actual a `.original` y copiar el nuevo), **en todas las cajas** de la distribuidora: una caja con el jar anterior vende normal, pero no tiene el botón Mostrador / Entrega ni registra entregas. Las
tablas nuevas se crean solas; volver a los jar de `mejoras` no daña nada.
La parte del repartidor es Chromis Android (`chromis-android`, rama `main`):
*Mis entregas* con el permiso "Mis entregas (repartidor)".
