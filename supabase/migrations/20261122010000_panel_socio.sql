-- Monzatti · panel del socio: pedidos de su marca, envío por marca y baja de productos.

-- Un pedido puede traer prendas de varias marcas: cada marca marca como enviadas solo sus partidas.
alter table public.order_items add column shipped_at timestamptz;

-- ───────────────────────── Pedidos de la marca ─────────────────────────
-- Una fila por partida vendida de las marcas del socio con sesión (la app las agrupa por pedido).
create or replace function public.brand_orders()
returns table (order_id uuid, created_at timestamptz, status public.order_status, product_name text,
               brand_name text, size text, quantity integer, unit_price_mxn numeric, shipped_at timestamptz)
language sql stable security definer set search_path = ''
as $$
  select o.id, o.created_at, o.status, oi.product_name, oi.brand_name, oi.size, oi.quantity,
         oi.unit_price_mxn, oi.shipped_at
    from public.order_items oi
    join public.orders o on o.id = oi.order_id and o.status in ('pagado', 'enviado', 'entregado')
    join public.products p on p.id = oi.product_id
    join public.brands b on b.id = p.brand_id
   where b.owner_id = (select auth.uid())
   order by o.created_at desc, oi.product_name;
$$;

-- Marca como enviadas las partidas de la marca del socio; el pedido pasa a «enviado»
-- cuando todas sus marcas ya enviaron.
create or replace function public.mark_order_shipped(p_order_id uuid)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
  update public.order_items oi
     set shipped_at = now()
    from public.products p
    join public.brands b on b.id = p.brand_id
   where oi.order_id = p_order_id
     and p.id = oi.product_id
     and b.owner_id = (select auth.uid())
     and oi.shipped_at is null
     and exists (select 1 from public.orders o where o.id = p_order_id and o.status = 'pagado');
  if not found then
    raise exception 'Este pedido no tiene prendas de tu marca por enviar';
  end if;

  update public.orders o
     set status = 'enviado'
   where o.id = p_order_id
     and o.status = 'pagado'
     and not exists (select 1 from public.order_items oi where oi.order_id = p_order_id and oi.shipped_at is null);
end;
$$;

-- ───────────────────────── Baja de productos ─────────────────────────
-- No se borra (los pedidos guardan referencia): se oculta de la tienda y se saca de los carritos.
create or replace function public.archive_product(p_product_id uuid)
returns void
language plpgsql security definer set search_path = ''
as $$
begin
  update public.products p
     set is_active = false
    from public.brands b
   where p.id = p_product_id
     and b.id = p.brand_id
     and (b.owner_id = (select auth.uid()) or (select public.is_admin()));
  if not found then
    raise exception 'Este producto no es de tu marca';
  end if;

  delete from public.cart_items c
   using public.product_variants v
   where v.id = c.variant_id and v.product_id = p_product_id;
end;
$$;

revoke execute on function public.brand_orders() from public, anon;
revoke execute on function public.mark_order_shipped(uuid) from public, anon;
revoke execute on function public.archive_product(uuid) from public, anon;
grant execute on function public.brand_orders() to authenticated;
grant execute on function public.mark_order_shipped(uuid) to authenticated;
grant execute on function public.archive_product(uuid) to authenticated;
