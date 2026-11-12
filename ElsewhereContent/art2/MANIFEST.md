# art2 MANIFEST

## art lane 2 (2026-11-11, handed in ~13:40 CST): mob models, items, boss icons, kingdom skins
**State:** all 4 planned mob models + textures, the Lazy Gorger texture, Glimmer Mote + Clockwork Repeater items, 24 boss-bar icons and 6 Aldermark skins are DONE and staged. Models **compile** against their modules on the iMac (`~/mcbuild/lane-art/{verdigris_ironpeak,greenhaze_hush}`, `compileClientJava`, BUILD SUCCESSFUL, 0 warnings, with the registration patch below applied). **Nothing was seen in game** (no runClient). Textures were checked as PNG contact sheets at x16 (the 24 icons and the golem body at x8 to x10) and as front/back paper-doll previews.

Copy rules are unchanged (mirror `art2/<module>/` over the module, no `--delete`). Do **not** copy `art2/_tools/` or `art2/vanilla/` (vanilla 26.3 textures extracted for the generators: `raider.py`, `clockwork.py`, `barnacle.py`, `stalker.py`, `gorger.py`, `items2.py`, `bossicons.py`, `kingdom.py`).

### Renderer registration change (integrator; the only code edits outside new files)
**verdigris_ironpeak** `src/client/java/dev/elsewhere/verdigris_ironpeak/VerdigrisIronpeakClient.java`, in `initClient()`:
- delete these 3 lines (36, 37, 43 at 13:00):
  - `EntityRendererRegistry.register(VerdigrisEntities.CLOCKWORK_DROWNED, Shapes.Clockwork::new);`
  - `EntityRendererRegistry.register(VerdigrisEntities.BARNACLE_GOLEM, ctx -> new Shapes.Golem<BarnacleGolem>(ctx, "barnacle_golem", 0.9F, g -> g.attackAnim));`
  - `EntityRendererRegistry.register(IronpeakEntities.IRONCLAN_RAIDER, ctx -> new Shapes.Humanoid<>(ctx, "ironclan_raider", 1.4F));`
- add (e.g. after the CLAN_QUARTERMASTER line): `dev.elsewhere.verdigris_ironpeak.client.art2.Art2Mobs.register();`
- the `BarnacleGolem` import becomes unused (harmless). `Shapes.Clockwork` stays (now unused).

**greenhaze_hush** `src/client/java/dev/elsewhere/greenhaze_hush/GreenhazeHushClient.java`, in `initClient()`:
- delete the 2-line `EntityRendererRegistry.register(HushEntities.ECHO_STALKER, c -> new Simple<...EndermanModel...>(... "echo_stalker" ..., 1.25F));` (lines 133-134 at 13:00)
- add in its place: `dev.elsewhere.greenhaze_hush.art2.Art2Mobs.register();`

`Art2Mobs.register()` registers the model layers (`ModelLayerRegistry`) and the renderers. Scales and shadows are unchanged (Raider 1.4, Drowned 1.1, Golem 0.9, Stalker 1.25).

### Mob models (new classes, 26.x render-state style, subclass the vanilla rig + extra child cubes)
| Mob | Class (new file) | Extra parts | Animation |
|---|---|---|---|
| Ironclan Raider | `verdigris_ironpeak/.../client/art2/IronclanRaiderArt` (`Model extends HumanoidModel<HumanoidRenderState>`, `Renderer extends HumanoidMobRenderer`) | horned iron helm (hat layer) + 4 horn cubes, braided beard, fur collar, iron/fur pauldrons (8 cubes) | vanilla humanoid walk, arm bob, item pose and swing; beard sways idle and bounces with the stride |
| Clockwork Drowned | `.../art2/ClockworkDrownedArt` (`Model extends DrownedModel`, `Renderer extends AbstractZombieRenderer<Drowned, ZombieRenderState, DrownedModel>` = vanilla DrownedRenderer behaviour: trident throw pose, swim tilt, baby model + armor sets) | riveted boiler on the back (the mob's weak spot), chimney, wind-up key (4 cubes) | vanilla drowned anims; key turns constantly (faster when walking), boiler chugs. Vanilla seaweed outer layer left off |
| Barnacle Golem | `.../art2/BarnacleGolemArt` (`Model extends IronGolemModel`, `Renderer` = copy of `Shapes.Golem` with this model) | 14 small barnacle cubes on shoulders, back, head, arms, a leg | vanilla golem walk + arm slam (`attackAnim`) |
| Echo Stalker | `greenhaze_hush/.../art2/EchoStalkerArt` (`Model extends EndermanModel<EndermanRenderState>`, `Renderer extends MobRenderer`) | 2 warden-style tendril planes (zero-depth, cutout) | vanilla enderman walk; tendrils sway idle and with the stride; while `LISTENING > 0` they perk up and the jaw drops (`isCreepy`). One emissive layer (`echo_stalker_emissive.png`, `RenderTypes::eyes`, same cost as vanilla enderman eyes) |

Textures (all vanilla layout in the top part, so they also work on the old renderers): `ironclan_raider.png` 64x64 (hand-painted: iron helm, ginger-brown beard, mail, clan-blue tabard with a mountain mark, leather belt, fur boots); `clockwork_drowned.png` 64x64 (vanilla 26.3 drowned with a brass half: amber lens eye, brass chest, clockwork left arm/leg, harness straps; boiler with gauge and firebox); `barnacle_golem.png` 128x128 (vanilla golem in weathered copper, verdigris crevices and tide line, kelp vines, teal eyes, barnacle UVs at x >= 84); `echo_stalker.png` 64x32 + `echo_stalker_emissive.png` (blind face, sculk veins, glowing core). **Overwrites:** the 4 existing mob PNGs.

### Lazy Gorger (texture only, no code)
`greenhaze_hush/.../textures/entity/lazy_gorger.png` overwrites the neon-lime one: vanilla `panda_lazy.png` recoloured, white to sage green, black to moss.

### Items (arsenal; lead request 13:05)
- **Glimmer Mote** `glimmer_mote.png`: lilac Glimmer spark (colours from Glimmer Tonic / Glimmerstone Focus). `models/item/glimmer_mote.json` **overwrites** the module's (it pointed at `minecraft:item/amethyst_shard`).
- **Clockwork Repeater**: 5 states `clockwork_repeater_{standby,pulling_0,pulling_1,pulling_2,arrow}.png` (vanilla crossbow: brass stock, verdigris-copper limbs, steel gear drum). Models `clockwork_repeater.json` (**overwrites**, now `parent: minecraft:item/crossbow`) + `clockwork_repeater_{pulling_0,pulling_1,pulling_2,arrow}.json`. `items/clockwork_repeater.json` **overwrites** the module's: `RepeaterItem` is not a `CrossbowItem` and stores its load in `custom_data.loaded`, so the select uses `minecraft:component` / `minecraft:custom_data` `"{loaded:3}"` for the loaded (arrow) model, then `using_item` + `minecraft:use_duration` (scale 0.0125 = 1/80, `RELOAD_TICKS`) for the pull states. The SNBT string form is valid (`NbtPredicate` uses `TagParser.LENIENT_CODEC`), but **not seen in game**. If the Repeater shows the missing model, replace that file's outer `condition` with its `on_false` branch.

### Boss-bar icons (boss_ui; lead request 13:05)
24 x `boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/<boss>.png` **overwrite** the placeholders (paths from `boss_bars.json`). Each is a round badge in the boss's bar colour with a 10x10 glyph: Gulpmother toad, Strawgrim scarecrow, Neferkhet pharaoh, Rimeback ice peak, Vorlagh kraken, Grunthar crown, Solaxis rod-sun, Mournmaw crying ghast, Ossyra shulker shell, Aphelion coiled wyrm, Tempestra gale eye, Ankhara serpent, Heliodor mirror cog, Kazehana kasa + katana, Auris tympanum rings, Grumblegut grin, Lithara crystals, Percolax smoke with ember eyes, Galvanox lighthouse, Hrolf horned helm, Thurgrim crowned iron jaw, Verdigrim organ pipes, Selenne moon + bloom, Mother Creak heartwood face.

### Kingdom skins (kingdom; 64x64 player layout, base layer only)
Aldermark heraldry: royal blue (#2E4C9E, the TabardLayer default) surcoat, red chief, white tower under a gold crown. Vanilla armor and the tinted `TabardLayer` still draw on top, so in game mostly the faces, hands and the gaps in chainmail show; the painted armor/tabard reads when a unit has no armor or at low detail.
| File | Unit | Look | Used by (unit JSON `skin`) |
|---|---|---|---|
| `soldier_a.png` (**overwrites**) | man-at-arms | chainmail, blue tabard with tower + crown, brown hair | man_at_arms, lancer |
| `soldier_b.png` (**overwrites**) | knight | plate, gold-trimmed tabard, dark beard | knight, mounted_swordsman |
| `archer.png` (**overwrites**) | archer | dyed green leather jerkin, quiver strap, small tower badge, auburn hair | archer, crossbowman |
| `officer.png` (**overwrites**) | sergeant | plate, tabard + red officer sash, grey beard | sergeant |
| `captain.png` (new) | captain | plate, gold-trimmed tabard, red sash, gold gorget | **no unit JSON yet**: set `"skin": "elsewhere:textures/entity/kingdom/captain.png"` |
| `serf.png` (new) | Serf | plain brown tunic, rope belt, rolled sleeves, hose | **no unit JSON yet**: `"skin": "elsewhere:textures/entity/kingdom/serf.png"` |
The overwrite names keep every unit JSON unchanged. If the kingdom lane renamed skins after 13:25, rename these to match. `tabard.png` is untouched.

### art lane 2 file table (source → target; all under `ElsewhereContent/`)
| Source (`art2/...`) | Target |
|---|---|
| `art2/arsenal/src/main/resources/assets/elsewhere/items/clockwork_repeater.json` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/items/clockwork_repeater.json` |
| `art2/arsenal/src/main/resources/assets/elsewhere/models/item/clockwork_repeater.json` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/models/item/clockwork_repeater.json` |
| `art2/arsenal/src/main/resources/assets/elsewhere/models/item/clockwork_repeater_arrow.json` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/models/item/clockwork_repeater_arrow.json` |
| `art2/arsenal/src/main/resources/assets/elsewhere/models/item/clockwork_repeater_pulling_0.json` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/models/item/clockwork_repeater_pulling_0.json` |
| `art2/arsenal/src/main/resources/assets/elsewhere/models/item/clockwork_repeater_pulling_1.json` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/models/item/clockwork_repeater_pulling_1.json` |
| `art2/arsenal/src/main/resources/assets/elsewhere/models/item/clockwork_repeater_pulling_2.json` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/models/item/clockwork_repeater_pulling_2.json` |
| `art2/arsenal/src/main/resources/assets/elsewhere/models/item/glimmer_mote.json` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/models/item/glimmer_mote.json` |
| `art2/arsenal/src/main/resources/assets/elsewhere/textures/item/clockwork_repeater_arrow.png` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/textures/item/clockwork_repeater_arrow.png` |
| `art2/arsenal/src/main/resources/assets/elsewhere/textures/item/clockwork_repeater_pulling_0.png` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/textures/item/clockwork_repeater_pulling_0.png` |
| `art2/arsenal/src/main/resources/assets/elsewhere/textures/item/clockwork_repeater_pulling_1.png` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/textures/item/clockwork_repeater_pulling_1.png` |
| `art2/arsenal/src/main/resources/assets/elsewhere/textures/item/clockwork_repeater_pulling_2.png` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/textures/item/clockwork_repeater_pulling_2.png` |
| `art2/arsenal/src/main/resources/assets/elsewhere/textures/item/clockwork_repeater_standby.png` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/textures/item/clockwork_repeater_standby.png` |
| `art2/arsenal/src/main/resources/assets/elsewhere/textures/item/glimmer_mote.png` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/textures/item/glimmer_mote.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/ankhara.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/ankhara.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/aphelion.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/aphelion.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/auris.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/auris.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/galvanox.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/galvanox.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/grumblegut.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/grumblegut.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/grunthar.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/grunthar.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/gulpmother.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/gulpmother.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/heliodor.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/heliodor.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/hrolf.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/hrolf.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/kazehana.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/kazehana.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/lithara.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/lithara.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/mother_creak.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/mother_creak.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/mournmaw.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/mournmaw.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/neferkhet.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/neferkhet.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/ossyra.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/ossyra.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/percolax.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/percolax.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/rimeback.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/rimeback.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/selenne.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/selenne.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/solaxis.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/solaxis.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/strawgrim.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/strawgrim.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/tempestra.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/tempestra.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/thurgrim.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/thurgrim.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/verdigrim.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/verdigrim.png` |
| `art2/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/vorlagh.png` | `ElsewhereContent/boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons/vorlagh.png` |
| `art2/greenhaze_hush/src/client/java/dev/elsewhere/greenhaze_hush/art2/Art2Mobs.java` | `ElsewhereContent/greenhaze_hush/src/client/java/dev/elsewhere/greenhaze_hush/art2/Art2Mobs.java` |
| `art2/greenhaze_hush/src/client/java/dev/elsewhere/greenhaze_hush/art2/EchoStalkerArt.java` | `ElsewhereContent/greenhaze_hush/src/client/java/dev/elsewhere/greenhaze_hush/art2/EchoStalkerArt.java` |
| `art2/greenhaze_hush/src/main/resources/assets/elsewhere/textures/entity/echo_stalker.png` | `ElsewhereContent/greenhaze_hush/src/main/resources/assets/elsewhere/textures/entity/echo_stalker.png` |
| `art2/greenhaze_hush/src/main/resources/assets/elsewhere/textures/entity/echo_stalker_emissive.png` | `ElsewhereContent/greenhaze_hush/src/main/resources/assets/elsewhere/textures/entity/echo_stalker_emissive.png` |
| `art2/greenhaze_hush/src/main/resources/assets/elsewhere/textures/entity/lazy_gorger.png` | `ElsewhereContent/greenhaze_hush/src/main/resources/assets/elsewhere/textures/entity/lazy_gorger.png` |
| `art2/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/archer.png` | `ElsewhereContent/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/archer.png` |
| `art2/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/captain.png` | `ElsewhereContent/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/captain.png` |
| `art2/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/officer.png` | `ElsewhereContent/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/officer.png` |
| `art2/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/serf.png` | `ElsewhereContent/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/serf.png` |
| `art2/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/soldier_a.png` | `ElsewhereContent/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/soldier_a.png` |
| `art2/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/soldier_b.png` | `ElsewhereContent/kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/soldier_b.png` |
| `art2/verdigris_ironpeak/src/client/java/dev/elsewhere/verdigris_ironpeak/client/art2/Art2Mobs.java` | `ElsewhereContent/verdigris_ironpeak/src/client/java/dev/elsewhere/verdigris_ironpeak/client/art2/Art2Mobs.java` |
| `art2/verdigris_ironpeak/src/client/java/dev/elsewhere/verdigris_ironpeak/client/art2/BarnacleGolemArt.java` | `ElsewhereContent/verdigris_ironpeak/src/client/java/dev/elsewhere/verdigris_ironpeak/client/art2/BarnacleGolemArt.java` |
| `art2/verdigris_ironpeak/src/client/java/dev/elsewhere/verdigris_ironpeak/client/art2/ClockworkDrownedArt.java` | `ElsewhereContent/verdigris_ironpeak/src/client/java/dev/elsewhere/verdigris_ironpeak/client/art2/ClockworkDrownedArt.java` |
| `art2/verdigris_ironpeak/src/client/java/dev/elsewhere/verdigris_ironpeak/client/art2/IronclanRaiderArt.java` | `ElsewhereContent/verdigris_ironpeak/src/client/java/dev/elsewhere/verdigris_ironpeak/client/art2/IronclanRaiderArt.java` |
| `art2/verdigris_ironpeak/src/main/resources/assets/elsewhere/textures/entity/barnacle_golem.png` | `ElsewhereContent/verdigris_ironpeak/src/main/resources/assets/elsewhere/textures/entity/barnacle_golem.png` |
| `art2/verdigris_ironpeak/src/main/resources/assets/elsewhere/textures/entity/clockwork_drowned.png` | `ElsewhereContent/verdigris_ironpeak/src/main/resources/assets/elsewhere/textures/entity/clockwork_drowned.png` |
| `art2/verdigris_ironpeak/src/main/resources/assets/elsewhere/textures/entity/ironclan_raider.png` | `ElsewhereContent/verdigris_ironpeak/src/main/resources/assets/elsewhere/textures/entity/ironclan_raider.png` |

---

# Earlier: art lane 1 (task 1 items; its mob-model plan is now done above)
## art lane 1 (2026-11-11, handed in 13:00 CST)

**State:** task 1 (item textures + armor layers) DONE. Task 2 (mob models) HELD by the lead at 12:52; no model code was written. The plan and API findings for the next art lane are below.

## How to merge (integrator)
- Every file under `art2/<module>/` mirrors its path in the module: `art2/<module>/src/...` → `ElsewhereContent/<module>/src/...` (the core is `art2/Elsewhere/` → `Elsewhere/`). Copy each module's tree over the module, or rsync **without** `--delete`. The full file table is at the end.
- Do **not** copy `art2/_tools/`. It holds the generator (`items.py`, `px.py`, `sheet.py`). To re-run it, put the vanilla textures it loads (extracted from the 26.3 client jar in the Loom cache) in a `vanilla/` folder next to `_tools/`.
- 13 files **overwrite** placeholders that the lanes made today: core `music_disc_pale_light.png`; arsenal `hushward_bell.png`, `mirrorglass_prism.png`; summoner `gale_ribbon.png`, `sporebloom_staff.png`; verdigris_ironpeak `climber_upgrade_smithing_template.png`; petalwind_glasswaste `music_disc_updraft.png`, `music_disc_zenith.png`, `zephyr_wings.png` (item icon only; the `entity/equipment/wings/zephyr_wings.png` wing texture is unchanged), plus 4 item models (next point).
- **Model JSON changes (petalwind_glasswaste):** `models/item/{gale_pinion,blossom_seal_charm,sunwright_mirror_key,sunpit_lure}.json` now point at their own new textures (`elsewhere:item/<id>`). Before, they reused `zephyr_feather`, `blossom_seal`, `sunglass_shard` and `sandswimmer_scale`. Those old textures stay; they're still used by their own items.
- No lang, recipe or registry changes. No ids renamed.

## Item textures (34 items, 38 item PNGs + 4 armor layer PNGs, all checked at x16)
Vanilla-style 16x16, limited ramps, 1 px material-tinted outlines, no noise or gradients. Recolours of vanilla bases are noted.

| Item | Registry id | Module | Notes |
|---|---|---|---|
| Gale Pinion | `gale_pinion` | petalwind_glasswaste | long flight feather, zephyrite vane, gold quill (+ model JSON) |
| Zephyr Wings | `zephyr_wings` | petalwind_glasswaste | vanilla elytra item recoloured to zephyrite (overwrites the old icon) |
| Blossom Seal Charm | `blossom_seal_charm` | petalwind_glasswaste | gold-rimmed zephyrite seal with a skyblossom, bail (+ model JSON) |
| Sunwright Mirror Key | `sunwright_mirror_key` | petalwind_glasswaste | gold key with a sunglass-mirror bow (+ model JSON) |
| Sunpit Lure | `sunpit_lure` | petalwind_glasswaste | sand-scale spoon lure, rotten-flesh bait, hook, line (+ model JSON) |
| Climber upgrade template | `climber_upgrade_smithing_template` | verdigris_ironpeak | vanilla upgrade template in Ironpeak slate with a skyiron arrow |
| Updraft disc | `music_disc_updraft` | petalwind_glasswaste | vanilla disc, pale-cyan label |
| Zenith disc | `music_disc_zenith` | petalwind_glasswaste | vanilla disc, gold label |
| Pale Light disc | `music_disc_pale_light` | Elsewhere (core) | vanilla disc, pale label with a resin-orange centre (Phase 1c/core) |
| Sporebloom Staff | `sporebloom_staff` | summoner | dark-oak shaft, spore-blossom head |
| Gale Ribbon | `gale_ribbon` | summoner | ribbon whip, gold-capped grip, petal tip |
| Mirrorglass Prism | `mirrorglass_prism` | arsenal | glass prism on a gold grip, small rainbow on the far face |
| Hushward Bell | `hushward_bell` | arsenal | deepslate handbell with a sculk-teal rim and rune |
| Galvanic Coilmail helmet/chestplate/leggings/boots | `galvanic_coilmail_*` | gear2 | vanilla chainmail recoloured: copper coils, verdigris gaps |
| Galvanic Coilmail armor layers | equipment asset `elsewhere:galvanic_coilmail` | gear2 | `entity/equipment/humanoid[_leggings]/galvanic_coilmail.png` + `equipment/galvanic_coilmail.json` |
| Amberstill helmet/chestplate/leggings/boots | `amberstill_*` **(FLAG: not registered yet, slug)** | gear2 | vanilla diamond armour recoloured to amber |
| Amberstill armor layers | equipment asset `elsewhere:amberstill` **(FLAG)** | gear2 | `entity/equipment/humanoid[_leggings]/amberstill.png` + `equipment/amberstill.json` |
| Ironclad Flail | `ironclad_flail` | gear2 | skyiron spiked ball, chain, grip (handheld) |
| Gale Rapier | `gale_rapier` | gear2 | needle blade, gold swept guard (handheld) |
| Mirrorbolt Arbalest | `mirrorbolt_arbalest` | gear2 | vanilla crossbow states (standby, pulling_0-2, arrow): bronze stock, mirror-steel limbs. `items/mirrorbolt_arbalest.json` copies the vanilla crossbow select (no firework state) |
| Steam Auger | `steam_auger` | gear2 | skyiron spiral bit, brass boiler, grip (handheld) |
| Termite Hive Lantern | `termite_hive_lantern` | gear2 | vanilla lantern with an amber comb and termites |
| Seer's Hourglass | `seers_hourglass` | gear2 | gold caps, moon-pale sand |
| Prospector's Pendulum | `prospectors_pendulum` | gear2 | cord, brass cap, green dowsing crystal |
| Void Tether Spool | `void_tether_spool` | gear2 | end-stone flanges, void thread, loose end |
| Galvanic upgrade template | `galvanic_upgrade_smithing_template` | gear2 | vanilla upgrade template in verdigris with a copper arrow |
| Heliodor Mirror Core | `heliodor_mirror_core` | gear2 | sunglass orb in a gold ring |
| Ironclad Chain | `ironclad_chain` | gear2 | vanilla iron chain in skyiron |
| Steam Auger Bit | `steam_auger_bit` | gear2 | spiral bit with collar |
| Termite Queen | `termite_queen` | gear2 | dark head, pale segmented abdomen |

- **gear2:** ids were checked against `ElsewhereContent/gear2/src/.../Gear2Items.java` at 12:54. All match except Amberstill, which isn't registered (phase 5); if its ids differ, rename the 4 item PNGs, models and items files and the 2 layer PNGs. For every gear2 item I also supplied `models/item/<id>.json` (generated; handheld for the flail, rapier and auger) and `items/<id>.json`. At 12:54 gear2 had no assets folder; if the gear2 lane has made its own model or items JSON since, keep theirs and copy only the PNGs. Armor and tools were not seen in game.
- **Not done in task 1:** none of the listed items. Not checked in game; the textures were only checked as PNGs (x16 contact sheets).

## Task 2: mob models (HELD; for the art lane after 14:50)
**Superseded:** art lane 2 delivered these models; see the registration change at the top.

Findings (the 14 from V2-PLAN `polish-mob-models-b`), judged from current renderers and textures:
| Mob | Current | Verdict |
|---|---|---|
| Creaking Husk | custom `CreakingHuskModel` (core) | keep |
| Bog Wight | **no such entity exists** in any module | skip, flag to lead |
| Blossom Breeze, Gale Kite, Sandswimmer, Glass Scarab | custom `GeneratedModels` (petalwind_glasswaste) | keep |
| Toke Toad (frog), Catalyst Mimic (shulker), Rimeguard (pale iron golem) | recoloured vanilla, fits | keep |
| **Ironclan Raider** | zombie humanoid ×1.4, brown smudge texture | **upgrade (worst)** |
| **Echo Stalker** | plain black enderman with teal eyes | **upgrade** |
| **Barnacle Golem** | beige iron golem, no barnacles | **upgrade** |
| **Clockwork Drowned** | brown noisy drowned; the class doc promises a boiler on its back | **upgrade** |
| **Lazy Gorger** | panda in neon lime | **texture only**: recolour vanilla `entity/panda/panda_lazy.png` (sleepy eyes) to sage and moss green; no code change |

Plan (cheap: subclass the vanilla model, add 4-11 cubes as children so vanilla animation and render state carry over). Verified in the 26.3 client jar with javap: `LayerDefinition.apply(MeshTransformer)`, `MeshTransformer` (functional), `PartDefinition.getChild/addOrReplaceChild`, `IronGolemModel.createBodyLayer()`, `DrownedModel.createBodyLayer(CubeDeformation)`, `EndermanModel.createBodyLayer()`, `HumanoidModel.createMesh(CubeDeformation, float)`, `BabyDrownedModel extends DrownedModel`, `ArmorModelSet.bake(ArmorModelSet<ModelLayerLocation>, EntityModelSet, Function<ModelPart,M>)`. Register layers with `net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry.registerModelLayer`. Put the new classes in `<module>/src/client/java/.../client/art2/`.
- **Barnacle Golem** (`VerdigrisIronpeakClient` line 37): `BarnacleGolemModel extends IronGolemModel`, `IronGolemModel.createBodyLayer().apply(...)` adds barnacle boxes (3x2x3, 2x1x2, 3x3x2) on the body top and back, the head and the shoulders. UV at x>=84 of the 128x128 texture (vanilla golem UV ends at x=82). Needs its own renderer (`Shapes.Golem` builds `IronGolemModel` itself); copy `Shapes.Golem` with the model passed in. Recolour `barnacle_golem.png` to weathered copper and verdigris.
- **Clockwork Drowned** (line 36): `ClockworkDrownedModel extends DrownedModel`; body children: boiler `addBox(-3,1,2, 6,7,4)`, chimney `addBox(1,-2,4, 2,3,2)`, key shaft plus a YZ-plane bow `addBox(0,-2,0, 0,4,4)` behind the boiler. UV in the free `(0..40, 32..48)` of a 64x64 drowned layout (check alpha first). It can't reuse `DrownedRenderer`: `AgeableMobRenderer` keeps `adultModel` private final. Write `extends AbstractZombieRenderer<Drowned, ZombieRenderState, DrownedModel>` with super(ctx, new ClockworkDrownedModel(bake(LAYER)), new BabyDrownedModel(ctx.bakeLayer(ModelLayers.DROWNED_BABY)), ArmorModelSet.bake(ModelLayers.DROWNED_ARMOR, ctx.getModelSet(), DrownedModel::new), ArmorModelSet.bake(ModelLayers.DROWNED_BABY_ARMOR, ctx.getModelSet(), BabyDrownedModel::new)). Copy vanilla `getArmPose` (main arm + aggressive + trident → `THROW_TRIDENT`) and `setupRotations` (if swimAmount>0, rotateAround(Axis.XP.rotationDegrees(lerp(swim, 0, -10 - xRot)), 0, bbHeight/2/scale, 0)), and scale 1.1. Texture: vanilla `drowned.png` recoloured brass/verdigris + boiler UV.
- **Ironclan Raider** (line 43): `IronclanRaiderModel extends HumanoidModel<HumanoidRenderState>` from `HumanoidModel.createMesh(NONE, 0)`, `LayerDefinition.create(mesh, 64, 64)`; head: horns (base 3x2x2 + tip 1x4x1, mirrored) and beard `addBox(-3.5,-3,-5.5, 7,5,1)` (in front of the hat at z=-4.5); body: fur mantle `addBox(-5,-0.5,-3, 10,4,6)`. UV in the free bottom half: horns (0,32)/(10,32), beard (16,32), mantle (0,40). Hat layer (32,0) = iron helmet. Needs a new hand-painted 64x64 texture; the current one is unusable. The top half keeps the zombie layout, so the new texture also works on the old renderer.
- **Echo Stalker** (`GreenhazeHushClient` line 133): `EchoStalkerModel extends EndermanModel<EndermanRenderState>`, `EndermanModel.createBodyLayer().apply(...)` adds two warden-style tendril planes on the head (`addBox(-6,-8,0, 6,8,0)`, mirrored, slight zRot sway in setupAnim). UV (32,0) in the free `(32..56, 0..16)` of the 64x32 texture. Swap only the model in the existing `Simple` registration. Texture: blind face (no eyes) with sculk-teal veins.

## Full file table (source → target)
| Source | Target |
|---|---|
| `art2/Elsewhere/src/main/resources/assets/elsewhere/textures/item/music_disc_pale_light.png` | `Elsewhere/src/main/resources/assets/elsewhere/textures/item/music_disc_pale_light.png` |
| `art2/arsenal/src/main/resources/assets/elsewhere/textures/item/hushward_bell.png` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/textures/item/hushward_bell.png` |
| `art2/arsenal/src/main/resources/assets/elsewhere/textures/item/mirrorglass_prism.png` | `ElsewhereContent/arsenal/src/main/resources/assets/elsewhere/textures/item/mirrorglass_prism.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/equipment/amberstill.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/equipment/amberstill.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/equipment/galvanic_coilmail.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/equipment/galvanic_coilmail.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/amberstill_boots.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/amberstill_boots.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/amberstill_chestplate.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/amberstill_chestplate.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/amberstill_helmet.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/amberstill_helmet.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/amberstill_leggings.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/amberstill_leggings.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/gale_rapier.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/gale_rapier.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/galvanic_coilmail_boots.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/galvanic_coilmail_boots.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/galvanic_coilmail_chestplate.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/galvanic_coilmail_chestplate.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/galvanic_coilmail_helmet.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/galvanic_coilmail_helmet.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/galvanic_coilmail_leggings.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/galvanic_coilmail_leggings.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/galvanic_upgrade_smithing_template.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/galvanic_upgrade_smithing_template.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/heliodor_mirror_core.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/heliodor_mirror_core.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/ironclad_chain.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/ironclad_chain.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/ironclad_flail.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/ironclad_flail.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/mirrorbolt_arbalest.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/mirrorbolt_arbalest.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/prospectors_pendulum.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/prospectors_pendulum.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/seers_hourglass.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/seers_hourglass.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/steam_auger.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/steam_auger.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/steam_auger_bit.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/steam_auger_bit.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/termite_hive_lantern.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/termite_hive_lantern.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/termite_queen.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/termite_queen.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/items/void_tether_spool.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/items/void_tether_spool.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/amberstill_boots.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/amberstill_boots.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/amberstill_chestplate.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/amberstill_chestplate.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/amberstill_helmet.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/amberstill_helmet.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/amberstill_leggings.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/amberstill_leggings.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/gale_rapier.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/gale_rapier.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/galvanic_coilmail_boots.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/galvanic_coilmail_boots.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/galvanic_coilmail_chestplate.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/galvanic_coilmail_chestplate.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/galvanic_coilmail_helmet.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/galvanic_coilmail_helmet.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/galvanic_coilmail_leggings.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/galvanic_coilmail_leggings.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/galvanic_upgrade_smithing_template.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/galvanic_upgrade_smithing_template.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/heliodor_mirror_core.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/heliodor_mirror_core.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/ironclad_chain.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/ironclad_chain.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/ironclad_flail.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/ironclad_flail.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/mirrorbolt_arbalest.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/mirrorbolt_arbalest.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/mirrorbolt_arbalest_arrow.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/mirrorbolt_arbalest_arrow.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/mirrorbolt_arbalest_pulling_0.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/mirrorbolt_arbalest_pulling_0.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/mirrorbolt_arbalest_pulling_1.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/mirrorbolt_arbalest_pulling_1.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/mirrorbolt_arbalest_pulling_2.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/mirrorbolt_arbalest_pulling_2.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/prospectors_pendulum.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/prospectors_pendulum.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/seers_hourglass.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/seers_hourglass.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/steam_auger.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/steam_auger.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/steam_auger_bit.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/steam_auger_bit.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/termite_hive_lantern.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/termite_hive_lantern.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/termite_queen.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/termite_queen.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/models/item/void_tether_spool.json` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/models/item/void_tether_spool.json` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/entity/equipment/humanoid/amberstill.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/entity/equipment/humanoid/amberstill.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/entity/equipment/humanoid/galvanic_coilmail.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/entity/equipment/humanoid/galvanic_coilmail.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/entity/equipment/humanoid_leggings/amberstill.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/entity/equipment/humanoid_leggings/amberstill.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/entity/equipment/humanoid_leggings/galvanic_coilmail.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/entity/equipment/humanoid_leggings/galvanic_coilmail.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/amberstill_boots.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/amberstill_boots.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/amberstill_chestplate.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/amberstill_chestplate.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/amberstill_helmet.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/amberstill_helmet.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/amberstill_leggings.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/amberstill_leggings.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/gale_rapier.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/gale_rapier.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/galvanic_coilmail_boots.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/galvanic_coilmail_boots.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/galvanic_coilmail_chestplate.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/galvanic_coilmail_chestplate.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/galvanic_coilmail_helmet.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/galvanic_coilmail_helmet.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/galvanic_coilmail_leggings.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/galvanic_coilmail_leggings.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/galvanic_upgrade_smithing_template.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/galvanic_upgrade_smithing_template.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/heliodor_mirror_core.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/heliodor_mirror_core.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/ironclad_chain.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/ironclad_chain.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/ironclad_flail.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/ironclad_flail.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/mirrorbolt_arbalest_arrow.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/mirrorbolt_arbalest_arrow.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/mirrorbolt_arbalest_pulling_0.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/mirrorbolt_arbalest_pulling_0.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/mirrorbolt_arbalest_pulling_1.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/mirrorbolt_arbalest_pulling_1.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/mirrorbolt_arbalest_pulling_2.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/mirrorbolt_arbalest_pulling_2.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/mirrorbolt_arbalest_standby.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/mirrorbolt_arbalest_standby.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/prospectors_pendulum.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/prospectors_pendulum.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/seers_hourglass.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/seers_hourglass.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/steam_auger.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/steam_auger.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/steam_auger_bit.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/steam_auger_bit.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/termite_hive_lantern.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/termite_hive_lantern.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/termite_queen.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/termite_queen.png` |
| `art2/gear2/src/main/resources/assets/elsewhere/textures/item/void_tether_spool.png` | `ElsewhereContent/gear2/src/main/resources/assets/elsewhere/textures/item/void_tether_spool.png` |
| `art2/petalwind_glasswaste/src/main/resources/assets/elsewhere/models/item/blossom_seal_charm.json` | `ElsewhereContent/petalwind_glasswaste/src/main/resources/assets/elsewhere/models/item/blossom_seal_charm.json` |
| `art2/petalwind_glasswaste/src/main/resources/assets/elsewhere/models/item/gale_pinion.json` | `ElsewhereContent/petalwind_glasswaste/src/main/resources/assets/elsewhere/models/item/gale_pinion.json` |
| `art2/petalwind_glasswaste/src/main/resources/assets/elsewhere/models/item/sunpit_lure.json` | `ElsewhereContent/petalwind_glasswaste/src/main/resources/assets/elsewhere/models/item/sunpit_lure.json` |
| `art2/petalwind_glasswaste/src/main/resources/assets/elsewhere/models/item/sunwright_mirror_key.json` | `ElsewhereContent/petalwind_glasswaste/src/main/resources/assets/elsewhere/models/item/sunwright_mirror_key.json` |
| `art2/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/blossom_seal_charm.png` | `ElsewhereContent/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/blossom_seal_charm.png` |
| `art2/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/gale_pinion.png` | `ElsewhereContent/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/gale_pinion.png` |
| `art2/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/music_disc_updraft.png` | `ElsewhereContent/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/music_disc_updraft.png` |
| `art2/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/music_disc_zenith.png` | `ElsewhereContent/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/music_disc_zenith.png` |
| `art2/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/sunpit_lure.png` | `ElsewhereContent/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/sunpit_lure.png` |
| `art2/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/sunwright_mirror_key.png` | `ElsewhereContent/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/sunwright_mirror_key.png` |
| `art2/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/zephyr_wings.png` | `ElsewhereContent/petalwind_glasswaste/src/main/resources/assets/elsewhere/textures/item/zephyr_wings.png` |
| `art2/summoner/src/main/resources/assets/elsewhere/textures/item/gale_ribbon.png` | `ElsewhereContent/summoner/src/main/resources/assets/elsewhere/textures/item/gale_ribbon.png` |
| `art2/summoner/src/main/resources/assets/elsewhere/textures/item/sporebloom_staff.png` | `ElsewhereContent/summoner/src/main/resources/assets/elsewhere/textures/item/sporebloom_staff.png` |
| `art2/verdigris_ironpeak/src/main/resources/assets/elsewhere/textures/item/climber_upgrade_smithing_template.png` | `ElsewhereContent/verdigris_ironpeak/src/main/resources/assets/elsewhere/textures/item/climber_upgrade_smithing_template.png` |
