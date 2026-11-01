--[[
  VR Soldado - Total War: WARHAMMER (II / III)
  ===========================================================================
  Script de batalla que "encarna" al jugador en un soldado de una de sus
  unidades:

    * La camara se ancla a la altura de los ojos (vista en primera persona,
      pensada para usarse con un inyector VR estereoscopico, ver README).
    * El soldado encarnado vive dentro de una unidad. Cada baja que sufre la
      unidad tiene una probabilidad de 1/N (N = hombres vivos antes de la
      baja) de ser "tu" soldado, de modo que estadisticamente mueres igual
      que cualquier otro miembro de la unidad.
    * Si tu soldado muere, puedes saltar a:
        - otro soldado de la MISMA unidad (si quedan vivos), o
        - un soldado de OTRA unidad (siguiente / anterior).
      Si no eliges en X segundos, se salta automaticamente.

  Los scripts colocados en script/battle/mod/ se cargan automaticamente en
  todas las batallas, no hace falta tocar ningun archivo del juego base.

  NOTA: la API de scripting de Creative Assembly no expone las posiciones de
  las entidades individuales (soldados), solo de la unidad. El soldado se
  representa por la posicion/orientacion de la unidad mas un pequeno
  desplazamiento aleatorio dentro de la formacion.
]]

---------------------------------------------------------------------------
-- CONFIGURACION
---------------------------------------------------------------------------
local CFG = {
	enabled              = true,
	eye_height           = 1.8,    -- metros sobre el terreno (los ogros/monstruos se escalan abajo)
	look_ahead           = 25,     -- distancia (m) del punto al que se mira
	camera_back_offset   = 0.0,    -- >0 = tercera persona por detras del soldado
	camera_fov           = 75,     -- campo de vision (VR suele ir bien entre 70-90)
	update_ms            = 100,    -- frecuencia de actualizacion de la camara
	camera_smooth_s      = 0.15,   -- duracion de cada movimiento de camara (suavizado)
	formation_jitter     = 4.0,    -- desplazamiento max (m) del soldado dentro de la formacion
	auto_switch_s        = 8,      -- segundos tras morir antes del salto automatico
	include_allies       = true,   -- permitir saltar a unidades de ejercitos aliados
	bearing_sign         = 1,      -- cambia a -1 si la camara mira hacia atras

	-- Atajos de teclado (ids de shortcut del juego, ver README para cambiarlos)
	keys = {
		toggle          = "camera_bookmark_view0", -- activar/desactivar encarnacion
		same_unit       = "camera_bookmark_view1", -- saltar a otro soldado de la misma unidad
		next_unit       = "camera_bookmark_view2", -- saltar a la siguiente unidad
		prev_unit       = "camera_bookmark_view3", -- saltar a la unidad anterior
	},
}

---------------------------------------------------------------------------
-- ESTADO
---------------------------------------------------------------------------
local PROCESS_NAME  = "vr_soldado_camera"
local AUTO_SWITCH   = "vr_soldado_auto_switch"

local state = {
	active        = false,
	unit          = nil,   -- objeto unit del juego
	unit_index    = 0,     -- indice dentro de la lista de unidades controlables
	men_alive     = 0,     -- ultimo recuento de hombres vivos de la unidad
	soldier_dead  = false,
	jitter_x      = 0,
	jitter_z      = 0,
	soldier_id    = 0,     -- numero de "vida" (para los logs)
}

local function log(msg)
	if bm and bm.out then
		bm:out("[VR Soldado] " .. tostring(msg))
	end
end

-- Llama a fn de forma protegida para que un cambio de API no rompa la batalla
local function safe(fn, ...)
	local ok, res = pcall(fn, ...)
	if not ok then
		log("error: " .. tostring(res))
		return nil
	end
	return res
end

---------------------------------------------------------------------------
-- UNIDADES
---------------------------------------------------------------------------

-- Devuelve la lista de unidades del jugador (y aliados, si se permite) con
-- al menos un hombre vivo.
local function get_controllable_units()
	local list = {}
	local alliance = bm:get_player_alliance()
	local armies = alliance:armies()
	local player_army = bm:get_player_army()

	for a = 1, armies:count() do
		local army = armies:item(a)
		if CFG.include_allies or army == player_army then
			local units = army:units()
			for u = 1, units:count() do
				local unit = units:item(u)
				if unit and unit:number_of_men_alive() > 0 then
					table.insert(list, unit)
				end
			end
		end
	end
	return list
end

local function unit_label(unit)
	return tostring(safe(function() return unit:type() end) or "unidad")
end

local function unit_is_alive(unit)
	return unit ~= nil and (safe(function() return unit:number_of_men_alive() end) or 0) > 0
end

-- Escala de altura de ojos segun el tamano de la unidad (ogros, monstruos...)
local function eye_height_for(unit)
	local men = safe(function() return unit:initial_number_of_men() end) or 100
	if men <= 1 then
		return CFG.eye_height * 2.5   -- personaje / monstruo unico
	elseif men <= 12 then
		return CFG.eye_height * 1.6   -- infanteria monstruosa / caballeria
	end
	return CFG.eye_height
end

---------------------------------------------------------------------------
-- CAMARA
---------------------------------------------------------------------------
local function update_camera()
	if not state.active or state.soldier_dead or not unit_is_alive(state.unit) then
		return
	end

	local unit = state.unit
	local pos = unit:position()
	local bearing = math.rad((unit:bearing() or 0) * CFG.bearing_sign)

	-- vector "hacia delante" de la unidad
	local fx, fz = math.sin(bearing), math.cos(bearing)
	-- vector "derecha"
	local rx, rz = fz, -fx

	local sx = pos:get_x() + rx * state.jitter_x + fx * state.jitter_z - fx * CFG.camera_back_offset
	local sz = pos:get_z() + rz * state.jitter_x + fz * state.jitter_z - fz * CFG.camera_back_offset
	local sy = pos:get_y() + eye_height_for(unit)

	local cam_pos = v(sx, sy, sz)
	local cam_tgt = v(sx + fx * CFG.look_ahead, sy - 0.5, sz + fz * CFG.look_ahead)

	bm:camera():move_to(cam_pos, cam_tgt, CFG.camera_smooth_s, false, CFG.camera_fov)
end

local function start_camera_process()
	bm:remove_process(PROCESS_NAME)
	bm:repeat_callback(function() safe(update_camera) end, CFG.update_ms, PROCESS_NAME)
end

local function stop_camera_process()
	bm:remove_process(PROCESS_NAME)
end

---------------------------------------------------------------------------
-- ENCARNACION
---------------------------------------------------------------------------
local function possess(unit, index)
	bm:remove_process(AUTO_SWITCH)

	state.unit         = unit
	state.unit_index   = index or state.unit_index
	state.men_alive    = unit:number_of_men_alive()
	state.soldier_dead = false
	state.soldier_id   = state.soldier_id + 1
	state.jitter_x     = (math.random() * 2 - 1) * CFG.formation_jitter
	state.jitter_z     = (math.random() * 2 - 1) * CFG.formation_jitter * 0.5

	safe(function() bm:camera():fade(false, 0.4) end)
	log(string.format("Encarnado soldado #%d en %s (%d hombres vivos)",
		state.soldier_id, unit_label(unit), state.men_alive))
end

-- Busca una unidad viva a partir de un indice, en una direccion (+1 / -1)
local function find_unit(direction, include_current)
	local units = get_controllable_units()
	local n = #units
	if n == 0 then return nil end

	-- localizar la unidad actual en la lista (los indices cambian al morir unidades)
	local current = 1
	for i, u in ipairs(units) do
		if u == state.unit then current = i break end
	end

	for step = (include_current and 0 or 1), n do
		local i = ((current - 1 + step * direction) % n) + 1
		local candidate = units[i]
		if unit_is_alive(candidate) and (include_current or candidate ~= state.unit) then
			return candidate, i
		end
	end
	return nil
end

local function jump_same_unit()
	if unit_is_alive(state.unit) then
		possess(state.unit, state.unit_index)
	else
		log("Tu unidad ha sido aniquilada: elige otra unidad.")
	end
end

local function jump_unit(direction)
	local unit, idx = find_unit(direction, false)
	if not unit and unit_is_alive(state.unit) then
		unit, idx = state.unit, state.unit_index
	end
	if unit then
		possess(unit, idx)
	else
		log("No quedan unidades a las que saltar.")
		state.active = false
		stop_camera_process()
	end
end

local function on_soldier_death(reason)
	if state.soldier_dead then return end
	state.soldier_dead = true
	log(string.format("Tu soldado #%d ha caido (%s). Pulsa %s (misma unidad) o %s/%s (otra unidad).",
		state.soldier_id, reason, CFG.keys.same_unit, CFG.keys.next_unit, CFG.keys.prev_unit))

	safe(function() bm:camera():fade(true, 0.6) end)

	-- salto automatico si el jugador no elige
	bm:callback(function()
		if state.active and state.soldier_dead then
			if unit_is_alive(state.unit) then
				jump_same_unit()
			else
				jump_unit(1)
			end
		end
	end, CFG.auto_switch_s * 1000, AUTO_SWITCH)
end

-- Comprueba las bajas de la unidad y decide si el soldado encarnado ha muerto
local function check_casualties()
	if not state.active or state.soldier_dead or not state.unit then return end

	local alive = state.unit:number_of_men_alive()
	if alive <= 0 then
		on_soldier_death("unidad aniquilada")
		return
	end

	local losses = state.men_alive - alive
	if losses > 0 then
		-- cada baja: probabilidad 1/N de ser el soldado encarnado
		local n = state.men_alive
		for _ = 1, losses do
			if math.random() < (1 / math.max(n, 1)) then
				state.men_alive = alive
				on_soldier_death("baja en combate")
				return
			end
			n = n - 1
		end
	end
	state.men_alive = alive
end

local function toggle()
	if state.active then
		state.active = false
		stop_camera_process()
		bm:remove_process(AUTO_SWITCH)
		bm:remove_process("vr_soldado_casualties")
		log("Encarnacion desactivada")
		return
	end

	local unit, idx = (unit_is_alive(state.unit) and state.unit), state.unit_index
	if not unit then
		unit, idx = find_unit(1, true)
	end
	if not unit then
		log("No hay unidades disponibles")
		return
	end

	state.active = true
	-- permitir que la camara baje a la altura de los ojos
	safe(function() bm:camera():change_height_range(0.5, 400) end)
	possess(unit, idx)
	start_camera_process()
	bm:repeat_callback(function() safe(check_casualties) end, 250, "vr_soldado_casualties")
	log("Encarnacion activada")
end

---------------------------------------------------------------------------
-- ENTRADA (atajos de teclado)
---------------------------------------------------------------------------
local function register_input()
	local actions = {
		[CFG.keys.toggle]    = toggle,
		[CFG.keys.same_unit] = function() if state.active then jump_same_unit() end end,
		[CFG.keys.next_unit] = function() if state.active then jump_unit(1) end end,
		[CFG.keys.prev_unit] = function() if state.active then jump_unit(-1) end end,
	}

	core:add_listener(
		"vr_soldado_shortcuts",
		"ShortcutTriggered",
		function(context) return actions[context.string] ~= nil end,
		function(context) safe(actions[context.string]) end,
		true
	)
end

---------------------------------------------------------------------------
-- ARRANQUE
---------------------------------------------------------------------------
local function init()
	if not CFG.enabled or not bm then return end
	math.randomseed(os.time())

	register_input()

	-- Activar automaticamente al empezar el combate
	bm:register_phase_change_callback("Deployed", function()
		if not state.active then safe(toggle) end
	end)

	bm:register_phase_change_callback("Complete", function()
		state.active = false
		stop_camera_process()
		bm:remove_process(AUTO_SWITCH)
		bm:remove_process("vr_soldado_casualties")
	end)

	log("cargado. Atajo de activacion: " .. CFG.keys.toggle)
end

safe(init)
