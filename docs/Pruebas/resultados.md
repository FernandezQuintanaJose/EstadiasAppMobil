# Resultados: 30 preguntas y 20 sinónimos contra el servidor

> Generado por `docs/Pruebas/correr_pruebas.py` el 10/10/2026 00:25 · modo: sin IA (solo detección de comandos)

Se usa la misma función que `POST /entender` (backend/main.py). Leyenda: ✅ correcto · ❌ falla · ➖ la resuelve el teléfono antes de llegar al servidor (aquí solo se revisa que no abra un módulo equivocado) · ⚠️ error o bloqueo.

## Resumen

- **Preguntas (30):** ✅ 18 correctas · ❌ 2 fallas · ➖ 10 las resuelve el teléfono
- **Sinónimos (80 frases de 20 acciones):** ✅ 11 correctas · ❌ 13 fallas · ➖ 56 las resuelve el teléfono
- **Frases que deben abrir un módulo y el servidor reconoce:** 7 de 20

## Preguntas

| # | Pregunta | Esperado | Resultado del servidor | ms | |
|---|---|---|---|---|---|
| 1 | ¿Qué hay enfrente de mí? | ENVIRONMENT | comando `ENVIRONMENT` | 0 | ✅ |
| 2 | ¿Me puedes leer este documento? | DOCUMENT_READER | iría a la IA | 0 | ❌ |
| 3 | ¿Qué dice este letrero? | DOCUMENT_READER | iría a la IA | 0 | ❌ |
| 4 | ¿Cuánta batería me queda? | teléfono | iría a la IA | 0 | ➖ |
| 5 | ¿Estoy conectado al wifi? | teléfono | iría a la IA | 0 | ➖ |
| 6 | ¿Qué hora es? | teléfono | iría a la IA | 0 | ➖ |
| 7 | ¿Qué día es hoy? | teléfono | iría a la IA | 0 | ➖ |
| 8 | ¿Puedes subir el volumen? | teléfono | iría a la IA | 0 | ➖ |
| 9 | ¿Tengo notificaciones nuevas? | teléfono | iría a la IA | 0 | ➖ |
| 10 | ¿Qué opciones tiene el menú? | teléfono | iría a la IA | 0 | ➖ |
| 11 | ¿Puedes abrir mis solicitudes? | REQUESTS | comando `REQUESTS` | 0 | ✅ |
| 12 | ¿Puedes abrir las actividades? | LEARNING | comando `LEARNING` | 0 | ✅ |
| 13 | ¿Cómo inicio sesión? | teléfono | iría a la IA | 0 | ➖ |
| 14 | ¿Me puedes repetir lo último que dijiste? | teléfono | iría a la IA | 0 | ➖ |
| 15 | ¿Qué comandos puedo usar? | teléfono | iría a la IA | 0 | ➖ |
| 16 | ¿Qué es el sistema braille? | IA | iría a la IA | 0 | ✅ |
| 17 | ¿Quién fue Louis Braille? | IA | iría a la IA | 0 | ✅ |
| 18 | ¿Cuál es la capital de Francia? | IA | iría a la IA | 0 | ✅ |
| 19 | ¿Cómo va a estar el clima mañana? | IA | iría a la IA | 0 | ✅ |
| 20 | ¿Cuántos habitantes tiene México? | IA | iría a la IA | 0 | ✅ |
| 21 | ¿Cuándo nació Benito Juárez? | IA | iría a la IA | 0 | ✅ |
| 22 | ¿Qué es un perro guía y cómo se consigue uno? | IA | iría a la IA | 0 | ✅ |
| 23 | ¿Dónde queda el CECAPI? | IA | iría a la IA | 0 | ✅ |
| 24 | ¿Por qué el cielo es azul? | IA | iría a la IA | 0 | ✅ |
| 25 | ¿Cómo se prepara un café de olla? | IA | iría a la IA | 0 | ✅ |
| 26 | ¿Qué es la diabetes y cómo afecta la vista? | IA | iría a la IA | 0 | ✅ |
| 27 | ¿Qué derechos tienen las personas con discapacidad visual en México? | IA | iría a la IA | 0 | ✅ |
| 28 | ¿Cómo puedo cruzar la calle de forma segura? | IA | iría a la IA | 0 | ✅ |
| 29 | ¿Qué apoyos da el gobierno a personas con discapacidad? | IA | iría a la IA | 0 | ✅ |
| 30 | Explícame qué es la inteligencia artificial. | IA | iría a la IA | 0 | ✅ |

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
