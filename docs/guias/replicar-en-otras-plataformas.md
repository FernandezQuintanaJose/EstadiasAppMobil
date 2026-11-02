# Cómo llevar la app a otro sistema (iOS, web u otro)

> Escrita el 2 nov 2026 con lo que hay en `main`. Lista lo que hay que repetir para que otra versión se comporte igual, y dónde está cada cosa en el código de Android.

## 1. Lo que no cambia, sea cual sea el sistema

**La app se usa con la voz primero.** Todo lo que sale en pantalla también se dice en voz alta, y todo lo que se puede tocar se puede pedir por voz. El micrófono ocupa la mitad de abajo de la pantalla, siempre. No se puede romper esto por «verse mejor».

**Dos toques para activar.** El primer toque a un botón lo selecciona y lo lee; el segundo lo activa. Está hecho para quien no ve dónde toca. Los botones grandes (Volver, Hablar) y los de las pantallas de una sola acción hacen la acción al primer toque. Ver `core/ui/AssistantComponents.kt` (`BigBtn`, `SeleccionTarjeta`, `MicPad`).

**Encabezado fijo y contenido que se desplaza.** Título y «Volver» no se mueven; abajo hay una barra de desplazamiento visible y arrastrable (`core/ui/Tarjetas.kt`, `ColumnaDesplazable`).

**Siempre se avisa que algo está pasando.** Cualquier espera lleva una rueda y un mensaje que el lector de pantalla anuncia solo (`CargandoConMensaje`).

**Errores con palabras que se pueden oír**, nunca códigos. Ver `AiMessages` para los de la IA y `AccountRules`, `EmailRules`, `PasswordRules` para los de cuentas.

## 2. Las reglas que cada sistema debe repetir

| Tema | Regla | Código de Android |
|---|---|---|
| Edad | Solo mayores de 18; se guarda cuándo lo confirmó | `CuentasLocales.register` |
| Usuario | 3 a 30 caracteres, letras, números, punto, guion, guion bajo; único sin importar mayúsculas | `AccountRules` |
| Contraseña | Al menos 15 letras, puede ser una frase; Argon2id con sal | `PasswordRules`, `PasswordHasher` |
| Correo | Forma válida y errores comunes (`gmial.com`); confirmarlo por código en el servidor | `EmailRules` |
| Roles | Administrador > directivo > educador > alumno; usuario independiente aparte; solo el servidor cambia el rol | `RolePermissions` |
| IA | Apagada por defecto; solo dentro de Consultas | decisiones 0002 y 0010 |
| Sin sesión | Estas pantallas piden cuenta: Chats, Clasificación, Actividades, Gestión, Cuenta, Consultas | `CecapiDestinations.REQUIEREN_SESION` |
| Estados HTTP | 429 muchas preguntas, 400 y 422 no entendió, 401 y 403 sin permiso, 5xx no disponible | `AiFailure.deEstado` |

## 3. Colores y marca

- Fondo `#121212`, tarjetas `#1E1E1E`, borde `#3A3A3A`, acento `#38BDF8`, texto `#FFFFFF` y `#C7C7C7`, éxito `#30D158`, error `#FF453A`, aviso `#F59E0B` (`core/theme/Color.kt`).
- Un color por sección: cámara `#7CC49A`, documentos `#E3B95B`, actividades `#A99BE0`, chats `#7FA8E0`, clasificación `#FFD60A`.
- Logo: monograma VA, la V en blanco `#F5F5FA` y la A en azul `#38BDF8`, con una onda blanca como barra de la A, sobre `#0B0B12`. Vector en `res/drawable/ic_logo_marca.xml` (viewport 512); vista previa en `docs/logo/logo-elegido-va.png`.

## 4. Las cuentas y el servidor

- La app habla con un solo servidor FastAPI (`backend/`), dividido en módulos, no en microservicios (decisión 0019).
- El contrato de cuentas, las rutas y la migración están en [cuentas-servicio.md](../cuentas-servicio.md). Una versión nueva debe implementar esas rutas del lado del cliente y nada más; no guarda cuentas por su cuenta.
- La app no lleva llaves de ningún proveedor. Todo pasa por el servidor.

## 5. Funciones y de dónde sale cada una

| Función | Cómo funciona hoy | Qué cambia en otro sistema |
|---|---|---|
| Voz a texto y texto a voz | `SpeechRecognizer` y `TextToSpeech` de Android | Usar los del sistema nuevo; los textos son los mismos |
| Leer texto, QR, traducir, qué hay enfrente | ML Kit y TensorFlow Lite en el teléfono | Equivalentes del sistema (Vision en iOS); el orden de lectura (`ReadingOrder.kt`) es lógica pura y se puede copiar |
| Códigos de barras | ML Kit y Open Food Facts (solo se envía el número) | Igual |
| Actividades y juegos | Reglas en `feature/modulo6_aprendizaje` | Copiar las reglas; el audio y la vibración los pone el sistema |
| Widgets | `widget/` | En iOS, un widget o atajo por cada uno: solo micrófono, Actividades y Cámara |
| Vigilancia de memoria y calor | `SaludDelDispositivo`, pantalla de esperar o cerrar | Avisos de memoria del sistema; no cerrar sin avisar |

## 6. Para empezar una versión nueva

1. Leer las decisiones en `docs/decisiones/` (sobre todo 0002, 0003, 0006, 0012, 0017 y 0019).
2. Armar primero el servidor de cuentas y la pantalla de entrar y crear cuenta, con las reglas de la sección 2.
3. Después la pantalla principal con micrófono y los botones, siguiendo la sección 1.
4. Probar con una persona que no ve antes de agregar más pantallas.
