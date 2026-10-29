"""
Protección del servidor del asistente (Módulo 3), para cuando esté en internet.

- Clave de la app: si CECAPI_CLAVE_APP tiene valor en .env, cada petición debe traer
  el encabezado X-Clave-App con esa misma clave. Si está vacía, no se pide (así sigue
  funcionando la app de hoy, que todavía no manda el encabezado).
- Límite por minuto: cada dirección IP puede hacer hasta LIMITE_POR_MINUTO peticiones
  en 60 segundos (por defecto 20). Evita que alguien gaste toda la cuota del día.

Esto no depende de FastAPI, para poder probarlo solo (test_seguridad.py). main.py lo
conecta a las rutas.
"""

import os
import secrets
import threading
import time
from collections import deque

VENTANA_S = 60.0


def clave_configurada() -> str:
    return os.getenv("CECAPI_CLAVE_APP", "").strip()


def clave_valida(enviada: str | None) -> bool:
    """True si no hay clave configurada, o si la que llegó es igual a la de .env."""
    esperada = clave_configurada()
    if not esperada:
        return True
    # compare_digest tarda lo mismo aunque falle al primer carácter: no deja adivinarla por tiempos.
    return secrets.compare_digest((enviada or "").encode(), esperada.encode())


def limite_por_minuto() -> int:
    try:
        return int(os.getenv("LIMITE_POR_MINUTO", "20"))
    except ValueError:
        return 20


class LimitadorPorMinuto:
    """Ventana deslizante: guarda la hora de las últimas peticiones de cada IP."""

    def __init__(self) -> None:
        self._peticiones: dict[str, deque] = {}
        self._candado = threading.Lock()

    def permitir(self, quien: str, ahora: float | None = None) -> bool:
        tope = limite_por_minuto()
        if tope <= 0:  # 0 = sin límite
            return True
        ahora = time.monotonic() if ahora is None else ahora
        with self._candado:
            cola = self._peticiones.setdefault(quien, deque())
            while cola and ahora - cola[0] >= VENTANA_S:
                cola.popleft()
            if len(cola) >= tope:
                return False
            cola.append(ahora)
            # Limpieza: que no crezca para siempre con IPs que ya no regresan.
            if len(self._peticiones) > 10_000:
                self._olvidar_inactivos(ahora)
            return True

    def _olvidar_inactivos(self, ahora: float) -> None:
        for ip in [ip for ip, c in self._peticiones.items() if not c or ahora - c[-1] >= VENTANA_S]:
            del self._peticiones[ip]

    def reiniciar(self) -> None:
        with self._candado:
            self._peticiones.clear()


limitador = LimitadorPorMinuto()
