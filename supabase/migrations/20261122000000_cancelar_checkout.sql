-- Monzatti · libera la reserva (RN-02) cuando la persona sale del pago sin pagar.
create or replace function public.cancel_checkout()
returns void
language plpgsql security definer set search_path = ''
as $$
begin
  delete from public.inventory_reservations where user_id = (select auth.uid());
end;
$$;

revoke execute on function public.cancel_checkout() from public, anon;
grant execute on function public.cancel_checkout() to authenticated;
