"""Prueba de la página del mapa de entregas en Chromium, con Google Maps simulado
(tests/gmaps_stub.js): dibujar y editar cuadrantes y ubicar clientes.

    java -cp dist/admin.jar tests/MapServerTest.java --esperar &     # deja el servidor abierto
    python tests/mapa_navegador.py mapa-url.txt                       # requiere playwright
    rm mapa-url.txt                                                   # termina y limpia
"""
import sys, json, time
from playwright.sync_api import sync_playwright
STUB = open(__import__('os').path.join(__import__('os').path.dirname(__file__), 'gmaps_stub.js')).read()
url, run = open(sys.argv[1]).read().split()
fails = 0
def check(what, got, exp):
    global fails
    ok = got == exp
    print(('OK   ' if ok else 'FAIL ') + what + ' -> ' + str(got))
    if not ok: fails += 1
with sync_playwright() as p:
    import glob
    exe = (glob.glob('/opt/pw-browsers/chromium-*/chrome-linux*/chrome') + ['/opt/pw-browsers/chromium'])[0]
    b = p.chromium.launch(executable_path=exe)
    pg = b.new_page(viewport={'width': 1366, 'height': 800})
    errors = []
    pg.on('pageerror', lambda e: errors.append(str(e)))
    dialogs = []
    def on_dialog(d):
        dialogs.append(d.message); d.accept()
    pg.on('dialog', on_dialog)
    loaded = []
    def gm(r):
        loaded.append(r.request.url)
        r.fulfill(status=200, content_type='application/javascript', body=STUB)
    pg.route('**/maps.googleapis.com/**', gm)
    pg.goto(url)
    pg.wait_for_function('window.__maps && window.__maps.map')
    check('carga Google Maps con la clave', 'key=CLAVE-DE-PRUEBA' in loaded[0], True)
    check('papel', pg.inner_text('#quien'), 'Administrator · administrador')
    tabs = pg.inner_text('#pestanas')
    n = pg.evaluate("Mapa._st.datos.entregas.length")
    check('pestañas', f'Entregas ({n})' in tabs and 'Cuadrantes (1)' in tabs, True)
    check('ningún cuadrante de antes', 'Cuadrantes (1)' in tabs, True)
    check('marcadores de entregas', pg.evaluate("window.__maps.markers.filter(m => m.o.zIndex === 100).length"),
          pg.evaluate("Mapa._st.datos.entregas.filter(e => e.lat !== null).length"))
    check('cuadrante pintado', pg.evaluate("window.__maps.polygons.length"), 1)
    # Ver una entrega en el mapa
    pg.click('.tarjeta:has-text("Mapa Centro ' + run + '")')
    check('centra en la entrega', pg.evaluate("window.__maps.map.z"), 16)
    # Dibujar un cuadrante
    pg.click('text=Cuadrantes')
    pg.click('text=＋ Nuevo cuadrante')
    for lat, lng in [(19.40, -99.20), (19.41, -99.20), (19.41, -99.19)]:
        pg.evaluate(f"window.__maps.map._fire('click', {{latLng: new google.maps.LatLng({lat}, {lng})}})")
    check('3 esquinas', 'esquinas (3)' in pg.inner_text('#lista'), True)
    pg.click('text=Guardar')
    check('sin nombre no guarda', dialogs[-1], 'Ponle nombre al cuadrante.')
    pg.fill('#nombre-c', 'Poniente')
    pg.click('text=Guardar')
    pg.wait_for_function("document.getElementById('pestanas').innerText.includes('Cuadrantes (2)')")
    check('cuadrante nuevo en la lista', 'Poniente' in pg.inner_text('#lista'), True)
    check('2 cuadrantes en el mapa', pg.evaluate("window.__maps.polygons.length"), 2)
    # Editar la forma
    pg.click('.tarjeta:has-text("Poniente") >> text=Editar')
    pg.evaluate("Mapa._st.editando.pol.setPath([{lat:19.40,lng:-99.20},{lat:19.42,lng:-99.20},{lat:19.42,lng:-99.18},{lat:19.40,lng:-99.18}])")
    pg.fill('#nombre-e', 'Poniente Grande')
    pg.click('#lista >> text=Guardar')
    pg.wait_for_function("document.getElementById('lista').innerText.includes('Poniente Grande')")
    check('forma guardada', pg.evaluate("Mapa._st.datos.cuadrantes.find(c => c.nombre === 'Poniente Grande').poligono.length"), 4)
    # Ubicar un cliente sin pin: el que se ubicó en la prueba Java ya tiene pin; uso uno de la lista
    pg.click('#pestanas >> text=Clientes')
    pg.uncheck('text=Solo los que no tienen ubicación')
    pg.fill('#buscar', 'Mapa Centro ' + run)
    check('búsqueda', pg.locator('#lista .tarjeta').count(), 1)
    pg.click('#lista >> text=Mover pin')
    pg.evaluate("window.__maps.map._fire('click', {latLng: new google.maps.LatLng(19.4311, -99.1311)})")
    pg.select_option('#estado-pin', 'pendiente_visita')
    pg.click('text=Guardar ubicación')
    pg.wait_for_function("document.getElementById('mensaje').innerText.includes('Ubicación guardada')")
    c = pg.evaluate("Mapa._st.datos.clientes.find(c => c.nombre === 'Papelería Mapa Centro ' + '" + run + "')")
    check('pin movido', (c['lat'], c['lng'], c['estado']), (19.4311, -99.1311, 'pendiente_visita'))
    # Buscar dirección sin resultados (el simulador no encuentra nada)
    pg.click('#lista >> text=Mover pin')
    pg.click('text=Buscar >> nth=0')
    check('dirección no encontrada avisa', 'No se encontró' in dialogs[-1], True)
    pg.click('text=Cancelar')
    check('sin errores de JavaScript', errors, [])
    pg.screenshot(path='mapa.png')
    b.close()
print('\nTODO BIEN' if fails == 0 else f'\nFALLAS: {fails}')
sys.exit(1 if fails else 0)
