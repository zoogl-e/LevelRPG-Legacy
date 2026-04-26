# LevelRPG — Skill Design Document

> Last updated: April 2026
> This document captures the agreed design philosophy and identity of each skill.
> It is the source of truth for skill decisions and should be updated as the design evolves.

---

## The Seven Skills

### Valor
**Combat through power and aggression.**

Two internal branches:
- **Offense** — damage output, critical hits, weapon mastery, combat techniques, weapon-specific specialization
- **Grit** — max health scaling (via attribute modifier), damage reduction, natural regeneration rate, knockback resistance, "second wind" mechanic when near death

Absorbs the former Vitality skill. The warrior who survives enough combat doesn't just hit harder — their body changes. Vitality as a parallel skill created redundancy; as Valor's defensive branch it becomes a coherent character arc. XP from killing enemies with weapons.

The glass cannon goes deep into Offense. The tank goes deep into Grit. The balanced warrior splits.

---

### Finesse
**Combat through mobility and positioning.**

Wins fights by dictating engagement distance and terms rather than by raw output. Bows are the natural weapon expression of this identity — the mobile fighter can leverage range effectively — but archery is not the core identity. A high-Finesse player with a sword is still playing Finesse.

XP from intentional movement choices: long-range kills, surviving falls that should be lethal, stealth approaches. Not from passive traversal — the activity must involve a deliberate choice.

Unlocks include: speed boost, double jump, dodge, silent movement, reduced fall damage, faster bow draw speed, extended bow range.

Distinct from Valor because it wins through positioning, not power. The two archetypes should feel like genuinely different ways to fight.

---

### Arcana
**Scholarly understanding of Minecraft's underlying magical forces.**

Not a damage-dealing class. A student of hidden forces. The player who chooses Arcana is curious about how the world works, not looking for a new way to hit things.

Internally organized around four inner schools drawn from vanilla Minecraft:
- **Animakinesis** — the magic of life force; golems, respawn anchors, XP as harvested soul currency
- **Alchemy** — transformation through combination; brewing, the transmutation of substances (adjacent to Hearth)
- **Imbuement** — inscription of power into objects; the enchanting system and its untranslated language (Arcana's home base)
- **Dimensional Entanglement** — displacement in space; ender pearls, portals, End mechanics

Arcana's depth comes from being a **multiplier across other skills**, not from standalone content. Each cross-skill Arcana ability taps into a different inner school, teaching players the magical framework through play rather than tooltips.

Example cross-skill ability — **Out of Body Experience** (Arcana + Artificing, Animakinesis school):
> You have learned to separate your soul from your body. You may exit your physical form and freely fly within 30 blocks of it, still capable of placing and breaking blocks. You cannot deal damage in this state. Your body remains stationary and vulnerable.

The Magick name was retired because it implied offensive spellcasting. Arcana implies hidden knowledge and study.

---

### Delving
**Mastery of the hidden and submerged.**

Two internal branches:
- **Underground** — caves, ore veins, buried structures, the deep earth, geological knowledge
- **Underwater** — ocean monuments, shipwrecks, aquatic resources, drowned ruins, pressure and current

Anywhere the world hides things beneath a surface is Delving territory. The skill covers both because both are about going somewhere most players won't, and finding what's there.

Early Delving value: extract more efficiently, survive hostile underground/underwater environments longer.
Late Delving value: **knowledge and access**. Knowing where rare veins and hidden structures are — things no mining automation will ever surface. As mods trivialize extraction, the skill's knowledge dimension becomes more valuable by comparison, not less.

Value scales with player courage and curiosity. The expert Delver finds things in places others won't go.

---

### Forging
**Blacksmith identity with social weight.**

Weapons and armor made with intentionality. Signatures, special properties, craftsmanship that cannot be replicated through standard crafting or enchanting. The master Forger is a community asset in multiplayer — other players seek them out.

Activity loop must be deliberate and distinct: anvil, smithing table, and potentially custom workflows. Not general crafting. The Forger makes fewer things than a casual player but each one is more intentional.

Progression gates: apprentice Forgers make competent weapons. Journeyman Forgers can sign their work. Master Forgers imbue items with permanent passive properties unavailable elsewhere.

Output quality over output quantity. One of the most socially powerful skills in a multiplayer context.

---

### Artificing
**The art of making things that persist in the world.**

Two internal branches:
- **Architect** — aesthetic, structural, decorative; building beautiful things that are meant to be seen and inhabited
- **Mechanist** — functional, automated, contraptions; building things that work by themselves and free the player from repetitive labor

Same core action (placing blocks, interacting with built systems), different intent. Both branches are legitimate specializations of one identity rather than separate skills.

Vanilla-grounded first. Mod integrations (Create and others) enrich the Mechanist branch without being prerequisites. XP comes from placing blocks and interacting with built systems — not from general crafting, which belongs to Forging and Hearth.

Covers both the player who loves making contraptions and the player who loves building something beautiful. The goal is to ease the gap and improve the building experience.

---

### Hearth
**Food, sustenance, alchemy, potions, brewing, and their intersections.**

Mundane alchemy — the transformation of ingredients into something with different properties through heat, time, and combination. Cooking is Alchemy without the mystical component; they share a school.

At deep cross-skill levels (Hearth + Arcana, Alchemy school), food takes on alchemical properties that neither skill alone can produce. Buffs that stack unusually. Effects vanilla brewing cannot achieve.

The **caretaker skill**. The most powerful combat players are quietly dependent on the Hearth specialist. This skill earns its slot not through personal power but through being the person everyone else needs. In a multiplayer world, the best food, the best potions, and the best buffs all come from one person.

Players who choose Hearth have a specific character identity in mind. Design for them.

---

## Core Philosophies

**Seven, not eight.**
Asymmetric by design. A seven-pointed identity reads as intentional, not as an octagon with a missing piece. The number was earned by eliminating redundancy rather than padded to fill a shape.

**The 3 + 4 split.**
Fantastical archetypes (Valor, Finesse, Arcana) and craft professions (Delving, Forging, Artificing, Hearth) are two different flavors of progression. Fantastical skills offer a character identity fantasy — the player has an image of who their character *is*. Craft skills offer a mastery and social fantasy — the player has an image of what their character *does* and who depends on them. Most players will have a primary cluster and supporting investment in the other, creating natural cross-skill stories.

**A skill earns its slot through its activity loop.**
If two skills share the same player behavior, one will become invisible. The activity that levels a skill must require an intentional choice, not passive background accumulation. Players level skills by deciding to do something, not by existing.

**Arcana is a multiplier, not a standalone.**
Its content lives in the spaces between skills. Cross-skill Arcana abilities should be: transformative (they let you do something categorically impossible without them), thematically coherent (the combination tells a story), and carry natural risk/reward rather than artificial cooldowns. The four inner schools are the map for where those cross-skill synergies live.

**Valor absorbs Vitality.**
The Grit branch earned through combat mastery is more narratively coherent than a parallel skill leveled identically. "I invested deeply in combat and my body changed" is one story. Two separate combat skills that players level in tandem is a UI chore.

**Delving's value shifts as players progress.**
Mods that trivialize block extraction do not undermine Delving — they make the knowledge dimension more valuable. The late-game Delver isn't faster than an automated system. They know what's there.

**The radar chart should tell a story.**
Each of the seven spokes represents a genuinely different kind of player. A player's chart should be readable as a character description. Two spokes that always mirror each other indicate one isn't earning its place.

**Vanilla-first, mod-enriched.**
All seven skills have a vanilla activity loop and a vanilla unlock path. Mod integrations deepen specific branches without being prerequisites. The system travels to future modpacks intact.

**Social roles are legitimate progression.**
Not every skill needs to make the individual more powerful in combat. Hearth and Forging earn their depth through being needed by others. Multiplayer creates the context where this matters most.

**Hidden depth over declared complexity.**
The skill tree should be bigger than it first appears. Players who invest deeply should encounter things that make them think: *I didn't know you could do that.* The system rewards curiosity alongside commitment. Abilities at deep cross-skill intersections should feel like discoveries, not like following a checklist.

---

## Appendix: Arcana's Inner Schools

| School | Domain | Vanilla Anchors | Adjacent Skill |
|---|---|---|---|
| Animakinesis | Life force, animation, soul | Golems, respawn anchors, XP orbs | Valor (Grit), Finesse |
| Alchemy | Transformation through combination | Brewing stand, potions, furnace | Hearth |
| Imbuement | Inscription of power into objects | Enchanting table, anvil, Standard Galactic Alphabet | Forging |
| Dimensional Entanglement | Displacement in space | Ender pearls, nether portals, End gateway | Delving (Underwater) |

Cross-skill Arcana abilities should draw from the school most thematically aligned with the other skill, not arbitrarily from any school.
