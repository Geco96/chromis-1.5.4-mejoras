# Chromis POS 1.5.4 — mejoras

Mejoras sobre **Chromis POS y Chromis Admin 1.5.4 (232306)** sin recompilar
todo el programa: solo se reemplazan, dentro de `chromispos.jar` y `admin.jar`
originales, las clases que se modificaron.

## Estructura

| Carpeta | Contenido |
|---|---|
| `original/` | Archivos instalados de Chromis POS 1.5.4, **sin modificar**. |
| `original-admin/` | `admin.jar` de Chromis Admin 1.5.4, **sin modificar**. |
| `src/` | Clases modificadas o nuevas de Chromis POS. |
| `src-admin/` | Clases y pantallas (FXML) modificadas o nuevas de Chromis Admin. |
| `src-common/` | Código que va en ambos: respaldos y tablas de precio sobre costo. |
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

### Tablas nuevas

Se crean solas la primera vez; **las tablas originales de Chromis no se
modifican**, por lo que volver al jar original no causa problemas.

| Tabla | Uso |
|---|---|
| `costpricing_customers (customer, markup)` | Margen sobre costo (%) por cliente. |
| `costpricing_excluded (product)` | Productos que conservan su precio normal. |
| `backup_settings` | Configuración y estado de los respaldos (una fila, `id = 1`). |

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
