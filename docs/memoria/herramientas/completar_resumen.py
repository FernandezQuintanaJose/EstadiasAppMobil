"""Resumen, abstract, palabras clave y dos siglas nuevas en el documento completo."""
import copy
import sys
from docx import Document

RUTA = sys.argv[1]
d = Document(RUTA)

RESUMEN = ("Las personas ciegas o con baja visión que atiende el Centro de Capacitación para Invidentes, A. C. (CECAPI), en Durango, dependen con "
           "frecuencia de otras personas para usar su teléfono, y las herramientas generales, como el lector de pantalla, exigen aprender gestos y recorrer "
           "cada elemento de la pantalla. Para atender esa necesidad se desarrolló una aplicación móvil Android de asistente por voz, con una interfaz "
           "accesible, un motor de voz único con palabra de activación, comandos globales y propios de cada pantalla, cuentas de usuario y un APK de "
           "instalación directa. El trabajo siguió un desarrollo incremental por módulos, con ramas de trabajo, revisiones de integración periódicas y "
           "decisiones de diseño documentadas, y las pantallas se recorrieron con el APK instalado en un teléfono Android. Como resultado, la aplicación "
           "cuenta con 17 destinos de navegación, una pantalla dividida con el micrófono fijo, tamaños de botón unificados, reconocimiento y síntesis de voz con "
           "tolerancia a errores, escucha en segundo plano opcional y cuentas locales con contraseñas de al menos 15 letras protegidas con Argon2id. La "
           "validación del correo con un código y las cuentas del servidor, así como la firma y la publicación del APK, forman la siguiente etapa.")
ABSTRACT = ("Blind and low-vision people served by the Centro de Capacitación para Invidentes, A. C. (CECAPI), in Durango, often depend on other people "
            "to use their phones, and general tools such as screen readers require learning gestures and moving through every element on the screen. "
            "To address this need, an Android voice assistant mobile application was developed, with an accessible interface, a single voice engine with "
            "a wake word, global and screen-specific commands, user accounts and a directly installable APK. The work followed an incremental, "
            "module-based development process, with working branches, periodic integration reviews and documented design decisions, and the screens were "
            "walked through with the APK installed on an Android phone. As a result, the application has 17 navigation destinations, a split screen with "
            "a fixed microphone, unified button sizes, speech recognition and synthesis tolerant to errors, optional background listening and local accounts "
            "with passwords of at least 15 letters protected with Argon2id. Email validation with a code and server accounts, as well as the signing "
            "and publication of the APK, make up the next stage.")

for p in d.paragraphs:
    t = p.text.strip()
    if t.startswith("[Redactar al concluir el capítulo V"):
        p.runs[0].text = RESUMEN
    elif t.startswith("Palabras clave: ["):
        p.runs[0].text = "Palabras clave: accesibilidad, asistente de voz, discapacidad visual, Android, aplicación móvil."
        molde_clave = p
    elif t.startswith("[Versión en inglés"):
        p.runs[0].text = ABSTRACT
        nuevo = copy.deepcopy(molde_clave._p)
        p._p.addnext(nuevo)
        from docx.text.paragraph import Paragraph
        Paragraph(nuevo, p._parent).runs[0].text = "Keywords: accessibility, voice assistant, visual impairment, Android, mobile application."

# siglas nuevas
t = d.tables[0]
filas = {r.cells[0].text: r for r in t.rows}


def agregar_antes(clave, sigla, significado):
    ref = filas[clave]._tr
    nueva = copy.deepcopy(filas["HTTPS"]._tr)
    ref.addprevious(nueva)
    from docx.table import _Row
    f = _Row(nueva, t)
    f.cells[0].paragraphs[0].runs[0].text = sigla
    f.cells[1].paragraphs[0].runs[0].text = significado


agregar_antes("HTTPS", "HTTP", "Protocolo de transferencia de hipertexto (Hypertext Transfer Protocol)")
agregar_antes("SQL", "SHA-256", "Algoritmo de resumen seguro de 256 bits (Secure Hash Algorithm 256)")
# que Word actualice índices y rótulos al abrir
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
st = d.settings.element
if st.find(qn("w:updateFields")) is None:
    uf = OxmlElement("w:updateFields")
    uf.set(qn("w:val"), "true")
    despues = ("hdrShapeDefaults", "footnotePr", "endnotePr", "compat", "docVars", "rsids", "mathPr", "attachedSchema", "themeFontLang", "clrSchemeMapping", "doNotIncludeSubdocsInStats", "doNotAutoCompressPictures", "forceUpgrade", "captions", "readModeInkLockDown", "smartTagType", "schemaLibrary", "shapeDefaults", "doNotEmbedSmartTags", "decimalSymbol", "listSeparator")
    for hijo in st:
        if hijo.tag.split("}")[1] in despues:
            hijo.addprevious(uf)
            break
    else:
        st.append(uf)
d.save(RUTA)
print("resumen:", len(RESUMEN.split()), "palabras; abstract:", len(ABSTRACT.split()))
