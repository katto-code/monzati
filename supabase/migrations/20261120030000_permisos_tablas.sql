-- Monzatti · permisos de tablas para la API.
-- Los proyectos nuevos de Supabase no exponen las tablas automáticamente: hay que dar GRANT explícito.
-- Las políticas RLS siguen decidiendo QUÉ filas ve o cambia cada persona; esto solo habilita el acceso.

grant usage on schema public to anon, authenticated;

grant select, update on public.profiles to authenticated;
grant select, insert, update on public.brands to authenticated;
grant select, insert, update, delete on public.products to authenticated;
grant select, insert, update, delete on public.product_variants to authenticated;
grant select, insert, update, delete on public.garments to authenticated;
grant select on public.orders to authenticated;
grant select on public.order_items to authenticated;
grant select on public.inventory_reservations to authenticated;
grant select, insert, update, delete on public.cart_items to authenticated;
grant select, insert, update, delete on public.mirror_sessions to authenticated;
