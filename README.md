# Monzatti para Android

Aplicación nativa para Android (Kotlin + Jetpack Compose) del proyecto Monzatti: closet virtual, asistente de estilo,
probador con medidas, tienda de marcas locales, panel para socios comerciales y espejo inteligente.

## Abrir y ejecutar

Abre la carpeta en Android Studio, espera a que Gradle sincronice y ejecuta la configuración `app` en un emulador o
dispositivo (API 26 o posterior). Desde la raíz también puedes compilar y correr las pruebas:

```powershell
.\gradlew.bat assembleDebug
```

```powershell
.\gradlew.bat testDebugUnitTest
```

Sin más configuración la app funciona en **modo demo**: usa datos de ejemplo en memoria y acepta cualquier correo.

## Arquitectura

Por capas, en un solo módulo:

| Carpeta (`com.monzatti.app`) | Qué contiene |
|---|---|
| `domain/` | Modelos, interfaces de repositorios y el cuestionario de estilo (Kotlin puro). |
| `data/` | Implementaciones: `fake/` (demo en memoria), `supabase/` (base de datos real) y `local/` (DataStore). |
| `di/` | Módulos de Hilt. Eligen Supabase si hay claves y la demo si no. |
| `core/` | Tema (paletas, Poppins) y configuración. |
| `ui/` | Navegación (Navigation Compose) y una carpeta por pantalla con su ViewModel. |

Librerías: Hilt, Navigation Compose, DataStore, Material 3 y supabase-kt. Versiones en `gradle/libs.versions.toml`.

## Conectar Supabase

1. Crea un proyecto en [supabase.com](https://supabase.com).
2. En **SQL Editor**, pega y ejecuta `supabase/migrations/20261119000000_init.sql`. Crea perfiles, marcas, productos con
   existencias por talla, closet, pedidos con comisión (10 % a 15 %), reservas de 10 minutos y sesiones del espejo en
   tiempo real, todo con seguridad por fila (RLS).
3. En **Project Settings → API**, copia la URL y la clave `anon` (publishable) y agrégalas a `local.properties`
   (este archivo no se sube a git):

   ```properties
   SUPABASE_URL=https://xxxxxxxx.supabase.co
   SUPABASE_ANON_KEY=tu-clave-anon
   ```

4. Vuelve a compilar. Con las claves puestas, el registro, el inicio de sesión, el closet, el catálogo y el estilo se
   guardan en Supabase.

Para probar sin confirmar correos, desactiva **Authentication → Providers → Email → Confirm email** mientras desarrollas.

## Estilos de la app

Al crear la cuenta se elige un estilo, directamente o con un cuestionario de 5 preguntas con imágenes:
**Coquette** (Romántico), **Gótica** (Gótico), **Streetwear** y **Old Money**. Si se omite, queda **Monzatti**, el
predeterminado. Se puede cambiar después desde el closet o el panel de socio.

### Imágenes del cuestionario

Van en `app/src/main/res/drawable-nodpi/`, cuadradas (~800 px), en `.webp` o `.jpg`, con este nombre:

```
quiz_p<pregunta>_<f|m|x>_<coquette|gotica|streetwear|oldmoney>.webp
```

`f` = femenino, `m` = masculino, `x` = compartida. Se necesitan 28: preguntas 1, 3 y 4 para cada género y la
pregunta 2 compartida. La pregunta 5 la dibuja la app. Mientras falte una imagen, se muestra una tarjeta con el
color del estilo.

## Probador con cámara

En **Medidas › Probar con cámara**, en el detalle de un producto o en el closet («Probármela»). La cámara frontal
funciona como espejo: ML Kit Pose detecta hombros, codos, muñecas, cadera y rodillas en el propio celular, y la prenda
(blusa, chamarra o vestido) se dibuja sobre el cuerpo siguiendo el movimiento. Desliza de lado para cambiar de prenda.
«Al espejo» manda la prenda al espejo inteligente (tabla `mirror_sessions` en Supabase). El indicador de fps sirve
para medir el KPI de rendimiento. Para la demo: buena luz, de frente, a 1–2 m, con hombros y cadera a la vista.

Las prendas se dibujan como vectores con su color. Cuando exista el recorte de fondo de las fotos, se pueden dibujar
las fotos reales con la misma geometría (`ui/feature/tryon/GarmentPainter.kt`).

## Qué sigue simulado

Pagos y checkout, el asistente de IA, el probador y el espejo. La base de datos ya tiene las tablas y reglas para
conectarlos.
