"""Arma Memoria_Capitulos_IV_V.docx con el formato del manual de la UTD y revisa las reglas de redacción.

Uso:  python generar_memoria.py
Requiere: python-docx, pillow, y haber generado antes las figuras (figuras.py) y las capturas.
"""
import re
import sys
from pathlib import Path

from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Cm, Pt
from PIL import Image

import contenido as K
import docx_base as B

RAIZ = Path(__file__).resolve().parent.parent
FIGURAS = RAIZ / "figuras"
CAPTURAS = RAIZ / "capturas"
SALIDA = RAIZ / "Memoria_Capitulos_IV_V.docx"


def componer_capturas():
    """Junta capturas en dos imágenes para ahorrar páginas."""
    fondo = (255, 255, 255)
    celulares = [Image.open(CAPTURAS / n).convert("RGB") for n in ("cap01_web_politicas_celular.png", "cap02_web_registro_celular.png", "cap03_web_oscuro_celular.png")]
    alto = min(i.height for i in celulares)
    celulares = [i.crop((0, 0, i.width, alto)) for i in celulares]
    hueco = 30
    ancho = sum(i.width for i in celulares) + hueco * (len(celulares) - 1)
    lienzo = Image.new("RGB", (ancho, alto), fondo)
    x = 0
    for i in celulares:
        lienzo.paste(i, (x, 0))
        x += i.width + hueco
    lienzo.save(CAPTURAS / "cap_web_celular.png", optimize=True)

    pc = Image.open(CAPTURAS / "cap06_sitio_computadora.png").convert("RGB")
    cel = Image.open(CAPTURAS / "cap07_sitio_celular.png").convert("RGB")
    alto = max(pc.height, cel.height)
    lienzo = Image.new("RGB", (pc.width + hueco + cel.width, alto), fondo)
    lienzo.paste(pc, (0, 0))
    lienzo.paste(cel, (pc.width + hueco, 0))
    lienzo.save(CAPTURAS / "cap_sitio.png", optimize=True)


def numerar(contenido):
    """Asigna número a cada figura y tabla en orden de aparición."""
    n_fig = n_tab = 0
    numeros = {}
    for item in contenido:
        if item[0] == "fig":
            n_fig += 1
            numeros[f"fig:{item[1]}"] = n_fig
        elif item[0] == "tab":
            n_tab += 1
            numeros[f"tab:{item[1]}"] = n_tab
    return numeros


def resolver(texto, numeros):
    def cambio(m):
        clave = m.group(1)
        if clave not in numeros:
            raise KeyError(f"Referencia sin figura o tabla: {clave}")
        etiqueta = "Figura" if clave.startswith("fig") else "Tabla"
        return f"{etiqueta} {numeros[clave]}"
    return re.sub(r"\[\[([a-z]+:[a-z_0-9]+)\]\]", cambio, texto)


def construir():
    componer_capturas()
    numeros = numerar(K.C)
    doc = B.crear_documento()
    B.encabezado_y_pie(doc, K.CARRERA, K.ALUMNO)
    primero = True
    n_fig = n_tab = 0
    for item in K.C:
        tipo = item[0]
        if tipo == "h1":
            doc.add_paragraph(item[1], style="Heading 1")
        elif tipo == "h2":
            doc.add_paragraph(item[1], style="Heading 2")
        elif tipo == "h3":
            doc.add_paragraph(item[1], style="Heading 3")
        elif tipo == "h4":
            doc.add_paragraph(item[1], style="Heading 4")
        elif tipo == "p":
            B.agregar_texto(doc.add_paragraph(style="Texto"), resolver(item[1], numeros))
        elif tipo == "bul":
            for texto in item[1]:
                par = doc.add_paragraph(style="Viñeta")
                par.add_run("●\t")
                par.paragraph_format.tab_stops.add_tab_stop(Cm(1.9))
                B.agregar_texto(par, resolver(texto, numeros))
        elif tipo == "ref":
            B.agregar_texto(doc.add_paragraph(style="Referencia"), item[1])
        elif tipo == "fig":
            n_fig += 1
            _, clave, archivo, nombre, ancho = item
            par = doc.add_paragraph()
            par.alignment = WD_ALIGN_PARAGRAPH.CENTER
            par.paragraph_format.first_line_indent = Cm(0)
            par.paragraph_format.keep_with_next = True
            par.paragraph_format.space_before = Pt(6)
            par.paragraph_format.line_spacing = 1.0
            par.add_run().add_picture(str(FIGURAS / archivo if (FIGURAS / archivo).exists() else CAPTURAS / archivo), width=Cm(ancho))
            B.rotulo(doc, "Figura", n_fig, nombre)
            sep = doc.add_paragraph()
            sep.paragraph_format.space_after = Pt(6)
            sep.paragraph_format.line_spacing = 1.0
        elif tipo == "tab":
            n_tab += 1
            _, clave, nombre, enc, filas, anchos, centro = item
            B.rotulo(doc, "Tabla", n_tab, nombre, mantener_con_siguiente=True)
            B.tabla(doc, enc, filas, anchos, centro)
            sep = doc.add_paragraph()
            sep.paragraph_format.space_after = Pt(6)
            sep.paragraph_format.line_spacing = 1.0
        elif tipo == "corte":
            B.salto_de_pagina(doc)
        else:
            raise ValueError(tipo)
    doc.core_properties.title = "Memoria de estadía: capítulos IV y V (borrador)"
    doc.core_properties.author = K.ALUMNO
    doc.save(SALIDA)
    return numeros


# ------------------------------------------------------------------------------------- validación
PRIMERA_PERSONA = re.compile(r"\b(nosotros|nosotras|nuestro|nuestra|nuestros|nuestras|realizamos|hicimos|desarrollamos|decidimos|creemos|opino|considero|\byo\b|\bmi\b|\bmis\b)\b", re.I)


def limpiar_marcas(texto):
    return re.sub(r"\{\{.*?\}\}", "", re.sub(r"\*\*|\*", "", texto))


def revisar(numeros):
    problemas = []
    palabras_total = 0
    for item in K.C:
        if item[0] != "p":
            continue
        texto = resolver(item[1], numeros)
        sin_marcas = limpiar_marcas(texto).strip()
        n = len(sin_marcas.split())
        palabras_total += n
        if not sin_marcas.startswith("Palabras clave") and not sin_marcas.startswith("Keywords") and n < 40:
            problemas.append(f"Párrafo de {n} palabras (mínimo 40): {sin_marcas[:70]}...")
        m = PRIMERA_PERSONA.search(sin_marcas)
        if m:
            problemas.append(f"Primera persona '{m.group(0)}': {sin_marcas[:70]}...")
    # referencias [[...]] resueltas y figuras mencionadas antes de aparecer
    vistas = set()
    for item in K.C:
        if item[0] == "fig":
            vistas.add(f"fig:{item[1]}")
        elif item[0] == "tab":
            vistas.add(f"tab:{item[1]}")
    mencionadas = set(re.findall(r"\[\[([a-z]+:[a-z_0-9]+)\]\]", " ".join(i[1] for i in K.C if i[0] == "p")))
    sin_mencion = [v for v in vistas if v not in mencionadas and not v.startswith("fig:web") and not v.startswith("fig:sitio") and not v.startswith("fig:win") and v not in ("tab:anexos", "tab:siglas")]
    for v in sin_mencion:
        problemas.append(f"La {v} no se menciona en el texto (el manual pide referenciarla)")
    # Resumen: 150 a 250 palabras
    for k, item in enumerate(K.C):
        if item[0] == "h1" and item[1] in ("RESUMEN", "ABSTRACT"):
            siguiente = K.C[k + 1]
            n = len(limpiar_marcas(siguiente[1]).split())
            if not 150 <= n <= 250:
                problemas.append(f"{item[1]} tiene {n} palabras (debe tener de 150 a 250)")
            else:
                print(f"  {item[1]}: {n} palabras (150 a 250: bien)")
    placeholders = sum(len(re.findall(r"\{\{", i[1])) for i in K.C if len(i) > 1 and isinstance(i[1], str) and i[0] in ("p", "ref"))
    print(f"  Palabras de texto corrido: {palabras_total}")
    print(f"  Pendientes resaltados en amarillo (texto corrido): {placeholders}")
    return problemas


if __name__ == "__main__":
    numeros = construir()
    figs = sum(1 for k in numeros if k.startswith("fig"))
    tabs = sum(1 for k in numeros if k.startswith("tab"))
    print(f"Generado: {SALIDA.name}  ({figs} figuras, {tabs} tablas)")
    problemas = revisar(numeros)
    if problemas:
        print("\nRevisar:")
        for pr in problemas:
            print(" -", pr)
        sys.exit(1)
    print("Reglas de redacción: sin problemas.")
