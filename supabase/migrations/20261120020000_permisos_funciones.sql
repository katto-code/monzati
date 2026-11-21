-- Monzatti · cierra funciones internas que no deben llamarse por la API (avisos del asesor de seguridad).

-- Funciones de trigger: solo las ejecuta la base de datos al insertar/actualizar.
revoke execute on function public.handle_new_user() from public, anon, authenticated;
revoke execute on function public.protect_profile_role() from public, anon, authenticated;

-- is_admin() se usa en las políticas RLS de usuarios con sesión; nadie anónimo la necesita.
revoke execute on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated;
