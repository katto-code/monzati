# Servidor de fotos con IA (prueba virtual de ropa)

Genera una imagen realista de la persona con la prenda puesta. Corre en la **PC con tarjeta NVIDIA que se queda en
casa**; la app lo alcanza por internet con un túnel. Usa **CatVTON**, un modelo abierto (uso académico / no comercial).

> ⚠️ El modo `catvton` no se ha probado todavía en una PC con GPU: se escribió siguiendo el repositorio oficial.
> El modo `mock` sí se probó. Si al instalar algo no coincide, el único archivo a ajustar es `backends/catvton.py`.

## Requisitos de la PC

- Tarjeta NVIDIA con 8 GB de VRAM o más (12 GB ideal) y driver actualizado.
- Windows 10/11 con Python 3.10 u 3.11 y Git (o Linux).
- ~30 GB libres (PyTorch + pesos del modelo).

## Instalar (una vez)

```powershell
cd tryon-server
powershell -ExecutionPolicy Bypass -File install.ps1
```

## Arrancar

```powershell
$env:MONZATTI_TOKEN = "una-clave-larga-que-inventes"
.\.venv\Scripts\python.exe server.py
```

- `http://localhost:8787/health` debe responder `"status": "ok"`.
- Cada imagen tarda ~10–30 s según la tarjeta. `MONZATTI_STEPS=50` da más calidad pero tarda más.
- Para probar sin GPU: `$env:MONZATTI_BACKEND = "mock"` (pega la prenda sobre la foto, sin IA).

## Exponerlo a internet (para usarlo desde la uni)

Opción recomendada, **Tailscale** (red privada, gratis):

1. Instala Tailscale en la PC y en el celular de la demo, con la misma cuenta.
2. En la app usa la dirección de Tailscale de la PC: `http://<nombre-pc>.<tailnet>.ts.net:8787`.

Opción pública, **Cloudflare Tunnel** (no requiere cuenta para una prueba rápida):

```powershell
cloudflared tunnel --url http://localhost:8787
```

Da una dirección `https://….trycloudflare.com` que cambia cada vez que se arranca. La clave `MONZATTI_TOKEN`
impide que otras personas lo usen.

## Configurar la app

En `local.properties` (no se sube a git):

```properties
TRYON_SERVER_URL=https://tu-direccion
TRYON_SERVER_TOKEN=una-clave-larga-que-inventes
```

Vuelve a compilar. En el probador con cámara aparece **«📸 Foto con IA»**.

## API

`POST /tryon` (multipart) con encabezado `X-Monzatti-Token`:

| Campo | Tipo | Descripción |
|---|---|---|
| `person` | imagen | Foto de la persona (de frente, cuerpo visible) |
| `garment` | imagen | Foto de la prenda (fondo transparente o blanco) |
| `category` | texto | `upper` (blusas, chamarras), `lower` (pantalones) u `overall` (vestidos) |

Responde un JPEG. Errores: 401 clave inválida, 429 ocupado, 503 modelo no listo.

## Privacidad

Las fotos solo existen en memoria durante la petición: no se guardan ni se registran. La app pide consentimiento
antes de enviarlas.
