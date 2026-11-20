-- Monzatti · esquema inicial (S0-09: requisitos funcionales y reglas de negocio)
-- Pégalo en Supabase > SQL Editor y ejecútalo una sola vez.

-- ───────────────────────── Tipos ─────────────────────────
create type public.user_role as enum ('usuario', 'socio', 'admin');
create type public.gender as enum ('femenino', 'masculino');
create type public.style_palette as enum ('monzatti', 'coquette', 'gotica', 'streetwear', 'old_money');
create type public.order_status as enum ('pendiente', 'pagado', 'cancelado', 'enviado', 'entregado');

-- ───────────────────────── Perfiles ─────────────────────────
-- Un perfil por cuenta de auth.users. Medidas y tono de piel alimentan el avatar (RF-03).
create table public.profiles (
  id             uuid primary key references auth.users (id) on delete cascade,
  full_name      text not null default '',
  role           public.user_role not null default 'usuario',
  gender         public.gender,
  style_palette  public.style_palette not null default 'monzatti',
  height_cm      numeric(5, 1) check (height_cm between 100 and 230),
  bust_cm        numeric(5, 1) check (bust_cm > 0),
  waist_cm       numeric(5, 1) check (waist_cm > 0),
  hip_cm         numeric(5, 1) check (hip_cm > 0),
  skin_tone      text,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

-- Ayudante para RLS: ¿la persona con sesión es admin?
create or replace function public.is_admin()
returns boolean
language sql stable security definer set search_path = ''
as $$
  select exists (select 1 from public.profiles where id = (select auth.uid()) and role = 'admin');
$$;

-- Crea el perfil al registrarse, con los metadatos que manda la app (full_name, role, gender).
-- Nadie puede registrarse como admin desde la app.
create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer set search_path = ''
as $$
declare
  requested_role text := coalesce(new.raw_user_meta_data ->> 'role', 'usuario');
begin
  insert into public.profiles (id, full_name, role, gender)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'full_name', ''),
    case when requested_role = 'socio' then 'socio'::public.user_role else 'usuario'::public.user_role end,
    case new.raw_user_meta_data ->> 'gender'
      when 'femenino' then 'femenino'::public.gender
      when 'masculino' then 'masculino'::public.gender
    end
  );
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- Impide que alguien se cambie el rol a sí mismo (solo un admin puede).
create or replace function public.protect_profile_role()
returns trigger
language plpgsql security definer set search_path = ''
as $$
begin
  if new.role <> old.role and not public.is_admin() then
    raise exception 'Solo un administrador puede cambiar el rol';
  end if;
  new.updated_at := now();
  return new;
end;
$$;

create trigger profiles_protect_role
  before update on public.profiles
  for each row execute function public.protect_profile_role();

-- ───────────────────────── Marcas aliadas ─────────────────────────
create table public.brands (
  id          uuid primary key default gen_random_uuid(),
  -- Null = marca ficticia administrada por Monzatti (catálogo de demostración).
  owner_id    uuid references public.profiles (id) on delete cascade,
  name        text not null,
  verified    boolean not null default false,  -- lo valida el admin
  created_at  timestamptz not null default now()
);
create index brands_owner_id_idx on public.brands (owner_id);

-- ───────────────────────── Catálogo e inventario (RF-01) ─────────────────────────
create table public.products (
  id          uuid primary key default gen_random_uuid(),
  brand_id    uuid not null references public.brands (id) on delete cascade,
  name        text not null,
  category    text not null default 'Nuevo',
  color       text,
  emoji       text,
  image_url   text,
  price_mxn   numeric(10, 2) not null check (price_mxn >= 0),
  is_active   boolean not null default true,
  created_at  timestamptz not null default now()
);
create index products_brand_id_idx on public.products (brand_id);

create table public.product_variants (
  id          uuid primary key default gen_random_uuid(),
  product_id  uuid not null references public.products (id) on delete cascade,
  size        text not null,
  stock       integer not null default 0 check (stock >= 0),
  unique (product_id, size)
);

-- ───────────────────────── Closet del usuario ─────────────────────────
create table public.garments (
  id           uuid primary key default gen_random_uuid(),
  owner_id     uuid not null references public.profiles (id) on delete cascade,
  name         text not null,
  category     text not null,
  size         text,
  color        text,
  emoji        text,
  image_url    text,
  is_favorite  boolean not null default false,
  created_at   timestamptz not null default now()
);
create index garments_owner_id_idx on public.garments (owner_id);

-- ───────────────────────── Pedidos y comisión (RN-01) ─────────────────────────
create table public.orders (
  id               uuid primary key default gen_random_uuid(),
  buyer_id         uuid not null references public.profiles (id),
  status           public.order_status not null default 'pendiente',
  -- Comisión Monzatti: 10 % a 15 % del importe neto, antes de envío.
  commission_rate  numeric(4, 3) not null default 0.10 check (commission_rate between 0.10 and 0.15),
  shipping_mxn     numeric(10, 2) not null default 0 check (shipping_mxn >= 0),
  created_at       timestamptz not null default now()
);
create index orders_buyer_id_idx on public.orders (buyer_id);

create table public.order_items (
  id              uuid primary key default gen_random_uuid(),
  order_id        uuid not null references public.orders (id) on delete cascade,
  variant_id      uuid not null references public.product_variants (id),
  quantity        integer not null check (quantity > 0),
  unit_price_mxn  numeric(10, 2) not null check (unit_price_mxn >= 0)
);
create index order_items_order_id_idx on public.order_items (order_id);
create index order_items_variant_id_idx on public.order_items (variant_id);

-- ───────────────────────── Reserva temporal de inventario (RN-02) ─────────────────────────
create table public.inventory_reservations (
  id          uuid primary key default gen_random_uuid(),
  variant_id  uuid not null references public.product_variants (id) on delete cascade,
  user_id     uuid not null references public.profiles (id) on delete cascade,
  quantity    integer not null default 1 check (quantity > 0),
  expires_at  timestamptz not null default now() + interval '10 minutes',
  created_at  timestamptz not null default now()
);
create index inventory_reservations_variant_id_idx on public.inventory_reservations (variant_id);
create index inventory_reservations_user_id_idx on public.inventory_reservations (user_id);

-- Reserva una unidad al iniciar el checkout. Falla si no hay existencias libres (RF-01).
create or replace function public.reserve_variant(p_variant_id uuid, p_quantity integer default 1)
returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  total_stock integer;
  reserved integer;
  reservation_id uuid;
begin
  if (select auth.uid()) is null then
    raise exception 'Inicia sesión para comprar';
  end if;

  delete from public.inventory_reservations where expires_at < now();

  -- Bloquea la variante para que dos compras simultáneas no reserven la misma unidad.
  select stock into total_stock from public.product_variants where id = p_variant_id for update;
  select coalesce(sum(quantity), 0) into reserved
    from public.inventory_reservations where variant_id = p_variant_id;

  if total_stock is null or total_stock - reserved < p_quantity then
    raise exception 'Sin existencias disponibles';
  end if;

  insert into public.inventory_reservations (variant_id, user_id, quantity)
  values (p_variant_id, (select auth.uid()), p_quantity)
  returning id into reservation_id;
  return reservation_id;
end;
$$;

-- Libera las reservas vencidas. Programable con pg_cron:
--   select cron.schedule('liberar-reservas', '* * * * *', 'select public.release_expired_reservations()');
create or replace function public.release_expired_reservations()
returns integer
language sql security definer set search_path = ''
as $$
  with deleted as (delete from public.inventory_reservations where expires_at < now() returning 1)
  select count(*)::integer from deleted;
$$;

-- ───────────────────────── Espejo inteligente (RF-02) ─────────────────────────
create table public.mirror_sessions (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references public.profiles (id) on delete cascade,
  mirror_code     text not null,            -- identificador del espejo vinculado
  garment_ids     uuid[] not null default '{}',
  product_ids     uuid[] not null default '{}',
  is_projecting   boolean not null default false,
  updated_at      timestamptz not null default now(),
  unique (user_id, mirror_code)
);

create or replace function public.touch_updated_at()
returns trigger
language plpgsql set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger mirror_sessions_touch
  before update on public.mirror_sessions
  for each row execute function public.touch_updated_at();

-- El espejo escucha los cambios en tiempo real (latencia objetivo ≤ 3 s).
alter publication supabase_realtime add table public.mirror_sessions;

-- ───────────────────────── Seguridad a nivel de fila ─────────────────────────
alter table public.profiles enable row level security;
alter table public.brands enable row level security;
alter table public.products enable row level security;
alter table public.product_variants enable row level security;
alter table public.garments enable row level security;
alter table public.orders enable row level security;
alter table public.order_items enable row level security;
alter table public.inventory_reservations enable row level security;
alter table public.mirror_sessions enable row level security;

-- Perfiles: cada quien el suyo; el admin todos.
create policy "perfil propio: leer" on public.profiles for select to authenticated
  using (id = (select auth.uid()) or (select public.is_admin()));
create policy "perfil propio: editar" on public.profiles for update to authenticated
  using (id = (select auth.uid()) or (select public.is_admin()))
  with check (id = (select auth.uid()) or (select public.is_admin()));

-- Marcas: visibles para cualquiera con sesión; solo socios crean la suya.
create policy "marcas: leer" on public.brands for select to authenticated using (true);
create policy "marcas: crear la propia" on public.brands for insert to authenticated
  with check (owner_id = (select auth.uid())
              and exists (select 1 from public.profiles where id = (select auth.uid()) and role = 'socio'));
create policy "marcas: editar la propia" on public.brands for update to authenticated
  using (owner_id = (select auth.uid()) or (select public.is_admin()))
  with check (owner_id = (select auth.uid()) or (select public.is_admin()));

-- Productos: activos visibles para todos; el dueño de la marca ve y edita los suyos.
create policy "productos: leer" on public.products for select to authenticated
  using (is_active
         or exists (select 1 from public.brands b where b.id = brand_id and b.owner_id = (select auth.uid()))
         or (select public.is_admin()));
create policy "productos: dueño escribe" on public.products for all to authenticated
  using (exists (select 1 from public.brands b where b.id = brand_id and b.owner_id = (select auth.uid()))
         or (select public.is_admin()))
  with check (exists (select 1 from public.brands b where b.id = brand_id and b.owner_id = (select auth.uid()))
              or (select public.is_admin()));

create policy "variantes: leer" on public.product_variants for select to authenticated using (true);
create policy "variantes: dueño escribe" on public.product_variants for all to authenticated
  using (exists (select 1 from public.products p join public.brands b on b.id = p.brand_id
                 where p.id = product_id and b.owner_id = (select auth.uid()))
         or (select public.is_admin()))
  with check (exists (select 1 from public.products p join public.brands b on b.id = p.brand_id
                      where p.id = product_id and b.owner_id = (select auth.uid()))
              or (select public.is_admin()));

-- Closet: solo el dueño.
create policy "closet: dueño" on public.garments for all to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));

-- Pedidos: el comprador los suyos; el admin todos. (La comisión la fija el servidor, no la app.)
create policy "pedidos: leer" on public.orders for select to authenticated
  using (buyer_id = (select auth.uid()) or (select public.is_admin()));
-- Los pedidos solo se crean con confirm_order() (ver migración de tienda), nunca directo desde la app.
create policy "partidas: leer" on public.order_items for select to authenticated
  using (exists (select 1 from public.orders o where o.id = order_id
                 and (o.buyer_id = (select auth.uid()) or (select public.is_admin()))));

-- Reservas: se crean con reserve_variant(); cada quien ve las suyas.
create policy "reservas: leer" on public.inventory_reservations for select to authenticated
  using (user_id = (select auth.uid()) or (select public.is_admin()));

-- Espejo: solo el dueño de la sesión.
create policy "espejo: dueño" on public.mirror_sessions for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

-- Las funciones de servicio no se exponen a anónimos.
revoke execute on function public.release_expired_reservations() from public, anon, authenticated;
revoke execute on function public.reserve_variant(uuid, integer) from public, anon;
grant execute on function public.reserve_variant(uuid, integer) to authenticated;
