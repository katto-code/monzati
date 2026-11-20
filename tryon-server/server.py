"""
Monzatti · servidor de fotos con IA (prueba virtual de ropa).

Recibe la foto de la persona y la de la prenda, y devuelve una imagen realista de la persona con la prenda puesta.
Corre en una PC con tarjeta NVIDIA (CatVTON) o en modo "mock" para probar la app sin tarjeta gráfica.

    python server.py                      # usa MONZATTI_BACKEND (catvton por defecto)
    MONZATTI_BACKEND=mock python server.py

Variables de entorno:
    MONZATTI_TOKEN    clave que la app manda en el encabezado X-Monzatti-Token (obligatoria)
    MONZATTI_BACKEND  catvton | mock
    MONZATTI_PORT     puerto (8787)
    MONZATTI_STEPS    pasos de difusión para CatVTON (30 = rápido, 50 = mejor calidad)

Privacidad: las imágenes solo viven en memoria durante la petición; no se guardan ni se registran.
"""
from __future__ import annotations

import hmac
import io
import os
import threading
import time

import uvicorn
from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import JSONResponse, Response
from PIL import Image, ImageOps

TOKEN = os.environ.get("MONZATTI_TOKEN", "")
BACKEND_NAME = os.environ.get("MONZATTI_BACKEND", "catvton").lower()
PORT = int(os.environ.get("MONZATTI_PORT", "8787"))
MAX_BYTES = 8 * 1024 * 1024
CATEGORIES = {"upper", "lower", "overall"}

app = FastAPI(title="Monzatti Try-On", version="1.0")

# Una sola generación a la vez: la tarjeta gráfica no se comparte bien entre peticiones.
_gpu_lock = threading.Lock()
_backend = None
_backend_error: str | None = None


def _load_backend():
    global _backend, _backend_error
    try:
        if BACKEND_NAME == "mock":
            from backends.mock import MockBackend
            _backend = MockBackend()
        else:
            from backends.catvton import CatVTONBackend
            _backend = CatVTONBackend(steps=int(os.environ.get("MONZATTI_STEPS", "30")))
    except Exception as e:  # noqa: BLE001 - se informa en /health
        _backend_error = f"{type(e).__name__}: {e}"


def _check_token(token: str | None):
    if not TOKEN:
        raise HTTPException(500, "El servidor no tiene MONZATTI_TOKEN configurado.")
    if not token or not hmac.compare_digest(token, TOKEN):
        raise HTTPException(401, "Clave inválida.")


async def _read_image(upload: UploadFile, name: str) -> Image.Image:
    data = await upload.read()
    if len(data) > MAX_BYTES:
        raise HTTPException(413, f"La imagen {name} pesa más de 8 MB.")
    try:
        img = Image.open(io.BytesIO(data))
        img = ImageOps.exif_transpose(img)
        if img.mode in ("RGBA", "LA", "P"):
            # Prendas con fondo transparente: se ponen sobre blanco.
            rgba = img.convert("RGBA")
            white = Image.new("RGBA", rgba.size, (255, 255, 255, 255))
            img = Image.alpha_composite(white, rgba)
        return img.convert("RGB")
    except Exception as e:  # noqa: BLE001
        raise HTTPException(400, f"No se pudo leer la imagen {name}.") from e


@app.get("/health")
def health():
    return {
        "status": "ok" if _backend is not None else "error",
        "backend": BACKEND_NAME,
        "busy": _gpu_lock.locked(),
        "error": _backend_error,
    }


@app.post("/tryon")
async def tryon(
    person: UploadFile = File(...),
    garment: UploadFile = File(...),
    category: str = Form("upper"),
    x_monzatti_token: str | None = Header(default=None),
):
    _check_token(x_monzatti_token)
    if _backend is None:
        raise HTTPException(503, f"El modelo no está listo: {_backend_error or 'cargando'}")
    if category not in CATEGORIES:
        raise HTTPException(400, "category debe ser upper, lower u overall.")

    person_img = await _read_image(person, "de la persona")
    garment_img = await _read_image(garment, "de la prenda")

    if not _gpu_lock.acquire(timeout=90):
        raise HTTPException(429, "El servidor está ocupado. Intenta en unos segundos.")
    try:
        started = time.time()
        result = _backend.generate(person_img, garment_img, category)
        elapsed = time.time() - started
    except Exception as e:  # noqa: BLE001
        return JSONResponse(status_code=500, content={"detail": f"Falló la generación: {type(e).__name__}"})
    finally:
        _gpu_lock.release()

    out = io.BytesIO()
    result.save(out, format="JPEG", quality=92)
    return Response(
        content=out.getvalue(),
        media_type="image/jpeg",
        headers={"X-Monzatti-Seconds": f"{elapsed:.1f}"},
    )


if __name__ == "__main__":
    if not TOKEN:
        raise SystemExit("Define MONZATTI_TOKEN antes de arrancar (la misma clave que pondrás en la app).")
    print(f"Cargando backend '{BACKEND_NAME}'…")
    _load_backend()
    if _backend_error:
        print("ERROR al cargar el modelo:", _backend_error)
    uvicorn.run(app, host="0.0.0.0", port=PORT)
