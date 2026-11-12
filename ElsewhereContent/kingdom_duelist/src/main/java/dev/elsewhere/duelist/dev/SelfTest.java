package dev.elsewhere.duelist.dev;

import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.DuelistEvents;
import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.boss.TestDuelist;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.Move;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The duelist module self-test: kit moves in a test arena with a dummy husk and fake players. Port 25741, exits when done. */
public final class SelfTest {
	private SelfTest() {}

	/** One logged hit of a boss. */
	public record Hit(HumanBoss boss, LivingEntity victim, float amount, DamageCaps kind, boolean landed) {}

	public static final List<Hit> HITS = new ArrayList<>();
	static LivingEntity arrowTarget;
	static float maxArrowSeen;

	public static void run(MinecraftServer server) {
		H h = new H(server);
		System.out.println("[duelist-selftest] start");
		server.tickRateManager().setTickRate(100F);
		DuelistEvents.HIT.add((b, v, a, k, l) -> HITS.add(new Hit(b, v, a, k, l)));
		Telegraph.audit = new ArrayList<>();
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amt) -> {
			if (e == arrowTarget && src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow) maxArrowSeen = Math.max(maxArrowSeen, amt);
			return true;
		});
		dev.elsewhere.duelist.kit.ShieldHook.reset();
		h.buildArena();
		long baseline = h.arenaHash();
		registration(h);
		hpCap(h);
		hpCapLoad(h);
		SelfTestTestMeter.add(h); // s-compat-testmeter: cap holds after Test Meter's ENTITY_LOAD bonus
		kitK1K2(h);
		kitSwing(h);
		kitShield(h);
		kitSwapRanged(h);
		kitHeal(h);
		kitPearl(h);
		SelfTestBlocks.add(h);
		SelfTestKit.add(h);
		SelfTestAdapt.add(h);
		SelfTestIdentity.add(h);
		SelfTestAnim.add(h);
		SelfTestPose.add(h);
		SelfTestForesight.add(h); // s-foresight B5
		h.test("telegraph audit and arena restored").run(() -> {}).check("every wind-up obeys the telegraph rule (" + Telegraph.audit.size() + " logged)", () -> {
			for (Object[] a : Telegraph.audit) {
				Telegraph.Weight w = (Telegraph.Weight) a[1];
				float raw = (Float) a[2];
				int ticks = (Integer) a[3];
				int min = w == Telegraph.Weight.ORDINARY ? (raw >= 16F ? 8 : 0) : w.minTicks;
				if (ticks < min) {
					h.info("violation " + a[0] + " " + w + " raw " + raw + " ticks " + ticks);
					return false;
				}
			}
			return !Telegraph.audit.isEmpty();
		}).check("no hit exceeded a cap", () -> {
			for (Hit x : HITS) {
				float cap = x.kind().cap(x.boss().profile);
				if (x.kind() != DamageCaps.MAGIC && x.amount() > cap + 0.01F) {
					h.info("over cap: " + x.amount() + " kind " + x.kind());
					return false;
				}
			}
			return true;
		}).check("no duel_block left in the arena", () -> h.countBlocks(DuelistContent.DUEL_BLOCK) == 0 && dev.elsewhere.duelist.blocks.DuelBlocks.total(h.level) == 0)
			.check("arena blocks identical to the start (nothing griefed or left behind)", () -> h.arenaHash() == baseline);
		h.start(() -> {
			System.out.println("[duelist-selftest] DONE " + h.pass + " passed, " + h.fail + " failed");
			for (String f : h.failures) System.out.println("[duelist-selftest] FAILED: " + f);
			if (Boolean.getBoolean("duelist.selftest.exit")) server.halt(false);
		});
	}

	// ---- registration and numbers --------------------------------------------------------------------------------

	static void hpCap(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		h.test("human boss 300 HP cap").run(() -> {
			b[0] = h.boss(H.base("hpcap").hp(250).build(), 0, 0);
			var a = b[0].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
			for (int i = 0; i < 3; i++) a.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(dev.elsewhere.duelist.DuelistContent.id("hpcap_t" + i), 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			a.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(dev.elsewhere.duelist.DuelistContent.id("hpcap_add"), 500, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
			dev.elsewhere.duelist.boss.HumanBossHealth.enforce(b[0]); // the call whoever adds a modifier makes
			b[0].setHealth(b[0].getMaxHealth());
		}).wait(5).check("three +100% modifiers and +500 on a 250 HP boss still read exactly 300", () -> {
			h.info("max " + b[0].getMaxHealth() + " hp " + b[0].getHealth() + " alive " + b[0].isAlive());
			return Math.abs(b[0].getMaxHealth() - 300F) < 0.01F;
		}).check("current health is at or below the max", () -> b[0].getHealth() <= 300.01F)
			.run(() -> b[0].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(dev.elsewhere.duelist.DuelistContent.id("hpcap_t9"), 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)))
			.run(() -> dev.elsewhere.duelist.boss.HumanBossHealth.enforce(b[0]))
			.wait(3).check("another +100% later is clamped too", () -> Math.abs(b[0].getMaxHealth() - 300F) < 0.01F)
			.run(() -> {
				var a = b[0].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
				for (var m : new java.util.ArrayList<>(a.getModifiers())) if (!m.id().equals(dev.elsewhere.duelist.boss.HumanBossHealth.CAP_ID)) a.removeModifier(m.id());
				dev.elsewhere.duelist.boss.HumanBossHealth.enforce(b[0]);
			}).wait(3).check("with the boosts gone the cap modifier goes too and the base 250 shows", () -> Math.abs(b[0].getMaxHealth() - 250F) < 0.01F && b[0].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getModifier(dev.elsewhere.duelist.boss.HumanBossHealth.CAP_ID) == null);
	}

	static void hpCapLoad(H h) {
		final TestDuelist[] b = new TestDuelist[2];
		final var HP = net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH;
		h.test("human boss 300 HP cap on load").run(() -> {
			// what a 2.0 world gives back after a load: base 560 and a saved health, no cap modifier
			for (int i = 0; i < 2; i++) {
				b[i] = h.boss(H.base("hpload" + i).hp(100).build(), 4 * i, 6);
				var a = b[i].getAttribute(HP);
				a.removeModifier(dev.elsewhere.duelist.boss.HumanBossHealth.CAP_ID);
				a.setBaseValue(560);
			}
			b[0].setHealth(560F);
			b[1].setHealth(120F);
			// the load path (readAdditionalSaveData) ends with exactly this call
			dev.elsewhere.duelist.boss.HumanBossHealth.enforce(b[0]);
			dev.elsewhere.duelist.boss.HumanBossHealth.enforce(b[1]);
		}).wait(5).check("a 560 HP boss at full health reads 300 max and 300 health", () -> Math.abs(b[0].getMaxHealth() - 300F) < 0.01F && Math.abs(b[0].getHealth() - 300F) < 0.01F)
			.check("a 560 HP boss at 120 health keeps 120 (never raised)", () -> Math.abs(b[1].getMaxHealth() - 300F) < 0.01F && Math.abs(b[1].getHealth() - 120F) < 0.5F);
	}

	static void registration(H h) {
		h.test("registration and numbers").check("duel_block, projectile and test bosses are registered", () -> DuelistContent.DUEL_BLOCK != null && DuelistContent.PROJECTILE != null && DuelistContent.TEST_DUELIST != null && DuelistContent.TEST_RULER != null)
			.check("telegraph rule rejects a 7-tick move of raw 16, a 10-tick signature and a 15-tick heavy", () -> {
				boolean a = false, b = false, c = false;
				try { Telegraph.check("x", Telegraph.Weight.ORDINARY, 16F, 7); } catch (IllegalArgumentException e) { a = true; }
				try { Telegraph.check("x", Telegraph.Weight.SIGNATURE, 5F, 10); } catch (IllegalArgumentException e) { b = true; }
				try { Telegraph.check("x", Telegraph.Weight.HEAVY, 30F, 15); } catch (IllegalArgumentException e) { c = true; }
				Telegraph.check("ok", Telegraph.Weight.ORDINARY, 15F, 4);
				Telegraph.check("ok", Telegraph.Weight.SIGNATURE, 38F, 14);
				Telegraph.check("ok", Telegraph.Weight.HEAVY, 38F, 20);
				return a && b && c;
			})
			.check("a profile over the caps or with a swing outside 14..18 is rejected", () -> {
				boolean a = false, b = false;
				try { H.base("x").swing(19F, 10, 8).build(); } catch (IllegalArgumentException e) { a = true; }
				try { H.base("y").signature(dev.elsewhere.duelist.boss.SignatureMove.of("big", "Big", Telegraph.Weight.HEAVY, 20, 100, 39F, 0, 5, (bb, t) -> {})).build(); } catch (IllegalArgumentException e) { b = true; }
				H.base("z").ruler().caps(18F, 40F).signature(dev.elsewhere.duelist.boss.SignatureMove.of("big", "Big", Telegraph.Weight.HEAVY, 20, 100, 40F, 0, 5, (bb, t) -> {})).build();
				return a && b;
			})
			.check("intake check: raw 40 on diamond + Prot IV takes at least 3 hits from 20 HP", () -> {
				// vanilla armour formula, armour 20, toughness 8, Protection IV on 4 pieces (EPF 16 -> x0.36)
				double raw = 40, armor = 20, tough = 8;
				double eff = Math.min(20, Math.max(armor / 5, armor - raw / (2 + tough / 4)));
				double after = raw * (1 - eff / 25) * (1 - 16.0 / 25);
				return Math.ceil(20 / after) >= 3;
			});
	}

	// ---- K1 strafe, K2 crit ---------------------------------------------------------------------------------------

	static void kitK1K2(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final Husk[] d = new Husk[1];
		final List<Double> ang = new ArrayList<>();
		final List<Double> dists = new ArrayList<>();
		final int[] still = {0, 0};
		final Vec3[] last = {null};
		final List<Long> flipTicks = new ArrayList<>();
		final int[] flipSeen = {0};
		final int[] facing = {0, 0};
		h.test("K1 strafe and spacing").run(() -> {
			d[0] = h.dummy(0, 4);
			b[0] = h.boss(H.only("k1", Move.STRAFE).build(), 0, 0);
			b[0].getRandom().setSeed(0x6B31L); // fixed flip times: the same run on every machine and under any load
			b[0].setTarget(d[0]);
		}).wait(40).each(300, () -> {
			b[0].setTarget(d[0]);
			if (b[0].stat("strafe_flip") != flipSeen[0]) {
				flipSeen[0] = b[0].stat("strafe_flip");
				flipTicks.add(h.now());
			}
			Vec3 rel = b[0].position().subtract(d[0].position());
			ang.add(Math.atan2(rel.z, rel.x));
			dists.add(Math.sqrt(rel.x * rel.x + rel.z * rel.z));
			// the strafe moves relative to the body yaw, so the boss must face its target while it circles
			float want = (float) (Math.atan2(-rel.z, -rel.x) * (180.0 / Math.PI)) - 90F;
			facing[1]++;
			if (Math.abs(net.minecraft.util.Mth.wrapDegrees(b[0].getYRot() - want)) <= 30F) facing[0]++;
			if (last[0] != null && b[0].position().subtract(last[0]).horizontalDistanceSqr() < 0.0004) still[0]++;
			else still[0] = 0;
			still[1] = Math.max(still[1], still[0]);
			last[0] = b[0].position();
		}).check("faces its target while circling (body yaw within 30 degrees on at least 90% of ticks)", () -> {
			h.info("facing the target on " + facing[0] + " of " + facing[1] + " ticks");
			return facing[1] > 0 && facing[0] * 10 >= facing[1] * 9;
		}).check("keeps 2.5 to 3.5 blocks in melee (mean in 2.4 to 3.8)", () -> {
			double m = dists.stream().mapToDouble(x -> x).average().orElse(0);
			long in = dists.stream().filter(x -> x >= 2.2 && x <= 3.9).count();
			h.info("mean spacing " + String.format("%.2f", m) + ", " + in + " of " + dists.size() + " ticks within 2.2 to 3.9");
			return m > 2.4 && m < 3.8;
		}).check("flips strafe direction several times, each run 16 to 40 ticks of its own numbers", () -> {
			List<Integer> runs = new ArrayList<>();
			int sign = 0, len = 0;
			for (int i = 1; i < ang.size(); i++) {
				double da = ang.get(i) - ang.get(i - 1);
				if (da > Math.PI) da -= 2 * Math.PI;
				if (da < -Math.PI) da += 2 * Math.PI;
				int s = da > 0.004 ? 1 : (da < -0.004 ? -1 : 0);
				if (s == 0) { len++; continue; }
				if (s != sign && sign != 0) { runs.add(len); len = 0; }
				sign = s;
				len++;
			}
			List<Long> gaps = new ArrayList<>();
			for (int i = 1; i < flipTicks.size(); i++) gaps.add(flipTicks.get(i) - flipTicks.get(i - 1));
			h.info("strafe runs seen " + runs + ", ticks between the boss's own flips " + gaps);
			// the boss flips every 16 to 40 ticks of its own numbers (a stand-still flip may add up to 16 + the still limit)
			// a boss pressed against a wall flips early on purpose (stand-still rule), so a short gap is allowed for a minority; the rest must sit in its own 16..40
			long inBand = gaps.stream().filter(g -> g >= 16 && g <= 40).count();
			for (long g : gaps) if (g < 1 || g > 40 + 16 + 10) return false;
			if (inBand * 4 < gaps.size() * 3) return false;
			return runs.size() >= 3 && gaps.size() >= 5;
		}).check("never stands still longer than 10 ticks (longest " + still[1] + ")", () -> still[1] <= 12);

		final List<Long> startTicks = new ArrayList<>();
		final int[] crouch = {0}, stage1 = {0};
		final double[] runDist = {0};
		final Vec3[] runStart = {null};
		final int[] seenHits = {0};
		final boolean[] staggerDone = {false};
		final boolean[] poseOk = {false};
		h.test("K2 sprint-jump crit").run(() -> {
			HITS.clear();
			d[0] = h.dummy(0, 11);
			b[0] = h.boss(H.only("k2", Move.CRIT).build(), 0, 0);
			b[0].setTarget(d[0]);
			startTicks.clear();
		}).until("crit started and crouched 8 ticks", 600, () -> {
			b[0].setTarget(d[0]);
			if (b[0].telegraph.active() && b[0].telegraph.id.equals("crit")) {
				crouch[0]++;
				if (b[0].getPose() == net.minecraft.world.entity.Pose.CROUCHING) poseOk[0] = true;
			}
			if (b[0].critStage() == 1 && runStart[0] == null) runStart[0] = b[0].position();
			if (b[0].critStage() == 1) runDist[0] = Math.max(runDist[0], b[0].position().subtract(runStart[0]).horizontalDistance());
			return b[0].stat("crit_jump") >= 1 || b[0].stat("crit_missed") >= 1;
		}).check("the wind-up is a crouch of at least 8 ticks with the crouch pose (" + crouch[0] + ")", () -> crouch[0] >= 8 && poseOk[0])
			.check("the run-up was at least 3 blocks before the jump", () -> runDist[0] >= 2.9 || b[0].stat("crit_jump") >= 1)
			.until("lands the crit (x1.5 = raw 24)", 200, () -> {
				b[0].setTarget(d[0]);
				for (Hit x : HITS) if (x.boss() == b[0] && x.kind() == DamageCaps.SIGNATURE && Math.abs(x.amount() - 24F) < 0.5F) return true;
				return b[0].stat("crit_missed") >= 1 && b[0].stat("crit_started") >= 2;
			}).check("a crit hit was dealt at raw 24 (16 x 1.5)", () -> HITS.stream().anyMatch(x -> x.boss() == b[0] && x.kind() == DamageCaps.SIGNATURE && Math.abs(x.amount() - 24F) < 0.5F))
			.check("the crit cooldown is this profile's own 80 ticks; the test ruler did not opt in at all", () -> b[0].profile.crit.cooldown() == 80 && TestDuelist.RULER_PROFILE.crit == null);
		final long[] t1 = {0}, t2 = {0};
		h.test("K2 crit repeats no sooner than the cooldown").run(() -> {
			d[0] = h.dummy(0, 11);
			b[0] = h.boss(H.only("k2b", Move.CRIT).build(), 0, 0);
			b[0].setTarget(d[0]);
			t1[0] = t2[0] = 0;
		}).until("two crits started", 1500, () -> {
			b[0].setTarget(d[0]);
			if (t1[0] == 0 && b[0].stat("crit_started") >= 1) t1[0] = h.now();
			if (t2[0] == 0 && b[0].stat("crit_started") >= 2) t2[0] = h.now();
			return t2[0] != 0;
		}).check("second crit started at least 80 ticks after the first (" + (t2[0] - t1[0]) + ")", () -> t2[0] - t1[0] >= 80);
		h.test("K2 a hit on a boss in mid-air staggers it for 16 ticks").run(() -> {
			d[0] = h.dummy(0, 11);
			b[0] = h.boss(H.only("k2c", Move.CRIT).build(), 0, 0);
			b[0].setTarget(d[0]);
			staggerDone[0] = false;
		}).until("boss is airborne in the jump", 800, () -> {
			b[0].setTarget(d[0]);
			return b[0].critStage() == 2 && !b[0].onGround();
		}).run(() -> {
			var fp = h.fake(0, 0);
			b[0].hurtServer(h.level, h.level.damageSources().playerAttack(fp), 2F);
		}).check("staggered", () -> b[0].isStaggered() && b[0].stat("staggered") == 1 && b[0].critStage() == 0)
			.wait(18).check("stagger lasted 16 ticks (ended)", () -> !b[0].isStaggered());
	}

	static void kitSwing(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final Husk[] d = new Husk[1];
		final int[] landedBefore = {0};
		final boolean[] raised = {false};
		final int[] windTicks = {0};
		h.test("swing: telegraphed with a raised arm, and dodgeable by stepping out of reach").run(() -> {
			SelfTest.HITS.clear();
			d[0] = h.dummy(0, 2.6);
			b[0] = h.boss(H.only("sw", Move.STRAFE).build(), 0, 0);
			b[0].setTarget(d[0]);
			windTicks[0] = 0;
			raised[0] = false;
		}).until("a swing wind-up starts", 200, () -> {
			b[0].setTarget(d[0]);
			return b[0].telegraph.active() && b[0].telegraph.id.equals("swing");
		}).run(() -> {
			raised[0] = b[0].armRaised();
			landedBefore[0] = (int) SelfTest.HITS.stream().filter(x -> x.landed()).count();
			d[0].setPos(0, H.Y, 14);
		}).until("the swing fires and misses", 40, () -> b[0].stat("swing") >= 1).check("the weapon arm is raised during the wind-up (synced to the client)", () -> raised[0] || b[0].armRaised())
			.wait(3).check("the target stepped out of reach: no hit landed from that swing", () -> SelfTest.HITS.stream().filter(x -> x.landed()).count() == landedBefore[0]);
	}

	// ---- K3 shield and the axe -----------------------------------------------------------------------------------

	static void kitShield(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final Husk[] att = new Husk[1];
		var arc = h.test("K3 shield raise delay and arc").run(() -> {
			b[0] = h.boss(H.only("k3", Move.SHIELD).build(), 0, 0);
			b[0].passive = true;
			b[0].setYRot(0F);
			b[0].setYHeadRot(0F);
			b[0].yBodyRot = 0F;
			att[0] = h.dummy(0, 4);
			b[0].forceShield(900);
		}).wait(2).check("not blocking 2 ticks after raising (5-tick delay)", () -> !b[0].isBlocking())
			.wait(8).check("blocking after the raise delay", () -> b[0].isBlocking());
		for (int deg : new int[] {0, 45, 59, 61, 90, 150}) {
			final boolean expectBlocked = deg <= 60;
			arc.run(() -> {
				double rad = Math.toRadians(deg);
				// facing south (+z, yaw 0): the attacker stands deg degrees off that line, 3 blocks away
				att[0].setPos(-Math.sin(rad) * 3, H.Y, Math.cos(rad) * 3);
				b[0].damageCooldownTime = 0;
				float before = b[0].getHealth();
				b[0].hurtServer(h.level, h.level.damageSources().mobAttack(att[0]), 4F);
				float lost = before - b[0].getHealth();
				h.check("attack from " + deg + " degrees is " + (expectBlocked ? "blocked" : "not blocked") + " (lost " + lost + ")", expectBlocked ? lost < 0.01F : lost > 3F);
				b[0].forceShield(100);
			}).wait(1);
		}
		final TestDuelist[] a = new TestDuelist[1];
		h.test("K3 an axe disables the boss shield for 100 ticks, a sword and a sprint hit do not").run(() -> {
			b[0] = h.boss(H.only("k3b", Move.SHIELD).build(), 0, 0);
			b[0].passive = true;
			att[0] = h.dummy(0, 3);
			att[0].setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			b[0].forceShield(400);
		}).wait(8).run(() -> {
			b[0].damageCooldownTime = 0;
			b[0].hurtServer(h.level, h.level.damageSources().mobAttack(att[0]), 3F);
		}).check("a sword hit does not disable it", () -> b[0].shieldDisabledTicks() == 0 && b[0].isBlocking())
			.run(() -> {
				att[0].setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
				b[0].damageCooldownTime = 0;
				b[0].hurtServer(h.level, h.level.damageSources().mobAttack(att[0]), 3F);
			}).check("an axe hit disables it for 100 ticks", () -> b[0].shieldDisabledTicks() >= 98 && b[0].shieldDisabledTicks() <= 100 && !b[0].isBlocking())
			.wait(60).check("still disabled after 60 ticks", () -> b[0].shieldDisabledTicks() > 30)
			.wait(45).check("back after about 100 ticks", () -> b[0].shieldDisabledTicks() == 0);
		final HumanBoss[] victim = new HumanBoss[1];
		h.test("K3 bash: raw 8, knockback 2, never disables a shield").run(() -> {
			HITS.clear();
			b[0] = h.boss(H.only("k3c", Move.SHIELD).melee(() -> new ItemStack(Items.DIAMOND_AXE)).build(), 0, 0);
			b[0].passive = true;
			victim[0] = h.boss(H.only("k3d", Move.SHIELD).build(), 0, 2);
			victim[0].passive = true;
			victim[0].setYRot(180F);
			victim[0].setYHeadRot(180F);
			victim[0].yBodyRot = 180F;
			victim[0].forceShield(300);
			b[0].setTarget(victim[0]);
		}).wait(8).run(() -> b[0].bashNow(victim[0])).until("bash lands", 60, () -> b[0].stat("bash") >= 1)
			.check("the bash was raw 8", () -> HITS.stream().anyMatch(x -> x.boss() == b[0] && Math.abs(x.amount() - 8F) < 0.01F))
			.check("a bash with an axe in hand did not disable the blocking shield", () -> victim[0].shieldDisabledTicks() == 0 && victim[0].stat("shield_disabled") == 0);
		final Husk[] tgt = new Husk[1];
		final Vec3[] v0 = {null};
		h.test("K3 bash knockback on an open target").run(() -> {
			b[0] = h.boss(H.only("k3e", Move.SHIELD).build(), 0, 0);
			b[0].passive = true;
			tgt[0] = h.dummy(0, 2);
			tgt[0].setNoAi(false);
			((dev.elsewhere.duelist.mixin.MobAccessor) tgt[0]).elsewhere$goalSelector().removeAllGoals(g -> true);
			((dev.elsewhere.duelist.mixin.MobAccessor) tgt[0]).elsewhere$targetSelector().removeAllGoals(g -> true);
			b[0].setTarget(tgt[0]);
			v0[0] = tgt[0].position();
		}).wait(8).run(() -> b[0].bashNow(tgt[0])).until("bash done", 60, () -> b[0].stat("bash") >= 1).wait(6).check("the target was pushed back", () -> tgt[0].position().subtract(v0[0]).horizontalDistance() > 0.5);
		final long[] dt = new long[2];
		h.test("K3 his axe disables a player shield for 60 ticks, once per 120").run(() -> {
			b[0] = h.boss(H.only("k3f", Move.SHIELD).shieldBreak(TestKit.shieldBreak()).melee(() -> new ItemStack(Items.IRON_AXE)).build(), 0, 0);
			b[0].passive = true;
		}).wait(3).check("first axe block disables for 3 s (60 ticks)", () -> Math.abs(b[0].getSecondsToDisableBlocking() - 3F) < 0.01F)
			.check("a second block right after does not", () -> b[0].getSecondsToDisableBlocking() == 0F)
			.wait(125).check("after 120 ticks it can again", () -> Math.abs(b[0].getSecondsToDisableBlocking() - 3F) < 0.01F);
		// what the loaded lanes set at init (nothing in the duelist alone; the Azmar lane's khopesh numbers in the merged jar),
		// kept so this case can put it back afterwards
		final dev.elsewhere.duelist.kit.ShieldHook.Params[] laneParams = {null};
		final java.util.concurrent.atomic.AtomicReference<java.util.function.Predicate<ItemStack>> laneHook = new java.util.concurrent.atomic.AtomicReference<>();
		h.test("K3 a FakePlayer shield goes on cooldown when hooked, and the khopesh limits hold").run(() -> {
			laneParams[0] = dev.elsewhere.duelist.kit.ShieldHook.params;
			laneHook.set(dev.elsewhere.duelist.kit.ShieldHook.isHook);
			var fp = h.fake(0, 0);
			fp.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			dev.elsewhere.duelist.kit.ShieldHook.disable(h.level, fp, 60);
			h.check("hooked player's shield is on cooldown", fp.getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD)));
			// limits: one boss hooked by attacker A, again by A inside 120 -> no; squad B inside 40 -> no
			dev.elsewhere.duelist.kit.ShieldHook.reset();
			// the duelist kit has no default numbers: the hook is on only where a loaded lane tags a khopesh and sets them
			boolean tagged = net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(dev.elsewhere.duelist.kit.ShieldHook.KHOPESH).iterator().hasNext();
			var lp = laneParams[0];
			h.info("khopesh tag " + (tagged ? "has items" : "is empty") + ", hook numbers at start " + lp);
			h.check("the hook is off until a lane sets its numbers (and on, with sane numbers, once a lane tags a khopesh)",
				tagged ? lp != null && lp.disableTicks() > 0 && lp.attackerCooldown() >= lp.disableTicks() && lp.squadCooldown() > 0 : lp == null);
			dev.elsewhere.duelist.kit.ShieldHook.params = new dev.elsewhere.duelist.kit.ShieldHook.Params(60, 120, 40);
			var hb = h.boss(H.only("k3g", Move.SHIELD).build(), 10, 10);
			hb.passive = true;
			hb.forceShield(500);
			att[0] = h.dummy(10, 14);
			victim[0] = hb;
			dev.elsewhere.duelist.kit.ShieldHook.isHook = s -> s.is(Items.GOLDEN_SWORD);
			att[0].setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
		}).wait(8).run(() -> {
			victim[0].setYRot(0F);
			victim[0].yBodyRot = 0;
			victim[0].setYHeadRot(0F);
			victim[0].damageCooldownTime = 0;
			victim[0].hurtServer(h.level, h.level.damageSources().mobAttack(att[0]), 2F);
		}).check("a khopesh hit on a raised shield hooks it down for 60 ticks", () -> victim[0].shieldDisabledTicks() >= 58 && dev.elsewhere.duelist.kit.ShieldHook.hooks == 1)
			.run(() -> {
				victim[0].disableShield(0);
			}).run(() -> {
				victim[0].damageCooldownTime = 0;
				victim[0].hurtServer(h.level, h.level.damageSources().mobAttack(att[0]), 2F);
			}).check("the same attacker cannot hook again inside 120 ticks", () -> dev.elsewhere.duelist.kit.ShieldHook.hooks == 1)
			.cleanup(() -> {
				// put back what the lanes set: switching the hook off here would leave the merged jar's khopesh hook off
				var hook = laneHook.get();
				dev.elsewhere.duelist.kit.ShieldHook.isHook = hook != null ? hook : s -> s.is(dev.elsewhere.duelist.kit.ShieldHook.KHOPESH);
				dev.elsewhere.duelist.kit.ShieldHook.params = laneParams[0];
				dev.elsewhere.duelist.kit.ShieldHook.reset();
			});
	}

	// ---- K4 swap, K5 bow, K6 crossbow -----------------------------------------------------------------------------

	static void kitSwapRanged(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final Husk[] d = new Husk[1];
		final List<long[]> swaps = new ArrayList<>();
		final HumanBoss.Weapon[] prev = {HumanBoss.Weapon.MELEE};
		final long[] swapStart = {0};
		final int[] lastSwapStat = {0};
		h.test("K4 weapon swap by range").run(() -> {
			d[0] = h.dummy(0, 2.5);
			b[0] = h.boss(H.only("k4", Move.SWAP, Move.BOW).build(), 0, 0);
			b[0].setTarget(d[0]);
			swaps.clear();
		}).each(50, () -> b[0].setTarget(d[0])).check("holds the melee weapon inside 3.5 blocks", () -> b[0].weapon() == HumanBoss.Weapon.MELEE && b[0].getMainHandItem().is(Items.IRON_SWORD))
			.run(() -> d[0].setPos(0, H.Y, 7)).run(() -> swapStart[0] = h.now()).until("swaps to the throwing weapon at 3.5 to 9 blocks", 120, () -> {
				b[0].setTarget(d[0]);
				d[0].setPos(0, H.Y, b[0].getZ() + 7);
				return b[0].weapon() == HumanBoss.Weapon.THROWN && b[0].getMainHandItem().is(Items.TRIDENT);
			}).run(() -> {
				h.info("melee to thrown took " + (h.now() - swapStart[0]) + " ticks from the request");
				swapStart[0] = h.now();
			}).until("swaps to the ranged weapon beyond 9 blocks", 160, () -> {
				b[0].setTarget(d[0]);
				d[0].setPos(0, H.Y, b[0].getZ() + 14);
				return b[0].weapon() == HumanBoss.Weapon.RANGED && b[0].getMainHandItem().is(Items.BOW);
			}).check("at least 40 ticks between swaps (the second swap came " + 0 + ")", () -> h.now() - swapStart[0] >= 36 && b[0].stat("swap") == 2);
		final long[] drawAt = {0}, shotAt = {0}, shot2At = {0};
		final float[] maxArrow = {0};
		final double[] spreadSeen = {0};
		final int[] arrows = {0};
		h.test("K5 bow: 20-tick draw, 30 between shots, raw 14, spread by range").run(() -> {
			HITS.clear();
			d[0] = h.dummy(0, 16);
			b[0] = h.boss(H.only("k5", Move.SWAP, Move.BOW).build(), 0, 0);
			b[0].setTarget(d[0]);
			b[0].setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			drawAt[0] = shotAt[0] = shot2At[0] = 0;
		}).until("draws and shoots twice", 700, () -> {
			b[0].setTarget(d[0]);
			if (b[0].weapon() == HumanBoss.Weapon.MELEE && b[0].getZ() > 4) d[0].setPos(0, H.Y, 16);
			if (drawAt[0] == 0 && b[0].stat("draw") >= 1) drawAt[0] = h.now();
			if (shotAt[0] == 0 && b[0].stat("arrow") >= 1) {
				shotAt[0] = h.now();
				spreadSeen[0] = b[0].lastSpread;
			}
			if (shot2At[0] == 0 && b[0].stat("arrow") >= 2) shot2At[0] = h.now();
			return shot2At[0] != 0;
		}).check("the draw took at least 20 ticks (" + (shotAt[0] - drawAt[0]) + ")", () -> shotAt[0] - drawAt[0] >= 19)
			.check("at least 30 ticks between shots (" + (shot2At[0] - shotAt[0]) + ")", () -> shot2At[0] - shotAt[0] >= 30)
			.check("spread at 14 to 16 blocks is 3.0 (" + spreadSeen[0] + ")", () -> Math.abs(spreadSeen[0] - TestKit.bow().spreadAt(16)) < 0.5 && spreadSeen[0] >= 3.0);
		h.test("K5 an arrow deals raw 14 at most").run(() -> {
			d[0] = h.dummy(0, 12);
			arrowTarget = d[0];
			b[0] = h.boss(H.only("k5b", Move.SWAP, Move.BOW).build(), 0, 0);
			b[0].setTarget(d[0]);
			maxArrowSeen = 0;
		}).until("an arrow hits the target", 900, () -> {
			b[0].setTarget(d[0]);
			d[0].setPos(0, H.Y, b[0].getZ() + 12);
			return maxArrowSeen > 0;
		}).check("arrow damage was 12 to 14", () -> {
			h.info("arrow damage " + maxArrowSeen);
			return maxArrowSeen >= 12F && maxArrowSeen <= 14.01F;
		});
		final long[] loadAt = {0}, xShot = {0};
		h.test("K6 crossbow: 25 load, flat spread 1.5").run(() -> {
			d[0] = h.dummy(0, 16);
			DuelistProfile p = H.only("k6", Move.SWAP, Move.CROSSBOW).ranged(() -> new ItemStack(Items.CROSSBOW)).build();
			b[0] = h.boss(p, 0, 0);
			b[0].setTarget(d[0]);
			loadAt[0] = xShot[0] = 0;
		}).until("loads and fires", 900, () -> {
			b[0].setTarget(d[0]);
			if (b[0].weapon() == HumanBoss.Weapon.MELEE && b[0].getZ() > 4) d[0].setPos(0, H.Y, 16);
			if (loadAt[0] == 0 && b[0].stat("draw") >= 1) loadAt[0] = h.now();
			if (xShot[0] == 0 && b[0].stat("arrow") >= 1) xShot[0] = h.now();
			return xShot[0] != 0;
		}).check("at least 25 ticks from loading to the shot (" + (xShot[0] - loadAt[0]) + ")", () -> xShot[0] - loadAt[0] >= 25)
			.check("spread 1.5", () -> b[0].lastSpread == 1.5);
	}

	// ---- K7 eat, K8 drink -----------------------------------------------------------------------------------------

	static void kitHeal(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final Husk[] d = new Husk[1];
		final float[] hp0 = {0};
		final long[] lockStart = {0};
		h.test("K7/K8 a hit during the 32 ticks cancels, the use is not spent, 120-tick lockout").run(() -> {
			d[0] = h.dummy(0, 12);
			b[0] = h.boss(H.only("k8", Move.DRINK).build(), 0, 0);
			b[0].setTarget(d[0]);
			b[0].setHealth(b[0].getMaxHealth() * 0.45F);
		}).until("starts drinking (nobody within 4 blocks)", 100, () -> {
			b[0].setTarget(d[0]);
			return b[0].isHealing();
		}).check("holds a visible potion", () -> b[0].getMainHandItem().is(Items.POTION)).wait(10).run(() -> {
			hp0[0] = b[0].getHealth();
			var fp = h.fake(0, 0);
			b[0].damageCooldownTime = 0;
			b[0].hurtServer(h.level, h.level.damageSources().playerAttack(fp), 3F);
			lockStart[0] = h.now();
		}).check("the hit cancelled the drink", () -> !b[0].isHealing() && b[0].stat("heal_interrupted") == 1)
			.check("the use was not spent", () -> b[0].healUses() == 0)
			.check("the weapon is back in hand", () -> b[0].getMainHandItem().is(Items.IRON_SWORD))
			.each(100, () -> b[0].setTarget(d[0])).check("no new try within the 120-tick lockout", () -> b[0].stat("heal_started") == 1)
			.until("tries again after the lockout", 80, () -> {
				b[0].setTarget(d[0]);
				return b[0].stat("heal_started") >= 2;
			}).check("it restarted no sooner than 120 ticks (" + 0 + ")", () -> h.now() - lockStart[0] >= 118);
		final float[] before = {0, 0};
		h.test("K8 drink heals 15 percent at once, then Eat and Drink share a cap of 2").run(() -> {
			d[0] = h.dummy(0, 12);
			b[0] = h.boss(H.only("k8b", Move.DRINK).build(), 0, 0);
			b[0].setTarget(d[0]);
			b[0].setHealth(b[0].getMaxHealth() * 0.30F);
		}).until("first drink completes", 120, () -> {
			b[0].setTarget(d[0]);
			before[1] = before[0];
			before[0] = b[0].getHealth();
			return b[0].healUses() == 1;
		}).check("it healed about 15 percent", () -> {
			h.info("drink: hp " + before[1] + " -> " + before[0] + " max " + b[0].getMaxHealth());
			return before[0] - before[1] >= b[0].getMaxHealth() * 0.14F;
		})
			.run(() -> b[0].setHealth(b[0].getMaxHealth() * 0.30F)).until("second drink completes", 200, () -> {
				b[0].setTarget(d[0]);
				return b[0].healUses() == 2;
			}).run(() -> b[0].setHealth(b[0].getMaxHealth() * 0.30F))
			.each(200, () -> b[0].setTarget(d[0])).check("no third use (cap 2 per fight)", () -> b[0].healUses() == 2 && b[0].stat("heal_started") == 2);
		final float[] e0 = {0};
		h.test("K7 eat heals 10 percent over 60 ticks").run(() -> {
			d[0] = h.dummy(0, 12);
			b[0] = h.boss(H.only("k7", Move.EAT).build(), 0, 0);
			b[0].setTarget(d[0]);
			b[0].setHealth(b[0].getMaxHealth() * 0.40F);
		}).until("eating completes", 120, () -> {
			b[0].setTarget(d[0]);
			if (b[0].isHealing()) e0[0] = b[0].getHealth();
			return b[0].healUses() == 1;
		}).check("holds food while eating (visible)", () -> {
			h.info("eat stats " + b[0].stats + " hand " + b[0].getMainHandItem());
			return b[0].stat("heal_eat") >= 1;
		}).check("not healed instantly", () -> b[0].getHealth() - e0[0] < b[0].getMaxHealth() * 0.05F)
			.wait(65).check("10 percent after 60 ticks", () -> b[0].getHealth() - e0[0] >= b[0].getMaxHealth() * 0.09F);
		final double[] hd = {0};
		h.test("K7/K8 he backs off first when a target is within 4 blocks").run(() -> {
			d[0] = h.dummy(0, 2);
			b[0] = h.boss(H.only("k8c", Move.DRINK).build(), 0, 0);
			b[0].setTarget(d[0]);
			b[0].setHealth(b[0].getMaxHealth() * 0.40F);
			hd[0] = 0;
		}).until("starts the drink", 200, () -> {
			b[0].setTarget(d[0]);
			if (b[0].isHealing() && hd[0] == 0) hd[0] = b[0].distanceTo(d[0]);
			return b[0].isHealing();
		}).check("he backed off (heal_backoff " + 0 + ")", () -> b[0].stat("heal_backoff") > 0).check("the drink began 4 or more blocks from the target (" + 0 + ")", () -> hd[0] >= 3.9);
	}

	// ---- K9 pearl -------------------------------------------------------------------------------------------------

	static void kitPearl(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final Husk[] d = new Husk[1];
		final float[] hp = {0};
		h.test("K9 pearl hop across a chasm: lands within 4 blocks, takes 2, cooldown 600").run(() -> {
			carveChasm(h, -10, 10);
			d[0] = h.dummy(16, 0);
			b[0] = h.boss(H.only("k9", Move.PEARL).build(), -14, 0);
			b[0].setTarget(d[0]);
			hp[0] = b[0].getHealth();
		}).until("hops after 60 ticks of being unable to reach", 300, () -> {
			b[0].setTarget(d[0]);
			return b[0].pearlsUsed() == 1;
		}).run(() -> h.info("pearls " + b[0].pearlsUsed())).wait(2)
			.check("landed within 4 blocks of the target (" + 0 + ")", () -> b[0].position().subtract(d[0].position()).horizontalDistance() <= 4.6)
			.check("not inside a block and not in a fluid", () -> h.level.noCollision(b[0]) && !b[0].isInWater())
			.check("took 2 raw fall damage", () -> Math.abs((hp[0] - b[0].getHealth()) - 2F) < 0.6F)
			.check("cooldown of 600 ticks", () -> b[0].pearlCooldownLeft() >= 570)
			.cleanup(() -> h.flatten(-12, 12, -H.R, H.R));
		h.test("K9 never hops inside 20 blocks").run(() -> {
			carveChasm(h, -6, 6);
			d[0] = h.dummy(8, 0);
			b[0] = h.boss(H.only("k9b", Move.PEARL).build(), -10, 0);
			b[0].setTarget(d[0]);
		}).each(260, () -> b[0].setTarget(d[0])).check("no pearl at 18 blocks (" + 0 + ")", () -> b[0].pearlsUsed() == 0).cleanup(() -> h.flatten(-12, 12, -H.R, H.R));
		h.test("K9 max 3 per fight and never leaves the 48-block arena").run(() -> {
			carveChasm(h, -10, 10);
			d[0] = h.dummy(16, 0);
			b[0] = h.boss(H.only("k9c", Move.PEARL).build(), -14, 0);
			b[0].setTarget(d[0]);
			b[0].debugSetPearls(3);
			b[0].debugClearPearlCooldown();
		}).each(220, () -> b[0].setTarget(d[0])).check("no fourth pearl", () -> b[0].pearlsUsed() == 3)
			.run(() -> {
				b[0].debugSetPearls(0);
				b[0].debugClearPearlCooldown();
				b[0].setHome(new BlockPos(-14, H.Y, 0));
				b[0].arenaRadius = 6;
			}).each(220, () -> b[0].setTarget(d[0])).check("no hop that would land outside the arena radius", () -> b[0].pearlsUsed() == 0)
			.cleanup(() -> h.flatten(-12, 12, -H.R, H.R));
	}

	static void carveChasm(H h, int x0, int x1) {
		for (int x = x0; x <= x1; x++) for (int z = -H.R; z <= H.R; z++) h.level.setBlock(new BlockPos(x, H.Y - 1, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
	}
}
