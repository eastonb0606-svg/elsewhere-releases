# kingdom_duelist API (mod id `elsewhere_duelist`, package `dev.elsewhere.duelist`)

**Version 0.2.1 (2026-11-12; 0.2.0 was the breaking change vs 0.1.0; 0.2.1 only removes the per-tick health check, see Health cap).** The shared "Human Duelist kit" is gone (RIVALS-DECISIONS section 11: every boss feels unique). What is left is plumbing: a boss entity, a wind-up state machine, telegraph looks, a temporary-block ledger, projectiles, ground patches, companion leash, phases, a last-stand hook, duel mode and the `boss_identity` loader plus sameness audit. **Nothing a boss does is on by default and no fighting number is shared**: a behaviour exists only if the profile is given its `Behaviours` record, which carries every number of that behaviour. The module has no dependency on `kingdom`.

## 0. Migrating from 0.1.0
| 0.1.0 | 0.2.0 |
|---|---|
| `K` table (`kit.Kit` numbers: strafe, crit, shield, bow...) | Gone. `Kit` holds only the rails (telegraph minimums, cap maximums, duel yield 10 percent, magic factor). Put each number in the boss's own `Behaviours.*` record |
| Lord/ruler kit on by default; `.without(Move...)` | Opt in: `.strafe(..) .sprintCrit(..) .shield(..) .swap(..) .bow(..) .crossbow(..) .eat(..) .drink(..) .pearl(..) .tempBlocks(..)`. Not called = does not happen. `without()` is removed |
| `DuelistProfile.builder(id).ruler().hp().swing(raw, interval)` with defaults | Required, no defaults: `hp, walkSpeed, swing(raw, interval, windup), reach, melee(stack), phases(...), caps(minor, signature), telegraph(style)`. `build()` throws naming what is missing |
| One telegraph look (`Cue.SWING/HEAVY/CROUCH/FLASH`) | `TelegraphStyle` (particle, colour, sound, pitch, shape HALO/RING/SPIRAL/COLUMN) per boss; `Cue(style, crouch, raised)` via `style.cue(..)`. Build from a card: `BossIdentity.style(shape)` |
| `DamageCaps` fixed 18/40/38 | `caps(minor, signature)` per boss, up to the rails 18 and 40 (ruler) / 38 (Lord) |
| Totem of Undying (`hasTotem()`, 30-tick chant, `Move.TOTEM`) | **Removed.** `profile.lastStand(hook[, maxUses])` or `boss.phase.onLethalDamage(hook[, maxUses])`; see section 2.1 |
| `ruler()` implied Totem and crit cooldown 60 | `ruler()` now only means: no duel mode, signature cap rail 40 |
| Move toggles per phase (requested) | `boss.setEnabled(Move, boolean)` and `boss.on(Move)` |
| `DuelistProjectile.throwAt` always MINOR cap | `throwAt(.., DamageCaps kind)` overload |
| `HumanBoss.move(LivingEntity, double)` package-private | `protected`: override it for prowl, backpedal, wade |
| `Move` enum had `TOTEM` | Removed. `Move.generic()` marks the seven audited behaviours; `Move.id()` is the card name |
Behaviour numbers inside `Behaviours` records have no defaults, so copy the number you want from your card.

## 1. Use it

`build.gradle`: `implementation files('devlibs/elsewhere_duelist.jar')`.
Call `DuelistContent.init()` first in your own init (idempotent; registers `elsewhere:duel_block`, `elsewhere:duelist_projectile`, the two test duelists, the last-stand hook, the khopesh hook, the timer, patch and boss_identity services). Client: `DuelistClient.initClient()` (idempotent; renderer for every boss type registered so far) and `DuelistClient.registerRenderer(type)` for types registered later. The renderer already applies the 26.3 skin fix (`state.skin`), so a duelist shows `textures/entity/<profile.skin>.png`, never Steve; a subclass renderer that overrides `extractRenderState` must call `super`.

### Register a boss: opt in to two behaviours with your own numbers
```java
public final class HrafnholtJarl extends HumanBoss {
    static final BossIdentity CARD = BossIdentity.get("hrafnholt_jarl");           // from data/elsewhere/boss_identity/
    public static final DuelistProfile PROFILE = DuelistProfile.builder("hrafnholt_jarl")
        .hp(260).walkSpeed(0.27).swing(15F, 16, 10).reach(3.2).phases(0.66, 0.33).caps(18F, 38F)
        .melee(() -> new ItemStack(Items.IRON_AXE))
        // opt-in 1: shield, this boss's own numbers (nothing else is on)
        .shield(new Behaviours.Shield(6, 20, 40, 24, 100F, 120, null))
        // opt-in 2: sprint-jump crit with its own run-up, crouch, cooldown, stagger and multiplier
        .sprintCrit(new Behaviours.SprintCrit(4.0, 10, 100, 12, 1.6F))
        .shieldBreak(new Behaviours.ShieldBreak(80, 160))           // weapon property: its axe takes a player shield down
        .telegraph(TelegraphStyle.of("crit", 0xC03020, "minecraft:item.axe.scrape", TelegraphStyle.Shape.RING, 0.8F)) // or CARD.style(Shape.RING)
        .signature(HORN_BLAST)                                      // SignatureMove, see section 2
        .lastStand((boss, level, src, amt) -> {                     // own last stand, replaces the Totem
            boss.lastStand(40, 0.25F, true, () -> level.playSound(null, boss.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3F, 0.7F));
            return true;
        })
        .identity("hrafnholt_jarl").skin("hrafnholt_jarl").barColor(BossEvent.BossBarColor.RED)
        .build();
    public HrafnholtJarl(EntityType<? extends HumanBoss> t, Level l) { super(t, l, PROFILE); }
}
EntityType<HrafnholtJarl> TYPE = DuelistContent.registerBoss("hrafnholt_jarl", HrafnholtJarl::new, HrafnholtJarl.PROFILE);
```
`registerBoss` makes a player-sized `MobCategory.MISC` type (never the natural mob cap) with attributes from the profile. Lang: `entity.elsewhere.<path>`; the bar title is that name. Skin: `assets/elsewhere/textures/entity/<profile.skin>.png` (64x64, wide model). Armor is real vanilla armor: override `applyLoadout()`, `setItemSlot` for armor, then `super.applyLoadout()`. Subclass constructors keep the `(EntityType, Level)` shape.

### Bar and music (data only)
* Boss bar: `ServerBossEvent` titled `Name . Label` (label = the current wind-up). Add `"elsewhere:<path>": {icon, color, sub}` to your `boss_bars.json` for an icon and colour.
* Music: `"elsewhere:<path>": "elsewhere:boss_music.theme.<slot>"` in `boss_music.json`. `HumanBoss.phaseIndex()` is synced.

## 2. HumanBoss, DuelistProfile, behaviours (`dev.elsewhere.duelist.boss`)

`class HumanBoss extends Monster`; `HumanBoss(EntityType, Level, DuelistProfile)`. With no opt-in it paths to its target, winds up and swings at `swing(raw, interval, windup)`, and runs phases, signatures, last stand and duel mode: nothing else.

**Profile builder (all required unless marked):** `hp, walkSpeed, swing(raw, interval, windup), reach, melee, phases(double...), caps(minor, signature), telegraph(TelegraphStyle)`. Optional: `ruler()`, `thrown/ranged/food/potion(Supplier<ItemStack>)`, `signature(SignatureMove)` (repeatable), `barColor`, `duelMode(bool)`, `court(CourtBonus)`, `frenzy(FrenzyRage)` (`noHeal` turns eat/drink off), `skin`, `identity`, `lastStand(hook[, maxUses])`, `shieldBreak(ShieldBreak)`. `build()` also checks: telegraph rule for the swing and each signature, caps within the rails, signature raw within the signature cap, swing within the minor cap, `swap` needs `thrown` or `ranged`, `bow`/`crossbow` need `ranged` (and a matching item), `eat` needs `food`, `drink` needs `potion`.

**Opt-in behaviours** (`Behaviours`, all numbers explicit; ticks 1/20 s, raw before armor):
`Strafe(speedMult, flipMin, flipMax, spacingMin, spacingMax, maxStillTicks)` | `SprintCrit(runUp, crouchTicks, cooldown, staggerTicks, multiplier)` | `Shield(raiseDelay, holdMin, holdMax, lowerMin, arcDegrees, axeDisableTicks, Bash bash-or-null)` with `Bash(windup, cooldown, raw, knockback)` | `ShieldBreak(ticks, cooldown)` | `Swap(meleeRange, thrownRange, swapTicks, minGap, throwWindup, throwCooldown, thrownRaw)` | `Bow(draw, minGap, spreadBase, spreadFlatRange, spreadPerBlock, spreadCap, lead, raw)` | `Crossbow(load, holdMax, spread, minGap, raw)` | `Eat(heal, spreadTicks, HealRule)` | `Drink(heal, HealRule)` with `HealRule(below, clearRadius, maxUses, lockout)` | `Pearl(minDist, maxRange, landRadius, unreachableTicks, windup, cooldown, max, fallRaw)` | `TempBlocks(interval, lifetime, maxActive, bridgeMax, pillarMax)`.
`profile.has(Move)`, `profile.opted()`, `profile.genericIds()` (the card's "generic" names), `boss.on(Move)`, `boss.setEnabled(Move, bool)` (switch a behaviour off or back on per phase; it cannot turn on one that was not opted into).

### 2.1 Last stand (replaces the Totem)
`PhaseController.LethalHook.onLethal(boss, level, source, amount)` runs when a hit would kill the boss; return true to keep it alive. Use `boss.lastStand(lockTicks, healTo, forceLastPhase, after)` for the plumbing (1 HP, wind-up cancelled, invulnerable for `lockTicks`, then heals to `healTo` of max, optionally last phase, then `after`). Particles, sound, speech and summons are yours (that is what makes each last stand unique). Set it with `profile.lastStand(hook, maxUses)` or `boss.phase.onLethalDamage(hook, maxUses)`; after the uses are spent the next lethal hit kills. In duel mode the hook runs when a hit would cross the 10 percent line, before it yields; never while yielding. `boss.inLastStand()`, `boss.lastStandUses()`. There is no Totem item, no `hasTotem()`.

### Signature moves (`SignatureMove`)
Data plus one method: `id, label, weight(), windup(), cooldown(), raw()`, optional `minRange, maxRange, priority, cue, freeze, minPhase, canUse(boss, target), begin(boss, target)`, and `fire(boss, target)`. Quick form: `SignatureMove.of(id, label, Telegraph.Weight, windup, cooldown, raw, minRange, maxRange, (boss, target) -> ...)`. The boss picks among usable moves (range, cooldown, phase), winds up in its own telegraph style, then calls `fire` (re-check range there). By script: `boss.fireSignature(move, target)` or `boss.startTelegraph(id, label, ticks, weight, raw, cue, freeze, runnable)`.

### Telegraph (`Telegraph`, `TelegraphStyle`)
`Weight.ORDINARY | SIGNATURE | HEAVY`. Rule enforced at `build()`: raw 16 or more needs 8 ticks, signature 14, area or heavy 20 (`Telegraph.check` throws). Wind-ups are cancelled only by a stagger. A style is a vanilla particle, a colour (accent dust always drawn with it), a sound and pitch, and a shape. `TelegraphStyle.of(particleId, colour, soundId, shape, pitch)`, `TelegraphStyle.fromJson(obj, shape)` (the `telegraph` object of a card: particle, colour "#RRGGBB", sound id or `{event, pitch}`; nothing falls back), `BossIdentity.style(shape)`. Synced to the client: `boss.action()`, `actionTicks()`, `actionTotal()`, `armRaised()`.

### Damage and the caps
Every attack goes through `boss.hurtRaw(level, victim, raw, DamageCaps kind, DamageSource)`: court bonus and frenzy first, then the boss's own cap (`MINOR` = `profile.capMinor`; `SIGNATURE` = `capSignature`; `MAGIC` converts raw to armour-ignoring damage at x0.21, signature cap). Bonuses cannot push a hit past a cap. `strike(level, target, crit)` is the player-style melee hit. `boss.maxRaw`, `lastRaw` record what it dealt.

### Health cap (RIVALS-DECISIONS section 13)
No human kingdom boss may exceed **300 max health**, whatever stacks on it (succession, Risen +15 percent, court or ruler bonus, phases, the Test Meter multiplier). `HumanBossHealth.enforce(mob)` runs in the `HumanBoss` constructor, after loading from disk (`readAdditionalSaveData`, so a 2.0 world's 560 HP Jarl comes back at 300; health is only ever clamped down, never raised) and right after the duelist code changes a max-health modifier (court bonus). **There is no per-tick check (0.2.1):** whoever else adds or changes a max-health modifier on a human boss (succession, Risen, Test Meter, phases in your own code) must call `HumanBossHealth.enforce(boss)` right after; vanilla `setHealth` already clamps to max, so no overhealing once max is at most 300. It keeps a transient `elsewhere:human_hp_cap` modifier (ADD_MULTIPLIED_TOTAL, negative, scaled so the final value is exactly 300) and keeps current health at or below max. It removes itself when the boosts go. A human boss that is not a `HumanBoss` may call it itself. `HumanBossHealth.MAX` = 300.

### Phases, duel mode, state
* `boss.phase` (`PhaseController`): `onEnter(phaseIndex, Runnable)`, `onLethalDamage(hook, maxUses)`. A phase change resets cooldowns, plays a sting and calls `onPhaseChange(level, newPhase)`.
* Duel mode: `setDuel(true)`: at 10 percent HP the boss yields (horn, invulnerable, `DuelistEvents.YIELD`), then dies as a normal kill after 30 ticks so drops are normal.
* Override: `applyLoadout()`, `onTelegraphStart(Telegraph)`, `onPhaseChange`, `onDefeated(level, participants)` (roll drops: `DropRule`), `extraTick(level, target)` (every tick: reader, companion, signals), `move(target, dist)` (movement slot, now protected).
* State: `weapon()`, `shieldUp()`, `shieldDisabledTicks()`, `healUses()`, `pearlsUsed()`, `isStaggered()`, `isHealing()`, `idle()`, `participants(level)`, `stat(id)/bump(id)`, `ready(id)/setCooldown(id, t)/cooldownLeft(id)`, `home/setHome`, `arenaRadius` (48), `squadKey`, `passive` (never picks a target or action; for scenes and tests), `forceShield(ticks)`, `bashNow(target)`.
* `CourtBonus.perLiving(IntSupplier living, dmgPer, dmgMax, hpPer)`, `.withFlatDamage(d)`. `new FrenzyRage(maxBonus, permanentBelow, noRetreat, noHeal)`.

## 2.2 boss_identity loader and audit (`dev.elsewhere.duelist.identity`)
Copy the 15 card JSONs to `data/elsewhere/boss_identity/` in your module (the duelist jar also ships them). `BossIdentity.get("hrafnholt_jarl")` (key or id) and `BossIdentity.all()` are loaded at server start (`DuelistContent.init()` hooks it); fields: `slots, generic, signature, look (particle, colour, sound, pitch), last_stand, ...`; `card.style(shape)` builds the render style. `BossIdentityAudit.check(cards)` returns a list of violations (empty = pass), the same rules and verdicts as `_tools/boss_audit.py` (34 fixture sets agree): exactly the 15 files, schema, at most 2 shared slot tokens between any two bosses, each generic used by at most 3 bosses and pearl by 1, no Totem text, unique last stands, movement, signatures and telegraph looks, at most 1 Mirewick builder. A kingdom self-test calls `BossIdentityAudit.checkPresent(its own cards)` (limits only) and `BossIdentityAudit.checkProfile(card, profile)` per boss: it reports a profile that opts into a generic its card does not list (or the reverse), different signatures or a different look. `checkRaw(map)`, `checkDir(path)` work on files.

## 3. Temporary blocks (`dev.elsewhere.duelist.blocks.DuelBlocks`)
Block `elsewhere:duel_block` (soft: breaks in about 14 ticks with any tool, drops nothing, translucent). A boss that opted into `tempBlocks(TempBlocks(...))` builds bridges and pillars with it (its own interval, lifetime, caps). For your own use:
* `DuelBlocks.place(level, pos, ownerUuid, ownerEntity[, lifetime])`: air only, never replaces a block, never in a fluid or inside an entity, never leaves a player within 4 blocks without an open side. Lifetime 600 ticks, cracks from 500.
* `clearOwner(level, uuid)`, `clearAll(level)`, `count(level, uuid)`, `total(level)`, `canPlace(level, pos, ignoreEntity)`.
* A saved per-world ledger removes them on expiry, boss death or removal, chunk unload and load, server stop, and a block the ledger does not know removes itself on its next random tick (crash safe).
* `TempStructure.wall(level, boss, base, facing, width, height, cap, lifetime, cooldown)` (X6) builds a wall on the same ledger.

## 4. Shared tools (`dev.elsewhere.duelist.kit`)
* `DodgeStep.dodge(level, boss, fromVec, cooldown)` (X1): 8 i-frames, about 5 blocks, afterimage particles.
* `DuelistProjectile.throwAt(level, boss, target, stack, raw, speed)` (X2): carries an item, raw damage through the caps, sticks 40 ticks then fades, no block damage. `DuelistProjectile.splashAt(level, boss, target, bottleStack, new Splash(effects, radius, raw), speed)` (X3): bursts on impact, effects plus raw magic damage that shields do not stop and that never takes a victim below 1 HP.
* `GroundPatch.spawn(level, boss, center, radius, armTicks, lifetime, pulseEvery, raw, effects, particle)` (X5): a warning outline, then pulses. Particles only, never ignites, never edits a block.
* `ReachAttack.hit(level, boss, target, reach, minRange, raw, pierce, kind)` (X8): extra reach, half damage inside `minRange`, optional line pierce.
* `Companion(boss, factory, leash, respawnTicks, maxRespawns, barTitle)` (X4): bound ally with a leash, respawns and a sub-bar; call `tick(level)` from `extraTick`, `dismiss()` on death.
* `Timers.after(server, ticks, runnable)`.

## 5. Khopesh hook (`kit.ShieldHook`)
Every khopesh hit that lands on a raised shield hooks it down: 60 ticks, once per 120 ticks per attacker, once per 40 ticks per squad. **Tag your khopesh items `elsewhere:khopesh`** (`data/elsewhere/tags/item/khopesh.json`) and it happens automatically for any wielder (boss, soldier, FakePlayer). A hooked player gets an item cooldown on the shield (like an axe); a hooked HumanBoss calls `disableShield`. Manual: `ShieldHook.hook(level, attacker, squadKey, victim)`. Replace `ShieldHook.squadKeyOf` (default: the attacker's UUID, HumanBoss uses `squadKey`) so a whole squad shares one key, and `ShieldHook.isHook` to change what counts as a khopesh.

## 7. Adaptive framework (`dev.elsewhere.duelist.adapt`, X11 and X12)
For the Sultana and the Wazir. Rules are the spec's B1 to B4, exact.

`new TacticReader(boss)`; call `reader.tick(level, playerTarget)` from `extraTick`; call `reader.watch(guard)` for each guard; `reader.close()` on death.
* Loadout read (B1): `reader.classes()` is an `EnumSet<LoadoutClass>` (`RANGED, SHIELD, HEAVY, REACH, LIGHT, MOBILE, POTION_HEAVY`), recomputed every 200 ticks from `PlayerTrace` (shield raised in the last 10 s, bows, potions used, pearls thrown, effects, elytra...). `readDone(now)` is true after 200 ticks from first sight, immediately while `setMarked(true)` (a falcon has marked the player); the read is lost after 600 ticks without line of sight.
* Repetition tracker (B2): ring of 12 `Event(tactic, side, count, tick)`; `Tactic.JUMP_CRIT_OPENER, COMBO, RANGED_POKE, PEARL_IN, HIGH_GROUND, SHIELD_TURTLE, HEAL_RUN` (T1 to T7), `Side.of(reader, player)` = `FRONT, LEFT, RIGHT, REAR`. The 3rd same-tactic event (same side, same combo hit count) inside 800 ticks queues a `Counter(tactic, player, side, tick)`: **the first and second are free**; the tactic resets and is blocked 400 ticks; at most 2 pending (oldest dropped); 6 per fight then `satisfied()` is true and the reader stops. Take counters with `reader.pollCounter()` when idle and run your faction's counter (Mirage Step, Slowness bottle...). `reader.rollFeint(random)` is true for 20 percent of Mirage Steps (a fake: no gold flash). `record(tactic, side, count, now, player)` lets a faction feed its own events; `events()` lists the ring.
* Observations (`PlayerTrace`, init'd by `DuelistContent.init()`): per-player shield samples, last bow shot, pearl landings, heal-item use, sprint-jump take-off, hits. Only players a reader is watching are sampled.
* Guard signals (B3, `GuardSignals`): `new GuardSignals(leaderMob)`, `setGuards(list, hookGuard)`, `signal(level, Formation, Tell, target)` plays the tell (banner horn, blade, bell, flare) and the guards obey after 10 ticks (40 for a flare) once they have turned to face the leader; `tick(level)` every AI tick moves guards (a path update every 10 ticks). `Formation`: `SCREEN (80, cd 400)`, `RING (120)`, `OPEN (60)`, `WITHDRAW (until next)`, `HOOK (60, cd 160)`. `screenCooldownOverride = 240` while the Wazir lives. A guard that implements `Guardable.raiseShield(ticks)` gets the shield wall orders. The controller never damages anything; a guard's attacks are its own AI.
* Not in this module (yours): the counters themselves, the Mirage Step, flares' colours as items, the bad-trade refusal and terrain rules. Use `DodgeStep`, `DuelistProjectile.splashAt`, `GroundPatch` and `fireSignature` for them.

## 8. Events (`DuelistEvents`)
`HIT` (boss, victim, amount, kind, landed), `MOVE` (boss, moveId), `YIELD` (boss). Plain listener lists.


## 6. Melee crit and shield model (matches player rules)
* Crit: falling, not on ground/climbing/in water/sprinting, x1.5 for the vanilla crit; a boss's sprint-jump crit multiplier is its own (`SprintCrit`).
* Shield: the vanilla `BlocksAttacks` path (`HumanBoss.shieldStack(arc, raiseDelay)`), so blocking, the disable sound and an axe's disable work as for a player. Players' shields are disabled by a boss only if it opted into `ShieldBreak`.


## 9. Test helpers and self-test
`elsewhere:duelist_test` (strafe + sprint crit, ember spiral) and `elsewhere:duelist_test_ruler` (nothing opted in, violet ring), deliberately different. `TestDuelist.NEXT` sets the profile of the next one spawned (tests). `./gradlew runServer` runs the module self-test (port 25741; env `DUELIST_ONLY=<text>` filters); `./gradlew runClientGameTest` takes screenshots.
Notes: `boss.blocks.holding()` is true while the boss stands still to build; weapon swap has a 0.7-block hysteresis; `DuelistClient`/`DuelistContent` are idempotent.
