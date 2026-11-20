# Monzatti: arquitectura por capas, Supabase y estilos personalizados

Fecha: 2026-11-19 · Fuentes: prototipo actual, `uml.pdf` (Ecosistema Monzatti, navegación por tipo de cuenta) y
`s0-9-10.docx` (S0-09 requisitos y reglas de negocio, S0-10 plan de pruebas y DoD).

## Intención

Monzatti es el prototipo Android (Kotlin + Jetpack Compose) del proyecto de titulación UTTEC. Hoy vive todo en un
solo `MainActivity.kt` con datos en memoria y colores fijos. Queremos:

1. Un "stack pesado": arquitectura por capas profesional, lista para crecer.
2. Dejar la app preparada para Supabase (base de datos y autenticación), sin depender de ello para funcionar, con un
   esquema que ya refleje los requisitos y reglas de negocio de S0-09.
3. Personalizar los colores de la app según el estilo de cada persona: 4 estilos + 1 predeterminado, elegidos al crear
   la cuenta, directamente o mediante un cuestionario con imágenes.
4. Tipografía Poppins en toda la app.

## Objetivos

- Separar `ui/`, `domain/` y `data/`; cada pantalla con su ViewModel.
- Navegación real con Navigation Compose, siguiendo el diagrama del UML.
- Inyección de dependencias con Hilt.
- Repositorios con dos implementaciones: de ejemplo (en memoria) y Supabase. Se elige automáticamente según si hay
  claves de Supabase configuradas.
- Esquema SQL listo para pegar en Supabase.
- Flujo de estilo al crear cuenta, con dos caminos que llegan al mismo resultado.
- Tema dinámico: ninguna pantalla usa colores fijos; todas leen del tema activo.
- Poppins como fuente de toda la app.

## No objetivos

- Base de datos local / modo sin conexión (Room).
- Varios módulos de Gradle.
- Pagos reales, checkout, IA real en el asistente, probador o espejo reales: siguen siendo simulados. El esquema sí
  los deja modelados (pedidos, comisión, reservas, sesión del espejo) para conectarlos después.
- Interfaz de administrador (el rol existe en la base de datos, sin pantallas).
- Subida de fotos de prendas a Supabase Storage.

## Anti-objetivos

- Que la app deje de compilar o arrancar si no hay claves de Supabase.
- Perder alguna pantalla o comportamiento que ya existe en el prototipo.
- Paletas con texto ilegible (contraste insuficiente; S0-10 lo exige en la DoD).
- Que falte una imagen del cuestionario y la pantalla se rompa.

## Enfoques considerados

- **A. Por capas en un solo módulo (elegido por el usuario).** Hilt + Navigation + DataStore + supabase-kt. Estándar
  actual de Android, robusto sin complicar la compilación.
- B. A + Room para modo sin conexión. Descartado: duplica la capa de datos sin necesidad para el prototipo.
- C. Multimódulo (`:core`, `:data`, `:feature-*`). Descartado: configuración frágil y lenta para este tamaño.

## Diseño

### Estructura de paquetes (`com.monzatti.app`)

```
MonzattiApplication.kt          @HiltAndroidApp
MainActivity.kt                 @AndroidEntryPoint; aplica MonzattiTheme con el estilo activo
core/theme/                     StylePalette, MonzattiColors, MonzattiTheme, tipografía Poppins
core/config/                    SupabaseConfig (lee BuildConfig)
domain/model/                   Garment, Product, ChatMessage, UserRole, Gender, UserProfile, Measurements
domain/repository/              interfaces: AuthRepository, ProfileRepository, ClosetRepository,
                                ProductRepository, StyleRepository
domain/style/                   StyleQuiz (preguntas + cálculo del resultado, Kotlin puro)
data/fake/                      implementaciones en memoria con los datos de ejemplo actuales
data/supabase/                  implementaciones con supabase-kt
data/local/                     StylePreferences (DataStore)
di/                             módulos Hilt (elige fake o supabase según SupabaseConfig.isConfigured)
ui/navigation/                  rutas y NavHost
ui/components/                  Brand, Title, PrimaryButton, ChoiceChip, InfoCard, etc. (leyendo del tema)
ui/feature/<pantalla>/          Screen + ViewModel por pantalla
```

### Navegación (según el UML)

```
Apertura ─▶ 1. Login / Autenticación (crear cuenta, recuperar, conoce el proyecto)
            └─ ¿Tipo de cuenta?
               ├─ Usuario final ─▶ Menú principal (barra inferior)
               │                   ├─ 2. Closet virtual ─▶ 3. Fotos / escaneo de ropa (nueva prenda)
               │                   ├─ 6. Tienda / marketplace ─▶ detalle de producto
               │                   ├─ 4. Asistente de IA
               │                   └─ 5. Proyección y medidas (probador) ─▶ 8. Espejo inteligente
               └─ Socio comercial ─▶ 7. App para empresarios (barra inferior)
                                   ├─ Panel de ventas (el resumen actual)
                                   ├─ Alta de productos
                                   └─ Gestión de inventario (nueva: lista con existencias y aviso de "bajo")
```

Decisión mía: el espejo deja de ser pestaña y se abre desde "Proyección y medidas", como en el UML. "Salir" pasa al
encabezado del panel de socio.

### Supabase

- Claves en `local.properties` (no se versiona): `SUPABASE_URL` y `SUPABASE_ANON_KEY`, expuestas como `BuildConfig`.
- Si faltan, `SupabaseConfig.isConfigured = false` y Hilt entrega las implementaciones de ejemplo. La app funciona
  igual que hoy.
- Si están, se usa `supabase-kt` (Auth con correo y contraseña + Postgrest).
- `supabase/migrations/20261119000000_init.sql`:
  - `profiles`: id = `auth.users.id`, nombre, rol (`usuario`, `socio`, `admin`), género (`femenino`, `masculino`),
    `style_palette` (5 valores), medidas (altura, busto/pecho, cintura, cadera) y tono de piel (RF-03). Trigger que
    crea el perfil al registrarse.
  - `brands`: marca aliada (dueño, nombre, `verified` que valida el admin).
  - `products`: marca, nombre, categoría, color, precio MXN, activo (RF-01).
  - `product_variants`: producto + talla + existencias (`stock >= 0`). RF-01: no se puede comprar con existencias 0.
  - `garments`: prendas del closet del usuario (nombre, categoría, color, talla, imagen, favorito).
  - `orders` y `order_items`: con `commission_rate` restringida a 0.10–0.15 sobre el importe neto (RN-01).
  - `inventory_reservations`: reserva por variante con `expires_at = now() + 10 min` y función que libera las vencidas
    (RN-02).
  - `mirror_sessions`: atuendo proyectado por usuario, publicado en Supabase Realtime para el espejo (RF-02).
  - RLS activado en todas: cada quien lee y escribe lo suyo; los productos activos son legibles por cualquier usuario
    autenticado; solo el dueño de la marca edita sus productos y existencias; el admin puede todo.
- RN-03 (70 % closet / 30 % marcas) queda documentada como regla del motor de recomendaciones, que sigue simulado.
- El README explica cómo crear el proyecto, correr el SQL y poner las claves.

### Tipografía

Poppins (Google Fonts, licencia OFL) incluida en `res/font` con los pesos Regular, Medium, SemiBold y Bold, aplicada
a toda la `Typography` de Material 3 y a los textos propios.

### Estilos y tema

`StylePalette`: `MONZATTI` (predeterminado), `COQUETTE`, `GOTICA`, `STREETWEAR`, `OLD_MONEY`.

Cada uno define tokens propios (`background`, `surface`, `surfaceVariant` para los marcos de imagen, `ink`/texto,
`muted`, `line`, `primary`/`onPrimary` para el botón principal, `accent`/`onAccent` para el botón dorado actual y los
chips, `brand` para el logotipo) y se traduce también a un `ColorScheme` de Material 3. Se exponen con un
`CompositionLocal` (`MonzattiTheme.colors`).

| Estilo | Modo | Fondo / superficie | Principal | Acento | Notas |
|---|---|---|---|---|---|
| Monzatti | claro | `#F3F2EF` / `#FFFFFF` | `#171717` | `#C9A227` | los colores actuales |
| Coquette | claro | `#FFF8E7` / `#FFFFF0` | `#DCAE96` (texto oscuro) | `#F4C2C2` | marcos `#FADADD`, detalles `#C8AD7F`, texto café oscuro |
| Gótica | oscuro | `#0B0B0B` / `#2C2C2C` | `#4A0404` (texto `#E5E4E2`) | `#C0C0C0` | `#301934` en marcos y burbujas, texto `#E5E4E2` |
| Streetwear | claro | `#F5F5DC` / `#FFFFFF` | `#1C1C1C` | `#FF5500` | `#556B2F` y `#5B7C99` en chips y datos, gris `#8E8E8E` en líneas |
| Old Money | claro | `#FFFFFF` / `#FFFFFF` | `#1B263B` | `#C19A6B` | marcos `#D8C3A5`, `#1E3F20` en indicadores, `#D4AF37` en el logotipo, `#A9A9A9` en líneas |

Regla: si una combinación texto/fondo no llega a contraste 4.5:1, se ajusta el texto, no el color de la paleta.

Nombres visibles: para género masculino, Coquette se muestra como "Romántico" y Gótica como "Gótico" (misma paleta).

### Flujo de estilo

```
Crear cuenta (incluye "Femenino / Masculino") ──▶ "Tu app, a tu estilo"
   ├─ Elige el estilo de tu app ─▶ 4 tarjetas con vista previa ─┐
   ├─ Descubre tu estilo (cuestionario con imágenes) ───────────┤
   └─ Omitir (queda Monzatti) ──────────────────────────────────┤
                                                                ▼
                           Resultado: "Tu estilo es Gótica" + vista previa
                           [Aplicar] [Elegir otro]  ─▶ Closet (o panel de socio)
```

- El género se pregunta en el registro (se guarda en el perfil) y decide qué imágenes ve el cuestionario.
- Ambos caminos terminan en la misma pantalla de resultado y guardan lo mismo.
- El estilo se puede cambiar después con "Cambiar estilo" en el closet y en el panel de socio.
- El tema se aplica al instante en toda la app. Antes del login se usa el último estilo guardado en el dispositivo o
  Monzatti.

### Cuestionario con imágenes

5 preguntas; cada respuesta es una imagen (cuadrícula 2×2) y vale un punto para un estilo. Gana el de más puntos.
Empate: gana el elegido en la pregunta 1 si está entre los empatados; si no, el primero de los empatados en el orden
Coquette, Gótica, Streetwear, Old Money. Las opciones se muestran en orden aleatorio.

| # | Pregunta | Femenino | Masculino |
|---|---|---|---|
| 1 | ¿Con qué look te identificas más? | imagen propia (4) | imagen propia (4) |
| 2 | Tu plan ideal de fin de semana | compartidas (4) | compartidas |
| 3 | ¿Qué zapatos usarías hoy? | imagen propia (4) | imagen propia (4) |
| 4 | Elige un accesorio | imagen propia (4) | imagen propia (4) |
| 5 | ¿Qué colores dominan tu closet? | dibujada por la app con la paleta | igual |

Total: 28 imágenes que entrega el usuario, en `app/src/main/res/drawable-nodpi/`, nombre
`quiz_p<n>_<f|m|x>_<coquette|gotica|streetwear|oldmoney>.webp` (`x` = compartida), cuadradas (~800 px). Mientras falte
una, se muestra una tarjeta con el color del estilo y el texto de la opción.

### Persistencia del estilo

`StyleRepository` guarda siempre en DataStore (para el arranque) y, si hay Supabase y sesión, actualiza
`profiles.style_palette`. Al iniciar sesión con Supabase, el valor del perfil gana sobre el local.

### Pruebas (alineadas con S0-10)

- Pruebas unitarias JVM: cálculo del cuestionario (cada estilo gana, empates), contraste mínimo de cada paleta (texto
  sobre fondo, sobre superficie y sobre botones) y regla de compra con existencias 0.
- Verificación manual: compilar `assembleDebug`, arrancar en el emulador y recorrer ambos tipos de cuenta y el flujo
  de estilo con al menos dos estilos y ambos géneros.

## Decisiones dejadas al implementador

- Versiones exactas de Hilt, Navigation, DataStore y supabase-kt compatibles con AGP 8.7.3 y Kotlin 2.0.21 (o subir
  Kotlin si supabase-kt lo exige, sin romper el resto).
- Animaciones y detalles visuales menores de las nuevas pantallas, siguiendo el lenguaje visual actual.
