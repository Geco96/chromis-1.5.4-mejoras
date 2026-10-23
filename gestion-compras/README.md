# Compras y Pagos por negocio / sucursal

Una herramienta sencilla para saber, cada mes, **qué compra le corresponde a cada negocio o sucursal**, **qué ya se pagó** y **qué falta por pagar**, sin cuadrar números a mano.

Son dos partes que trabajan juntas:

- **`index.html` – en la computadora**: el cuadre del mes, importación de estados de cuenta, pagos y reportes. Se abre con doble clic en Chrome, Edge o Firefox.
- **`movil/` – en el celular**: capturas la compra en el momento, leyendo el monto de la captura de pantalla del comprobante. Ver [movil/README.md](movil/README.md).

## Cómo se usa

1. **Negocios y reglas**: da de alta tus negocios, sus sucursales y las cuentas/tarjetas con las que pagas.
2. **Importar / conciliar**: sube tu archivo (Excel `.xlsx` o `.csv`).
   - **Estado de cuenta** (banco o tarjeta): cada cargo se registra como una compra *ya pagada* con esa cuenta. Si el cargo coincide con una compra que ya tenías por pagar (mismo importe, fecha cercana), se propone ligarlo como **pago** de esa compra. Los abonos y los movimientos ya importados se ignoran solos.
   - **Compras capturadas en el celular**: el archivo `capturas-celular-….csv` que manda la app. Se reconoce solo; cada compra entra pagada, con su sucursal y cuenta. Las que ya importaste se ignoran.
   - **Listado de compras / facturas por pagar**: cada renglón se vuelve una compra *pendiente*. Si el archivo trae una columna de sucursal, se asigna automáticamente.
   - La herramienta adivina qué columna es cuál (fecha, descripción, cargo, abono…). Puedes corregirlo antes de confirmar.
   - Antes de registrar ves un **cuadre**: total de cargos del archivo contra lo que se va a registrar y lo ignorado.
3. **Reglas**: en la vista previa, elige la sucursal de un movimiento y presiona **Recordar**. La próxima vez, todo lo que contenga ese texto (p. ej. "COSTCO") se asignará solo a esa sucursal y proveedor.
4. **Compras**: filtra por mes, sucursal o estado (por pagar, vencidas, sin asignar). Una compra se puede **repartir entre varias sucursales** (p. ej. una factura de Costco: $4,000 para Centro y $2,890 para la cafetería). Para asignar muchas de golpe, selecciónalas y usa "Asignar".
5. **Pagos**: un pago puede cubrir varias compras y una compra puede pagarse en partes. El total por cuenta del mes sirve para cuadrar contra el estado de cuenta.
6. **Resumen**: comprado, pagado y pendiente del mes por negocio y sucursal, los próximos vencimientos y el dinero que salió sin estar ligado a ninguna compra. Se puede imprimir o exportar a CSV.

Para conocerla sin capturar nada, en la primera pantalla presiona **"Probar con datos de ejemplo"** y luego importa los archivos de la carpeta `ejemplos/`.

## ¿Dónde se guardan los datos?

En el navegador de la computadora donde la abres (almacenamiento local). Eso significa:

- **Haz respaldo** con frecuencia en la pestaña *Respaldo* (descarga un `.json`). La app te recuerda si llevas más de una semana sin hacerlo.
- Para pasar los datos a otra computadora: descarga el respaldo y cárgalo allá.
- Si borras los datos de navegación del navegador, se borran también los de la herramienta (por eso el respaldo).

## Formatos aceptados

- CSV separado por coma, punto y coma, tabulador o `|`; en UTF-8 o Windows/Latin-1.
- Excel `.xlsx`/`.xls` (se lee la primera hoja). Para leer Excel la página descarga un lector la primera vez, así que necesita internet; sin internet, guarda el archivo como CSV.
- Fechas `dd/mm/aaaa`, `aaaa-mm-dd`, `15/ENE/2026`, `15 ene 2026` o fecha de Excel. Puedes cambiar a `mm/dd/aaaa`.
- Importes `1,234.56`, `1.234,56`, `$ 1,234.56`, `-1234.56` o `(1234.56)`.
- Los renglones de títulos, saldos o totales del estado de cuenta (sin fecha o sin importe) se omiten.

## Notas técnicas

- Todo es HTML + JavaScript sin dependencias (salvo el lector de Excel, que se carga bajo demanda desde cdnjs).
- Los importes se guardan en centavos (enteros) para que los totales cuadren exactamente.
- Cada movimiento importado deja una "huella" (cuenta + fecha + importe + descripción) para no registrarlo dos veces si vuelves a subir el mismo archivo o uno que se traslapa.
