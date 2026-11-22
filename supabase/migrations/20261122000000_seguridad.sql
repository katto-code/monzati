-- Monzatti · cierre de huecos de seguridad.

-- 1) Marcas: solo un administrador decide «verified». Un socio que crea o edita su marca no puede tocarlo.
create or replace function public.brands_guard_verified()
returns trigger
language plpgsql set search_path = ''
as $$
begin
  -- Solo se vigila a quien entra por la API; el panel de Supabase (postgres / service_role) sigue libre.
  if current_user in ('authenticated', 'anon') and not (select public.is_admin()) then
    if tg_op = 'INSERT' then
      new.verified := false;
    else
      new.verified := old.verified;
    end if;
  end if;
  return new;
end;
$$;

create trigger brands_guard_verified
  before insert or update on public.brands
  for each row execute function public.brands_guard_verified();

-- 2) reserve_variant: la app no la usa (start_checkout reserva por su cuenta, ya validando el carrito).
--    Expuesta, cualquiera podía apartar todo el inventario sin comprar.
revoke execute on function public.reserve_variant(uuid, integer) from public, anon, authenticated;

-- 3) Actualizaciones: una versión publicada debe traer su SHA-256 (la app ya no instala sin él).
--    not valid: no rompe filas viejas; se aplica a todo lo nuevo o editado.
alter table public.app_releases
  add constraint app_releases_publicada_con_sha256 check (not published or sha256 is not null) not valid;

-- 4) Espejo: canales privados de Realtime, uno por persona (mirror:<user_id>).
--    Antes había un único canal público y cualquiera podía escuchar o mandar prendas.
create policy "espejo: escuchar el canal propio" on realtime.messages for select to authenticated
  using (realtime.topic() = 'mirror:' || (select auth.uid())::text and extension = 'broadcast');

create policy "espejo: enviar al canal propio" on realtime.messages for insert to authenticated
  with check (realtime.topic() = 'mirror:' || (select auth.uid())::text and extension = 'broadcast');
