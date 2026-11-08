# Contexto: Capítulos IV y V de la memoria (aplicación móvil Android)

Autor: Fernández Quintana José · UTD, Desarrollo de Software Multiplataforma · Generación 2024-2026
Título: «Desarrollo de una aplicación móvil Android de asistencia por voz para el CECAPI»
Estado: los capítulos ya están redactados en `Memoria_Movil_Cap_IV_V.docx` (20 páginas, 8 figuras, 10 tablas nuevas). Sin marcas amarillas ni pendientes. Datos tomados del repositorio EstadiasAppMobil, rama `main`, commit `b8e39d8` (2 nov 2026).

## Qué pedirle a la otra IA

Revisar redacción, ortografía y consistencia del `.docx`, o ajustar un apartado concreto, **sin inventar datos**. No tocar ni describir las memorias de los demás compañeros (Windows, asistente web, servicio de IA, sitio). No mencionar cómo se creó el código ni el uso de herramientas de IA.

## Reglas de formato (manual UTD)

Carta; márgenes izq 3.0, der 2.5, sup 2.5, inf 2.5 cm. Times New Roman 12, interlineado 1.5, justificado, sangría 1.25 cm. Tercera persona, verbos en infinitivo, párrafos de 40 palabras o más, sin primera persona. Título 1: TNR 14 negritas centrado mayúsculas. Título 2: 12 negritas centrado mayúsculas. Título 3: 12 negritas justificado mayúsculas. Título 4: 12 negritas cursiva. Rótulos «Figura N.» / «Tabla N.» con campo SEQ, TNR 10; tablas con rótulo arriba, figuras abajo; cada una mencionada en el texto inmediato anterior. APA 6. Conclusiones de al menos una cuartilla, sin experiencias personales.

## Cómo integrarlo en el documento del alumno

1. El documento ya trae «CAPÍTULO IV», «4.1. METODOLOGÍA UTILIZADA», el párrafo de la Tabla 2 y la Tabla 2. El archivo nuevo **empieza donde termina la Tabla 2** (reemplaza el marcador «[Redactar al concluir el desarrollo…]») y trae el Capítulo V después de un salto de página (reemplaza los marcadores del Cap. V).
2. Numeración: la primera tabla nueva fuerza el número 3 (`SEQ … \r 3`); las figuras empiezan en 1. Al pegar, en Word: Ctrl+A y F9 para actualizar campos, y revisar que texto y rótulos coincidan.
3. Los títulos usan el formato «4.1.1. Texto» con punto final, como el documento del alumno. Si al pegar cambian los estilos, aplicar de nuevo Título 3 y Título 4.
4. Actualizar el índice de tablas, figuras y anexos de las páginas preliminares.

## Los 4 objetivos específicos (Cap. II del alumno, no cambiar)

1. Diseñar pantallas con etiquetas descriptivas para el lector de pantalla y señales distintas del color.
2. Programar un asistente de voz con palabra de activación, comandos globales y por pantalla, que envíe preguntas al servidor, lea la respuesta de la IA y avise cuando no entiende.
3. Construir registro e inicio de sesión con cuenta propia, contraseña pensada para voz y código al correo.
4. Distribuir la aplicación como APK de instalación directa.

## Hechos verificados en el código (para no inventar)

- Módulo 1 (`modulo1_aplicacionprincipal`, 35 archivos, 8,225 líneas), módulo 2 (`modulo2_asistentevoz`, 4 archivos, 297 líneas), `core/voice` (18 archivos, 1,711), `core/data` (3, 356), `service` (1, 159). Líneas no vacías.
- Actividad en esas carpetas por semana ISO: W38 2, W39 6, W40 30, W41 1, W42 0, W43 0, W44 21, W45 8 (parcial); más 1 commit en julio.
- Voz: `VoiceEngine` envuelve `SpeechRecognizer` y `TextToSpeech`; `WakeWordController` («hola» o el nombre elegido); `VoiceText` tolera acentos, una letra mal y palabras partidas; modos activo, silencio y detenido (decisión 0006); escucha fuera de la app opcional y apagada por defecto, como servicio en primer plano con tipo micrófono, solo con sesión iniciada (decisión 0007).
- IA: apagada por defecto (decisión 0002), se activa en Configuración, sección Privacidad.
- Cuentas: `ServicioDeCuentas` (interfaz) con `CuentasLocales`; contraseña de 15 letras o más, solo a-z y ñ, normalizada; Argon2id con 32 MiB, 3 pasadas, 1 hilo, hash de 32 bytes y sal de 16 bytes; 10 intentos fallidos bloquean 15 minutos; confirmación de 18 años o más guardada con fecha (Room versión 19); cuatro roles (administrador, directivo, educador, alumno). La validación con código por correo y las cuentas en el servidor **no existen todavía**: se presentan como siguiente etapa.
- Interfaz: 19 destinos de navegación (11 del módulo 1; el módulo 6 aporta 3, con los dos juegos con cámara); seis tamaños de botón (decisión 0012); micrófono fijo; pantalla dividida 58 % contenido y 42 % micrófono (`PantallaConMicrofono`, `MIC_FRACCION = 0.42`); pantalla negra (decisión 0003); 15 descripciones de contenido y 22 usos de semántica en el módulo 1.
- APK: compilación de lanzamiento con reducción y ofuscación; versión 1.0; API 26 mínimo y 35 objetivo; decisión 0008 (APK sin tiendas, costo cero). No hay firma con clave propia ni release publicada: se presenta como siguiente etapa.
- Seguridad: `allowBackup` apagado; tráfico sin cifrar solo en la variante de depuración; borrar cuenta y datos.
- Pruebas (datos aportados por el alumno desde adb): teléfono Oppo CPH2387 con Android 12, com.cecapi.app versión 1.0 (código 1) compilada desde main (commit b8e39d8). Se comprobó en el teléfono: creación de cuenta con validaciones y aviso a la persona menor de edad, contador de contraseñas incorrectas, barra de desplazamiento fija en Cuenta, Personalización y Configuración, y más de 3,700 eventos aleatorios sin cierres. **No hay** recorrido con TalkBack, ni pruebas con alumnos, ni resultados de pruebas unitarias: no escribir cifras de eso.

## Figuras (`figuras/`)

m01 capas, m02 mapa de pantallas, m03 flujo de una frase de voz, m04 modos del asistente, m05 cuenta local y siguiente etapa, m06 tablas de los módulos 1 y 2, m07 cambios por semana, m08 alcance de los objetivos. Son diagramas, no capturas. Si se agregan capturas reales del teléfono, deben ir con rótulo «Figura N.» y mención previa en el texto.

## Ajustes ya aplicados al documento completo (`Memoria_App_movil_Android_ajustada.docx`)

Partiendo del documento del alumno del 2 de noviembre: Tabla 2 actualizada al 2 de noviembre de 2026 (16 filas: se agregaron Gradle 8.9, Navigation Compose 2.8.2, Lifecycle 2.8.6, Coroutines 1.9.0 y Argon2kt 1.5.0, y se quitó «No incorporada»); apartado 2.1 sin la promesa de comprobar la hipótesis con alumnos; objetivo específico 3 con cuenta propia protegida con Argon2id y validación por correo «preparada» con el servidor; alcances 2.4.1 sin pantallas de políticas en la app y con comprobación mediante recorridos de prueba; límites 2.4.2 con la validación del correo como dependencia del servidor; y 3.10 con el aviso de privacidad en futuro. Los capítulos IV y V están en `Memoria_Movil_Cap_IV_V.docx`; el marcador del 4.1 sigue en el documento hasta pegarlos.

## Archivos

`Memoria_Movil_Cap_IV_V.docx` y `.pdf` · `figuras/` · `herramientas/` (scripts para regenerar figuras y documento) · `referencia/` (borrador general del ecosistema, solo como referencia de estilo; no usar sus datos de otros componentes).

## Reglas de redacción acordadas con el alumno (3 nov 2026)

1. Cada frase debe tener fuente: el código, un commit, una decisión del repositorio o algo que dijo el alumno. Si no la tiene, se corta; no se usan marcadores de pendiente ni amarillos.
2. No se afirman resultados que no se midieron (ni «la mayoría de los paneles funcionó», ni autonomía de los usuarios). Se dice qué se hizo: recorridos con el APK instalado en un teléfono Android.
3. Cada objetivo se declara como cumplido, cumplido en parte o en proceso, según el código: O1 en parte (etiquetas en el módulo 1), O2 cumplido en el teléfono (la IA depende del servidor), O3 en parte (cuenta local; código por correo es del servidor), O4 en proceso (APK de instalación directa; firma y publicación después).
4. No se menciona cómo se creó el código ni el uso de herramientas de IA. No se usa «equipo» para esta memoria individual.
5. Las citas de los capítulos IV y V usan solo referencias que ya están en la lista del alumno (Android Developers 2026d y 2026k, AOSP s. f.-b).
6. La palabra de activación es el nombre del asistente (personalizable) y, si se activa, «hola».

Versión vigente: `Memoria_App_movil_Android_COMPLETA.docx` (51 págs.): capítulos II, III (3.7 y 3.10 ajustados), IV y V, resumen, abstract, Tabla 2 actualizada y Anexo A (decisiones 0001 a 0019). Word actualiza índices y rótulos al abrir (campos automáticos).

## Cambios del 3 de noviembre de 2026 (segunda revisión crítica)

- **Flujo a terceros:** con la IA apagada, cualquier frase que la app no entiende (de 3 letras en adelante) y hay internet se envía desde el teléfono a Wikipedia (o a Wikidata si pide un dato como capital o fecha de nacimiento), sin pasar por el servidor ni por filtro (`HomeViewModel.notUnderstood`, `DashboardViewModel`). Declarado en 3.10, 4.4.2 y la Tabla 11; la decisión 0002 deja por decidir el filtro y el aviso.
- **Responsable legal:** el documento ya no afirma quién es; dice que mientras haya cuentas de demostración no se usan datos reales y que antes se buscará convenio con el CECAPI y revisión de una persona abogada (decisión 0005). Artículos de la ley verificados contra el texto oficial (última reforma DOF 14-11-2025): 7, 8, 10, 15 y 18.
- **Android 13 y ajustes restringidos:** se cita la ayuda oficial de Android (Google, s. f.-c), agregada a las referencias; esa página no menciona APK de forma explícita, por eso el texto dice «una aplicación» y no «un APK descargado».
- **Pruebas:** solo un teléfono (Oppo CPH2387, Android 12) y sin recorrido con lector de pantalla; el texto lo dice y ya no afirma que se revisó «con su motor de voz y permisos reales». La instalación en API 26 o superior no se probó en otros teléfonos.
- **Objetivos (Cap. II):** el objetivo 2 dice «dejar conectado» el envío al servidor, el 3 ya no promete conservar conversaciones y el 4 dice «generar el APK y dejar definida su distribución».
- **Anexo A:** la 0011 queda como propuesta sin aplicar (mínimo real API 26); la 0008 aclara que el único gasto que acepta es el dominio y remite a 2.4.2; la 0005 sin postura legal.
- **Índices:** se regeneran con las páginas reales (`herramientas/regenerar_indices.py`); Word los vuelve a calcular al abrir.
- Sigue abierto, solo del alumno: pruebas con lector de pantalla y con Android 14 o superior, y resultados de las pruebas con alumnos.

## Tercera revisión (3 de noviembre de 2026, versión de 54 páginas)

- **Wikipedia (Q-01):** sin cambiar el código de la app, el flujo queda declarado como límite explícito en 2.4.2 (nuevo párrafo), en 4.5 (excepción al principio «la persona decide qué sale de su teléfono»), en RNF2 y en la Tabla 11. Verificado en el código que la consulta solo existe en `HomeViewModel` y `DashboardViewModel` (inicio y panel); el inicio de sesión y el registro, que usan `voiceEngine.rawInput` para dictar usuario y contraseña, no la usan. Si el alumno decide condicionarla al interruptor de la IA, es un cambio pequeño en esos dos ViewModels y habría que reescribir esos cuatro textos.
- **Lector de pantalla (Q-02):** no hay recorrido con TalkBack; 4.6, 5.1.1 y la conclusión del objetivo 1 lo dicen. Si el alumno lo hace, reemplazar esas frases por una tabla pantalla / ¿se anuncia? / observación, con modelo y versión de TalkBack.
- **Pruebas con alumnos (Q-03):** la recomendación de 5.3 dice que se hagan con cuentas de demostración mientras no se resuelvan el convenio y el aviso (decisión 0005).
- **Fechas (Q-06):** estado del documento al 3 de noviembre de 2026; la actividad del repositorio llega hasta el 2 de noviembre (último commit).
- **Objetivo general y alcances (Q-05):** hablan de «un APK de instalación directa». 2.1 plantea la dependencia de otras personas como punto de partida, sin llamarla hipótesis.
- Abiertos solo del alumno: recorrido con TalkBack, datos propios del CECAPI para 2.1 y motivo del hueco de actividad del 5 al 25 de octubre.

## Cuarta revisión (3 de noviembre de 2026, 54 páginas)

- Figura 8 rehecha con tres columnas (objetivo, estado y alcance, siguiente etapa) coherente con la Tabla 13 y con 5.2: O1, O2 y O3 «en parte», O4 «en proceso». Corte unificado al 3 de noviembre en texto, tablas y figuras 1 y 8 (el último commit de las carpetas es del 2 de noviembre).
- Sin recorrido con TalkBack ni prueba de voz registrada: RNF1 dice «el recorrido con lector de pantalla es la siguiente etapa», 4.6 y 5.1.2 dicen que no se probó el reconocimiento de voz de forma sistemática, y 2.4.2 lo declara como límite. Si el alumno hace las pruebas, reemplazar esas frases por tablas pantalla / resultado.
- Fusionados los dos párrafos de menos de 40 palabras (2.1 y 2.4.2). La Tabla 5 separa la decisión 0011 (propuesta sin aplicar) de la 0012.
- Abiertos solo del alumno: motivo del hueco de octubre (Figura 1), dictamen y agradecimientos (se entregan firmados o aparte), y revisar que las otras memorias coincidan en Wikipedia, cuentas, Argon2id y costos.

## Cierre de capítulos (3 de noviembre de 2026)

- 4.1.1 explica el hueco del 5 al 25 de octubre tal como lo dijo el alumno: ese periodo se dedicó a validar y revisar lo desarrollado y a generar la documentación; la programación se retomó el 26 de octubre. No se menciona ninguna visita al CECAPI porque el alumno no la confirmó.
- Alcance acordado: solo capítulos y formato (índices). No se agregan pruebas en el teléfono, recorrido con TalkBack ni datos nuevos del CECAPI.
- Antes de exportar desde Word: Ctrl+A y F9 para actualizar índices y rótulos; el PDF de entrega debe exportarse desde Word.
