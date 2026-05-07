LevelRPG is a hybrid progression system.


Players organically build Practice by using Disciplines in the world.


Practice raises Potential, which determines how far a player may invest in each Discipline.


Players earn Essence globally through Bookmark Bounties, Advancements (including milestones), and other structured wins—not from undirected grinding.


Players spend Essence at The Index to raise Discipline Levels, limited by Potential.


Discipline Level grants stats and Discipline-specific Insight.


Insight unlocks Discipline Tree nodes.


Tree nodes unlock options, but node power improves through actual use as Node Mastery.


Players are meant to specialize, not master everything at once.


Reforging lets players begin again while preserving hard-earned knowledge.




Core Rule:
Practice fills Potential.
Potential grants permission.
Essence pays the cost.
Discipline Level is chosen.
Insight is spent.
Node Mastery is proven.
Reforging preserves knowledge while resetting commitment.






# LevelRPG Progression Design


## 1. Design Pillars
- Organic practice
- Deliberate build investment
- Server role specialization
- Long-term replay through Reforging
- Knowledge persists, builds change


## 2. Core Terms


- Archetype
- Discipline
- Practice
- Potential
- Essence
- Discipline Level
- Insight
- Discipline Tree
- Core
- Trait
- Technique
- Axiom
- Manifestation
- Node Mastery
- Bounty
- The Index
- Trial (future milestone / challenge concept; not a primary Essence source in first implementation)
- Bookmark
- Reforging






## 3. Progression Loop


Practice raises Potential.


Essence raises Discipline Level, limited by Potential.


Discipline Level grants stats and Discipline-specific Insight.


Insight unlocks Discipline Tree nodes.


Using unlocked nodes builds Node Mastery.


Reforging resets commitment while preserving knowledge.


In short:


Practice → Potential → Essence → Discipline Level → Insight → Unlock Node → Use Node → Node Mastery → Reforge




## 4. Character Level Cap
Total invested Discipline Levels across all Disciplines are capped at **50**. This total is the character level cap and the main anti-master-everything rule.

The intended endgame build should support roughly two heavily invested Disciplines, or one primary Discipline with several secondary investments. Players should not be able to fully invest in all seven Disciplines at once.




## 5. Archetypes
Starting stats, identity, early direction, not permanent prison.


## 6. Disciplines
List Valor, Finesse, Arcana, Delving, Forging, Artificing, Hearth.
Define each fantasy and mechanical role.


## 7. Practice


Practice is Discipline-specific progress earned by using a Discipline in the world.


Repeated actions build Practice, not Essence.


When Practice fills, the player’s Potential in that Discipline increases.


Practice should be earned through relevant activity, but should not directly grant stats, Insight, or Discipline Levels.


Practice exists to preserve the organic “learn by doing” side of LevelRPG.


## 8. Potential


Potential is earned by practicing a Discipline.


Potential represents how far the player is allowed to invest in that Discipline.


Potential is a cap, not a spendable currency.


Potential does not directly grant stats, Insight, or power.


Potential survives Reforging.


Example:


If a player has Finesse Potential 12, they may invest Finesse up to Discipline Level 12, but no higher until they practice Finesse more.


## 9. Essence


**Essence is canonical** and **global only**. There is no discipline-flavored Essence; one pool applies to all Discipline Level investment decisions.


Essence is a global spendable currency used to raise Discipline Levels at **The Index**.


Essence is not earned from every repeated action. Repeated action builds Practice and Potential.


Essence is awarded by:


- **Bookmark Bounties** — the primary structured objective system tied to the Bookmark.
- **Advancements and milestones** — including first-time clears, achievement-style beats, and other milestone rewards the design chooses to hook.


Optional world events (bosses, structure clears, special beats) may also award Essence when explicitly designed as rewards, but **Bounties** and **Advancements/milestones** are the default, documented sources—not generic repetition.


Essence should create a Dark Souls-like investment tension:


- The player carries Essence.
- The player risks Essence while adventuring.
- The player must return to **The Index** to commit Essence (by default).
- The player chooses which Disciplines deserve investment.


Essence should not replace Practice or Potential.


Practice grants permission.
Essence pays the cost.




## 10. Discipline Level


Discipline Level is deliberate build investment.


Players spend Essence to raise Discipline Levels—**by default at The Index** (see §14); optional config may allow book-based investment.


Discipline Level cannot exceed Potential.


The **sum of invested Discipline Levels** across all Disciplines is capped (see §4).


Discipline Level grants stats and may grant or progress Discipline-specific Insight.


Discipline Level is the player’s build commitment.


Example:


A player may have Finesse Potential 18, but only Finesse Discipline Level 10. This means they have practiced enough to invest further, but have not yet committed the Essence.






## 11. Insight


Insight is the currency used to unlock Discipline Tree nodes.


Insight is earned through commitment to a Discipline, usually by raising that Discipline’s Level.


Insight should eventually be tracked per Discipline:


- Valor Insight
- Finesse Insight
- Arcana Insight
- Delving Insight
- Forging Insight
- Artificing Insight
- Hearth Insight


Insight is spent only in its matching Discipline Tree.


Most nodes should cost 1 Insight.


Depth, prerequisites, Discipline Level, Potential, and node requirements should provide most gating instead of large Insight costs.


## 12. Bounties and Advancements


**Bounties** and **Advancements** are structured objectives that give Minecraft play clear goals without replacing the sandbox.


They are the primary sources of **Essence**. **Commissions** (as a top-level design term) are **replaced or demoted** in favor of Bounties; any older “commission” idea should fold into the Bounty model or be treated as legacy vocabulary, not a parallel system.


### Bounties


Bounties are the **primary** repeatable structured objective system.


They are issued and framed through the **Bookmark**: the Bookmark is the quest-giving and guidance interface and wants the player to gather Essence for it. Bounties give the player concrete tasks that reward Essence when completed or turned in (exact flow TBD).


Bounties should support all Disciplines, including non-combat roles such as Forging, Artificing, Hearth, and Delving.


Examples:


- Recover rare resources from dangerous depths.
- Prepare meals that support multiple players.
- Repair or forge valuable equipment.
- Land weak-spot hits in combat.
- Explore or clear dangerous locations.


### Bookmark Offer Growth (Bounty Offer Spreads)


**Presentation.** Bounty offers are shown as **two-page spreads** in the book UI. Each spread presents **two** Bounty options side by side. The player **browses** by **flipping** through spreads.


**Browsing before commitment.** When the Bookmark presents a Bounty offer set, the player may **flip through all currently available Bounty spreads** before choosing. At **tier 1**, that means browsing **2** offers on **1** spread; at **tier 2**, **4** offers across **2** spreads; at **tier 3 and later**, **6** offers across **3** spreads. Nothing forces a pick from the first spread seen—the full current set is available for **deliberation**.


**Claiming commits the set.** **Claiming** one Bounty **commits** the offer set: **every** other **unclaimed** Bounty in that **same** offering **dissolves**—across **all** spreads in the set, not only the spread where the claim happened. The chosen Bounty becomes the player’s **active Solo Bounty**. The dissolved offers are **gone** from browse; they do **not** remain flippable. The **active Solo Bounty** stays **visible** in the Bounty section until it is **completed** or **abandoned** (see §19 for abandon / unclaim rules).

In short: when the Bookmark presents a Bounty offer set, the player may flip through all currently available Bounty spreads before choosing. Claiming one Bounty commits the offer set: every unclaimed Bounty across the current set dissolves, and the chosen Bounty becomes the player’s active Solo Bounty until completed or abandoned. The player is choosing **one path** from several possible **inscriptions**, not collecting a menu of tasks.


**Ritual feeling.** **Browsing** is deliberation. **Claiming** is commitment. **Dissolving** the rest reinforces that the Bookmark offered **possible** paths, but only **one** path was **inscribed**.


**How many offers unlock.** The Bookmark’s **offer count grows** over early progression:


- **First** Bounty offering: **2** Bounties on **1** spread.
- **Second** Bounty offering: **4** Bounties on **2** spreads.
- **Third and later** Bounty offerings: **6** Bounties on **3** spreads (the **maximum** for this design).


**When growth advances.** The step from 2 → 4 → 6 options should advance after the player has **spent Essence** and **strengthened the Bookmark loop**—for example after committing Essence at **The Index** and related beats—not merely after **opening** the Bounty page. The fantasy: the Bookmark **grows stronger** as Essence is **brought into the system**, tying wider choice to its hunger for Essence and the Index return loop.


**Future spreads must stay legible as “not the end.”** **Future or locked** Bounty spreads should **never** be hidden so completely that the UI looks like a **single** static page. The player should always **sense** that more leaves may **awaken** later. Suggested behaviors: **faint page edges**, **quiet or sealed** leaf silhouettes, subtle page texture, or a **short line** when trying to flip **past** the current unlocked offer limit—so no one assumes flipping is impossible.


**Bookmark dialogue examples** (tone: see §13):


- **First offering:** “I can only offer these two, for now. Choose wisely.”
- **Second offering:** “My strength allows me four, this time. Bring me more Essence.”
- **Third offering:** “Ever more do I feel my power. Six is all I offer you. Time is of the Essence... heheh.”
- **Attempting to flip beyond the current unlocked offer limit:** “The remaining leaves are quiet. For now.”
- **Attempting to flip beyond the final six-offer set:** “No more. Even I must leave some paths unread.”


### Bounty Authorship and Repetition


**Content model.** Bounties should be **hand-authored**, not generic procedural fetch quests. They may be **multi-step**, may **repeat** with **controlled** variation, may grant a **first-completion bonus**, and may form **quest lines** or **chains**. When something repeats, **variation** should preserve **authored flavor** and intent—not collapse into anonymous “collect X item” filler.


### Advancements


Advancements and **milestones** (including achievement-style beats and one-time or rare clears) award Essence to mark meaningful progress. They complement Bounties by rewarding exploration of the full game, not only repeatable objectives.


### Trials (held for later)


**Trials** are **not** described as a primary Essence source for the first Essence implementation.


Trials remain a **future** milestone and challenge concept—possibly for cap breaks, Reforging, archetype evolution, major unlocks, or special rewards. Tiering and reward rules stay open until that phase.


Bounties provide repeatable rhythm.
Advancements anchor one-time and milestone beats.
Trials are reserved for memorable, high-stakes beats later—not required to ship the first Essence loop.




## 13. The Enchiridion and Bookmark


The Enchiridion is the player’s source of **knowledge**, **planning**, and **preview**: it can explain the loop, surface trees, and route attention. **By default, Discipline Level investment happens at The Index**, not from arbitrary UI—though optional config may allow book-based investment for convenience (see §14).


The Enchiridion’s final page is not merely blank; it is **absent** (ripped out / missing). That absence foreshadows **The Index**: the book records possibility, while the missing page points to where possibility is committed and transformed.


The Bookmark is the Enchiridion’s **quest-giving and guidance interface**. It **wants the player to gather Essence** and uses **Bounties** (and pointers to Advancements) to steer play without replacing sandbox freedom.


The Bookmark can alert the player when the book has meaningful progression guidance, for example:


- new or updated **Bounties**
- completed objectives or turn-ins
- unspent Essence (nudging a return to **The Index**)
- new Insight
- newly revealed Discipline Tree nodes
- Reforging or future milestone hooks


Clicking the Bookmark can open a short dialogue with the player and route them to the relevant book section.


The Bookmark should feel like a magical vessel or emissary of the book rather than a normal menu button.


**Voice and personality.** The Bookmark reads as **mysterious**, **hungry for Essence** (needling the player toward the Index loop without breaking tone), **theatrical**, and **occasionally playful**—as in the §12 dialogue examples. It is not a dry quest log; it is a presence that **wants** the system fed.


The Bookmark gives shape to the sandbox without removing player freedom.




## 14. The Index


**The Index** is the canonical name for the **commitment station** and is ultimately envisioned as a **mysterious, book-linked structure** centered on the Enchiridion’s missing final page.


### V1 Scope


V1 should stay intentionally narrow. A full multi-block implementation is **not required** for first pass.


In V1, The Index may be represented by a **single custom block** (Index Lectern, Pedestal, or similar). This block is the interactable **heart/seed** of the eventual larger Index structure.


By default, the original Index should **generate near world spawn**, preferably a few blocks beneath the surface, potentially inside a small hidden archive/library-like chamber or similarly mysterious underground pocket. Exact worldgen/structure details remain open implementation design.


In V1, treat the original Index as potentially **unique** in the world by default. Do **not** require crafting, rebuilding, relocation, destruction, or replacement rules yet.


**V1 functional scope** should support:


- spending **Essence** and raising **Discipline Levels**
- enforcing **Potential** as the per-Discipline cap
- enforcing the total invested Discipline Level cap of **50**


**Not required in V1:** multi-block validation, Reforging, Shared Bounties, full Insight management, complex rituals, crafting, relocation, and destroy/rebuild behavior.


### Long-Term Structure Vision


Long term, The Index may evolve into a **multi-block archive/ritual structure**: a central missing-page artifact, surrounding supports/anchors/bindings, and an interactable pedestal/lectern. The intended feeling is magical and mysterious, with a sense of wonder similar to Minecraft’s enchanting-table fantasy (without forcing that full complexity in v1).


Later progression may teach players how to repair, rebuild, relocate, or reproduce fuller Index structures. That is future progression, not first-pass functionality.


**By default, players should not spend Essence from anywhere.** Essence is committed at **The Index**.


The **Enchiridion** may preview, explain, and route progression, but it is for knowledge and planning. **The Index** is for commitment and transformation.


**Optional config:** servers or modpacks may allow **book-based Discipline Level investment** anywhere for convenience; that is an exception to the default return-to-Index behavior.


This creates a return-to-safety loop:


Venture out → earn Practice, complete **Bounties** and **Advancements**, carry Essence → return to **The Index** → commit growth.






## 15. Discipline Trees
Node types:


Core
The central identity node of a Discipline or branch. Often establishes the basic mechanic or theme.


Trait
A passive or conditional modifier. Usually a stepping stone or small build-shaping improvement.


Technique
An active or triggered ability. Adds an action, input, combat option, or usable mechanic.


Axiom
A build-defining rule with a trade-off. Changes how a Discipline behaves by accepting a principle, limitation, or bargain.


Manifestation
The ultimate expression of a branch or Discipline. Unlocks a major feature, transformation, or advanced mechanic.


Additional Node Attributes:
Node Visibility
- Hidden: not shown yet.
- Revealed: visible but not unlockable or not yet purchased.
- Unlockable: requirements met and enough Insight available.


Node Unlock State
- Locked: not owned.
- Unlocked: owned and active unless disabled.


Node Behavior Flags
- Toggleable: player can enable/disable the node.
- Masterable: node gains Mastery through use.




## 16. Node Mastery
Nodes improve through use.
Unlocking gives baseline.
Using proves mastery.


## 17. Reforging


Reforging is the long-term rebuild system.


It resets or refunds build commitment while preserving hard-earned knowledge.


Reforging may preserve:


- Potential
- Node Mastery
- discovered nodes
- archetype history
- future Trial or challenge records (when implemented)


Reforging may reset or refund:


- Discipline Levels
- spent Essence investment
- Insight allocation
- active tree choices


Reforging is thematically tied to Forging.


At its highest form, Forging does not only shape tools. It reshapes the self.


Reforging may require **The Index** (or a dedicated ritual tied to it), rare materials, a Forging Axiom, or a Forging Manifestation.




## 18. Backend Refactor Requirements


Rename Skills to Disciplines in UI/commands.
Separate Potential from Discipline Level.
Separate Insight from generic Skill Points/Specialization Points.
Prepare for per-Discipline Insight.
Rename node types:
- Keystone → Axiom
- Mastery → Manifestation
Add Node Mastery as a separate progression track.
Keep compatibility aliases where needed during migration.
- Add **Essence** as the **global only** spendable currency for Discipline Level investment (no discipline-flavored Essence).
- Separate Practice from Potential.
- Practice should fill Potential progress.
- Potential should cap Discipline Level investment.
- Discipline Level should be bought with Essence (at **The Index** by default).
- Discipline Level should grant or support per-Discipline Insight.
- Add support for per-Discipline Insight pools.
- Add support for **Bookmark Bounties** as structured Essence sources.
- Add support for **Advancement / milestone** Essence rewards.
- Add support for **The Index** as the station where Essence is spent and Discipline Levels are raised (with optional config for book-based investment).
- Add support for Bookmark-driven guidance in the Enchiridion.
- Keep **Trials** as **future** milestone/challenge support—not a first-pass Essence source.


## 19. Open Questions
Potential pacing.
Insight pacing.
Per-discipline Insight.
Reforging costs/requirements.
Essence pacing.
Bounty generation, refresh rules, and balancing.
Solo Bounty abandon / unclaim rules (forfeit, whether a new offer set appears immediately, penalties, etc.).
Trial tiering and reward rules (future).
Death and Essence recovery/loss rules.
Final **The Index** structure layout and block composition (single-block seed vs full multi-block evolution path).
Whether The Index is unique by default.
Future rebuild / relocation / reproduction rules for The Index.
Final near-spawn generation details for the original Index.
Whether/when to upgrade from a single Index block to a full multi-block structure.
Final mechanics, block behavior, and crafting/relocation rules for **The Index**.
Whether book-based Discipline Level investment should be allowed by config (default off).
What Reforging resets.
What Reforging preserves.
