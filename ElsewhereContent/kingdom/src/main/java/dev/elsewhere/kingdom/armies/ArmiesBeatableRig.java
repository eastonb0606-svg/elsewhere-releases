package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.ArmyComposer;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Squads stay beatable (ARMIES.md 9.10, RIVALS-DECISIONS 19 item 12) for EVERY kingdom with {@code army.scale}, run by the armies self-test
 * after the other benches. Per kingdom:
 * <ol>
 * <li><b>Full squads.</b> {@link #REPS} basic squads ({@code squad_size}: a sergeant plus members rolled from the kingdom's own makeup, no
 * cavalry, as war battles, waves and garrisons field them) spawn 44 blocks apart. After 30 ticks (a Berserker's Winter Rage and a
 * Wolf-Handler's hounds are up by then) each squad's real attributes are read, and every non-soldier fighter that came with it (the
 * hounds) is recorded as a companion. A squad the caps cut short fails the check: only complete squads count.</li>
 * <li><b>Measured damage.</b> One soldier of every unit type those squads hold fights a weak-diamond dummy (in reach for melee, 10 blocks off
 * for shooters) for 24 s: a husk with no AI,
 * full diamond with Protection I on every piece (20 armor, 8 toughness, EPF 4: the player of the calculator), knockback-proof, at Normal
 * difficulty. The damage it takes after armor and Protection, split into the soldier's own (swings, crits, arrows, bolts, fire pots and their
 * fire, shield shoves) and its companions', gives each unit type's real damage per second at full uptime.</li>
 * <li><b>Duel.</b> {@link ArmyBeatable#duel} for every squad with those numbers (each fighter deals the larger of the model and the
 * measurement), the kingdom's {@code attack_tokens}, companions unheld by tokens and axes taking the shield down. The worst squad must lose.</li>
 * </ol>
 */
final class ArmiesBeatableRig {
	private ArmiesBeatableRig() {}

	static final int REPS = 8, SETTLE = 60, WINDOW = 480, SLICE = 60;
	static final int[][] SPOTS = {{-66, -40}, {-22, -40}, {22, -40}, {66, -40}, {-66, 40}, {-22, 40}, {22, 40}, {66, 40}};

	/** One recorded squad: its soldiers and companions as calculator units, and whether all {@code squad_size} spawned. */
	record Rec(ArmyScale scale, int rep, List<ArmyBeatable.Unit> units, List<ArmyBeatable.Unit> companions, int spawned, String makeup) {}

	/** One measuring lane: a soldier, its dummy and the damage the dummy took in the window. */
	static final class Lane {
		final Identifier unit;
		final KingdomSoldier soldier;
		final Husk dummy;
		double own, comp, lastHealth;
		final Set<UUID> compIds = new HashSet<>();
		Identifier compType;

		Lane(Identifier unit, KingdomSoldier soldier, Husk dummy) {
			this.unit = unit;
			this.soldier = soldier;
			this.dummy = dummy;
		}
	}

	static final List<List<KingdomSoldier>> LIVE = new ArrayList<>();
	static final List<Rec> RECS = new ArrayList<>();
	static final Map<UUID, Lane> LANES = new LinkedHashMap<>();
	/** faction|unit -> measured damage per second of the soldier itself; faction|entity type -> per companion. */
	static final Map<String, Double> DPS = new HashMap<>(), COMP_DPS = new HashMap<>();
	static boolean measuring, listening;
	static Difficulty difficulty;

	static String key(Identifier faction, Identifier unit) {
		return faction + "|" + unit;
	}

	/** Switch: {@code -Delsewhere.armies.beatable=false} skips the rig (test-only code; it runs only under the armies self-test). */
	static final boolean ENABLED = !"false".equals(System.getProperty("elsewhere.armies.beatable"));

	static void build() {
		if (!ENABLED) {
			ArmiesSelfTest.STEPS.add((srv, l) -> {
				ArmiesSelfTest.info("BEATABLE skipped (-Delsewhere.armies.beatable=false)");
				return 1;
			});
			return;
		}
		List<ArmyScale> kingdoms = new ArrayList<>(ArmyScale.all().values());
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			RECS.clear();
			KingdomPerf.forceActive = true; // no player online: every soldier on full AI
			KingdomPerf.testObservers.clear();
			DPS.clear();
			COMP_DPS.clear();
			if (!listening) {
				listening = true;
				ServerLivingEntityEvents.AFTER_DAMAGE.register(ArmiesBeatableRig::onDamage);
			}
			ArmiesSelfTest.info("BEATABLE kingdoms with army.scale: " + kingdoms.stream().map(ArmyScale::faction).toList());
			return 1;
		});
		for (ArmyScale s : kingdoms) {
			squads(s);
			measure(s);
		}
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			verdicts(kingdoms);
			ArmiesSelfTest.clean(l);
			return 5;
		});
	}

	// ---- 1. full squads ------------------------------------------------------------------------------------------------------------
	static void squads(ArmyScale s) {
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			LIVE.clear();
			for (int rep = 0; rep < REPS; rep++) {
				RandomSource r = RandomSource.create(9100L + rep * 131L + s.faction().hashCode());
				List<Identifier> members = ArmyComposer.members(r, s.squadSize() - 1, false, s.faction());
				LIVE.add(KingdomApi.spawnSquad(l, ArmiesSelfTest.at(SPOTS[rep][0], SPOTS[rep][1]), s.faction(), Squad.Formation.LINE, UnitTypes.SERGEANT, members));
			}
			return 30; // Winter Rage (every 20 ticks) and the hounds (tick 3) are up by now
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			List<LivingEntity> others = l.getEntitiesOfClass(LivingEntity.class, new AABB(-100, ArmiesSelfTest.y - 20, -100, 100, ArmiesSelfTest.y + 40, 100),
				e -> e.isAlive() && !(e instanceof KingdomSoldier) && !(e instanceof Player) && e.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)); // fighters only (hounds), not stray farm animals
			for (int rep = 0; rep < LIVE.size(); rep++) {
				List<KingdomSoldier> sq = LIVE.get(rep);
				List<ArmyBeatable.Unit> units = new ArrayList<>(), comps = new ArrayList<>();
				Map<String, Integer> makeup = new TreeMap<>();
				for (KingdomSoldier x : sq) {
					if (!x.isAlive()) continue;
					units.add(ArmyBeatable.Unit.of(x));
					makeup.merge(x.unitId().getPath(), 1, Integer::sum);
				}
				for (LivingEntity o : others) {
					KingdomSoldier near = null;
					double best = 12 * 12;
					for (List<KingdomSoldier> q : LIVE) for (KingdomSoldier x : q) {
						double d = x.distanceToSqr(o);
						if (d < best) {
							best = d;
							near = x;
						}
					}
					if (near != null && sq.contains(near)) {
						comps.add(ArmyBeatable.Unit.companion(o));
						makeup.merge("+" + BuiltInRegistries.ENTITY_TYPE.getKey(o.getType()).getPath(), 1, Integer::sum);
					}
				}
				RECS.add(new Rec(s, rep, units, comps, units.size(), makeup.toString()));
			}
			ArmiesSelfTest.clean(l);
			LIVE.clear();
			return 2;
		});
	}

	// ---- 2. measured damage against the weak-diamond dummy ----------------------------------------------------------------------
	static void measure(ArmyScale s) {
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			LANES.clear();
			difficulty = l.getDifficulty();
			srv.setDifficulty(Difficulty.NORMAL, true);
			List<Identifier> types = new ArrayList<>();
			for (Rec r : RECS) if (r.scale() == s) for (ArmyBeatable.Unit u : r.units()) if (!types.contains(u.id())) types.add(u.id());
			Holder<Enchantment> prot = l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
			for (int i = 0; i < types.size(); i++) {
				int x = -72 + (i % 10) * 16, z = -40 + (i / 10) * 50;
				Squad q = KingdomApi.createSquad(l, s.faction(), Squad.Formation.LINE);
				KingdomSoldier k = KingdomApi.spawnSoldier(l, types.get(i), s.faction(), Vec3.atBottomCenterOf(ArmiesSelfTest.at(x, z)), q);
				if (k == null) {
					ArmiesSelfTest.info("BEATABLE rig: could not spawn " + types.get(i) + " (caps)");
					continue;
				}
				Husk d = dummy(l, ArmiesSelfTest.at(x, z + (k.unit().role().ranged() ? 10 : 2)), prot); // melee starts in reach, shooters at 10 blocks
				if (d == null) continue;
				k.setTarget(d);
				LANES.put(d.getUUID(), new Lane(types.get(i), k, d));
			}
			ArmiesSelfTest.info("BEATABLE rig " + s.faction() + ": " + LANES.size() + " unit types against weak-diamond dummies " + types);
			return SETTLE; // walk up, armor modifiers on the dummies, hounds out
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			for (Lane ln : LANES.values()) {
				ln.own = 0;
				ln.comp = 0;
				ln.compIds.clear();
				ln.lastHealth = ln.dummy.getHealth();
				ArmiesSelfTest.check("the weak-diamond dummy for " + s.faction().getPath() + " " + ln.unit.getPath() + " has 20 armor, 8 toughness and Protection I on all four pieces", ln.dummy.getArmorValue() == 20
					&& Math.abs(ln.dummy.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - 8) < 0.01 && protectionPieces(ln.dummy) == 4,
					ln.dummy.getArmorValue() + " armor, " + ln.dummy.getAttributeValue(Attributes.ARMOR_TOUGHNESS) + " toughness, " + protectionPieces(ln.dummy) + " pieces");
			}
			measuring = true;
			return 0;
		});
		for (int t = 0; t < WINDOW; t += SLICE) {
			ArmiesSelfTest.STEPS.add((srv, l) -> {
				for (Lane ln : LANES.values()) {
					if (ln.soldier.isAlive() && ln.dummy.isAlive() && ln.soldier.getTarget() != ln.dummy) ln.soldier.setTarget(ln.dummy);
					if (ln.dummy.isAlive() && ln.dummy.getHealth() < 200) {
						ln.dummy.setHealth(ln.dummy.getMaxHealth());
						ln.lastHealth = ln.dummy.getHealth();
					}
				}
				return SLICE;
			});
		}
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			measuring = false;
			double secs = WINDOW / 20.0;
			for (Lane ln : LANES.values()) {
				double own = ln.own / secs;
				DPS.merge(key(s.faction(), ln.unit), own, Math::max);
				String c = "";
				if (!ln.compIds.isEmpty() && ln.compType != null) {
					double each = ln.comp / secs / ln.compIds.size();
					COMP_DPS.merge(key(s.faction(), ln.compType), each, Math::max);
					c = String.format(", %d companions (%s) %.2f each", ln.compIds.size(), ln.compType.getPath(), each);
				}
				ArmiesSelfTest.info(String.format("BEATABLE measured %s %s: %.2f damage/s to the weak-diamond dummy (model %.2f)%s", s.faction().getPath(), ln.unit.getPath(), own,
					ln.soldier.unit().role().ranged() ? ArmyBeatable.rangedDps(ArmyBeatable.Unit.of(ln.soldier).withDps(-1)) : ArmyBeatable.meleeDps(ArmyBeatable.Unit.of(ln.soldier).withDps(-1)), c));
			}
			srv.setDifficulty(difficulty, true);
			ArmiesSelfTest.clean(l);
			LANES.clear();
			return 2;
		});
	}

	static Husk dummy(ServerLevel l, BlockPos at, Holder<Enchantment> prot) {
		Husk d = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		if (d == null) return null;
		d.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180F, 0F);
		d.setNoAi(true);
		d.setPersistenceRequired();
		d.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		d.setHealth(1000.0F);
		d.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		d.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		AttributeInstance reinf = d.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (reinf != null) reinf.setBaseValue(0.0);
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		ItemStack[] kit = {new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)};
		for (int i = 0; i < 4; i++) {
			kit[i].enchant(prot, 1);
			d.setItemSlot(slots[i], kit[i]);
			d.setDropChance(slots[i], 0F);
		}
		return l.addFreshEntity(d) ? d : null;
	}

	/**
	 * Damage after armor, Protection and the hurt cooldown, read as the dummy's health loss since its last hit (Fabric's {@code damageTaken} is
	 * before armor). The dummy has no AI and never heals, so every loss is a hit; it is credited to the lane's soldier (its own swings, arrows,
	 * bolts, pots and their fire all name it) or to its companions.
	 */
	static void onDamage(LivingEntity e, DamageSource src, float base, float taken, boolean blocked) {
		if (!measuring) return;
		Lane ln = LANES.get(e.getUUID());
		if (ln == null) return;
		double lost = Math.max(0, ln.lastHealth - e.getHealth());
		ln.lastHealth = e.getHealth();
		Entity by = src.getEntity();
		if (by == ln.soldier) ln.own += lost;
		else if (by instanceof LivingEntity le && !(le instanceof KingdomSoldier) && !(le instanceof Player) && !(le instanceof Husk)) {
			ln.comp += lost;
			ln.compIds.add(le.getUUID());
			ln.compType = BuiltInRegistries.ENTITY_TYPE.getKey(le.getType());
		}
	}

	static int protectionPieces(LivingEntity d) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack st = d.getItemBySlot(slot);
			for (var en : st.getEnchantments().entrySet()) if (en.getKey().is(Enchantments.PROTECTION) && en.getIntValue() == 1) n++;
		}
		return n;
	}

	// ---- 3. the duel ------------------------------------------------------------------------------------------------------------------
	static final Set<String> DUMPED = new HashSet<>();

	static List<ArmyBeatable.Unit> concat(List<ArmyBeatable.Unit> a, List<ArmyBeatable.Unit> b) {
		List<ArmyBeatable.Unit> all = new ArrayList<>(a);
		all.addAll(b);
		return all;
	}

	static void verdicts(List<ArmyScale> kingdoms) {
		DUMPED.clear();
		for (ArmyScale s : kingdoms) {
			ArmyBeatable.Result worst = null;
			Rec worstRec = null;
			int complete = 0, total = 0;
			for (Rec r : RECS) {
				if (r.scale() != s) continue;
				total++;
				if (r.spawned() != s.squadSize()) {
					ArmiesSelfTest.info("BEATABLE " + s.faction().getPath() + " squad " + r.rep() + " incomplete: " + r.spawned() + " of " + s.squadSize());
					continue;
				}
				complete++;
				List<ArmyBeatable.Unit> units = new ArrayList<>(), comps = new ArrayList<>();
				for (ArmyBeatable.Unit u : r.units()) units.add(u.withDps(DPS.getOrDefault(key(s.faction(), u.id()), -1.0)));
				for (ArmyBeatable.Unit c : r.companions()) comps.add(c.withDps(COMP_DPS.getOrDefault(key(s.faction(), c.id()), -1.0)));
				for (ArmyBeatable.Unit u : concat(units, comps)) {
					if (!DUMPED.add(key(s.faction(), u.id()))) continue;
					// the duel's inputs, one line per unit type, so a squad can be replayed by hand when tuning data
					ArmiesSelfTest.info(String.format("BEATABLE unit %s %s: health %.1f armor %.1f toughness %.1f melee %.2f ranged %b block %.2f disables %.1f s measured %.2f/s",
						s.faction().getPath(), u.id().getPath(), u.health(), u.armor(), u.toughness(), u.melee(), u.ranged(), u.block(), u.disableSecs(), u.measuredDps()));
				}
				ArmyBeatable.Result res = ArmyBeatable.duel(units, comps, s.meleeTokens(), s.rangedTokens());
				ArmiesSelfTest.info(String.format("BEATABLE %s squad %d %s: %s with %.1f of 44 health left after %.0f s, %d/%d killed", s.faction().getPath(), r.rep(), r.makeup(),
					res.playerWins() ? "won" : "lost", res.playerHealthLeft(), res.seconds(), res.killed(), res.squad() + res.companions()));
				boolean worse = worst == null || (!res.playerWins() && worst.playerWins())
					|| (res.playerWins() == worst.playerWins() && (res.playerWins() ? res.playerHealthLeft() < worst.playerHealthLeft() : res.killed() < worst.killed()));
				if (worse) {
					worst = res;
					worstRec = r;
				}
			}
			final ArmyBeatable.Result w = worst;
			final Rec wr = worstRec;
			ArmiesSelfTest.check("a full " + s.unitName() + " of " + s.squadSize() + " (" + s.faction() + ", tokens " + s.meleeTokens() + "/" + s.rangedTokens()
				+ ") is beatable by one player in Protection I diamond with a Sharpness I sword (" + complete + " of " + total + " full squads, measured damage)",
				w != null && w.playerWins() && complete == total && total == REPS,
				w == null ? "no full squad spawned" : String.format("worst %s: %s with %.1f of 44 health left after %.0f s, %d/%d killed", wr.makeup(),
					w.playerWins() ? "won" : "lost", w.playerHealthLeft(), w.seconds(), w.killed(), w.squad() + w.companions()));
		}
	}
}
