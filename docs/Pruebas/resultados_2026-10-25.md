# Resultados: 30 preguntas y 20 sinónimos contra el servidor

> Generado por `docs/Pruebas/correr_pruebas.py` el 25/10/2026 19:03 · modo: con IA (backend/.env)

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
| 4 | ¿Cuánta batería me queda? | teléfono | IA (groq): No tengo forma de saber la batería. Por favor verifica en la pantalla o la app. | 1219 | ➖ |
| 5 | ¿Estoy conectado al wifi? | teléfono | IA (groq): No sé si estás conectado al wifi. | 867 | ➖ |
| 6 | ¿Qué hora es? | teléfono | IA (groq): Para saber la hora, di a la app "qué hora es". | 2307 | ➖ |
| 7 | ¿Qué día es hoy? | teléfono | IA (groq): Por favor, pregúntale a la app “qué día es”. | 899 | ➖ |
| 8 | ¿Puedes subir el volumen? | teléfono | IA (groq): Lo siento, no puedo cambiar el volumen. Puedes intentar usar la app para ajustarlo. | 425 | ➖ |
| 9 | ¿Tengo notificaciones nuevas? | teléfono | IA (groq): No sé, pero puedes decirle a la app “¿tengo notificaciones?” y escuchar lo que te responda. | 1803 | ➖ |
| 10 | ¿Qué opciones tiene el menú? | teléfono | IA (groq): Puedes decir “ayuda” o preguntar “qué comandos hay”. | 1026 | ➖ |
| 11 | ¿Puedes abrir mis solicitudes? | REQUESTS | comando `REQUESTS` | 0 | ✅ |
| 12 | ¿Puedes abrir las actividades? | LEARNING | comando `LEARNING` | 0 | ✅ |
| 13 | ¿Cómo inicio sesión? | teléfono | IA (groq): Puedes decir “ayuda” o “qué comandos hay” en la app. | 857 | ➖ |
| 14 | ¿Me puedes repetir lo último que dijiste? | teléfono | IA (groq): No hay nada que repetir. | 906 | ➖ |
| 15 | ¿Qué comandos puedo usar? | teléfono | IA (groq): Puedes decir "ayuda" o "qué comandos hay" para que la app te indique lo que puedes hacer. | 776 | ➖ |
| 16 | ¿Qué es el sistema braille? | IA | IA (groq): El sistema Braille es un alfabeto táctil que usa puntos en relieve para representar letras, números y signos. Se lee con la punta del dedo, permitiendo que las personas ciegas o con baja visión obtengan información escrita. | 1879 | ✅ |
| 17 | ¿Quién fue Louis Braille? | IA | IA (groq): Louis Braille fue un francés que inventó el alfabeto táctil que lleva su nombre. Nació en 1809 y murió en 1852. Se hizo famoso por su trabajo en educación de personas ciegas. | 1659 | ✅ |
| 18 | ¿Cuál es la capital de Francia? | IA | IA (groq): La capital de Francia es París. | 332 | ✅ |
| 19 | ¿Cómo va a estar el clima mañana? | IA | IA (groq): Lo siento, no dispongo de información actualizada sobre el clima. Por favor consulta la app o un servicio de pronóstico local para saber la previsión de mañana. | 1105 | ⚠️ |
| 20 | ¿Cuántos habitantes tiene México? | IA | IA (groq): México tiene alrededor de 126 millones de habitantes según el último censo. | 1943 | ✅ |
| 21 | ¿Cuándo nació Benito Juárez? | IA | IA (groq): Benito Juárez nació el 21 de marzo de 1806. | 768 | ✅ |
| 22 | ¿Qué es un perro guía y cómo se consigue uno? | IA | IA (groq): Un perro guía es un animal entrenado para ayudar a personas con discapacidad visual. Para conseguir uno, debes contactar con una organización especializada que realice la selección y entrenamiento, y luego recibir el perro una vez aprobado. | 1502 | ✅ |
| 23 | ¿Dónde queda el CECAPI? | IA | IA (groq): El CECAPI se encuentra en la calle Jacarandá 1603, colonia Valle Florido, código postal 34226, Durango, Durango. Su teléfono es 618 455 0146. Abre de lunes a viernes, de dos a siete de la tarde. | 1192 | ✅ |
| 24 | ¿Por qué el cielo es azul? | IA | IA (groq): El cielo parece azul porque la luz del sol se dispersa en la atmósfera. Las moléculas de aire dispersan más las ondas de luz azul, por eso llegan a nuestros ojos en mayor cantidad. | 724 | ✅ |
| 25 | ¿Cómo se prepara un café de olla? | IA | IA (groq): Calienta agua con una rama de canela y un pedazo de piloncillo. Cuando llegue a hervir, añade el café molido y deja reposar un minuto. Sirve y disfruta. | 647 | ✅ |
| 26 | ¿Qué es la diabetes y cómo afecta la vista? | IA | IA (groq): La diabetes es una enfermedad crónica en la cual el cuerpo no produce suficiente insulina o no la usa de manera adecuada, lo que provoca un aumento de los niveles de glucosa en sangre. Con el tiempo, el exceso de azúcar puede dañar los vasos sanguíneos del ojo, provocando retinopatía diabética, visión borrosa y cambios en la percepción del color. En casos graves, puede conducir a la pérdida de la visión. | 1436 | ✅ |
| 27 | ¿Qué derechos tienen las personas con discapacidad visual en México? | IA | IA (groq): En México, la Ley General de los Derechos de las Personas con Discapacidad y la Ley General de Educación para Personas con Discapacidad garantizan acceso a educación, empleo, salud, accesibilidad y participación social sin discriminación. También se reconoce el derecho a información accesible y atención médica especializada. El Código de Derechos de las Personas con Discapacidad refuerza estos principios y promueve su inclusión plena. | 819 | ✅ |
| 28 | ¿Cómo puedo cruzar la calle de forma segura? | IA | IA (groq): Para cruzar la calle, sigue las franjas con relieve que se sienten con el bastón o tus pies, que llevan hacia el cruce. Antes de avanzar, escucha el tráfico y espera a que haya un espacio seguro. Si es posible, cruza en una esquina o en un semáforo con sonido, y si lo necesitas, pide ayuda a alguien cercano. | 619 | ✅ |
| 29 | ¿Qué apoyos da el gobierno a personas con discapacidad? | IA | IA (groq): El gobierno ofrece la Pensión para el Bienestar de las Personas con Discapacidad Permanente. Los montos y requisitos cambian cada año, así que te sugiero preguntar directamente a la Secretaría de Bienestar. También hay apoyos en educación y empleo, pero su disponibilidad varía; lo mejor es consultar a la oficina correspondiente. | 975 | ✅ |
| 30 | Explícame qué es la inteligencia artificial. | IA | IA (groq): La inteligencia artificial es la disciplina que estudia cómo hacer que las máquinas realicen tareas que normalmente requerirían inteligencia humana, como reconocer voces, traducir textos o tomar decisiones. Se basa en algoritmos y grandes volúmenes de datos para que la computadora aprenda y mejore con el tiempo. (hay más) | 1515 | ✅ |

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
