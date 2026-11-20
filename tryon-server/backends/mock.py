"""Backend de prueba sin tarjeta gráfica: pega la prenda sobre el pecho de la persona.

No es realista; sirve para probar la app de punta a punta (envío, espera, respuesta) en cualquier computadora.
"""
from PIL import Image, ImageDraw, ImageOps


class MockBackend:
    def generate(self, person: Image.Image, garment: Image.Image, category: str) -> Image.Image:
        canvas = ImageOps.fit(person, (768, 1024)).copy()
        g = garment.copy()
        # Aproxima el fondo blanco de la prenda como transparente.
        g = g.convert("RGBA")
        px = g.load()
        for y in range(g.height):
            for x in range(g.width):
                r, gg, b, a = px[x, y]
                if r > 235 and gg > 235 and b > 235:
                    px[x, y] = (r, gg, b, 0)
        target_w = int(canvas.width * (0.62 if category == "upper" else 0.55))
        target_h = int(g.height * target_w / g.width)
        g = g.resize((target_w, target_h))
        top = int(canvas.height * (0.28 if category != "lower" else 0.52))
        canvas.paste(g, ((canvas.width - target_w) // 2, top), g)
        draw = ImageDraw.Draw(canvas)
        draw.rectangle([0, 0, canvas.width, 44], fill=(17, 17, 17))
        draw.text((16, 14), "MONZATTI · modo mock (sin IA)", fill=(201, 162, 39))
        return canvas
