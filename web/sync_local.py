"""
Sincronizador para la PC del punto de venta.

  web -> POS: clientes de la agenda pasan a customers de Chromis.
  POS -> web: tickets marcados "Entrega" quedan como Pedido Activo del cliente
              y se registran en la tabla "entregas" de Supabase.

La web corre en Render y no puede ver la base de Chromis de la red local,
así que este script se ejecuta en la PC del POS: lee los clientes de
Supabase y los escribe en Chromis.

Variables de entorno: SUPABASE_URL, SUPABASE_KEY, CHROMIS_DB_URL
Opcional: SYNC_INTERVALO_MIN (por defecto 5). Usa --una-vez para una sola pasada.
"""
import os
import sys
import time
from datetime import datetime

from supabase import create_client

import chromis_sync

DIAS_ENTREGAS = int(os.environ.get("SYNC_DIAS_ENTREGAS", "7"))


def enviar_clientes(supabase):
    clientes = supabase.table("clientes").select("*").execute().data
    # Los clientes que nacieron en el POS ya existen en Chromis; no se reenvían.
    clientes = [c for c in clientes if not c.get("chromis_id")]
    resumen = chromis_sync.sincronizar_clientes(clientes)
    print(f"[{datetime.now():%Y-%m-%d %H:%M}] clientes -> POS  nuevos: {resumen['insertados']}, "
          f"actualizados: {resumen['actualizados']}, errores: {len(resumen['errores'])}")
    for e in resumen["errores"]:
        print(f"   cliente #{e['id_cliente']}: {e['error']}")


def ubicar_cliente(supabase, entrega):
    """Regresa el id_cliente de la web para el cliente del ticket; lo crea si no existe."""
    id_web = chromis_sync.id_cliente_web(entrega["chromis_customer"])
    if id_web:
        return id_web
    encontrado = supabase.table("clientes").select("id_cliente") \
        .eq("chromis_id", entrega["chromis_customer"]).execute().data
    if encontrado:
        return str(encontrado[0]["id_cliente"])
    # Cliente creado en el POS: se da de alta en la web sin coordenadas para que lo ubiquen en el mapa.
    nuevo = supabase.table("clientes").insert({
        "nombre_comercial": entrega["cliente_nombre"],
        "telefono": entrega["cliente_telefono"],
        "direccion_completa": entrega["cliente_direccion"],
        "estado_logistico": "cautivo",
        "estado_secundario": "ninguno",
        "asignado_a": "sin_asignar",
        "chromis_id": entrega["chromis_customer"],
    }).execute().data
    return str(nuevo[0]["id_cliente"])


def recibir_entregas(supabase):
    entregas = chromis_sync.leer_entregas(DIAS_ENTREGAS)
    if not entregas:
        return
    ids = [e["id_ticket"] for e in entregas]
    ya = {r["id_ticket"] for r in supabase.table("entregas").select("id_ticket").in_("id_ticket", ids).execute().data}
    nuevas = 0
    for e in entregas:
        if e["id_ticket"] in ya:
            continue
        id_cliente = ubicar_cliente(supabase, e)
        supabase.table("entregas").insert({
            "id_ticket": e["id_ticket"], "numero_ticket": e["numero_ticket"], "fecha": e["fecha"],
            "id_cliente": id_cliente, "total": e["total"], "notas": e["notas"],
        }).execute()
        # "Pedido Activo" es lo que usa la web para armar la Ruta Óptima.
        supabase.table("clientes").update({"estado_secundario": "pedido_activo"}) \
            .eq("id_cliente", id_cliente).execute()
        nuevas += 1
    print(f"[{datetime.now():%Y-%m-%d %H:%M}] entregas POS -> web  nuevas: {nuevas}")


def una_pasada(supabase):
    enviar_clientes(supabase)
    recibir_entregas(supabase)


def main():
    url, key = os.environ.get("SUPABASE_URL"), os.environ.get("SUPABASE_KEY")
    if not url or not key:
        sys.exit("Faltan SUPABASE_URL y SUPABASE_KEY.")
    supabase = create_client(url, key)

    if "--una-vez" in sys.argv:
        una_pasada(supabase)
        return

    intervalo = int(os.environ.get("SYNC_INTERVALO_MIN", "5")) * 60
    while True:
        try:
            una_pasada(supabase)
        except Exception as e:
            print(f"[{datetime.now():%Y-%m-%d %H:%M}] error: {e}")
        time.sleep(intervalo)


if __name__ == "__main__":
    main()
