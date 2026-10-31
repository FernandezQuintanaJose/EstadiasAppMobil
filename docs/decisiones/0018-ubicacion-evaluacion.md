# 0018. Ubicación para orientarse: evaluación (no implementada)

> Propuesta · Dueño: Pp · Evaluada el 31 oct 2026 · Fuente: idea de Pp del 30 oct y `docs/agentes/normativa/borradores-politicas.md`
> **No es asesoría legal.** Es una evaluación técnica para que Pp y la persona abogada decidan.

## La idea

En la cámara (o junto a ella), ayudar a la persona a ubicarse:
1. **¿Dónde estoy?**: decir en voz alta la calle y la colonia aproximadas.
2. **Cómo llegar**: decir un destino y abrir Google Maps ya en modo caminar.
3. **Mis lugares**: guardar sitios pequeños (casa, institución) con notas de cómo orientarse, y volver a escucharlas.

## Qué dicen hoy nuestros documentos

- La línea «sin ubicación precisa» (`borradores-politicas.md`, sección de menores) se escribió para el peor caso con menores. El 30 oct se confirmó que **todas las personas usuarias son adultas**, así que esa restricción ya no manda por sí sola.
- La app **no usa ubicación hoy**. Se quitó Emergencias, que era lo único que la pedía. El manifiesto seguía declarando el permiso de ubicación aproximada sin usarlo; se quitó en esta misma fecha (un permiso declarado y sin uso es una promesa falsa en el aviso de privacidad).
- El dato sensible ya identificado es la **discapacidad visual** (LFPDPPP art. 8, consentimiento expreso y por escrito). Una ubicación de una persona que usa una app para ciegas la relaciona con ese dato.

## Evaluación de privacidad

| Tema | Valoración |
|---|---|
| ¿Es salud, como Emergencias? | No. Emergencias tenía contactos de terceras personas y contexto de peligro o salud. La ubicación en sí no es un dato de salud. |
| ¿Es útil para la población? | Sí. Orientarse es de las cosas más pedidas por personas ciegas. |
| ¿Es dato personal? | Sí, y más cuando va ligado a una cuenta. Por eso la versión mínima no la liga a nada. |
| ¿Sale del teléfono? | Sí, en dos puntos: la **dirección en palabras** se pide a un servicio de Google (recibe las coordenadas) y **Maps** abre con el destino. Hay que decirlo en el aviso: es una transferencia a un tercero. |
| Riesgo si se guarda | Mis lugares con coordenadas dicen dónde vive y dónde estudia la persona. Si solo se guardan notas de texto, el riesgo baja mucho. |
| Riesgo de seguridad física | Una voz que dice la dirección en voz alta puede escucharla otra gente. Debe avisarse antes («voy a decir dónde estás»). |

## Versión mínima recomendada (cuando se decida)

- La ubicación se pide **solo cuando la persona lo ordena** («dónde estoy»); nunca de fondo.
- **No se guarda** la ubicación actual. Mis lugares guarda solo nombre y notas de texto, en el teléfono, por cuenta; coordenadas únicamente si la persona dice «guarda este lugar con mi ubicación».
- Cómo llegar no manda nada a nuestro servidor.
- Textos nuevos en el aviso de privacidad: qué se usa, para qué, que Google recibe las coordenadas para la dirección y el mapa, y cómo borrar los lugares.

## Decisión sobre implementarlo ya

**No se implementa todavía.** No cumple el criterio de «solo textos de privacidad y algún detalle»:
- necesita un panel nuevo, el permiso de ubicación en tiempo de ejecución, una tabla nueva en la base y un cambio de versión;
- y la transferencia de datos a Google debe quedar en el aviso de privacidad **antes** de publicar, lo que depende de la revisión de la persona abogada.

Siguiente paso cuando Pp lo apruebe: redactar los textos del aviso, y luego implementar la versión mínima.

## Pendiente por decidir

1. ¿Se guardan coordenadas en Mis lugares, o solo notas de texto?
2. ¿Se acepta que la dirección en palabras la dé un servicio de Google, o se prefiere dar solo las coordenadas y abrir Maps?
3. ¿Quién redacta los textos del aviso y quién los revisa?
