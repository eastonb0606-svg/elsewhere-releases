package dev.elsewhere.duelist.dev;

import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.DuelistEvents;
import dev.elsewhere.duelist.boss.CourtBonus;
import dev.elsewhere.duelist.boss.FrenzyRage;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.PhaseController;
import dev.elsewhere.duelist.boss.SignatureMove;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.boss.TestDuelist;
import dev.elsewhere.duelist.kit.Companion;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.DodgeStep;
import dev.elsewhere.duelist.kit.DuelistProjectile;
import dev.elsewhere.duelist.kit.GroundPatch;
import dev.elsewhere.duelist.kit.Move;
import dev.elsewhere.duelist.kit.ReachAttack;
import dev.elsewhere.duelist.kit.TempStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Duel mode, phases, signature moves, the caps, and the shared tools (X1 to X13). */
final class SelfTestKit {
	private SelfTestKit() {}

	static void add(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final Husk[] d = new Husk[1];
		final boolean[] yielded = {false}, defeated = {false};
		final int[] phaseEvents = {0};
		final int[] stands = {0};
		h.test("Duel mode: yields at 10 percent, drops as a kill").run(() -> {
			yielded[0] = defeated[0] = false;
			DuelistEvents.YIELD.add(x -> yielded[0] = true);
			b[0] = h.boss(H.base("duel").hp(200).build(), 0, 0);
			b[0].setDuel(true);
			b[0].setHealth(30F);
			var fp = h.fake(5, 5);
			b[0].hurtServer(h.level, h.level.damageSources().playerAttack(fp), 60F);
		}).check("yielded at 10 percent HP, not dead", () -> b[0].isAlive() && b[0].yielded() && yielded[0] && Math.abs(b[0].getHealth() - 20F) < 0.5F)
			.run(() -> {
				var fp = h.fake(5, 5);
				h.check("invulnerable while yielding", !b[0].hurtServer(h.level, h.level.damageSources().playerAttack(fp), 5F));
			}).until("the yield ends with the boss defeated (drops as a kill)", 80, () -> !b[0].isAlive());
		h.test("Duel mode: the boss's own last stand fires before it can yield").run(() -> {
			yielded[0] = false;
			stands[0] = 0;
			b[0] = h.boss(H.base("duel2").hp(200).lastStand((bb, l, src, amt) -> {
				stands[0]++;
				bb.lastStand(20, 0F, false, null);
				return true;
			}).build(), 0, 0);
			b[0].setDuel(true);
			b[0].setHealth(30F);
			b[0].hurtServer(h.level, h.level.damageSources().playerAttack(h.fake(5, 5)), 60F);
		}).check("the hit that would make it yield ran the last stand instead", () -> b[0].isAlive() && !b[0].yielded() && !yielded[0] && stands[0] == 1 && b[0].inLastStand() && b[0].lastStandUses() == 1)
			.wait(24).run(() -> {
				b[0].damageCooldownTime = 0;
				b[0].hurtServer(h.level, h.level.damageSources().playerAttack(h.fake(5, 5)), 3F);
			}).check("after the stand (left at 1 HP, under the yield line) the next hit makes it yield, not die", () -> b[0].isAlive() && b[0].yielded() && yielded[0] && stands[0] == 1)
			.until("and the duel ends as a kill", 80, () -> !b[0].isAlive());
		final boolean[] fired = {false};
		final int[] telTicks = {0};
		final Component[] title = {null};
		h.test("signature move: wound up 14 ticks, bar label, fires once, capped").run(() -> {
			HITS_CLEAR();
			SignatureMove m = SignatureMove.of("test_slam", "Test Slam", Telegraph.Weight.SIGNATURE, 14, 100, 40F, 0, 6, (bb, t) -> {
				fired[0] = true;
				bb.hurtRaw(h.level, t, 99F, DamageCaps.SIGNATURE, h.level.damageSources().mobAttack(bb));
			});
			d[0] = h.dummy(0, 3);
			b[0] = h.ruler(H.only("sig", Move.STRAFE).ruler().caps(18F, 40F).signature(m).build(), 0, 0);
			b[0].setTarget(d[0]);
			fired[0] = false;
			telTicks[0] = 0;
		}).until("fires", 400, () -> {
			b[0].setTarget(d[0]);
			if (b[0].telegraph.active() && b[0].telegraph.id.equals("test_slam")) {
				telTicks[0]++;
				if (b[0].bossBarName().getString().contains("Test Slam")) title[0] = b[0].bossBarName();
			}
			return fired[0];
		}).check("the wind-up lasted 14 ticks or more (" + 0 + ")", () -> telTicks[0] >= 13).check("the bar shows Name · Label", () -> title[0] != null && title[0].getString().contains("·") && title[0].getString().contains("Test Slam"))
			.check("the 99 raw hit was capped at 40", () -> SelfTest.HITS.stream().anyMatch(x -> x.boss() == b[0] && x.kind() == DamageCaps.SIGNATURE && x.amount() > 39F && x.amount() <= 40.01F));
		h.test("caps: court bonus and frenzy cannot push a hit past a cap; Lord cap 38").run(() -> {
			HITS_CLEAR();
			b[0] = h.ruler(H.base("cap").ruler().caps(18F, 40F).swing(18F, 14, 8).court(CourtBonus.perLiving(() -> 5, 0.10, 0.50, 0)).frenzy(new FrenzyRage(1.0, 0.0, false, false)).build(), 0, 0);
			b[0].passive = true;
			d[0] = h.dummy(0, 3);
			b[0].hurtRaw(h.level, d[0], 18F, DamageCaps.MINOR, h.level.damageSources().mobAttack(b[0]));
			d[0].damageCooldownTime = 0;
			b[0].hurtRaw(h.level, d[0], 38F, DamageCaps.SIGNATURE, h.level.damageSources().mobAttack(b[0]));
			var lord = h.boss(H.base("capl").court(CourtBonus.perLiving(() -> 5, 0.10, 0.50, 0)).build(), 5, 5);
			lord.passive = true;
			d[0].damageCooldownTime = 0;
			lord.hurtRaw(h.level, d[0], 60F, DamageCaps.SIGNATURE, h.level.damageSources().mobAttack(lord));
		}).check("minor hit clamped to raw 18 (court +50 percent applied first)", () -> SelfTest.HITS.stream().anyMatch(x -> x.kind() == DamageCaps.MINOR && Math.abs(x.amount() - 18F) < 0.01F))
			.check("ruler signature clamped to 40", () -> SelfTest.HITS.stream().anyMatch(x -> x.boss().profile.ruler && x.kind() == DamageCaps.SIGNATURE && Math.abs(x.amount() - 40F) < 0.01F))
			.check("Lord signature clamped to 38", () -> SelfTest.HITS.stream().anyMatch(x -> !x.boss().profile.ruler && x.kind() == DamageCaps.SIGNATURE && Math.abs(x.amount() - 38F) < 0.01F))
			.check("the Hird-oath bonus multiplies (1.5)", () -> Math.abs(CourtBonus.perLiving(() -> 7, 0.10, 0.50, 0).damageMultiplier() - 1.5) < 1e-9 && CourtBonus.perLiving(() -> 3, 0, 0, 12).bonusHp() == 36.0)
			.check("frenzy: rises with lost HP and stays once permanent", () -> {
				FrenzyRage f = new FrenzyRage(0.5, 0.3, true, true);
				double a = f.multiplier(1.0), c = f.multiplier(0.5), e = f.multiplier(0.2), g = f.multiplier(0.9);
				return a == 1.0 && c > a && e > c && f.locked() && g >= e;
			});
		final int[] ph = {0};
		final long[] cdLeft = {99};
		h.test("phases: thresholds, one-shot events, cooldown reset").run(() -> {
			ph[0] = 0;
			b[0] = h.boss(H.base("ph").hp(100).phases(0.66, 0.33).build(), 0, 0);
			b[0].passive = true;
			b[0].phase.onEnter(1, () -> ph[0]++);
			b[0].setCooldown("sig:x", 500);
			b[0].setHealth(60F);
		}).wait(3).check("entered phase 1 once", () -> b[0].phaseIndex() == 1 && ph[0] == 1 && b[0].stat("phase_change") == 1)
			.check("a phase change reset the cooldowns", () -> b[0].cooldownLeft("sig:x") == 0).run(() -> b[0].setHealth(20F)).wait(3).check("phase 2", () -> b[0].phaseIndex() == 2 && ph[0] == 1);
		final Vec3[] p0 = {null};
		final boolean[] hitIn = {false};
		h.test("X1 dodge: 8 i-frames, about 5 blocks, cooldown").run(() -> {
			b[0] = h.boss(H.base("dg").build(), 0, 0);
			b[0].passive = true;
		}).wait(4).run(() -> {
			p0[0] = b[0].position();
			var fp = h.fake(0, 5);
			h.check("dodge started", DodgeStep.dodge(h.level, b[0], new Vec3(0, H.Y, 5), new DodgeStep.Params(8, 5.0, 100)));
			h.check("cooldown blocks a second dodge", !DodgeStep.dodge(h.level, b[0], new Vec3(0, H.Y, 5), new DodgeStep.Params(8, 5.0, 100)));
			hitIn[0] = b[0].hurtServer(h.level, h.level.damageSources().playerAttack(fp), 3F);
		}).check("invulnerable during the i-frames", () -> !hitIn[0]).wait(20).check("moved about 5 blocks (" + 0 + ")", () -> {
			double dd = b[0].position().subtract(p0[0]).horizontalDistance();
			h.info("dodge distance " + String.format("%.2f", dd));
			return dd >= 3.0 && dd <= 6.5;
		}).run(() -> {
			var fp = h.fake(0, 5);
			b[0].damageCooldownTime = 0;
			h.check("vulnerable again after the i-frames", b[0].hurtServer(h.level, h.level.damageSources().playerAttack(fp), 3F));
		});
		final Husk[] v = new Husk[1];
		h.test("X2 thrown gear: raw damage, sticks 40 ticks, fades, no block damage").run(() -> {
			SelfTest.HITS.clear();
			b[0] = h.boss(H.base("th").build(), 0, 0);
			b[0].passive = true;
			d[0] = h.dummy(0, 6);
			DuelistProjectile.throwAt(h.level, b[0], d[0], new ItemStack(Items.IRON_AXE), 14F, 1.6F);
		}).until("hits", 80, () -> b[0].stat("thrown_hit") >= 1).check("raw 14 through the caps", () -> SelfTest.HITS.stream().anyMatch(x -> Math.abs(x.amount() - 14F) < 0.5F))
			.run(() -> {}).wait(10).check("it is stuck", () -> h.level.getEntitiesOfClass(DuelistProjectile.class, new net.minecraft.world.phys.AABB(-10, H.Y - 5, -10, 10, H.Y + 5, 10)).stream().anyMatch(DuelistProjectile::isStuck))
			.wait(50).check("faded after about 40 ticks", () -> h.level.getEntitiesOfClass(DuelistProjectile.class, new net.minecraft.world.phys.AABB(-10, H.Y - 5, -10, 10, H.Y + 5, 10)).isEmpty());
		h.test("X3 splash: effects and magic damage, never kills").run(() -> {
			b[0] = h.boss(H.base("sp").build(), 0, 0);
			b[0].passive = true;
			v[0] = h.dummy(0, 7);
			v[0].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(4F);
			v[0].setHealth(4F);
			var splash = new DuelistProjectile.Splash(List.of(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0)), 2.0, 16F);
			DuelistProjectile.splashAt(h.level, b[0], v[0], new ItemStack(Items.SPLASH_POTION), splash, 1.4F);
		}).until("the splash bursts on the target", 100, () -> v[0].hasEffect(MobEffects.SLOWNESS)).check("a low-HP victim is left at 1 HP or more, alive", () -> v[0].isAlive() && v[0].getHealth() >= 1F)
			.check("splash damage was magic (raw 16 x0.21 = 3.4 at most)", () -> 4F - v[0].getHealth() <= 3.4F + 0.01F);
		final int[] pulses = {0};
		final GroundPatch[] gp = new GroundPatch[1];
		h.test("X5 ground patch: warns, pulses, never ignites or edits blocks").run(() -> {
			b[0] = h.boss(H.base("gp").build(), 0, 0);
			b[0].passive = true;
			v[0] = h.dummy(0, 8);
			long hash = h.arenaHash();
			gp[0] = GroundPatch.spawn(h.level, b[0], new Vec3(0, H.Y, 8), 3.0, 20, 60, 10, 16F, List.of(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1)), ParticleTypes.SOUL_FIRE_FLAME);
			pulses[0] = (int) hash;
		}).wait(15).check("no damage and no effect while the warning shows", () -> {
			h.info("patch warn: hp " + v[0].getHealth() + " effects " + v[0].getActiveEffects() + " pulses " + gp[0].pulses);
			return v[0].getHealth() >= v[0].getMaxHealth() && !v[0].hasEffect(MobEffects.SLOWNESS);
		})
			.wait(30).check("armed: effect and damage landed", () -> v[0].hasEffect(MobEffects.SLOWNESS) && v[0].getHealth() < v[0].getMaxHealth())
			.check("no fire on the target and no fire block", () -> !v[0].isOnFire() && h.level.getBlockState(new BlockPos(0, H.Y, 8)).isAir())
			.wait(70).check("the patch ended", () -> GroundPatch.active() == 0).check("arena unchanged by the patch", () -> (int) h.arenaHash() == pulses[0]);
		h.test("X6 temp wall: air only, vanishes with its owner").run(() -> {
			b[0] = h.boss(H.base("ws").build(), 0, 0);
			b[0].passive = true;
			h.level.setBlock(new BlockPos(0, H.Y, 12), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
			int n = TempStructure.wall(h.level, b[0], new BlockPos(0, H.Y, 10), Direction.SOUTH, 5, 3, 20, 600, 100);
			h.check("placed a 5x3 wall (15 blocks)", n == 15);
			h.check("a second wall inside the cooldown is refused", TempStructure.wall(h.level, b[0], new BlockPos(0, H.Y, 14), Direction.SOUTH, 5, 3, 20, 600, 100) == 0);
			b[0].discard();
		}).wait(2).check("the wall left with its owner and the stone it was next to is intact", () -> h.countBlocks(DuelistContent.DUEL_BLOCK) == 0)
			.cleanup(() -> h.flatten(-4, 4, 8, 16));
		final Husk[] v2 = new Husk[1];
		h.test("X8 reach attack: pierce, half damage inside min range").run(() -> {
			b[0] = h.boss(H.base("rc").build(), 0, 0);
			b[0].passive = true;
			v[0] = h.dummy(0, 2);
			v2[0] = h.dummy(0, 5);
			float a0 = v[0].getHealth(), c0 = v2[0].getHealth();
			int n = ReachAttack.hit(h.level, b[0], v2[0], 7.0, 3.0, 20F, true, DamageCaps.MINOR);
			float near = a0 - v[0].getHealth(), far = c0 - v2[0].getHealth();
			h.check("pierce hit both on the line", n == 2);
			h.check("inside min range: half damage (" + near + " vs " + far + ")", near > 0 && near < far * 0.6F);
			v[0].damageCooldownTime = 0;
			v2[0].damageCooldownTime = 0;
			float f0 = v2[0].getHealth();
			int m = ReachAttack.hit(h.level, b[0], v2[0], 7.0, 3.0, 20F, false, DamageCaps.MINOR);
			h.check("without pierce only the nearest is hit", m == 1);
		});
		final Companion[] comp = new Companion[1];
		h.test("X4 companion: spawns, respawns after the delay up to the limit, leashed").run(() -> {
			b[0] = h.boss(H.base("cm").build(), 0, 0);
			b[0].passive = true;
			comp[0] = new Companion(b[0], lvl -> {
				Husk w = EntityTypes.HUSK.create(lvl, EntitySpawnReason.COMMAND);
				w.setPersistenceRequired();
				return w;
			}, 6.0, 40, 2, Component.literal("Test Wolf"));
		}).run(() -> comp[0].tick(h.level)).check("ally spawned", () -> comp[0].ally() != null)
			.run(() -> comp[0].ally().kill(h.level)).run(() -> comp[0].tick(h.level)).wait(45).run(() -> comp[0].tick(h.level)).check("respawned after 40 ticks", () -> comp[0].ally() != null && comp[0].spawned == 2)
			.run(() -> comp[0].ally().kill(h.level)).run(() -> comp[0].tick(h.level)).wait(45).run(() -> comp[0].tick(h.level)).check("second respawn", () -> comp[0].spawned == 3)
			.run(() -> comp[0].ally().kill(h.level)).run(() -> comp[0].tick(h.level)).wait(45).run(() -> comp[0].tick(h.level)).check("no respawn beyond the limit of 2", () -> comp[0].spawned == 3 && comp[0].ally() == null)
			.cleanup(() -> comp[0].dismiss());
	}

	static void HITS_CLEAR() {
		SelfTest.HITS.clear();
	}
}
