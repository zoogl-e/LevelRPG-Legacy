# LevelRPG Legacy Rewrite Audit

This audit reviews the current `LevelRPG-Legacy` codebase and identifies what should be preserved for a rewrite toward recipe-gated progression, and what should be rewritten because it is tightly coupled to the current item-use gate model.

Scope:
- Preserve useful scaffolding.
- Isolate or remove assumptions tied to item-use gating.
- Prepare for new skills, archetypes, and recipe ownership.
- Do not implement the new system yet.

## Keep Mostly Intact

These classes already provide reusable infrastructure and should survive with only targeted schema or API adjustments.

### Bootstrap and config

- `src/main/java/net/zoogle/levelrpg/LevelRPG.java`
  - Good mod bootstrap and event registration entrypoint.
  - Keep as the composition root, but swap legacy event registrations as the rewrite lands.
- `src/main/java/net/zoogle/levelrpg/Config.java`
  - Safe to preserve.
  - Keep the common config pattern and cached values.
  - Review individual options later and remove client/debug toggles that no longer fit.

### Data loader / registry patterns

- `src/main/java/net/zoogle/levelrpg/data/SkillDefinition.java`
- `src/main/java/net/zoogle/levelrpg/data/SkillLoader.java`
- `src/main/java/net/zoogle/levelrpg/data/SkillRegistry.java`
- `src/main/java/net/zoogle/levelrpg/data/SkillTreeDefinition.java`
- `src/main/java/net/zoogle/levelrpg/data/SkillTreeLoader.java`
- `src/main/java/net/zoogle/levelrpg/data/SkillTreeRegistry.java`
- `src/main/java/net/zoogle/levelrpg/data/XpCurveDefinition.java`
- `src/main/java/net/zoogle/levelrpg/data/XpCurves.java`
- `src/main/java/net/zoogle/levelrpg/data/DataEvents.java`
  - These are the strongest reusable part of the codebase.
  - Keep the datapack reload listener pattern and registry structure.
  - Extend it for new data domains:
    - `recipe_progression`
    - `recipe_ownership`
    - `archetypes`
    - `skill_groups` or similar
  - `GateRules` should not be preserved as-is, but the loader shape is worth reusing for new recipe-gate datasets.

### Profile persistence and sync concepts

- `src/main/java/net/zoogle/levelrpg/profile/LevelProfile.java`
  - Keep the core idea: server-authoritative profile persisted in player NBT and synced to the client.
  - Rewrite the schema, not the persistence concept.
  - Good candidates to preserve:
    - `get/save/copy`
    - profile serialization pattern
    - per-player authoritative state model
  - Planned schema changes:
    - add recipe ownership / unlock state
    - add archetype state
    - possibly split tree progression from generic skill progression
  - Current methods that assume legacy XP flow should be refactored rather than copied blindly:
    - `addSkillXp`
    - player level derived from summed skill levels
    - direct tree point spending tied to current skill-level math

- `src/main/java/net/zoogle/levelrpg/net/Network.java`
- `src/main/java/net/zoogle/levelrpg/net/payload/SyncLevelProfilePayload.java`
- `src/main/java/net/zoogle/levelrpg/net/payload/SyncLevelDeltaPayload.java`
- `src/main/java/net/zoogle/levelrpg/client/data/ClientProfileCache.java`
- `src/main/java/net/zoogle/levelrpg/events/ProfileEvents.java`
  - Preserve the sync model: login/respawn/dimension-change snapshot sync and lightweight client cache.
  - Rewrite payload contents to support recipe ownership, archetypes, and any revised progression state.
  - Keep the server-authoritative synchronization pattern.

### Utility and command scaffolding

- `src/main/java/net/zoogle/levelrpg/util/IdUtil.java`
  - Keep if still useful for data identifiers and command resolution.
- `src/main/java/net/zoogle/levelrpg/command/LevelCommands.java`
  - Keep the command registration pattern.
  - Rewrite the command surface and internals around new profile concepts.
  - Existing admin/debug commands are still useful as scaffolding for future `/levelrpg sync`, `/levelrpg grantrecipe`, `/levelrpg setskill`, `/levelrpg setarchetype`, etc.

### Optional UI scaffold

- `src/main/java/net/zoogle/levelrpg/client/Keybinds.java`
- `src/main/java/net/zoogle/levelrpg/client/ui/SkillTreeScreen.java`
- `src/main/java/net/zoogle/levelrpg/client/ui/LevelBookScreen.java`
- `src/main/java/net/zoogle/levelrpg/client/ui/BlurUtil.java`
- `src/main/java/net/zoogle/levelrpg/client/gecko/*`
- `src/main/java/net/zoogle/levelrpg/client/model/*`
- `src/main/resources/assets/levelrpg/*`
  - Preserve only if the rewrite still wants a book-driven UI shell.
  - Treat this as presentation scaffolding, not gameplay logic.
  - The current screens are placeholders and should not drive progression design.

## Rewrite Targets

These classes are tightly coupled to the current item-use gate system or to legacy activity-based XP awards.

### Hard rewrite: old gate enforcement system

Mark all of the following as rewrite targets.

- `src/main/java/net/zoogle/levelrpg/data/GateRules.java`
  - Entire dataset is based on item -> required skill level.
  - Replace with recipe ownership / recipe gate data.

- `src/main/java/net/zoogle/levelrpg/events/GateEvents.java`
  - Central server enforcement for blocked item use, crafting, block interaction, attack, equip, and periodic cleanup.
  - Highly coupled to the legacy design.

- `src/main/java/net/zoogle/levelrpg/util/ItemGateUtil.java`
  - Helper layer entirely built around item gate lookups and level checks.

- `src/main/java/net/zoogle/levelrpg/client/InventoryClickGuards.java`
  - Client prediction suppression specifically for illegal equip paths.

- `src/main/java/net/zoogle/levelrpg/mixin/AbstractContainerMenuMixin.java`
- `src/main/java/net/zoogle/levelrpg/mixin/client/SlotMixin.java`
- `src/main/java/net/zoogle/levelrpg/mixin/client/ArmorItemUseMixin.java`
- `src/main/java/net/zoogle/levelrpg/mixin/client/ShieldItemUseMixin.java`
- `src/main/java/net/zoogle/levelrpg/mixin/client/LocalPlayerMixin.java`
- `src/main/resources/levelrpg.mixins.json`
  - These exist to patch edge cases in item-use or equip denial.
  - They should be removed or replaced with a smaller, recipe-focused interception layer only if the new design truly needs one.

### Hard rewrite: old activity award system

Mark all of the following as rewrite targets.

- `src/main/java/net/zoogle/levelrpg/data/ActivityRules.java`
  - Current rule types are `break_block`, `kill_entity`, `craft_item`, and `smelt_item`.
  - This is legacy progression logic, not recipe ownership.

- `src/main/java/net/zoogle/levelrpg/events/ActivityEvents.java`
  - Directly awards XP from gameplay activities using `ActivityRules`.
  - Replace with a progression service that can evaluate:
    - recipe discovery
    - recipe ownership
    - recipe execution
    - archetype or skill-specific unlock conditions

### Partial rewrite: profile semantics

- `src/main/java/net/zoogle/levelrpg/profile/LevelProfile.java`
  - Keep the persistence/sync role.
  - Rewrite the data model away from assumptions like:
    - player level = sum of skill levels / divider
    - every progression change is an XP delta on a skill
    - tree unlocks are funded only by generic unspent skill points
  - Add a versioned migration path rather than mutating old fields ad hoc.

- `src/main/java/net/zoogle/levelrpg/net/payload/SyncLevelProfilePayload.java`
- `src/main/java/net/zoogle/levelrpg/net/payload/SyncLevelDeltaPayload.java`
- `src/main/java/net/zoogle/levelrpg/client/data/ClientProfileCache.java`
  - These should evolve with the new profile schema.
  - The transport pattern is good; the payload model is legacy.

- `src/main/java/net/zoogle/levelrpg/command/LevelCommands.java`
  - Keep command registration structure.
  - Rewrite command semantics to manage recipe ownership, archetypes, and new progression data.

## Rewrite Direction

The rewrite should introduce a clean separation between reusable framework code and progression rules.

Suggested target modules:

- `profile`
  - persistent player state
  - recipe ownership
  - archetype selection / progression
  - skill progression if still retained
- `data`
  - skills
  - archetypes
  - recipe gate definitions
  - recipe reward / unlock definitions
  - progression curves
- `progression`
  - server-authoritative service layer
  - no direct event logic embedded in profile objects
- `integration.events`
  - thin event listeners that translate NeoForge events into progression service calls
- `net`
  - sync state snapshots / targeted deltas
- `client`
  - passive rendering of synced state
  - no client-side authority for gating decisions

## Specific Legacy Assumptions To Remove

- Item usability is the primary gate.
- Equip prevention must be enforced in many UI and mixin interception points.
- Crafting is denied by item result level requirements.
- Progression is primarily granted by broad activity events.
- Player level is computed only from summed skill levels.
- Tree unlocks depend on one generic pool of unspent skill points.

## Recommended Rewrite Sequence

1. Preserve bootstrap, config, registries, profile persistence, and sync plumbing.
2. Freeze legacy systems behind clear boundaries:
   - `GateEvents`
   - `ActivityEvents`
   - `GateRules`
   - `ActivityRules`
   - item-gate mixins and client guards
3. Introduce new data definitions for:
   - recipe ownership
   - recipe unlock conditions
   - archetypes
   - revised skill metadata if needed
4. Refactor `LevelProfile` into a broader progression profile with explicit schema versioning and migration.
5. Replace direct event-to-XP logic with a dedicated progression service.
6. Rebuild commands and UI around the new profile model.
7. Remove obsolete gate mixins and item-use enforcement once the recipe-gated system is live.

## Short Decision Summary

Safe to keep mostly intact:
- bootstrap
- config
- datapack loader / registry patterns
- persistence and sync concepts
- lightweight client cache concept
- command registration scaffold

Rewrite targets:
- old gate enforcement
- old activity award logic
- item-gate utility helpers
- equip/use denial mixins and client guards
- profile semantics that assume XP-only skill leveling and item-use gating
