import os
from flask import Flask, request, jsonify, send_from_directory, session, redirect
from supabase import create_client, Client
import chromis_sync

app = Flask(__name__, static_folder='static')
app.secret_key = os.environ.get("FLASK_SECRET_KEY", "clave_secreta_markhoor_2026")

SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")

if SUPABASE_URL and SUPABASE_KEY:
    supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)
else:
    print("Advertencia: Faltan las credenciales de Supabase.")
    supabase = None

# --- RUTAS DE AUTENTICACIÓN ---

@app.route('/login', methods=['GET'])
def vista_login():
    return send_from_directory('static', 'login.html')

@app.route('/api/login', methods=['POST'])
def procesar_login():
    if not supabase: return jsonify({"error": "Error de BD"}), 500
        
    datos = request.json
    email = datos.get('email')
    password = datos.get('password')
    
    try:
        # 1. Autenticar con Supabase
        respuesta = supabase.auth.sign_in_with_password({"email": email, "password": password})
        usuario_id = respuesta.user.id
        
        # 2. Consultar el perfil y verificar si está activo
        perfil = supabase.table("perfiles").select("rol, activo").eq("id_usuario", usuario_id).execute()
        
        if len(perfil.data) > 0:
            es_activo = perfil.data[0].get('activo', True) # Si es null, asumimos True por defecto
            
            # NUEVO: Bloquear el acceso si la cuenta fue desactivada
            if not es_activo:
                return jsonify({"error": "Cuenta desactivada. Contacta al administrador."}), 403
                
            session['rol'] = perfil.data[0]['rol']
        else:
            session['rol'] = 'empleado'
            
        # 3. Establecer la sesión en Flask solo si pasó la validación
        session['usuario_id'] = usuario_id
        session['correo'] = email 
        
        return jsonify({"mensaje": "Bienvenido"}), 200
    except Exception as e:
        return jsonify({"error": "Credenciales inválidas"}), 401

@app.route('/api/logout', methods=['POST'])
def logout():
    session.pop('usuario_id', None)
    session.pop('rol', None)
    session.pop('correo', None)
    return jsonify({"mensaje": "Sesión cerrada"}), 200

@app.route('/api/sesion', methods=['GET'])
def obtener_sesion():
    if 'usuario_id' not in session:
        return jsonify({"error": "No autorizado"}), 401
    return jsonify({
        "rol": session.get('rol', 'empleado'),
        "correo": session.get('correo', '')
    }), 200

@app.route('/api/usuarios', methods=['POST'])
def crear_empleado():
    if 'usuario_id' not in session or session.get('rol') != 'admin':
        return jsonify({"error": "No tienes permisos de Administrador"}), 403
        
    datos = request.json
    email = datos.get('email')
    password = datos.get('password')
    rol_solicitado = datos.get('rol', 'empleado')
    
    if rol_solicitado not in ['empleado', 'despachador']:
        rol_solicitado = 'empleado'
    
    if not email or not password or len(password) < 6:
        return jsonify({"error": "Datos inválidos."}), 400
        
    try:
        cliente_admin_limpio = create_client(SUPABASE_URL, SUPABASE_KEY)
        respuesta_auth = cliente_admin_limpio.auth.admin.create_user({
            "email": email, "password": password, "email_confirm": True
        })
        
        nuevo_id = respuesta_auth.user.id
        # NUEVO: Aseguramos que se cree con activo = True por defecto
        cliente_admin_limpio.table("perfiles").insert({
            "id_usuario": nuevo_id, "correo": email, "rol": rol_solicitado, "activo": True
        }).execute()
        
        rol_texto = "Repartidor" if rol_solicitado == "empleado" else "Centro de Distribución"
        return jsonify({"mensaje": f"¡Cuenta de {rol_texto} creada exitosamente!\n\nUsuario: {email}"}), 201
    except Exception as e:
        return jsonify({"error": str(e)}), 400

# NUEVO: Endpoint para leer todos los usuarios (activos e inactivos) para el panel de Admin
@app.route('/api/usuarios_admin', methods=['GET'])
def obtener_usuarios_admin():
    if 'usuario_id' not in session or session.get('rol') != 'admin':
        return jsonify({"error": "No tienes permisos de Administrador"}), 403
    try:
        respuesta = supabase.table('perfiles').select('correo, rol, activo').execute()
        return jsonify(respuesta.data), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# NUEVO: Endpoint para cambiar el estatus (Activar / Desactivar)
@app.route('/api/usuarios/estado', methods=['PUT'])
def actualizar_estado_usuario():
    if 'usuario_id' not in session or session.get('rol') != 'admin':
        return jsonify({"error": "No tienes permisos de Administrador"}), 403
    try:
        datos = request.json
        correo = datos.get('correo')
        nuevo_estado = datos.get('activo')
        
        if not correo:
            return jsonify({"error": "Falta el correo"}), 400
            
        respuesta = supabase.table('perfiles').update({'activo': nuevo_estado}).eq('correo', correo).execute()
        return jsonify({"mensaje": "Estado actualizado correctamente"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


# --- RUTAS DE LOGÍSTICA ---

@app.route('/api/repartidores', methods=['GET'])
def obtener_repartidores():
    if 'usuario_id' not in session: return jsonify({"error": "No autorizado"}), 401
    try:
        # NUEVO: Filtramos para que solo devuelva a los empleados que están activos (activo = True)
        respuesta = supabase.table("perfiles").select("correo").eq("rol", "empleado").eq("activo", True).execute()
        return jsonify(respuesta.data), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 400


# --- RUTAS PROTEGIDAS (MAPA Y CLIENTES) ---

@app.route('/')
def index():
    if 'usuario_id' not in session: return redirect('/login')
    with open(os.path.join(app.static_folder, 'index.html'), encoding='utf-8') as f:
        html = f.read()
    return html.replace('{{GOOGLE_MAPS_API_KEY}}', os.environ.get('GOOGLE_MAPS_API_KEY', ''))

@app.route('/api/clientes', methods=['POST'])
def crear_cliente():
    if 'usuario_id' not in session: return jsonify({"error": "No autorizado"}), 401
    datos = request.json
    
    rol_actual = session.get('rol', 'empleado')
    asignado = datos.get("asignado_a", "sin_asignar")
    if rol_actual == 'empleado':
        asignado = session.get('correo') 
        
    nuevo_cliente = {
        "nombre_comercial": datos.get("nombre_comercial"),
        "telefono": datos.get("telefono"),
        "direccion_completa": datos.get("direccion_completa"),
        "latitud": datos.get("latitud"),
        "longitud": datos.get("longitud"),
        "estado_logistico": datos.get("estado_logistico", "nuevo"),
        "estado_secundario": datos.get("estado_secundario", "ninguno"),
        "notas_internas": datos.get("notas_internas"),
        "asignado_a": asignado
    }
    try:
        respuesta = supabase.table("clientes").insert(nuevo_cliente).execute()
        return jsonify({"mensaje": "Cliente guardado", "datos": respuesta.data}), 201
    except Exception as e:
        return jsonify({"error": str(e)}), 400

@app.route('/api/clientes', methods=['GET'])
def obtener_clientes():
    if 'usuario_id' not in session: return jsonify({"error": "No autorizado"}), 401
    try:
        respuesta = supabase.table("clientes").select("*").execute()
        return jsonify(respuesta.data), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 400

@app.route('/api/clientes/<id_cliente_url>', methods=['PUT'])
def actualizar_cliente(id_cliente_url):
    if 'usuario_id' not in session: return jsonify({"error": "No autorizado"}), 401
    datos = request.json
    
    rol_actual = session.get('rol', 'empleado')
    asignado = datos.get("asignado_a", "sin_asignar")
    if rol_actual == 'empleado':
        asignado = session.get('correo')
        
    datos_actualizar = {
        "nombre_comercial": datos.get("nombre_comercial"),
        "telefono": datos.get("telefono"),
        "direccion_completa": datos.get("direccion_completa"),
        "latitud": datos.get("latitud"),
        "longitud": datos.get("longitud"),
        "estado_logistico": datos.get("estado_logistico"),
        "estado_secundario": datos.get("estado_secundario"),
        "notas_internas": datos.get("notas_internas"),
        "asignado_a": asignado
    }
    try:
        respuesta = supabase.table("clientes").update(datos_actualizar).eq("id_cliente", id_cliente_url).execute()
        # Si el cliente deja de tener Pedido Activo, sus entregas del POS se dan por entregadas.
        if "pedido_activo" not in (datos_actualizar["estado_logistico"], datos_actualizar["estado_secundario"]):
            try:
                supabase.table("entregas").update({"estado": "entregado"}) \
                    .eq("id_cliente", str(id_cliente_url)).eq("estado", "pendiente").execute()
            except Exception as e:
                print("Aviso: no se pudieron cerrar las entregas:", e)
        return jsonify({"mensaje": "Cliente actualizado", "datos": respuesta.data}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 400

# --- INTEGRACIÓN CON CHROMIS POS ---

@app.route('/api/chromis/estado', methods=['GET'])
def chromis_estado():
    if 'usuario_id' not in session or session.get('rol') != 'admin':
        return jsonify({"error": "No tienes permisos de Administrador"}), 403
    try:
        return jsonify(chromis_sync.estado_conexion()), 200
    except Exception as e:
        return jsonify({"conectado": False, "error": str(e)}), 500

@app.route('/api/chromis/sincronizar', methods=['POST'])
def chromis_sincronizar():
    if 'usuario_id' not in session or session.get('rol') != 'admin':
        return jsonify({"error": "No tienes permisos de Administrador"}), 403
    if not supabase: return jsonify({"error": "Error de BD"}), 500
    try:
        clientes = [c for c in supabase.table("clientes").select("*").execute().data if not c.get("chromis_id")]
        resumen = chromis_sync.sincronizar_clientes(clientes)
        return jsonify(resumen), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(debug=True, port=5000)