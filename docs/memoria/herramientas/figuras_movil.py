"""Figuras de la parte móvil (módulos 1 y 2, voz, cuentas y APK). Datos tomados del código en main (b8e39d8).

Uso: python figuras_movil.py   ->  /home/user/memoria/paquete_movil/figuras/
"""
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle

import figuras as F
from figuras import (AMBAR, AMBAR_CLARO, ANCHO, AZUL, AZUL_CLARO, GRIS, GRIS_CLARO, ROJO, TINTA, VERDE, VERDE_CLARO,
                     caja, circulo, flecha, guardar, lienzo, tabla_er)

SALIDA = Path(__file__).resolve().parent.parent / "figuras"
SALIDA.mkdir(parents=True, exist_ok=True)
F.SALIDA = SALIDA


def m01_capas():
    fig, ax = lienzo(5.2)
    ax.text(50, 97, "Pantalla (Jetpack Compose) → ViewModel → núcleo compartido → servicios de Android", ha="center", fontsize=7.6, color=GRIS)
    # Capa 1 pantallas
    caja(ax, 3, 78, 94, 14, "", fc=VERDE_CLARO, ec=VERDE)
    ax.text(5, 89.5, "Pantallas (Compose)", fontsize=8.2, fontweight="bold", color=VERDE)
    for i, t in enumerate(["Inicio y menú", "Inicio de sesión\ny registro", "Ajustes y\npersonalización", "Chats, gestión,\nranking, soporte", "Asistente de voz\n(módulo 2)"]):
        caja(ax, 5 + i * 18.4, 80, 16.4, 7.5, t, fc="white", ec=VERDE, tam=6.6)
    # Capa 2 ViewModels + Hilt
    caja(ax, 3, 60, 94, 13, "", fc=AZUL_CLARO, ec=AZUL)
    ax.text(5, 70.4, "ViewModels y reglas (Kotlin)", fontsize=8.2, fontweight="bold", color=AZUL)
    for i, t in enumerate(["LoginViewModel\nRegisterViewModel", "ServicioDeCuentas\n(interfaz)", "AccountRules ·\nEmailRules ·\nPasswordRules", "Comandos por pantalla\n(SessionCommands,\nAssistCommands)"]):
        caja(ax, 5 + i * 23, 61.6, 21, 7.8, t, fc="white", ec=AZUL, tam=6.3)
    # Capa 3 núcleo
    caja(ax, 3, 36, 94, 19, "", fc=AMBAR_CLARO, ec=AMBAR)
    ax.text(5, 52.2, "Núcleo compartido (core/)", fontsize=8.2, fontweight="bold", color=AMBAR)
    caja(ax, 5, 38, 28, 11, "core/voice\nVoiceEngine · VoiceText ·\nWakeWordController ·\nGlobalVoiceCommands", fc="white", ec=AMBAR, tam=6.4)
    caja(ax, 36, 38, 28, 11, "core/data\nAppDatabase (Room, v19) ·\nMigrations ·\nAccountEraser", fc="white", ec=AMBAR, tam=6.4)
    caja(ax, 67, 38, 28, 11, "core/util y di\nPasswordHasher (Argon2id) ·\nLoginAttemptsStore ·\nHilt (inyección)", fc="white", ec=AMBAR, tam=6.4)
    # Capa 4 Android
    caja(ax, 3, 6, 94, 24, "", fc=GRIS_CLARO, ec=GRIS)
    ax.text(5, 27.4, "Servicios del teléfono y almacenamiento", fontsize=8.2, fontweight="bold", color=GRIS)
    for i, t in enumerate(["SpeechRecognizer\n(voz a texto)", "TextToSpeech\n(texto a voz)", "ListeningService\n(primer plano,\nmicrófono)", "Room / SQLite\ncecapi.db", "DataStore\n(ajustes)"]):
        caja(ax, 5 + i * 18.4, 9, 16.4, 14, t, fc="white", ec=GRIS, tam=6.6)
    for x in (12, 38, 62, 85):
        flecha(ax, (x, 78), (x, 73.3), color=GRIS)
    for x in (18, 50, 80):
        flecha(ax, (x, 60), (x, 55.3), color=GRIS)
        flecha(ax, (x, 36), (x, 30.3), color=GRIS)
    guardar(fig, "m01_capas_app")


def m02_pantallas():
    fig, ax = lienzo(5.2)
    ax.text(50, 97, "Destinos de navegación (CecapiNavGraph)", ha="center", fontsize=8, color=GRIS, fontweight="bold")
    caja(ax, 38, 80, 24, 11, "Inicio\n(home)", fc=VERDE, ec=VERDE, color="white", peso="bold", tam=8.5)
    ax.text(2, 72, "Sin sesión", fontsize=7.8, fontweight="bold", color=AZUL)
    caja(ax, 2, 57, 22, 10, "Iniciar sesión\n(login)", fc=AZUL_CLARO, ec=AZUL, tam=7)
    caja(ax, 28, 57, 22, 10, "Crear cuenta\n(register)", fc=AZUL_CLARO, ec=AZUL, tam=7)
    flecha(ax, (40, 80), (13, 67.6), color=GRIS)
    flecha(ax, (45, 80), (39, 67.6), color=GRIS)
    ax.text(68, 70, "El menú de Inicio abre\nlos demás destinos.\n* pide sesión iniciada", fontsize=6.8, color=GRIS, ha="left", va="center", linespacing=1.3)
    ax.text(2, 49, "Módulo 1 · App principal", fontsize=7.8, fontweight="bold", color=VERDE)
    nombres = [("Panel\n(dashboard)", False), ("Ajustes\n(settings)", False), ("Personalización", False), ("Chats*", True), ("Gestión*", True), ("Ranking*", True), ("Editar cuenta*", True), ("Soporte", False)]
    for i, (t, ses) in enumerate(nombres):
        col, fila = i % 4, i // 4
        caja(ax, 2 + col * 24, 36 - fila * 12.5, 21, 9, t, fc="#cfe6d8" if ses else VERDE_CLARO, ec=VERDE, tam=6.8)
    ax.text(2, 20, "Módulo 2 · Asistente de voz, y destinos de otros equipos (solo se enlazan)", fontsize=7.8, fontweight="bold", color=AMBAR)
    caja(ax, 2, 5, 22, 10, "Asistente de voz\n(voice_assistant)", fc=AMBAR_CLARO, ec=AMBAR, tam=6.8)
    otros = ["IA\n(módulo 3)", "Lector\n(módulo 4)", "Solicitudes*\n(módulo 5)", "Actividades*\n(módulo 6)", "Entorno\n(módulo 7)"]
    for i, t in enumerate(otros):
        caja(ax, 28 + i * 14.4, 5, 13, 10, t, fc=GRIS_CLARO, ec=GRIS, tam=5.9)
    guardar(fig, "m02_mapa_pantallas")


def m03_flujo_voz():
    fig, ax = lienzo(5.6)
    caja(ax, 22, 91, 30, 7, "La persona habla", fc="white", ec=TINTA, tam=8)
    caja(ax, 58, 91, 38, 7, "Wake word: «hola» o el nombre elegido", fc=GRIS_CLARO, ec=GRIS, tam=7)
    flecha(ax, (52, 94.5), (58, 94.5), color=GRIS)
    pasos = [
        (78, "1. ¿Es «silencio» o «para» como frase completa?", "Cambia de modo", "Sí"),
        (63, "2. ¿La pantalla pide texto tal cual\n(usuario o contraseña)?", "Salta al paso 4", "Sí"),
        (48, "3. ¿Es un comando global?\notras formas de decirlo, pantalla y brillo,\nestado del teléfono, volumen, notificaciones", "Lo responde el comando global", "Sí"),
        (31, "4. La pantalla abierta atiende la frase\n(ViewModel de la pantalla)", None, None),
    ]
    for y, t, lado, et in pasos:
        alto = 11 if y != 48 else 13
        caja(ax, 5, y - alto / 2 + 1, 52, alto, t, fc=AZUL_CLARO, ec=AZUL, tam=6.9)
        if lado:
            caja(ax, 68, y - 4, 27, 8, lado, fc=AMBAR_CLARO, ec=AMBAR, tam=6.8)
            flecha(ax, (57, y + 1), (68, y + 1), et, color=AMBAR, off=(0, 2.6))
    flecha(ax, (31, 91), (31, 84.5), color=GRIS)
    flecha(ax, (31, 72), (31, 69.8), color=GRIS)
    flecha(ax, (31, 57), (31, 55.5), color=GRIS)
    flecha(ax, (31, 41), (31, 38.5), color=GRIS)
    caja(ax, 5, 12, 52, 12, "Resultado: la app habla la respuesta\n(TextToSpeech) y actualiza la pantalla", fc=VERDE_CLARO, ec=VERDE, tam=7)
    flecha(ax, (31, 25.5), (31, 24), color=GRIS)
    caja(ax, 68, 12, 27, 12, "Con la app en segundo\nplano solo responde\ncomandos generales;\nlo demás: «abre la app»", fc=GRIS_CLARO, ec=GRIS, tam=6.4)
    ax.text(50, 4, "Fuente: docs/explicacion/voz.md y core/voice/VoiceEngine.kt. «Silencio» y «para» no necesitan el wake word.", ha="center", fontsize=6.6, color=GRIS)
    guardar(fig, "m03_flujo_frase_voz")


def m04_modos():
    fig, ax = lienzo(3.3)
    caja(ax, 4, 52, 24, 24, "ACTIVO\nHabla y escucha", fc=VERDE_CLARO, ec=VERDE, peso="bold", tam=8.2)
    caja(ax, 38, 52, 24, 24, "SILENCIO\nCalla, pero sigue\natento a «hola»\no al nombre", fc=AMBAR_CLARO, ec=AMBAR, peso="bold", tam=7.6)
    caja(ax, 72, 52, 24, 24, "DETENIDO\nNo hace nada", fc=GRIS_CLARO, ec=GRIS, peso="bold", tam=8.2)
    flecha(ax, (28, 68), (38, 68), "«silencio», «espera»,\n«un momento»…", color=AMBAR, off=(0, 11), tam=6.4)
    flecha(ax, (38, 60), (28, 60), "«hola» o\nsu nombre", color=VERDE, off=(0, -7), tam=6.4)
    flecha(ax, (62, 64), (72, 64), "«para», «detente»,\n«adiós»…", color=GRIS, off=(0, 11), tam=6.4)
    flecha(ax, (16, 52), (84, 52), None, color=GRIS, curva=0.28)
    ax.text(50, 21, "«para» desde Activo también detiene todo", ha="center", fontsize=6.6, color=GRIS)
    ax.text(50, 9, "Se sale de Detenido abriendo de nuevo la app o con el botón «Detener» del aviso fijo (decisiones 0006 y 0007).", ha="center", fontsize=6.6, color=GRIS)
    guardar(fig, "m04_modos_asistente")


def m05_cuentas():
    fig, ax = lienzo(5.2)
    ax.text(2, 97, "Hoy en la app (cuenta local en el teléfono)", fontsize=8.2, fontweight="bold", color=VERDE)
    fila = [("Nombre de usuario,\ncontraseña (15 letras\no más), nombre\ny correo opcional", VERDE_CLARO, VERDE),
            ("PasswordRules\nnormaliza y valida;\nEmailRules revisa\nla forma del correo", AZUL_CLARO, AZUL),
            ("PasswordHasher:\nArgon2id, 32 MiB,\n3 pasadas, sal propia", AMBAR_CLARO, AMBAR),
            ("Fila en la tabla\nusuarios (Room)\nen el teléfono", GRIS_CLARO, GRIS)]
    for i, (t, fc, ec) in enumerate(fila):
        caja(ax, 2 + i * 25, 72, 22, 20, t, fc=fc, ec=ec, tam=6.5)
        if i:
            flecha(ax, (2 + i * 25 - 3, 82), (2 + i * 25, 82), color=GRIS)
    ax.text(2, 64, "Iniciar sesión: usuario + contraseña → compara con el hash → sesión; 10 fallos bloquean 15 minutos", fontsize=7, color=TINTA)
    ax.add_patch(Rectangle((1, 52), 98, 0.2, fc=GRIS, ec="none"))
    ax.text(2, 46, "Siguiente etapa: cuenta en el servidor (decisión del 30 de octubre de 2026)", fontsize=8.2, fontweight="bold", color=AMBAR)
    prev = [("Usuario o correo\ny contraseña\n(sin Google)", 0), ("Servidor guarda\nel hash y manda un\ncódigo al correo", 1), ("La persona escribe\no dicta el código", 2), ("Servidor entrega\nun token por usuario;\nlos chats se guardan\nen Neon", 3)]
    for i, (t, _) in enumerate(prev):
        caja(ax, 2 + i * 25, 18, 22, 20, t, fc="white", ec=AMBAR, tam=6.5, lw=1.4)
        if i:
            flecha(ax, (2 + i * 25 - 3, 28), (2 + i * 25, 28), color=AMBAR)
    ax.text(2, 9, "La interfaz ServicioDeCuentas permite cambiar de cuentas locales a cuentas del servidor sin tocar las pantallas.", fontsize=6.9, color=GRIS)
    ax.text(2, 3, "Código por correo solo para registro y recuperación. Requiere elegir un proveedor de correo y publicar las rutas en el servidor.", fontsize=6.9, color=GRIS)
    guardar(fig, "m05_cuentas_hoy_y_previsto")


def m06_datos():
    fig, ax = lienzo(4.1, ylim=(24, 100))
    usuarios = [("id", "entero", "PK"), ("nombre_usuario", "texto", ""), ("contrasena_hash", "texto (Argon2id)", ""), ("nombre_completo", "texto", ""), ("fecha_registro", "fecha", ""), ("rol", "texto", ""), ("email", "texto, nulo", ""), ("acepto_adulto_en", "fecha, nulo", ""), ("validado", "booleano", ""), ("educador_id", "entero, nulo", "")]
    conf = [("id", "entero", "PK"), ("usuario_id", "entero", "FK"), ("velocidad_voz", "decimal", ""), ("volumen", "decimal", ""), ("modo_simple", "booleano", "")]
    perm = [("id", "entero", "PK"), ("usuario_id", "entero", "FK"), ("modulo_codigo", "texto", ""), ("habilitado", "booleano", "")]
    cmd = [("id", "entero", "PK"), ("usuario_id", "entero", "FK"), ("texto_comando", "texto", ""), ("fecha_hora", "fecha", "")]
    hist = [("id", "entero", "PK"), ("comando_id", "entero", "FK"), ("modulo_destino", "texto, nulo", ""), ("resultado", "texto", "")]
    resp = [("id", "entero", "PK"), ("comando_id", "entero", "FK")]
    ax.text(2, 98, "Módulo 1 · App principal", fontsize=7.6, fontweight="bold", color=AZUL)
    tabla_er(ax, 2, 95, "usuarios", usuarios, ancho=30)
    tabla_er(ax, 38, 95, "configuracion_usuario", conf, ancho=29)
    tabla_er(ax, 38, 66, "permisos_modulos", perm, ancho=29)
    ax.text(70, 98, "Módulo 2 · Asistente de voz", fontsize=7.6, fontweight="bold", color=AMBAR)
    tabla_er(ax, 70, 95, "comandos_voz", cmd, ancho=28, color=AMBAR, fondo=AMBAR_CLARO)
    tabla_er(ax, 70, 68, "historial_comandos", hist, ancho=28, color=AMBAR, fondo=AMBAR_CLARO)
    tabla_er(ax, 70, 43, "respuestas_auditivas", resp, ancho=28, color=AMBAR, fondo=AMBAR_CLARO)
    flecha(ax, (32, 88), (38, 88), color=AZUL, estilo="-")
    flecha(ax, (32, 80), (38, 62), color=AZUL, estilo="-", curva=-0.1)
    flecha(ax, (84, 73.5), (84, 68), color=AMBAR, estilo="-")
    flecha(ax, (84, 46.5), (84, 43), color=AMBAR, estilo="-")
    ax.text(2, 41, "También en el módulo 1: cambios_cuenta e incidencias (soporte).", fontsize=6.8, color=GRIS)
    ax.text(2, 36, "Una sola base local (cecapi.db, Room versión 19, 22 entidades en total).\nPK: llave primaria · FK: llave foránea. La pantalla Chats lee la tabla\nconsultas_ia, del módulo 3.", fontsize=6.8, color=GRIS, va="top", linespacing=1.3)
    guardar(fig, "m06_modelo_datos_modulos_1_y_2")


def m07_avance():
    semanas = ["14-20\nsep", "21-27\nsep", "28 sep-\n4 oct", "5-11\noct", "12-18\noct", "19-25\noct", "26 oct-\n1 nov", "2-8 nov\n(parcial)"]
    commits = [2, 6, 30, 1, 0, 0, 21, 8]
    fig, ax = plt.subplots(figsize=(ANCHO, 3.0))
    barras = ax.bar(range(len(semanas)), commits, color=VERDE, width=0.62)
    barras[-1].set_color("#7fb596")
    for b, n in zip(barras, commits):
        ax.text(b.get_x() + b.get_width() / 2, n + 0.7, str(n), ha="center", fontsize=8.5, color=TINTA, fontweight="bold")
    ax.set_xticks(range(len(semanas)))
    ax.set_xticklabels(semanas, fontsize=7.5)
    ax.set_ylabel("Cambios (commits) en la parte móvil", fontsize=8)
    ax.set_ylim(0, 35)
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    ax.grid(axis="y", color="#dfe3e8", lw=0.6)
    ax.set_axisbelow(True)
    fig.subplots_adjust(left=0.1, right=0.99, top=0.97, bottom=0.2)
    guardar(fig, "m07_cambios_por_semana")


def m08_objetivos():
    fig, ax = lienzo(4.4)
    ax.text(50, 97, "Alcance de los objetivos específicos al 2 de noviembre de 2026", ha="center", fontsize=7.8, color=GRIS)
    filas = [
        ("O1  Pantallas accesibles\n(etiquetas y señales\ndistintas del color)", "Construido: micrófono fijo,\npantalla dividida y etiquetas", VERDE, VERDE_CLARO),
        ("O2  Asistente de voz\n(wake word, comandos,\nrespuesta hablada)", "Construido: motor de voz único;\nIA conectada y apagada por defecto", VERDE, VERDE_CLARO),
        ("O3  Registro e inicio de sesión", "Cuenta local con Argon2id;\ncódigo por correo: siguiente etapa", AMBAR, AMBAR_CLARO),
        ("O4  Distribución como APK", "APK de instalación directa;\nfirma y GitHub Releases: siguiente etapa", AMBAR, AMBAR_CLARO),
    ]
    for i, (obj, est, ec, fc) in enumerate(filas):
        y = 78 - i * 22
        caja(ax, 3, y - 8, 44, 17, obj, fc="white", ec=GRIS, tam=7)
        caja(ax, 52, y - 8, 45, 17, est, fc=fc, ec=ec, tam=7, peso="bold", color=TINTA)
        flecha(ax, (47.5, y + 0.5), (52, y + 0.5), color=GRIS)
    guardar(fig, "m08_estado_objetivos")


if __name__ == "__main__":
    for f in (m01_capas, m02_pantallas, m03_flujo_voz, m04_modos, m05_cuentas, m06_datos, m07_avance, m08_objetivos):
        f()
        print("ok", f.__name__)
