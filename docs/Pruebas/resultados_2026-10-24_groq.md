# Resultados: 30 preguntas y 20 sinónimos contra el servidor

> Generado por `docs/Pruebas/correr_pruebas.py` el 24/10/2026 19:12 · modo: con IA (backend/.env)
> Corrida 2 de 3 · Groq como proveedor principal (`IA_ORDEN=groq,gemini`), antes de corregir `INSTRUCCIONES` y `comandos.py`. Corrida 1: `resultados.md` (10/10/2026, Gemini con 403).
>

Se usa la misma función que `POST /entender` (backend/main.py). Leyenda: ✅ correcto · ❌ falla · ➖ la resuelve el teléfono antes de llegar al servidor (aquí solo se revisa que no abra un módulo equivocado) · ⚠️ error o bloqueo.

## Resumen

- **Preguntas (30):** ✅ 18 correctas · ❌ 2 fallas · ➖ 10 las resuelve el teléfono
- **Sinónimos (80 frases de 20 acciones):** ✅ 11 correctas · ❌ 13 fallas · ➖ 56 las resuelve el teléfono
- **Frases que deben abrir un módulo y el servidor reconoce:** 7 de 20

## Preguntas

| # | Pregunta | Esperado | Resultado del servidor | ms | |
|---|---|---|---|---|---|
| 1 | ¿Qué hay enfrente de mí? | ENVIRONMENT | comando `ENVIRONMENT` | 1 | ✅ |
| 2 | ¿Me puedes leer este documento? | DOCUMENT_READER | IA (groq): Lo siento, no puedo leer documentos sin acceso. | 1516 | ❌ |
| 3 | ¿Qué dice este letrero? | DOCUMENT_READER | IA (groq): Lo siento, no puedo ver el letrero. Necesito que describas el texto o me lo digas. | 484 | ❌ |
| 4 | ¿Cuánta batería me queda? | teléfono | IA (groq): No puedo saberlo. Revisa el indicador de batería en tu dispositivo. | 482 | ➖ |
| 5 | ¿Estoy conectado al wifi? | teléfono | IA (groq): No puedo determinarlo. | 389 | ➖ |
| 6 | ¿Qué hora es? | teléfono | IA (groq): Lo siento, no puedo decirte la hora exacta. | 1243 | ➖ |
| 7 | ¿Qué día es hoy? | teléfono | IA (groq): Hoy es sábado 25 de octubre de 2026. | 388 | ➖ |
| 8 | ¿Puedes subir el volumen? | teléfono | IA (groq): Lo siento, no puedo subir el volumen. | 498 | ➖ |
| 9 | ¿Tengo notificaciones nuevas? | teléfono | IA (groq): Lo siento, no tengo acceso a esa información. | 513 | ➖ |
| 10 | ¿Qué opciones tiene el menú? | teléfono | IA (groq): El menú tiene opciones como Inicio, Servicios, Información, Ayuda y Salir. | 546 | ➖ |
| 11 | ¿Puedes abrir mis solicitudes? | REQUESTS | comando `REQUESTS` | 0 | ✅ |
| 12 | ¿Puedes abrir las actividades? | LEARNING | comando `LEARNING` | 0 | ✅ |
| 13 | ¿Cómo inicio sesión? | teléfono | IA (groq): Para iniciar sesión, abre la aplicación o el sitio web de CECAPI y toca “Iniciar sesión”. Luego ingresa tu correo electrónico y contraseña en los campos correspondientes y presiona el botón “Entrar”. Si olvidaste tu contraseña, puedes usar la opción “¿Olvidaste tu contraseña?” para recuperarla. | 437 | ➖ |
| 14 | ¿Me puedes repetir lo último que dijiste? | teléfono | IA (groq): Claro, dime qué necesitas y con gusto te ayudo. | 334 | ➖ |
| 15 | ¿Qué comandos puedo usar? | teléfono | IA (groq): Pídele al asistente que te dé la hora, reproduzca música, controle dispositivos inteligentes o te ayude con el clima. También puedes usarlo para hacer búsquedas o recordatorios. | 571 | ➖ |
| 16 | ¿Qué es el sistema braille? | IA | IA (groq): El Braille es un código de puntos en relieve que representa letras, números y signos. Se usa para que las personas con discapacidad visual lean mediante el tacto. | 484 | ✅ |
| 17 | ¿Quién fue Louis Braille? | IA | IA (groq): Louis Braille fue un educador francés que desarrolló el sistema de lectura táctil que lleva su nombre. Nació en 1809 y perdió la visión en su infancia. Su sistema permitió a las personas ciegas leer y escribir. | 646 | ✅ |
| 18 | ¿Cuál es la capital de Francia? | IA | IA (groq): La capital de Francia es París. | 404 | ✅ |
| 19 | ¿Cómo va a estar el clima mañana? | IA | IA (groq): No tengo acceso a datos en tiempo real, lo siento. | 673 | ✅ |
| 20 | ¿Cuántos habitantes tiene México? | IA | IA (groq): Al 2023, México tiene alrededor de 126 millones de habitantes. | 518 | ✅ |
| 21 | ¿Cuándo nació Benito Juárez? | IA | IA (groq): Benito Juárez nació el 21 de marzo de 1806. | 549 | ✅ |
| 22 | ¿Qué es un perro guía y cómo se consigue uno? | IA | IA (groq): Un perro guía es un animal entrenado para ayudar a una persona con discapacidad visual a moverse con seguridad. Se consigue a través de organizaciones de perros de asistencia; se debe solicitar, completar el programa de entrenamiento y seguir las pautas de cuidado. | 577 | ✅ |
| 23 | ¿Dónde queda el CECAPI? | IA | IA (groq): El CECAPI está ubicado en la Av. Insurgentes Sur, 2001, Colonia Del Valle, Ciudad de México. | 311 | ✅ |
| 24 | ¿Por qué el cielo es azul? | IA | IA (groq): El cielo es azul porque la luz del sol se dispersa en la atmósfera, y las moléculas de aire dispersan más la luz azul que la roja. | 574 | ✅ |
| 25 | ¿Cómo se prepara un café de olla? | IA | IA (groq): Para preparar café de olla, mezcla agua con piloncillo y una rama de canela en una olla y lleva a ebullición. Baja el fuego y añade café molido, cocina a fuego bajo unos 10 minutos. Sirve caliente, tamizando el café y disfruta. | 450 | ✅ |
| 26 | ¿Qué es la diabetes y cómo afecta la vista? | IA | IA (groq): La diabetes es una enfermedad crónica en la que el cuerpo no regula bien la glucosa en la sangre. El exceso de azúcar puede dañar los vasos sanguíneos de la retina, provocando visión borrosa, manchas y, en casos graves, ceguera. Con un control adecuado, se pueden reducir estos riesgos. | 339 | ✅ |
| 27 | ¿Qué derechos tienen las personas con discapacidad visual en México? | IA | IA (groq): Las personas con discapacidad visual tienen derecho a la educación inclusiva, al acceso a la información y a los recursos en formatos accesibles. También pueden ejercer el derecho al trabajo digno, con medidas de apoyo y no discriminación. Además, cuentan con el derecho a la movilidad segura y a participar plenamente en la vida social y política del país. | 420 | ✅ |
| 28 | ¿Cómo puedo cruzar la calle de forma segura? | IA | IA (groq): Busca el semáforo o el paso de peatones. Espera a que el semáforo diga verde o el cruce esté seguro. Cruza mirando ambos lados y no uses tu celular. | 500 | ✅ |
| 29 | ¿Qué apoyos da el gobierno a personas con discapacidad? | IA | IA (groq): El gobierno ofrece pensiones de apoyo, subsidios para la educación y capacitación, y exenciones fiscales. También facilita transporte público accesible y ayudas para la adquisición de tecnologías de asistencia. Además, existen programas de inclusión laboral y adaptaciones en el entorno público. | 627 | ✅ |
| 30 | Explícame qué es la inteligencia artificial. | IA | IA (groq): La inteligencia artificial es un campo de la informática que crea sistemas capaces de aprender y tomar decisiones. Utiliza algoritmos y datos para que las máquinas imiten tareas humanas, como reconocer voz o tomar decisiones. | 769 | ✅ |

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
