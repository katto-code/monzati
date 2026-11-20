"""Backend con CatVTON (modelo abierto de prueba virtual de ropa basado en Stable Diffusion inpainting).

Requiere el repositorio de CatVTON clonado en ./CatVTON (lo hace install.ps1 / install.sh) y una tarjeta NVIDIA
con ~8 GB de VRAM o más. Los pesos se descargan de Hugging Face la primera vez (varios GB).

Licencia: CatVTON se publica para uso no comercial / académico. Sirve para la titulación; revisar antes de
cualquier uso comercial de Monzatti.

Esta integración sigue el app.py del repositorio de CatVTON. Si una versión nueva del repositorio cambia nombres,
ajusta solo este archivo.
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.environ.get("CATVTON_REPO", os.path.join(HERE, "..", "CatVTON"))
sys.path.insert(0, os.path.abspath(REPO))

WIDTH, HEIGHT = 768, 1024


class CatVTONBackend:
    def __init__(self, steps: int = 30):
        import torch
        from diffusers.image_processor import VaeImageProcessor
        from huggingface_hub import snapshot_download
        from model.cloth_masker import AutoMasker
        from model.pipeline import CatVTONPipeline
        from utils import init_weight_dtype, resize_and_crop, resize_and_padding

        if not torch.cuda.is_available():
            raise RuntimeError("No se detectó una tarjeta NVIDIA con CUDA. Usa MONZATTI_BACKEND=mock para probar.")

        self.torch = torch
        self.steps = steps
        self.resize_and_crop = resize_and_crop
        self.resize_and_padding = resize_and_padding

        repo_path = snapshot_download(repo_id="zhengchong/CatVTON")
        self.pipeline = CatVTONPipeline(
            base_ckpt="booksforcharlie/stable-diffusion-inpainting",
            attn_ckpt=repo_path,
            attn_ckpt_version="mix",
            weight_dtype=init_weight_dtype("bf16" if torch.cuda.is_bf16_supported() else "fp16"),
            use_tf32=True,
            device="cuda",
        )
        self.mask_processor = VaeImageProcessor(
            vae_scale_factor=8, do_normalize=False, do_binarize=True, do_convert_grayscale=True
        )
        self.automasker = AutoMasker(
            densepose_ckpt=os.path.join(repo_path, "DensePose"),
            schp_ckpt=os.path.join(repo_path, "SCHP"),
            device="cuda",
        )

    def generate(self, person: Image.Image, garment: Image.Image, category: str) -> Image.Image:
        person = self.resize_and_crop(person, (WIDTH, HEIGHT))
        garment = self.resize_and_padding(garment, (WIDTH, HEIGHT))
        mask = self.automasker(person, category)["mask"]
        mask = self.mask_processor.blur(mask, blur_factor=9)
        generator = self.torch.Generator(device="cuda").manual_seed(42)
        result = self.pipeline(
            image=person,
            condition_image=garment,
            mask=mask,
            num_inference_steps=self.steps,
            guidance_scale=2.5,
            generator=generator,
        )[0]
        return result
