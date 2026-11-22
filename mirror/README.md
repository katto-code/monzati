# Monzatti Mirror (espejo 3D en tiempo real)

Página web que convierte cualquier laptop con webcam en el espejo inteligente: la cámara ve a la persona,
**MediaPipe Pose** calcula sus puntos del cuerpo en 3D y una **prenda 3D (Three.js)** la sigue y gira con ella.
La prenda se elige desde la app (botón «Al espejo») o con el teclado. No necesita tarjeta gráfica dedicada.

## Abrirlo

Necesita servirse por `http://localhost` (la cámara no funciona abriendo el archivo directo):

```powershell
python -m http.server 5178 --directory mirror
```

Luego abre `http://localhost:5178` en Chrome, permite la cámara y presiona **F** para pantalla completa.
Conecta la laptop a la TV o al proyector por HDMI.

- `http://localhost:5178/?demo` abre el **maniquí que gira 360°** sin usar la cámara (plan B de la presentación).
- Teclas: **← →** cambiar prenda · **D** alternar maniquí/cámara · **F** pantalla completa.
- El indicador de arriba muestra los FPS (KPI: 15 FPS en el espejo con pose).

## Vincularlo con la app

1. Copia `config.example.js` como `config.js` y pon la URL y la clave publicable de Supabase, más el correo y la
   contraseña de la misma cuenta que usa la app: cada persona tiene su propio canal privado (`mirror:<id>`).
2. En la app, con Supabase configurado, toca **«Al espejo»** en el probador: el espejo recibe la prenda por
   Supabase Realtime en menos de un segundo (RF-02). Abajo a la derecha dirá «● Vinculado a la app».

## Para la demo

- Buena luz de frente, fondo despejado, persona a 1.5–2 m con hombros, cadera y rodillas a la vista.
- Girar despacio: de frente y en tres cuartos se ve mejor; de espaldas la detección a veces confunde lados.
- Si algo falla, presiona **D** y sigue con el maniquí.

## Prendas 3D

Hoy las prendas se generan por código (`garments.js`). Cuando el equipo tenga modelos `.glb`
(ver `docs/guia-modelado-3d.md`), se cargan con `GLTFLoader` y se colocan con la misma base del cuerpo
(hombros, cadera y codos).
