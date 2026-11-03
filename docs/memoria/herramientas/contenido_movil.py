"""Capítulos IV (desde la continuación de 4.1) y V de la memoria de la parte móvil Android (módulos 1 y 2).

Datos tomados de EstadiasAppMobil, rama main, commit b8e39d8 (2 de noviembre de 2026).
La Tabla 1 (siglas) y la Tabla 2 (herramientas) ya existen en el documento del alumno: las tablas nuevas empiezan en la 3.
"""

CARRERA = "DESARROLLO DE SOFTWARE MULTIPLATAFORMA"
ALUMNO = "Fernández Quintana José"
TABLAS_PREVIAS = 2
FIGURAS_PREVIAS = 0

C = []


def h1(t): C.append(("h1", t))
def h2(t): C.append(("h2", t))
def h3(t): C.append(("h3", t))
def h4(t): C.append(("h4", t))
def p(t): C.append(("p", t))
def bul(lista): C.append(("bul", lista))
def fig(clave, archivo, nombre, ancho=15.0): C.append(("fig", clave, archivo, nombre, ancho))
def tab(clave, nombre, enc, filas, anchos, centro=()): C.append(("tab", clave, nombre, enc, filas, anchos, centro))
def corte(): C.append(("corte",))


# =====================================================================================================
# CAPÍTULO IV (continuación del apartado 4.1 que ya trae la Tabla 2)
# =====================================================================================================
p("El proyecto es un desarrollo de software aplicado a una necesidad de la institución, y su marco de trabajo fue incremental por módulos independientes: cada módulo se construyó, se integró y se ajustó por separado, con una rama "
  "de trabajo por tarea, revisiones de integración periódicas y decisiones de diseño escritas en documentos numerados que explican el contexto, lo "
  "decidido y sus consecuencias. Cada versión de la aplicación se compiló como APK de instalación directa y se instaló en un teléfono Android para "
  "recorrer sus pantallas.")

h4("4.1.1. Organización del trabajo")

p("La aplicación se divide en siete módulos, cada uno en su propia carpeta dentro de la carpeta feature del proyecto, y una carpeta compartida llamada "
  "core. Esta memoria describe el módulo 1, que reúne el inicio, el menú, las cuentas, los ajustes, la personalización y los chats, y el módulo 2, "
  "el asistente de voz, además del motor de voz, la base de datos y el servicio de escucha de la carpeta compartida. Los demás módulos los desarrollan "
  "otros estudiantes y se describen en las memorias correspondientes; aquí solo se citan como dependencias.")

p("La [[tab:alcance]] resume el tamaño del código descrito en esta memoria, contado en líneas no vacías con los comentarios incluidos, y la "
  "[[fig:avance]] muestra los cambios registrados por semana en esas carpetas hasta el 2 de noviembre de 2026. La mayor actividad se registró en la "
  "semana del 28 de septiembre al 4 de octubre, con 30 cambios, y en la del 26 de octubre al 1 de noviembre, con 21.")

tab("alcance", "Código de los módulos 1 y 2 y su apoyo en core",
    ["Parte", "Carpeta", "Archivos", "Líneas"],
    [["Módulo 1. Aplicación principal", "feature/modulo1_aplicacionprincipal", "35", "8,225"],
     ["Módulo 2. Asistente de voz (pantalla y datos)", "feature/modulo2_asistentevoz", "4", "297"],
     ["Motor y comandos de voz del asistente", "core/voice", "18", "1,711"],
     ["Base de datos y migraciones", "core/data", "3", "356"],
     ["Servicio de escucha en segundo plano", "service", "1", "159"]],
    [5.2, 5.6, 1.9, 2.4], centro=(2, 3))

fig("avance", "m07_cambios_por_semana.png", "Cambios registrados por semana en las carpetas de los módulos 1 y 2", 14.5)

p("Las reglas de colaboración quedaron escritas en una guía del repositorio: cada equipo edita solo la carpeta de su módulo, lo compartido vive en "
  "core, y cada cambio llega con una descripción de lo que modifica y de cómo se probó. Las versiones de las herramientas "
  "de compilación se congelaron desde el inicio (decisión 0001), de modo que Gradle 8.9, el complemento de Android 8.5.2 y Kotlin 2.0.21 no cambian "
  "sin acuerdo, porque una actualización a versiones más nuevas ya había roto la compilación desde un clon limpio.")

h4("4.1.2. Técnicas e instrumentos de recolección de datos")

p("Los datos del proyecto se recolectaron con los instrumentos de la [[tab:instrumentos]]. El historial del repositorio permitió medir el avance por "
  "semana, las decisiones numeradas, reunidas en el Anexo A, dejaron constancia de cada diseño con su contexto, y las pruebas en un "
  "teléfono Android permitieron revisar el funcionamiento de la aplicación.")

tab("instrumentos", "Instrumentos de recolección de datos de la aplicación móvil",
    ["Instrumento", "Qué mide", "Cómo se aplica"],
    [["Historial del repositorio", "Avance por semana y por carpeta", "Conteo de cambios registrados"],
     ["Decisiones numeradas", "Contexto, decisión y consecuencias de cada diseño", "Lectura de la carpeta docs/decisiones (Anexo A)"],
     ["Pruebas en teléfono", "Cuenta, contraseñas, pantallas y estabilidad de la aplicación", "Pruebas manuales y con adb en un teléfono Android"]],
    [4.4, 5.6, 5.1])

h4("4.1.3. Recursos utilizados")

p("En lo humano, el desarrollo de los módulos 1 y 2 y de la carpeta compartida se coordinó con los equipos de los otros módulos y con el servidor "
  "central. Las herramientas de desarrollo se enlistan en la Tabla 2. A ellas se suman la biblioteca Argon2kt 1.5.0, "
  "para guardar contraseñas, Navigation Compose 2.8.2, para la navegación entre pantallas, Lifecycle 2.8.6 y Coroutines 1.9.0, todas declaradas en el "
  "archivo de versiones del proyecto.")

p("En lo económico, la decisión 0008 estableció que la aplicación se distribuye solo como APK desde la página del proyecto y que todo lo que cueste "
  "dinero se evita salvo que sea indispensable. Por eso las herramientas de esta parte son gratuitas y de código abierto: Android Studio, Kotlin, "
  "Jetpack Compose, Room, Hilt, DataStore y GitHub. El teléfono de pruebas, un Oppo CPH2387 con Android 12, y el tiempo de desarrollo son los únicos recursos que no se compraron.")

h4("4.1.4. Cronograma de actividades realizadas")

p("La [[tab:cronograma]] ordena las actividades centrales con su fecha. Cada una puede comprobarse en el historial de cambios o en las decisiones "
  "numeradas, y las fechas corresponden a los commits del repositorio en las carpetas de los módulos 1 y 2, el motor de voz y el servicio de "
  "escucha.")

tab("cronograma", "Actividades centrales del desarrollo de la aplicación móvil",
    ["Fecha (2026)", "Actividad", "Evidencia"],
    [["13 de julio", "Primer cambio del repositorio (prototipo previo a la estadía)", "Historial"],
     ["18 de septiembre", "Corrección de la compilación y entorno virtual funcional", "Historial"],
     ["25 de septiembre", "Interfaz para personas invidentes: menú, voz global, personalización, notificaciones y widget; decisiones 0006 y 0007", "Historial y decisiones"],
     ["26 y 27 de septiembre", "Pantalla negra, brillo mínimo, módulo Chats y comandos con tolerancia a errores de reconocimiento", "Historial y decisión 0003"],
     ["28 de septiembre", "IA apagada por defecto, borrar la cuenta y los datos, y correcciones de privacidad y accesibilidad táctil", "Historial y decisión 0002"],
     ["30 de septiembre", "Roles (administrador, directivo, educador, alumno), primer panel de Gestión y sesión recordada", "Historial"],
     ["1 al 3 de octubre", "Validación de cuentas, editar cuenta, soporte y tamaños de tarjeta unificados", "Historial"],
     ["8 de octubre", "Decisiones 0008 (APK sin tiendas y costo cero), 0011 y 0012 (tamaños y micrófono fijo)", "Decisiones"],
     ["27 de octubre", "Contraseña de 15 letras normalizada, correo opcional y avisos de servidor dormido, límite y falla", "Historial"],
     ["30 de octubre", "Pantalla dividida con micrófono al 42 %, tarjeta iluminada, confirmación de 18 años o más y base de datos versión 19", "Historial"],
     ["2 de noviembre", "Interfaz ServicioDeCuentas, contraseñas con Argon2id, widgets de un solo botón e ícono de la aplicación", "Historial"]],
    [3.2, 8.4, 3.5])

# ----------------------------------------------------------------------------------------------------
h3("4.2. ANÁLISIS DE REQUISITOS")

p("Los requisitos salen de los objetivos específicos, de las decisiones documentadas y de las necesidades del CECAPI descritas en el capítulo II. Los "
  "funcionales de la [[tab:rf]] describen lo que la aplicación debe hacer y cada uno indica el objetivo al que responde; los no funcionales de la "
  "[[tab:rnf]] describen las condiciones en las que debe hacerlo y cómo se verifica cada uno.")

tab("rf", "Requisitos funcionales de la aplicación móvil",
    ["Clave", "Requisito", "Objetivo"],
    [["RF1", "Recorrer todas las pantallas con lector de pantalla, con etiquetas descriptivas y señales distintas del color", "O1"],
     ["RF2", "Operar la aplicación con comandos de voz globales y propios de cada pantalla", "O2"],
     ["RF3", "Activar la escucha con una palabra de activación (el nombre del asistente y, si se activa, hola) y detenerla con voz", "O2"],
     ["RF4", "Enviar a la IA las frases que la aplicación no entiende y leer su respuesta en voz alta", "O2"],
     ["RF5", "Avisar por voz cuando no se entendió una frase o cuando el servidor tarda o falla", "O2"],
     ["RF6", "Crear una cuenta con usuario, contraseña y confirmación de 18 años o más, e iniciar y cerrar sesión", "O3"],
     ["RF7", "Validar el correo con un código y recuperar la contraseña con el servidor central", "O3"],
     ["RF8", "Instalarse como APK en teléfonos con Android 8.0 o posterior", "O4"]],
    [1.6, 11.2, 2.3], centro=(0, 2))

tab("rnf", "Requisitos no funcionales de la aplicación móvil",
    ["Clave", "Requisito", "Cómo se verifica"],
    [["RNF1", "Accesibilidad: todo operable con voz, con lector de pantalla y sin depender del color", "Revisión del código y recorridos en teléfono"],
     ["RNF2", "Privacidad: la IA y la escucha en segundo plano empiezan apagadas y la persona puede borrar su cuenta y sus datos", "Revisión de código y decisiones 0002 y 0007"],
     ["RNF3", "Seguridad: contraseñas con Argon2id, bloqueo tras intentos fallidos y sin copias de seguridad de la app", "Revisión de código y recorridos en teléfono"],
     ["RNF4", "Costo directo cero en la aplicación móvil y en sus herramientas de desarrollo", "Decisión 0008"],
     ["RNF5", "Compatibilidad: nivel de API 26 como mínimo y 35 como objetivo", "Archivo build.gradle.kts"],
     ["RNF6", "Mantenibilidad: una carpeta por módulo, versiones congeladas y decisiones registradas", "Revisión de la estructura del repositorio"]],
    [1.6, 8.5, 5.0], centro=(0,))

# ----------------------------------------------------------------------------------------------------
h3("4.3. DISEÑO DE LA SOLUCIÓN")

h4("4.3.1. Arquitectura de la aplicación")

p("La aplicación sigue una arquitectura por capas, representada en la [[fig:capas]]. Las pantallas se escriben con Jetpack Compose y solo muestran "
  "información y reciben toques o voz; cada pantalla tiene un ViewModel que decide qué hacer; el núcleo compartido reúne el motor de voz, la base de "
  "datos y las utilerías de seguridad; y debajo están los servicios del teléfono, como el reconocimiento de voz, la síntesis de voz y el almacenamiento "
  "local. Hilt entrega a cada clase los objetos que necesita en lugar de que los cree por su cuenta.")

fig("capas", "m01_capas_app.png", "Arquitectura por capas de la aplicación Android", 11.6)

h4("4.3.2. Navegación y pantallas")

p("La aplicación tiene 17 destinos de navegación, de los que la [[fig:pantallas]] muestra los de los módulos 1 y 2 y enlaza los de otros equipos. "
  "Antes de iniciar sesión se pueden usar el inicio, el inicio de sesión y el registro; algunas pantallas, como Chats, Gestión, Clasificación y Editar "
  "cuenta, piden una cuenta con sesión iniciada. El acceso se maneja con roles, no con planes (decisión 0009), y la [[tab:roles]] resume qué puede "
  "hacer cada rol.")

fig("pantallas", "m02_mapa_pantallas.png", "Destinos de navegación de la aplicación", 12.2)

tab("roles", "Roles de las cuentas y lo que pueden hacer",
    ["Rol", "A quién ve", "Qué puede hacer"],
    [["Administrador", "Todas las instituciones y los usuarios independientes", "Cambiar roles, asignar educador y validar a cualquiera"],
     ["Directivo", "Toda su institución", "Cambiar roles sin dar administrador, asignar educador y validar educadores de su institución"],
     ["Educador", "Solo a sus alumnos", "Validar a sus alumnos"],
     ["Alumno", "A nadie", "Ver quién es su educador y reportar un problema"]],
    [3.0, 5.2, 6.9])

p("Las decisiones de diseño de la interfaz responden a la necesidad de quien no ve la pantalla. Los botones y tarjetas siguen tamaños definidos en un componente compartido, de modo que "
  "ninguna pantalla dibuja botones propios (decisión 0012); el micrófono ocupa siempre el mismo lugar, para que se encuentre sin buscarlo; la pantalla puede "
  "ponerse en negro, con salida por voz y por toque (decisión 0003); y la tarjeta seleccionada se ilumina con el color de su módulo para indicar "
  "dónde está la persona.")

h4("4.3.3. Flujo de una frase de voz")

p("Toda la aplicación usa un solo motor de voz, porque Android solo permite un reconocedor y una síntesis de voz confiables por proceso. Ninguna pantalla "
  "crea el suyo: reciben las frases que el motor les pasa. La [[fig:flujo]] muestra el orden en que se atiende una frase. Primero se revisa si es una orden "
  "de control, como silencio o para; luego, si la pantalla pide el texto tal cual, como el usuario o la contraseña, se salta a la pantalla; si no, se "
  "prueba con los comandos globales y, por último, con los de la pantalla abierta.")

fig("flujo", "m03_flujo_frase_voz.png", "Recorrido de una frase dicha al asistente", 13.2)

p("El asistente tiene tres modos, que muestra la [[fig:modos]]. En el modo activo habla y escucha; en silencio calla, pero sigue atento a la palabra de "
  "activación; y detenido no hace nada hasta que la persona abre la aplicación de nuevo o pulsa el botón Detener del aviso fijo. La decisión 0006 fijó "
  "que las órdenes silencio y para solo valen como frase completa, porque la palabra para también aparece en frases comunes como para qué sirve. Las "
  "frases de cada orden se listan en la [[tab:control]].")

fig("modos", "m04_modos_asistente.png", "Modos del asistente: activo, silencio y detenido", 13.5)

tab("control", "Órdenes de control del asistente (decisión 0006)",
    ["Orden", "Frases que la activan", "Qué hace"],
    [["Silencio", "silencio, espera, espérame, cállate, un momento, un segundo", "Calla al asistente; vuelve con hola o con su nombre"],
     ["Para", "para, detente, alto, basta, para ya, ya para, hasta luego, adiós, apágate", "Detiene todo hasta abrir de nuevo la aplicación"]],
    [2.4, 7.4, 5.3])

h4("4.3.4. Modelo de datos")

p("La aplicación guarda sus datos en una sola base local, cecapi.db, con Room en la versión 19 del esquema y 22 entidades en total. La [[fig:datos]] "
  "muestra las tablas de los módulos 1 y 2: usuarios, con el hash de la contraseña, el rol y la fecha en que la persona confirmó tener 18 años o más; "
  "configuración y permisos por módulo de cada usuario; y las tablas de comandos de voz, su historial y sus respuestas auditivas. El módulo 1 incluye "
  "además cambios de cuenta e incidencias de soporte, y la pantalla Chats lee la tabla de consultas del módulo 3.")

fig("datos", "m06_modelo_datos_modulos_1_y_2.png", "Tablas de los módulos 1 y 2 en Room", 14.8)

h4("4.3.5. Cuentas y contraseñas")

p("El diseño de las cuentas se decidió el 30 de octubre de 2026: una sola forma de ingreso, con una cuenta propia, sin acceso con Google, y un código al "
  "correo solo para registrarse y recuperar la contraseña. La [[fig:cuentas]] distingue la cuenta guardada en el teléfono, que es la que funciona en "
  "la aplicación, de la cuenta en el servidor con código por correo, que es la siguiente etapa. La interfaz ServicioDeCuentas separa las pantallas "
  "de la forma de guardar las cuentas, de modo que el cambio al servidor no obliga a tocar la interfaz.")

fig("cuentas", "m05_cuentas_hoy_y_previsto.png", "Cuenta local actual y flujo previsto con código por correo", 14.5)

# ----------------------------------------------------------------------------------------------------
h3("4.4. IMPLEMENTACIÓN POR OBJETIVO")

h4("4.4.1. Interfaz accesible (O1)")

p("La interfaz se programó con Jetpack Compose y la guía de accesibilidad de Android. En el módulo 1 el código declara 15 descripciones de contenido y 22 "
  "usos de semántica, entre ellos encabezados, roles, descripciones de estado y regiones de lectura. Además, los recuadros de carga muestran una rueda con "
  "mensaje que TalkBack anuncia, hay un botón visible para volver y la barra de desplazamiento se unificó en los siete paneles.")

p("La pantalla dividida, creada el 30 de octubre, reparte el espacio con una proporción fija: el contenido ocupa el 58 % superior y el micrófono el 42 % "
  "inferior, y toda esa zona es el botón. Un toque habla, dos toques o un toque con dos dedos callan al asistente y mantener oprimido da la ayuda. "
  "Antes el micrófono era un panel flotante con medidas fijas que no se adaptaban al tamaño del teléfono ni al texto grande, y el contenido seguía "
  "existiendo debajo, donde el lector de pantalla podía enfocar elementos tapados.")

h4("4.4.2. Asistente de voz (O2)")

p("El asistente envuelve SpeechRecognizer y TextToSpeech, los servicios de reconocimiento y de síntesis de voz de Android, en una sola clase llamada "
  "VoiceEngine. El asistente atiende su nombre, que la persona puede personalizar, y la palabra hola cuando esa opción está activada, y un controlador mantiene "
  "la escucha encendida mientras al menos una pantalla la necesita. La voz se ejecuta fuera del hilo principal, lo que evita que la aplicación se congele si el audio "
  "del teléfono se traba, y existe un botón para elegir el motor de voz cuando el teléfono no tiene uno activo.")

p("La clase VoiceText quita acentos y mayúsculas y compara por palabras completas, de modo que menudo no se confunde con menú. Tolera una letra mal en "
  "palabras largas y una palabra partida en dos, como en isquierda por izquierda o en frente por enfrente, para compensar los errores del "
  "reconocimiento. Los comandos globales funcionan en cualquier pantalla e incluyen el estado del teléfono, el volumen, las notificaciones, la pantalla negra, "
  "la velocidad de la voz y la ayuda para aprender a usar la aplicación.")

p("La escucha fuera de la aplicación es opcional y empieza apagada (decisión 0007). Cuando se activa en Configuración funciona como un servicio en primer "
  "plano con tipo micrófono, con un aviso fijo y un botón Detener, y fuera de la aplicación solo responde a los comandos generales. Solo existe con una sesión "
  "iniciada: se enciende al entrar y se apaga al cerrar sesión, avisando por voz. Android exige declarar un tipo para cada servicio en primer plano "
  "desde el nivel de API 34, y para el micrófono se requieren los permisos correspondientes (Android Developers, 2026d).")

p("Las preguntas que la aplicación no entiende pueden pasar a la inteligencia artificial del servidor central mediante un resolvedor registrado al "
  "abrir la aplicación. Por la decisión 0002, la IA empieza apagada y se activa en Configuración, en la sección Privacidad; mientras esté apagada ninguna "
  "frase sale del teléfono hacia el servidor de IA. Además, cuando la aplicación no entiende una frase y hay internet, el texto de la frase se envía directamente desde el teléfono a "
  "Wikipedia o, si pide un dato como una capital o una fecha de nacimiento, a Wikidata, servicios de terceros; esa consulta no necesita cuenta y se "
  "hace aunque la IA esté apagada. El servidor y el módulo de consultas los desarrollan otros equipos y se describen en sus memorias.")

h4("4.4.3. Registro e inicio de sesión (O3)")

p("Las cuentas de la aplicación se guardan en el teléfono mediante la clase CuentasLocales, que implementa la interfaz ServicioDeCuentas. La "
  "contraseña de CECAPI se diseñó para dictarse: debe tener al menos 15 letras, solo letras de la a a la z y la ñ, y antes de validarla se normaliza "
  "quitando espacios, acentos y mayúsculas, de modo que una frase como Perro Café Rojo, dicha o escrita, llega igual. Una frase larga es fácil de decir y difícil de "
  "adivinar, y no depende de números ni de símbolos que el reconocimiento escribe de formas distintas.")

p("La contraseña se guarda con Argon2id, con los parámetros de la [[tab:argon]], y cada cuenta lleva su propia sal. Tras diez intentos fallidos el inicio de sesión se "
  "bloquea 15 minutos, medidos con un reloj interno que la persona no puede cambiar. El correo es opcional y se revisa en el teléfono por su forma y por "
  "errores comunes de escritura.")

tab("argon", "Parámetros de la protección de contraseñas en la aplicación",
    ["Parámetro", "Valor"],
    [["Algoritmo", "Argon2id con sal propia por cuenta"],
     ["Memoria", "32 MiB"],
     ["Pasadas", "3"],
     ["Hilos", "1"],
     ["Largo del resumen", "32 bytes"],
     ["Largo de la sal", "16 bytes"],
     ["Bloqueo por intentos", "10 fallos, 15 minutos"]],
    [5.5, 9.6])

p("Al registrarse se pide confirmar que se tiene 18 años o más y se guarda en qué momento lo declaró la persona, con la migración a la versión 19 de "
  "la base. Esta confirmación es declarativa y no sustituye una verificación de edad. El código de seis dígitos enviado al correo, la recuperación de "
  "la contraseña y las cuentas en el servidor forman la siguiente etapa, porque requieren que el servidor central publique las rutas de cuenta y que "
  "se elija un proveedor de correo.")

h4("4.4.4. Distribución como APK (O4)")

p("La decisión 0008 estableció que en Android el APK se distribuirá solo desde la página de descargas del proyecto, con su huella SHA-256 y un guion de "
  "instalación por voz. El archivo de compilación de la aplicación activa para la versión de lanzamiento la reducción y la ofuscación del código, "
  "que lo hacen más ligero y más difícil de descompilar. La versión de la aplicación es 1.0, con nivel de API 26 como mínimo y 35 como objetivo.")

p("Durante el desarrollo, el APK se compila y se instala directamente en el teléfono, que es la modalidad de instalación directa que persigue el "
  "objetivo. La firma digital con una clave propia, que Android exige para instalar o actualizar un APK (Android Developers, 2026k), y la publicación de cada versión como release "
  "en GitHub con su huella SHA-256 forman la siguiente etapa, que convierte esta compilación en una distribución para los alumnos del CECAPI. La ayuda de Android indica además que, en Android 13 o "
  "superior, algunos ajustes de una aplicación, como los de accesibilidad, pueden quedar restringidos hasta que la persona los permite (Google, s. f.-c), "
  "por lo que el guion de instalación debe explicarlo.")

# ----------------------------------------------------------------------------------------------------
h3("4.5. SEGURIDAD Y PRIVACIDAD DE LA APLICACIÓN")

p("La [[tab:seguridad]] reúne los controles aplicados en la aplicación y el envío de frases a servicios de terceros. El principio de trabajo fue que lo que no se necesita no se guarda y que la "
  "persona decide qué sale de su teléfono. Dos de las funciones que más datos pueden mover, la IA y la escucha en segundo plano, empiezan apagadas.")

tab("seguridad", "Controles y envíos de datos de la aplicación móvil",
    ["Control o envío", "Qué evita o qué sale del teléfono", "Fuente"],
    [["IA apagada por defecto", "Enviar frases al servidor de IA sin que la persona lo decida", "Decisión 0002"],
     ["Escucha fuera de la app apagada por defecto", "Escuchar sin que la persona lo sepa", "Decisión 0007"],
     ["Contraseñas con Argon2id y sal propia", "Que una base filtrada revele las contraseñas", "Código de la aplicación"],
     ["Bloqueo tras 10 intentos fallidos", "Adivinar contraseñas probando", "Código de la aplicación"],
     ["Sin copias de seguridad de la app", "Que los datos salgan por la copia automática de Android", "Manifiesto (allowBackup apagado)"],
     ["Tráfico sin cifrar solo en la versión de depuración", "Enviar datos por HTTP en la versión de lanzamiento", "Archivo build.gradle.kts"],
     ["Borrar mi cuenta y mis datos", "Conservar datos de quien ya no usa la app", "Código de la aplicación"],
     ["Confirmación de 18 años o más", "Uso por menores sin declaración", "Registro de cuenta"],
     ["Consulta a Wikipedia y Wikidata", "Sale el texto de la frase hacia servicios de terceros, sin pasar por el servidor ni por un filtro", "Por decidir (decisión 0002)"]],
    [5.2, 6.0, 3.9])

p("Sobre el micrófono, la documentación de Android advierte que el reconocimiento de voz probablemente transmite el audio a servidores remotos "
  "(Android Open Source Project [AOSP], s. f.-b), de modo que lo que se dice "
  "cerca del teléfono con la escucha activa puede salir del dispositivo, también el de personas cercanas que no usan la aplicación. Por eso la escucha "
  "fuera de la app es opcional, muestra un aviso fijo mientras funciona y solo puede iniciarse con una sesión abierta.")

# ----------------------------------------------------------------------------------------------------
h3("4.6. PRUEBAS REALIZADAS")

p("Las pruebas se realizaron en un teléfono Oppo CPH2387 con Android 12, con la aplicación com.cecapi.app en su versión 1.0, compilada desde la rama "
  "principal del repositorio (commit b8e39d8). Se combinaron pruebas manuales sobre las pantallas con comandos de adb, la herramienta de Android "
  "para controlar un dispositivo desde una computadora.")

p("La [[tab:pruebas_tel]] resume lo que se comprobó en el teléfono: la creación de cuenta con sus validaciones y el aviso a las personas menores de "
  "edad, el contador de contraseñas incorrectas, la barra de desplazamiento fija en las pantallas Cuenta, Personalización y Configuración, y la "
  "estabilidad de la aplicación ante más de 3,700 eventos aleatorios, durante los cuales no se registraron cierres.")

tab("pruebas_tel", "Pruebas realizadas en el teléfono",
    ["Prueba", "Qué se comprobó"],
    [["Creación de cuenta", "Validaciones de usuario y contraseña, y aviso a la persona menor de edad"],
     ["Inicio de sesión", "Contador de contraseñas incorrectas"],
     ["Pantallas", "Barra de desplazamiento fija en Cuenta, Personalización y Configuración"],
     ["Estabilidad", "Más de 3,700 eventos aleatorios sin cierres de la aplicación"]],
    [4.0, 11.1])

p("El reconocimiento de voz depende de que el teléfono tenga descargado el paquete de voz del idioma, y las funciones disponibles dependen de la "
  "versión de Android de cada aparato. El nivel de API 26 es el mínimo con el que se compila la aplicación; la instalación y las pruebas se hicieron en un solo teléfono, con Android 12, "
  "y no incluyeron el recorrido con lector de pantalla.")

# =====================================================================================================
# CAPÍTULO V
# =====================================================================================================
corte()
h1("CAPÍTULO V")
h2("RESULTADOS Y CONCLUSIONES")

h3("5.1. RESULTADOS")

p("Los resultados se presentan en el orden de los objetivos específicos del capítulo II. La [[tab:cumplimiento]] y la [[fig:estado]] resumen el alcance "
  "logrado en cada objetivo al 2 de noviembre de 2026, según el código del repositorio y las pruebas en el teléfono.")

tab("cumplimiento", "Alcance de los objetivos específicos al 2 de noviembre de 2026",
    ["Objetivo", "Alcance logrado", "Siguiente etapa"],
    [["O1. Pantallas accesibles", "17 destinos de navegación, micrófono fijo, tamaños de botón unificados, pantalla dividida y etiquetas descriptivas en el módulo 1", "Extender las etiquetas a todas las pantallas"],
     ["O2. Asistente de voz", "Motor de voz único, palabra de activación, tolerancia a errores, comandos globales y por pantalla, escucha en segundo plano opcional y preguntas a la IA conectadas en el código", "Comprobar la conexión con el servidor central"],
     ["O3. Registro e inicio de sesión", "Cuenta local con contraseña de 15 letras, Argon2id, bloqueo por intentos y confirmación de 18 años", "Código por correo y cuentas en el servidor"],
     ["O4. Distribución como APK", "APK de instalación directa compilado e instalado en teléfono, con versión de lanzamiento optimizada", "Firma digital y publicación en GitHub Releases"]],
    [3.6, 7.2, 4.3])

fig("estado", "m08_estado_objetivos.png", "Alcance de los objetivos específicos al 2 de noviembre de 2026", 13.6)

h4("5.1.1. Interfaz accesible (O1)")

p("La aplicación tiene 17 destinos de navegación, 11 de ellos del módulo 1, y todos comparten el micrófono fijo en la zona inferior y los tamaños "
  "de botón de la decisión 0012. El código del módulo 1 declara 15 descripciones de contenido y 22 usos de semántica, y los recuadros de carga "
  "anuncian su mensaje con el lector de pantalla. En el teléfono de pruebas se comprobó la barra de desplazamiento fija en las pantallas Cuenta, Personalización y Configuración.")

h4("5.1.2. Asistente de voz (O2)")

p("El asistente reconoce comandos globales y propios de cada pantalla, con tolerancia a acentos, a una letra mal y a palabras partidas, y se detiene o "
  "calla con las órdenes de la decisión 0006. Funciona con un solo motor de reconocimiento y síntesis de voz para toda la aplicación, atiende la palabra "
  "de activación y ofrece la escucha en segundo plano como opción apagada por defecto, que se activa en Configuración. El envío de las preguntas al servidor central "
  "quedó conectado en el código, y su funcionamiento de punta a punta depende de que el servidor publique sus rutas.")

h4("5.1.3. Registro e inicio de sesión (O3)")

p("El registro y el inicio de sesión funcionan con cuentas guardadas en el teléfono, con cuatro roles y con la confirmación de 18 años o más. La "
  "contraseña de al menos 15 letras se normaliza antes de validarse, se protege con Argon2id y el acceso se bloquea 15 minutos tras diez intentos "
  "fallidos. La interfaz ServicioDeCuentas deja preparada la conexión con las cuentas del servidor, a las que se sumarán el código por correo y la "
  "recuperación de la contraseña. En el teléfono de pruebas se comprobaron las validaciones de la creación de cuenta, el aviso a las personas "
  "menores de edad y el contador de contraseñas incorrectas.")

h4("5.1.4. Distribución como APK (O4)")

p("La aplicación se compila como APK de instalación directa, con la versión de lanzamiento reducida y ofuscada, y requiere "
  "el nivel de API 26 o superior, y se instaló y probó en un teléfono con Android 12. La decisión 0008 fijó el canal de distribución: el APK se entrega desde la página del proyecto con su huella SHA-256 y "
  "un guion de instalación por voz, sin tiendas de aplicaciones y con costo cero. La firma con clave propia y la publicación de cada versión en "
  "GitHub Releases completan esa distribución.")

h3("5.2. CONCLUSIONES")

p("El proyecto produjo una aplicación Android de asistencia por voz con una interfaz pensada para personas ciegas o con baja visión, un motor de voz único "
  "y cuentas locales con contraseñas protegidas, y dejó documentadas las decisiones que la sustentan. Las conclusiones siguientes se organizan por "
  "objetivo específico y distinguen lo cumplido de lo que queda como siguiente etapa.")

p("El primer objetivo se cumplió en parte. Se diseñaron las pantallas con navegación consistente, micrófono fijo, tamaños de botón unificados, pantalla dividida "
  "y tarjeta iluminada por módulo, y con etiquetas descriptivas para el lector de pantalla en las pantallas del módulo 1. Extender esas etiquetas a "
  "las demás pantallas es el trabajo siguiente.")

p("El segundo objetivo se cumplió en parte: se programó en el teléfono un asistente que usa un solo motor de reconocimiento y síntesis de voz, atiende una palabra de "
  "activación, tolera errores del reconocimiento y reconoce comandos globales y propios de cada pantalla. La función de enviar preguntas a la inteligencia "
  "artificial quedó conectada en el código y apagada por defecto por privacidad, y su funcionamiento completo depende del servidor central, que desarrolla otro equipo.")

p("El tercer objetivo se cumplió en parte: existe el registro y el inicio de sesión con una cuenta propia y una contraseña de al menos 15 letras pensada "
  "para dictarse, protegida con Argon2id, con bloqueo por intentos y confirmación de mayoría de edad. La validación del correo con un código y la "
  "recuperación de la contraseña dependen del servidor central y quedan como siguiente etapa, y la interfaz ServicioDeCuentas permite incorporarlas "
  "sin rehacer las pantallas.")

p("El cuarto objetivo está en proceso: la aplicación se compila como APK de instalación directa y se instala en el teléfono, y la decisión 0008 fijó que "
  "se distribuirá sin tiendas de aplicaciones y con costo cero. La firma digital propia y la publicación en GitHub Releases son el paso siguiente. En "
  "conjunto, la aplicación reúne comandos de voz globales y por pantalla, respuestas habladas, cuentas locales y ajustes de voz, y deja definida la "
  "ruta para completar las cuentas con el servidor y la distribución del APK.")

h3("5.3. RECOMENDACIONES")

p("Las recomendaciones se dirigen a quien continúe el proyecto y siguen el orden de importancia. La primera es cerrar la distribución, porque con un "
  "APK firmado y publicado los alumnos del CECAPI podrán instalar y usar lo que se desarrolló, y las demás recomendaciones se apoyan en esa entrega.")

bul(["**Firmar y publicar el APK.** Crear una clave de firma propia, guardarla fuera del repositorio, automatizar la compilación y la publicación en GitHub Releases con su huella SHA-256 y escribir el guion de instalación por voz.",
     "**Conectar las cuentas al servidor.** Implementar una segunda versión de ServicioDeCuentas que llame a las rutas del servidor, con el código por correo para registro y recuperación de la contraseña.",
     "**Agregar el aviso de privacidad y los términos a la aplicación.** Leerlos por partes y por audio, con aceptación registrada.",
     "**Evaluar el uso con alumnos del CECAPI.** Medir el tiempo y los pasos de tareas cotidianas con el lector de pantalla y con la aplicación, para valorar con datos cuánto reduce el asistente de voz."])

p("Estas acciones llevarían la aplicación de un desarrollo funcional a una herramienta instalada y evaluada por las personas que la van a usar, que es la "
  "condición necesaria para recomendar su adopción por el CECAPI y para que el asistente de voz se convierta en una ayuda cotidiana de sus alumnos.")


ANEXO_DECISIONES = [
    ("0001", "Versiones de Gradle, AGP y Kotlin congeladas",
     "AGP 8.5.2, Gradle 8.9, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Room 2.6.1 y Hilt 2.51.1, con compileSdk y targetSdk 35 y minSdk 26; no se cambian sin acuerdo.",
     "Una actualización a versiones más nuevas había roto la compilación desde un clon limpio."),
    ("0002", "IA como cerebro temporal, apagada por defecto",
     "La IA solo traduce una frase no entendida a un comando conocido o da una respuesta corta. Empieza apagada y se activa en Configuración, sección Privacidad.",
     "Mientras está apagada, ninguna frase se envía a un servidor de IA."),
    ("0003", "Pantalla negra con salida por voz y por toque",
     "Pantalla negra y brillo mínimo funcionan en cualquier pantalla, con dos salidas: decir pantalla normal y mantener presionada la pantalla.",
     "Los comandos de pantalla se atienden antes que cualquier otro, para que la salida funcione siempre."),
    ("0005", "Responsable legal, menores y datos reales",
     "Mientras solo haya cuentas de demostración no se usan datos reales; antes de usarlos se buscará un convenio con el CECAPI y se definirá el responsable legal. El aviso de privacidad y el consentimiento dentro de la app esperan la revisión de una persona abogada.",
     "Los textos legales son borradores para revisión de abogado. Ya se construyó borrar la cuenta y los datos, la IA apagada por defecto y el registro interno del inicio de sesión sin el nombre de usuario."),
    ("0006", "Silencio y para solo como frase completa",
     "Las órdenes silencio y para solo valen como frase completa, porque para también aparece en frases comunes.",
     "Se atienden antes que cualquier otro comando y funcionan sin decir la palabra de activación."),
    ("0007", "Escuchar fuera de la app: opcional y apagado",
     "La escucha fuera de la app se activa en Configuración, empieza apagada y funciona como servicio en primer plano con aviso fijo y botón Detener.",
     "Fuera de la app solo responde a los comandos generales."),
    ("0008", "Android: solo el APK desde la página del proyecto, y costo cero",
     "El APK se distribuye solo desde la página de descargas, con su huella SHA-256 y un guion de instalación por voz. El único gasto que esta decisión acepta es el dominio; los costos del servidor y de la IA se describen en el apartado 2.4.2.",
     "El guion de instalación por voz debe explicar los permisos que Android pida al instalar y activar la aplicación."),
    ("0009", "El acceso se maneja con roles, no con planes",
     "El acceso y lo que ve cada persona dependen solo de su rol, sin planes ni pagos dentro de la aplicación.",
     "Las funciones se activan por rol, no por pago."),
    ("0011", "Versión mínima de Android",
     "Propone un mínimo de Android 5 (API 21) y recomienda Android 11 o superior.",
     "El cambio en Gradle no se aplicó: el mínimo con el que se compila es API 26, y bajarlo exige cambiar Gradle, que está congelado."),
    ("0012", "Tamaños de botón y micrófono fijo",
     "Seis tamaños de tarjeta en toda la app, cuatro de un componente compartido y dos del podio de la clasificación; el micrófono va anclado abajo, con la misma altura en todas las pantallas.",
     "Ningún equipo hace botones a mano, y se revisan los tamaños y el micrófono en cada panel nuevo."),
    ("0018", "Ubicación para orientarse: evaluación",
     "Evaluación, sin implementar, de decir dónde está la persona. Identifica la discapacidad visual como dato sensible (ley federal de datos personales, art. 8).",
     "La aplicación no usa ubicación, y se quitó un permiso de ubicación que no se usaba."),
]
