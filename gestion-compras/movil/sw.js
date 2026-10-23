// Service worker de la app de captura: funciona sin internet y recibe imágenes desde "Compartir".
const VERSION = 'compras-movil-v1';
const SHELL = ['./', './index.html', './manifest.webmanifest', './icon.svg', './icon-192.png', './icon-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(ks => Promise.all(ks.filter(k => k !== VERSION && k !== 'compartido').map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (e.request.method === 'POST' && url.pathname.endsWith('/compartir')) {
    e.respondWith(recibirCompartido(e.request));
    return;
  }
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  // El lector OCR no cambia: primero caché. La app: primero red, para recibir actualizaciones.
  e.respondWith(url.pathname.includes('/vendor/') ? primeroCache(e.request) : primeroRed(e.request));
});

async function primeroCache(req) {
  const hit = await caches.match(req);
  if (hit) return hit;
  const res = await fetch(req);
  if (res.ok) (await caches.open(VERSION)).put(req, res.clone());
  return res;
}

async function primeroRed(req) {
  try {
    const res = await fetch(req);
    if (res.ok) (await caches.open(VERSION)).put(req, res.clone());
    return res;
  } catch (err) {
    const hit = await caches.match(req, { ignoreSearch: true });
    if (hit) return hit;
    throw err;
  }
}

// Android: la captura compartida llega como POST; se guarda y se abre la app para leerla.
async function recibirCompartido(req) {
  try {
    const fd = await req.formData();
    const f = fd.getAll('imagen').find(x => x && x.type && x.type.startsWith('image/'));
    if (f) await (await caches.open('compartido')).put('ultima', new Response(f, { headers: { 'Content-Type': f.type } }));
  } catch (err) { /* se abre la app aunque no llegue imagen */ }
  return Response.redirect('./?compartido=1', 303);
}
