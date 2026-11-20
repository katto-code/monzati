# Monzatti · instalación del servidor de fotos con IA en la PC con tarjeta NVIDIA (Windows).
# Ejecuta desde la carpeta tryon-server:   powershell -ExecutionPolicy Bypass -File install.ps1
# Requisitos previos: Python 3.10 u 3.11, Git y el driver de NVIDIA actualizado.

$ErrorActionPreference = "Stop"

Write-Host "1/4 Creando entorno virtual (.venv)..."
python -m venv .venv
& .\.venv\Scripts\python.exe -m pip install --upgrade pip

Write-Host "2/4 Instalando PyTorch con soporte CUDA 12.1..."
& .\.venv\Scripts\pip.exe install torch==2.4.1 torchvision==0.19.1 --index-url https://download.pytorch.org/whl/cu121

Write-Host "3/4 Descargando CatVTON y sus dependencias..."
if (-not (Test-Path CatVTON)) {
    git clone https://github.com/Zheng-Chong/CatVTON.git
}
& .\.venv\Scripts\pip.exe install -r CatVTON\requirements.txt
& .\.venv\Scripts\pip.exe install -r requirements.txt

Write-Host "4/4 Comprobando la tarjeta gráfica..."
& .\.venv\Scripts\python.exe -c "import torch; print('CUDA disponible:', torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'Sin GPU')"

Write-Host ""
Write-Host "Listo. Para arrancar el servidor:"
Write-Host '  $env:MONZATTI_TOKEN = "una-clave-larga-que-inventes"'
Write-Host "  .\.venv\Scripts\python.exe server.py"
Write-Host "La primera vez descarga los pesos del modelo (varios GB) y tarda unos minutos."
