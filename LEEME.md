# Descargas: Chromis 1.5.4 con mejoras

Archivos compilados de la rama `mejoras` (commit `4e9d0a5`), listos para instalar.
Las instrucciones completas están en el README de la rama `mejoras`.

| Archivo | Dónde va | SHA-256 |
|---|---|---|
| `chromispos.jar` | Carpeta de instalación de Chromis POS, en cada caja | `36f52cb0416b0bfceff5dc66cb1d6ec697748e2d934a4d23110b98afed4bd798` |
| `admin.jar` | Carpeta de instalación de Chromis Admin | `ae0137d32b606214e07263c0fb3af8ba5ef4295fb15fb2765fd6922d0964f8fe` |
| `restaurar.jar` | Carpeta de instalación de Chromis (donde está `chromisposconfig.properties`); se abre con doble clic para restaurar un respaldo | `5562c4cd7101a6f5a83f3a40b462d67690697a0b6967a146d0b0638a1cfe7efe` |

## Novedades de esta versión (también en `enterprise/`)

- **Ventas en espera borradas** (Admin → Utilities): queda una copia de cada
  venta en espera que sale de la lista sin cobrarse (borrada con el bote, con
  *borrar todas* —clic derecho en el botón de ventas en espera o F10— o perdida
  porque Chromis se cerró con ella abierta). Se ve quién la capturó, cómo y
  quién la borró, y se puede **restaurar** a las ventas en espera de todas las
  cajas. Las copias se guardan 60 días.
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

Compilados de la rama `enterprise` (commit `6df44e7`): todo lo de `mejoras` más
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
| `enterprise/chromispos.jar` | Cajas de la distribuidora | `72e45c0ff90d1a7f0c9adab9012f22c8f5c2b44ff4ea26049d746fa6546c5a23` |
| `enterprise/admin.jar` | Chromis Admin de la distribuidora | `87dfc7e88719290c77558b1ff9def3ffa1638998b98097b93f08c6ba812d8ba1` |
| `enterprise/restaurar.jar` | Igual que el de arriba (no cambia) | `2c398e3dee6e9aa6ef4832b6e697651f80e13ffd537743a7b52419e883eb07f0` |

Se instalan igual (renombrar el actual a `.original` y copiar el nuevo), **en todas las cajas** de la distribuidora: una caja con el jar anterior vende normal, pero no tiene el botón Mostrador / Entrega ni registra entregas. Las
tablas nuevas se crean solas; volver a los jar de `mejoras` no daña nada.
La parte del repartidor es Chromis Android (`chromis-android`, rama `main`):
*Mis entregas* con el permiso "Mis entregas (repartidor)".
