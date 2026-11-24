# Variables: Objetivos de misiones

> Archivo generado automáticamente desde el registro del plugin (`core/text/Variables.java`). No lo edite: se sobrescribe al arrancar.

**Dónde se usan:** settings/quests.yml: objective  
**Sintaxis:** el valor completo, `tipo:clave[:FILTRO]`

| Variable | Descripción | Ejemplo | Válida en |
|---|---|---|---|
| `stat:<tipo>` | Sube una estadística (ver tipos de /lpa top). Cuenta desde que empieza la misión | `objective: "stat:mob_kills"` | settings/quests.yml: objective |
| `event:break[:MATERIAL]` | Romper bloques (todos o de un material) | `objective: "event:break:DIAMOND_ORE"` | settings/quests.yml: objective |
| `event:place[:MATERIAL]` | Colocar bloques | `objective: "event:place"` | settings/quests.yml: objective |
| `event:kill[:ENTIDAD]` | Matar criaturas o jugadores (ENTITY_TYPE) | `objective: "event:kill:ZOMBIE"` | settings/quests.yml: objective |
| `event:craft[:MATERIAL]` | Fabricar objetos (cuenta la cantidad del resultado) | `objective: "event:craft:BREAD"` | settings/quests.yml: objective |
| `event:consume[:MATERIAL]` | Comer o beber | `objective: "event:consume:GOLDEN_APPLE"` | settings/quests.yml: objective |
| `event:fish` | Pescar peces | `objective: "event:fish"` | settings/quests.yml: objective |
| `event:enchant` | Encantar en la mesa de encantamientos | `objective: "event:enchant"` | settings/quests.yml: objective |
| `event:chat` | Enviar mensajes al chat | `objective: "event:chat"` | settings/quests.yml: objective |

Un objetivo inválido (o una estadística desconocida) se avisa en consola y esa misión se omite.
