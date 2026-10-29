"""
Prepara la foto que manda la app antes de pasarla a la IA de visión (Módulo 3).

Las fotos de celular pesan varios MB y la IA no necesita tanto detalle para
reconocer un billete u objetos: mandarlas así hace lenta la respuesta y gasta
más cuota. preparar() la endereza, la reduce y la vuelve JPEG sin metadatos, y
devuelve (bytes, tipo_mime), que es lo que recibe proveedores.ver_con_respaldo().
Si la foto no sirve lanza ImagenInvalida, que /ver convierte en un HTTP 400.
"""

import io
import os

from PIL import Image, ImageOps, UnidentifiedImageError

# Lado más largo, en píxeles. 1024 deja legibles los números de un billete.
LADO_MAX = int(os.getenv("IMAGEN_LADO_MAX", "1024"))
CALIDAD_JPEG = int(os.getenv("IMAGEN_CALIDAD_JPEG", "80"))
# Lo que se acepta de la app, antes de comprimir. Más que esto ya no es una foto normal.
MAX_BYTES_ENTRADA = int(os.getenv("IMAGEN_MAX_MB", "10")) * 1024 * 1024


class ImagenInvalida(Exception):
    """La foto no se pudo leer o es demasiado grande. El mensaje se puede decir en voz alta."""


def preparar(foto: bytes) -> tuple[bytes, str]:
    """Devuelve la foto enderezada, reducida y en JPEG, con su tipo MIME."""
    if not foto:
        raise ImagenInvalida("No llegó ninguna foto.")
    if len(foto) > MAX_BYTES_ENTRADA:
        raise ImagenInvalida("La foto es demasiado grande.")
    try:
        imagen = Image.open(io.BytesIO(foto))
        # Los celulares guardan la rotación en el EXIF en vez de girar los píxeles;
        # sin esto la IA puede recibir el billete de lado o de cabeza.
        imagen = ImageOps.exif_transpose(imagen)
        imagen.thumbnail((LADO_MAX, LADO_MAX))
        # JPEG no admite transparencia (PNG) ni paletas: se pasa a RGB.
        imagen = imagen.convert("RGB")
        # Se copian solo los píxeles a una imagen nueva: así no sale el EXIF original,
        # que puede traer GPS, modelo del teléfono y la fecha.
        limpia = Image.new("RGB", imagen.size)
        limpia.paste(imagen)
        salida = io.BytesIO()
        limpia.save(salida, format="JPEG", quality=CALIDAD_JPEG, optimize=True)
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError):
        raise ImagenInvalida("No pude leer la foto. Intenta tomarla otra vez.")
    return salida.getvalue(), "image/jpeg"
