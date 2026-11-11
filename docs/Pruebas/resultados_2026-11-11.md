# Resultados: 30 preguntas y 20 sinónimos contra el servidor

> Generado por `docs/Pruebas/correr_pruebas.py` el 11/11/2026 00:34 · modo: con IA (backend/.env)

Se usa la misma función que `POST /entender` (backend/main.py). Leyenda: ✅ correcto · ❌ falla · ➖ la resuelve el teléfono antes de llegar al servidor (aquí solo se revisa que no abra un módulo equivocado) · ⚠️ error, bloqueo, respuesta "no lo sé" / "no dispongo de" o marca MAS_DISPONIBLE en el texto (revisar a mano).

## Resumen

- **Preguntas (30):** ✅ 20 correctas · ➖ 9 las resuelve el teléfono · ⚠️ 1 avisos
- **Sinónimos (80 frases de 20 acciones):** ✅ 24 correctas · ➖ 56 las resuelve el teléfono
- **Frases que deben abrir un módulo y el servidor reconoce:** 20 de 20

## Preguntas

| # | Pregunta | Esperado | Resultado del servidor | ms | |
|---|---|---|---|---|---|
| 1 | ¿Qué hay enfrente de mí? | ENVIRONMENT | comando `ENVIRONMENT` | 28 | ✅ |
| 2 | ¿Me puedes leer este documento? | DOCUMENT_READER | comando `DOCUMENT_READER` | 1 | ✅ |
| 3 | ¿Qué dice este letrero? | DOCUMENT_READER | comando `DOCUMENT_READER` | 0 | ✅ |
| 4 | ¿Cuánta batería me queda? | teléfono | IA (groq): Para saber cuánta batería tienes, pregúntale a la app. | 993 | ➖ |
| 5 | ¿Estoy conectado al wifi? | teléfono | IA (groq): Para saber si estás conectado al wifi, pregunta a la app. | 527 | ➖ |
| 6 | ¿Qué hora es? | teléfono | IA (groq): No tengo acceso a la hora, pregúntale a la app. | 381 | ➖ |
| 7 | ¿Qué día es hoy? | teléfono | error: El asistente no está disponible en este momento. | 19324 | ⚠️ |
| 8 | ¿Puedes subir el volumen? | teléfono | IA (groq): No puedo cambiar el volumen. Pregúntale a la app si puede subirlo. | 994 | ➖ |
| 9 | ¿Tengo notificaciones nuevas? | teléfono | IA (groq): Para saber si tienes notificaciones nuevas, pregúntale a la app. | 1096 | ➖ |
| 10 | ¿Qué opciones tiene el menú? | teléfono | IA (groq): Para saber los comandos de la app, di "ayuda" o "qué comandos hay". | 696 | ➖ |
| 11 | ¿Puedes abrir mis solicitudes? | REQUESTS | comando `REQUESTS` | 1 | ✅ |
| 12 | ¿Puedes abrir las actividades? | LEARNING | comando `LEARNING` | 1 | ✅ |
| 13 | ¿Cómo inicio sesión? | teléfono | IA (groq): Para iniciar sesión, diga “iniciar sesión” y la aplicación le guiará. Si necesita más ayuda, diga “ayuda”. | 764 | ➖ |
| 14 | ¿Me puedes repetir lo último que dijiste? | teléfono | IA (groq): Si una respuesta más larga le serviría a la persona, agrega al final, en su propia línea, solo: . (hay más) | 651 | ➖ |
| 15 | ¿Qué comandos puedo usar? | teléfono | IA (groq): Puedes decir “ayuda” o “qué comandos hay” para conocer los comandos disponibles. | 537 | ➖ |
| 16 | ¿Qué es el sistema braille? | IA | IA (groq): El sistema braille es un código táctil que permite a las personas con discapacidad visual leer y escribir mediante puntos en relieve. Cada carácter se forma con combinaciones de hasta seis puntos que se sienten con el dedo. | 1086 | ✅ |
| 17 | ¿Quién fue Louis Braille? | IA | IA (groq): Louis Braille fue un inventor francés que desarrolló el sistema de lectura táctil para personas con discapacidad visual. Nació en 1809 y murió en 1852. Su método permite leer y escribir con el tacto. (hay más) | 562 | ✅ |
| 18 | ¿Cuál es la capital de Francia? | IA | IA (groq): La capital de Francia es París. | 513 | ✅ |
| 19 | ¿Cómo va a estar el clima mañana? | IA | IA (groq): Para saber el clima mañana, pregúntale a la app. | 517 | ✅ |
| 20 | ¿Cuántos habitantes tiene México? | IA | IA (groq): México tiene alrededor de 126 millones de habitantes. | 341 | ✅ |
| 21 | ¿Cuándo nació Benito Juárez? | IA | IA (groq): Benito Juárez nació el 21 de marzo de 1806. | 358 | ✅ |
| 22 | ¿Qué es un perro guía y cómo se consigue uno? | IA | IA (groq): Un perro guía es un animal entrenado para ayudar a personas con discapacidad visual a moverse de forma segura. Se consigue a través de organizaciones especializadas que ofrecen programas de entrenamiento y adopción, y normalmente se requiere completar un proceso de selección y capacitación. Si quieres saber más, dime "ayuda" o "qué comandos hay". (hay más) | 891 | ✅ |
| 23 | ¿Dónde queda el CECAPI? | IA | IA (groq): El CECAPI está en la calle Jacarandá 1603, colonia Valle Florido, código postal 34226, Durango, Durango. Llama al 618 455 0146 para confirmar horarios. Abre de lunes a viernes, de dos a siete de la tarde. | 700 | ✅ |
| 24 | ¿Por qué el cielo es azul? | IA | IA (groq): El cielo es azul porque la atmósfera dispersa la luz solar. Los rayos de luz azul se dispersan más que los rojos. Por eso vemos el cielo azul. | 575 | ✅ |
| 25 | ¿Cómo se prepara un café de olla? | IA | IA (groq): Para preparar café de olla, hierve agua con piloncillo, una rama de canela y un par de clavos de olor. Cuando el agua esté caliente, agrega café molido y deja reposar unos minutos antes de colar y servir. | 682 | ✅ |
| 26 | ¿Qué es la diabetes y cómo afecta la vista? | IA | IA (groq): La diabetes es una enfermedad que controla la cantidad de azúcar en la sangre. Cuando el nivel de glucosa se mantiene alto, puede dañar los vasos sanguíneos de la retina, provocando visión borrosa o pérdida de la visión. También aumenta el riesgo de cataratas y glaucoma. (hay más) | 990 | ✅ |
| 27 | ¿Qué derechos tienen las personas con discapacidad visual en México? | IA | IA (groq): Tienen derecho a la educación inclusiva, a la igualdad de oportunidades laborales y a la accesibilidad en espacios públicos. La Ley General para la Inclusión de las Personas con Discapacidad garantiza estos derechos. También cuentan con apoyo para la movilidad y la información accesible. | 818 | ✅ |
| 28 | ¿Cómo puedo cruzar la calle de forma segura? | IA | IA (groq): Busca la guía podotáctil y sigue la franja con relieve. Escucha el tráfico y cruza en la esquina o en semáforo con sonido, y pide ayuda si lo necesitas. (hay más) | 527 | ✅ |
| 29 | ¿Qué apoyos da el gobierno a personas con discapacidad? | IA | IA (groq): El gobierno ofrece la Pensión para el Bienestar de las Personas con Discapacidad Permanente, además de apoyos en educación, empleo y accesibilidad. Para conocer los montos y requisitos actuales, consulta la Secretaría de Bienestar. Si necesitas más detalles, pregunta “ayuda”. | 555 | ✅ |
| 30 | Explícame qué es la inteligencia artificial. | IA | IA (groq): La inteligencia artificial es un conjunto de programas que permiten a las máquinas aprender y tomar decisiones sin intervención humana directa. Se basa en algoritmos que analizan datos y mejoran su rendimiento con el tiempo. (hay más) | 558 | ✅ |

## Sinónimos

Los sinónimos no se mandan a la IA: solo se revisa si el servidor los reconoce como comando.

| # | Acción | Frase | Esperado | Resultado del servidor | |
|---|---|---|---|---|---|
| 1 | Describir el entorno | qué hay enfrente | ENVIRONMENT | comando `ENVIRONMENT` | ✅ |
| 1 | Describir el entorno | qué tengo delante | ENVIRONMENT | comando `ENVIRONMENT` | ✅ |
| 1 | Describir el entorno | describe lo que veo | ENVIRONMENT | comando `ENVIRONMENT` | ✅ |
| 1 | Describir el entorno | qué hay a mi alrededor | ENVIRONMENT | comando `ENVIRONMENT` | ✅ |
| 2 | Leer un documento | lee esto | DOCUMENT_READER | comando `DOCUMENT_READER` | ✅ |
| 2 | Leer un documento | léeme el texto | DOCUMENT_READER | comando `DOCUMENT_READER` | ✅ |
| 2 | Leer un documento | qué dice aquí | DOCUMENT_READER | comando `DOCUMENT_READER` | ✅ |
| 2 | Leer un documento | lee la hoja | DOCUMENT_READER | comando `DOCUMENT_READER` | ✅ |
| 3 | Abrir la cámara | abre la cámara | DOCUMENT_READER | comando `DOCUMENT_READER` | ✅ |
| 3 | Abrir la cámara | prende la cámara | DOCUMENT_READER | comando `DOCUMENT_READER` | ✅ |
| 3 | Abrir la cámara | activa la cámara | DOCUMENT_READER | comando `DOCUMENT_READER` | ✅ |
| 3 | Abrir la cámara | enciende el lector | DOCUMENT_READER | comando `DOCUMENT_READER` | ✅ |
| 4 | Batería | cuánta batería tengo | teléfono | iría a la IA | ➖ |
| 4 | Batería | cuánta pila me queda | teléfono | iría a la IA | ➖ |
| 4 | Batería | nivel de carga | teléfono | iría a la IA | ➖ |
| 4 | Batería | se me va a apagar el celular | teléfono | iría a la IA | ➖ |
| 5 | Wifi | estoy conectado | teléfono | iría a la IA | ➖ |
| 5 | Wifi | tengo internet | teléfono | iría a la IA | ➖ |
| 5 | Wifi | hay señal | teléfono | iría a la IA | ➖ |
| 5 | Wifi | a qué red estoy conectado | teléfono | iría a la IA | ➖ |
| 6 | Hora | qué hora es | teléfono | iría a la IA | ➖ |
| 6 | Hora | dime la hora | teléfono | iría a la IA | ➖ |
| 6 | Hora | qué horas son | teléfono | iría a la IA | ➖ |
| 6 | Hora | cuánto es de hora | teléfono | iría a la IA | ➖ |
| 7 | Fecha | qué día es hoy | teléfono | iría a la IA | ➖ |
| 7 | Fecha | qué fecha es | teléfono | iría a la IA | ➖ |
| 7 | Fecha | a cuántos estamos | teléfono | iría a la IA | ➖ |
| 7 | Fecha | en qué mes estamos | teléfono | iría a la IA | ➖ |
| 8 | Subir volumen | sube el volumen | teléfono | iría a la IA | ➖ |
| 8 | Subir volumen | más fuerte | teléfono | iría a la IA | ➖ |
| 8 | Subir volumen | no te escucho | teléfono | iría a la IA | ➖ |
| 8 | Subir volumen | habla más alto | teléfono | iría a la IA | ➖ |
| 9 | Bajar volumen | baja el volumen | teléfono | iría a la IA | ➖ |
| 9 | Bajar volumen | más bajito | teléfono | iría a la IA | ➖ |
| 9 | Bajar volumen | habla más quedito | teléfono | iría a la IA | ➖ |
| 9 | Bajar volumen | estás muy fuerte | teléfono | iría a la IA | ➖ |
| 10 | Notificaciones | tengo mensajes | teléfono | iría a la IA | ➖ |
| 10 | Notificaciones | me llegó algo | teléfono | iría a la IA | ➖ |
| 10 | Notificaciones | lee mis notificaciones | teléfono | iría a la IA | ➖ |
| 10 | Notificaciones | hay avisos nuevos | teléfono | iría a la IA | ➖ |
| 11 | Menú | qué opciones hay | teléfono | iría a la IA | ➖ |
| 11 | Menú | abre el menú | teléfono | iría a la IA | ➖ |
| 11 | Menú | qué puedo hacer | teléfono | iría a la IA | ➖ |
| 11 | Menú | dime los módulos | teléfono | iría a la IA | ➖ |
| 12 | Solicitudes | abre mis solicitudes | REQUESTS | comando `REQUESTS` | ✅ |
| 12 | Solicitudes | mis documentos | REQUESTS | comando `REQUESTS` | ✅ |
| 12 | Solicitudes | mis trámites | REQUESTS | comando `REQUESTS` | ✅ |
| 12 | Solicitudes | ver mis peticiones | REQUESTS | comando `REQUESTS` | ✅ |
| 13 | Actividades | abre las actividades | LEARNING | comando `LEARNING` | ✅ |
| 13 | Actividades | quiero practicar | LEARNING | comando `LEARNING` | ✅ |
| 13 | Actividades | ejercicios | LEARNING | comando `LEARNING` | ✅ |
| 13 | Actividades | quiero entrenar | LEARNING | comando `LEARNING` | ✅ |
| 14 | Iniciar sesión | inicia sesión | teléfono | iría a la IA | ➖ |
| 14 | Iniciar sesión | entrar a mi cuenta | teléfono | iría a la IA | ➖ |
| 14 | Iniciar sesión | quiero entrar | teléfono | iría a la IA | ➖ |
| 14 | Iniciar sesión | ingresar | teléfono | iría a la IA | ➖ |
| 15 | Cerrar sesión | cierra sesión | teléfono | iría a la IA | ➖ |
| 15 | Cerrar sesión | salir de mi cuenta | teléfono | iría a la IA | ➖ |
| 15 | Cerrar sesión | desconéctame | teléfono | iría a la IA | ➖ |
| 15 | Cerrar sesión | ya me voy | teléfono | iría a la IA | ➖ |
| 16 | Repetir | repite | teléfono | iría a la IA | ➖ |
| 16 | Repetir | otra vez | teléfono | iría a la IA | ➖ |
| 16 | Repetir | no te entendí | teléfono | iría a la IA | ➖ |
| 16 | Repetir | qué dijiste | teléfono | iría a la IA | ➖ |
| 17 | Más información | dime más | teléfono | iría a la IA | ➖ |
| 17 | Más información | cuéntame más | teléfono | iría a la IA | ➖ |
| 17 | Más información | explícame a fondo | teléfono | iría a la IA | ➖ |
| 17 | Más información | quiero saber más | teléfono | iría a la IA | ➖ |
| 18 | Lista de comandos | qué comandos hay | teléfono | iría a la IA | ➖ |
| 18 | Lista de comandos | ayuda | teléfono | iría a la IA | ➖ |
| 18 | Lista de comandos | qué puedo decir | teléfono | iría a la IA | ➖ |
| 18 | Lista de comandos | cómo te uso | teléfono | iría a la IA | ➖ |
| 19 | Configuración | abre la configuración | teléfono | iría a la IA | ➖ |
| 19 | Configuración | ajustes | teléfono | iría a la IA | ➖ |
| 19 | Configuración | cambia las opciones | teléfono | iría a la IA | ➖ |
| 19 | Configuración | preferencias | teléfono | iría a la IA | ➖ |
| 20 | Buscar información | busca | IA | iría a la IA | ✅ |
| 20 | Buscar información | investiga | IA | iría a la IA | ✅ |
| 20 | Buscar información | qué es | IA | iría a la IA | ✅ |
| 20 | Buscar información | quién fue | IA | iría a la IA | ✅ |
