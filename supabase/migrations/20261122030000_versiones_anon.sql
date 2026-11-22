-- Monzatti · la app busca actualizaciones antes de iniciar sesión (rol anon).
-- La política anterior llamaba a is_admin(), que anon no puede ejecutar: toda la consulta fallaba con
-- «permission denied for function is_admin» y nadie sin sesión veía las versiones publicadas.
drop policy "versiones: leer publicadas" on public.app_releases;

create policy "versiones: leer publicadas" on public.app_releases for select to anon, authenticated
  using (published);

create policy "versiones: admin lee todas" on public.app_releases for select to authenticated
  using ((select public.is_admin()));
