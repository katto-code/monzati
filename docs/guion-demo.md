# Guion de la demo (15 minutos)

## Antes de empezar (checklist)

- [ ] Celular con la app instalada (build de depuración), batería > 80 %, modo no molestar.
- [ ] Laptop con el espejo abierto: `python -m http.server 5178 --directory mirror` → `http://localhost:5178`,
      pantalla completa (**F**), conectada al proyector.
- [ ] Luz de frente sobre la persona que posa; fondo despejado; marca en el piso a 1.5–2 m de la webcam.
- [ ] Si hay Supabase: probar que «Al espejo» cambia la prenda en la laptop (abajo dice «● Vinculado a la app»).
- [ ] Una prenda real a la mano (para digitalizar) extendida sobre una mesa lisa.
- [ ] Planes B listos: `?demo` en el espejo, modo demo en la app, prototipo HTML, video grabado.

## Recorrido

| Min | Quién | Qué se muestra | Qué decir |
|---|---|---|---|
| 0–1 | Hanny | Abrir la app (splash con el monograma) | «Vuelve a ti»: el problema del clóset olvidado |
| 1–3 | Mariana | Crear cuenta → género → **cuestionario de estilo** → resultado → la app cambia de colores | Personalización desde el primer minuto; 4 estilos + Monzatti |
| 3–5 | Max | Closet → **Nueva prenda** → apuntar a la prenda real → captura automática → fondo recortado, categoría y color sugeridos → guardar | Todo corre en el celular, la foto no sale del dispositivo |
| 5–7 | Max | **Probador con cámara**: la prenda sigue el cuerpo; deslizar para cambiar; FPS arriba | ML Kit Pose; KPI de rendimiento |
| 7–10 | Angel | **Espejo 3D** en el proyector: tocar «Al espejo» en el celular → la prenda 3D aparece; girar despacio | Tiempo real (RF-02), MediaPipe + Three.js, sin hardware especial |
| 10–12 | Alejandro | Tienda → producto → talla agotada → **carrito** → pago de prueba con **reserva de 10 min** → pedido confirmado | RF-01, RN-02, pagos en modo prueba |
| 12–14 | Alejandro | Cerrar sesión → **Socio comercial** → ventas con comisión 12 % → alta de producto → inventario | RN-01, panel del empresario |
| 14–15 | Todos | Preguntas | — |

## Si algo falla

| Falla | Qué hacer |
|---|---|
| No hay internet / Supabase | En el acceso: «Usar modo demo (sin conexión)» |
| El espejo no detecta a la persona | Acercarse a la luz; si sigue, tecla **D** (maniquí 360°) |
| La app se cierra | Reabrir: el estilo y el modo demo se recuerdan |
