"""Arma Memoria_Movil_Cap_IV_V.docx (formato UTD) con el contenido de contenido_movil.py y valida las reglas de redacción."""
import re
import sys
from pathlib import Path

from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Cm, Pt

import contenido_movil as K
import docx_base as B
from generar_memoria import limpiar_marcas, PRIMERA_PERSONA

RAIZ = Path(__file__).resolve().parent.parent
FIGURAS = RAIZ / "figuras"
SALIDA = RAIZ / "Memoria_Movil_Cap_IV_V.docx"


def numerar():
    nf, nt, num = K.FIGURAS_PREVIAS, K.TABLAS_PREVIAS, {}
    for it in K.C:
        if it[0] == "fig":
            nf += 1
            num[f"fig:{it[1]}"] = nf
        elif it[0] == "tab":
            nt += 1
            num[f"tab:{it[1]}"] = nt
    return num


def resolver(t, num):
    def f(m):
        k = m.group(1)
        if k not in num:
            raise KeyError(k)
        return f"{'Figura' if k.startswith('fig') else 'Tabla'} {num[k]}"
    return re.sub(r"\[\[([a-z]+:[a-z_0-9]+)\]\]", f, t)


def construir():
    num = numerar()
    doc = B.crear_documento()
    B.encabezado_y_pie(doc, K.CARRERA, K.ALUMNO)
    nf, nt = K.FIGURAS_PREVIAS, K.TABLAS_PREVIAS
    for it in K.C:
        t = it[0]
        if t in ("h1", "h2", "h3", "h4"):
            doc.add_paragraph(it[1], style={"h1": "Heading 1", "h2": "Heading 2", "h3": "Heading 3", "h4": "Heading 4"}[t])
        elif t == "p":
            B.agregar_texto(doc.add_paragraph(style="Texto"), resolver(it[1], num))
        elif t == "bul":
            for x in it[1]:
                par = doc.add_paragraph(style="Viñeta")
                par.add_run("●\t")
                par.paragraph_format.tab_stops.add_tab_stop(Cm(1.9))
                B.agregar_texto(par, resolver(x, num))
        elif t == "ref":
            B.agregar_texto(doc.add_paragraph(style="Referencia"), it[1])
        elif t == "fig":
            nf += 1
            _, _, archivo, nombre, ancho = it
            par = doc.add_paragraph()
            par.alignment = WD_ALIGN_PARAGRAPH.CENTER
            par.paragraph_format.first_line_indent = Cm(0)
            par.paragraph_format.keep_with_next = True
            par.paragraph_format.space_before = Pt(6)
            par.paragraph_format.line_spacing = 1.0
            par.add_run().add_picture(str(FIGURAS / archivo), width=Cm(ancho))
            B.rotulo(doc, "Figura", nf, nombre)
            sep = doc.add_paragraph()
            sep.paragraph_format.space_after = Pt(6)
            sep.paragraph_format.line_spacing = 1.0
        elif t == "tab":
            nt += 1
            _, _, nombre, enc, filas, anchos, centro = it
            B.rotulo(doc, "Tabla", nt, nombre, mantener_con_siguiente=True, reiniciar=nt if nt == K.TABLAS_PREVIAS + 1 else None)
            B.tabla(doc, enc, filas, anchos, centro)
            sep = doc.add_paragraph()
            sep.paragraph_format.space_after = Pt(6)
            sep.paragraph_format.line_spacing = 1.0
        elif t == "corte":
            B.salto_de_pagina(doc)
    doc.core_properties.title = "Memoria de estadía: capítulos IV y V de la aplicación móvil (borrador)"
    doc.core_properties.author = K.ALUMNO
    doc.save(SALIDA)
    return num


def revisar(num):
    pr, palabras = [], 0
    for it in K.C:
        if it[0] != "p":
            continue
        txt = limpiar_marcas(resolver(it[1], num)).strip()
        n = len(txt.split())
        palabras += n
        if n < 40:
            pr.append(f"Párrafo de {n} palabras: {txt[:70]}...")
        m = PRIMERA_PERSONA.search(txt)
        if m:
            pr.append(f"Primera persona '{m.group(0)}': {txt[:70]}...")
    texto = " ".join(i[1] for i in K.C if i[0] in ("p", "bul") and isinstance(i[1], str))
    texto += " ".join(" ".join(i[1]) for i in K.C if i[0] == "bul")
    ment = set(re.findall(r"\[\[([a-z]+:[a-z_0-9]+)\]\]", texto))
    for k in num:
        if k not in ment:
            pr.append(f"{k} no se menciona en el texto")
    todo = " ".join(" ".join(map(str, i)) for i in K.C).lower()
    for mala in (r"\{\{", r"\bpendientes?\b", r"confirmar con", r"\bverificar\b", r"claude", r"asistentes de ia", r"\bagentes?\b"):
        if re.search(mala, todo):
            pr.append(f"Aparece una expresión no deseada: {mala}")
    print(f"  Palabras de texto corrido: {palabras}")
    return pr


if __name__ == "__main__":
    num = construir()
    print(f"Generado: {SALIDA.name} ({sum(k.startswith('fig') for k in num)} figuras, {sum(k.startswith('tab') for k in num)} tablas nuevas)")
    pr = revisar(num)
    if pr:
        print("Revisar:")
        for x in pr:
            print(" -", x)
        sys.exit(1)
    print("Reglas de redacción: sin problemas.")
