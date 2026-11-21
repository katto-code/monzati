# Guía de modelado 3D para Monzatti

Para que cualquier prenda que modele el equipo funcione a la primera en el **espejo 3D** (`mirror/`) y más adelante en
el avatar de la app. Hoy el espejo usa prendas generadas por código; estas reglas permiten cambiarlas por modelos reales.

## 1. Avatar base

- **Herramienta:** MakeHuman (gratuito; lo que exporta es de licencia libre) + Blender 4.x.
- **Dos avatares:** femenino y masculino, en **pose A** (brazos a ~45°), de pie, mirando a **+Y en Blender**
  (al frente de la cámara en glTF, **+Z**).
- **Escala real:** 1 unidad = 1 metro. Altura de referencia 1.68 m.
- **Origen:** entre los pies, en el suelo.
- **Shape keys** (deformaciones) con estos nombres exactos, de 0 a 1:

| Shape key | Qué cambia | Rango aproximado |
|---|---|---|
| `altura` | estatura | 1.50 → 1.90 m |
| `busto` | busto / pecho | 80 → 115 cm |
| `cintura` | cintura | 60 → 100 cm |
| `cadera` | cadera | 85 → 120 cm |

## 2. Esqueleto común (obligatorio)

Todas las prendas usan **el mismo esqueleto** que el avatar, con estos nombres de huesos:

```
hips → spine → chest → neck → head
chest → shoulder.L → upper_arm.L → forearm.L → hand.L
chest → shoulder.R → upper_arm.R → forearm.R → hand.R
hips → thigh.L → shin.L → foot.L
hips → thigh.R → shin.R → foot.R
```

El espejo mueve `chest` (giro del torso), `upper_arm.*` y `forearm.*` (mangas) y `thigh.*` (faldas, pantalones)
a partir de los puntos que da MediaPipe.

## 3. Prendas (10 plantillas)

Se modela **una plantilla por tipo**, no una por producto: el color y la textura se cambian desde la app.

| # | Plantilla | Tipo en la app | Categoría IA |
|---|---|---|---|
| 1 | Blusa manga corta | BLOUSE | upper |
| 2 | Blusa manga larga | BLOUSE | upper |
| 3 | Playera oversize | BLOUSE | upper |
| 4 | Blazer | JACKET | upper |
| 5 | Chamarra bomber | JACKET | upper |
| 6 | Vestido midi | DRESS | overall |
| 7 | Vestido corto | DRESS | overall |
| 8 | Pantalón recto | (futuro) | lower |
| 9 | Falda | (futuro) | lower |
| 10 | Hoodie | JACKET | upper |

Reglas:

- Modelar **sobre el avatar base**, 3–8 mm separado de la piel para que no atraviese.
- **Pesos de piel (skinning):** transferir del avatar con *Data Transfer* → *Vertex Groups*; revisar hombros y codos.
- **Shape keys** de la prenda con los mismos nombres que el avatar (`altura`, `busto`, `cintura`, `cadera`), usando
  *Surface Deform* para que se ajuste a las medidas.
- **Un solo material** llamado `Tela` (la app cambia su color base). Opcional: textura de patrón en *Base Color*
  y mapa normal para arrugas.
- **Polígonos:** máximo **15 000 triángulos** por prenda (rinde 30 FPS en gama media).
- **Texturas:** 1024 × 1024 máximo, en `.webp` o `.jpg` dentro del `.glb`.

## 4. Exportar

En Blender: *File → Export → glTF 2.0*

- Formato: **glTF Binary (.glb)**
- Incluir: *Selected Objects*, *Skinning*, *Shape Keys*
- *+Y Up* activado
- Comprimir con **Draco** (opcional, reduce el peso)
- Nombre: `prenda-<plantilla>.glb` (ej. `prenda-blazer.glb`); peso objetivo **< 2 MB**

## 5. Probar

1. Copiar el `.glb` a `mirror/models/`.
2. Abrir `https://gltf-viewer.donmccurdy.com/` y arrastrar el archivo: debe verse con el esqueleto y las
   deformaciones.
3. Avisar para conectarlo en `mirror/garments.js` (se carga con `GLTFLoader` y se mueve con los mismos puntos
   del cuerpo que las prendas actuales).

## 6. Reparto sugerido (2 plantillas por persona)

| Persona | Plantillas |
|---|---|
| Angel | 4 Blazer · 5 Bomber |
| Alejandro | 8 Pantalón · 9 Falda |
| Mariana | 6 Vestido midi · 7 Vestido corto + avatar base |
| Max | 3 Playera · 10 Hoodie |
| Hanny | 1 Blusa corta · 2 Blusa larga |
