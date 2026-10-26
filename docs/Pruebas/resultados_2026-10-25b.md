# Resultados: 30 preguntas y 20 sinónimos contra el servidor

> Generado por `docs/Pruebas/correr_pruebas.py` el 25/10/2026 19:08 · modo: con IA (backend/.env)

Se usa la misma función que `POST /entender` (backend/main.py). Leyenda: ✅ correcto · ❌ falla · ➖ la resuelve el teléfono antes de llegar al servidor (aquí solo se revisa que no abra un módulo equivocado) · ⚠️ error, bloqueo, respuesta "no lo sé" / "no dispongo de" o marca MAS_DISPONIBLE en el texto (revisar a mano).

## Resumen

- **Preguntas (30):** ✅ 19 correctas · ➖ 10 las resuelve el teléfono · ⚠️ 1 avisos
- **Sinónimos (80 frases de 20 acciones):** ✅ 24 correctas · ➖ 56 las resuelve el teléfono
- **Frases que deben abrir un módulo y el servidor reconoce:** 20 de 20

## Preguntas

| # | Pregunta | Esperado | Resultado del servidor | ms | |
|---|---|---|---|---|---|
| 1 | ¿Qué hay enfrente de mí? | ENVIRONMENT | comando `ENVIRONMENT` | 0 | ✅ |
| 2 | ¿Me puedes leer este documento? | DOCUMENT_READER | comando `DOCUMENT_READER` | 0 | ✅ |
| 3 | ¿Qué dice este letrero? | DOCUMENT_READER | comando `DOCUMENT_READER` | 0 | ✅ |
| 4 | ¿Cuánta batería me queda? | teléfono | IA (groq): Puedes preguntar a la app cuánta batería te queda. | 1149 | ➖ |
| 5 | ¿Estoy conectado al wifi? | teléfono | IA (groq): No lo sé. Pregunta a la app si está conectado al Wi‑Fi. Puedes decir «ayuda» o «qué comandos hay» para ver las opciones. | 690 | ➖ |
| 6 | ¿Qué hora es? | teléfono | IA (groq): Para saber la hora, pregúntale directamente a la app. | 590 | ➖ |
| 7 | ¿Qué día es hoy? | teléfono | IA (groq): Para saber qué día es hoy, pregunta a la app. (hay más) | 521 | ➖ |
| 8 | ¿Puedes subir el volumen? | teléfono | IA (groq): Para subir el volumen, intenta usar la función de audio de la app o consulta dentro de ella con algo como “¿puedo subir el volumen?” (hay más) | 628 | ➖ |
| 9 | ¿Tengo notificaciones nuevas? | teléfono | IA (groq): No tengo forma de saber si tienes notificaciones nuevas.   Revisa la aplicación o pregúntale directamente a la app. | 615 | ➖ |
| 10 | ¿Qué opciones tiene el menú? | teléfono | IA (groq): No tengo acceso al menú, por favor pregúntale a la app: «ayuda» o «qué comandos hay». | 561 | ➖ |
| 11 | ¿Puedes abrir mis solicitudes? | REQUESTS | comando `REQUESTS` | 0 | ✅ |
| 12 | ¿Puedes abrir las actividades? | LEARNING | comando `LEARNING` | 0 | ✅ |
| 13 | ¿Cómo inicio sesión? | teléfono | IA (groq): Para iniciar sesión, primero verifica si la app ofrece una opción de “iniciar sesión” en su pantalla principal.   Si no la ves, dime “ayuda” o “qué comandos hay” y la aplicación te guiará paso a paso. (hay más) | 656 | ➖ |
| 14 | ¿Me puedes repetir lo último que dijiste? | teléfono | IA (groq): No recuerdo haber dicho nada antes.   ¿Podrías aclarar lo que necesitas repetir? | 829 | ➖ |
| 15 | ¿Qué comandos puedo usar? | teléfono | IA (groq): Para ver los comandos disponibles, di "ayuda" o "qué comandos hay". Si necesitas más ayuda, simplemente pregúntalo. | 711 | ➖ |
| 16 | ¿Qué es el sistema braille? | IA | IA (groq): El sistema Braille es una forma de lectura y escritura táctil que utiliza puntos en relieve dispuestos en bloques de seis. Se emplea para que personas ciegas o con baja visión puedan leer libros, textos y señalizaciones. Está basado en 63 caracteres que representan letras, números y signos de puntuación. | 1192 | ✅ |
| 17 | ¿Quién fue Louis Braille? | IA | IA (groq): Louis Braille fue un inventor francés que desarrolló el sistema de escritura táctil para personas ciegas. Creó el código Braille en 1824, facilitando la lectura y escritura en la comunidad daltónica y ciega. Sus contribuciones siguen siendo fundamentales en la educación accesible. | 1426 | ✅ |
| 18 | ¿Cuál es la capital de Francia? | IA | IA (groq): La capital de Francia es París. | 486 | ✅ |
| 19 | ¿Cómo va a estar el clima mañana? | IA | IA (groq): No dispongo de esa información. Puedes preguntar en la app o consultar un servicio de pronóstico. | 831 | ⚠️ |
| 20 | ¿Cuántos habitantes tiene México? | IA | IA (groq): Alrededor de 126 millones de habitantes. (hay más) | 570 | ✅ |
| 21 | ¿Cuándo nació Benito Juárez? | IA | IA (groq): Nació el 21 de marzo de 1806. | 615 | ✅ |
| 22 | ¿Qué es un perro guía y cómo se consigue uno? | IA | IA (groq): Un perro guía es un animal entrenado para asistir a personas con discapacidad visual, ayudándolas a desplazarse con seguridad y a evitar obstáculos. Para conseguir uno, puedes contactar a organizaciones locales de perros guía o programas de entrenamiento, presentar la documentación médica requerida y completar el proceso de adopción y certificación. | 538 | ✅ |
| 23 | ¿Dónde queda el CECAPI? | IA | IA (groq): El CECAPI se encuentra en la calle Jacarandá número 1603, colonia Valle Florido, 34226, Durango. Abre de lunes a viernes de 2 p.m. a 7 p.m. Puedes llamarlos al 618 455 0146. | 637 | ✅ |
| 24 | ¿Por qué el cielo es azul? | IA | IA (groq): El cielo es azul porque la luz del sol se dispersa en la atmósfera. Las moléculas de aire dispersan más la luz azul que las otras longitudes de onda. | 384 | ✅ |
| 25 | ¿Cómo se prepara un café de olla? | IA | IA (groq): Primero, calienta agua en una olla hasta que hierva. Añade piloncillo, canela y clavos, y deja hervir lentamente hasta que el azúcar se disuelva. Cuela el café y sirve caliente, disfrutando su aroma. | 644 | ✅ |
| 26 | ¿Qué es la diabetes y cómo afecta la vista? | IA | IA (groq): Diabetes es una condición en la cual el cuerpo no controla bien el azúcar en sangre. El exceso de glucosa puede dañar los vasos sanguíneos de la retina, provocando retinopatía diabética y pérdida de visión. El control temprano del azúcar y visitas regulares al oftalmólogo ayudan a prevenir complicaciones. | 554 | ✅ |
| 27 | ¿Qué derechos tienen las personas con discapacidad visual en México? | IA | IA (groq): Tienen derecho a la accesibilidad en espacios públicos y privados, a la educación inclusiva, a la capacitación y empleo digno, y al transporte accesible.   Estos derechos están garantizados por la Ley General para la Inclusión de las Personas con Discapacidad.   También cuentan con la participación plena en la vida social, cultural y política. | 931 | ✅ |
| 28 | ¿Cómo puedo cruzar la calle de forma segura? | IA | IA (groq): Para cruzar, busca la guía podotáctil, una franja con relieve que se siente con el bastón o con el pie y te indica el camino. Escucha el tráfico, espera que el sonido cambie y pide ayuda si lo necesitas. Cruza en lugares con semáforos de sonido o en esquinas donde la señal te indique que es seguro pasar. | 768 | ✅ |
| 29 | ¿Qué apoyos da el gobierno a personas con discapacidad? | IA | IA (groq): El gobierno federal entrega la Pensión para el Bienestar de las Personas con Discapacidad Permanente, que brinda apoyo económico a quienes califican. También existen descuentos en transporte, en servicios médicos y programas de inclusión laboral; los montos y requisitos cambian cada año, así que consulta con la Secretaría de Bienestar. (hay más) | 893 | ✅ |
| 30 | Explícame qué es la inteligencia artificial. | IA | IA (groq): La inteligencia artificial es un conjunto de programas que permiten a las máquinas aprender y realizar tareas que normalmente requieren inteligencia humana, como reconocer voces, traducir textos o tomar decisiones. Estas máquinas usan algoritmos y grandes volúmenes de datos para mejorar su rendimiento con el tiempo. | 645 | ✅ |

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
