# Variables: Quest objectives

> File generated automatically from the plugin registry (`core/text/Variables.java`). Do not edit it: it is overwritten on startup.

**Used in:** settings/quests.yml: objective  
**Syntax:** the whole value, `kind:key[:FILTER]`

| Variable | Description | Example | Valid in |
|---|---|---|---|
| `stat:<tipo>` | Raise a statistic (see the /lpa top types). Counts from when the quest starts | `objective: "stat:mob_kills"` | settings/quests.yml: objective |
| `event:break[:MATERIAL]` | Break blocks (any or one material) | `objective: "event:break:DIAMOND_ORE"` | settings/quests.yml: objective |
| `event:place[:MATERIAL]` | Place blocks | `objective: "event:place"` | settings/quests.yml: objective |
| `event:kill[:ENTIDAD]` | Kill mobs or players (ENTITY_TYPE) | `objective: "event:kill:ZOMBIE"` | settings/quests.yml: objective |
| `event:craft[:MATERIAL]` | Craft items (counts the result amount) | `objective: "event:craft:BREAD"` | settings/quests.yml: objective |
| `event:consume[:MATERIAL]` | Eat or drink | `objective: "event:consume:GOLDEN_APPLE"` | settings/quests.yml: objective |
| `event:fish` | Catch fish | `objective: "event:fish"` | settings/quests.yml: objective |
| `event:enchant` | Enchant at an enchanting table | `objective: "event:enchant"` | settings/quests.yml: objective |
| `event:chat` | Send chat messages | `objective: "event:chat"` | settings/quests.yml: objective |

An invalid objective (or an unknown statistic) is reported in the console and that quest is skipped.
