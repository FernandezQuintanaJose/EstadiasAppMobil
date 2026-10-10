# Resultados: 30 preguntas y 20 sinónimos contra el servidor

> Generado por `docs/Pruebas/correr_pruebas.py` el 10/10/2026 00:36 · modo: con IA (backend/.env) · corrido en la PC del equipo con el `comandos.py` de la rama `modulo03/servidor-tablas-pruebas`

Se usa la misma función que `POST /entender` (backend/main.py). Leyenda: ✅ correcto · ❌ falla · ➖ la resuelve el teléfono antes de llegar al servidor (aquí solo se revisa que no abra un módulo equivocado) · ⚠️ error o bloqueo.

## Resumen

- **Preguntas (30):** ✅ 18 correctas · ❌ 2 fallas · ➖ 10 las resuelve el teléfono
- **Sinónimos (80 frases de 20 acciones):** ✅ 11 correctas · ❌ 13 fallas · ➖ 56 las resuelve el teléfono
- **Frases que deben abrir un módulo y el servidor reconoce:** 7 de 20

## Observaciones (revisión a mano, 10/10/2026)

La columna ✅ de las preguntas de IA solo dice que **hubo respuesta**, no que fuera correcta. Esto se encontró al leerlas:

**Proveedores**
- **Gemini no contestó ninguna.** Respondió 403 (prohibido) en 15 peticiones y 429 (demasiadas peticiones) en 12. Todo lo contestó Groq como respaldo. Hay que revisar la clave `GEMINI_API_KEY` y el acceso al modelo `gemini-3.8-flash` en AI Studio.
- **Groq limita por minuto.** En una primera corrida sin pausa topó su límite y 5 preguntas (25, 27-30) se quedaron sin respuesta (503). Por eso el script ahora espera 3 segundos entre preguntas.

**Respuestas que inventan datos**
- **23. ¿Dónde queda el CECAPI?** Da una dirección distinta en cada corrida (Av. Insurgentes Sur 1200 y Av. Río de la Pila 5). La IA no sabe dónde está el CECAPI: hay que darle el dato real en `INSTRUCCIONES` o pedirle que diga que no lo sabe.
- **7. ¿Qué día es hoy?** En la primera corrida dijo "lunes 10 de octubre" (era sábado). La IA no sabe la fecha: el teléfono debe contestarla siempre.
- **13, 10 y 15 (iniciar sesión, menú, comandos).** Inventa opciones que la app no tiene, como "ingresa tu correo", "reproducir música" o "llamar a un contacto". Si alguna llega al servidor, la persona recibe instrucciones falsas.
- **14. ¿Me puedes repetir lo último que dijiste?** Sin contexto, contestó algo sin relación ("usa la función calculatePrice..."). "Repite" lo tiene que resolver el teléfono.
- **28. ¿Cómo puedo cruzar la calle de forma segura?** Dice "mira a ambos lados", un consejo que no sirve para una persona ciega. Conviene que `INSTRUCCIONES` le recuerde que la persona no ve.
- **29. Apoyos del gobierno.** Menciona una "Pensión para adultos mayores con discapacidad". Hay que verificar el nombre real del programa antes de confiar en la respuesta.

**Comandos que el servidor no reconoce** (van a la IA, que no puede hacerlos)
- **Leer un documento:** "¿Me puedes leer este documento?" (la lista solo tiene "leer documentos", en plural), "qué dice este letrero", "lee esto", "léeme el texto", "qué dice aquí", "lee la hoja", "enciende el lector".
- **Describir el entorno:** "qué tengo delante", "describe lo que veo", "qué hay a mi alrededor".
- **Solicitudes:** "mis trámites", "ver mis peticiones".
- **Actividades:** "quiero practicar", "ejercicios", "quiero entrenar".

Todas están en la lista de sinónimos de la app (`CommandAlternatives.kt`) pero no en `backend/comandos.py`. Agregarlas es un cambio pequeño, aunque hay que cuidar que no choquen entre sí (por ejemplo, "documentos" ya abre Solicitudes).

**Antes y después de unir `IA_recuerda.py`**
- Con el `comandos.py` anterior (sin quitar acentos), "¿Qué hay enfrente de mí?", "qué hay enfrente", "abre la cámara", "prende la cámara" y "activa la cámara" **no** se reconocían.
- Con el nuevo sí: de las 20 frases que deben abrir un módulo, se reconocen 7 (antes 3).

## Preguntas

| # | Pregunta | Esperado | Resultado del servidor | ms | |
|---|---|---|---|---|---|
| 1 | ¿Qué hay enfrente de mí? | ENVIRONMENT | comando `ENVIRONMENT` | 0 | ✅ |
| 2 | ¿Me puedes leer este documento? | DOCUMENT_READER | IA (groq): Lo siento, no tengo acceso al documento. | 1694 | ❌ |
| 3 | ¿Qué dice este letrero? | DOCUMENT_READER | IA (groq): No lo veo. No sé qué dice. | 645 | ❌ |
| 4 | ¿Cuánta batería me queda? | teléfono | IA (groq): Lo siento, no puedo saberlo. | 689 | ➖ |
| 5 | ¿Estoy conectado al wifi? | teléfono | IA (groq): No tengo forma de saberlo. | 430 | ➖ |
| 6 | ¿Qué hora es? | teléfono | IA (groq): Lo siento, no puedo decirte la hora exacta.   Puedes revisar el reloj de tu dispositivo. | 517 | ➖ |
| 7 | ¿Qué día es hoy? | teléfono | IA (groq): Hoy es 10 de octubre de 2026. | 530 | ➖ |
| 8 | ¿Puedes subir el volumen? | teléfono | IA (groq): Lo siento, no puedo subir el volumen. | 535 | ➖ |
| 9 | ¿Tengo notificaciones nuevas? | teléfono | IA (groq): Lo siento, no puedo comprobar si tienes notificaciones nuevas. Por favor revisa tu dispositivo. | 790 | ➖ |
| 10 | ¿Qué opciones tiene el menú? | teléfono | IA (groq): ¿Podrías especificar a qué menú te refieres? | 688 | ➖ |
| 11 | ¿Puedes abrir mis solicitudes? | REQUESTS | comando `REQUESTS` | 0 | ✅ |
| 12 | ¿Puedes abrir las actividades? | LEARNING | comando `LEARNING` | 0 | ✅ |
| 13 | ¿Cómo inicio sesión? | teléfono | IA (groq): Abre la aplicación y toca “Iniciar sesión”. Ingresa tu correo y contraseña, luego selecciona “Entrar”. | 386 | ➖ |
| 14 | ¿Me puedes repetir lo último que dijiste? | teléfono | IA (groq): Claro. Usa la función `calculatePrice` con el precio base y la tasa de descuento. Por ejemplo, `calculatePrice(1000, 0.05)` te devuelve 950.00. | 812 | ➖ |
| 15 | ¿Qué comandos puedo usar? | teléfono | IA (groq): Puedes usar comandos como:   ¿Qué hora es?   ¿Dónde está el baño?   ¿Puedes encender la luz?   Pon música.   Reproduce mi podcast favorito.   Llama a [nombre].   ¿Cómo está el clima hoy?   ¿Qué tengo programado hoy?   Añade un recordatorio a mi agenda.   Enciende el ventilador.    ¿Algo más? | 689 | ➖ |
| 16 | ¿Qué es el sistema braille? | IA | IA (groq): El sistema Braille es un código táctil para personas con visión limitada. Se compone de puntos en relieve que se forman en grupos de seis y se leen con los dedos. Cada patrón representa una letra, número o signo. | 550 | ✅ |
| 17 | ¿Quién fue Louis Braille? | IA | IA (groq): Louis Braille fue un francés que inventó el sistema de lectura y escritura táctil para personas ciegas. Nació en 1809 y falleció en 1852. Su método sigue siendo la base para la alfabetización de los ciegos en todo el mundo. | 598 | ✅ |
| 18 | ¿Cuál es la capital de Francia? | IA | IA (groq): La capital de Francia es París. | 327 | ✅ |
| 19 | ¿Cómo va a estar el clima mañana? | IA | IA (groq): Lo siento, no dispongo de datos en tiempo real sobre el clima. Te recomiendo consultar la app del Servicio Meteorológico Nacional o buscar en línea. | 1150 | ✅ |
| 20 | ¿Cuántos habitantes tiene México? | IA | IA (groq): Alrededor de 126 millones de habitantes. | 568 | ✅ |
| 21 | ¿Cuándo nació Benito Juárez? | IA | IA (groq): Benito Juárez nació el 21 de marzo de 1806. | 476 | ✅ |
| 22 | ¿Qué es un perro guía y cómo se consigue uno? | IA | IA (groq): Un perro guía es una mascota entrenada para ayudar a personas con discapacidad visual, indicando obstáculos y conduciendo con seguridad. Para conseguirlo, contacta a asociaciones de perros guiadores, completa su proceso de adopción y sigue el entrenamiento que ofrecen. | 518 | ✅ |
| 23 | ¿Dónde queda el CECAPI? | IA | IA (groq): Se encuentra en la Av. Río de la Pila 5, Colonia del Valle, Delegación Benito Juárez, Ciudad de México. | 656 | ✅ |
| 24 | ¿Por qué el cielo es azul? | IA | IA (groq): El cielo es azul porque la atmósfera dispersa la luz del sol. Los rayos de color azul, que tienen una longitud de onda más corta, se dispersan más que los de otros colores, y eso es lo que vemos. | 680 | ✅ |
| 25 | ¿Cómo se prepara un café de olla? | IA | IA (groq): Coloca agua con piloncillo, canela y clavo en una olla. Añade café molido y deja hervir suavemente. Sirve caliente. | 592 | ✅ |
| 26 | ¿Qué es la diabetes y cómo afecta la vista? | IA | IA (groq): La diabetes es una enfermedad que dificulta que el cuerpo use o produzca insulina, lo que provoca altos niveles de azúcar en la sangre. El exceso de glucosa puede dañar los vasos sanguíneos de la retina, causando retinopatía diabética. Con el tiempo, este daño puede provocar visión borrosa o incluso pérdida de la vista. | 789 | ✅ |
| 27 | ¿Qué derechos tienen las personas con discapacidad visual en México? | IA | IA (groq): En México las personas con discapacidad visual tienen derecho a la igualdad y no discriminación, protegido por la Constitución.   Tienen derecho a recibir educación inclusiva y a acceder a información y tecnología adaptadas.   También tienen derecho al acceso a espacios públicos, transporte y oportunidades laborales, con las adecuaciones necesarias. | 579 | ✅ |
| 28 | ¿Cómo puedo cruzar la calle de forma segura? | IA | IA (groq): Primero, busca un paso peatonal y espera que el semáforo se ponga verde para caminar.   Luego, mira a ambos lados antes de cruzar y mantente en la acera hasta que el vehículo haya pasado.   Si la calle es muy ancha, pide ayuda o usa señales de mano. | 732 | ✅ |
| 29 | ¿Qué apoyos da el gobierno a personas con discapacidad? | IA | IA (groq): El gobierno ofrece subsidios a la adquisición de prótesis y dispositivos de ayuda, así como la Pensión para adultos mayores con discapacidad. También promueve el acceso a la educación, al empleo y a servicios de transporte accesibles. | 754 | ✅ |
| 30 | Explícame qué es la inteligencia artificial. | IA | IA (groq): La inteligencia artificial es la simulación de procesos de inteligencia humana por sistemas informáticos. Estos procesos incluyen aprendizaje, razonamiento y autocorrección. | 466 | ✅ |

## Sinónimos

Los sinónimos no se mandan a la IA: solo se revisa si el servidor los reconoce como comando.

| # | Acción | Frase | Esperado | Resultado del servidor | |
|---|---|---|---|---|---|
| 1 | Describir el entorno | qué hay enfrente | ENVIRONMENT | comando `ENVIRONMENT` | ✅ |
| 1 | Describir el entorno | qué tengo delante | ENVIRONMENT | iría a la IA | ❌ |
| 1 | Describir el entorno | describe lo que veo | ENVIRONMENT | iría a la IA | ❌ |
| 1 | Describir el entorno | qué hay a mi alrededor | ENVIRONMENT | iría a la IA | ❌ |
| 2 | Leer un documento | lee esto | DOCUMENT_READER | iría a la IA | ❌ |
| 2 | Leer un documento | léeme el texto | DOCUMENT_READER | iría a la IA | ❌ |
| 2 | Leer un documento | qué dice aquí | DOCUMENT_READER | iría a la IA | ❌ |
| 2 | Leer un documento | lee la hoja | DOCUMENT_READER | iría a la IA | ❌ |
| 3 | Abrir la cámara | abre la cámara | DOCUMENT_READER | comando `DOCUMENT_READER` | ✅ |
| 3 | Abrir la cámara | prende la cámara | DOCUMENT_READER | comando `DOCUMENT_READER` | ✅ |
| 3 | Abrir la cámara | activa la cámara | DOCUMENT_READER | comando `DOCUMENT_READER` | ✅ |
| 3 | Abrir la cámara | enciende el lector | DOCUMENT_READER | iría a la IA | ❌ |
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
| 12 | Solicitudes | mis trámites | REQUESTS | iría a la IA | ❌ |
| 12 | Solicitudes | ver mis peticiones | REQUESTS | iría a la IA | ❌ |
| 13 | Actividades | abre las actividades | LEARNING | comando `LEARNING` | ✅ |
| 13 | Actividades | quiero practicar | LEARNING | iría a la IA | ❌ |
| 13 | Actividades | ejercicios | LEARNING | iría a la IA | ❌ |
| 13 | Actividades | quiero entrenar | LEARNING | iría a la IA | ❌ |
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
