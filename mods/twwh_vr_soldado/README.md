# VR Soldado — mod para Total War: WARHAMMER II / III

Te pone en la piel de un soldado de una de tus unidades, con la cámara a la altura de los ojos y pensado para jugarse en realidad virtual. Si tu soldado muere, saltas a otro soldado de la **misma unidad** o de **otra unidad**.

## Funciones

| Función | Detalle |
|---|---|
| Vista en primera persona | La cámara va anclada a la posición y orientación de la unidad, a la altura de los ojos (1,8 m). Para unidades pequeñas (caballería, monstruos, personajes) la altura se escala. |
| Muerte realista | Cada baja de la unidad tiene una probabilidad de 1/N de ser tu soldado, donde N es el número de hombres vivos. Si la unidad entera cae, tú caes con ella. |
| Salto a la misma unidad | Te pasa a otro soldado vivo de la misma unidad, con una nueva posición dentro de la formación. |
| Salto a otra unidad | Te lleva a la unidad siguiente o anterior. Puede incluir ejércitos aliados. |
| Salto automático | Si no eliges en 8 s, saltas solo: primero a tu misma unidad y, si ya no queda nadie, a la siguiente. |
| Control | Das órdenes a tu unidad como siempre (clic derecho, formación, carga...). Tu soldado se mueve con ella. |

## Controles (por defecto)

Se usan los atajos de los marcadores de cámara del juego. Puedes reasignarlos en *Opciones → Controles* o cambiar los IDs en `CFG.keys` dentro del script.

| Acción | ID del atajo |
|---|---|
| Activar / desactivar | `camera_bookmark_view0` |
| Otro soldado de la misma unidad | `camera_bookmark_view1` |
| Siguiente unidad | `camera_bookmark_view2` |
| Unidad anterior | `camera_bookmark_view3` |

La encarnación se activa sola cuando termina el despliegue.

## Instalación

1. Instala **RPFM** (Rusted PackFile Manager).
2. Crea un pack nuevo: *PackFile → New PackFile*, de tipo `mod`.
3. Añade la carpeta `script/` de este directorio respetando la ruta:
   `script/battle/mod/vr_soldado.lua`
4. Guárdalo como `vr_soldado.pack` en la carpeta `data/` del juego, o súbelo a Steam Workshop.
5. Actívalo en el lanzador.

Los scripts que están en `script/battle/mod/` se cargan automáticamente en cada batalla. El mod no modifica ningún archivo del juego, así que es compatible con casi todo.

## Realidad virtual

Total War no tiene soporte VR nativo y la API de scripting no permite añadir renderizado estereoscópico. Este mod se encarga de la cámara y la lógica de juego. La imagen VR la genera un inyector externo:

- **VorpX**: perfil DX11 genérico con *Geometry 3D* o *Z-Normal*, y *Head tracking* en modo "Mouse". Pon `CFG.camera_smooth_s` bajo (0,05–0,1) para reducir el mareo.
- **ReShade + SuperDepth3D** y **Virtual Desktop / Bigscreen**: 3D lado a lado más ligero, sin seguimiento de cabeza.

Ajustes recomendados en el juego: interfaz al mínimo (tecla `K` para ocultarla), FOV entre 75 y 90, y 72–90 FPS estables.

## Configuración (`CFG` al principio del script)

- `eye_height`: altura de los ojos.
- `camera_back_offset`: si es mayor que 0, la vista pasa a tercera persona por detrás del soldado.
- `camera_fov`, `update_ms`, `camera_smooth_s`: comodidad en VR.
- `auto_switch_s`: segundos que pasan antes del salto automático.
- `include_allies`: permite saltar a unidades aliadas.
- `bearing_sign`: ponlo a `-1` si la cámara mira hacia atrás (depende de la versión del juego).

## Limitaciones conocidas

- La API de Lua de Creative Assembly **no da la posición de cada soldado**, solo la de la unidad. Por eso tu soldado se representa con la posición de la unidad más un desplazamiento fijo dentro de la formación. Para seguir una entidad concreta y moverla con el mando VR haría falta ingeniería inversa del motor (inyectar una DLL). Eso queda fuera de lo que permite el modding oficial.
- No puedes mover tu soldado de forma independiente: controlas la unidad entera.
- Los mensajes salen en el log de scripts (`script_log` con el *script debugger* activado). Para mostrarlos en pantalla hay que añadir claves de localización en una tabla `.loc`.
- Los IDs de los atajos y algunas funciones (`change_height_range`, `bearing`) pueden variar entre WH2 y WH3. Todas las llamadas van protegidas con `pcall`, así que un fallo no rompe la batalla.
