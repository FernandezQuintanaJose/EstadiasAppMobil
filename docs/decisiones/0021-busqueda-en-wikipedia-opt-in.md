# 0021. La búsqueda en Wikipedia y Wikidata es opt-in: empieza apagada y tiene su propio interruptor

> Aceptada · Dueño: Pp · 17 nov 2026 · Relacionada con la [0002](0002-ia-apagada-por-defecto.md)

## Contexto

Hasta ahora, cuando la app no entendía una frase y había internet, el texto de la frase salía del teléfono hacia Wikipedia o Wikidata (servicios de terceros) aunque la IA estuviera apagada, sin interruptor y sin que la persona lo decidiera (`docs/referencia/que-sale-del-telefono.md`). Eso contradecía el principio de la 0002: la persona decide qué sale de su teléfono.

## Decisión

1. **Nuevo ajuste `wikipediaEnabled`** en `DeviceSettings`, **apagado por defecto**, independiente del interruptor de la IA.
2. **Interruptor «Búsqueda en Wikipedia»** en Configuración, sección Privacidad. Al cambiarlo, el asistente dice por voz qué pasa.
3. **Con el ajuste apagado, ninguna frase sale hacia Wikipedia ni Wikidata**: `HomeViewModel` y `DashboardViewModel` no llaman a `WikipediaLookup` ni a `WikidataLookup`, y la app responde que no entendió la frase. Una petición explícita («busca en Wikipedia…») tampoco sale mientras esté apagado.
4. **Con el ajuste encendido** el comportamiento es el de antes: el texto de la frase sale directo del teléfono, sin pasar por el servidor ni por el filtro de privacidad. El aviso de privacidad (0005) debe mencionarlo.

## Lo que falta

- Probar en el teléfono que el ajuste arranca apagado, que se activa con TalkBack y que, apagado, no sale ninguna consulta. Este cambio no se compiló ni se probó todavía.

## Cuándo reconsiderarlo

Si la consulta pasa a hacerse a través del servidor con el filtro de privacidad, el interruptor podría fusionarse con el de la IA.
