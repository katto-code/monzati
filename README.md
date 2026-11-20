# Monzatti

Ecosistema de moda del proyecto de titulación UTTEC: **app Android** (closet, digitalización de prendas, probador con
cámara, fotos con IA, tienda con carrito y panel para socios), **espejo 3D en tiempo real** (web) y **servidor de
fotos con IA**.

| Carpeta | Qué es |
|---|---|
| `app/` | App Android (Kotlin, Jetpack Compose, Hilt, Supabase) |
| `mirror/` | Espejo 3D web: webcam + MediaPipe Pose + Three.js → [mirror/README.md](mirror/README.md) |
| `tryon-server/` | Servidor de fotos con IA para la PC con NVIDIA → [tryon-server/README.md](tryon-server/README.md) |
| `supabase/` | Esquema de base de datos (migraciones) y catálogo de demostración (`seed.sql`) |
| `docs/` | Diseño, [guía de modelado 3D](docs/guia-modelado-3d.md) y [guion de la demo](docs/guion-demo.md) |

## App: abrir y ejecutar

Abre la carpeta en Android Studio, espera a que Gradle sincronice y ejecuta `app` en un emulador o celular (Android 8+).

```powershell
.\gradlew.bat assembleDebug
```

```powershell
.\gradlew.bat testDebugUnitTest
```

Sin configuración, la app funciona en **modo demo** con datos de ejemplo en memoria y acepta cualquier correo.

### Qué incluye

- **Acceso y registro** con tipo de cuenta (usuario / socio) y género.
- **Estilo de la app:** 4 estilos + Monzatti, elegidos directamente o con un cuestionario con imágenes; tipografía
  Poppins y cambio de colores animado.
- **Closet** y **digitalización de prendas:** cámara con captura automática, recorte de fondo, categoría y color
  sugeridos (ML Kit en el celular).
- **Probador con cámara:** la prenda (foto real o dibujo) sigue el cuerpo en vivo; «Al espejo» la manda al espejo 3D.
- **Foto realista con IA:** consentimiento → cuenta regresiva → imagen generada por el servidor de IA.
- **Tienda:** catálogo con filtros, detalle con tallas y existencias, **carrito**, pago de prueba con reserva de 10 min,
  confirmación e historial de pedidos.
- **Socio comercial:** ventas con comisión (12 %), alta de productos e inventario por talla.
- Splash, ícono, transiciones, pantallas de carga y **modo demo** de respaldo para la presentación.

### Arquitectura

Por capas en un solo módulo: `domain/` (modelos y reglas, Kotlin puro) · `data/` (`fake/` demo, `supabase/`,
`local/`, `remote/`) · `di/` (Hilt elige Supabase o demo) · `core/` (tema y configuración) · `ui/` (una carpeta por
pantalla con su ViewModel). Versiones de librerías en `gradle/libs.versions.toml`. 29 pruebas unitarias.

## Configuración (`local.properties`, no se sube a git)

```properties
# Supabase (Project Settings → API)
SUPABASE_URL=https://xxxxxxxx.supabase.co
SUPABASE_ANON_KEY=tu-clave-publicable

# Servidor de fotos con IA (opcional)
TRYON_SERVER_URL=https://tu-servidor
TRYON_SERVER_TOKEN=la-misma-clave-del-servidor
```

## Supabase

Ejecuta, en orden, en **SQL Editor** (o con la integración de Supabase):

1. `supabase/migrations/20261119000000_init.sql` — perfiles, marcas, productos con existencias por talla, closet,
   pedidos, reservas, sesiones del espejo y RLS.
2. `supabase/migrations/20261120000000_tienda_carrito.sql` — carrito y funciones `start_checkout`, `confirm_order`,
   `brand_sales_summary`.
3. `supabase/migrations/20261120010000_storage_prendas.sql` — bucket privado de fotos de prendas.
4. `supabase/migrations/20261120020000_permisos_funciones.sql` — cierra funciones internas a la API.
5. `supabase/migrations/20261120030000_permisos_tablas.sql` — permisos de tablas (los proyectos nuevos no los dan solos).
6. `supabase/seed.sql` — catálogo de demostración (7 marcas ficticias, 8 productos).

Ya están aplicadas en el proyecto «monsati».

Para probar sin confirmar correos: **Authentication → Providers → Email → Confirm email** desactivado.
Si falla la conexión durante la presentación, en el acceso toca **«Usar modo demo (sin conexión)»**.

## Imágenes del cuestionario de estilo

En `app/src/main/res/drawable-nodpi/`, cuadradas (~800 px), nombre `quiz_p<n>_<f|m|x>_<estilo>.webp`
(`coquette`, `gotica`, `streetwear`, `oldmoney`; `x` = compartida). Son 28. Mientras falten, se muestran tarjetas de
color.

## Qué sigue simulado

El asistente de estilo (respuestas fijas), las cifras base del panel de ventas en modo demo y el cobro (modo prueba,
sin pasarela real). El avatar 3D con medidas en la app aún no existe: el 3D vive en el espejo web.
