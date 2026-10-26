# Descargas: Chromis 1.5.4 con mejoras

Archivos compilados de la rama `mejoras` (commit `5cf8ab1`), listos para instalar.
Las instrucciones completas están en el README de la rama `mejoras`.

| Archivo | Dónde va | SHA-256 |
|---|---|---|
| `chromispos.jar` | Carpeta de instalación de Chromis POS, en cada caja | `431f3f21e45b50779233e3e3082e33ece02dca4bb98f58757422837c12136bdd` |
| `admin.jar` | Carpeta de instalación de Chromis Admin | `752784a8dc6821d27f0f473df050320c0f4ee3835782438c6d3c7865cbec01d2` |
| `restaurar.jar` | Carpeta de instalación de Chromis (donde está `chromisposconfig.properties`); se abre con doble clic para restaurar un respaldo | `5562c4cd7101a6f5a83f3a40b462d67690697a0b6967a146d0b0638a1cfe7efe` |

## Novedades de esta versión (también en `enterprise/`)

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

Compilados de la rama `enterprise` (commit `836fcc1`): todo lo de `mejoras` más
**Mostrador / Entrega** en la caja, **Pedido tentativo** diario y el **Gestor de
entregas** (Admin → Customers: estados, cuadrantes, repartidores y mapa de
Google en su propia ventana, pines sugeridos por la dirección y cierre de
entregas viejas) y el **Auditor de salida** (Admin → Customers: escanear la
mercancía de una venta antes de que salga). Es para la distribuidora; en las tiendas se
usan los de arriba.

| Archivo | Dónde va | SHA-256 |
|---|---|---|
| `enterprise/chromispos.jar` | Cajas de la distribuidora | `4e4f91234f189e14a419b51f6695f4944dd7ab4c0855db9c2e8fefaa6dc37130` |
| `enterprise/admin.jar` | Chromis Admin de la distribuidora | `7df12317cd818bbc8a0d6ad45564b79a204ea09909e8255c989150dc8fb39836` |
| `enterprise/restaurar.jar` | Igual que el de arriba (no cambia) | `2c398e3dee6e9aa6ef4832b6e697651f80e13ffd537743a7b52419e883eb07f0` |

Se instalan igual (renombrar el actual a `.original` y copiar el nuevo), **en todas las cajas** de la distribuidora: una caja con el jar anterior vende normal, pero no tiene el botón Mostrador / Entrega ni registra entregas. Las
tablas nuevas se crean solas; volver a los jar de `mejoras` no daña nada.
La parte del repartidor es Chromis Android (`chromis-android`, rama `main`):
*Mis entregas* con el permiso "Mis entregas (repartidor)".
