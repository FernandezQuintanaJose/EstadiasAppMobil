"""Envío de los códigos de verificación por correo (decisión 0020).

Dos modos, según CORREO_MODO:
- "consola" (por defecto): no envía nada; escribe el código en el registro del servidor. Sirve para probar en tu
  computadora y para una demostración, nunca con personas reales: quien lea el registro ve los códigos.
- "smtp": lo envía por SMTP con STARTTLS. Funciona con cualquier proveedor que dé SMTP (Gmail con contraseña de
  aplicación, Brevo, Resend). Variables: CORREO_SMTP_HOST, CORREO_SMTP_PUERTO (587), CORREO_SMTP_USUARIO,
  CORREO_SMTP_CONTRASENA y CORREO_REMITENTE. La contraseña va solo en el entorno (Render), nunca en el código.

Si el envío falla, la ruta responde igual que si hubiera salido (para no revelar qué correos existen) y el error
queda en el registro, sin el código.
"""

import logging
import os
import smtplib
import ssl
from email.message import EmailMessage

log = logging.getLogger("cecapi.correo")

ASUNTOS = {
    "registro": "Tu código para crear tu cuenta de CECAPI",
    "recuperar": "Tu código para cambiar tu contraseña de CECAPI",
}
QUE_HACE = {
    "registro": "terminar de crear tu cuenta del asistente de CECAPI",
    "recuperar": "cambiar la contraseña de tu cuenta del asistente de CECAPI",
}


def modo() -> str:
    valor = os.getenv("CORREO_MODO", "consola").strip().lower()
    return valor if valor in ("consola", "smtp") else "consola"


def _enmascarar(correo: str) -> str:
    """juan.perez@example.com -> ju***@example.com, para que el registro no guarde el correo completo."""
    nombre, _, dominio = correo.partition("@")
    return f"{nombre[:2]}***@{dominio}"


def mensaje(correo: str, codigo: str, proposito: str, minutos: int) -> EmailMessage:
    """El correo en texto simple: se lee bien con lector de pantalla y no lleva enlaces que alguien pueda falsear."""
    m = EmailMessage()
    m["Subject"] = ASUNTOS[proposito]
    nombre = os.getenv("CORREO_NOMBRE", "Asistente CECAPI").strip() or "Asistente CECAPI"
    m["From"] = f"{nombre} <{os.getenv('CORREO_REMITENTE', '').strip()}>"
    m["To"] = correo
    digitos = " ".join(codigo)
    m.set_content(
        f"Tu código es: {codigo}\n"
        f"Dígito por dígito: {digitos}\n\n"
        f"Escríbelo o díctalo en la aplicación para {QUE_HACE[proposito]}. Vence en {minutos} minutos.\n\n"
        "Si tú no lo pediste, ignora este correo: nadie puede entrar a tu cuenta sin este código.\n"
    )
    return m


def enviar_codigo(correo: str, codigo: str, proposito: str, minutos: int) -> bool:
    """Manda el código. Regresa False si no se pudo; quien llama responde igual a la persona."""
    if modo() == "consola":
        log.warning("CORREO_MODO=consola: código de %s para %s: %s (no se envió ningún correo)",
                    proposito, _enmascarar(correo), codigo)
        return True
    host = os.getenv("CORREO_SMTP_HOST", "").strip()
    usuario = os.getenv("CORREO_SMTP_USUARIO", "").strip()
    contrasena = os.getenv("CORREO_SMTP_CONTRASENA", "")
    if not (host and usuario and contrasena and os.getenv("CORREO_REMITENTE", "").strip()):
        log.error("CORREO_MODO=smtp pero falta CORREO_SMTP_HOST, CORREO_SMTP_USUARIO, CORREO_SMTP_CONTRASENA "
                  "o CORREO_REMITENTE: no se envió el código a %s.", _enmascarar(correo))
        return False
    try:
        puerto = int(os.getenv("CORREO_SMTP_PUERTO", "587"))
    except ValueError:
        puerto = 587
    try:
        with smtplib.SMTP(host, puerto, timeout=20) as servidor:
            servidor.starttls(context=ssl.create_default_context())
            servidor.login(usuario, contrasena)
            servidor.send_message(mensaje(correo, codigo, proposito, minutos))
        log.info("Código de %s enviado a %s.", proposito, _enmascarar(correo))
        return True
    except (smtplib.SMTPException, OSError) as error:
        log.error("No se pudo enviar el código de %s a %s: %s", proposito, _enmascarar(correo), type(error).__name__)
        return False
