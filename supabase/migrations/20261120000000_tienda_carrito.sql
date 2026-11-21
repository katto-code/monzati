-- Monzatti · tienda: carrito, checkout con reserva de 10 min (RN-02), pedidos con comisión (RN-01)
-- y resumen de ventas para el socio comercial.

-- ───────────────────────── Columnas de pedido ─────────────────────────
alter table public.orders
  add column subtotal_mxn     numeric(10, 2) not null default 0,
  add column commission_mxn   numeric(10, 2) not null default 0,
  add column total_mxn        numeric(10, 2) not null default 0,
  add column payment_ref      text,
  add column shipping_address text;

-- Copia del producto al momento de la compra, para el historial.
alter table public.order_items
  add column product_id   uuid references public.products (id) on delete set null,
  add column product_name text not null default '',
  add column brand_name   text not null default '',
  add column size         text not null default '';
create index order_items_product_id_idx on public.order_items (product_id);

-- ───────────────────────── Carrito ─────────────────────────
create table public.cart_items (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  variant_id  uuid not null references public.product_variants (id) on delete cascade,
  quantity    integer not null default 1 check (quantity between 1 and 10),
  created_at  timestamptz not null default now(),
  unique (user_id, variant_id)
);
create index cart_items_variant_id_idx on public.cart_items (variant_id);

alter table public.cart_items enable row level security;
create policy "carrito: dueño" on public.cart_items for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

-- El socio ve las partidas vendidas de sus marcas.
create policy "partidas: marca lee" on public.order_items for select to authenticated
  using (exists (select 1 from public.products p join public.brands b on b.id = p.brand_id
                 where p.id = product_id and b.owner_id = (select auth.uid())));

-- ───────────────────────── Checkout paso 1: reservar (RN-02) ─────────────────────────
-- Aparta por 10 minutos todo lo que hay en el carrito. Falla si algo no tiene existencias (RF-01).
create or replace function public.start_checkout()
returns timestamptz
language plpgsql security definer set search_path = ''
as $$
declare
  uid uuid := (select auth.uid());
  item record;
  total_stock integer;
  reserved integer;
  expires timestamptz := now() + interval '10 minutes';
begin
  if uid is null then
    raise exception 'Inicia sesión para comprar';
  end if;

  delete from public.inventory_reservations where expires_at < now() or user_id = uid;

  if not exists (select 1 from public.cart_items where user_id = uid) then
    raise exception 'Tu carrito está vacío';
  end if;

  for item in
    select c.variant_id, c.quantity, p.name as product_name, v.size
      from public.cart_items c
      join public.product_variants v on v.id = c.variant_id
      join public.products p on p.id = v.product_id
     where c.user_id = uid
     order by c.variant_id
  loop
    select stock into total_stock from public.product_variants where id = item.variant_id for update;
    select coalesce(sum(quantity), 0) into reserved
      from public.inventory_reservations where variant_id = item.variant_id;
    if total_stock - reserved < item.quantity then
      raise exception 'Ya no hay existencias de % talla %', item.product_name, item.size;
    end if;
    insert into public.inventory_reservations (variant_id, user_id, quantity, expires_at)
    values (item.variant_id, uid, item.quantity, expires);
  end loop;

  return expires;
end;
$$;

-- ───────────────────────── Checkout paso 2: pago de prueba y pedido (RN-01) ─────────────────────────
-- Convierte la reserva vigente en un pedido pagado, descuenta existencias y vacía el carrito.
-- La comisión de Monzatti (12 %, dentro del rango 10–15 %) se calcula sobre el importe neto, sin envío.
create or replace function public.confirm_order(p_payment_ref text, p_shipping_address text)
returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  uid uuid := (select auth.uid());
  rate numeric(4, 3) := 0.12;
  shipping numeric(10, 2) := 0;
  new_order uuid;
  subtotal numeric(10, 2);
begin
  if uid is null then
    raise exception 'Inicia sesión para comprar';
  end if;

  delete from public.inventory_reservations where expires_at < now();

  if not exists (select 1 from public.cart_items where user_id = uid) then
    raise exception 'Tu carrito está vacío';
  end if;

  -- Cada partida del carrito debe estar cubierta por una reserva vigente de esta persona.
  if exists (
    select 1
      from public.cart_items c
      left join (
        select variant_id, sum(quantity) as qty
          from public.inventory_reservations
         where user_id = uid
         group by variant_id
      ) r on r.variant_id = c.variant_id
     where c.user_id = uid and coalesce(r.qty, 0) < c.quantity
  ) then
    raise exception 'Tu reserva expiró. Vuelve a intentar el pago.';
  end if;

  insert into public.orders (buyer_id, status, commission_rate, shipping_mxn, payment_ref, shipping_address)
  values (uid, 'pagado', rate, shipping, p_payment_ref, p_shipping_address)
  returning id into new_order;

  insert into public.order_items (order_id, variant_id, quantity, unit_price_mxn, product_id, product_name, brand_name, size)
  select new_order, c.variant_id, c.quantity, p.price_mxn, p.id, p.name, b.name, v.size
    from public.cart_items c
    join public.product_variants v on v.id = c.variant_id
    join public.products p on p.id = v.product_id
    join public.brands b on b.id = p.brand_id
   where c.user_id = uid;

  update public.product_variants v
     set stock = v.stock - c.quantity
    from public.cart_items c
   where c.user_id = uid and c.variant_id = v.id;

  select coalesce(sum(quantity * unit_price_mxn), 0) into subtotal
    from public.order_items where order_id = new_order;

  update public.orders
     set subtotal_mxn = subtotal,
         commission_mxn = round(subtotal * rate, 2),
         total_mxn = subtotal + shipping
   where id = new_order;

  delete from public.inventory_reservations where user_id = uid;
  delete from public.cart_items where user_id = uid;

  return new_order;
end;
$$;

-- ───────────────────────── Ventas del socio ─────────────────────────
create or replace function public.brand_sales_summary()
returns table (orders_count bigint, units bigint, gross_mxn numeric, commission_mxn numeric, net_mxn numeric)
language sql stable security definer set search_path = ''
as $$
  select count(distinct o.id),
         coalesce(sum(oi.quantity), 0),
         coalesce(sum(oi.quantity * oi.unit_price_mxn), 0),
         coalesce(round(sum(oi.quantity * oi.unit_price_mxn * o.commission_rate), 2), 0),
         coalesce(sum(oi.quantity * oi.unit_price_mxn), 0)
           - coalesce(round(sum(oi.quantity * oi.unit_price_mxn * o.commission_rate), 2), 0)
    from public.order_items oi
    join public.orders o on o.id = oi.order_id and o.status in ('pagado', 'enviado', 'entregado')
    join public.products p on p.id = oi.product_id
    join public.brands b on b.id = p.brand_id
   where b.owner_id = (select auth.uid());
$$;

revoke execute on function public.start_checkout() from public, anon;
revoke execute on function public.confirm_order(text, text) from public, anon;
revoke execute on function public.brand_sales_summary() from public, anon;
grant execute on function public.start_checkout() to authenticated;
grant execute on function public.confirm_order(text, text) to authenticated;
grant execute on function public.brand_sales_summary() to authenticated;
