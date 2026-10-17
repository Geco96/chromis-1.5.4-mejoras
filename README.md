# Chromis POS 1.5.4 — mejoras

Mejoras sobre **Chromis POS y Chromis Admin 1.5.4 (232306)** sin recompilar
todo el programa: solo se reemplazan, dentro de `chromispos.jar` y `admin.jar`
originales, las clases que se modificaron.

## Estructura

| Carpeta | Contenido |
|---|---|
| `original/` | Archivos instalados de Chromis POS 1.5.4, **sin modificar**. |
| `original-admin/` | `admin.jar` de Chromis Admin 1.5.4, **sin modificar**. |
| `original-createdb/` | `dbcreator.jar` 1.5.4 (crea la base; solo se usó para pruebas). |
| `src/` | Clases modificadas o nuevas de Chromis POS. |
| `src-admin/` | Clases y pantallas (FXML) modificadas o nuevas de Chromis Admin. |
| `src-common/` | Código que va en ambos: respaldos, precio sobre costo y umbrales de reposición. |
| `tools/PatchJar.java` | Genera cada jar final: copia el original y reemplaza solo lo compilado. |
| `tools/javafx-11.0.2/` | JavaFX, solo para compilar Admin (en ejecución se usa el del JRE instalado). |
| `tests/` | Pruebas manuales contra MySQL/MariaDB (no van dentro del jar). |
| `dist/` | Resultado de la compilación (no se versiona). |

## Mejoras incluidas

1. **Lector de códigos de barras**: se ignoran los caracteres invisibles que
   envían algunos lectores (p. ej. Kuiiyer E308) y los programas que simulan
   teclado. El lector puede usarse en modo teclado normal, sin Scanpal2 ni
   scripts de AutoHotkey.
2. **Precios por niveles**: las líneas se pueden editar. Si solo cambia la
   cantidad, el precio se recalcula al nivel correspondiente; si se cambia el
   precio, se respeta.
3. **Productos auxiliares**: aparecen en el catálogo como cualquier producto y
   ya no se descartan si no hay una línea seleccionada.
4. **Precio sobre costo por cliente**: ciertos clientes pagan *costo + margen %*.
   Tiene prioridad sobre niveles y descuentos. Si un producto no tiene costo,
   se avisa y no se permite venderlo a ese cliente.
   - Admin → Clientes, pestaña general: interruptor **Precio sobre costo** y
     **Margen sobre costo %**.
   - Admin → Productos, primera pestaña: interruptor **Precio sobre costo**
     (encendido por omisión; apagado = ese producto conserva su precio normal).
5. **Respaldos periódicos**: la terminal designada respalda la base completa
   (tablas, vistas, triggers, procedimientos) en un `.zip` dentro de la carpeta
   elegida (local, red o Google Drive), conserva los N más recientes y avisa al
   cajero si falla. No requiere `mysqldump`.
   - Admin → Utilidades → **Respaldos programados**: configuración, estado del
     último respaldo, *Pedir respaldo ahora a la terminal* y *Respaldar ahora
     desde esta PC*. Usa el mismo permiso que el respaldo manual de Admin.
6. **Reposición** (lo que hacía el monitor *Lite* de sucursales):
   - Cada producto con *Controlar stock* tiene **mínimo, reserva y máximo**.
     Estados: *Agotado* (≤ 0), *Bajo* (≤ mínimo), *Reserva* (≤ reserva),
     *Normal*. Mínimo y máximo son los niveles de stock propios de Chromis (la
     pestaña *Stock Levels* del producto los muestra); la reserva se guarda aparte.
   - Admin → Stock Management → **Reposición**: tabla con el estado de cada
     producto, filtros y búsqueda. Los umbrales se escriben directo en la tabla.
     *Pedir* = máximo − stock y se puede cambiar. **Generar PDF de pedido**
     crea el pedido de los productos marcados, con el nombre de la empresa, la
     fecha, el usuario y espacio para la firma. Usa el permiso de *Orders*.
   - En la caja, al cobrar, un aviso en la esquina indica qué productos
     **entraron en reserva, quedaron bajo el mínimo o se agotaron**. También
     resume al abrir la caja cuántos productos están agotados o bajos. El aviso
     no quita el foco (el lector sigue escribiendo en el ticket), se cierra solo
     y se quita con un clic. Se activa o desactiva en la misma pantalla de Admin.
   - Corrección en Admin → Productos: al guardar un producto con niveles de
     stock, Chromis volvía a escribir el stock que tenía cuando se abrió el
     producto; las ventas hechas mientras estaba abierto se perdían del
     inventario. Ahora el stock no se toca al guardar.

### Tablas nuevas

Se crean solas la primera vez; **la estructura de las tablas originales de
Chromis no cambia**, por lo que volver al jar original no causa problemas
(los mínimos y máximos de reposición se guardan en la tabla original
`stocklevel` y el jar original también los entiende).

| Tabla | Uso |
|---|---|
| `costpricing_customers (customer, markup)` | Margen sobre costo (%) por cliente. |
| `costpricing_excluded (product)` | Productos que conservan su precio normal. |
| `backup_settings` | Configuración y estado de los respaldos (una fila, `id = 1`). |
| `restock_reserve (location, product, reserve)` | Nivel de reserva por producto. |
| `restock_settings` | Avisos en caja activados y aviso de reserva (una fila, `id = 1`). |

Se configuran desde Admin; si hiciera falta, también con SQL, por ejemplo:

```sql
-- Cliente con 7.5 % sobre costo (id del cliente de la tabla customers)
INSERT INTO costpricing_customers (customer, markup) VALUES ('<id-cliente>', 7.5);

-- Respaldos cada 24 h desde la terminal CAJA1, conservando 30
UPDATE backup_settings
   SET enabled = 1, terminal = 'CAJA1', folder = 'G:\\Mi unidad\\Respaldos Chromis',
       interval_hours = 24, keep_count = 30
 WHERE id = 1;
-- Respaldar ahora
UPDATE backup_settings SET run_now = 1 WHERE id = 1;
```

El nombre de la terminal es el que Chromis usa como `terminalID`.

## Pruebas realizadas

Con una base creada por el `dbcreator.jar` 1.5.4 (en `original-createdb/`) sobre
MariaDB, se ejecutaron Chromis POS y Chromis Admin modificados (capturas en
`docs/capturas/`):

| Prueba | Resultado |
|---|---|
| Código con caracteres invisibles delante (STX, GS, ancho cero, uso privado) | Original: *"❈7500000000024 - This Product Code does not exist"*. Mejorado: agrega el producto. |
| Editar línea de producto con niveles (10 pzas → $25) | Original: botón de editar desactivado. Mejorado: cantidad 12 → $25 c/u; precio manual $27 se respeta. |
| Auxiliar en catálogo y con ticket vacío | Original: no aparece y escanearlo con ticket vacío no agrega nada. Mejorado: aparece y se agrega. |
| Cliente con 7.5 % sobre costo | Arroz $30 (costo $20) → $21.50; producto excluido conserva $80; producto sin costo muestra aviso y no se agrega; al quitar el cliente vuelve a $30. |
| Captura en Admin | Margen del cliente y opción del producto se guardan y se vuelven a mostrar al reabrir. |
| Respaldo programado | La caja designada respaldó al abrir ventas y al pedirlo desde Admin; la restauración tiene las mismas tablas, vista, 35 triggers y rutina, con checksums iguales. |
| Reposición en Admin | Umbrales escritos en la tabla se guardan en `stocklevel`/`restock_reserve` y se ven en *Stock Levels* del producto; reserva menor que el mínimo se rechaza con mensaje; PDF con 2 productos y cantidad editada a mano. |
| Avisos en caja | Al abrir: "1 producto bajo el mínimo". Ventas de Arroz (mín. 5, reserva 10): 12 → 9 *En reserva*, 9 → 4 *Bajo el mínimo*, 4 → 3 sin aviso, 3 → 0 *Se agotó*. El lector siguió funcionando con el aviso en pantalla. |
| Guardar producto en Admin con ventas de por medio | Original: el stock regresó de 7 a 11 (se perdió la venta). Mejorado: se conserva. |

El botón de editar línea solo aparece a usuarios con el permiso
`button.lineeditor` (comportamiento original de Chromis; se asigna en Admin →
Roles).

## Compilar

Requiere **JDK 11 o superior** (no basta el JRE).

- Windows: `build.bat`
- Linux/macOS: `./build.sh`

El resultado queda en `dist\chromispos.jar` y `dist\admin.jar`.

## Instalar

Igual para cada programa (`chromispos.jar` en las cajas, `admin.jar` donde se
use Chromis Admin):

1. Cerrar el programa.
2. En la carpeta de instalación, **renombrar** el jar a `*.jar.original`
   (es el respaldo para volver atrás).
3. Copiar ahí el jar nuevo de `dist\`.
4. Abrir el programa y probar.

Para volver atrás: cerrar el programa, borrar el jar nuevo y quitar
`.original` al nombre del respaldo.

Se recomienda probar primero en **una sola terminal** antes de copiarlo a las demás.

## Restaurar un respaldo

Descomprimir el `.zip` y cargar el `.sql` con el cliente de MySQL:

```
mysql -u USUARIO -p NOMBRE_BASE < chromis-AAAAMMDD-HHMMSS.sql
```

Esto **reemplaza** el contenido de esa base: hacerlo con Chromis cerrado en
todas las terminales.
