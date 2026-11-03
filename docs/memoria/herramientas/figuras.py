"""Genera las figuras de los capítulos IV y V de la memoria de estadía.

Todo lo que se dibuja sale de datos del proyecto (repositorios, documentos y pruebas al 2 nov 2026).
Cada figura se puede regenerar con:  python figuras.py   (requiere matplotlib).
Salida: ../figuras/*.png (ancho 6.3 in = ancho útil de la hoja con márgenes de 3.0 y 2.5 cm).
"""
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Ellipse, FancyArrowPatch, FancyBboxPatch, Rectangle

SALIDA = Path(__file__).resolve().parent.parent / "figuras"
SALIDA.mkdir(exist_ok=True)

plt.rcParams.update({"font.family": "Liberation Sans", "font.size": 9, "axes.unicode_minus": False})

VERDE = "#1c6b3c"
VERDE_CLARO = "#e3f1e8"
GRIS = "#5a6470"
GRIS_CLARO = "#eef0f2"
AZUL = "#1d4e89"
AZUL_CLARO = "#e4edf7"
AMBAR = "#b45309"
AMBAR_CLARO = "#fdf0dc"
ROJO = "#9b1c1c"
TINTA = "#14213d"
ANCHO = 6.3


def lienzo(alto, xlim=(0, 100), ylim=None):
    fig, ax = plt.subplots(figsize=(ANCHO, alto))
    ax.set_xlim(*xlim)
    ax.set_ylim(*(ylim or (0, 100)))
    ax._alto = alto
    ax.axis("off")
    fig.subplots_adjust(left=0.01, right=0.99, top=0.99, bottom=0.01)
    return fig, ax


def circulo(ax, x, y, r, **kw):
    """Círculo de radio r (en unidades del eje x) que se ve redondo aunque los ejes tengan otra escala."""
    ax.add_patch(Ellipse((x, y), 2 * r, 2 * r * ANCHO / ax._alto, **kw))


def caja(ax, x, y, w, h, texto, fc=GRIS_CLARO, ec=GRIS, tam=8.5, peso="normal", color=TINTA, lw=1.1, estilo="round,pad=0.25,rounding_size=1.2", alin="center"):
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle=estilo, fc=fc, ec=ec, lw=lw))
    ax.text(x + w / 2, y + h / 2, texto, ha="center", va="center", fontsize=tam, fontweight=peso, color=color, linespacing=1.25, multialignment=alin)


def flecha(ax, p1, p2, texto=None, color=GRIS, lw=1.3, estilo="-|>", off=(0, 1.2), tam=7.5, curva=0.0, tpos=0.5, ha="center"):
    ax.add_patch(FancyArrowPatch(p1, p2, arrowstyle=estilo, mutation_scale=11, color=color, lw=lw, connectionstyle=f"arc3,rad={curva}", shrinkA=0, shrinkB=0))
    if texto:
        mx = p1[0] + (p2[0] - p1[0]) * tpos + off[0]
        my = p1[1] + (p2[1] - p1[1]) * tpos + off[1]
        ax.text(mx, my, texto, ha=ha, va="center", fontsize=tam, color=color, linespacing=1.2, bbox=dict(fc="white", ec="none", pad=0.6))


def guardar(fig, nombre):
    fig.savefig(SALIDA / f"{nombre}.png", dpi=220, facecolor="white")
    plt.close(fig)


# --------------------------------------------------------------------------------------------- 1. equipos
def fig_equipos():
    fig, ax = lienzo(3.6)
    caja(ax, 25, 82, 50, 13, "Responsable del proyecto y de la integración\n(fusión de las partes en la rama principal)", fc=VERDE, ec=VERDE, color="white", peso="bold", tam=8.5)
    filas = [
        ("Equipo 1", "Módulos 1 y 2\nApp principal y\nasistente de voz", 2),
        ("Equipo 2", "Módulo 3\nAsistente inteligente\ny servidor de IA", 21),
        ("Equipo 3", "Módulos 4 y 5\nLector de documentos\ny solicitudes", 40),
        ("Equipo 4", "Módulo 6\nActividades\n(audio y vibración)", 59),
        ("Equipo 5", "Módulo 7\nAsistente del\nentorno", 78),
    ]
    for nombre, texto, x in filas:
        caja(ax, x, 48, 19.5, 22, f"{nombre}\n{texto}", fc=VERDE_CLARO, ec=VERDE, tam=7.6)
        flecha(ax, (50, 82), (x + 9.75, 70), color=GRIS, lw=0.9)
    ax.text(2, 42, "App móvil y servidor (repositorio EstadiasAppMobil)", fontsize=7.8, color=VERDE, fontweight="bold")
    ax.text(2, 31, "Otros repositorios", fontsize=7.8, color=AZUL, fontweight="bold")
    caja(ax, 2, 6, 29, 20, "Asistente web\nEquipo de 3\n(PaginaAsistenteCECAPI)", fc=AZUL_CLARO, ec=AZUL, tam=7.6)
    caja(ax, 35.5, 6, 29, 20, "Sitio CECAPI\nEquipo de 2\n(PaginaCECAPI)", fc=AZUL_CLARO, ec=AZUL, tam=7.6)
    caja(ax, 69, 6, 29, 20, "App de Windows\n(WindowsCECAPI)", fc=AZUL_CLARO, ec=AZUL, tam=7.6)
    guardar(fig, "fig01_equipos")


# --------------------------------------------------------------------------------------------- 2. kanban
def fig_kanban():
    fig, ax = lienzo(5.6)
    columnas = [
        ("POR HACER", GRIS, GRIS_CLARO),
        ("EN CURSO", AMBAR, AMBAR_CLARO),
        ("HECHO", VERDE, VERDE_CLARO),
    ]
    tarjetas = {
        "POR HACER": [
            ("S", "Cuentas con token por usuario"),
            ("S", "CORS para la página web"),
            ("S", "Cupo de consultas por persona"),
            ("S", "Auditoría OWASP Top 10"),
            ("A", "Pruebas con TalkBack"),
            ("W", "Pruebas con NVDA y JAWS"),
            ("W", "Conectar la web con el servidor"),
            ("T", "Aviso de privacidad aprobado"),
            ("T", "Proveedor de correo para códigos"),
        ],
        "EN CURSO": [
            ("S", "Publicar en Render y Neon"),
            ("V", "Primer instalador .exe en Actions"),
            ("T", "Pruebas con estudiantes de CECAPI"),
        ],
        "HECHO": [
            ("S", "Respaldo entre Groq y Gemini"),
            ("S", "Migraciones y base en Neon"),
            ("S", "Filtro de privacidad"),
            ("S", "Rutas /documento y /panel"),
            ("A", "Contraseñas con Argon2id"),
            ("A", "Pantalla dividida con micrófono"),
            ("W", "Chat, políticas y descargas"),
            ("C", "Sitio institucional"),
            ("V", "App de Windows 1.0.0"),
        ],
    }
    colores = {"S": VERDE, "A": AZUL, "W": "#6b3fa0", "C": "#8a5a00", "V": "#0f6b6b", "T": GRIS}
    ancho_col = 31.3
    for i, (nombre, c, fondo) in enumerate(columnas):
        x = 1 + i * (ancho_col + 1.6)
        ax.add_patch(Rectangle((x, 6), ancho_col, 88.5, fc=fondo, ec=c, lw=1.2))
        ax.text(x + ancho_col / 2, 91.2, f"{nombre} ({len(tarjetas[nombre])})", ha="center", va="center", fontsize=9, fontweight="bold", color=c)
        for j, (clave, texto) in enumerate(tarjetas[nombre]):
            y = 80 - j * 8.6
            ax.add_patch(FancyBboxPatch((x + 1.4, y), ancho_col - 2.8, 7.2, boxstyle="round,pad=0.1,rounding_size=0.8", fc="white", ec="#b8bec6", lw=0.8))
            ax.add_patch(Rectangle((x + 1.4, y), 1.1, 7.2, fc=colores[clave], ec="none"))
            ax.text(x + 3.4, y + 3.6, texto, ha="left", va="center", fontsize=7, color=TINTA)
    leyenda = [("S", "Servidor"), ("A", "App Android"), ("W", "Asistente web"), ("C", "Sitio CECAPI"), ("V", "App Windows"), ("T", "Transversal")]
    for k, (clave, texto) in enumerate(leyenda):
        x = 1 + k * 16.4
        ax.add_patch(Rectangle((x, 1.6), 1.6, 2.6, fc=colores[clave], ec="none"))
        ax.text(x + 2.3, 2.9, texto, fontsize=7, va="center", color=TINTA)
    guardar(fig, "fig02_kanban")


# --------------------------------------------------------------------------------------------- 3. commits por semana
def fig_commits():
    semanas = ["14-20\nsep", "21-27\nsep", "28 sep-\n4 oct", "5-11\noct", "12-18\noct", "19-25\noct", "26 oct-\n1 nov", "2-8 nov\n(parcial)"]
    commits = [3, 21, 62, 22, 3, 8, 47, 18]
    fig, ax = plt.subplots(figsize=(ANCHO, 3.0))
    barras = ax.bar(range(len(semanas)), commits, color=VERDE, width=0.62)
    barras[-1].set_color("#7fb596")
    for b, n in zip(barras, commits):
        ax.text(b.get_x() + b.get_width() / 2, n + 1.2, str(n), ha="center", fontsize=8.5, color=TINTA, fontweight="bold")
    ax.set_xticks(range(len(semanas)))
    ax.set_xticklabels(semanas, fontsize=7.5)
    ax.set_ylabel("Cambios registrados (commits)", fontsize=8)
    ax.set_ylim(0, 72)
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    ax.grid(axis="y", color="#dfe3e8", lw=0.6)
    ax.set_axisbelow(True)
    fig.subplots_adjust(left=0.1, right=0.99, top=0.97, bottom=0.2)
    guardar(fig, "fig03_commits")


# --------------------------------------------------------------------------------------------- 4. arquitectura
def fig_arquitectura():
    fig, ax = lienzo(5.2)
    ax.text(2, 96, "Clientes", fontsize=8.5, fontweight="bold", color=VERDE)
    clientes = [
        ("App Android\nKotlin · Compose · Room\n(APK)", 82),
        ("Asistente web\nHTML · CSS · JavaScript\n(GitHub Pages)", 62),
        ("App de Windows\nKotlin · Compose Desktop\n(.exe)", 42),
    ]
    for texto, y in clientes:
        caja(ax, 1, y, 24, 12.5, texto, fc=VERDE_CLARO, ec=VERDE, tam=7.4)
    caja(ax, 1, 20, 24, 12.5, "Sitio CECAPI\nHTML · CSS · JavaScript\n(informativo)", fc=GRIS_CLARO, ec=GRIS, tam=7.4)
    ax.text(31, 96, "Servidor (FastAPI en Render)", fontsize=8.5, fontweight="bold", color=AZUL)
    ax.add_patch(FancyBboxPatch((31, 12), 38, 82, boxstyle="round,pad=0.2,rounding_size=1.5", fc=AZUL_CLARO, ec=AZUL, lw=1.3))
    modulos = [
        ("seguridad.py", "clave de la app · límite por minuto"),
        ("privacidad.py", "filtro de datos personales"),
        ("comandos.py", "frases que abren un módulo"),
        ("proveedores.py", "Groq y Gemini con respaldo"),
        ("imagenes.py", "endereza y limpia las fotos"),
        ("historial.py · uso.py", "conversaciones y contador diario"),
        ("panel.py", "uso de los últimos días"),
    ]
    for k, (nombre, detalle) in enumerate(modulos):
        y = 82.5 - k * 10.1
        caja(ax, 33, y, 34, 8.2, f"{nombre}\n{detalle}", fc="white", ec=AZUL, tam=6.9, lw=0.8)
    ax.text(75, 96, "Servicios externos", fontsize=8.5, fontweight="bold", color=AMBAR)
    caja(ax, 75, 62, 24, 14, "Groq\n(IA principal)", fc=AMBAR_CLARO, ec=AMBAR, tam=7.6)
    caja(ax, 75, 42, 24, 14, "Gemini\n(IA de respaldo)", fc=AMBAR_CLARO, ec=AMBAR, tam=7.6)
    caja(ax, 75, 14, 24, 20, "Neon\nPostgreSQL\nconversaciones,\nmensajes, uso diario", fc=AMBAR_CLARO, ec=AMBAR, tam=7.2)
    for y in (88.25, 68.25, 48.25):
        flecha(ax, (25, y), (31, y), color=VERDE, lw=1.3)
    flecha(ax, (67, 56.4), (75, 69), color=AMBAR, curva=0.0)
    flecha(ax, (67, 56.4), (75, 49), color=AMBAR, estilo="-|>")
    flecha(ax, (67, 36.2), (75, 26), color=AMBAR, estilo="<|-|>")
    ax.text(50, 6, "Los tres clientes hablan con el servidor por HTTPS y llevan la clave de la app (X-Clave-App).\nEl sitio CECAPI no usa el servidor: solo enlaza al asistente y a las descargas.", fontsize=7.2, ha="center", va="center", color=GRIS)
    guardar(fig, "fig04_arquitectura")


# --------------------------------------------------------------------------------------------- 5. despliegue
def fig_despliegue():
    fig, ax = lienzo(4.2)
    caja(ax, 28, 80, 44, 15, "GitHub\nrepositorios · ramas · Actions\n(construye el .exe)", fc=GRIS_CLARO, ec=GRIS, tam=8)
    caja(ax, 3, 54, 30, 15, "GitHub Pages\nasistente web y\nsitio CECAPI", fc=VERDE_CLARO, ec=VERDE, tam=7.8)
    caja(ax, 36, 54, 30, 15, "GitHub Releases\nAPK y .exe con su\nsuma SHA-256", fc=VERDE_CLARO, ec=VERDE, tam=7.8)
    caja(ax, 69, 54, 30, 15, "Render (plan gratuito)\nservidor FastAPI\nse duerme a los 15 min", fc=AZUL_CLARO, ec=AZUL, tam=7.6)
    caja(ax, 3, 8, 38, 20, "Personas usuarias\nnavegador · teléfono ·\ncomputadora con Windows", fc="white", ec=TINTA, tam=7.8)
    caja(ax, 46, 8, 24, 20, "Groq · Gemini\nplanes gratuitos", fc=AMBAR_CLARO, ec=AMBAR, tam=7.8)
    caja(ax, 75, 8, 24, 20, "Neon (plan gratuito)\nPostgreSQL\nse suspende\na los 5 min", fc=AMBAR_CLARO, ec=AMBAR, tam=7.4)
    flecha(ax, (40, 80), (20, 69), "publica", color=GRIS, off=(-2, 1.5))
    flecha(ax, (50, 80), (51, 69), "publica", color=GRIS, off=(5, 0))
    flecha(ax, (60, 80), (84, 69), "despliega", color=GRIS, off=(4, 1.5))
    flecha(ax, (14, 28), (14, 54), "abren la web", color=VERDE, off=(-0.5, 0), tam=7.2)
    flecha(ax, (34, 28), (46, 54), "descargan la app", color=VERDE, off=(-6, 0), tam=7.2)
    flecha(ax, (92, 54), (92, 28), color=AMBAR, estilo="<|-|>")
    flecha(ax, (76, 54), (62, 28), "consulta a la IA", color=AMBAR, off=(-6.5, 1), tam=7.2)
    ax.text(84, 46.5, "recibe las preguntas\npor HTTPS desde\nlas apps", fontsize=6.9, ha="center", color=AZUL, linespacing=1.2)
    ax.text(50, 2.5, "Costo directo: cero. Solo el dominio, de unos 10 USD al año, sería un gasto (decisión 0008).", fontsize=7.2, ha="center", color=GRIS)
    guardar(fig, "fig05_despliegue")


# --------------------------------------------------------------------------------------------- 6. flujo de una pregunta
def fig_flujo_pregunta():
    fig, ax = lienzo(5.6)
    caja(ax, 1, 86, 22, 10, "Persona\nhabla o escribe", fc="white", ec=TINTA, tam=8)
    caja(ax, 28, 86, 26, 10, "Cliente (app, web o Windows)\narma pregunta + 6 líneas\nde contexto", fc=VERDE_CLARO, ec=VERDE, tam=7.2)
    flecha(ax, (23, 91), (28, 91), color=VERDE)
    caja(ax, 59, 86, 40, 10, "POST /preguntar  con  X-Clave-App\n{ pregunta, contexto, conversacionId }", fc=GRIS_CLARO, ec=GRIS, tam=7.4)
    flecha(ax, (54, 91), (59, 91), color=GRIS)
    pasos = [
        ("1", "¿La clave de la app es válida?", "No: error 401", 74),
        ("2", "¿Pasó el límite por minuto de su IP?", "Sí: error 429", 62),
        ("3", "¿Trae correo, teléfono, tarjeta, CURP,\nRFC o contraseña?", "Sí: responde con un aviso\ny no llama a la IA", 49),
        ("4", "Prueba la IA en orden (IA_ORDEN):\nGroq y, si falla o agotó su cupo del día, Gemini", "Ninguna responde: error 503", 36),
        ("5", "Guarda solo metadatos: ruta, tipo, proveedor, tiempo\n(el texto no se guarda: GUARDAR_TEXTOS=0)", "", 24),
    ]
    for n, texto, desvio, y in pasos:
        circulo(ax, 5, y + 4.2, 2.6, fc=AZUL, ec="none")
        ax.text(5, y + 4, n, color="white", ha="center", va="center", fontsize=8.5, fontweight="bold")
        caja(ax, 10, y, 50, 8.4, texto, fc=AZUL_CLARO, ec=AZUL, tam=7.3)
        if desvio:
            caja(ax, 66, y + 0.2, 33, 8, desvio, fc=AMBAR_CLARO, ec=AMBAR, tam=7)
            flecha(ax, (60, y + 4.2), (66, y + 4.2), color=AMBAR)
    flecha(ax, (45, 86), (45, 82.4), color=GRIS, lw=1.1)
    ys = [p[3] for p in pasos]
    for arriba, abajo in zip(ys, ys[1:]):
        flecha(ax, (35, arriba), (35, abajo + 8.4), color=AZUL, lw=1.1)
    flecha(ax, (35, ys[-1]), (35, 17.6), color=AZUL, lw=1.1)
    caja(ax, 10, 8, 50, 9.6, "Responde  { respuesta, proveedor, hayMas, conversacionId }", fc=VERDE_CLARO, ec=VERDE, tam=7.6)
    caja(ax, 66, 8, 33, 9.6, "El cliente la muestra y la\nlee en voz alta", fc="white", ec=TINTA, tam=7.4)
    flecha(ax, (60, 12.8), (66, 12.8), color=VERDE)
    ax.text(50, 3, "Si el servidor está dormido, el cliente avisa por voz a los 4 segundos y espera hasta 75 segundos.", fontsize=7.2, ha="center", color=GRIS)
    guardar(fig, "fig06_flujo_pregunta")


# --------------------------------------------------------------------------------------------- 7. modelo de datos
def tabla_er(ax, x, y, titulo, columnas, ancho=30, color=AZUL, fondo=AZUL_CLARO):
    alto_fila = 4.3
    alto = alto_fila * (len(columnas) + 1)
    ax.add_patch(Rectangle((x, y - alto), ancho, alto, fc="white", ec=color, lw=1.2))
    ax.add_patch(Rectangle((x, y - alto_fila), ancho, alto_fila, fc=color, ec=color))
    ax.text(x + ancho / 2, y - alto_fila / 2, titulo, ha="center", va="center", color="white", fontsize=8.5, fontweight="bold")
    for i, (nombre, tipo, marca) in enumerate(columnas):
        yy = y - alto_fila * (i + 1.5)
        if marca:
            ax.add_patch(Rectangle((x, yy - alto_fila / 2), ancho, alto_fila, fc=fondo, ec="none"))
        ax.text(x + 1.2, yy, (marca + " " if marca else "") + nombre, va="center", fontsize=6.8, color=TINTA, fontweight="bold" if marca else "normal")
        ax.text(x + ancho - 1.2, yy, tipo, va="center", ha="right", fontsize=6.4, color=GRIS)
    return alto


def fig_modelo_datos():
    fig, ax = lienzo(4.7)
    conv = [("id", "texto(36)", "PK"), ("usuario_id", "entero, nulo", ""), ("titulo", "texto(60), nulo", ""), ("iniciada_en", "fecha y hora", ""), ("ultima_actividad", "fecha y hora", "")]
    msg = [("id", "entero", "PK"), ("conversacion_id", "texto(36)", "FK"), ("ruta", "texto(20)", ""), ("rol", "texto(10)", ""), ("tipo", "texto(10)", ""), ("texto", "texto, nulo", ""), ("comando", "texto(40), nulo", ""), ("proveedor", "texto(40), nulo", ""), ("hay_mas", "booleano", ""), ("duracion_ms", "entero, nulo", ""), ("creado_en", "fecha y hora", "")]
    uso = [("fecha", "texto(10)", "PK"), ("proveedor", "texto(40)", "PK"), ("peticiones", "entero", ""), ("aviso_enviado", "booleano", "")]
    tabla_er(ax, 2, 96, "conversaciones", conv, ancho=30)
    tabla_er(ax, 38, 96, "mensajes", msg, ancho=32)
    tabla_er(ax, 2, 60, "uso_diario", uso, ancho=30, color=AMBAR, fondo=AMBAR_CLARO)
    flecha(ax, (32, 89.55), (38, 85.25), color=AZUL, estilo="-")
    ax.text(32.4, 91.2, "1", fontsize=8, color=AZUL, fontweight="bold")
    ax.text(34.6, 83.2, "∞", fontsize=9, color=AZUL, fontweight="bold")
    ax.text(75, 91, "Notas de diseño", fontsize=8, fontweight="bold", color=TINTA)
    notas = [
        "Un mensaje pertenece a una\nconversación; al borrar la\nconversación se borran sus\nmensajes (ON DELETE CASCADE).",
        "El id de la conversación es un\nUUID al azar: nadie puede\nadivinar el de otra persona.",
        "texto y titulo quedan vacíos\nsalvo que GUARDAR_TEXTOS=1.\nLo bloqueado por privacidad,\nel contexto y las fotos nunca\nse guardan.",
        "usuario_id sigue vacío: el\nservidor todavía no tiene cuentas.",
    ]
    yy = 86
    for n in notas:
        ax.text(75, yy, n, fontsize=6.9, va="top", color=GRIS, linespacing=1.25)
        yy -= 3.6 * (n.count("\n") + 1) + 4
    ax.text(2, 2.5, "PK: llave primaria · FK: llave foránea. Tablas creadas con Alembic (migración 20261030_0001); SQLite en desarrollo y PostgreSQL en Neon.", fontsize=6.9, color=GRIS)
    guardar(fig, "fig07_modelo_datos")


# --------------------------------------------------------------------------------------------- 8. pantalla dividida
def fig_pantalla_dividida():
    fig, ax = lienzo(3.6)
    def marco(x, y, w, h, etiqueta):
        ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.2,rounding_size=1.4", fc="white", ec=TINTA, lw=1.6))
        ax.text(x + w / 2, y - 3.4, etiqueta, ha="center", fontsize=7.6, fontweight="bold", color=TINTA)
    # celular vertical
    marco(3, 10, 22, 78, "Celular vertical")
    ax.add_patch(Rectangle((3.4, 10.4 + 78 * 0.42 - 0.4), 21.2, 78 * 0.58 - 0.4, fc=VERDE_CLARO, ec="none"))
    ax.add_patch(Rectangle((3.4, 10.4), 21.2, 78 * 0.42 - 0.4, fc=AZUL_CLARO, ec="none"))
    ax.text(14, 10 + 78 * 0.71, "Contenido\ncon desplazamiento\n58 %", ha="center", va="center", fontsize=7.4, color=VERDE, fontweight="bold")
    ax.text(14, 10 + 78 * 0.21 + 8, "Micrófono\n42 %", ha="center", va="center", fontsize=7.4, color=AZUL, fontweight="bold")
    circulo(ax, 14, 10 + 78 * 0.21 - 8, 4.2, fc="white", ec=AZUL, lw=1.4)
    # celular horizontal
    marco(32, 30, 40, 24, "Celular horizontal")
    ax.add_patch(Rectangle((32.4, 30.4), 40 * 0.58 - 0.2, 23.2, fc=VERDE_CLARO, ec="none"))
    ax.add_patch(Rectangle((32.4 + 40 * 0.58 - 0.2, 30.4), 40 * 0.42 - 0.2, 23.2, fc=AZUL_CLARO, ec="none"))
    ax.text(32 + 40 * 0.29, 42, "Contenido\n58 %", ha="center", va="center", fontsize=7.4, color=VERDE, fontweight="bold")
    ax.text(32 + 40 * 0.79, 47, "Micrófono\n42 %", ha="center", va="center", fontsize=7.4, color=AZUL, fontweight="bold")
    circulo(ax, 32 + 40 * 0.79, 36.5, 3.0, fc="white", ec=AZUL, lw=1.4)
    # tablet
    marco(78, 10, 20, 78, "Tablet")
    ax.add_patch(Rectangle((78.4, 10.4 + 78 * 0.42 - 0.4), 19.2, 78 * 0.58 - 0.4, fc=VERDE_CLARO, ec="none"))
    ax.add_patch(Rectangle((78.4, 10.4), 19.2, 78 * 0.42 - 0.4, fc=AZUL_CLARO, ec="none"))
    ax.add_patch(Rectangle((82, 10.4 + 78 * 0.42 + 2), 12, 78 * 0.58 - 4.6, fc="white", ec=VERDE, lw=1, ls="--"))
    ax.text(88, 10 + 78 * 0.71, "Contenido\n(máx. 720 dp,\ncentrado)", ha="center", va="center", fontsize=6.6, color=VERDE, fontweight="bold")
    ax.text(88, 10 + 78 * 0.21 + 8, "Micrófono", ha="center", va="center", fontsize=7.2, color=AZUL, fontweight="bold")
    circulo(ax, 88, 10 + 78 * 0.21 - 8, 4.2, fc="white", ec=AZUL, lw=1.4)
    ax.text(52, 14, "Una sola proporción (MIC_FRACCION = 0.42) para todas las pantallas.\nToda la zona del micrófono es el botón: un toque habla, dos toques\n(o un toque con dos dedos) callan, y mantener oprimido da la ayuda.", ha="center", va="center", fontsize=7, color=GRIS, linespacing=1.3)
    guardar(fig, "fig08_pantalla_dividida")


# --------------------------------------------------------------------------------------------- 9. flujo de cuenta
def fig_flujo_cuenta():
    fig, ax = lienzo(4.4)
    ax.text(2, 96, "Crear cuenta", fontsize=8.5, fontweight="bold", color=VERDE)
    pasos = ["Usuario, correo,\ncontraseña (15 o más)\ny 18 años o más", "El servidor guarda\nla contraseña con\nArgon2id y manda\nun código al correo", "La persona escribe\nel código", "El servidor da un\ntoken por usuario"]
    for k, t in enumerate(pasos):
        x = 2 + k * 24.5
        caja(ax, x, 74, 21, 16, t, fc=VERDE_CLARO, ec=VERDE, tam=7.1)
        if k:
            flecha(ax, (x - 3.5, 82), (x, 82), color=VERDE)
    ax.text(2, 64, "Iniciar sesión", fontsize=8.5, fontweight="bold", color=AZUL)
    caja(ax, 2, 40, 28, 18, "Correo o nombre de usuario\ny contraseña", fc=AZUL_CLARO, ec=AZUL, tam=7.4)
    caja(ax, 38, 40, 28, 18, "El servidor compara con el\nresumen Argon2id guardado", fc=AZUL_CLARO, ec=AZUL, tam=7.4)
    caja(ax, 74, 40, 24, 18, "Token por usuario\n(solo en memoria)", fc=AZUL_CLARO, ec=AZUL, tam=7.4)
    flecha(ax, (30, 49), (38, 49), color=AZUL)
    flecha(ax, (66, 49), (74, 49), color=AZUL)
    ax.text(2, 30, "Recuperar la contraseña", fontsize=8.5, fontweight="bold", color=AMBAR)
    rec = ["Correo de la\ncuenta", "Código al correo\n(misma respuesta\nexista o no la cuenta)", "Código y\ncontraseña nueva", "Contraseña\ncambiada"]
    for k, t in enumerate(rec):
        x = 2 + k * 24.5
        caja(ax, x, 6, 21, 18, t, fc=AMBAR_CLARO, ec=AMBAR, tam=7)
        if k:
            flecha(ax, (x - 3.5, 15), (x, 15), color=AMBAR)
    ax.text(50, 1.8, "Diseño previsto (decisión del 30 oct 2026). El servidor todavía no tiene las rutas de cuenta;\nel cliente web ya está probado con un servidor simulado.", ha="center", va="center", fontsize=6.9, color=GRIS)
    guardar(fig, "fig09_flujo_cuenta")


# --------------------------------------------------------------------------------------------- 10. resultados IA
def fig_resultados_ia():
    corridas = ["10 oct\n(inicial)", "24 oct\n(antes de\ncorregir)", "24 oct\n(corregido)", "25 oct"]
    series = [
        ("Frases que abren un módulo, reconocidas (de 20)", [7, 7, 20, 20], VERDE),
        ("Sinónimos correctos (de 24 evaluables)", [11, 11, 24, 24], AZUL),
        ("Preguntas con respuesta correcta (de 20)", [18, 18, 20, 19], AMBAR),
    ]
    fig, ax = plt.subplots(figsize=(ANCHO, 3.4))
    ancho = 0.25
    for i, (nombre, valores, color) in enumerate(series):
        xs = [k + (i - 1) * ancho for k in range(len(corridas))]
        barras = ax.bar(xs, valores, width=ancho * 0.92, color=color, label=nombre)
        for b, v in zip(barras, valores):
            ax.text(b.get_x() + b.get_width() / 2, v + 0.5, str(v), ha="center", fontsize=7.6, color=TINTA)
    ax.set_xticks(range(len(corridas)))
    ax.set_xticklabels(corridas, fontsize=8)
    ax.set_ylim(0, 29)
    ax.set_ylabel("Casos correctos", fontsize=8)
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    ax.grid(axis="y", color="#dfe3e8", lw=0.6)
    ax.set_axisbelow(True)
    ax.legend(fontsize=7, frameon=False, loc="upper left", bbox_to_anchor=(0.0, 1.02))
    fig.subplots_adjust(left=0.09, right=0.99, top=0.97, bottom=0.2)
    guardar(fig, "fig10_resultados_ia")


# --------------------------------------------------------------------------------------------- 11. pruebas automáticas
def fig_pruebas():
    comp = ["Servidor", "App Android", "Asistente web", "Sitio CECAPI", "App de Windows"]
    comportamiento = [199, 60, 85, 7, 14]
    accesibilidad = [0, 0, 88, 88, 0]
    fig, ax = plt.subplots(figsize=(ANCHO, 3.0))
    ys = range(len(comp))
    ax.barh(ys, comportamiento, color=VERDE, label="Comportamiento (pruebas y verificaciones)")
    ax.barh(ys, accesibilidad, left=comportamiento, color=AZUL, label="Accesibilidad automática (páginas × pantallas × temas)")
    for y, a, b in zip(ys, comportamiento, accesibilidad):
        ax.text(a + b + 3, y, str(a + b), va="center", fontsize=8.5, color=TINTA, fontweight="bold")
    ax.set_yticks(list(ys))
    ax.set_yticklabels(comp, fontsize=8.5)
    ax.invert_yaxis()
    ax.set_xlim(0, 235)
    ax.set_xlabel("Cantidad de pruebas o verificaciones que pasan", fontsize=8)
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    ax.legend(fontsize=7.2, frameon=False, loc="lower right")
    fig.subplots_adjust(left=0.2, right=0.98, top=0.97, bottom=0.18)
    guardar(fig, "fig11_pruebas")


if __name__ == "__main__":
    for f in (fig_equipos, fig_kanban, fig_commits, fig_arquitectura, fig_despliegue, fig_flujo_pregunta, fig_modelo_datos, fig_pantalla_dividida, fig_flujo_cuenta, fig_resultados_ia, fig_pruebas):
        f()
    print("figuras:", sorted(p.name for p in SALIDA.glob("*.png")))
