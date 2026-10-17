# Chromis POS 1.5.4 — mejoras

Mejoras sobre **Chromis POS 1.5.4 (232306)** sin recompilar todo el programa:
solo se reemplazan, dentro del `chromispos.jar` original, las clases que se
modificaron.

## Estructura

| Carpeta | Contenido |
|---|---|
| `original/` | Archivos instalados de Chromis 1.5.4, **sin modificar**. |
| `src/` | Clases modificadas o nuevas. Las modificadas se descompilaron del jar original (se verificó que recompilarlas sin cambios produce código equivalente). |
| `tools/PatchJar.java` | Genera el jar final: copia el original y reemplaza solo las clases de `src/`. |
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
4. **Precio sobre costo por cliente** (parte del punto de venta): ciertos
   clientes pagan *costo + margen %*. Tiene prioridad sobre niveles y
   descuentos. Si un producto no tiene costo, se avisa y no se permite
   venderlo a ese cliente. *Pendiente:* captura del margen en Chromis Admin.
5. **Respaldos periódicos** (parte del punto de venta): la terminal designada
   respalda la base completa (tablas, vistas, triggers, procedimientos) en un
   `.zip` dentro de la carpeta elegida (local, red o Google Drive), conserva
   los N más recientes y avisa al cajero si falla. *Pendiente:* pantalla de
   configuración en Chromis Admin.

### Tablas nuevas

Se crean solas la primera vez; **las tablas originales de Chromis no se
modifican**, por lo que volver al jar original no causa problemas.

| Tabla | Uso |
|---|---|
| `costpricing_customers (customer, markup)` | Margen sobre costo (%) por cliente. |
| `costpricing_excluded (product)` | Productos que conservan su precio normal. |
| `backup_settings` | Configuración y estado de los respaldos (una fila, `id = 1`). |

Mientras no esté la pantalla en Admin, se pueden configurar con SQL, por ejemplo:

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

El resultado queda en `dist\chromispos.jar`.

## Instalar en una caja

1. Cerrar Chromis POS.
2. En la carpeta de instalación, **renombrar** `chromispos.jar` a
   `chromispos.jar.original` (es el respaldo para volver atrás).
3. Copiar ahí el `dist\chromispos.jar` nuevo.
4. Abrir Chromis y probar.

Para volver atrás: cerrar Chromis, borrar el jar nuevo y renombrar
`chromispos.jar.original` a `chromispos.jar`.

Se recomienda probar primero en **una sola terminal** antes de copiarlo a las demás.

## Restaurar un respaldo

Descomprimir el `.zip` y cargar el `.sql` con el cliente de MySQL:

```
mysql -u USUARIO -p NOMBRE_BASE < chromis-AAAAMMDD-HHMMSS.sql
```

Esto **reemplaza** el contenido de esa base: hacerlo con Chromis cerrado en
todas las terminales.
