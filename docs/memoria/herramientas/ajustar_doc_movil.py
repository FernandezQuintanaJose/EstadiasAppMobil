"""Aplica al documento del alumno los ajustes del Capítulo II, el párrafo de 4.1, el 3.10 y la Tabla 2."""
import copy
import sys
from docx import Document

ORIGEN, DESTINO = sys.argv[1], sys.argv[2]
d = Document(ORIGEN)

CAMBIOS = [
    # (inicio del párrafo, texto viejo, texto nuevo)
    ("Se plantea también", ", hipótesis que se comprobará en las pruebas con los alumnos; una aplicación", "; una aplicación"),
    ("•\tConstruir las pantallas de registro",
     "(una cuenta propia con un correo electrónico existente y una contraseña pensada para el uso por voz, validada con un código enviado a ese correo, que también se usa para recuperar la contraseña), para identificar a cada usuario y conservar sus conversaciones.",
     "(una cuenta propia con una contraseña pensada para el uso por voz, protegida con Argon2id), y dejar preparada la validación del correo electrónico con un código enviado por el servidor central, que también se usará para recuperar la contraseña, para identificar a cada usuario y conservar sus conversaciones."),
    ("La aplicación incluye interfaz accesible",
     "(cuenta propia validada con un código enviado al correo), pantallas de políticas de privacidad y de uso que se leen en pantalla y por audio, con la declaración de mayoría de edad y su registro, y distribución en APK",
     "(cuenta propia con una contraseña pensada para el uso por voz, preparada para validar el correo con un código que enviará el servidor central), confirmación de mayoría de edad con su fecha de registro, y distribución en APK"),
    ("La aplicación incluye interfaz accesible",
     "Se realizarán pruebas en el CECAPI para comprobar el funcionamiento de la aplicación, mediante muestras de resultados y de desempeño durante el desarrollo y en el periodo de evaluación.",
     "El funcionamiento de la aplicación se comprueba durante el desarrollo mediante recorridos de prueba en el teléfono."),
    ("Los términos de uso de la API", "como el APK se publica en GitHub Releases", "como el APK se distribuye desde GitHub Releases"),
    ("Los términos de uso de la API",
     "El registro depende de un servicio de correo por interfaz web y de un dominio propio para enviar el código de validación, y el funcionamiento completo",
     "La validación del correo con un código depende de un servicio de correo por interfaz web, de un dominio propio y de que el servidor central publique las rutas de cuenta, y el funcionamiento completo"),
    ("En el proyecto, el aviso de privacidad se presenta",
     "el aviso de privacidad se presenta en la aplicación, se lee en pantalla y por audio, y se acepta con un botón.",
     "el aviso de privacidad se presentará en la aplicación, se leerá en pantalla y por audio, y se aceptará con un botón."),
    ("Las herramientas utilizadas en el desarrollo",
     "al 27 de octubre de 2026, y las herramientas que aún no se incorporan se señalan como tales.",
     "al 2 de noviembre de 2026."),
    ("Se plantea también", "Se parte de la hipótesis de que", "Se considera, además, que"),
    ("Como consecuencia, se espera", "; este beneficio se comprueba con los resultados del capítulo V.", ", que es el propósito que orienta el diseño de la aplicación."),
    ("En los meses previos a la estadía", "se realizan revisiones de integración cada fin de semana, cuyo resultado", "se realizan revisiones de integración periódicas, cuyo resultado"),
    ("La aplicación incluye interfaz accesible", "(el nombre de la aplicación o un nombre personalizable)", "(el nombre del asistente, que la persona puede personalizar, y la palabra hola si se activa)"),
    ("El ingreso a la aplicación se realiza",
     "a la que se entra con un correo electrónico y una contraseña. El correo debe ser un correo existente y se valida con un código que el servidor envía a esa dirección; el mismo procedimiento se usa para recuperar la contraseña.",
     "a la que se entra con un nombre de usuario y una contraseña. El correo electrónico es opcional; cuando se proporciona, debe ser un correo existente y se valida con un código que el servidor envía a esa dirección, y el mismo procedimiento se usa para recuperar la contraseña."),
    ("El ingreso a la aplicación se realiza",
     "(comportamiento de diseño, pendiente de integrarse al código del repositorio)",
     "(la aplicación ya aplica esta normalización y el servidor deberá repetirla)"),
    ("La aplicación trata datos personales",
     "el correo electrónico y las conversaciones con el asistente. No se solicitan datos de salud ni otros datos sensibles, y se advierte al usuario que no los dicte.",
     "el nombre de usuario, el nombre, el rol, la fecha en que la persona confirmó tener 18 años o más, el correo electrónico (opcional), el resumen cifrado de la contraseña y las conversaciones con el asistente. La aplicación no solicita datos de salud y se advierte al usuario que no los dicte; sin embargo, saber que una persona usa una aplicación para personas ciegas puede revelar su discapacidad visual, que el proyecto trata como dato sensible (decisión 0018)."),
    ("La aplicación trata datos personales",
     "(art. 18) (Ley Federal de Protección de Datos Personales en Posesión de los Particulares, 2025).",
     "(art. 18) (Ley Federal de Protección de Datos Personales en Posesión de los Particulares, 2025). Para los datos personales sensibles, el responsable debe obtener el consentimiento expreso y por escrito de la persona titular, mediante firma autógrafa, firma electrónica o cualquier mecanismo de autenticación (art. 8) (Ley Federal de Protección de Datos Personales en Posesión de los Particulares, 2025). Por ello, el mecanismo con que se acepte el aviso de privacidad se someterá a la revisión de una persona abogada antes de usar datos reales (decisión 0005)."),
    ("En el proyecto, el aviso de privacidad se presenta",
     "El responsable del tratamiento es el CECAPI, A. C., y se prevé",
     "Por la decisión 0005, mientras solo haya cuentas de demostración el responsable del tratamiento es el responsable del proyecto, a título personal, y antes de usar datos reales se buscará un convenio con el CECAPI, A. C. Se prevé"),
    ("En el proyecto, el aviso de privacidad se presenta",
     "(Render, s. f.-b), lo que concuerda con que la aplicación sea solo para mayores de 18 años y no trate datos de salud.",
     "(Render, s. f.-b). Las tablas de usuarios de la aplicación no incluyen la discapacidad visual como campo."),
    ("Desarrollar una aplicación móvil Android de asistente por voz",
     "con cuentas de usuario y distribución mediante APK, mediante",
     "con cuentas de usuario y un APK de instalación directa, mediante"),
]
hechos = 0
for inicio, viejo, nuevo in CAMBIOS:
    for p in d.paragraphs:
        if inicio in p.text and viejo in p.text:
            assert len(p.runs) == 1, f"el párrafo '{inicio}' tiene {len(p.runs)} runs"
            p.runs[0].text = p.runs[0].text.replace(viejo, nuevo)
            hechos += 1
            break
    else:
        raise SystemExit(f"No se encontró: {inicio} / {viejo[:40]}")

# ---- Tabla 2
t = d.tables[1]
assert t.rows[0].cells[0].text == "Herramienta"
molde = copy.deepcopy(t.rows[1]._tr)


def poner(tr_el, valores):
    from docx.table import _Row
    fila = _Row(tr_el, t)
    for celda, v in zip(fila.cells, valores):
        celda.paragraphs[0].runs[0].text = v


filas = [
    ("Kotlin", "2.0.21", "Lenguaje de programación"),
    ("Android Gradle Plugin", "8.5.2", "Compilación del proyecto"),
    ("Gradle", "8.9", "Sistema de compilación"),
    ("Jetpack Compose (BOM)", "2024.10.00", "Interfaz de usuario"),
    ("Navigation Compose", "2.8.2", "Navegación entre pantallas"),
    ("Lifecycle (ViewModel)", "2.8.6", "Estado de cada pantalla"),
    ("Coroutines", "1.9.0", "Tareas en segundo plano"),
    ("Room", "2.6.1", "Base de datos local"),
    ("DataStore", "1.1.1", "Ajustes del usuario"),
    ("Hilt", "2.51.1", "Inyección de dependencias"),
    ("Argon2kt", "1.5.0", "Protección de contraseñas con Argon2id"),
    ("Cliente de red (HTTPS)", "Incluido en Android", "Comunicación con el servidor central"),
    ("Servicio en primer plano", "Incluido en Android", "Escucha con la palabra de activación"),
    ("SpeechRecognizer", "Incluido en Android", "Reconocimiento de voz"),
    ("TextToSpeech", "Incluido en Android", "Respuestas habladas"),
    ("Git y GitHub", "No aplica", "Control de versiones del código"),
]
# quitar las filas de datos actuales y regenerar con el molde
for fila in list(t.rows)[1:]:
    t._tbl.remove(fila._tr)
for f in filas:
    nueva = copy.deepcopy(molde)
    t._tbl.append(nueva)
    poner(nueva, f)

# ---- dividir 2.4.2 (Límites) en párrafos por tema
for p in d.paragraphs:
    if p.text.strip().startswith("Los términos de uso de la API"):
        texto = p.runs[0].text
        cortes = ["No se desarrolla una versión para iPhone", "El reconocimiento de voz depende", "El proyecto se desarrolla de septiembre", "La validación del correo con un código depende"]
        trozos, resto = [], texto
        for c in cortes:
            pos = resto.index(c)
            trozos.append(resto[:pos].strip())
            resto = resto[pos:]
        trozos.append(resto.strip())
        p.runs[0].text = trozos[0]
        ref = p._p
        for trozo in trozos[1:]:
            nuevo = copy.deepcopy(p._p)
            ref.addnext(nuevo)
            from docx.text.paragraph import Paragraph
            Paragraph(nuevo, p._parent).runs[0].text = trozo
            ref = nuevo
        print("2.4.2 dividido en", len(trozos), "párrafos")
        break

d.save(DESTINO)
print("Párrafos ajustados:", hechos, "· filas de la Tabla 2:", len(t.rows) - 1)
