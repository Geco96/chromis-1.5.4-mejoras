"""
Instala en la base de Chromis los recursos de la versión Enterprise:
  - script.TipoPedido         (botón Mostrador / Entrega en el ticket)
  - script.TipoPedido.Total   (pregunta el tipo al cobrar si no se marcó)
  - Ticket.Buttons            (se le agregan el botón y el evento)
  - Printer.Ticket            (imprime "PEDIDO PARA ENTREGA" y las indicaciones)

Uso (con Chromis cerrado):
    set CHROMIS_DB_URL=mysql://usuario:clave@localhost:3306/chromispos
    python instalar.py

Se puede ejecutar varias veces: no duplica nada. Antes haz respaldo de la base.
"""
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(AQUI, "..", "web"))
import chromis_sync  # noqa: E402

MARCA = "ENTERPRISE: Tipo de pedido"


def leer(nombre):
    with open(os.path.join(AQUI, "recursos", nombre), encoding="utf-8") as f:
        return f.read()


def main():
    con = chromis_sync.conectar()
    try:
        cur = con.cursor()
        chromis_sync.instalar_recurso(cur, "script.TipoPedido", leer("script.TipoPedido.txt"))
        chromis_sync.instalar_recurso(cur, "script.TipoPedido.Total", leer("script.TipoPedido.Total.txt"))
        print("Scripts instalados.")

        if chromis_sync.agregar_a_recurso(cur, "Ticket.Buttons", leer("fragmento.Ticket.Buttons.xml"),
                                          "</configuration>", MARCA):
            print("Botón y evento agregados a Ticket.Buttons.")
        else:
            print("Ticket.Buttons ya tenía el botón.")

        fragmento = "        <!-- " + MARCA + " -->\n" + leer("fragmento.Printer.Ticket.xml")
        if chromis_sync.agregar_a_recurso(cur, "Printer.Ticket", fragmento,
                                          '<line><text align="left" length="15">Date:', MARCA):
            print("Aviso de entrega agregado a Printer.Ticket.")
        else:
            print("Printer.Ticket ya tenía el aviso.")
        con.commit()
    except Exception:
        con.rollback()
        raise
    finally:
        con.close()
    print("Listo. Abre Chromis: verás el botón 'Mostrador / Entrega' en el ticket.")


if __name__ == "__main__":
    main()
