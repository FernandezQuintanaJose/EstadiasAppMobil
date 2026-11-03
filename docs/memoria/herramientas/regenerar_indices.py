"""Regenera el contenido guardado de los índices (contenido, figuras, tablas y anexos) con las páginas reales.

Las páginas se leen del PDF que produce LibreOffice (el número impreso en el pie de cada página). Word vuelve a
calcular los campos al abrir el archivo, pero así el documento ya se ve completo en cualquier visor.

Uso: python regenerar_indices.py documento.docx
"""
import copy
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from docx import Document
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

RUTA = Path(sys.argv[1]).resolve()


def norm(t):
    return " ".join(t.split())


def pdf_de(docx):
    salida = Path(tempfile.mkdtemp(prefix="idx_"))
    subprocess.run(["soffice", "--headless", "--convert-to", "pdf", "--outdir", str(salida), str(docx)],
                   check=True, capture_output=True)
    return salida / (docx.stem + ".pdf")


def paginas(pdf):
    n = int(re.search(r"Pages:\s+(\d+)", subprocess.run(["pdfinfo", str(pdf)], capture_output=True, text=True).stdout).group(1))
    res = []
    for i in range(1, n + 1):
        t = subprocess.run(["pdftotext", "-f", str(i), "-l", str(i), "-layout", str(pdf), "-"], capture_output=True, text=True).stdout
        lineas = [l.strip() for l in t.splitlines() if l.strip()]
        etiqueta = None
        if lineas and "Fernández Quintana" in lineas[-1]:
            etiqueta = lineas[-1].split()[-1]
        res.append((etiqueta, lineas))
    return res


def run_campo(tipo):
    r = OxmlElement("w:r")
    f = OxmlElement("w:fldChar")
    f.set(qn("w:fldCharType"), tipo)
    r.append(f)
    return r


def run_instr(texto):
    r = OxmlElement("w:r")
    i = OxmlElement("w:instrText")
    i.set(qn("xml:space"), "preserve")
    i.text = f" {texto} "
    r.append(i)
    return r


def instruccion_de(par):
    m = re.search(r"<w:instrText[^>]*>([^<]*)<", par._p.xml)
    return m.group(1).strip() if m else None


def calcular(d, pag):
    """Devuelve las entradas (nivel, texto, página) de los cuatro índices según el PDF."""
    ps = d.paragraphs
    idx = {norm(p.text): i for i, p in enumerate(ps) if p.style is not None and p.style.name == "Titulo preliminar"}
    ini_idx = next(i for i, (_, l) in enumerate(pag) if any(x == "ÍNDICE DE CONTENIDO" for x in l))
    fin_idx = next(i for i, (_, l) in enumerate(pag) if any(x == "ÍNDICE DE ANEXOS" for x in l))
    indices = set(range(ini_idx, fin_idx + 1))
    cuerpo_desde = fin_idx + 1
    # encabezados
    entradas_toc, actual = [], cuerpo_desde
    i_anexos = idx["ÍNDICE DE ANEXOS"]
    for p in ps[i_anexos + 1:]:
        nombre = p.style.name if p.style is not None else ""
        if nombre.startswith("toc"):
            continue
        es_intro = nombre == "Titulo preliminar" and norm(p.text) == "INTRODUCCIÓN"
        if nombre in ("Heading 1", "Heading 2", "Heading 3") or es_intro:
            nivel = 1 if es_intro else int(nombre[-1])
            primera = norm(p.text.split("\n")[0]) if nivel == 1 and "\n" in p.text else norm(p.text)
            titulo = norm(p.text)
            exacta = nivel == 1 and "\n" in p.text
            prefijo = primera.upper() if exacta else titulo.upper()[:30]
            hallada = None
            for k in range(actual, len(pag)):
                for l in pag[k][1]:
                    if (exacta and l.upper() == prefijo) or (not exacta and l.upper().startswith(prefijo)):
                        hallada = k
                        break
                if hallada is not None:
                    break
            if hallada is None:
                raise SystemExit(f"No se encontró en el PDF el encabezado: {titulo}")
            actual = hallada
            entradas_toc.append((nivel, titulo, pag[hallada][0] or "?"))
    # rótulos
    por_tipo = {"Figura": [], "Tabla": [], "Anexo": []}
    for p in ps:
        nombre = p.style.name if p.style is not None else ""
        if nombre.startswith("toc") or nombre == "Titulo preliminar":
            continue
        m = re.match(r"^(Tabla|Figura|Anexo) (\d+|[A-Z])\. ", norm(p.text))
        if not m:
            continue
        texto = norm(p.text)
        prefijo = texto[:28]
        hallada = None
        for k, (_, lineas) in enumerate(pag):
            if k in indices:
                continue
            if any(l.startswith(prefijo) for l in lineas):
                hallada = k
                break
        if hallada is None:
            raise SystemExit(f"No se encontró en el PDF el rótulo: {texto[:50]}")
        por_tipo[m.group(1)].append((1, texto, pag[hallada][0] or "?"))
    return entradas_toc, por_tipo


def reconstruir(d, toc, por_tipo):
    ps = d.paragraphs
    plantilla = next(p for p in ps if p.style is not None and p.style.name == "toc 2")
    idx = {norm(p.text): i for i, p in enumerate(ps) if p.style is not None and p.style.name == "Titulo preliminar"}
    bloques = [("ÍNDICE DE CONTENIDO", toc), ("ÍNDICE DE FIGURAS", por_tipo["Figura"]),
               ("ÍNDICE DE TABLAS", por_tipo["Tabla"]), ("ÍNDICE DE ANEXOS", por_tipo["Anexo"])]
    for titulo, entradas in bloques:
        ps = d.paragraphs
        i = next(k for k, p in enumerate(ps) if p.style is not None and p.style.name == "Titulo preliminar" and norm(p.text) == titulo)
        # entradas actuales: párrafos toc justo después del título
        viejos = []
        k = i + 1
        while k < len(ps) and ps[k].style is not None and ps[k].style.name.startswith("toc"):
            viejos.append(ps[k])
            k += 1
        instr = instruccion_de(viejos[0])
        ancla = ps[i]._p
        nuevos = []
        for n, (nivel, texto, pagina) in enumerate(entradas):
            el = copy.deepcopy(plantilla._p)
            el.find(qn("w:pPr")).find(qn("w:pStyle")).set(qn("w:val"), f"TOC{nivel}")
            runs = el.findall(qn("w:r"))
            for r in runs:
                el.remove(r)
            runs_nuevos = []
            if n == 0:
                runs_nuevos += [run_campo("begin"), run_instr(instr), run_campo("separate")]
            plantilla_run = runs[0]
            r1 = copy.deepcopy(plantilla_run)
            r1.find(qn("w:t")).text = texto
            r2 = copy.deepcopy(plantilla_run)
            for hijo in list(r2):
                if hijo.tag != qn("w:rPr"):
                    r2.remove(hijo)
            tab = OxmlElement("w:tab")
            r2.append(tab)
            r3 = copy.deepcopy(plantilla_run)
            r3.find(qn("w:t")).text = str(pagina)
            runs_nuevos += [r1, r2, r3]
            if n == len(entradas) - 1:
                runs_nuevos.append(run_campo("end"))
            for r in runs_nuevos:
                el.append(r)
            nuevos.append(el)
        for el in nuevos:
            ancla.addnext(el)
            ancla = el
        for v in viejos:
            v._p.getparent().remove(v._p)


anterior = None
for vuelta in range(4):
    d = Document(str(RUTA))
    pdf = pdf_de(RUTA)
    pag = paginas(pdf)
    toc, por_tipo = calcular(d, pag)
    actual = (toc, por_tipo)
    if actual == anterior:
        print(f"Índices estables tras {vuelta} ajustes.")
        break
    reconstruir(d, toc, por_tipo)
    d.save(str(RUTA))
    anterior = actual
else:
    print("Aviso: las páginas no se estabilizaron del todo; Word las recalcula al abrir.")
print("Entradas: contenido", len(toc), "· figuras", len(por_tipo["Figura"]), "· tablas", len(por_tipo["Tabla"]), "· anexos", len(por_tipo["Anexo"]))
