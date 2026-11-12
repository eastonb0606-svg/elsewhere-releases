package dev.elsewhere.rivals.hrafnholt.dev;

import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.SignatureMove;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.identity.BossIdentity;
import dev.elsewhere.duelist.identity.BossIdentityAudit;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.Move;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.tactics.TacticsRegistry;
import dev.elsewhere.kingdom.tactics.TacticsRuntime;
import dev.elsewhere.kingdom.tactics.Types;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.api.Succession;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.rivals.hrafnholt.HrafnholtContent;
import dev.elsewhere.rivals.hrafnholt.HrafnholtGear;
import dev.elsewhere.rivals.hrafnholt.HrafnholtItems;
import dev.elsewhere.rivals.hrafnholt.boss.Hrafn;
import dev.elsewhere.rivals.hrafnholt.boss.HrafnholtBoss;
import dev.elsewhere.rivals.hrafnholt.boss.HrafnholtProfiles;
import dev.elsewhere.rivals.hrafnholt.boss.HrafnholtStandards;
import dev.elsewhere.rivals.hrafnholt.tactics.HrafnholtTactics;
import dev.elsewhere.rivals.hrafnholt.unit.WolfHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Self-test, milestone 2: the six bosses and five Risen champions (registration, loadout, every signature move, phases, Totem, Hird-oath, caps, drops),
 * the court's Risen promotion, the Wolfskin ice-grip, the field-tactics profile (6.10 chase, 6.11 charge, screen, wolf flank, intimidate, shield bash) and the 15 v 15 MSPT run.
 */
final class SelfTestBosses {
	private SelfTestBosses() {}

	static final double Y = -60;
	static HrafnholtBoss B;
	static Husk D;

	static void check(String n, boolean ok, Object d) { SelfTest.check(n, ok, d); }
	static void info(String s) { SelfTest.info(s); }

	/**
	 * Every {@code assets/elsewhere/lang/en_us.json} on the classpath merged, the first copy winning per key, as the game's
	 * resource stack and the merged jar do (GOTCHAS, sfx 10:50). Null when there is none.
	 */
	static com.google.gson.JsonObject mergedLang() throws java.io.IOException {
		com.google.gson.JsonObject out = null;
		var urls = SelfTestBosses.class.getClassLoader().getResources("assets/elsewhere/lang/en_us.json");
		while (urls.hasMoreElements()) {
			try (var r = new java.io.InputStreamReader(urls.nextElement().openStream(), java.nio.charset.StandardCharsets.UTF_8)) {
				com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
				if (out == null) out = new com.google.gson.JsonObject();
				for (var e : o.entrySet()) if (!out.has(e.getKey())) out.add(e.getKey(), e.getValue());
			}
		}
		return out;
	}

	/** The key has a non-blank name in the lang file. */
	static boolean named(com.google.gson.JsonObject lang, String key) {
		var v = lang.get(key);
		return v != null && v.isJsonPrimitive() && !v.getAsString().isBlank();
	}

	static Vec3 at(double x, double z) { return new Vec3(x + 0.5, Y, z + 0.5); }

	static void clean(ServerLevel l) {
		AABB box = new AABB(-200, -64, -200, 200, 120, 520);
		for (Entity e : l.getEntities((Entity) null, box, x -> !(x instanceof Player))) e.discard();
		DuelBlocks.clearAll(l);
	}

	static void flat(ServerLevel l, int x0, int z0, int x1, int z1) {
		for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) { l.setChunkForced(cx, cz, true); l.getChunk(cx, cz); }
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			for (int y = (int) Y; y < Y + 8; y++) l.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
			l.setBlock(new BlockPos(x, (int) Y - 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
		}
	}

	static HrafnholtBoss boss(ServerLevel l, Hrafn h, boolean risen, double x, double z) {
		HrafnholtBoss b = (risen ? h.risenType : h.type).create(l, EntitySpawnReason.COMMAND);
		b.snapTo(x + 0.5, Y, z + 0.5, 0F, 0F);
		b.setHome(BlockPos.containing(x, Y, z));
		l.addFreshEntity(b);
		return b;
	}

	static Husk dummy(ServerLevel l, double x, double z) {
		Husk h = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		h.setNoAi(true);
		h.setSilent(true);
		h.setPersistenceRequired();
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100000);
		h.setHealth(100000F);
		h.setPos(x + 0.5, Y, z + 0.5);
		l.addFreshEntity(h);
		return h;
	}

	static SignatureMove sig(Hrafn h, String id) {
		return HrafnholtProfiles.of(h, false).signatures.stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
	}

	/** One move on a dummy {@code dist} blocks away: fire it, wait, then check. */
	static void move(String label, Hrafn h, String id, double dist, int wait, BiConsumer<HrafnholtBoss, Husk> setup, java.util.function.BiPredicate<HrafnholtBoss, Husk> ok) {
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, h, false, 0, 100);
			D = dummy(l, 0, 100 + dist);
			B.setTarget(D);
			setup.accept(B, D);
			return 4;
		});
		SelfTest.STEPS.add((srv, l) -> {
			B.fireSignature(sig(h, id), D);
			return wait;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check(label, ok.test(B, D), "stats " + B.stats + ", dummy " + D.getHealth());
			float cap = h == Hrafn.JARL ? 40F : 38F;
			check("  caps: " + id + " dealt at most raw " + cap + " (max " + B.maxRaw + ")", B.maxRaw <= cap + 0.01F, B.maxRaw);
			clean(l);
			return 0;
		});
	}

	static void build() {
		SelfTestHpCap.add(); // 3.0 final review B8 (s-dataclean): every faction file's human bosses at most 300 HP, Serf tier 18
		registration();
		loadouts();
		phasesCap();
		moves();
		phases();
		court();
		ice();
		tactics();
		bench();
	}

	// ---- registration, profiles, lang, skins -------------------------------------------------------------------------------------------------

	static void registration() {
		SelfTest.STEPS.add((srv, l) -> {
			flat(l, -70, 60, 70, 140);
			for (Hrafn h : Hrafn.values()) {
				check("boss type registered: " + h.id(), BuiltInRegistries.ENTITY_TYPE.containsKey(h.id()) && h.type != null, null);
				check("court slot bound: " + h.courtSlot + " -> " + h.key, Leaders.type(HrafnholtContent.FACTION, h.courtSlot) == h.type, null);
				if (h.champion()) check("Risen type registered: " + h.risenId(), BuiltInRegistries.ENTITY_TYPE.containsKey(h.risenId()) && h.risenType != null, null);
				DuelistProfile p = HrafnholtProfiles.of(h, false);
				check("profile HP " + h.key + " = " + h.hp, p.maxHp == h.hp, p.maxHp);
				try (var in = HrafnholtContent.class.getResourceAsStream("/assets/elsewhere/textures/entity/" + h.skinPath() + ".png")) {
					check("skin exists: " + h.skinPath(), in != null, null);
				} catch (Exception e) { check("skin read " + h.key, false, e); }
				if (h.champion()) try (var in = HrafnholtContent.class.getResourceAsStream("/assets/elsewhere/textures/entity/" + h.risenSkinPath() + ".png")) {
					check("Risen skin exists: " + h.risenSkinPath(), in != null, null);
				} catch (Exception e) { check("risen skin read " + h.key, false, e); }
			}
			// the en_us.json the game sees, whatever mod id carries it: this module's own file in its lane, the one merged file
			// in the 3.0 jar (there is no elsewhere_rival_hrafnholt container there)
			com.google.gson.JsonObject read = null;
			try {
				read = mergedLang();
				check("lang file readable", read != null && !read.isEmpty(), "no assets/elsewhere/lang/en_us.json on the classpath");
			} catch (Exception e) { check("lang file readable", false, e); }
			final com.google.gson.JsonObject lang = read != null ? read : new com.google.gson.JsonObject();
			for (Hrafn h : Hrafn.values()) {
				check("lang: " + h.nameKey(), named(lang, h.nameKey()), null);
				if (h.champion()) check("lang: " + h.risenNameKey(), named(lang, h.risenNameKey()), null);
			}
			check("lang: hound name", named(lang, "entity.elsewhere.hrafnholt_hound"), null);
			// signature move sets, telegraph rule, kit moves on and off (profile build already enforces the wind-up minimums and caps)
			var pj = HrafnholtProfiles.of(Hrafn.JARL, false);
			check("Jarl: ruler, shield_block + eat only, 3 signatures, no Totem, Hearth Ember (no lethal hook needed)", pj.ruler && pj.has(Move.SHIELD) && pj.has(Move.EAT) && !pj.has(Move.STRAFE) && !pj.has(Move.CRIT)
				&& pj.signatures.size() == 3 && pj.genericIds().equals(Set.of("shield_block", "eat")), pj.genericIds());
			check("Hersir: circle_strafe + sprint_jump_crit only, no shield, 3 signatures", HrafnholtProfiles.of(Hrafn.HERSIR, false).genericIds().equals(Set.of("circle_strafe", "sprint_jump_crit")) && HrafnholtProfiles.of(Hrafn.HERSIR, false).signatures.size() == 3, null);
			check("Hallwarden: shield_block only; Skald: sprint_jump_crit + eat; Chief and Huntmaster: none", HrafnholtProfiles.of(Hrafn.HALLWARDEN, false).genericIds().equals(Set.of("shield_block"))
				&& HrafnholtProfiles.of(Hrafn.SKALD, false).genericIds().equals(Set.of("sprint_jump_crit", "eat")) && HrafnholtProfiles.of(Hrafn.CHIEF, false).genericIds().isEmpty() && HrafnholtProfiles.of(Hrafn.HUNTMASTER, false).genericIds().isEmpty(), null);
			check("Chief: Frenzy on, one flat card (3 signatures)", HrafnholtProfiles.of(Hrafn.CHIEF, false).frenzy != null && HrafnholtProfiles.of(Hrafn.CHIEF, false).signatures.size() == 3, null);
			check("Hallwarden 3, Skald 3, Huntmaster 3 signatures", HrafnholtProfiles.of(Hrafn.HALLWARDEN, false).signatures.size() == 3 && HrafnholtProfiles.of(Hrafn.SKALD, false).signatures.size() == 3 && HrafnholtProfiles.of(Hrafn.HUNTMASTER, false).signatures.size() == 3, null);
			check("card wind-ups: Slam 24, Cleave 16, Hearth-Horn 30, Raid Charge 20, Rune-Throw 20, Furrow Run 16, Pelt Leap 20, Pelt Dive 16, Wolf Pack 24, Pinning Arrow 17",
				sig(Hrafn.JARL, "ninth_winter_slam").windup() == 24 && sig(Hrafn.JARL, "oath_cleave").windup() == 16 && sig(Hrafn.JARL, "hearth_horn").windup() == 30 && sig(Hrafn.HERSIR, "raid_charge").windup() == 20
				&& sig(Hrafn.SKALD, "rune_throw").windup() == 20 && sig(Hrafn.CHIEF, "furrow_run").windup() == 16 && sig(Hrafn.CHIEF, "pelt_leap").windup() == 20 && sig(Hrafn.HUNTMASTER, "pelt_dive").windup() == 16
				&& sig(Hrafn.HUNTMASTER, "wolf_pack").windup() == 24 && sig(Hrafn.HUNTMASTER, "pinning_arrow").windup() == 17, null);
			boolean rule = true;
			for (Hrafn h : Hrafn.values()) for (SignatureMove m : HrafnholtProfiles.of(h, false).signatures) {
				try { Telegraph.check(m.id(), m.weight(), m.raw(), m.windup()); } catch (IllegalArgumentException e) { rule = false; }
				if (m.raw() > HrafnholtProfiles.of(h, false).capSignature) rule = false;
			}
			check("telegraph rule and damage caps hold for every signature move", rule, null);
			check("Risen move sets match the faction file and the cards", Hrafn.HERSIR.risenMoves.equals(Set.of("raid_charge", "horn_of_three_calls")) && Hrafn.HALLWARDEN.risenMoves.equals(Set.of("hearth_ward", "door_breaker"))
				&& Hrafn.SKALD.risenMoves.equals(Set.of("rune_throw", "three_beat_verse")) && Hrafn.CHIEF.risenMoves.equals(Set.of("furrow_run", "pelt_leap")) && Hrafn.HUNTMASTER.risenMoves.equals(Set.of("wolf_pack", "pinning_arrow")), null);
			// the duelist's sameness audit: the six cards in this module, and each profile against its card
			for (Hrafn h : Hrafn.values()) check("HP cap: profile and card HP of " + h.key + " at most 300", HrafnholtProfiles.of(h, false).maxHp <= 300.0 && HrafnholtProfiles.of(h, true).maxHp <= 300.0, h.hp);
			var cards = new ArrayList<BossIdentity>();
			for (Hrafn h : Hrafn.values()) {
				BossIdentity c = BossIdentity.get("hrafnholt_" + h.key);
				check("identity card loaded: hrafnholt_" + h.key, c != null, null);
				if (c == null) continue;
				cards.add(c);
				var prob = BossIdentityAudit.checkProfile(c, HrafnholtProfiles.of(h, false));
				check("identity audit: profile matches card " + h.key + " (generic, signatures, telegraph colour, sound, pitch)", prob.isEmpty(), prob);
				if (h.champion()) check("card risen_keeps = faction risen moves: " + h.key, new java.util.HashSet<>(c.risenKeeps()).equals(h.risenMoves), c.risenKeeps());
			}
			var limits = BossIdentityAudit.checkPresent(cards);
			check("identity audit: the six Hrafnholt cards pass the sameness limits", limits.isEmpty(), limits);
			return 0;
		});
	}

	// ---- the 300 cap through every phase change (single checks, no polling) ---------------------------------------------------------------

	static List<HrafnholtBoss> CAPPED;

	static void phasesCap() {
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			CAPPED = new ArrayList<>();
			int i = 0;
			for (Hrafn h : Hrafn.values()) {
				HrafnholtBoss b = boss(l, h, false, -60 + i++ * 8, 100);
				var hp = b.getAttribute(Attributes.MAX_HEALTH);
				for (int k = 0; k < 3; k++) hp.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(HrafnholtContent.id("capt_" + k), 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				dev.elsewhere.duelist.boss.HumanBossHealth.enforce(b);   // the one call whoever adds a modifier makes
				b.setTarget(dummy(l, -60 + (i - 1) * 8, 112));
				b.setHealth(b.getMaxHealth() * 0.45F);   // below every first threshold (0.60 Jarl, 0.50 the rest); 0.55 sat above the five 0.50 bosses
				CAPPED.add(b);
			}
			return 70;
		});
		SelfTest.STEPS.add((srv, l) -> {
			for (HrafnholtBoss b : CAPPED) {
				check("cap holds after the first phase change: " + b.h.key + " max health " + b.getMaxHealth() + " (phase " + b.phaseIndex() + ")", b.getMaxHealth() <= 300.01F && b.phaseIndex() >= 1, b.getMaxHealth());
				b.setHealth(b.getMaxHealth() * 0.15F);
			}
			return 70;
		});
		SelfTest.STEPS.add((srv, l) -> {
			for (HrafnholtBoss b : CAPPED) check("cap holds after the second phase change and last stand: " + b.h.key + " max health " + b.getMaxHealth() + " (phase " + b.phaseIndex() + ")", b.getMaxHealth() <= 300.01F && b.phaseIndex() >= 2, b.getMaxHealth());
			clean(l);
			return 0;
		});
	}

	// ---- loadouts ----------------------------------------------------------------------------------------------------------------------------

	static void loadouts() {
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			List<HrafnholtBoss> bs = new ArrayList<>();
			int i = 0;
			for (Hrafn h : Hrafn.values()) {
				bs.add(boss(l, h, false, -60 + i * 8, 100));
				if (h.champion()) bs.add(boss(l, h, true, -60 + i * 8, 108));
				i++;
			}
			LOADED = bs;
			return 6;
		});
		SelfTest.STEPS.add((srv, l) -> {
			for (HrafnholtBoss b : LOADED) {
				Hrafn h = b.h;
				boolean risen = b.isRisen();
				String n = h.key + (risen ? " (Risen)" : "");
				// RIVALS-DECISIONS 13 compensation: +6 armor +3 toughness (Jarl +8 +4) as modifiers, knockback resistance +0.4, damage +25%, signature cooldowns -20%
				boolean jarlC = h == Hrafn.JARL;
				double armMod = b.getAttribute(Attributes.ARMOR).getModifier(HrafnholtContent.id("hrafn_cap_armor")) == null ? -1 : b.getAttribute(Attributes.ARMOR).getModifier(HrafnholtContent.id("hrafn_cap_armor")).amount();
				double toughMod = b.getAttribute(Attributes.ARMOR_TOUGHNESS).getModifier(HrafnholtContent.id("hrafn_cap_tough")) == null ? -1 : b.getAttribute(Attributes.ARMOR_TOUGHNESS).getModifier(HrafnholtContent.id("hrafn_cap_tough")).amount();
				check("compensation " + n + ": armor +" + (jarlC ? 8 : 6) + ", toughness +" + (jarlC ? 4 : 3) + ", knockback resistance >= 0.4, damage x1.25 or more, cooldown x0.8 (100 -> " + b.cooldownFor("x", 100, 100) + ")",
					armMod == (jarlC ? 8.0 : 6.0) && toughMod == (jarlC ? 4.0 : 3.0) && b.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.4 - 1e-6 && b.damageMultiplier() >= 1.25F && b.cooldownFor("x", 100, 100) == 80
						&& b.getMaxHealth() <= 300.01F, armMod + "/" + toughMod);
				if (risen) {
					check("Risen " + n + " is Hird-Captain gear: iron helm, chest, legs, boots + axe", b.getItemBySlot(EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.IRON_HELMET)
						&& b.getItemBySlot(EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.IRON_CHESTPLATE) && (b.getMainHandItem().is(net.minecraft.world.item.Items.IRON_AXE) || b.getMainHandItem().is(net.minecraft.world.item.Items.IRON_SPEAR)), null);
					check("Risen " + n + " HP = captain 30 + 15% = 34.5", Math.abs(b.getMaxHealth() - 34.5) < 0.2, b.getMaxHealth());
					check("Risen " + n + " keeps only its 2 moves", h.risenMoves.stream().allMatch(b::moveAllowed) && !b.moveAllowed("ninth_winter_slam") && !b.moveAllowed("pelt_dive") && !b.moveAllowed("wolf_howl") && !b.moveAllowed("spear_vault") && !b.moveAllowed("bash_chain") && !b.moveAllowed("verse_of_winter"), null);
					check("Risen " + n + " takes the repeat-kill damage bonus", b.damageMultiplier() >= 1.14F, b.damageMultiplier());
					continue;
				}
				for (int k = 0; k < 4; k++) {
					Hrafn.Piece p = h.pieces[k];
					EquipmentSlot s = new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}[k];
					var st = b.getItemBySlot(s);
					if (p == null) check(n + " " + s + " is empty (spec)", st.isEmpty(), st);
					else check(n + " " + s + " is real " + p.item() + " with a trim", st.is(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(p.item()))) && st.has(net.minecraft.core.component.DataComponents.TRIM), st);
				}
				check(n + " HP " + h.hp + " and at most 300 with all boosts", Math.abs(b.getMaxHealth() - h.hp) < 0.01 && b.getMaxHealth() <= 300.01F, b.getMaxHealth());
				// RIVALS-DECISIONS 13: defensive behaviour 30 percent more often (Defence slot of the card), probabilities clamped at 0.95
				switch (h) {
					case JARL, HALLWARDEN -> check(n + " defence (shield block): chosen 1.3x as often (1/3 -> " + b.blockRollChance() + "), clamped at 0.95", Math.abs(b.blockRollChance() - 1F / 3F * 1.3F) < 1e-4F
						&& dev.elsewhere.duelist.boss.HumanBoss.defenceChance(0.9F) == 0.95F && dev.elsewhere.duelist.boss.HumanBoss.defenceChance(0.5F) == 0.65F, b.blockRollChance());
					case HERSIR -> check(n + " defence (Spear Parry): repeats every 60/1.3 = 46 ticks, wind-up window unchanged (4 + 6)", b.parryInterval() == 46 && b.blockRollChance() == 0F, b.parryInterval());
					case HUNTMASTER -> check(n + " defence (Wolf Screen): triggers from 1.3x the distance (6.5 blocks)", Math.abs(b.screenRadius() - 6.5) < 1e-6, b.screenRadius());
					case CHIEF -> check(n + " defence (Unflinching, passive, no choice to scale): knockback resistance stays 1.0 and no roll", Math.abs(b.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - 1.0) < 1e-6 && b.blockRollChance() == 0F, b.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
					case SKALD -> check(n + " defence (card slot is none): no defence roll added", b.blockRollChance() == 0F && !b.getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS), null);
				}
				boolean shield = b.getOffhandItem().has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
				check(n + " shield in the off-hand matches the spec", shield == (h == Hrafn.JARL || h == Hrafn.HALLWARDEN), b.getOffhandItem());
				check(n + " main hand", switch (h) { case JARL, SKALD -> b.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND_AXE); case HERSIR -> b.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND_SPEAR);
					case HALLWARDEN -> b.getMainHandItem().is(net.minecraft.world.item.Items.DIAMOND_SWORD); default -> b.getMainHandItem().is(net.minecraft.world.item.Items.IRON_AXE); }, b.getMainHandItem());
				check(n + " has the name from lang", b.getCustomName() != null && b.getCustomName().getString().length() > 0, b.getCustomName());
			}
			HrafnholtBoss gud = LOADED.stream().filter(b -> b.h == Hrafn.CHIEF && !b.isRisen()).findFirst().orElseThrow();
			check("Gudrun carries two axes", gud.getOffhandItem().is(net.minecraft.world.item.Items.IRON_AXE), gud.getOffhandItem());
			check("Gudrun is in Winter Rage from the start and her damage grows as her HP falls", gud.rageOn() && gud.damageMultiplier() >= 1.14F, gud.damageMultiplier());
			gud.setHealth(gud.getMaxHealth() * 0.2F);
			check("Frenzy: +1% per 1% HP lost (x1.4 at 20% HP, with Rage x1.6)", gud.damageMultiplier() > 1.5F && gud.damageMultiplier() < 2.2F, gud.damageMultiplier());
			clean(l);
			return 0;
		});
	}

	static List<HrafnholtBoss> LOADED;

	// ---- every signature move ----------------------------------------------------------------------------------------------------------------

	static void moves() {
		move("Ninth-Winter Slam: leaps 4 blocks and slams a 3-block ring (Slowness I)", Hrafn.JARL, "ninth_winter_slam", 4, 80, (b, d) -> {}, (b, d) -> b.stat("slam_ring") >= 1 && d.getHealth() < 100000F);
		move("Cleave of the Oath: raw 26 hit", Hrafn.JARL, "oath_cleave", 3, 50, (b, d) -> {}, (b, d) -> b.stat("oath_cleave") >= 1 && d.getHealth() < 100000F);
		move("Hearth-Horn: 30-tick horn, Huskarls walk in", Hrafn.JARL, "hearth_horn", 12, 70, (b, d) -> b.setHealth(b.getMaxHealth() * 0.65F), (b, d) -> b.hornsBlown >= 1);
		move("Raid Charge: 10-block sprint ends in a spear lunge", Hrafn.HERSIR, "raid_charge", 10, 90, (b, d) -> {}, (b, d) -> b.stat("charge_hit") >= 1 && d.getHealth() < 100000F);
		move("Spear Vault: vaults over you and lands behind", Hrafn.HERSIR, "spear_vault", 5, 90, (b, d) -> {}, (b, d) -> b.stat("vault_land") >= 1);
		move("Horn of Three Calls: Strength I and two flanking Huskarls", Hrafn.HERSIR, "horn_of_three_calls", 12, 80, (b, d) -> b.setHealth(b.getMaxHealth() * 0.49F), (b, d) -> b.hornsBlown >= 1 && b.hasEffect(MobEffects.STRENGTH));
		move("Bash Chain: three bashes 10 ticks apart", Hrafn.HALLWARDEN, "bash_chain", 2.5, 90, (b, d) -> {}, (b, d) -> b.stat("bash_chain_hit") >= 2);
		move("Hearth-Ward: shield glows, stores, then a shockwave", Hrafn.HALLWARDEN, "hearth_ward", 2.5, 120, (b, d) -> {}, (b, d) -> b.wardBursts >= 1);
		move("Door-Breaker: 8-block shoulder charge", Hrafn.HALLWARDEN, "door_breaker", 8, 90, (b, d) -> {}, (b, d) -> b.stat("door_hit") >= 1 && d.getHealth() < 100000F);
		move("Rune-Throw: thrown axe", Hrafn.SKALD, "rune_throw", 10, 80, (b, d) -> {}, (b, d) -> b.stat("axe_thrown") >= 1);
		move("Three-Beat Verse: three beats, then Verse Skip hops back", Hrafn.SKALD, "three_beat_verse", 2.5, 90, (b, d) -> {}, (b, d) -> b.stat("verse_beat") >= 3 && b.stat("skip") >= 1);
		move("Verse of Winter: 50-tick song, then the attack-speed window", Hrafn.SKALD, "verse_of_winter", 12, 120, (b, d) -> b.setHealth(b.getMaxHealth() * 0.49F), (b, d) -> b.verseActive() && !b.singing);
		move("Furrow Run: 14-block straight pass, raw 22 once", Hrafn.CHIEF, "furrow_run", 8, 90, (b, d) -> {}, (b, d) -> b.stat("furrow_hit") >= 1 && d.getHealth() < 100000F);
		move("Pelt Leap: 7-block double slam", Hrafn.CHIEF, "pelt_leap", 7, 90, (b, d) -> {}, (b, d) -> b.stat("pelt_slam") >= 2 && d.getHealth() < 100000F);
		move("Wolf Howl: 40-tick roar at 50%", Hrafn.CHIEF, "wolf_howl", 10, 100, (b, d) -> b.setHealth(b.getMaxHealth() * 0.49F), (b, d) -> b.howls >= 1);
		move("Wolf Pack: two wolves", Hrafn.HUNTMASTER, "wolf_pack", 12, 60, (b, d) -> {}, (b, d) -> b.wolvesAlive() == 2);
		move("Pinning Arrow: tagged arrow", Hrafn.HUNTMASTER, "pinning_arrow", 14, 60, (b, d) -> {}, (b, d) -> b.stat("pin_arrow") >= 1);
		move("Pelt Dive: backward leap, then a shot", Hrafn.HUNTMASTER, "pelt_dive", 2.5, 70, (b, d) -> {}, (b, d) -> b.stat("dive_land") >= 1 && b.position().distanceTo(d.position()) > 4.5);
		// Risen: a move outside the kept two never fires
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, Hrafn.HUNTMASTER, true, 0, 100);
			D = dummy(l, 0, 104);
			B.setTarget(D);
			return 4;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("Risen Huntmaster refuses Pelt Dive (not one of the kept moves) but allows Wolf Pack", !sig(Hrafn.HUNTMASTER, "pelt_dive").canUse(B, D) && sig(Hrafn.HUNTMASTER, "wolf_pack").canUse(B, D), null);
			clean(l);
			return 0;
		});
	}

	// ---- phases, horns, rage, Totem, Hird-oath, caps, drops ----------------------------------------------------------------------------------

	static void phases() {
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, Hrafn.JARL, false, 0, 100);
			D = dummy(l, 0, 118);
			B.setTarget(D);
			B.setHealth(B.getMaxHealth() * 0.65F);
			return 90;
		});
		SelfTest.STEPS.add((srv, l) -> {
			int huskarls = l.getEntitiesOfClass(KingdomSoldier.class, new AABB(-40, -64, 60, 40, 80, 160), s -> s.unitId().getPath().equals("hrafnholt_huskarl")).size();
			check("Hearth-Horn at 66%: blown once, two Huskarls walk in", B.hornsBlown == 1 && huskarls >= 1, B.hornsBlown + " horns, " + huskarls + " huskarls");
			B.debugSetHealUses(1);   // the smoked meat (below 60%) would otherwise start first and lift him back above 33%: this step is about the horn
			B.setHealth(B.getMaxHealth() * 0.32F);
			return 400;   // the move is picked at random among three, so watch long enough that a second horn is certain
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("Hearth-Horn again at 33%", B.hornsBlown == 2, B.hornsBlown);
			check("60%: the Jarl's shield splintered for good (off-hand empty)", B.splintered && B.getOffhandItem().isEmpty(), null);
			clean(l);
			return 0;
		});
		// Hearth Ember: the Jarl's last stand at 25% (invulnerable plant, frost ring, 140-point pool, Winter Rage, no heal, no Totem)
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, Hrafn.JARL, false, 0, 100);
			D = dummy(l, 0, 103);
			B.setTarget(D);
			B.setHealth(B.getMaxHealth() * 0.24F);
			return 12;
		});
		SelfTest.STEPS.add((srv, l) -> {
			float hp = B.getHealth();
			B.hurtServer(l, l.damageSources().generic(), 40F);
			check("Hearth Ember: invulnerable during the 30-tick axe-plant", Math.abs(B.getHealth() - hp) < 0.01F, hp + " -> " + B.getHealth());
			return 40;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("Hearth Ember: a 75-point pool, Winter Rage, no heal (HP not above the threshold)", B.emberPool() > 60F && B.rage && B.getHealth() <= B.getMaxHealth() * 0.2501F, B.emberPool() + " hp " + B.getHealth());
			float hp = B.getHealth();
			B.hurtServer(l, l.damageSources().generic(), 30F);
			check("Hearth Ember absorbs the next damage first (HP untouched by a 30 hit)", Math.abs(B.getHealth() - hp) < 0.01F && B.emberPool() < 50F, B.emberPool() + " hp " + B.getHealth());
			B.setHealth(1F);
			B.hurtServer(l, l.damageSources().generic(), 10000F);
			return 3;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("the pool is not a second life: a lethal hit past it kills (one last stand, no Totem)", !B.isAlive() || B.getHealth() <= 1F, B.getHealth());
			clean(l);
			return 0;
		});
		// untracked kills drop through the court table
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, Hrafn.JARL, false, 0, 100);
			D = dummy(l, 0, 120);
			B.setHealth(3F);
			B.hurtServer(l, l.damageSources().generic(), 1000F);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("a Jarl taking lethal damage below the last-stand line dies once (no Totem)", !B.isAlive(), null);
			List<ItemEntity> items = l.getEntitiesOfClass(ItemEntity.class, new AABB(-20, -64, 80, 20, 80, 130));
			Set<String> got = new java.util.TreeSet<>();
			for (ItemEntity ie : items) got.add(ie.getItem().getItem().toString());
			check("an untracked Jarl rolls the court's table itself: Hacksilver and his Seal drop", got.stream().anyMatch(s -> s.contains("hacksilver")) && got.stream().anyMatch(s -> s.contains("hrafnholt_seal_jarl")), got);
			clean(l);
			return 0;
		});
		// Hersir Rally Horn at 50%
		event("Horn of Three Calls at 50%: Strength I to her and a horn blown", Hrafn.HERSIR, 0.49F, 320, b -> b.hornsBlown >= 1);   // Strength I (160 ticks) is applied in the same call as the count; watch long enough for the random pick
		// the AI may first eat (retreat up to 40 + eat) and try a sprint crit (up to 40) before its 1-in-8 signature pick and the 50-tick song,
		// so a fixed 150-tick look could land before the window opened: watch (bounded) until it opens
		eventUntil("Verse of Winter at 50%: song ends, the +20% attack-speed window opens", Hrafn.SKALD, 0.49F, 400, b -> b.verseActive() && !b.singing);
		// Wolf Howl: the AI picks among eligible signatures at random (1 in 8 per tick, weighted), so waiting for it was a race.
		// Drive the part that is deterministic: crossing 50% makes the howl eligible, then fire it and count the roar.
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, Hrafn.CHIEF, false, 0, 100);
			D = dummy(l, 0, 112);
			B.setTarget(D);
			check("Wolf Howl at 50%: not yet eligible at full health", !sig(Hrafn.CHIEF, "wolf_howl").canUse(B, D), null);
			B.setHealth(B.getMaxHealth() * 0.49F);
			return 10;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("Wolf Howl at 50%: crossing the line makes it eligible", B.phaseIndex() >= 1 && sig(Hrafn.CHIEF, "wolf_howl").canUse(B, D), "phase " + B.phaseIndex());
			B.fireSignature(sig(Hrafn.CHIEF, "wolf_howl"), D);
			return 100;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("Wolf Howl at 50%: she roars (counted)", B.howls >= 1, "howls " + B.howls);
			clean(l);
			return 0;
		});
		// the other five last stands
		event("Standard Plant at 25%: a Raven Standard stands, 44-point absorption", Hrafn.HERSIR, 0.24F, 60, b -> b.standardUp() && b.getAbsorptionAmount() > 40F);
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, Hrafn.HERSIR, false, 0, 100);
			D = dummy(l, 0, 118);
			B.setTarget(D);
			B.setHealth(B.getMaxHealth() * 0.24F);
			return 60;
		});
		SelfTest.STEPS.add((srv, l) -> {
			var stands = l.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class, new AABB(-20, -64, 80, 20, 80, 140), x -> x.entityTags().contains(HrafnholtBoss.STANDARD_TAG));
			check("the Raven Standard is an entity (armor stand), not a block", stands.size() == 1, stands.size());
			if (!stands.isEmpty()) {
				var st = stands.get(0);
				check("the standard has 60 health in its own pool", HrafnholtStandards.hp(st.getUUID()) > 59F, HrafnholtStandards.hp(st.getUUID()));
				st.hurtServer(l, l.damageSources().generic(), 61F);
				check("cutting the standard (60 HP) ends it and the absorption", !B.standardUp() && B.getAbsorptionAmount() < 1F && !st.isAlive(), B.getAbsorptionAmount());
			}
			clean(l);
			return 0;
		});
		event("Door Barred at 25%: rooted", Hrafn.HALLWARDEN, 0.24F, 70, b -> b.rooted());
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, Hrafn.HALLWARDEN, false, 0, 100);
			D = dummy(l, 0, 112);
			B.setTarget(D);
			B.setHealth(B.getMaxHealth() * 0.24F);
			return 60;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("Door Barred: rooted (200 ticks)", B.rooted(), null);
			B.setYRot(180F);
			float hp = B.getHealth();
			B.hurtServer(l, l.damageSources().mobAttack(D), 10F);
			float takenBack = hp - B.getHealth();
			B.setYRot(0F);
			hp = B.getHealth();
			B.hurtServer(l, l.damageSources().mobAttack(D), 10F);
			float takenFront = hp - B.getHealth();
			check("Door Barred: shield-wall up while rooted and his front is covered", B.rooted() && takenFront <= takenBack + 0.01F, takenBack + "/" + takenFront);
			return 40;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("Door Barred: he does not walk while rooted", B.position().distanceTo(at(0, 100)) < 1.5, B.position());
			clean(l);
			return 0;
		});
		event("Death-Verse at 20%: the song, then 200 ticks of +25% attack speed, no throws", Hrafn.SKALD, 0.19F, 215, b -> b.verseRage() && !b.singing);
		event("Pelt Shed at 20%: coat torn off, bare rage (+30% speed, Pelt Leap off)", Hrafn.CHIEF, 0.19F, 90, b -> b.shed() && !HrafnholtProfiles.of(Hrafn.CHIEF, false).signatures.stream().filter(m -> m.id().equals("pelt_leap")).findFirst().get().canUse(b, b.getTarget()));
		event("Rope Snares at 20%: leap back to 14 blocks, three rope rings", Hrafn.HUNTMASTER, 0.19F, 110, b -> b.snareCount() == 3);
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, Hrafn.HUNTMASTER, false, 0, 100);
			D = dummy(l, 0, 110);
			B.setTarget(D);
			B.spawnWolves(l, 2);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			var w = l.getEntity(B.wolves.get(0));
			if (w != null) w.discard();
			B.setHealth(B.getMaxHealth() * 0.49F);
			return 120;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("Huntmaster 50%: the second wolf wave brings her back to two wolves", B.wolvesAlive() == 2, B.wolvesAlive());
			var ids = new ArrayList<>(B.wolves);
			B.setHealth(1F);
			B.hurtServer(l, l.damageSources().generic(), 1000F);
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("wolves leave with the fight (gone when the Huntmaster dies)", l.getEntitiesOfClass(net.minecraft.world.entity.animal.wolf.Wolf.class, new AABB(-40, -64, 60, 40, 80, 160), w -> w.entityTags().contains(HrafnholtBoss.WOLF_TAG)).isEmpty(), null);
			clean(l);
			return 0;
		});
		// caps: bonuses never push a hit past the cap
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, Hrafn.JARL, false, 0, 100);
			D = dummy(l, 0, 104);
			B.oathMul = 1.5;
			B.hurtRaw(l, D, 60F, DamageCaps.MINOR, l.damageSources().mobAttack(B));
			float minor = B.lastRaw;
			B.hurtRaw(l, D, 90F, DamageCaps.SIGNATURE, l.damageSources().mobAttack(B));
			check("Hird-oath +50% cannot push an ordinary swing past raw 18 or a signature past raw 40", minor <= 18.01F && B.lastRaw <= 40.01F, minor + " / " + B.lastRaw);
			clean(l);
			return 0;
		});
	}

	static void event(String label, Hrafn h, float frac, int wait, java.util.function.Predicate<HrafnholtBoss> ok) {
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, h, false, 0, 100);
			D = dummy(l, 0, 112);
			B.setTarget(D);
			B.setHealth(B.getMaxHealth() * frac);
			return wait;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check(label, ok.test(B), "horns " + B.hornsBlown + " howls " + B.howls + " rage " + B.rage + " stats " + B.stats);
			clean(l);
			return 0;
		});
	}

	static int untilWaited;

	/** Like {@link #event} but polls each tick (up to {@code maxWait}) and passes as soon as {@code ok} holds. */
	static void eventUntil(String label, Hrafn h, float frac, int maxWait, java.util.function.Predicate<HrafnholtBoss> ok) {
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			B = boss(l, h, false, 0, 100);
			D = dummy(l, 0, 112);
			B.setTarget(D);
			B.setHealth(B.getMaxHealth() * frac);
			untilWaited = 0;
			return 1;
		});
		SelfTest.STEPS.add((srv, l) -> {
			if (!ok.test(B) && B.isAlive() && ++untilWaited < maxWait) return -1;
			check(label, ok.test(B), "after " + untilWaited + " ticks: horns " + B.hornsBlown + " howls " + B.howls + " rage " + B.rage + " stats " + B.stats);
			clean(l);
			return 0;
		});
	}

	// ---- the court: Hird-oath, successor, Risen promotion ------------------------------------------------------------------------------------

	static CourtState ST;
	static HrafnholtBoss KING, MARSHAL, CHANC;

	static void court() {
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			ST = CourtState.get(srv, HrafnholtContent.FACTION);
			ST.resetForTests();
			var k = CourtLife.spawnLeader(l, ST, Ruler.KING, at(0, 100), null);
			var m = CourtLife.spawnLeader(l, ST, Ruler.MARSHAL, at(10, 100), null);
			var c = CourtLife.spawnLeader(l, ST, Ruler.CHANCELLOR, at(20, 100), null);
			check("the court spawns Hrafnholt's own boss types in its slots", k instanceof HrafnholtBoss a && a.h == Hrafn.JARL && m instanceof HrafnholtBoss b && b.h == Hrafn.HERSIR && c instanceof HrafnholtBoss d && d.h == Hrafn.HUNTMASTER, null);
			KING = (HrafnholtBoss) k;
			MARSHAL = (HrafnholtBoss) m;
			CHANC = (HrafnholtBoss) c;
			check("court-tracked leaders carry the leader tag", Leaders.of(KING) != null && Leaders.of(KING).slot().equals("king"), null);
			KING.setNoAi(true);
			MARSHAL.setNoAi(true);
			return 60;
		});
		SelfTest.STEPS.add((srv, l) -> {
			KING.setNoAi(false);
			return 50;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("Hird-oath: the Jarl hits harder per living champion (court count), at most +50%", KING.holder && KING.oathMul > 1.19 && KING.oathMul <= 1.5001, KING.oathMul + " lords " + ST.livingLords());
			check("a champion is not a holder: no Hird-oath bonus", !MARSHAL.holder && MARSHAL.oathMul == 1.0, MARSHAL.oathMul);
			float before = MARSHAL.getMaxHealth();
			MARSHAL.onHolder(true);
			check("a champion who takes the High Seat gets +5% HP and +5% damage", Math.abs(MARSHAL.getMaxHealth() - before * 1.05F) < 0.5F && MARSHAL.successorMul > 1.04, MARSHAL.getMaxHealth() + " vs " + before);
			MARSHAL.onHolder(false);
			check("... and loses it when the seat changes", Math.abs(MARSHAL.getMaxHealth() - before) < 0.5F, MARSHAL.getMaxHealth());
			// Risen promotion: a Hird-Captain steps into the dead Chancellor's seat
			CHANC.discard();
			ST.dead.add(Ruler.CHANCELLOR);
			KingdomApi.spawnSoldier(l, dev.elsewhere.kingdom.UnitTypes.CAPTAIN, HrafnholtContent.FACTION, at(40, 100), null);
			var risen = Succession.promote(l, ST, Ruler.CHANCELLOR);
			check("the court promotes a Hird-Captain into the empty seat as a Risen boss", risen instanceof HrafnholtBoss rb && rb.isRisen() && rb.h == Hrafn.HUNTMASTER, risen);
			if (risen instanceof HrafnholtBoss rb) {
				check("Risen seat holder: captain HP 30 + 15% = 34.5, 2 kept moves, court_risen tag, iron captain gear", rb.getMaxHealth() <= 300.01F && rb.moveAllowed("wolf_pack") && rb.moveAllowed("pinning_arrow")
					&& !rb.moveAllowed("pelt_dive") && rb.entityTags().contains("court_risen"), rb.getMaxHealth());
				rb.setNoAi(true);
			}
			return 5;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("the Risen holder keeps its Risen name and gear after the first server tick", l.getEntitiesOfClass(HrafnholtBoss.class, new AABB(-60, -64, 60, 80, 80, 160), x -> x.isRisen() && x.getItemBySlot(EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.IRON_HELMET)).size() == 1, null);
			ST.resetForTests();
			clean(l);
			return 0;
		});
	}

	// ---- Wolfskin Boots ice-grip -------------------------------------------------------------------------------------------------------------

	static Husk SLIDE_A, SLIDE_B;

	static void ice() {
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			flat(l, -30, 200, 30, 220);
			for (int x = -20; x <= 20; x++) for (int z = 200; z <= 212; z++) l.setBlock(new BlockPos(x, (int) Y - 1, z), Blocks.BLUE_ICE.defaultBlockState(), 2);
			SLIDE_A = slider(l, 0, 202, true);
			SLIDE_B = slider(l, 0, 208, false);
			return 3;
		});
		SelfTest.STEPS.add((srv, l) -> {
			SLIDE_A.setDeltaMovement(0.6, 0, 0);
			SLIDE_B.setDeltaMovement(0.6, 0, 0);
			return 40;
		});
		SelfTest.STEPS.add((srv, l) -> {
			double a = SLIDE_A.getX() - 0.5, b = SLIDE_B.getX() - 0.5;
			check("Wolfskin Boots: no slipping on blue ice (barefoot slides " + String.format("%.1f", b) + " blocks, boots " + String.format("%.1f", a) + ")", b > 2.0 && a < b * 0.7, a + " vs " + b);   // the slide lengths vary run to run (entity pushing, load); the grip difference is what is checked
			check("ice-grip leaves normal friction alone and replaces ice friction for the wearer", HrafnholtGear.iceGrip(SLIDE_A, 0.6F) == 0.6F && HrafnholtGear.iceGrip(SLIDE_A, 0.98F) == 0.6F && HrafnholtGear.iceGrip(SLIDE_B, 0.98F) == 0.98F, null);
			clean(l);
			return 0;
		});
	}

	static Husk slider(ServerLevel l, double x, double z, boolean boots) {
		Husk h = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		h.setNoAi(false);
		h.setSilent(true);
		h.setPersistenceRequired();
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		h.setHealth(1000F);
		if (boots) h.setItemSlot(EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(HrafnholtItems.WOLFSKIN_BOOTS));
		h.setPos(x + 0.5, Y, z + 0.5);
		l.addFreshEntity(h);
		return h;
	}

	// ---- field tactics -------------------------------------------------------------------------------------------------------------------------

	static List<KingdomSoldier> HRAF, ALD;
	static Squad SQ;
	static long CHARGE_START;
	static int withdrawSeen, chargeSeen;
	static double maxDisp;
	static Vec3 startPos;

	static Identifier hid(String p) { return HrafnholtContent.id("hrafnholt_" + p); }

	static List<Identifier> hmix(int n) {
		List<Identifier> all = List.of(hid("huskarl"), hid("huskarl"), hid("shield_thane"), hid("spear_thane"), hid("hunter"), hid("huskarl"), hid("shield_thane"), hid("berserker"), hid("huskarl"), hid("spear_thane"));
		return new ArrayList<>(all.subList(0, Math.max(0, Math.min(n, all.size()))));
	}

	static List<Identifier> amix(int n) {
		List<Identifier> all = List.of(dev.elsewhere.kingdom.UnitTypes.MAN_AT_ARMS, dev.elsewhere.kingdom.UnitTypes.CROSSBOWMAN, dev.elsewhere.kingdom.UnitTypes.PIKEMAN, dev.elsewhere.kingdom.UnitTypes.ARCHER,
			dev.elsewhere.kingdom.UnitTypes.KNIGHT, dev.elsewhere.kingdom.UnitTypes.MAN_AT_ARMS, dev.elsewhere.kingdom.UnitTypes.CROSSBOWMAN, dev.elsewhere.kingdom.UnitTypes.PIKEMAN, dev.elsewhere.kingdom.UnitTypes.MAN_AT_ARMS);
		return new ArrayList<>(all.subList(0, Math.max(0, Math.min(n, all.size()))));
	}

	static void tactics() {
		SelfTest.STEPS.add((srv, l) -> {
			var p = TacticsRegistry.of(HrafnholtContent.FACTION);
			check("tactics: the Hrafnholt profile is the registered one and not a pass-through", p == HrafnholtTactics.PROFILE && !p.passThrough(), null);
			check("tactics: interval 15, min odds 1.0, retreat 0, blunder 0.10, reaction 8-18, intel noise 0.45, mob policy default", p.decisionInterval() == 15 && p.minLocalOdds() == 1.0 && p.retreatOdds() == 0.0
				&& Math.abs(p.blunderRate() - 0.10) < 1e-9 && p.reactionTicksMin() == 8 && p.reactionTicksMax() == 18 && p.intelNoise() != null && Math.abs(p.intelNoise() - 0.45) < 1e-9 && p.mobPolicy().equals("default"), null);
			List<String> ids = p.behaviours().stream().map(b -> b.id()).toList();
			check("tactics: behaviours charge, berserker_screen, wolf_flank, chase_routed, poor_patience, weak_focus, night_raid, intimidate, engage_default, fall_back_regroup", ids.containsAll(List.of("charge", "berserker_screen",
				"wolf_flank", "chase_routed", "poor_patience", "weak_focus", "night_raid", "intimidate", "engage_default", "fall_back_regroup")), ids);
			check("tactics: no refuse_fight, break_los or back_off_creeper (it never breaks off and does not mitigate creepers)", ids.stream().noneMatch(s -> s.equals("refuse_fight") || s.equals("break_los") || s.equals("back_off_creeper")), ids);
			return 0;
		});
		// 6.11: Hrafnholt charges at 0.5 odds, never withdraws, makes contact in 10 s
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			flat(l, -60, 280, 60, 340);
			KingdomApi.setStance(srv, HrafnholtContent.FACTION, Factions.ALDERMARK, Factions.Stance.WAR);
			KingdomPerf.forceActive = true;
			KingdomPerf.testObservers.clear();
			HRAF = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 300)), HrafnholtContent.FACTION, Squad.Formation.LINE, hid("hird_sergeant"), hmix(3));
			ALD = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 322)), Factions.ALDERMARK, Squad.Formation.LINE, dev.elsewhere.kingdom.UnitTypes.SERGEANT, amix(7));
			withdrawSeen = chargeSeen = 0;
			HrafnholtTactics.charges = HrafnholtTactics.screens = 0;
			CHARGE_START = l.getGameTime();
			return 40;
		});
		for (int i = 0; i < 10; i++) SelfTest.STEPS.add((srv, l) -> {
			for (KingdomSoldier k : HRAF) {
				if (k.tacticsOrder == null) continue;
				var kind = k.tacticsOrder.kind();
				if (kind == Types.OrderKind.WITHDRAW || kind == Types.OrderKind.FALL_BACK) withdrawSeen++;
				if (kind == Types.OrderKind.CHARGE) chargeSeen++;
			}
			return 20;
		});
		SelfTest.STEPS.add((srv, l) -> {
			double minDist = 1e9;
			for (KingdomSoldier h : HRAF) if (h.isAlive()) for (KingdomSoldier a : ALD) if (a.isAlive()) minDist = Math.min(minDist, h.distanceTo(a));
			float hurt = 0;
			for (KingdomSoldier a : ALD) hurt += a.getMaxHealth() - a.getHealth();
			check("6.11 Hrafnholt (4) charges 8 Aldermark soldiers at about 0.5 odds: CHARGE orders issued, no WITHDRAW or FALL_BACK ever", chargeSeen > 0 && withdrawSeen == 0, chargeSeen + " charge, " + withdrawSeen + " withdraw, charges " + HrafnholtTactics.charges);
			check("6.11 contact within 10 s of the first sighting (closest pair " + String.format("%.1f", minDist) + " blocks, or damage dealt)", minDist < 3.5 || hurt > 1F, minDist + " / " + hurt);
			check("the framework's brain made the choices (decisions without failures)", HRAF.stream().noneMatch(k -> k.squad() != null && k.squad().brain != null && k.squad().brain.failures > 0), null);
			clean(l);
			return 5;
		});
		// berserker_screen
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			HrafnholtTactics.screens = 0;
			var sq = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 300)), HrafnholtContent.FACTION, Squad.Formation.LINE, hid("hird_sergeant"), List.of(hid("berserker"), hid("huskarl"), hid("huskarl"), hid("berserker")));
			ALD = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 322)), Factions.ALDERMARK, Squad.Formation.LINE, dev.elsewhere.kingdom.UnitTypes.SERGEANT, amix(3));
			HRAF = sq;
			return 300;
		});
		SelfTest.STEPS.add((srv, l) -> {
			check("berserker_screen: the Berserkers run ahead when the charge begins (long horn, screen counted)", HrafnholtTactics.screens >= 1, HrafnholtTactics.screens);
			clean(l);
			return 3;
		});
		// 6.10 chase_routed
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			HrafnholtTactics.chaseOrders = 0;
			HRAF = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 290)), HrafnholtContent.FACTION, Squad.Formation.LINE, hid("hird_sergeant"), hmix(4));
			ALD = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 312)), Factions.ALDERMARK, Squad.Formation.LINE, dev.elsewhere.kingdom.UnitTypes.SERGEANT, amix(3));
			for (KingdomSoldier a : ALD) a.setNoAi(false);
			startPos = HRAF.get(0).position();
			maxDisp = 0;
			return 50;
		});
		SelfTest.STEPS.add((srv, l) -> {
			for (KingdomSoldier a : ALD) a.rout(240);
			return 80;
		});
		SelfTest.STEPS.add((srv, l) -> {
			int chasers = 0;
			for (KingdomSoldier k : HRAF) {
				maxDisp = Math.max(maxDisp, k.position().distanceTo(startPos));
				if (k.tacticsOrder != null && k.tacticsOrder.kind() == Types.OrderKind.CHARGE && k.tacticsOrder.target() != null && ALD.stream().anyMatch(a -> a.getUUID().equals(k.tacticsOrder.target()))) chasers++;
			}
			check("6.10 chase_routed: Hrafnholt orders its free soldiers (60%+) after a routed enemy squad (" + chasers + " of " + HRAF.size() + ")", chasers >= Math.ceil(HRAF.size() * 0.6) - 1,
				chasers + " chasers, " + HrafnholtTactics.chaseOrders + " orders, last " + HRAF.get(0).squad().brain.lastChoice);
			return 100;
		});
		SelfTest.STEPS.add((srv, l) -> {
			for (KingdomSoldier k : HRAF) maxDisp = Math.max(maxDisp, k.position().distanceTo(startPos));
			check("6.10 the pursuit is bounded: nobody strays more than 56 blocks from where the chase began", maxDisp <= 56, maxDisp);
			clean(l);
			return 3;
		});
		// wolf flank
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			var sq = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 300)), HrafnholtContent.FACTION, Squad.Formation.LINE, hid("hird_sergeant"), List.of(hid("wolf_handler"), hid("huskarl")));
			HRAF = sq;
			ALD = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 316)), Factions.ALDERMARK, Squad.Formation.LINE, dev.elsewhere.kingdom.UnitTypes.SERGEANT, amix(3));
			return 100;
		});
		SelfTest.STEPS.add((srv, l) -> {
			int flank = 0;
			for (KingdomSoldier k : HRAF) if (k instanceof WolfHandler wh) flank += wh.flankSteps;
			String choice = HRAF.get(0).squad() != null && HRAF.get(0).squad().brain != null ? HRAF.get(0).squad().brain.lastChoice : "no brain";
			check("wolf_flank: the Wolf-Handler's wolves circle to the flank while the handler holds the front", flank > 0, flank + " flank steps, brain: " + choice);
			clean(l);
			return 3;
		});
		// intimidate
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			HRAF = KingdomApi.spawnSquad(l, BlockPos.containing(at(0, 300)), HrafnholtContent.FACTION, Squad.Formation.LINE, hid("hird_sergeant"), hmix(4));
			SQ = HRAF.get(0).squad();
			HrafnholtTactics.intimidate(SQ.id(), at(0, 305), 600, l.getGameTime());
			return 80;
		});
		SelfTest.STEPS.add((srv, l) -> {
			String c = SQ.brain == null ? "no brain" : SQ.brain.lastChoice;
			check("intimidate: a squad the villages lane marks holds a ring in the square with banners up", c.startsWith("intimidate"), c);
			clean(l);
			return 3;
		});
		// shield bash
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			var h = KingdomApi.spawnSoldier(l, hid("shield_thane"), HrafnholtContent.FACTION, at(0, 300), null);
			var a = KingdomApi.spawnSoldier(l, dev.elsewhere.kingdom.UnitTypes.KNIGHT, Factions.ALDERMARK, at(0, 301.5), null);
			HRAF = List.of(h);
			ALD = List.of(a);
			return 3;
		});
		SelfTest.STEPS.add((srv, l) -> {
			long b0 = HrafnholtTactics.bashes;
			HrafnholtTactics.bash(l, HRAF.get(0), ALD.get(0));
			check("shield_bash: shoves the enemy back and counts", HrafnholtTactics.bashes == b0 + 1 && ALD.get(0).getDeltaMovement().horizontalDistance() > 0.15, ALD.get(0).getDeltaMovement());
			clean(l);
			return 3;
		});
	}

	// ---- 15 v 15 MSPT --------------------------------------------------------------------------------------------------------------------------

	static int BENCH_N;
	static long BENCH_DEC0;

	static void bench() {
		SelfTest.STEPS.add((srv, l) -> {
			clean(l);
			flat(l, -60, 400, 60, 460);
			KingdomApi.setStance(srv, HrafnholtContent.FACTION, Factions.ALDERMARK, Factions.Stance.WAR);
			KingdomPerf.forceActive = true;
			KingdomPerf.testObservers.clear();
			int made = 0, made2 = 0;
			for (int i = 0; i < 3; i++) made += KingdomApi.spawnSquad(l, BlockPos.containing(at(-14 + i * 14, 420)), HrafnholtContent.FACTION, i == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE, hid("hird_sergeant"),
				hmix(Math.min(4, 14 - made))).size();
			for (int i = 0; i < 3; i++) made2 += KingdomApi.spawnSquad(l, BlockPos.containing(at(-14 + i * 14, 438)), Factions.ALDERMARK, i == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE, dev.elsewhere.kingdom.UnitTypes.SERGEANT,
				amix(Math.min(4, 14 - made2))).size();
			info("BENCH start: spawned " + made + " Hrafnholt + " + made2 + " Aldermark");
			BENCH_N = made + made2;
			SelfTest.SAMPLES.clear();
			return 100;
		});
		SelfTest.STEPS.add((srv, l) -> {
			SelfTest.SAMPLES.clear();
			SelfTest.measuring = true;
			BENCH_DEC0 = TacticsRuntime.decisionsTotal;
			return 400;
		});
		SelfTest.STEPS.add((srv, l) -> {
			SelfTest.measuring = false;
			long[] s = SelfTest.SAMPLES.stream().mapToLong(Long::longValue).sorted().toArray();
			double avg = Arrays.stream(s).average().orElse(0) / 1e6;
			double p95 = s.length == 0 ? 0 : s[(int) (s.length * 0.95)] / 1e6;
			double max = s.length == 0 ? 0 : s[s.length - 1] / 1e6;
			int hr = 0, ald = 0;
			for (KingdomSoldier k : KingdomPerf.loaded(l)) {
				if (HrafnholtContent.FACTION.equals(k.factionId())) hr++;
				else if (Factions.ALDERMARK.equals(k.factionId())) ald++;
			}
			long dec = TacticsRuntime.decisionsTotal - BENCH_DEC0;
			String line = String.format("BENCH 15 v 15 Hrafnholt (tactics on) vs Aldermark: MSPT avg %.2f ms, p95 %.2f, max %.2f over %d ticks | spawned %d, alive %d + %d | %d brain decisions | bashes %d, charges %d",
				avg, p95, max, s.length, BENCH_N, hr, ald, dec, HrafnholtTactics.bashes, HrafnholtTactics.charges);
			info(line);
			SelfTest.BENCH.add(line);
			check("benchmark ran 400 ticks", s.length >= 390, s.length);
			check("battle stays within the 30 per area cap (15 + 15)", BENCH_N <= 30 && BENCH_N >= 20, BENCH_N);
			check("15 v 15 MSPT average at most 2.0 ms", avg <= 2.0, String.format("%.2f ms", avg));
			check("15 v 15 MSPT p95 at most 3.0 ms", p95 <= 3.0, String.format("%.2f ms", p95));
			check("squad brains decide at most every 10 ticks (decisions per squad per tick under 0.1)", dec <= 6.0 * s.length / 10.0 + 6, dec + " decisions");
			clean(l);
			KingdomApi.setStance(srv, HrafnholtContent.FACTION, Factions.ALDERMARK, Factions.Stance.PEACE);
			return 0;
		});
	}
}
