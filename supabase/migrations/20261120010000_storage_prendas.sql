-- Monzatti · fotos de prendas digitalizadas en Supabase Storage.
-- Bucket privado; cada persona solo ve y sube archivos dentro de su carpeta (<user_id>/<prenda>.png).

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('garments', 'garments', false, 5242880, array['image/png', 'image/jpeg', 'image/webp'])
on conflict (id) do nothing;

create policy "prendas: leer las propias" on storage.objects for select to authenticated
  using (bucket_id = 'garments' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy "prendas: subir las propias" on storage.objects for insert to authenticated
  with check (bucket_id = 'garments' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy "prendas: reemplazar las propias" on storage.objects for update to authenticated
  using (bucket_id = 'garments' and (storage.foldername(name))[1] = (select auth.uid())::text)
  with check (bucket_id = 'garments' and (storage.foldername(name))[1] = (select auth.uid())::text);

create policy "prendas: borrar las propias" on storage.objects for delete to authenticated
  using (bucket_id = 'garments' and (storage.foldername(name))[1] = (select auth.uid())::text);
