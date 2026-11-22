# Servicio de actualizaciones de la app

Monzatti se reparte como APK, fuera de Play Store, así que trae su propio servicio de actualizaciones:

1. Al abrirse, la app consulta la tabla `app_releases` de Supabase. Lo hace antes de iniciar sesión.
2. Si hay una versión con `version_code` mayor, muestra un aviso con las novedades: **Actualizar ahora** o **Más tarde**.
   «Más tarde» pospone el aviso 24 horas.
3. Descarga la APK con barra de progreso. Si la red se corta, reintenta y continúa donde se quedó.
4. Antes de instalar verifica la **huella SHA-256**, que el archivo sea **Monzatti** y que sea **la versión anunciada**.
5. La instala con el instalador de Android:
   - La primera vez, Android pide permitir que Monzatti instale apps.
   - Siempre muestra «¿Quieres actualizar esta app?». Ninguna app puede instalarse sola sin esa confirmación.
6. Una versión **obligatoria** (`mandatory = true`) no deja seguir sin actualizar. Úsala cuando cambie la base de datos.

En **«Conoce el proyecto Monzatti»** se ve la versión instalada y está el botón **Buscar actualizaciones**.

## Canales

| Canal | Quién lo usa | Para qué |
|---|---|---|
| `estable` | APK de release firmada (la que repartes) | Actualizaciones reales |
| `pruebas` | Builds de depuración de Android Studio | Probar el flujo sin afectar a nadie |

Una actualización solo se instala si está **firmada con la misma llave** que la app instalada. Por eso:

- Las builds de depuración (cada computadora tiene su propia llave) usan el canal `pruebas`.
- Lo que repartas tiene que salir siempre de la misma llave de release.

## 1. Crear la llave de firma (una sola vez)

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore monzatti-release.jks -alias monzatti -keyalg RSA -keysize 2048 -validity 10000
```

Guarda `monzatti-release.jks` y sus contraseñas en un lugar seguro, fuera de git.
**Si la pierdes, las apps instaladas ya no podrán actualizarse**: habría que desinstalarlas y volver a instalar.

Agrega a `local.properties` (este archivo no se sube a git):

```properties
RELEASE_STORE_FILE=monzatti-release.jks
RELEASE_STORE_PASSWORD=tu-contraseña
RELEASE_KEY_ALIAS=monzatti
RELEASE_KEY_PASSWORD=tu-contraseña
```

## 2. Publicar una versión

1. En `gradle.properties`, sube la versión: `monzatti.versionCode` +1 y el `monzatti.versionName` nuevo (por ejemplo 1.1.0).
2. Ejecuta:

   ```powershell
   .\scripts\publicar-version.ps1 -Notas "• Carrito más rápido`n• Correcciones del espejo"
   ```

   Agrega `-Obligatoria` si nadie debe seguir con la versión anterior. El script compila la APK firmada, la deja en
   `dist\` y genera el SQL con su huella.
3. En Supabase, **Storage → releases**, sube `dist\monzatti-<versión>.apk`.
4. En Supabase, **SQL Editor**, ejecuta `dist\publicar-<versión>.sql`.

Listo: las apps instaladas verán el aviso la próxima vez que se abran.

- **Despublicar una versión:** `update app_releases set published = false where version_code = …;`
- **Tamaño:** la APK de release incluye solo ARM de 64 bits (todos los celulares actuales) y pesa unos 35 MB.
  El plan gratuito de Supabase acepta hasta 50 MB por archivo.
- **Otro alojamiento:** `apk_url` puede ser cualquier enlace `https` de descarga directa, por ejemplo GitHub Releases
  si el repositorio es público.

## La primera instalación

La primera vez la APK se instala a mano: pásala por WhatsApp, Drive o cable y ábrela en el celular.
Android pedirá permitir instalar desde esa app. De ahí en adelante, Monzatti se actualiza sola.
