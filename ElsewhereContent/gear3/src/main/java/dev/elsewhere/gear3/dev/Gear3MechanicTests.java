package dev.elsewhere.gear3.dev;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.checkS;
import static dev.elsewhere.gear3.dev.SelfTest.near;

import dev.elsewhere.arsenal.spell.CasterItem;
import dev.elsewhere.gear3.ChiselItem;
import dev.elsewhere.gear3.Gear3Hooks;
import dev.elsewhere.gear3.Gear3Items;
import dev.elsewhere.gear3.Gear3LightBlocks;
import dev.elsewhere.gear3.Gear3Scheduler;
import dev.elsewhere.gear3.SpellItems;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Per-mechanic test blocks: one block per unique mechanic, called at the end of the self-test. Everything runs synchronously on a FakePlayer. */
final class Gear3MechanicTests {
	private Gear3MechanicTests() {}

	static Item it(String id) {
		return Gear3Items.BY_ID.get(id);
	}

	static Mob husk(ServerLevel l, Vec3 at) {
		Mob m = EntityTypes.HUSK.create(l, EntitySpawnReason.COMMAND);
		m.snapTo(at.x, at.y, at.z, 0, 0);
		m.setNoAi(true);
		l.addFreshEntity(m);
		return m;
	}

	static void run(MinecraftServer s) {
		SelfTest.group("Gear3MechanicTests", () -> mechanics(s)); // s-asm-gear3: own guard, so the whip and hurlbat blocks still run if this one stops
		SelfTest.group("WhipTests", () -> WhipTests.run(s)); // s-whips: "<whip id>: ..." and "whips: ..." checks
		SelfTest.group("HurlbatSelfTest", () -> dev.elsewhere.gear3.s3.hurlbat.HurlbatSelfTest.run(s)); // s-hurlbat: "hurlbat: ..." checks
	}

	static void mechanics(MinecraftServer s) {
		ServerLevel l = s.overworld();
		BlockPos base = new BlockPos(40, 100, 40);
		FakePlayer p = FakePlayer.get(l);
		p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);

		// ---- Chisel
		checkS("Chisel: every family cycles back to its start", () -> {
			for (Block start : ChiselItem.NEXT.keySet()) {
				Block b = start;
				int n = 0;
				do {
					b = ChiselItem.NEXT.get(b);
					n++;
				} while (b != null && b != start && n < 8);
				if (b != start) return false;
			}
			return true;
		});
		checkS("Chisel: use turns stone into stone bricks and costs 1 durability", () -> {
			BlockPos at = base.offset(0, -3, 0);
			l.setBlock(at, Blocks.STONE.defaultBlockState(), 3);
			ItemStack st = new ItemStack(it("aldermark_masons_chisel"));
			p.setItemInHand(InteractionHand.MAIN_HAND, st);
			var r = st.getItem().useOn(new UseOnContext(l, p, InteractionHand.MAIN_HAND, st, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
			boolean ok = r.consumesAction() && l.getBlockState(at).is(Blocks.STONE_BRICKS) && st.getDamageValue() == 1;
			l.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
			return ok;
		});

		// ---- Rangefinder Lens (the hook's number)
		check("Rangefinder: x1.0 up to 16 blocks, +1% per block, max +20%", near(Gear3Hooks.lensFactor(10), 1.0) && near(Gear3Hooks.lensFactor(26), 1.10) && near(Gear3Hooks.lensFactor(100), 1.20));
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(it("rangefinder_lens")));
		check("Rangefinder: active from the off hand", dev.elsewhere.arsenal.trinket.Trinkets.isActive(p, it("rangefinder_lens")));

		// ---- Oath Ring
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(it("aldermark_oath_ring")));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(it("mirewick_lanternfang_estoc")));
		double t0 = p.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		Gear3Hooks.refreshOath(p);
		check("Oath Ring: +1 toughness while a Vanguard weapon is held", near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), t0 + 1.0), p.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		Gear3Hooks.refreshOath(p);
		check("Oath Ring: gone again with a non-Vanguard item", near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), t0));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(it("mirewick_lanternfang_estoc")));
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		Gear3Hooks.refreshOath(p);
		check("Oath Ring: needs the ring", near(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), t0));
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

		// ---- Quickdraw Quiver
		check("Quiver: no change without the trinket", Gear3Hooks.bowTimeLeft(71990, 72000, p) == 71990 && Gear3Hooks.crossbowDuration(25, p) == 25);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(it("aldermark_quickdraw_quiver")));
		check("Quiver: bow draw counts 1/0.85 as long", Gear3Hooks.bowTimeLeft(72000 - 17, 72000, p) == 72000 - 20, Gear3Hooks.bowTimeLeft(72000 - 17, 72000, p));
		check("Quiver: crossbow charge 25 -> 21 ticks", Gear3Hooks.crossbowDuration(25, p) == 21);

		// ---- Fowler's Bandolier
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(it("mirewick_fowlers_bandolier")));
		int saved = 0;
		boolean plainSaved = false;
		for (int i = 0; i < 400; i++) {
			if (Gear3Hooks.bandolierSaves(p, new ItemStack(Items.SPECTRAL_ARROW))) saved++;
			if (Gear3Hooks.bandolierSaves(p, new ItemStack(Items.ARROW))) plainSaved = true;
		}
		check("Bandolier: spectral arrows saved about 40% (400 shots)", saved > 120 && saved < 200 && !plainSaved, saved);
		{ // s-asm-gear3 (lead directive 12:58): the swarm's special arrows are saved too
			java.util.List<String> special = java.util.List.of("echo_arrow", "sunglass_arrow", "tidebrass_harpoon_bolt");
			java.util.Map<String, Integer> got = new java.util.LinkedHashMap<>();
			boolean all = true;
			for (String sid : special) {
				Item si = it(sid);
				if (si == null) { all = false; got.put(sid, -1); continue; }
				int n = 0;
				for (int i = 0; i < 400; i++) if (Gear3Hooks.bandolierSaves(p, new ItemStack(si))) n++;
				got.put(sid, n);
				all &= Gear3Hooks.isSpecialArrow(new ItemStack(si)) && n > 120 && n < 200;
			}
			check("Bandolier: Echo, Sunglass and Harpoon arrows are special arrows, saved about 40% (400 shots each)", all, got);
		}
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		check("Bandolier: nothing without the trinket", !Gear3Hooks.bandolierSaves(p, new ItemStack(Items.SPECTRAL_ARROW)));

		// ---- Binding Sigil
		Mob target = husk(l, new Vec3(base.getX() + 4.5, base.getY(), base.getZ() + 0.5));
		MobEffectInstance slow = new MobEffectInstance(MobEffects.SLOWNESS, 100, 0);
		check("Sigil: no change without the trinket", Gear3Hooks.sigil(target, slow, p).getDuration() == 100);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(it("hush_binding_sigil")));
		check("Sigil: Slowness 100 -> 130 ticks on a non-boss", Gear3Hooks.sigil(target, slow, p).getDuration() == 130);
		check("Sigil: Poison untouched", Gear3Hooks.sigil(target, new MobEffectInstance(MobEffects.POISON, 100, 0), p).getDuration() == 100);
		check("Sigil: no source, no bonus", Gear3Hooks.sigil(target, slow, null).getDuration() == 100);
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

		// ---- Spell items: costs from GEAR.md and effects
		String[][] costs = {{"amber_grimoire", "" + SpellItems.GRIMOIRE_COST}, {"mirewick_almoners_chalice", "" + SpellItems.CHALICE_COST}, {"azmar_ka_sigil", "" + SpellItems.KA_COST},
			{"glimmer_lantern", "" + SpellItems.LANTERN_COST}, {"blinkstone", "" + SpellItems.BLINK_COST}, {"emberfall_rod", "" + SpellItems.EMBER_COST},
			{"frostwake_scepter", "" + SpellItems.FROST_COST}, {"starfall_orrery", "" + SpellItems.ORRERY_COST}};
		int[] want = {6, 8, 10, 2, 12, 8, 7, 20};
		for (int i = 0; i < costs.length; i++) {
			final int k = i;
			checkS(costs[i][0] + ": is a CasterItem costing " + want[i] + " Glimmer", () -> it(costs[k][0]) instanceof CasterItem c && c.rawCost(p) == want[k]);
		}
		if (!SelfTest.spellEnv()) { // s-asm-gear3: a classes-only devlib (cloud mirror) has no elsewhere:spell damage type, so no spell hit can be built
			SelfTest.info("spell item effects skipped: damage type elsewhere:spell is not in this environment");
			target.discard();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return;
		}
		LivingEntity dummy = husk(l, new Vec3(base.getX() + 6.5, base.getY(), base.getZ() + 0.5));
		SpellItems.AmberGrimoire.amberLock(p, dummy, 4);
		check("Grimoire: non-boss amber-locked (Slowness IV, Weakness II)", dummy.hasEffect(MobEffects.SLOWNESS) && dummy.getEffect(MobEffects.SLOWNESS).getAmplifier() == 3
			&& dummy.hasEffect(MobEffects.WEAKNESS) && dummy.getEffect(MobEffects.WEAKNESS).getAmplifier() == 1);
		SpellItems.KaSigil.ward(p);
		check("Ka Sigil: Absorption 6 HP", near(p.getAbsorptionAmount(), 6));
		var wolf = EntityTypes.WOLF.create(l, EntitySpawnReason.COMMAND);
		wolf.snapTo(base.getX() + 2.5, base.getY(), base.getZ() + 0.5, 0, 0);
		wolf.tame(p);
		l.addFreshEntity(wolf);
		wolf.setHealth(4);
		int healed = 0;
		for (int i = 0; i < 2 && healed == 0; i++) healed = SpellItems.AlmonersChalice.splash(l, p, new Vec3(base.getX() + 2.5, base.getY(), base.getZ() + 0.5));
		check("Chalice: heals the caster's minion/pet by 4", healed >= 1 && near(wolf.getHealth(), 8), wolf.getHealth());
		wolf.discard();
		BlockPos lamp = base.offset(8, 5, 0);
		checkS("Glimmer Lantern: light in air, never over a block, max 8 per player", () -> {
			if (!Gear3LightBlocks.place(p, l, lamp) || !l.getBlockState(lamp).is(Blocks.LIGHT)) return false;
			l.setBlock(lamp.above(), Blocks.STONE.defaultBlockState(), 3);
			boolean refused = !Gear3LightBlocks.place(p, l, lamp.above());
			l.setBlock(lamp.above(), Blocks.AIR.defaultBlockState(), 3);
			for (int i = 1; i <= 9; i++) Gear3LightBlocks.place(p, l, lamp.offset(i, 0, 0));
			boolean capped = Gear3LightBlocks.count(p.getUUID()) == 8 && l.getBlockState(lamp).isAir();
			Gear3Scheduler.flush(s);
			return refused && capped;
		});
		check("Glimmer Lantern: expiry timers were queued and cleaned", Gear3Scheduler.pending() == 0);
		check("Glimmer Lantern: expired lights leave no chunk record", Gear3LightBlocks.recorded(l, lamp).isEmpty(), Gear3LightBlocks.recorded(l, lamp));
		// Self-healing after a crash (z-items 3.0.0): lights are recorded in their chunk (persistent attachment, saved with the
		// chunk); a recorded light that is not live in this session is cleared when its chunk loads.
		BlockPos stale = lamp.offset(0, 2, 0), foreign = lamp.offset(2, 2, 0);
		checkS("Glimmer Lantern: each light is recorded in its chunk with a persistent codec (NBT round trip)", () -> {
			if (!Gear3LightBlocks.place(p, l, stale)) return false;
			List<Long> rec = Gear3LightBlocks.recorded(l, stale);
			if (!rec.contains(stale.asLong()) || !Gear3LightBlocks.RECORD.isPersistent()) return false;
			var codec = Gear3LightBlocks.RECORD.persistenceCodec();
			var tag = codec.encodeStart(NbtOps.INSTANCE, rec).getOrThrow();
			return codec.parse(NbtOps.INSTANCE, tag).getOrThrow().equals(rec) && l.getChunkAt(stale).isUnsaved();
		});
		check("Glimmer Lantern: a live light survives its chunk loading again", Gear3LightBlocks.onChunkLoad(l, l.getChunkAt(stale)) == 0
			&& l.getBlockState(stale).is(Blocks.LIGHT));
		checkS("Glimmer Lantern: after a crash, the stale light is cleared when its chunk loads (block and record)", () -> {
			if (!Gear3LightBlocks.place(p, l, foreign)) return false;
			Gear3LightBlocks.forgetSessionForTest(); // the crash: in-memory state gone, light blocks and chunk records stay
			l.setBlock(foreign, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3); // someone put their own light there
			if (!l.getBlockState(stale).is(Blocks.LIGHT) || !Gear3LightBlocks.recorded(l, stale).contains(stale.asLong())) return false;
			int queued = Gear3LightBlocks.onChunkLoad(l, l.getChunkAt(stale));
			Gear3Scheduler.runDueWithin(s, 1);
			return queued == 2 && l.getBlockState(stale).isAir() && Gear3LightBlocks.recorded(l, stale).isEmpty();
		});
		check("Glimmer Lantern: the clean-up never removes a light that isn't a Glimmer Lantern light", l.getBlockState(foreign).is(Blocks.LIGHT)
			&& l.getBlockState(foreign).getValue(LightBlock.LEVEL) == 15);
		l.setBlock(foreign, Blocks.AIR.defaultBlockState(), 3);
		Gear3Scheduler.flush(s); // the forgotten lights' 5-minute timers find nothing to do
		check("Glimmer Lantern: crash test left nothing queued or recorded", Gear3Scheduler.pending() == 0 && Gear3LightBlocks.recorded(l, stale).isEmpty());
		p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
		checkS("Blinkstone: moves the player ~8 blocks along the view", () -> {
			double d = SpellItems.Blinkstone.blink(l, p);
			return d > 7.0 && d <= 8.5;
		});
		p.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
		LivingEntity ember = husk(l, new Vec3(base.getX() + 0.5, base.getY(), base.getZ() + 12.5));
		int hit = 0;
		for (int i = 0; i < 2 && hit == 0; i++) hit = SpellItems.EmberfallRod.burst(l, p, ember.position(), false);
		check("Emberfall Rod: burst hits the mob and sets it on fire", hit >= 1 && ember.isOnFire(), hit);
		LivingEntity frost = husk(l, new Vec3(base.getX() + 0.5, base.getY(), base.getZ() - 8.5));
		float frostTick = SpellItems.FrostwakeScepter.chill(l, p, frost, false);
		float frostWant = dev.elsewhere.arsenal.spell.Spells.damage(p, frost, SpellItems.FROST_DPS, false, false);
		check("Frostwake: every freeze tick is the full spell damage (spell power, Calling, Test Meter half bonus), not a raw 3 (s-compat-testmeter)", Math.abs(frostTick - frostWant) < 1e-3F, frostTick + " vs " + frostWant);
		check("Frostwake: Slowness II and frozen", frost.hasEffect(MobEffects.SLOWNESS) && frost.getEffect(MobEffects.SLOWNESS).getAmplifier() == 1 && frost.getTicksFrozen() > 0);
		check("Frostwake: two more freeze ticks queued", Gear3Scheduler.pending() == 2, Gear3Scheduler.pending());
		Gear3Scheduler.flush(s);
		LivingEntity star = husk(l, new Vec3(base.getX() + 20.5, base.getY(), base.getZ() + 0.5));
		checkS("Starfall Orrery: five stars run without error", () -> SpellItems.StarfallOrrery.stars(l, p, star.position(), false) >= 0);
		for (LivingEntity e : new LivingEntity[] {target, dummy, ember, frost, star}) e.discard();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		SelfTest.info("mechanic blocks done");
	}
}
