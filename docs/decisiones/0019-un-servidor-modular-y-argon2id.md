# 0019. Un solo servidor con módulos, no un microservicio por cada cosa; contraseñas con Argon2id

> Propuesta · Dueño: Pp · 2 nov 2026 · Relacionada con la [0017](0017-base-en-neon-detras-del-servidor.md) y con [cuentas-servicio.md](../cuentas-servicio.md)

## Contexto

Pp preguntó si conviene partir el servidor en un microservicio por cada cosa (cuentas, IA, reportes...) para que no sea todo un bloque. Hoy el servidor FastAPI tiene cinco rutas, corre en el plan gratuito de Render y la base va en Neon (0017).

## Propuesta

1. **Un solo servidor FastAPI dividido en módulos** (un archivo de rutas por tema: cuentas, asistente, reportes). Cada módulo tiene sus propias pruebas y no importa código de otro; si algún día un módulo crece, se separa sin reescribirlo. Es lo que se llama un monolito modular.
2. **No microservicios todavía**, por tres razones:
   - En el plan gratuito de Render cada servicio se duerme tras unos minutos y tarda cerca de un minuto en despertar. Con cuatro servicios, la persona que depende de la voz esperaría varios despertares, no uno.
   - Cada servicio extra suma su propio despliegue, sus propias variables secretas y su propia forma de fallar, para un equipo de estudiantes.
   - Las cuentas y el uso comparten la misma base (Neon). Separarlos obligaría a que se hablen entre sí por la red.
3. **La app ya está dividida por dentro**: cada función vive en su carpeta (`feature/moduloN`) y las cuentas ya salen por la interfaz `ServicioDeCuentas`. No hace falta partirla en módulos de Gradle ahora.
4. **Contraseñas con Argon2id en el teléfono y en el servidor**, con sal propia por cuenta. Teléfono: 32 MiB, 3 pasadas, 1 hilo. Servidor: 19 MiB, 2 pasadas, 1 hilo (mínimo de OWASP). Las cuentas con el SHA-256 de antes siguen entrando y se vuelven a guardar con Argon2id al iniciar sesión.

## Cuándo reconsiderarlo

Si la IA necesita escalar aparte (mucho más tráfico que las cuentas) o si se contrata un plan de pago donde los servicios no se duerman.
