"""Inserta los capítulos IV (continuación de 4.1 en adelante) y V en el documento completo del alumno,
con el formato de ese documento (estilos Heading, rótulos SEQ, tablas con el molde de la Tabla 2).

Uso: python insertar_en_documento.py documento_ajustado.docx figuras_dir salida.docx
"""
import copy
import re
import sys
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt

import contenido_movil as K

ORIGEN, FIGURAS, DESTINO = sys.argv[1], Path(sys.argv[2]), sys.argv[3]
d = Document(ORIGEN)
FUENTE = "Times New Roman"


# ------------------------------------------------------------------ numeración
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


NUM = numerar()


def resolver(t):
    return re.sub(r"\[\[([a-z]+:[a-z_0-9]+)\]\]",
                  lambda m: f"{'Figura' if m.group(1).startswith('fig') else 'Tabla'} {NUM[m.group(1)]}", t)


# ------------------------------------------------------------------ utilerías de runs
def fmt(run, negrita=None, cursiva=None, tam=None):
    rpr = run._r.get_or_add_rPr()
    fonts = rpr.find(qn("w:rFonts"))
    if fonts is None:
        fonts = OxmlElement("w:rFonts")
        rpr.insert(0, fonts)
    for a in ("w:ascii", "w:hAnsi", "w:cs", "w:eastAsia"):
        fonts.set(qn(a), FUENTE)
    if negrita:
        run.bold = True
    if cursiva:
        run.italic = True
    if tam:
        run.font.size = Pt(tam)
    return run


def texto_inline(p, texto, negrita=False, cursiva=False, tam=None):
    for trozo in re.split(r"(\*\*.+?\*\*|\*.+?\*)", texto):
        if not trozo:
            continue
        if trozo.startswith("**"):
            fmt(p.add_run(trozo[2:-2]), True, cursiva, tam)
        elif trozo.startswith("*"):
            fmt(p.add_run(trozo[1:-1]), negrita, True, tam)
        else:
            fmt(p.add_run(trozo), negrita, cursiva, tam)


def campo(p, instruccion, resultado, tam=10):
    def run_con(el):
        r = fmt(p.add_run(), True, None, tam)
        r._r.append(el)

    for tipo in ("begin", None, "separate"):
        if tipo is None:
            i = OxmlElement("w:instrText")
            i.set(qn("xml:space"), "preserve")
            i.text = f" {instruccion} "
            run_con(i)
        else:
            f = OxmlElement("w:fldChar")
            f.set(qn("w:fldCharType"), tipo)
            run_con(f)
    fmt(p.add_run(str(resultado)), True, None, tam)
    f = OxmlElement("w:fldChar")
    f.set(qn("w:fldCharType"), "end")
    run_con(f)


# ------------------------------------------------------------------ constructores (devuelven el elemento XML)
def nuevo_parrafo(estilo=None):
    p = d.add_paragraph(style=estilo) if estilo else d.add_paragraph()
    return p


def p_texto(texto):
    p = nuevo_parrafo()
    p.paragraph_format.first_line_indent = Cm(1.25)
    texto_inline(p, resolver(texto))
    return p._p


def p_titulo(estilo, texto):
    p = nuevo_parrafo(estilo)
    fmt(p.add_run(texto))
    return p._p


def p_vineta(texto):
    p = nuevo_parrafo("Viñeta")
    fmt(p.add_run("•\t"))
    texto_inline(p, resolver(texto))
    return p._p


def p_rotulo(etiqueta, numero, nombre, reiniciar=None, arriba=True, formato="ARABIC"):
    p = nuevo_parrafo()
    pf = p.paragraph_format
    pf.keep_with_next = arriba
    pf.space_before = Pt(6)
    pf.space_after = Pt(4)
    pf.line_spacing = 1.0
    pf.first_line_indent = Cm(0)
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT if arriba else WD_ALIGN_PARAGRAPH.CENTER
    fmt(p.add_run(f"{etiqueta} "), True, None, 10)
    campo(p, f"SEQ {etiqueta} \\* {formato}" + (f" \\r {reiniciar}" if reiniciar else ""), numero)
    fmt(p.add_run(". "), True, None, 10)
    texto_inline(p, nombre, cursiva=True, tam=10)
    return p._p


def p_figura(archivo, ancho):
    p = nuevo_parrafo()
    pf = p.paragraph_format
    pf.keep_with_next = True
    pf.space_before = Pt(6)
    pf.line_spacing = 1.0
    pf.first_line_indent = Cm(0)
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(str(FIGURAS / archivo), width=Cm(ancho))
    return p._p


def p_espacio():
    p = nuevo_parrafo()
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.line_spacing = 1.0
    p.paragraph_format.first_line_indent = Cm(0)
    return p._p


TABLA_MOLDE = copy.deepcopy(d.tables[1]._tbl)
ANCHO_DXA = 9123


def _fila(tr_molde, textos, anchos, centro, encabezado):
    tr = copy.deepcopy(tr_molde)
    tcs = tr.findall(qn("w:tc"))
    base = copy.deepcopy(tcs[0])
    for tc in tcs:
        tr.remove(tc)
    for i, (t, w) in enumerate(zip(textos, anchos)):
        tc = copy.deepcopy(base)
        tc.find(qn("w:tcPr")).find(qn("w:tcW")).set(qn("w:w"), str(w))
        par = tc.find(qn("w:p"))
        run = par.find(qn("w:r"))
        run.find(qn("w:t")).text = t
        jc = par.find(qn("w:pPr")).find(qn("w:jc"))
        jc.set(qn("w:val"), "center" if i in centro else "left")
        tr.append(tc)
    return tr


def tabla(encabezados, filas, anchos_cm, centro):
    total = sum(anchos_cm)
    anchos = [round(ANCHO_DXA * a / total) for a in anchos_cm]
    anchos[-1] += ANCHO_DXA - sum(anchos)
    tbl = copy.deepcopy(TABLA_MOLDE)
    trs = tbl.findall(qn("w:tr"))
    cab, dato = trs[0], trs[1]
    for tr in trs:
        tbl.remove(tr)
    grid = tbl.find(qn("w:tblGrid"))
    for g in list(grid):
        grid.remove(g)
    for w in anchos:
        g = OxmlElement("w:gridCol")
        g.set(qn("w:w"), str(w))
        grid.append(g)
    tbl.append(_fila(cab, encabezados, anchos, centro, True))
    for f in filas:
        tbl.append(_fila(dato, f, anchos, centro, False))
    return tbl


# ------------------------------------------------------------------ armar secciones
def construir(items):
    """Devuelve la lista de elementos XML para esos items, en orden."""
    els, primera_tabla = [], True
    for it in items:
        t = it[0]
        if t == "h3":
            els.append(p_titulo("Heading 2", it[1]))
        elif t == "h4":
            els.append(p_titulo("Heading 3", it[1].upper()))
        elif t == "p":
            els.append(p_texto(it[1]))
        elif t == "bul":
            els.extend(p_vineta(x) for x in it[1])
        elif t == "fig":
            _, clave, archivo, nombre, ancho = it
            els.append(p_figura(archivo, ancho))
            els.append(p_rotulo("Figura", NUM[f"fig:{clave}"], nombre, arriba=False))
            els.append(p_espacio())
        elif t == "tab":
            _, clave, nombre, enc, filas, anchos, centro = it
            n = NUM[f"tab:{clave}"]
            els.append(p_rotulo("Tabla", n, nombre, reiniciar=n if n == K.TABLAS_PREVIAS + 1 else None))
            els.append(tabla(enc, filas, anchos, centro))
            els.append(p_espacio())
    return els


def trocear():
    iv, v, secciones, actual = [], None, {}, None
    en_v = False
    for it in K.C:
        if it[0] == "corte":
            en_v = True
            continue
        if not en_v:
            iv.append(it)
            continue
        if it[0] in ("h1", "h2"):
            continue
        if it[0] == "h3":
            actual = it[1].split(".")[0] + "." + it[1].split(".")[1].split(" ")[0]  # "5.1", "5.2", "5.3"
            secciones[actual] = []
            continue
        secciones[actual].append(it)
    return iv, secciones


def reemplazar(inicio_texto, items):
    for p in d.paragraphs:
        if p.text.strip().startswith(inicio_texto):
            ref = p._p
            for el in construir(items):
                ref.addprevious(el)
            ref.getparent().remove(ref)
            return
    raise SystemExit(f"No se encontró el marcador: {inicio_texto}")


iv, sec = trocear()
reemplazar("[Redactar al concluir el desarrollo: metodología", iv)
reemplazar("[Redactar al concluir las pruebas", sec["5.1"])
reemplazar("[Redactar en mínimo una cuartilla", sec["5.2"])
reemplazar("[Apartado opcional", sec["5.3"])

# Anexo A: decisiones de diseño
for p in d.paragraphs:
    if p.text.strip().startswith("En este apartado se incluirá todo aquel material"):
        p.runs[0].text = "El material de apoyo de esta memoria son las decisiones de diseño citadas en esta memoria, con lo que se decidió y su consecuencia, y se presentan en el Anexo A."
        for r_extra in p.runs[1:]:
            r_extra.text = ""
        ref = p._p
        filas = [list(x) for x in K.ANEXO_DECISIONES]
        for el in (p_rotulo("Anexo", "A", "Decisiones de diseño citadas en esta memoria", formato="ALPHABETIC"),
                   tabla(["Núm.", "Decisión", "Qué se decidió", "Consecuencia"], filas, [1.4, 3.3, 6.3, 4.1], (0,)), p_espacio()):
            ref.addnext(el)
            ref = el
        break
else:
    raise SystemExit("No se encontró el texto de ANEXOS")

# quitar los párrafos vacíos que python-docx dejó al final (los elementos se movieron, no hay residuos), y guardar
d.save(DESTINO)
print("Listo:", DESTINO, "| tablas nuevas:", sum(k.startswith("tab") for k in NUM), "| figuras:", sum(k.startswith("fig") for k in NUM))
