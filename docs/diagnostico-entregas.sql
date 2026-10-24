-- Diagnóstico: ¿por qué una entrega sale sin cuadrante o sin repartidor?
-- Correr en la base que usa el equipo con el problema (HeidiSQL, mysql, etc.).
-- El cuadrante de una entrega sale del PIN DEL CLIENTE (no de la dirección
-- escrita), y el repartidor sale del cuadrante (o del asignado a mano).

-- 0) ¿Es la base que crees? (nombre y cuántos cuadrantes y pines tiene)
SELECT DATABASE() AS base,
       (SELECT COUNT(*) FROM delivery_zones WHERE active = 1) AS cuadrantes_activos,
       (SELECT COUNT(*) FROM delivery_zones WHERE active = 1 AND driver IS NOT NULL) AS cuadrantes_con_repartidor,
       (SELECT COUNT(*) FROM customer_geo WHERE lat IS NOT NULL) AS clientes_con_pin;

-- 1) Entregas abiertas y qué les falta
SELECT t.ticketid AS ticket,
       DATE(r.datenew) AS vendida,
       COALESCE(c.name, '(SIN CLIENTE)') AS cliente,
       CASE WHEN t.customer IS NULL THEN 'venta sin cliente: asígnale cliente en la caja'
            WHEN g.lat IS NULL THEN 'cliente SIN PIN: ubícalo en el mapa'
            ELSE CONCAT('pin ', g.lat, ',', g.lng, ' (si no sale cuadrante, el pin quedó fuera de todos)') END AS revisar,
       COALESCE(s.status, 'confirmado') AS estado,
       p.name AS repartidor_a_mano
  FROM customer_delivery cd
  JOIN tickets t ON t.id = cd.id AND t.tickettype = 0
  JOIN receipts r ON r.id = t.id
  LEFT JOIN customers c ON c.id = t.customer
  LEFT JOIN customer_geo g ON g.customer = t.customer
  LEFT JOIN delivery_status s ON s.ticket = cd.id
  LEFT JOIN people p ON p.id = s.driver
 WHERE cd.delivered = 0 AND COALESCE(s.status, 'confirmado') NOT IN ('completado', 'cancelado')
 ORDER BY r.datenew DESC;

-- 2) Cuadrantes y su repartidor (sin repartidor = sus entregas salen "Sin repartidor")
SELECT z.id, z.name AS cuadrante, z.active AS activo,
       COALESCE(p.name, '(SIN REPARTIDOR)') AS repartidor,
       COALESCE(d.name, 'cualquiera') AS despachador
  FROM delivery_zones z
  LEFT JOIN people p ON p.id = z.driver
  LEFT JOIN people d ON d.id = z.dispatcher
 ORDER BY z.active DESC, z.name;
