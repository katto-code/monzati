-- Monzatti · servicio de actualizaciones de la app (APK fuera de Play Store).
-- La app consulta esta tabla al abrirse; si hay una versión con version_code mayor, ofrece descargarla e instalarla.
--   canal 'estable'  → APK de release firmado con la llave de Monzatti (lo que se reparte).
--   canal 'pruebas'  → builds de depuración, para probar el flujo sin molestar a nadie.

create table public.app_releases (
  id uuid primary key default gen_random_uuid(),
  channel text not null default 'estable' check (channel in ('estable', 'pruebas')),
  version_code integer not null check (version_code > 0),
  version_name text not null check (length(version_name) between 1 and 32),
  apk_url text not null check (apk_url ~ '^https://' or channel = 'pruebas'),
  -- SHA-256 del APK en hexadecimal; la app lo verifica antes de instalar.
  sha256 text check (sha256 ~ '^[0-9a-f]{64}$'),
  notes text not null default '' check (length(notes) <= 2000),
  -- Obligatoria: la app no deja seguir sin actualizar (por ejemplo, si cambió la base de datos).
  mandatory boolean not null default false,
  published boolean not null default true,
  created_at timestamptz not null default now(),
  unique (channel, version_code)
);

comment on table public.app_releases is 'Versiones de la app Android que se ofrecen como actualización.';

alter table public.app_releases enable row level security;

-- Se consulta antes de iniciar sesión, así que también la lee anon.
create policy "versiones: leer publicadas" on public.app_releases for select to anon, authenticated
  using (published or (select public.is_admin()));

create policy "versiones: administrar" on public.app_releases for all to authenticated
  using ((select public.is_admin()))
  with check ((select public.is_admin()));

grant select on public.app_releases to anon, authenticated;
grant insert, update, delete on public.app_releases to authenticated;

-- Bucket público para los APK (cualquiera con el enlace puede descargarlo; solo administradores suben).
-- 50 MB es el máximo por archivo del plan gratuito.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('releases', 'releases', true, 52428800,
        array['application/vnd.android.package-archive', 'application/octet-stream'])
on conflict (id) do nothing;

create policy "apk: subir (admin)" on storage.objects for insert to authenticated
  with check (bucket_id = 'releases' and (select public.is_admin()));

create policy "apk: reemplazar (admin)" on storage.objects for update to authenticated
  using (bucket_id = 'releases' and (select public.is_admin()))
  with check (bucket_id = 'releases' and (select public.is_admin()));

create policy "apk: borrar (admin)" on storage.objects for delete to authenticated
  using (bucket_id = 'releases' and (select public.is_admin()));
