package dev.elsewhere.rivals.azmar.dev;

import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.TRIED;
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

import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.identity.BossIdentity;
import dev.elsewhere.duelist.identity.BossIdentityAudit;
import dev.elsewhere.rivals.azmar.AzmarFalcon;
import dev.elsewhere.rivals.azmar.AzmarItems;
import dev.elsewhere.rivals.azmar.boss.AzmarBoss;
import dev.elsewhere.rivals.azmar.boss.AzmarBosses;
import dev.elsewhere.rivals.azmar.boss.AzmarFalconer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Falconer fight tests (card A3): audit, high ground, Perch Post and Rest, Falcon Mark, Triple Stoop, Intercept, Dust Dodge, Falcon Storm, cleanup. */
final class SelfTestFalconer {
	private SelfTestFalconer() {}

	static AzmarFalconer f;
	static long baseHash;
	static Vec3 perch;
	static Projectile arrow;

	static AzmarFalconer fresh(ServerLevel l) {
		if (SelfTestBosses.boss != null && SelfTestBosses.boss.isAlive()) SelfTestBosses.boss.discard();
		SelfTest.clean(l);
		SelfTestBosses.fp = player(l, 0, 150 + 12);
		SelfTestBosses.boss = spawn(l, AzmarBosses.FALCONER, 0, 150);
		SelfTestBosses.boss.setTarget(SelfTestBosses.fp);
		f = (AzmarFalconer) SelfTestBosses.boss;
		return f;
	}

	static void platform(ServerLevel l, boolean on) {
		for (int x = 5; x <= 9; x++) for (int z = 147; z <= 153; z++) for (int h = 0; h < 6; h++)
			l.setBlock(new BlockPos(x, y() + h, z), on ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
	}

	static Projectile shoot(ServerLevel l) {
		Projectile p = (Projectile) (Object) EntityTypes.ARROW.create(l, EntitySpawnReason.COMMAND);
		p.setOwner(SelfTestBosses.fp);
		Vec3 from = f.position().add(7, 1.2, 0);
		p.setPos(from.x, from.y, from.z);
		p.setDeltaMovement(f.position().add(0, 1.0, 0).subtract(from).normalize().scale(1.2));
		l.addFreshEntity(p);
		return p;
	}

	static void add() {
		run((srv, l) -> {
			SelfTestBosses.LV = l;
			baseHash = hash(l, 0, 150, 40);
			fresh(l);
			AzmarBoss.clearMarks();
		});
		each(30, l -> { keepTarget(); face(SelfTestBosses.fp, SelfTestBosses.boss.position()); });
		sync("Falconer: 171 HP lord (cap 300 rule), Falcon Helm worn, composite bow shown", () -> f.getMaxHealth() == 171F && f.getItemBySlot(EquipmentSlot.HEAD).is(AzmarItems.FALCON_HELM));
		sync("Falconer card vs profile audit is clean (generic temp_blocks, 3 signatures, telegraph firework #F2E3A0 swoop 1.6)", () -> {
			BossIdentity card = BossIdentity.get("azmar_falconer");
			return card != null && BossIdentityAudit.checkProfile(card, AzmarFalconer.PROFILE).isEmpty();
		});
		sync("Falconer has a real custom falcon (azmar_falcon, not a parrot) with 20 HP", () -> f.falconAlive() && f.falconMob().getMaxHealth() == 20F && f.falconMob().getType() == AzmarFalcon.TYPE);
		// high ground: a 6-high stone rise 5 to 9 blocks from the arena centre with a clear line to the player
		run((srv, l) -> { pass(f, true); platform(l, true); perch = f.findPerch(l, SelfTestBosses.fp); });
		sync("High-Ground Kite: she picks the rise (6 blocks up) over the flat", () -> perch != null && perch.y >= y() + 5.9 && perch.x >= 4.5 && perch.x <= 10.5);
		run((srv, l) -> { platform(l, false); perch = null; });
		sync("High-Ground Kite: with no rise there is no perch goal", () -> f.findPerch(SelfTestBosses.LV, SelfTestBosses.fp) == null);
		// Perch Post
		run((srv, l) -> { SelfTestBosses.fp.snapTo(0.5, y(), 150.5 + 12, 0F, 0F); f.setHealth(f.getMaxHealth() * 0.5F); f.fireSignature(AzmarFalconer.PERCH_POST, SelfTestBosses.fp); });
		each(70, l -> keepTarget());
		sync("Perch Post: a 2 to 5 high soft temporary pillar on the ledger, she stands on top", () -> {
			int n = DuelBlocks.count(SelfTestBosses.LV, f.getUUID());
			return f.posts >= 1 && n >= 2 && n <= 5 && f.postTop != null && f.getY() >= f.postTop.getY() + 0.9;
		});
		each(330, l -> keepTarget());
		sync("Perch Rest: on the post with no damage she regained 1 percent per 20 ticks, at most 15 percent", () -> f.restedPercent >= 13.5F && f.restedPercent <= 16.2F && f.getHealth() / f.getMaxHealth() <= 0.5F + 0.17F);
		run((srv, l) -> f.discard());
		each(3, l -> {});
		sync("Perch Post cleaned up on her removal: no ledger blocks left", () -> {
			boolean ok = DuelBlocks.total(SelfTestBosses.LV) == 0 && hash(SelfTestBosses.LV, 0, 150, 40) == baseHash;
			if (!ok) SelfTest.info("falconer ledger blocks " + DuelBlocks.total(SelfTestBosses.LV) + ", vs the Wazir-start snapshot: " + SelfTestWazir.changed(SelfTestBosses.LV));
			return ok;
		});
		// Falcon Mark
		run((srv, l) -> { fresh(l); pass(f, true); SelfTestBosses.fp.removeAllEffects(); face(SelfTestBosses.fp, f.position()); f.fireSignature(AzmarFalconer.FALCON_MARK, SelfTestBosses.fp); });
		each(30, l -> face(SelfTestBosses.fp, f.position()));
		sync("Falcon Mark: the falcon dives, Glowing 8 s, the court reads at once (isMarked), next arrow +25 percent ready", () ->
			f.marks >= 1 && SelfTestBosses.fp.hasEffect(MobEffects.GLOWING) && AzmarBoss.isMarked(SelfTestBosses.fp.getUUID(), f.level().getGameTime()) && f.arrowBonusReady);
		// Triple Stoop: 3 then 5
		run((srv, l) -> { SelfTestBosses.fp.snapTo(f.getX(), y(), f.getZ() + 14, 0F, 0F); f.fireSignature(AzmarFalconer.TRIPLE_STOOP, SelfTestBosses.fp); });
		each(50, l -> {});
		sync("Triple Stoop: three arrows in phase 1", () -> f.stoops == 1 && f.lastStoop == 3 && f.arrows == 3);
		run((srv, l) -> f.setHealth(f.getMaxHealth() * 0.45F));
		each(8, l -> keepTarget());
		sync("Falconer P2 at 50 percent", () -> f.phaseIndex() == 1);
		run((srv, l) -> f.fireSignature(AzmarFalconer.TRIPLE_STOOP, SelfTestBosses.fp));
		each(60, l -> {});
		sync("Triple Stoop: five arrows in phase 2", () -> f.stoops == 2 && f.lastStoop == 5 && f.arrows == 8);
		// Falcon Intercept: first projectile taken, then 160 ticks of cooldown; no falcon, no intercept
		run((srv, l) -> { while (f.falconMob() != null) f.falconMob().discard(); });
		run((srv, l) -> { f.setCooldown("intercept", 0); arrow = shoot(l); });
		each(2, l -> {});
		sync("Falcon Intercept: with the falcon down, the arrow is not intercepted", () -> f.intercepts == 0);
		run((srv, l) -> { arrow.discard(); fresh(l); pass(f, true); });
		each(20, l -> keepTarget());
		run((srv, l) -> { arrow = shoot(l); });
		each(2, l -> {});
		sync("Falcon Intercept: the falcon takes the first arrow aimed at her", () -> f.intercepts == 1 && arrow.isRemoved());
		run((srv, l) -> { arrow = shoot(l); });
		each(2, l -> {});
		sync("Falcon Intercept: not again inside 160 ticks", () -> f.intercepts == 1);
		run((srv, l) -> { if (arrow != null) arrow.discard(); });
		// Dust Dodge
		run((srv, l) -> { pass(f, false); SelfTestBosses.fp.snapTo(f.getX() + 1.5, y(), f.getZ(), 0F, 0F); f.dodges = 0; });
		each(60, l -> { keepTarget(); if (f.dodges == 0 && f.distanceTo(SelfTestBosses.fp) > 3.0) SelfTestBosses.fp.snapTo(f.getX() + 1.6, y(), f.getZ(), 0F, 0F); });
		sync("Dust Dodge: one 5-block backward dodge when closed on (once per 123 ticks)", () -> f.dodges == 1);
		// Falcon Storm at 20 percent, once
		run((srv, l) -> { pass(f, false); SelfTestBosses.fp.snapTo(f.getX(), y(), f.getZ() + 8, 0F, 0F); f.setHealth(f.getMaxHealth() * 0.19F); });
		poll("Falcon Storm: four falcons dive in turn", 400, () -> { keepTarget(); face(SelfTestBosses.fp, f.position()); return f.stormSpawned == 4; });
		each(40, l -> keepTarget());
		sync("Falcon Storm fired once with four 10-HP one-hit falcons", () -> f.stormsDone == 1 && f.stormSpawned == 4 && f.stormFalcons.size() == 4 && f.stormFalcons.stream().allMatch(x -> x.getMaxHealth() == 10F && x.storm));
		run((srv, l) -> f.setHealth(f.getMaxHealth() * 0.10F));
		each(60, l -> keepTarget());
		sync("Falcon Storm does not repeat", () -> f.stormsDone == 1 && f.stormSpawned == 4);
		sync("Falconer tried hits stay inside the Lord caps (raw 14 arrows, 8 storm dives)", () -> TRIED.getOrDefault("azmar_falconer|MINOR", 0F) <= 18.01F && TRIED.getOrDefault("azmar_falconer|SIGNATURE", 0F) <= 38.01F);
		run((srv, l) -> f.discard());
		each(5, l -> {});
		sync("Her falcons (own and storm) are gone with her", () -> !f.falconAlive() && f.stormFalcons.isEmpty()
			&& SelfTestBosses.LV.getEntitiesOfClass(AzmarFalcon.class, new AABB(-60, y() - 10, 90, 60, y() + 60, 210)).stream().noneMatch(x -> x.isAlive()));
		run((srv, l) -> SelfTest.clean(l));
		sync("no block griefing and no ledger blocks after the Falconer fight", () -> {
			boolean ok = DuelBlocks.total(SelfTestBosses.LV) == 0 && hash(SelfTestBosses.LV, 0, 150, 40) == baseHash;
			if (!ok) SelfTest.info("falconer ledger blocks " + DuelBlocks.total(SelfTestBosses.LV) + ", vs the Wazir-start snapshot: " + SelfTestWazir.changed(SelfTestBosses.LV));
			return ok;
		});
	}
}
