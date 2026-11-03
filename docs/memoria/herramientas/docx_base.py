"""Utilidades para armar el documento con el formato del Manual de la Memoria de Estadía de la UTD.

Formato que aplica (manual, capítulo III):
  - Carta, márgenes: izquierdo 3.00 cm, derecho 2.50 cm, superior 2.50 cm, inferior 2.50 cm.
  - Times New Roman 12, interlineado 1.5, justificado, sangría de primera línea de 1.25 cm.
  - Título 1: TNR 14, negritas, centrado, mayúsculas.   Título 2: TNR 12, negritas, centrado, mayúsculas.
  - Título 3: TNR 12, negritas, justificado, mayúsculas. Título 4: TNR 12, negritas, justificado, cursiva.
  - Rótulos de figuras y tablas: "Figura N." en negritas y el nombre en cursiva, TNR 10, con campo SEQ de Word,
    para que Referencias > Insertar tabla de ilustraciones funcione.
  - Encabezado: carrera a la izquierda y UTD a la derecha. Pie: DOCUMENTO, apellidos y nombre, número de página.
Marcas en el texto:  **negritas**   *cursiva*   {{texto pendiente}} (se resalta en amarillo, como la plantilla).
"""
import re

from docx import Document
from docx.enum.section import WD_ORIENT
from docx.enum.style import WD_STYLE_TYPE
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_COLOR_INDEX, WD_LINE_SPACING, WD_TAB_ALIGNMENT
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

FUENTE = "Times New Roman"
ANCHO_UTIL_CM = 21.59 - 3.0 - 2.5


def _fuente(estilo, tam, negrita=None, cursiva=None):
    estilo.font.name = FUENTE
    estilo.font.size = Pt(tam)
    estilo.font.color.rgb = RGBColor(0, 0, 0)
    if negrita is not None:
        estilo.font.bold = negrita
    if cursiva is not None:
        estilo.font.italic = cursiva
    rpr = estilo.element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    for atributo in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        rfonts.set(qn(atributo), FUENTE)
    for atributo in ("w:asciiTheme", "w:hAnsiTheme", "w:eastAsiaTheme", "w:cstheme"):
        if rfonts.get(qn(atributo)) is not None:
            del rfonts.attrib[qn(atributo)]


def crear_documento():
    doc = Document()
    seccion = doc.sections[0]
    seccion.orientation = WD_ORIENT.PORTRAIT
    seccion.page_width, seccion.page_height = Cm(21.59), Cm(27.94)
    seccion.left_margin, seccion.right_margin = Cm(3.0), Cm(2.5)
    seccion.top_margin, seccion.bottom_margin = Cm(2.5), Cm(2.5)
    seccion.header_distance = Cm(1.25)
    seccion.footer_distance = Cm(1.25)

    estilos = doc.styles
    normal = estilos["Normal"]
    _fuente(normal, 12)
    normal.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    normal.paragraph_format.line_spacing = 1.5
    normal.paragraph_format.space_after = Pt(0)
    normal.paragraph_format.space_before = Pt(0)

    # Título 1..4 según el manual
    reglas = {
        "Heading 1": (14, True, False, WD_ALIGN_PARAGRAPH.CENTER, 18, 6),
        "Heading 2": (12, True, False, WD_ALIGN_PARAGRAPH.CENTER, 0, 18),
        "Heading 3": (12, True, False, WD_ALIGN_PARAGRAPH.JUSTIFY, 18, 12),
        "Heading 4": (12, True, True, WD_ALIGN_PARAGRAPH.JUSTIFY, 12, 12),
    }
    for nombre, (tam, negrita, cursiva, alineacion, antes, despues) in reglas.items():
        e = estilos[nombre]
        _fuente(e, tam, negrita, cursiva)
        e.paragraph_format.alignment = alineacion
        e.paragraph_format.space_before = Pt(antes)
        e.paragraph_format.space_after = Pt(despues)
        e.paragraph_format.line_spacing = 1.5
        e.paragraph_format.keep_with_next = True
        e.paragraph_format.first_line_indent = Cm(0)

    texto = estilos.add_style("Texto", WD_STYLE_TYPE.PARAGRAPH)
    texto.base_style = normal
    _fuente(texto, 12)
    texto.paragraph_format.first_line_indent = Cm(1.25)
    texto.paragraph_format.space_after = Pt(12)
    texto.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY

    vineta = estilos.add_style("Viñeta", WD_STYLE_TYPE.PARAGRAPH)
    vineta.base_style = normal
    _fuente(vineta, 12)
    vineta.paragraph_format.left_indent = Cm(1.9)
    vineta.paragraph_format.first_line_indent = Cm(-0.65)
    vineta.paragraph_format.space_after = Pt(4)
    vineta.paragraph_format.line_spacing = 1.5

    rotulo = estilos["Caption"]
    _fuente(rotulo, 10, False, False)
    rotulo.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.CENTER
    rotulo.paragraph_format.line_spacing = 1.0
    rotulo.paragraph_format.space_before = Pt(0)
    rotulo.paragraph_format.space_after = Pt(0)

    celda = estilos.add_style("Celda", WD_STYLE_TYPE.PARAGRAPH)
    celda.base_style = normal
    _fuente(celda, 10)
    celda.paragraph_format.line_spacing = 1.0
    celda.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    celda.paragraph_format.first_line_indent = Cm(0)

    ref = estilos.add_style("Referencia", WD_STYLE_TYPE.PARAGRAPH)
    ref.base_style = normal
    _fuente(ref, 12)
    ref.paragraph_format.left_indent = Cm(1.25)
    ref.paragraph_format.first_line_indent = Cm(-1.25)  # sangría francesa
    ref.paragraph_format.space_after = Pt(10)
    ref.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    return doc


# ----------------------------------------------------------------------------------- texto con marcas
_PATRON = re.compile(r"(\*\*.+?\*\*|\*.+?\*|\{\{.+?\}\})", re.S)


def agregar_texto(parrafo, texto, negrita=False, cursiva=False, tam=None):
    """Agrega texto con **negritas**, *cursivas* y {{pendientes}} al párrafo."""
    for trozo in _PATRON.split(texto):
        if not trozo:
            continue
        b, c, resaltar = negrita, cursiva, False
        if trozo.startswith("**") and trozo.endswith("**"):
            trozo, b = trozo[2:-2], True
        elif trozo.startswith("{{") and trozo.endswith("}}"):
            trozo, resaltar = "[" + trozo[2:-2] + "]", True
        elif trozo.startswith("*") and trozo.endswith("*") and len(trozo) > 2:
            trozo, c = trozo[1:-1], True
        run = parrafo.add_run(trozo)
        run.bold = b or None
        run.italic = c or None
        if tam:
            run.font.size = Pt(tam)
        if resaltar:
            run.font.highlight_color = WD_COLOR_INDEX.YELLOW
    return parrafo


def campo(parrafo, instruccion, resultado, negrita=False, cursiva=False, tam=None):
    """Campo de Word (por ejemplo SEQ o PAGE) con su resultado ya calculado."""
    def run_con(elemento):
        r = parrafo.add_run()
        r.bold = negrita or None
        r.italic = cursiva or None
        if tam:
            r.font.size = Pt(tam)
        r._r.append(elemento)
        return r

    inicio = OxmlElement("w:fldChar")
    inicio.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = f" {instruccion} "
    separar = OxmlElement("w:fldChar")
    separar.set(qn("w:fldCharType"), "separate")
    fin = OxmlElement("w:fldChar")
    fin.set(qn("w:fldCharType"), "end")
    run_con(inicio)
    run_con(instr)
    run_con(separar)
    r = parrafo.add_run(str(resultado))
    r.bold = negrita or None
    r.italic = cursiva or None
    if tam:
        r.font.size = Pt(tam)
    run_con(fin)


def rotulo(doc, etiqueta, numero, nombre, mantener_con_siguiente=False, reiniciar=None):
    p = doc.add_paragraph(style="Caption")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.keep_with_next = mantener_con_siguiente
    r = p.add_run(f"{etiqueta} ")
    r.bold = True
    campo(p, f"SEQ {etiqueta} \\* ARABIC" + (f" \\r {reiniciar}" if reiniciar else ""), numero, negrita=True)
    r = p.add_run(". ")
    r.bold = True
    agregar_texto(p, nombre, cursiva=True)
    return p


# ----------------------------------------------------------------------------------- tablas
def _sombrear(celda, color):
    tc_pr = celda._tc.get_or_add_tcPr()
    sombra = OxmlElement("w:shd")
    sombra.set(qn("w:val"), "clear")
    sombra.set(qn("w:color"), "auto")
    sombra.set(qn("w:fill"), color)
    tc_pr.append(sombra)


def _margenes_celda(tabla, arriba=40, abajo=40, lados=80):
    tbl_pr = tabla._tbl.tblPr
    margenes = OxmlElement("w:tblCellMar")
    for lado, valor in (("top", arriba), ("left", lados), ("bottom", abajo), ("right", lados)):
        e = OxmlElement(f"w:{lado}")
        e.set(qn("w:w"), str(valor))
        e.set(qn("w:type"), "dxa")
        margenes.append(e)
    tbl_pr.append(margenes)


def tabla(doc, encabezados, filas, anchos_cm, alinear_centro=()):
    t = doc.add_table(rows=1, cols=len(encabezados))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    _margenes_celda(t)
    for i, texto in enumerate(encabezados):
        c = t.rows[0].cells[i]
        c.width = Cm(anchos_cm[i])
        _sombrear(c, "D9D9D9")
        p = c.paragraphs[0]
        p.style = "Celda"
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        agregar_texto(p, texto, negrita=True)
    fila_enc = t.rows[0]._tr
    tr_pr = fila_enc.get_or_add_trPr()
    rep = OxmlElement("w:tblHeader")
    rep.set(qn("w:val"), "true")
    tr_pr.append(rep)
    for fila in filas:
        celdas = t.add_row().cells
        for i, texto in enumerate(fila):
            celdas[i].width = Cm(anchos_cm[i])
            p = celdas[i].paragraphs[0]
            p.style = "Celda"
            if i in alinear_centro:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            partes = str(texto).split("\n")
            agregar_texto(p, partes[0])
            for extra in partes[1:]:
                p2 = celdas[i].add_paragraph(style="Celda")
                agregar_texto(p2, extra)
        no_partir = OxmlElement("w:cantSplit")
        no_partir.set(qn("w:val"), "true")
        t.rows[-1]._tr.get_or_add_trPr().append(no_partir)
    return t


# ----------------------------------------------------------------------------------- encabezado y pie
def encabezado_y_pie(doc, carrera_texto, alumno_texto, pie_documento="DOCUMENTO"):
    seccion = doc.sections[0]
    ancho = Cm(ANCHO_UTIL_CM)
    enc = seccion.header
    p = enc.paragraphs[0]
    p.style = doc.styles["Normal"]
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.line_spacing = 1.0
    p.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p.paragraph_format.tab_stops.add_tab_stop(ancho, WD_TAB_ALIGNMENT.RIGHT)
    agregar_texto(p, carrera_texto, tam=11)
    r = p.add_run("\tUTD")
    r.font.size = Pt(11)

    pie = seccion.footer
    p1 = pie.paragraphs[0]
    p1.style = doc.styles["Normal"]
    p1.paragraph_format.first_line_indent = Cm(0)
    p1.paragraph_format.line_spacing = 1.0
    p1.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    r = p1.add_run(pie_documento)
    r.bold = True
    r.font.size = Pt(11)
    p2 = pie.add_paragraph(style="Normal")
    p2.paragraph_format.first_line_indent = Cm(0)
    p2.paragraph_format.line_spacing = 1.0
    p2.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p2.paragraph_format.tab_stops.add_tab_stop(ancho, WD_TAB_ALIGNMENT.RIGHT)
    agregar_texto(p2, alumno_texto, cursiva=True, tam=11)
    r = p2.add_run("\t")
    campo(p2, "PAGE", 1, cursiva=True, tam=11)


def salto_de_pagina(doc):
    doc.add_page_break()
