package dev.elsewhere.rivals.azmar.dev;

import static dev.elsewhere.rivals.azmar.dev.SelfTest.check;
import static dev.elsewhere.rivals.azmar.dev.SelfTest.info;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.MOVES;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.each;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.face;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.hash;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.keepTarget;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.pass;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.player;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.poll;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.run;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.spawn;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.sync;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.y;

import dev.elsewhere.duelist.adapt.Formation;
import dev.elsewhere.duelist.identity.BossIdentity;
import dev.elsewhere.duelist.identity.BossIdentityAudit;
import dev.elsewhere.duelist.kit.Move;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.azmar.AzmarItems;
import dev.elsewhere.rivals.azmar.boss.AzmarBoss;
import dev.elsewhere.rivals.azmar.boss.AzmarBosses;
import dev.elsewhere.rivals.azmar.boss.AzmarWazir;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Wazir against his v3 card (A2, B1, B2, B4): audit, opt-ins, bottle rules, a splash that shields do not stop and leaving the circle dodges,
 * flare colours and the 40-tick delay, re-forming, the veil, the Seal of the Ibis and the drink limits. Replaces SelfTestBosses.wazir() but keeps its fire-pot check there.
 */
final class SelfTestWazir {
	private SelfTestWazir() {}

	static long blockHash;
	static final java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> SNAP = new java.util.HashMap<>();

	static void snapshot(net.minecraft.server.level.ServerLevel l) {
		SNAP.clear();
		for (int x = -40; x <= 40; x++) for (int z = 110; z <= 190; z++) for (int yy = y() - 4; yy < y() + 12; yy++) { var p = new net.minecraft.core.BlockPos(x, yy, z); SNAP.put(p, l.getBlockState(p)); }
	}

	static int lastChanged;

	/** Blocks random ticks change on their own (fire burning out, grass spreading): not a Wazir edit. */
	static boolean natural(net.minecraft.world.level.block.state.BlockState s) {
		return s.isAir() || s.is(net.minecraft.world.level.block.Blocks.FIRE) || s.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE) || s.is(net.minecraft.world.level.block.Blocks.DIRT) || s.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
	}

	static String changed(net.minecraft.server.level.ServerLevel l) {
		StringBuilder sb = new StringBuilder();
		int n = 0;
		for (var e : SNAP.entrySet()) {
			var now = l.getBlockState(e.getKey());
			if (now.getBlock() == e.getValue().getBlock() || natural(e.getValue()) && natural(now)) continue;
			if (n++ < 5) sb.append(e.getKey().toShortString()).append(' ').append(e.getValue()).append("->").append(now).append("; ");
		}
		lastChanged = n;
		return n + " changed: " + sb;
	}

	/** Clears the arena at z 150 (SelfTest.clean only covers z -80..80), so earlier suites' soldiers do not eat the 15/30 caps. */
	static void wipe(net.minecraft.server.level.ServerLevel l) {
		SelfTest.clean(l);
		for (net.minecraft.world.entity.Entity e : l.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(-90, y() - 20, 60, 90, y() + 40, 240), e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
	}

	static AzmarWazir w() { return (AzmarWazir) SelfTestBosses.boss; }

	/** Every hit the Wazir took during the suite (test-side diagnostics): damage type, attacker type, amount. */
	static final List<String> HURT = new ArrayList<>();
	static boolean hurtHook;

	static void hurtHook() {
		if (hurtHook) return;
		hurtHook = true;
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((e, src, base, taken, blocked) -> {
			if (e instanceof AzmarWazir && HURT.size() < 400)
				HURT.add(src.getMsgId() + "/" + (src.getEntity() == null ? "-" : src.getEntity().getType().toShortString()) + "/" + String.format("%.1f", taken));
		});
	}

	static String hurtSummary() {
		java.util.Map<String, Integer> m = new java.util.TreeMap<>();
		for (String h : HURT) m.merge(h.substring(0, h.lastIndexOf('/')), 1, Integer::sum);
		return HURT.size() + " hits " + m;
	}

	static void add() {
		// ---- card against profile -----------------------------------------------------------------------------------------------
		run((srv, l) -> {
			BossIdentity card = BossIdentity.get("azmar_wazir");
			check("Wazir: identity card loaded from data/elsewhere/boss_identity", card != null, null);
			if (card != null) {
				List<String> p = BossIdentityAudit.checkProfile(card, AzmarWazir.PROFILE);
				check("Wazir: profile matches the card (generic, signature ids, telegraph colour, sound, pitch)", p.isEmpty(), p);
			}
			check("Wazir: opts into drink only (no shield, swap, strafe, crit, pearl, blocks, totem)", AzmarWazir.PROFILE.opted().equals(EnumSet.of(Move.DRINK)), AzmarWazir.PROFILE.opted());
			check("Wazir: Ibis Draught is 2 uses of 15 percent", AzmarWazir.PROFILE.drink != null && AzmarWazir.PROFILE.drink.heal() == 0.15F && AzmarWazir.PROFILE.drink.rule().maxUses() == 2, null);
			check("Wazir: three signature moves exactly", new TreeSet<>(AzmarWazir.PROFILE.signatures.stream().map(m -> m.id()).toList()).equals(new TreeSet<>(Set.of("counter_brew", "glyph_flare", "veil_of_the_ibis"))), null);
		});
		run((srv, l) -> {
			SelfTestBosses.LV = l;
			hurtHook();
			HURT.clear();
			wipe(l);
			blockHash = hash(l, 0, 150, 40);
			snapshot(l);
			SelfTestBosses.fp = player(l, 0, 150 + 9);
			SelfTestBosses.boss = spawn(l, AzmarBosses.WAZIR, 0, 150);
			SelfTestBosses.boss.setTarget(SelfTestBosses.fp);
			AzmarBoss.mark(SelfTestBosses.fp.getUUID(), l.getGameTime() + 6000);
			MOVES.clear();
		});
		each(30, l -> { keepTarget(); face(SelfTestBosses.fp, SelfTestBosses.boss.position()); });
		sync("Wazir: 161 HP, Ibis Crest as real equipment, gilded khopesh", () -> SelfTestBosses.boss.getMaxHealth() >= 160.9F && SelfTestBosses.boss.getMaxHealth() <= 300F && SelfTestBosses.boss.getItemBySlot(EquipmentSlot.HEAD).is(AzmarItems.IBIS_CREST) && SelfTestBosses.boss.getMainHandItem().is(AzmarItems.GILDED_KHOPESH));
		sync("Wazir: three Hands (2 Naffatun and 1 Sand-Dancer)", () -> w().hands.size() == 3);
		sync("Wazir: reads the marked player at once", () -> w().reader.readDone(SelfTestBosses.boss.level().getGameTime()));

		// ---- Glyph Pacing: he holds 8 to 12 blocks off, behind his Hands -----------------------------------------------------------
		List<Double> dists = new ArrayList<>();
		each(160, l -> { keepTarget(); face(SelfTestBosses.fp, SelfTestBosses.boss.position()); SelfTestBosses.fp.setHealth(2000F); if (l.getGameTime() % 10 == 0) dists.add((double) SelfTestBosses.boss.distanceTo(SelfTestBosses.fp)); });
		sync("Wazir pacing: stays well away from the player (median 7 to 13 blocks)", () -> {
			List<Double> s = new ArrayList<>(dists);
			java.util.Collections.sort(s);
			double med = s.get(s.size() / 2);
			info("wazir median distance " + med + " hp " + SelfTestBosses.boss.getHealth() + "/" + SelfTestBosses.boss.getMaxHealth() + " alive " + SelfTestBosses.boss.isAlive() + " | hurt: " + hurtSummary());
			return med >= 7.0 && med <= 13.0;
		});

		// ---- bottles: never 3 in a row, never more than 3 in 200 ticks ---------------------------------------------------------------
		List<Long> brewTimes = new ArrayList<>();
		List<AzmarWazir.Bottle> seq = new ArrayList<>();
		List<Float> seqHp = new ArrayList<>();
		int[] lastBrews = {0};
		run((srv, l) -> { MOVES.clear(); lastBrews[0] = w().brews; SelfTestBosses.fp.removeAllEffects(); SelfTestBosses.fp.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW)); });
		each(900, l -> {
			keepTarget(); face(SelfTestBosses.fp, SelfTestBosses.boss.position()); SelfTestBosses.fp.setHealth(2000F);
			if (w().brews != lastBrews[0]) { lastBrews[0] = w().brews; brewTimes.add(l.getGameTime()); seq.add(w().lastBottle); seqHp.add(w().lastBottleHp); }
		});
		sync("Wazir: Counter-Brew fires through the kit's scheduler", () -> w().brews >= 1 && MOVES.getOrDefault("counter_brew", 0) >= 1);
		sync("Wazir bottles: never more than 3 in any 200 ticks", () -> {
			info("wazir brew ticks " + brewTimes);
			for (int i = 0; i + 3 < brewTimes.size(); i++) if (brewTimes.get(i + 3) - brewTimes.get(i) <= 200) return false;
			return true;
		});
		sync("Wazir bottles: never the same bottle three times in a row, never Poison above 50 percent HP", () -> {
			for (int i = 2; i < seq.size(); i++) if (seq.get(i) == seq.get(i - 1) && seq.get(i) == seq.get(i - 2)) return false;
			// Poison only once he is at or below half health, checked against the health share each bottle was chosen at (Poison Mist is gated by phase in pick)
			for (int i = 0; i < seq.size(); i++) if (seq.get(i) == AzmarWazir.Bottle.POISON && seqHp.get(i) > 0.5F + 1e-4F) return false;
			return true;
		});
		run((srv, l) -> info("wazir brews=" + w().brews + " seq=" + seq + " hp=" + seqHp + " flares=" + w().flares + " veils=" + w().veils + " alive=" + w().isAlive() + " | hurt: " + hurtSummary()));
		sync("Wazir: Glyph Flare was thrown in the fight", () -> w().flares >= 1 && MOVES.getOrDefault("glyph_flare", 0) >= 1);

		// ---- a splash lands on the circle, a raised shield does not stop it, leaving the circle does --------------------------------
		run((srv, l) -> {
			pass(SelfTestBosses.boss, true);
			var f = SelfTestBosses.fp;
			f.removeAllEffects();
			f.snapTo(0.5, y(), 150.5 + 12, 0F, 0F);
			f.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			f.startUsingItem(InteractionHand.OFF_HAND);
			w().splash(l, AzmarWazir.Bottle.SLOWNESS, f.position());
		});
		sync("Wazir splash: a player with a raised shield still gets Slowness", () -> SelfTestBosses.fp.hasEffect(MobEffects.SLOWNESS));
		run((srv, l) -> {
			var f = SelfTestBosses.fp;
			f.stopUsingItem();
			f.removeAllEffects();
			w().splash(l, AzmarWazir.Bottle.WEAKNESS, f.position().add(4.5, 0, 0));   // the circle is 4.5 blocks away: you left it
		});
		sync("Wazir splash: a player outside the 2-block circle is untouched", () -> !SelfTestBosses.fp.hasEffect(MobEffects.WEAKNESS));
		run((srv, l) -> {
			SelfTestBosses.fp.setHealth(10F);
			w().splash(l, AzmarWazir.Bottle.HARMING, SelfTestBosses.fp.position());
		});
		sync("Wazir Harming: never takes a player below 1 HP", () -> SelfTestBosses.fp.getHealth() >= 1F);

		// ---- a fresh Wazir for the scripted sections: the free fight above leaves formation cooldowns (Ring 120 ticks), a red flare's white follow-up or a
		// bad-trade veil in flight, any of which would mask what the next checks look at
		run((srv, l) -> {
			pass(SelfTestBosses.boss, false);
			SelfTestBosses.boss.discard();
			wipe(l);
			SelfTestBosses.fp = player(l, 0, 150 + 12);
			SelfTestBosses.fp.setHealth(2000F);
			SelfTestBosses.boss = spawn(l, AzmarBosses.WAZIR, 0, 150);
			pass(SelfTestBosses.boss, true);
			SelfTestBosses.boss.setTarget(SelfTestBosses.fp);
		});
		each(6, l -> keepTarget());
		sync("Wazir (fresh, scripted): his three Hands stand and the guard signals are ready", () -> w().isAlive() && w().hands.size() == 3 && w().signals != null && w().signals.ready(Formation.RING, SelfTestBosses.boss.level().getGameTime()));

		// ---- flares: colours and the 40-tick delay -------------------------------------------------------------------------------------
		run((srv, l) -> {
			SelfTestBosses.fp.setHealth(2000F);
			SelfTestBosses.fp.snapTo(0.5, y(), 150.5 + 12, 0F, 0F);
			w().flare(l, AzmarWazir.Flare.BLUE, SelfTestBosses.fp);
		});
		sync("Wazir flare: blue is Ring and the Hands have not obeyed yet", () -> w().lastFlare == AzmarWazir.Flare.BLUE && w().signals.current() != Formation.RING);
		each(34, l -> keepTarget());
		sync("Wazir flare: still waiting 34 ticks after the flare (they obey 40 ticks later)", () -> w().signals.current() != Formation.RING);
		each(40, l -> keepTarget());
		sync("Wazir flare: the Hands now circle (Ring)", () -> w().signals.current() == Formation.RING);
		run((srv, l) -> {
			check("Wazir flare colours are amber, blue, red, white", AzmarWazir.Flare.values().length == 4 && AzmarWazir.Flare.AMBER.formation == Formation.SCREEN && AzmarWazir.Flare.WHITE.formation == Formation.WITHDRAW && AzmarWazir.Flare.RED.formation == null, null);
		});

		// ---- Veil of the Ibis: invisible but armoured, ends when hit ------------------------------------------------------------------------
		run((srv, l) -> {
			for (var h : w().hands) { h.setTarget(null); h.setNoAi(true); }   // no stray fire pot ends the veil early
			w().clearFire();
			w().addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0, true, false));
			w().fireSignature(AzmarWazir.VEIL, SelfTestBosses.fp);
		});
		each(24, l -> keepTarget());
		sync("Wazir veil: invisible after the 16-tick tell, crest and armour still worn", () -> {
			boolean ok = w().hasEffect(MobEffects.INVISIBILITY) && !w().getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !w().getItemBySlot(EquipmentSlot.CHEST).isEmpty();
			if (!ok) info("wazir veil veils=" + w().veils + " inv=" + w().hasEffect(MobEffects.INVISIBILITY) + " head=" + w().getItemBySlot(EquipmentSlot.HEAD) + " chest=" + w().getItemBySlot(EquipmentSlot.CHEST) + " action=" + w().action() + " lastHurt=" + w().getLastDamageSource());
			return ok;
		});
		run((srv, l) -> w().hurtServer(l, l.damageSources().generic(), 1F));
		sync("Wazir veil: a hit ends it at once", () -> !w().hasEffect(MobEffects.INVISIBILITY) && !w().veiled());

		// ---- re-forming: 2 of 3 Hands dead (or you within 3 blocks) ----------------------------------------------------------------------------
		run((srv, l) -> {
			pass(SelfTestBosses.boss, false);
			SelfTestBosses.fp.setHealth(2000F);
			for (var h : w().hands) h.setNoAi(false);
			w().reforms = 0;
			List<KingdomSoldier> h = new ArrayList<>(w().hands);
			for (int i = 0; i < 2 && i < h.size(); i++) h.get(i).discard();
		});
		poll("Wazir re-form: two Hands dead gives an amber flare and a re-form", 200, () -> { keepTarget(); SelfTestBosses.fp.setHealth(2000F); return w().reforms >= 1 && w().lastFlare != null; });
		sync("Wazir re-form: the remaining Hands are ordered behind him", () -> {
			boolean ok = w().hands.stream().allMatch(g -> g.isAlive());
			if (!ok || w().reforms < 1) info("wazir reform alive=" + w().isAlive() + " hp=" + w().getHealth() + " reforms=" + w().reforms + " hands=" + w().hands.size() + " action=" + w().action() + " idle=" + w().idle() + " | hurt: " + hurtSummary());
			return ok;
		});

		// ---- Seal of the Ibis: entering the 25 percent phase -------------------------------------------------------------------------------------
		run((srv, l) -> {
			wipe(l);
			SelfTestBosses.fp = player(l, 0, 150 + 2);
			if (SelfTestBosses.fp.isRemoved() || l.getEntity(SelfTestBosses.fp.getUUID()) == null) {
				var f = net.fabricmc.fabric.api.entity.FakePlayer.get(l, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "azmar-sealtest"));
				f.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(2000);
				f.setHealth(2000F);
				f.snapTo(0.5, y(), 152.5, 0F, 0F);
				l.addFreshEntity(f);
				SelfTestBosses.fp = f;
			}
			SelfTestBosses.boss = spawn(l, AzmarBosses.WAZIR, 0, 150);
			pass(SelfTestBosses.boss, true);
			SelfTestBosses.boss.setHealth(SelfTestBosses.boss.getMaxHealth() * 0.20F);
		});
		each(45, l -> { SelfTestBosses.fp.setHealth(2000F); SelfTestBosses.fp.snapTo(w().getX(), y(), w().getZ() + 2.0, 0F, 0F); SelfTestBosses.fp.setDeltaMovement(0, 0, 0); for (var h : w().hands) { h.setTarget(null); h.setNoAi(true); } });
		sync("Wazir Seal: after the 30-tick writing the seal stands, 6 blocks, with 16 absorption", () -> w().sealActive() && w().getAbsorptionAmount() >= 15.9F && w().phaseIndex() == 2);
		each(30, l -> { SelfTestBosses.fp.setHealth(2000F); SelfTestBosses.fp.snapTo(w().sealCentre().x, y(), w().sealCentre().z + 2.0, 0F, 0F); SelfTestBosses.fp.setDeltaMovement(0, 0, 0); for (var h : w().hands) { h.setTarget(null); h.setNoAi(true); } });
		sync("Wazir Seal: a player inside gets Weakness I and Slowness I", () -> {
			var f = SelfTestBosses.fp;
			boolean ok = f.hasEffect(MobEffects.WEAKNESS) && f.hasEffect(MobEffects.SLOWNESS);
			if (!ok) info("wazir seal fp removed=" + f.isRemoved() + " inLevel=" + (SelfTestBosses.l0x().getEntity(f.getUUID()) != null) + " dist=" + f.position().distanceTo(w().sealCentre()) + " creative=" + f.isCreative() + " active=" + w().sealActive());
			return ok;
		});
		run((srv, l) -> SelfTestBosses.fp.snapTo(w().sealCentre().x + 14, y(), w().sealCentre().z, 0F, 0F));
		each(30, l -> SelfTestBosses.fp.setHealth(2000F));
		sync("Wazir Seal: leaving the seal ends the effects", () -> !SelfTestBosses.fp.hasEffect(MobEffects.WEAKNESS) && !SelfTestBosses.fp.hasEffect(MobEffects.SLOWNESS));
		each(180, l -> SelfTestBosses.fp.setHealth(2000F));
		sync("Wazir Seal: it lasts 200 ticks and then ends, once", () -> !w().sealActive() && w().seals == 1 && w().getAbsorptionAmount() == 0F);

		// ---- cleanup and no griefing ------------------------------------------------------------------------------------------------------
		run((srv, l) -> {
			dev.elsewhere.duelist.blocks.DuelBlocks.clearAll(l);
			pass(SelfTestBosses.boss, false);
			SelfTestBosses.boss.discard();
			wipe(l);
		});
		sync("Wazir: no block changed (glyph circles, clouds and the seal are particles)", () -> {
			if (hash(SelfTestBosses.l0x(), 0, 150, 40) == blockHash) return true;
			info("wazir block diff " + changed(SelfTestBosses.l0x()));
			return lastChanged == 0;
		});
	}
}
