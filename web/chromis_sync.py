"""
Puente entre la agenda web (Supabase, tabla "clientes") y la tabla
"customers" de Chromis POS 1.5.4.

Configuración por variable de entorno:
    CHROMIS_DB_URL = mysql://usuario:clave@host:3306/chromispos
                     postgresql://usuario:clave@host:5432/chromispos

Cada cliente de la web se escribe en Chromis con un id fijo
("web-<id_cliente>"), así que sincronizar varias veces actualiza el mismo
registro en lugar de duplicarlo. Los clientes creados directamente en el POS
no se tocan.
"""
import os
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
from urllib.parse import urlparse, unquote

PREFIJO_ID = "web-"
PREFIJO_TAXID = "WEB"


def _cortar(valor, largo):
    if valor is None:
        return None
    return str(valor)[:largo]


def conectar():
    url = os.environ.get("CHROMIS_DB_URL")
    if not url:
        raise RuntimeError("Falta la variable CHROMIS_DB_URL.")
    p = urlparse(url)
    usuario, clave = unquote(p.username or ""), unquote(p.password or "")
    base = p.path.lstrip("/")

    if p.scheme in ("mysql", "mariadb"):
        import pymysql
        return pymysql.connect(host=p.hostname, port=p.port or 3306, user=usuario,
                               password=clave, database=base, charset="utf8mb4")
    if p.scheme in ("postgres", "postgresql"):
        import psycopg2
        return psycopg2.connect(host=p.hostname, port=p.port or 5432, user=usuario,
                                password=clave, dbname=base)
    raise RuntimeError(f"Motor no soportado: {p.scheme}. Usa mysql:// o postgresql://")


def obtener_siteguid(cursor):
    cursor.execute("SELECT guid FROM siteguid")
    fila = cursor.fetchone()
    if not fila:
        raise RuntimeError("La base de Chromis no tiene siteguid. ¿Se creó con dbcreator?")
    return fila[0]


def cliente_a_customer(cliente):
    """Traduce un registro de Supabase a las columnas de Chromis."""
    id_web = cliente["id_cliente"]
    lat, lng = cliente.get("latitud"), cliente.get("longitud")
    gps = f"GPS {lat},{lng}" if lat is not None and lng is not None else None

    notas = cliente.get("notas_internas") or ""
    asignado = cliente.get("asignado_a")
    if asignado and asignado != "sin_asignar":
        notas = f"[Ruta: {asignado}] {notas}".strip()

    return {
        "id": f"{PREFIJO_ID}{id_web}",
        "taxid": f"{PREFIJO_TAXID}{id_web}",
        "name": _cortar(cliente.get("nombre_comercial") or f"Cliente {id_web}", 50),
        "phone": _cortar(cliente.get("telefono"), 20),
        "address": _cortar(cliente.get("direccion_completa"), 100),
        "address2": _cortar(gps, 100),
        "notes": _cortar(notas, 255) or None,
        "curdate": datetime.now(),
    }


def sincronizar_clientes(clientes):
    """Inserta o actualiza los clientes web en Chromis. Devuelve un resumen."""
    resumen = {"insertados": 0, "actualizados": 0, "errores": []}
    con = conectar()
    try:
        cur = con.cursor()
        siteguid = obtener_siteguid(cur)
        for cliente in clientes:
            try:
                c = cliente_a_customer(cliente)
                cur.execute("SELECT 1 FROM customers WHERE id = %s", (c["id"],))
                if cur.fetchone():
                    cur.execute(
                        "UPDATE customers SET name=%s, phone=%s, address=%s, address2=%s, "
                        "notes=%s, curdate=%s, sflag=%s WHERE id=%s",
                        (c["name"], c["phone"], c["address"], c["address2"], c["notes"],
                         c["curdate"], True, c["id"]))
                    resumen["actualizados"] += 1
                else:
                    cur.execute(
                        "INSERT INTO customers (id, customertype, taxid, name, maxdebt, phone, "
                        "address, address2, notes, active, curdate, curdebt, discount, "
                        "loyaltyenabled, marketable, sflag, siteguid) "
                        "VALUES (%s,'account',%s,%s,0,%s,%s,%s,%s,%s,%s,0,0,%s,%s,%s,%s)",
                        (c["id"], c["taxid"], c["name"], c["phone"], c["address"],
                         c["address2"], c["notes"], True, c["curdate"], False, False,
                         True, siteguid))
                    resumen["insertados"] += 1
                con.commit()
            except Exception as e:
                con.rollback()
                resumen["errores"].append({"id_cliente": cliente.get("id_cliente"), "error": str(e)})
    finally:
        con.close()
    return resumen


def estado_conexion():
    """Prueba la conexión y cuenta cuántos clientes web existen en Chromis."""
    con = conectar()
    try:
        cur = con.cursor()
        cur.execute("SELECT COUNT(*) FROM customers")
        total = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM customers WHERE id LIKE %s", (PREFIJO_ID + "%",))
        web = cur.fetchone()[0]
        return {"conectado": True, "clientes_pos": total, "clientes_desde_web": web}
    finally:
        con.close()


# --- PEDIDOS DE ENTREGA (Chromis -> web) ---

def _leer_propiedades(blob):
    """receipts.attributes es un java.util.Properties guardado con storeToXML."""
    if not blob:
        return {}
    try:
        raiz = ET.fromstring(bytes(blob))
        return {e.get("key"): (e.text or "") for e in raiz.iter("entry")}
    except ET.ParseError:
        return {}


def leer_entregas(dias=7):
    """Tickets de venta de los últimos días marcados como "entrega"."""
    desde = datetime.now() - timedelta(days=dias)
    con = conectar()
    try:
        cur = con.cursor()
        cur.execute(
            "SELECT r.id, r.datenew, r.attributes, t.ticketid, t.customer, "
            "c.name, c.phone, c.address, c.address2, c.city, "
            "(SELECT SUM(p.total) FROM payments p WHERE p.receipt = r.id) "
            "FROM receipts r JOIN tickets t ON t.id = r.id "
            "LEFT JOIN customers c ON c.id = t.customer "
            "WHERE t.tickettype = 0 AND r.datenew >= %s", (desde,))
        entregas = []
        for (id_ticket, fecha, attrs, numero, id_customer, nombre, tel,
             dir1, dir2, ciudad, total) in cur.fetchall():
            props = _leer_propiedades(attrs)
            if props.get("tipo_pedido") != "entrega" or not id_customer:
                continue
            direccion = ", ".join(x for x in (dir1, ciudad) if x)
            if dir2 and not dir2.startswith("GPS "):
                direccion = ", ".join(x for x in (dir1, dir2, ciudad) if x)
            entregas.append({
                "id_ticket": id_ticket,
                "numero_ticket": numero,
                "fecha": fecha.isoformat(),
                "total": float(total) if total is not None else None,
                "notas": props.get("entrega_notas") or None,
                "chromis_customer": id_customer,
                "cliente_nombre": nombre,
                "cliente_telefono": tel,
                "cliente_direccion": direccion or None,
            })
        return entregas
    finally:
        con.close()


def id_cliente_web(id_customer):
    """Si el cliente de Chromis vino de la web ("web-12"), regresa 12 como texto."""
    if id_customer and id_customer.startswith(PREFIJO_ID):
        return id_customer[len(PREFIJO_ID):]
    return None


# --- INSTALACIÓN DE LOS RECURSOS ENTERPRISE EN CHROMIS ---

def instalar_recurso(cur, nombre, contenido, extension="txt"):
    cur.execute("SELECT id FROM resources WHERE name = %s", (nombre,))
    fila = cur.fetchone()
    datos = contenido.encode("utf-8")
    if fila:
        cur.execute("UPDATE resources SET content = %s WHERE id = %s", (datos, fila[0]))
    else:
        import uuid
        cur.execute("INSERT INTO resources (id, name, restype, content, extension) "
                    "VALUES (%s, %s, 0, %s, %s)", (str(uuid.uuid4()), nombre, datos, extension))


def agregar_a_recurso(cur, nombre, fragmento, antes_de, marca):
    """Inserta un fragmento en un recurso existente (una sola vez, identificado por 'marca')."""
    cur.execute("SELECT id, content FROM resources WHERE name = %s", (nombre,))
    fila = cur.fetchone()
    if not fila:
        raise RuntimeError(f"No existe el recurso {nombre} en Chromis.")
    texto = bytes(fila[1]).decode("utf-8")
    if marca in texto:
        return False
    if antes_de not in texto:
        raise RuntimeError(f"No encontré '{antes_de}' en {nombre}; agrégalo a mano.")
    i = texto.rindex(antes_de) if antes_de.startswith("</") else texto.index(antes_de)
    texto = texto[:i] + fragmento + texto[i:]
    cur.execute("UPDATE resources SET content = %s WHERE id = %s", (texto.encode("utf-8"), fila[0]))
    return True
