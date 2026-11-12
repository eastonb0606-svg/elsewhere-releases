package dev.elsewhere.gear3.dev;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.info;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import dev.elsewhere.gear3.setbonus.a.FalconerPair;
import dev.elsewhere.gear3.setbonus.a.GlyphmailPair;
import dev.elsewhere.gear3.setbonus.a.GreaterBond;
import dev.elsewhere.gear3.setbonus.a.HirdPair;
import dev.elsewhere.gear3.setbonus.a.HoldTheLine;
import dev.elsewhere.gear3.setbonus.a.LamplighterPair;
import dev.elsewhere.gear3.setbonus.a.SingleCombat;
import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.minion.MinionTuning;
import dev.elsewhere.summoner.SummonerEntities;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * s-setbonus-a self-test: the SetBonus registry (event-driven mask, kill switches, tier II, the no-wearer fast path,
 * lang and JEI lines) and the seven A bonuses: Greater Bond, Falconer pair, Single Combat, Glyphmail pair, Hold the
 * Line, Hird pair, Lamplighter pair. Synchronous on FakePlayers (they don't tick, so equipment is re-read by hand; they
 * take no damage, so incoming-damage hooks are called directly). A set whose pieces come from a module that is not in
 * this environment is reported as INFO, not failed.
 */
public final class SetBonusTestsA {
	private SetBonusTestsA() {}

	static final String[] IDS = {"greater_bond", "azmar_falconer", "single_combat", "azmar_glyphmail", "hold_the_line", "hrafnholt_hird", "mirewick_lamplighter"};
	static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	static ServerLevel l;
	static FakePlayer p, ally;
	static Mob victim, foeA, foeB;

	static boolean close(double a, double b) {
		return Math.abs(a - b) <= 1.0E-3 * Math.max(1.0, Math.abs(b));
	}

	static FakePlayer fake(String name, Vec3 at) {
		FakePlayer f = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes(("setbonus-a/" + name).getBytes()), name));
		f.snapTo(at.x, at.y, at.z, 0, 0);
		return f;
	}

	static Mob husk(Vec3 at) {
		Mob m = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(at.x, at.y, at.z, 0, 0);
		m.setNoAi(true);
		l.addFreshEntity(m);
		return m;
	}

	static ItemStack stack(Identifier id) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
	}

	/** Every accepted piece of {@code b} is registered in this environment. */
	static boolean complete(SetBonus b) {
		for (int i = 0; i < b.pieceCount(); i++) if (!BuiltInRegistries.ITEM.containsKey(b.accepted(i).iterator().next())) return false;
		return true;
	}

	static void settle(ServerPlayer f) {
		Calling.recompute(f, true);
		SetBonuses.recompute(f);
	}

	static void strip(ServerPlayer f) {
		for (EquipmentSlot s : ARMOR) f.setItemSlot(s, ItemStack.EMPTY);
		settle(f);
	}

	static void wear(ServerPlayer f, SetBonus b) {
		for (EquipmentSlot s : ARMOR) f.setItemSlot(s, ItemStack.EMPTY);
		for (int i = 0; i < b.pieceCount(); i++) f.setItemSlot(b.slot(i), stack(b.accepted(i).iterator().next()));
		settle(f);
	}

	static void fresh(Mob m) {
		m.setHealth(m.getMaxHealth());
		m.damageCooldownTime = 0;
		m.setInvulnerableTime(0);
	}

	static MinionEntity minion() {
		MinionEntity m = MinionManager.summon(p, SummonerEntities.ROOTLING, 0);
		if (m == null) info("setbonus-a: could not summon a test Rootling (slots " + MinionApi.maxSlots(p) + ")");
		return m;
	}

	public static void run(MinecraftServer s) {
		l = s.overworld();
		Vec3 base = new Vec3(52.5, 100, 52.5);
		p = fake("owner", base);
		ally = fake("ally", base.add(2, 0, 0));
		Function<UUID, ServerPlayer> oldLookup = MinionEntity.testOwnerLookup;
		MinionEntity.testOwnerLookup = u -> u.equals(p.getUUID()) ? p : u.equals(ally.getUUID()) ? ally : oldLookup.apply(u);
		victim = husk(base.add(0, 0, 3));
		foeA = husk(base.add(-3, 0, 0));
		foeB = husk(base.add(0, 0, -3));
		try {
			registry();
			singleCombat();
			holdTheLine();
			greaterBond();
			falconer();
			glyphmail();
			hird();
			lamplighter();
		} finally {
			MinionManager.dismissAll(p.getUUID());
			strip(p);
			strip(ally);
			MinionEntity.testOwnerLookup = oldLookup;
			for (Mob m : new Mob[] {victim, foeA, foeB}) m.discard();
		}
	}

	// ---------------------------------------------------------------- registry, lang, JEI, switches

	static void registry() {
		checkS("setbonus-a: the 7 A bonuses are registered (pieces, kill-switch key, detail lines)", () -> {
			for (String id : IDS) {
				SetBonus b = SetBonuses.get(id);
				if (b == null || b.pieceCount() < 2 || !b.killKey().equals("setbonus_" + id) || b.detailLines() < 2) return false;
			}
			return true;
		});
		JsonObject lang = SelfTest.mergedJson("assets/elsewhere/lang/en_us.json");
		checkS("setbonus-a: lang for the shared tooltip lines and every A bonus name and detail line", () -> {
			for (String k : new String[] {"header", "header_pair", "shift", "pair", "tier2"}) if (!lang.has("setbonus.elsewhere." + k)) return false;
			for (String id : IDS) {
				SetBonus b = SetBonuses.get(id);
				if (!lang.has(b.langKey())) return false;
				for (int i = 1; i <= b.detailLines(); i++) if (!lang.has(b.langKey() + ".detail" + i)) return false;
			}
			return true;
		});
		JsonObject jei = SelfTest.json("elsewhere_gear3/jei_info.json");
		checkS("setbonus-a: every gear3 piece of an A bonus has a set/pair bonus JEI line", () -> {
			for (String id : IDS) {
				SetBonus b = SetBonuses.get(id);
				for (int i = 0; i < b.pieceCount(); i++) {
					Identifier piece = b.accepted(i).iterator().next();
					if (!dev.elsewhere.gear3.Gear3Items.BY_ID.containsKey(piece.getPath())) continue; // another module's piece
					JsonArray a = jei.getAsJsonArray(piece.toString());
					if (a == null || !a.toString().contains("bonus")) return false;
				}
			}
			return true;
		});
		strip(p);
		checkS("setbonus-a: nobody wearing a set: hooks take the fast path (damage unchanged, no mask)", () ->
			!SetBonuses.anyone() && SetBonuses.mask(p) == 0L && SetBonuses.modifyDamage(victim, l.damageSources().playerAttack(p), 7F) == 7F);
		for (String id : IDS) {
			SetBonus b = SetBonuses.get(id);
			if (!complete(b)) {
				info("setbonus-a: " + id + " skipped: a piece's module is not in this environment " + b.accepted(0) + "...");
				continue;
			}
			checkS("setbonus-a: " + id + " turns on with every piece worn and off with one missing (equipment-change mask)", () -> {
				wear(p, b);
				boolean on = SetBonuses.active(p, b) && SetBonuses.anyone();
				p.setItemSlot(b.slot(0), ItemStack.EMPTY);
				SetBonuses.recompute(p);
				boolean off = !SetBonuses.active(p, b);
				strip(p);
				return on && off;
			});
			checkS("setbonus-a: " + id + " kill switch setbonus_" + id + " turns it off", () -> {
				wear(p, b);
				SetBonuses.setEnabledForTest(b, false, List.of(p));
				boolean off = !SetBonuses.active(p, b) && SetBonuses.power(p, b) == 0F;
				SetBonuses.setEnabledForTest(b, true, List.of(p));
				boolean back = SetBonuses.active(p, b);
				strip(p);
				return off && back;
			});
		}
		SetBonus sc = SetBonuses.get("single_combat");
		if (complete(sc)) checkS("setbonus-a: Ascendant set = bonus II (tier 2, power x1.5)", () -> {
			Predicate<ItemStack> old = SetBonuses.ascendant;
			try {
				wear(p, sc);
				boolean t1 = SetBonuses.tier(p, sc) == 1 && close(SetBonuses.power(p, sc), 1.0);
				SetBonuses.ascendant = st -> true;
				boolean t2 = SetBonuses.tier(p, sc) == 2 && close(SetBonuses.power(p, sc), SetBonuses.TIER2_SCALE);
				return t1 && t2;
			} finally {
				SetBonuses.ascendant = old;
				strip(p);
			}
		});
	}

	// ---------------------------------------------------------------- Galeplate: Single Combat

	static void singleCombat() {
		SetBonus b = SingleCombat.BONUS;
		if (!complete(b)) return;
		wear(p, b);
		DamageSource hit = l.damageSources().playerAttack(p);
		try {
			foeA.setTarget(p);
			foeB.setTarget(null);
			SingleCombat.clearCache();
			check("setbonus-a: Single Combat counts the one mob targeting you", SingleCombat.hostilesTargeting(p) == 1, SingleCombat.hostilesTargeting(p));
			check("setbonus-a: Single Combat +10% damage dealt with exactly one hostile", close(SetBonuses.modifyDamage(victim, hit, 10F), 11F), SetBonuses.modifyDamage(victim, hit, 10F));
			check("setbonus-a: Single Combat -10% damage taken from that mob", close(SingleCombat.damage(p, l.damageSources().mobAttack(foeA), 10F), 9F));
			check("setbonus-a: Single Combat never against players (PvP neutral)", SingleCombat.damage(ally, hit, 10F) == 10F);
			checkS("setbonus-a: Single Combat through hurtServer (the mixin is wired): the hit lands x1.1", () -> {
				fresh(victim);
				victim.hurtServer(l, hit, 10F);
				float on = victim.getMaxHealth() - victim.getHealth();
				SetBonuses.setEnabledForTest(b, false, List.of(p));
				fresh(victim);
				victim.hurtServer(l, hit, 10F);
				float off = victim.getMaxHealth() - victim.getHealth();
				SetBonuses.setEnabledForTest(b, true, List.of(p));
				fresh(victim);
				return off > 0F && close(on / off, 1.1);
			});
			foeB.setTarget(p);
			SingleCombat.clearCache();
			check("setbonus-a: Single Combat is off with two hostiles", SingleCombat.hostilesTargeting(p) == 2 && SetBonuses.modifyDamage(victim, hit, 10F) == 10F
				&& SingleCombat.damage(p, l.damageSources().mobAttack(foeA), 10F) == 10F);
		} finally {
			foeA.setTarget(null);
			foeB.setTarget(null);
			SingleCombat.clearCache();
			strip(p);
		}
	}

	// ---------------------------------------------------------------- Hearthguard: Hold the Line

	static double lineArmor(ServerPlayer f) {
		AttributeModifier m = f.getAttribute(Attributes.ARMOR).getModifier(HoldTheLine.ARMOR_ID);
		return m == null ? 0.0 : m.amount();
	}

	static void holdTheLine() {
		SetBonus b = HoldTheLine.BONUS;
		if (!complete(b)) {
			info("setbonus-a: Hold the Line skipped (needs rival_hrafnholt's Hearth-Ward Mail)");
			return;
		}
		wear(p, b);
		try {
			check("setbonus-a: Hold the Line: standing, no armor bonus", lineArmor(p) == 0.0 && !HoldTheLine.isHolder(p));
			p.setShiftKeyDown(true);
			check("setbonus-a: Hold the Line: sneaking gives +4 armor (stance event)", close(lineArmor(p), 4.0) && HoldTheLine.isHolder(p), lineArmor(p));
			ally.snapTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
			check("setbonus-a: Hold the Line: an ally within 4 blocks takes 10% less", close(SetBonuses.modifyDamage(ally, l.damageSources().mobAttack(foeA), 10F), 9F));
			ally.snapTo(p.getX() + 6, p.getY(), p.getZ(), 0, 0);
			check("setbonus-a: Hold the Line: an ally 6 blocks away takes full damage", SetBonuses.modifyDamage(ally, l.damageSources().mobAttack(foeA), 10F) == 10F);
			p.setShiftKeyDown(false);
			check("setbonus-a: Hold the Line: standing up removes it", lineArmor(p) == 0.0 && !HoldTheLine.isHolder(p));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			p.startUsingItem(InteractionHand.OFF_HAND);
			boolean up = close(lineArmor(p), 4.0);
			p.stopUsingItem();
			check("setbonus-a: Hold the Line: a raised shield gives it, lowering removes it", up && lineArmor(p) == 0.0);
			p.setShiftKeyDown(true);
			p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
			SetBonuses.recompute(p);
			check("setbonus-a: Hold the Line: taking a piece off while sneaking removes it", lineArmor(p) == 0.0 && !HoldTheLine.isHolder(p));
		} finally {
			p.setShiftKeyDown(false);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			ally.snapTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
			strip(p);
		}
	}

	// ---------------------------------------------------------------- Elderbark: Greater Bond

	static void greaterBond() {
		SetBonus b = GreaterBond.BONUS;
		if (!complete(b)) return;
		GreaterBond.TWO_SLOT.add(SummonerEntities.ROOTLING); // test stand-in for a 2-slot minion
		try {
			MinionEntity m = minion();
			if (m == null) return;
			double baseHp = m.getMaxHealth();
			wear(p, b);
			float pw = SetBonuses.power(p, b);
			check("setbonus-a: Greater Bond: set on gives an out 2-slot minion +30% max health", close(m.getMaxHealth(), baseHp * (1 + 0.30 * pw)), m.getMaxHealth() + " vs " + baseHp);
			check("setbonus-a: Greater Bond: 2-slot minion hits +15%", close(SetBonuses.modifyDamage(victim, MinionApi.damageSource(l, m, p), 10F), 10F * (1F + 0.15F * pw)));
			p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
			SetBonuses.recompute(p);
			check("setbonus-a: Greater Bond: set off removes the health bonus (health clamped)", close(m.getMaxHealth(), baseHp) && m.getHealth() <= m.getMaxHealth());
			MinionManager.dismissAll(p.getUUID());
			wear(p, b);
			float pw2 = SetBonuses.power(p, b);
			MinionEntity m2 = minion();
			check("setbonus-a: Greater Bond: a 2-slot minion called while the set is on joins with +30% (entity-load hook)",
				m2 != null && close(m2.getMaxHealth(), baseHp * (1 + 0.30 * pw2)) && close(m2.getHealth(), m2.getMaxHealth()));
			GreaterBond.TWO_SLOT.remove(SummonerEntities.ROOTLING);
			check("setbonus-a: Greater Bond: 1-slot minions get nothing", m2 != null && SetBonuses.modifyDamage(victim, MinionApi.damageSource(l, m2, p), 10F) == 10F);
		} finally {
			GreaterBond.TWO_SLOT.remove(SummonerEntities.ROOTLING);
			MinionManager.dismissAll(p.getUUID());
			strip(p);
		}
	}

	// ---------------------------------------------------------------- Falconer pair

	static void falconer() {
		SetBonus b = FalconerPair.BONUS;
		if (!complete(b)) {
			info("setbonus-a: Falconer pair skipped (needs rival_azmar's Sandrunner Sandals)");
			return;
		}
		wear(p, b);
		try {
			Arrow arrow = EntityTypes.ARROW.create(l, EntitySpawnReason.COMMAND);
			arrow.setOwner(p);
			MinionEntity m = SummonerEntities.ROOTLING.create(l, EntitySpawnReason.COMMAND);
			m.setOwner(p);
			DamageSource shot = l.damageSources().arrow(arrow, p), bite = MinionApi.damageSource(l, m, p);
			float pw = SetBonuses.power(p, b);
			FalconerPair.clearMarks();
			check("setbonus-a: Falconer pair strength is H(marksman, summoner)", pw > 0F && close(pw, Calling.hybrid(p, Calling.Path.MARKSMAN, Calling.Path.SUMMONER)), pw);
			check("setbonus-a: Falconer pair: unmarked target, no bonus", FalconerPair.damage(victim, bite, 10F) == 10F && FalconerPair.damage(victim, shot, 10F) == 10F);
			FalconerPair.hit(victim, shot, 4F);
			check("setbonus-a: Falconer pair: an arrow hit marks +15% minion damage x H", FalconerPair.arrowMarked(victim, p) && close(FalconerPair.damage(victim, bite, 10F), 10F * (1F + 0.15F * pw)));
			FalconerPair.hit(victim, bite, 4F);
			check("setbonus-a: Falconer pair: a minion hit marks +10% arrow damage x H", FalconerPair.minionMarked(victim, p) && close(FalconerPair.damage(victim, shot, 10F), 10F * (1F + 0.10F * pw)));
			check("setbonus-a: Falconer pair: marks are the owner's own (another player's minion gets nothing)", !FalconerPair.arrowMarked(victim, ally));
			arrow.discard();
			m.discard();
		} finally {
			FalconerPair.clearMarks();
			strip(p);
		}
	}

	// ---------------------------------------------------------------- Glyphmail pair

	static void glyphmail() {
		SetBonus b = GlyphmailPair.BONUS;
		if (!complete(b)) return;
		wear(p, b);
		try {
			float pw = SetBonuses.power(p, b);
			check("setbonus-a: Glyphmail pair strength is H(vanguard, caster)", pw > 0F && close(pw, Calling.hybrid(p, Calling.Path.VANGUARD, Calling.Path.CASTER)), pw);
			GlyphmailPair.clear();
			Glimmer.setForTest(p, 0F);
			DamageSource swing = l.damageSources().playerAttack(p);
			GlyphmailPair.hit(victim, swing, 5F);
			float after1 = Glimmer.current(p);
			GlyphmailPair.hit(victim, swing, 5F);
			float after2 = Glimmer.current(p);
			check("setbonus-a: Glyphmail pair: a melee hit makes 1 Glimmer x H, once per 0.5 s", close(after1, Math.min(Glimmer.max(p), GlyphmailPair.GLIMMER_PER_HIT * pw)) && after2 == after1,
				after1 + " / " + after2);
			check("setbonus-a: Glyphmail pair: spells within 3 s of a hit cost -30% x H", close(GlyphmailPair.costMult(p), 1F - 0.30F * pw));
			float cheap = Glimmer.cost(p, 20, false);
			SetBonuses.setEnabledForTest(b, false, List.of(p));
			float full = Glimmer.cost(p, 20, false);
			SetBonuses.setEnabledForTest(b, true, List.of(p));
			check("setbonus-a: Glyphmail pair: Glimmer.cost is cut (cost hook wired)", cheap < full && Math.abs(cheap - Math.max(1F, full * (1F - 0.30F * pw))) <= 0.11F, cheap + " vs " + full);
			GlyphmailPair.clear();
			check("setbonus-a: Glyphmail pair: no recent hit, full cost", GlyphmailPair.costMult(p) == 1F);
			check("setbonus-a: Glyphmail pair: hits on players make nothing", nothingFromPlayer(swing));
		} finally {
			GlyphmailPair.clear();
			strip(p);
		}
	}

	static boolean nothingFromPlayer(DamageSource swing) {
		GlyphmailPair.clear();
		Glimmer.setForTest(p, 0F);
		GlyphmailPair.hit(ally, swing, 5F);
		return Glimmer.current(p) == 0F;
	}

	// ---------------------------------------------------------------- Hird pair

	static void hird() {
		SetBonus b = HirdPair.BONUS;
		if (!complete(b)) return;
		wear(p, b);
		try {
			float pw = SetBonuses.power(p, b);
			check("setbonus-a: Hird pair strength is H(vanguard, summoner)", pw > 0F && close(pw, Calling.hybrid(p, Calling.Path.VANGUARD, Calling.Path.SUMMONER)), pw);
			check("setbonus-a: Hird pair: Herd focus halved x H", close(HirdPair.herdFocus(p, -0.2), -0.2 * (1.0 - 0.5 * pw)));
			MinionEntity m = minion();
			if (m != null) {
				m.fervorTicks = 1;
				m.idleTicks = 50;
				HirdPair.hit(victim, l.damageSources().playerAttack(p), 5F);
				int want = Math.round(MinionTuning.fervorTicks(p, m) * Math.min(1F, pw));
				check("setbonus-a: Hird pair: a melee hit refreshes minion battle timers (x H)", m.fervorTicks == want && m.idleTicks == 0, m.fervorTicks + " vs " + want);
			}
			strip(p);
			check("setbonus-a: Hird pair off: Herd focus unchanged", HirdPair.herdFocus(p, -0.2) == -0.2);
		} finally {
			MinionManager.dismissAll(p.getUUID());
			strip(p);
		}
	}

	// ---------------------------------------------------------------- Lamplighter pair

	static void lamplighter() {
		SetBonus b = LamplighterPair.BONUS;
		if (!complete(b)) return;
		wear(p, b);
		try {
			float pw = SetBonuses.power(p, b);
			check("setbonus-a: Lamplighter pair strength is H(caster, summoner)", pw > 0F && close(pw, Calling.hybrid(p, Calling.Path.CASTER, Calling.Path.SUMMONER)), pw);
			check("setbonus-a: Lamplighter pair: no minions, no spell bonus", LamplighterPair.spellMult(p, victim) == 1.0);
			MinionEntity m = minion();
			if (m == null) return;
			int n = MinionManager.count(p);
			check("setbonus-a: Lamplighter pair: +6% spell power per minion out x H (max 24%)", close(LamplighterPair.spellMult(p, victim), 1.0 + Math.min(0.24, 0.06 * n) * pw),
				LamplighterPair.spellMult(p, victim));
			m.fervorTicks = 1;
			m.setHealth(m.getMaxHealth() - 3F);
			float hp = m.getHealth();
			LamplighterPair.onCast(p);
			int want = Math.round(MinionTuning.fervorTicks(p, m) * Math.min(1F, pw));
			check("setbonus-a: Lamplighter pair: a cast refreshes minion timers and heals them 2 x H", m.fervorTicks == want && close(m.getHealth(), Math.min(m.getMaxHealth(), hp + 2F * pw)),
				m.fervorTicks + "/" + want + " hp " + m.getHealth());
			check("setbonus-a: Lamplighter pair is in arsenal's spell hooks", dev.elsewhere.arsenal.spell.Spells.DAMAGE_MULT.size() > 0 && dev.elsewhere.arsenal.spell.Spells.ON_CAST.size() > 0);
		} finally {
			MinionManager.dismissAll(p.getUUID());
			strip(p);
		}
	}
}
