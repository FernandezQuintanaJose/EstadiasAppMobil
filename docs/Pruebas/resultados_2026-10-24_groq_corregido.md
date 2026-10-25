# Resultados: 30 preguntas y 20 sinónimos contra el servidor

> Generado por `docs/Pruebas/correr_pruebas.py` el 24/10/2026 20:25 · modo: con IA (backend/.env)
> Corrida 3 de 3 · Groq como principal, con `INSTRUCCIONES` corregidas (datos reales del CECAPI, la persona no ve, sin fecha ni funciones inventadas) y sinónimos nuevos en `comandos.py`.
>

Se usa la misma función que `POST /entender` (backend/main.py). Leyenda: ✅ correcto · ❌ falla · ➖ la resuelve el teléfono antes de llegar al servidor (aquí solo se revisa que no abra un módulo equivocado) · ⚠️ error o bloqueo.

## Resumen

- **Preguntas (30):** ✅ 20 correctas · ➖ 10 las resuelve el teléfono
- **Sinónimos (80 frases de 20 acciones):** ✅ 24 correctas · ➖ 56 las resuelve el teléfono
- **Frases que deben abrir un módulo y el servidor reconoce:** 20 de 20

## Preguntas

| # | Pregunta | Esperado | Resultado del servidor | ms | |
|---|---|---|---|---|---|
| 1 | ¿Qué hay enfrente de mí? | ENVIRONMENT | comando `ENVIRONMENT` | 0 | ✅ |
| 2 | ¿Me puedes leer este documento? | DOCUMENT_READER | comando `DOCUMENT_READER` | 0 | ✅ |
| 3 | ¿Qué dice este letrero? | DOCUMENT_READER | comando `DOCUMENT_READER` | 0 | ✅ |
| 4 | ¿Cuánta batería me queda? | teléfono | IA (groq): No lo sé. | 1501 | ➖ |
| 5 | ¿Estoy conectado al wifi? | teléfono | IA (groq): No lo sé, no tengo acceso a esa información. | 557 | ➖ |
| 6 | ¿Qué hora es? | teléfono | IA (groq): No tengo acceso a la hora. Pregúntale a la app: "¿qué hora es?" | 332 | ➖ |
| 7 | ¿Qué día es hoy? | teléfono | IA (groq): No sé la fecha. Pregúntale a la app 'qué día es'. | 285 | ➖ |
| 8 | ¿Puedes subir el volumen? | teléfono | IA (groq): No puedo cambiar el volumen. Usa los botones de volumen del dispositivo o pide ayuda a alguien. | 479 | ➖ |
| 9 | ¿Tengo notificaciones nuevas? | teléfono | IA (groq): No tengo forma de saberlo. Puedes preguntar a la app. | 422 | ➖ |
| 10 | ¿Qué opciones tiene el menú? | teléfono | IA (groq): Para conocer las opciones puedes decir “ayuda” o “qué comandos hay”. | 582 | ➖ |
| 11 | ¿Puedes abrir mis solicitudes? | REQUESTS | comando `REQUESTS` | 0 | ✅ |
| 12 | ¿Puedes abrir las actividades? | LEARNING | comando `LEARNING` | 0 | ✅ |
| 13 | ¿Cómo inicio sesión? | teléfono | IA (groq): Para iniciar sesión, di "ayuda" o pregunta "qué comandos hay" dentro de la app. La aplicación te guiará paso a paso. | 655 | ➖ |
| 14 | ¿Me puedes repetir lo último que dijiste? | teléfono | IA (groq): Lo siento, no he dicho nada todavía. | 631 | ➖ |
| 15 | ¿Qué comandos puedo usar? | teléfono | IA (groq): Puede decir 'ayuda' o 'qué comandos hay'. | 398 | ➖ |
| 16 | ¿Qué es el sistema braille? | IA | IA (groq): El sistema braille es un código táctil de puntos en relieve que permite a las personas ciegas leer y escribir. Se compone de 6 puntos dispuestos en 3 filas y 2 columnas. Cada combinación representa letras, números o signos. | 539 | ✅ |
| 17 | ¿Quién fue Louis Braille? | IA | IA (groq): Louis Braille fue un inventor francés que desarrolló el sistema Braille, un código táctil que permite a las personas ciegas leer y escribir. Nació en 1809 y murió en 1852. | 820 | ✅ |
| 18 | ¿Cuál es la capital de Francia? | IA | IA (groq): La capital de Francia es París. | 482 | ✅ |
| 19 | ¿Cómo va a estar el clima mañana? | IA | IA (groq): Lo siento, no dispongo de la información del clima.   Puedes preguntar a la app o buscar en un servicio de pronóstico. | 536 | ✅ |
| 20 | ¿Cuántos habitantes tiene México? | IA | IA (groq): México tiene aproximadamente 126 millones de habitantes. | 652 | ✅ |
| 21 | ¿Cuándo nació Benito Juárez? | IA | IA (groq): Nació el 21 de marzo de 1806. | 733 | ✅ |
| 22 | ¿Qué es un perro guía y cómo se consigue uno? | IA | IA (groq): Un perro guía es un animal entrenado para ayudar a personas con discapacidad visual. Se consigue a través de organizaciones especializadas que entrenan y asignan estos perros. Para obtener uno, puedes contactar con la organización de tu zona y solicitar información sobre el proceso de adopción y entrenamiento. | 669 | ✅ |
| 23 | ¿Dónde queda el CECAPI? | IA | IA (groq): El CECAPI está en la calle Jacarandá 1603, colonia Valle Florido, código postal 34226, Durango, Durango. | 402 | ✅ |
| 24 | ¿Por qué el cielo es azul? | IA | IA (groq): El cielo parece azul porque la luz del sol se dispersa al chocar con las moléculas del aire, y las longitudes de onda cortas (el azul) se dispersan mucho más que las largas. Esta dispersión hace que, desde la Tierra, veamos mayormente luz azul proveniente del cielo. | 560 | ✅ |
| 25 | ¿Cómo se prepara un café de olla? | IA | IA (groq): Hierve agua con piloncillo, canela y clavos de olor. Agrega café molido y deja reposar 5 minutos. Cuela y sirve caliente. | 479 | ✅ |
| 26 | ¿Qué es la diabetes y cómo afecta la vista? | IA | IA (groq): La diabetes es una enfermedad en la que el cuerpo no produce suficiente insulina o no la usa bien, provocando altos niveles de azúcar en la sangre. El azúcar elevado daña los vasos sanguíneos en la retina, lo que puede causar visión borrosa, manchas y, si no se controla, ceguera. | 643 | ✅ |
| 27 | ¿Qué derechos tienen las personas con discapacidad visual en México? | IA | IA (groq): Tienes derecho al acceso a la información en formatos accesibles, a la educación inclusive y a la participación plena en la sociedad. También tienes derecho a la igualdad de oportunidades, la no discriminación y la accesibilidad en espacios públicos y privados. Para saber más, llama al CECAPI al 618 455 0146. | 1046 | ✅ |
| 28 | ¿Cómo puedo cruzar la calle de forma segura? | IA | IA (groq): Espera a que suene el timbre del semáforo y cuando indique “caminata” avanza lentamente.   Utiliza la zanja de acera o la línea de guía en el suelo para saber el borde de la calle.   Si te sientes inseguro, pide a alguien que te acompañe o que te indique dónde cruzar. | 777 | ✅ |
| 29 | ¿Qué apoyos da el gobierno a personas con discapacidad? | IA | IA (groq): Lo siento, no dispongo de esa información. Te recomiendo buscar en la Secretaría de Bienestar o llamar a la línea de atención a la discapacidad de tu estado. | 932 | ✅ |
| 30 | Explícame qué es la inteligencia artificial. | IA | IA (groq): La inteligencia artificial es una rama de la informática que crea sistemas capaces de aprender, razonar y tomar decisiones, imitando funciones cognitivas humanas. Estas tecnologías se usan para resolver tareas que requieren análisis, reconocimiento o predicción, como el reconocimiento de voz, la visión por computadora o la recomendación de contenidos. | 629 | ✅ |

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
