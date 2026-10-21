-- Ejecutar una vez en Supabase (SQL Editor).

-- Liga un cliente de la web con su cliente en Chromis (para clientes dados de alta en el POS).
alter table clientes add column if not exists chromis_id text unique;

-- Pedidos marcados como "Entrega" en el punto de venta.
create table if not exists entregas (
    id_ticket       text primary key,          -- receipts.id de Chromis
    numero_ticket   integer,
    fecha           timestamptz,
    id_cliente      text,                      -- clientes.id_cliente
    total           numeric,
    notas           text,
    estado          text not null default 'pendiente'   -- pendiente / entregado
);
