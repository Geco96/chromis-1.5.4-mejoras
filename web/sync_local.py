"""
Sincronizador para la PC del punto de venta.

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


def una_pasada(supabase):
    clientes = supabase.table("clientes").select("*").execute().data
    resumen = chromis_sync.sincronizar_clientes(clientes)
    print(f"[{datetime.now():%Y-%m-%d %H:%M}] nuevos: {resumen['insertados']}, "
          f"actualizados: {resumen['actualizados']}, errores: {len(resumen['errores'])}")
    for e in resumen["errores"]:
        print(f"   cliente #{e['id_cliente']}: {e['error']}")


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
