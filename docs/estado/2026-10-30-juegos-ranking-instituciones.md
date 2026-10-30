# Estado de juegos con cámara, ranking e instituciones

Fecha: 30 oct 2026. Revisado en la rama `ajuste/mic-mitad` (parte de `main`, commit `911706f`). Es una lectura del código: no se compiló ni se ejecutó nada.

## Resultado en una línea

| Tema | Estado |
|---|---|
| Lote de juegos con cámara (objetos rojos, llaves, con dificultad creciente) | **No está implementado** |
| Ranking | Implementado, con un defecto en cómo cuenta los puntos |
| Instituciones para la clasificación | Implementadas (texto libre + validación) |
| Panel de consultas propio | No existe; hay Chats, que cubre una parte |

## Juegos

**Qué hay en el Módulo 6 (Centro de Aprendizaje).** El menú (`feature/modulo6_aprendizaje/LearningScreen.kt`, alrededor de las líneas 166 a 186) trae: Sonidos, Vibración, Juegos y "Retos IA · Próximamente".

- **Sonidos:** un banco de ejercicios de audio espacial por niveles (`LearningRepository.kt`: "banco de 34 ejercicios activos"). Cada acierto da 10 puntos y a los 50 sube de nivel (`LearningRepository.kt:229-230`).
- **Vibración:** ejercicios propios (`VibrationScreen.kt`, `VibrationViewModel.kt`).
- **Juegos:** son 10 actividades **escritas como descripción** (`RecreationalActivities.kt`): "Caza el sonido", "La caja misteriosa", Goalball, etc. No son interactivas y ninguna usa la cámara. El propio archivo dice "primer borrador".
- **Retos IA:** solo un botón de "Próximamente". Es el lugar natural para el lote de juegos con cámara, ya sin IA generativa.

**Qué hay que se puede reutilizar para los juegos con cámara (Módulo 7, Entorno):**

- Captura de foto y procesamiento (`EnvironmentRepository.processCapturedPhoto`).
- Detector de objetos TensorFlow Lite (`efficientdet_lite0.tflite`, `EnvironmentRepository.kt:526`).
- Nombre del color dominante (`NombreColor.kt`; `colorDeLaCaja` en `EnvironmentRepository.kt:398`).
- Nombres en español de objetos, con `llave` y `llaves` incluidas (`EtiquetasEntorno.kt:125-126`).

**Dudas técnicas verificables solo con el teléfono:** no comprobé que el detector reconozca unas llaves. Los nombres existen en la tabla, pero eso no prueba que el modelo las detecte. El color rojo sí se puede decidir con los píxeles del centro de la foto.

### Propuesta de diseño (sin código)

1. **Nivel 1, color:** "Busca un objeto rojo y ponlo frente a la cámara". La persona dice o toca "listo", se toma la foto y se mide el color dominante del centro. Si es rojo, se celebra y se cuenta; con tres, se pasa de nivel.
2. **Nivel 2, más colores:** azul, verde, amarillo, uno a la vez.
3. **Nivel 3, objetos con nombre:** "Tráeme unas llaves", "una taza", "unas tijeras", solo con objetos que el detector reconozca de verdad (hay que probarlos uno por uno).
4. **Nivel 4, combinado y con tiempo:** "un objeto rojo y uno redondo", con un tiempo generoso.
5. Pistas por voz, ayuda si falla dos veces seguidas, y que cada acierto sume puntos.

**Hay que decidir:** si estos juegos suman al ranking (la tabla de resultados de ejercicios exige un ejercicio con audio, así que hay que crear una tabla o un tipo nuevo), y qué objetos exactos entran.

## Ranking

Implementado: `RankingScreen.kt`, `RankingViewModel.kt` y `RankingData.kt`.

- Tabla individual con apodo cuando la persona lo elige (hay menores), con alcance "mi institución" o "todas" (`AlcanceRanking`).
- Comando "mi lugar", que dice la posición y los puntos.
- Consulta por instituciones (suma de puntos y número de miembros, `RankingData.kt:66-70`), aunque el comentario del `RankingViewModel` dice que las instituciones no compiten entre sí.

**Defecto encontrado (por lectura, sin ejecutar):** el ranking ordena y filtra por `puntos_totales` (`RankingData.kt:47-48`, `:59-60`, `:69`), pero al subir de nivel ese número **se reinicia a 0** (`LearningRepository.kt:39`) y las consultas excluyen a quien tenga 0. Consecuencias:

- Quien acaba de subir de nivel desaparece de la tabla hasta que vuelve a acertar.
- Se ordena por puntos del nivel actual, no por avance total: una persona de nivel 3 con 10 puntos queda debajo de una de nivel 1 con 40.

**Corrección sugerida (no aplicada):** ordenar por `nivel_actual` y luego por puntos, e incluir a quien tenga nivel mayor a 1 aunque sus puntos sean 0. Mostrar "nivel y puntos". Falta decidir si el ranking debe contar puntos acumulados.

**Pruebas:** en esta rama solo existe `PasswordRulesTest`. El ranking no tiene pruebas.

## Instituciones

Implementadas como **texto libre** (`usuarios.origen`), sin tabla propia de instituciones:

- La persona puede pedir unirse a una institución (`institucion_solicitada`, `solicita_validacion`) y la institución la valida o la rechaza (`confirmarInstitucion`, `MainAppData.kt:190-201`).
- Hay pantallas de gestión para directivo, educador y administrador (`GestionScreen.kt`, `FichaPersona.kt`).
- Se puede quitar a alguien de la institución (`quitarDeInstitucion`).

**Riesgo:** como es texto libre, "CECAPI", "Cecapi" y "cecapi " se tratan como iguales solo en algunas consultas (`COLLATE NOCASE`), pero un espacio de más o un error de escritura crea una institución distinta. No verifiqué si hay una lista de instituciones válidas.

## Panel de consultas propio

No existe como tal. Hoy `ChatsScreen.kt` permite escuchar, repasar y borrar las conversaciones con el asistente. Lo que se quiere (poder repetir lo que dijo el asistente, más tiempo a la vista) puede resolverse de dos formas:

1. **Ampliar Chats** con un botón "Repetir la última respuesta" siempre a la mano.
2. **Panel nuevo** "Consultas", como tarjeta propia del menú, con las últimas respuestas y un botón grande "Repetir" en cada una.

**Hay que decidir:** dónde se abre, cuántas consultas guarda y si se guardan en el teléfono (y por cuánto tiempo, por privacidad).
