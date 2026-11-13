package dev.elsewhere.duelist.dev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.boss.Behaviours;
import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.SignatureMove;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.boss.TelegraphStyle;
import dev.elsewhere.duelist.boss.TestDuelist;
import dev.elsewhere.duelist.identity.BossIdentity;
import dev.elsewhere.duelist.identity.BossIdentityAudit;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.Move;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** 0.2.0: opt-in (a boss with nothing opted in does none of it), per-boss numbers, the lethal hook, the identity loader and the sameness audit. */
final class SelfTestIdentity {
	private SelfTestIdentity() {}

	private static final String[] OPT_IN_STATS = {"strafe_flip", "crit_started", "crit_jump", "shield_raised", "bash", "blocked", "swap", "draw", "arrow", "throw",
		"heal_started", "heal_backoff", "heal_done", "pearl", "block", "dodge", "last_stand", "player_shield_disabled"};

	static void add(H h) {
		optIn(h);
		numbers(h);
		lethal(h);
		audit(h);
	}

	// ---- opt-in ---------------------------------------------------------------------------------------------------

	private static void optIn(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final Husk[] d = new Husk[1];
		final boolean[] bad = {false};
		final String[] why = {""};
		final long[] hash = {0};
		Runnable watch = () -> {
			b[0].setTarget(d[0]);
			HumanBoss x = b[0];
			if (x.tickCount < 3) return;
			String w = null;
			if (x.shieldUp()) w = "shield up";
			else if (!x.getOffhandItem().isEmpty()) w = "holds " + x.getOffhandItem();
			else if (x.weapon() != HumanBoss.Weapon.MELEE) w = "weapon " + x.weapon();
			else if (!x.getMainHandItem().is(Items.IRON_SWORD)) w = "main hand " + x.getMainHandItem();
			else if (x.isHealing() || x.isUsingItem()) w = "using an item";
			else if (x.getPose() == Pose.CROUCHING) w = "crouched";
			else if (x.critStage() != 0) w = "crit stage";
			else if (x.inLastStand()) w = "last stand";
			else if (x.isSprinting()) w = "sprinting";
			else if (DuelBlocks.count(h.level, x.getUUID()) != 0) w = "placed blocks";
			if (w != null && !bad[0]) {
				bad[0] = true;
				why[0] = w;
			}
		};
		h.test("opt-in: a boss with nothing opted in does none of the player-style behaviours").run(() -> {
			DuelistProfile p = H.base("plain").hp(400).build();
			h.check("the profile opted into nothing", p.opted().isEmpty() && p.genericIds().isEmpty() && p.lastStand == null && p.strafe == null && p.crit == null && p.shield == null
				&& p.swap == null && p.bow == null && p.crossbow == null && p.eat == null && p.drink == null && p.pearl == null && p.blocks == null && p.shieldBreak == null);
			hash[0] = h.arenaHash();
			bad[0] = false;
			d[0] = h.dummy(0, 5);
			b[0] = h.boss(p, 0, 0);
		}).each(260, watch).check("in melee it walked up and swung (it does fight)", () -> b[0].stat("swing") >= 5 && b[0].stat("hits") >= 3)
			.run(() -> {
				// hurt: an eater or drinker would back off and heal now
				b[0].setHealth(b[0].getMaxHealth() * 0.25F);
			}).each(200, watch).check("at 25 percent HP it neither ate nor drank and its HP did not go up", () -> b[0].getHealth() <= b[0].getMaxHealth() * 0.25F + 0.01F)
			.run(() -> {
				// far: a swapper would take a thrown weapon or a bow; a sprint-critter would run up
				d[0].setPos(0, H.Y, 22);
				b[0].setPos(0, H.Y, 0);
			}).each(120, watch)
			.run(() -> {
				// unreachable, 3 up on a tower close by, then across a chasm: a builder would pillar or bridge, a pearler would hop
				for (int y = 0; y < 3; y++) h.level.setBlock(new BlockPos(6, H.Y + y, 0), Blocks.STONE.defaultBlockState(), 2);
				d[0].setPos(6.5, H.Y + 3, 0.5);
				b[0].setPos(3, H.Y, 0.5);
			}).each(160, watch)
			.run(() -> {
				h.flatten(4, 8, -2, 2);
				for (int x = 6; x <= 9; x++) for (int z = -30; z <= 30; z++) for (int y = H.Y - 5; y < H.Y; y++) h.level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				d[0].setPos(32, H.Y, 0.5);
				b[0].setPos(2, H.Y, 0.5);
			}).each(260, watch)
			.check("it never raised a shield, swapped, drew, healed, crouched, sprinted or built (" + why[0] + ")", () -> {
				if (bad[0]) h.info("opt-in broken: " + why[0]);
				return !bad[0];
			}).check("none of the behaviour counters moved", () -> {
				for (String s : OPT_IN_STATS) if (b[0].stat(s) != 0) {
					h.info("counter " + s + " = " + b[0].stat(s) + " in " + b[0].stats);
					return false;
				}
				return true;
			}).check("it did not cross the chasm or leave the ground", () -> b[0].getX() < 6.0 && b[0].pearlsUsed() == 0 && b[0].healUses() == 0)
			.check("its axe-less, opt-out weapon never disables a player shield", () -> b[0].getSecondsToDisableBlocking() == 0F)
			.run(() -> {
				b[0].setEnabled(Move.SHIELD, true);
				b[0].forceShield(40);
				b[0].bashNow(d[0]);
			}).wait(12).check("setEnabled, forceShield and bashNow cannot turn on what the profile did not opt into", () -> !b[0].on(Move.SHIELD) && !b[0].shieldUp() && b[0].stat("bash") == 0 && !b[0].telegraph.active())
			.run(() -> b[0].hurtServer(h.level, h.level.damageSources().playerAttack(h.fake(0, 3)), 5000F))
			.wait(2).check("with no last stand it dies at lethal damage (there is no Totem)", () -> !b[0].isAlive())
			.cleanup(() -> h.flatten(2, 12, -32, 32));
		h.test("opt-in: an axe only breaks shields with shieldBreak; a profile needs every number").run(() -> {
			TestDuelist axe = h.boss(H.base("axe").melee(() -> new ItemStack(Items.IRON_AXE)).build(), 0, 0);
			axe.passive = true;
			b[0] = axe;
		}).wait(3).check("an axe boss without shieldBreak does not disable a shield", () -> b[0].getMainHandItem().is(Items.IRON_AXE) && b[0].getSecondsToDisableBlocking() == 0F)
			.check("a profile with nothing set is rejected and names every missing number", () -> {
				try {
					DuelistProfile.builder("empty").build();
					return false;
				} catch (IllegalArgumentException e) {
					String m = e.getMessage();
					h.info(m);
					for (String need : new String[] {"telegraph", "hp", "walkSpeed", "swing", "reach", "melee", "phases", "caps"}) if (!m.contains(need)) return false;
					return true;
				}
			}).check("no telegraph look is ever assumed: a profile without one is rejected", () -> {
				try {
					DuelistProfile.builder("nolook").hp(100).walkSpeed(0.3).swing(10F, 14, 4).reach(3).phases().caps(18F, 38F).melee(() -> new ItemStack(Items.IRON_SWORD)).build();
					return false;
				} catch (IllegalArgumentException e) {
					return e.getMessage().contains("telegraph");
				}
			}).check("eat without food, bow without swap, caps over the rails are rejected", () -> {
				int n = 0;
				try { H.base("e1").eat(TestKit.eat()).build(); } catch (IllegalArgumentException e) { n++; }
				try { H.base("e2").bow(TestKit.bow()).ranged(() -> new ItemStack(Items.BOW)).build(); } catch (IllegalArgumentException e) { n++; }
				try { H.base("e3").caps(19F, 38F).build(); } catch (IllegalArgumentException e) { n++; }
				try { H.base("e4").caps(18F, 39F).build(); } catch (IllegalArgumentException e) { n++; }
				try { TelegraphStyle.fromJson(JsonParser.parseString("{\"particle\":\"flame\",\"colour\":\"#FF0000\"}").getAsJsonObject(), TelegraphStyle.Shape.HALO); } catch (IllegalArgumentException e) { n++; }
				try { TelegraphStyle.of("not_a_particle", 0xFF0000, "block.bell.use", TelegraphStyle.Shape.HALO, 1F); } catch (IllegalArgumentException e) { n++; }
				return n == 6;
			});
	}

	// ---- per-boss numbers -----------------------------------------------------------------------------------------

	private static void numbers(H h) {
		final TestDuelist[] a = new TestDuelist[1], c = new TestDuelist[1];
		final Husk[] da = new Husk[1], dc = new Husk[1];
		final int[] totalA = {0}, totalC = {0};
		final List<long[]> swingsA = new ArrayList<>(), swingsC = new ArrayList<>();
		TelegraphStyle lookA = new TelegraphStyle(ParticleTypes.FLAME, 0xFF6A1A, SoundEvents.BLAZE_SHOOT, 0.9F, TelegraphStyle.Shape.SPIRAL);
		TelegraphStyle lookC = new TelegraphStyle(ParticleTypes.SNOWFLAKE, 0x66CCFF, SoundEvents.BELL_BLOCK, 1.4F, TelegraphStyle.Shape.RING);
		h.test("per-boss numbers: two bosses, two sets of numbers, two looks").run(() -> {
			SelfTestKit.HITS_CLEAR();
			DuelistProfile pa = DuelistProfile.builder("num_a").hp(300).walkSpeed(0.30).swing(16F, 14, 8).reach(3.0).phases(0.66, 0.33).caps(18F, 38F)
				.melee(() -> new ItemStack(Items.IRON_SWORD)).telegraph(lookA)
				.sprintCrit(new Behaviours.SprintCrit(3.0, 8, 80, 16, 1.5F))
				.shield(new Behaviours.Shield(5, 12, 30, 16, 120F, 100, null)).build();
			DuelistProfile pc = DuelistProfile.builder("num_c").hp(140).walkSpeed(0.22).swing(9F, 30, 16).reach(4.0).phases(0.5).caps(10F, 20F)
				.melee(() -> new ItemStack(Items.STONE_SWORD)).telegraph(lookC)
				.sprintCrit(new Behaviours.SprintCrit(5.0, 12, 200, 30, 2.0F))
				.shield(new Behaviours.Shield(12, 40, 60, 40, 70F, 30, new Behaviours.Bash(14, 200, 5F, 1F))).build();
			da[0] = h.dummy(-10, 2.5);
			dc[0] = h.dummy(10, 2.5);
			a[0] = h.boss(pa, -10, 0);
			c[0] = h.boss(pc, 10, 0);
			a[0].setEnabled(Move.CRIT, false);
			c[0].setEnabled(Move.CRIT, false);
			a[0].setEnabled(Move.SHIELD, false);
			c[0].setEnabled(Move.SHIELD, false);
		}).each(200, () -> {
			a[0].setTarget(da[0]);
			c[0].setTarget(dc[0]);
			if (a[0].telegraph.active() && a[0].telegraph.id.equals("swing")) {
				totalA[0] = a[0].telegraph.total;
				if (a[0].telegraph.left == a[0].telegraph.total - 1) swingsA.add(new long[] {h.now()});
			}
			if (c[0].telegraph.active() && c[0].telegraph.id.equals("swing")) {
				totalC[0] = c[0].telegraph.total;
				if (c[0].telegraph.left == c[0].telegraph.total - 1) swingsC.add(new long[] {h.now()});
			}
		}).check("hp, speed and phases are each boss's own", () -> a[0].getMaxHealth() == 300F && c[0].getMaxHealth() == 140F && a[0].phase.phases() == 3 && c[0].phase.phases() == 2
				&& Math.abs(a[0].getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) - 300) < 0.01)
			.check("swing wind-up 8 ticks for one, 16 for the other (" + 0 + ")", () -> {
				h.info("wind-up totals " + totalA[0] + " / " + totalC[0] + ", swings " + swingsA.size() + " / " + swingsC.size());
				return totalA[0] == 8 && totalC[0] == 16;
			}).check("swing raw 16 for one, 9 for the other", () -> {
				boolean okA = false, okC = false;
				for (SelfTest.Hit x : SelfTest.HITS) {
					if (x.boss() == a[0] && x.kind() == DamageCaps.MINOR) okA = Math.abs(x.amount() - 16F) < 0.01F;
					if (x.boss() == c[0] && x.kind() == DamageCaps.MINOR) okC = Math.abs(x.amount() - 9F) < 0.01F;
				}
				return okA && okC;
			}).check("the faster swinger swung clearly more often", () -> swingsA.size() >= swingsC.size() + 3 && swingsC.size() >= 2)
			.check("swings are at least each boss's own interval apart", () -> {
				for (int i = 1; i < swingsA.size(); i++) if (swingsA.get(i)[0] - swingsA.get(i - 1)[0] < 14) return false;
				for (int i = 1; i < swingsC.size(); i++) if (swingsC.get(i)[0] - swingsC.get(i - 1)[0] < 30) return false;
				return true;
			}).run(() -> {
				SelfTestKit.HITS_CLEAR();
				a[0].passive = c[0].passive = true;
				da[0].damageCooldownTime = 0;
				dc[0].damageCooldownTime = 0;
				a[0].strike(h.level, da[0], true);
				c[0].strike(h.level, dc[0], true);
			}).check("crit multiplier x1.5 (raw 24) for one, x2.0 (raw 18) for the other", () -> {
				boolean okA = false, okC = false;
				for (SelfTest.Hit x : SelfTest.HITS) {
					if (x.boss() == a[0]) okA = x.kind() == DamageCaps.SIGNATURE && Math.abs(x.amount() - 24F) < 0.01F;
					if (x.boss() == c[0]) okC = x.kind() == DamageCaps.SIGNATURE && Math.abs(x.amount() - 18F) < 0.01F;
				}
				return okA && okC;
			}).run(() -> {
				SelfTestKit.HITS_CLEAR();
				da[0].damageCooldownTime = 0;
				dc[0].damageCooldownTime = 0;
				a[0].hurtRaw(h.level, da[0], 99F, DamageCaps.MINOR, h.level.damageSources().mobAttack(a[0]));
				c[0].hurtRaw(h.level, dc[0], 99F, DamageCaps.MINOR, h.level.damageSources().mobAttack(c[0]));
				da[0].damageCooldownTime = 0;
				dc[0].damageCooldownTime = 0;
				a[0].hurtRaw(h.level, da[0], 99F, DamageCaps.SIGNATURE, h.level.damageSources().mobAttack(a[0]));
				c[0].hurtRaw(h.level, dc[0], 99F, DamageCaps.SIGNATURE, h.level.damageSources().mobAttack(c[0]));
			}).check("caps 18/38 for one, 10/20 for the other", () -> {
				Set<String> got = new HashSet<>();
				for (SelfTest.Hit x : SelfTest.HITS) got.add((x.boss() == a[0] ? "a" : "c") + x.kind() + Math.round(x.amount()));
				h.info("caps seen " + got);
				return got.equals(Set.of("aMINOR18", "aSIGNATURE38", "cMINOR10", "cSIGNATURE20"));
			}).check("each shield stack carries its own arc and raise delay", () -> {
				var sa = a[0].getItemBySlot(EquipmentSlot.OFFHAND).get(DataComponents.BLOCKS_ATTACKS);
				var sc = c[0].getItemBySlot(EquipmentSlot.OFFHAND).get(DataComponents.BLOCKS_ATTACKS);
				return sa != null && sc != null && Math.abs(sa.blockDelaySeconds() - 0.25F) < 1e-4 && Math.abs(sc.blockDelaySeconds() - 0.6F) < 1e-4
					&& sa.damageReductions().get(0).horizontalBlockingAngle() == 60F && sc.damageReductions().get(0).horizontalBlockingAngle() == 35F;
			}).run(() -> {
				a[0].startTelegraph("t", "T", 10, Telegraph.Weight.ORDINARY, 0F, a[0].style().cue(false, true), true, () -> {});
				c[0].startTelegraph("t", "T", 10, Telegraph.Weight.ORDINARY, 0F, c[0].style().cue(false, true), true, () -> {});
			}).check("each wind-up carries its own boss's particle, colour, sound, pitch and shape", () -> {
				TelegraphStyle sa = a[0].telegraph.cue().style(), sc = c[0].telegraph.cue().style();
				return sa == lookA && sc == lookC && sa.particle() != sc.particle() && sa.colour() != sc.colour() && sa.sound() != sc.sound() && sa.pitch() != sc.pitch() && sa.shape() != sc.shape();
			}).check("the two shipped test duelists are configured differently", () -> {
				DuelistProfile p = TestDuelist.PROFILE, r = TestDuelist.RULER_PROFILE;
				return !p.opted().equals(r.opted()) && p.style.particle() != r.style.particle() && p.style.colour() != r.style.colour() && p.style.sound() != r.style.sound()
					&& p.style.shape() != r.style.shape() && p.swingWindup != r.swingWindup && p.maxHp != r.maxHp;
			});
	}

	// ---- the lethal hook ------------------------------------------------------------------------------------------

	private static void lethal(H h) {
		final TestDuelist[] b = new TestDuelist[1];
		final int[] calls = {0}, after = {0}, phaseEvents = {0};
		final float[] seenAmount = {0};
		h.test("lethal hook: a ruler's own last stand (lock, heal, last phase, once)").run(() -> {
			calls[0] = after[0] = phaseEvents[0] = 0;
			DuelistProfile p = H.base("ls").ruler().caps(18F, 40F).hp(200).lastStand((boss, level, src, amt) -> {
				calls[0]++;
				seenAmount[0] = amt;
				boss.lastStand(30, 0.25F, true, () -> after[0]++);
				return true;
			}).build();
			b[0] = h.ruler(p, 0, 0);
			b[0].passive = true;
			b[0].setHealth(30F);
			b[0].phase.onEnter(2, () -> phaseEvents[0]++);
		}).wait(2).run(() -> b[0].hurtServer(h.level, h.level.damageSources().playerAttack(h.fake(5, 5)), 500F))
			.check("lethal damage ran the hook and did not kill", () -> b[0].isAlive() && calls[0] == 1 && b[0].inLastStand() && b[0].lastStandUses() == 1 && b[0].phase.hasLastStand() && b[0].stat("last_stand") == 1)
			.check("it holds no Totem and popped none", () -> !b[0].getMainHandItem().is(Items.TOTEM_OF_UNDYING) && !b[0].getOffhandItem().is(Items.TOTEM_OF_UNDYING) && b[0].getHealth() <= 1.01F)
			.run(() -> h.check("invulnerable during the last-stand window", !b[0].hurtServer(h.level, h.level.damageSources().playerAttack(h.fake(5, 5)), 50F)))
			.wait(33).check("after the 30-tick window: 25 percent HP, the after-callback ran once", () -> !b[0].inLastStand() && Math.abs(b[0].getHealth() - 50F) < 1F && after[0] == 1)
			.check("it forced the last phase (once)", () -> b[0].phaseIndex() == 2 && phaseEvents[0] == 1)
			.run(() -> b[0].hurtServer(h.level, h.level.damageSources().playerAttack(h.fake(5, 5)), 500F))
			.wait(2).check("the second lethal hit kills it: the hook is not asked again (max 1 use)", () -> !b[0].isAlive() && calls[0] == 1);
		h.test("lethal hook: declined, twice-allowed, set on the controller").run(() -> {
			calls[0] = 0;
			b[0] = h.ruler(H.base("ls2").ruler().caps(18F, 40F).hp(100).lastStand((boss, level, src, amt) -> {
				calls[0]++;
				return false;
			}).build(), 0, 0);
			b[0].passive = true;
		}).wait(2).run(() -> b[0].hurtServer(h.level, h.level.damageSources().playerAttack(h.fake(5, 5)), 500F))
			.wait(2).check("a hook that returns false lets the boss die", () -> !b[0].isAlive() && calls[0] == 1)
			.run(() -> {
				calls[0] = 0;
				b[0] = h.ruler(H.base("ls3").ruler().caps(18F, 40F).hp(100).lastStand((boss, level, src, amt) -> {
					calls[0]++;
					boss.setHealth(10F);
					return true;
				}, 2).build(), 6, 0);
				b[0].passive = true;
			}).wait(2).run(() -> b[0].hurtServer(h.level, h.level.damageSources().playerAttack(h.fake(5, 5)), 500F))
			.wait(12).run(() -> {
				b[0].damageCooldownTime = 0;
				b[0].hurtServer(h.level, h.level.damageSources().playerAttack(h.fake(5, 5)), 500F);
			}).wait(2).check("maxUses 2: it survived two lethal hits at the hook's own 10 HP", () -> b[0].isAlive() && calls[0] == 2 && Math.abs(b[0].getHealth() - 10F) < 0.01F && b[0].lastStandUses() == 2)
			.wait(12).run(() -> {
				b[0].damageCooldownTime = 0;
				b[0].hurtServer(h.level, h.level.damageSources().playerAttack(h.fake(5, 5)), 500F);
			}).wait(2).check("the third kills it", () -> !b[0].isAlive() && calls[0] == 2)
			.run(() -> {
				calls[0] = 0;
				b[0] = h.boss(H.base("ls4").hp(100).build(), -6, 0);
				b[0].passive = true;
				b[0].phase.onLethalDamage((boss, level, src, amt) -> {
					calls[0]++;
					boss.setHealth(5F);
					return true;
				});
			}).wait(2).run(() -> b[0].hurtServer(h.level, h.level.damageSources().playerAttack(h.fake(5, 5)), 500F))
			.wait(2).check("a hook set with phase.onLethalDamage on one boss works the same", () -> b[0].isAlive() && calls[0] == 1 && Math.abs(b[0].getHealth() - 5F) < 0.01F);
	}

	// ---- identity cards and the sameness audit --------------------------------------------------------------------

	private static Map<String, String> mutate(Map<String, String> raw, String key, Consumer<JsonObject> f) {
		Map<String, String> m = new TreeMap<>(raw);
		JsonObject o = JsonParser.parseString(m.get(key)).getAsJsonObject();
		f.accept(o);
		m.put(key, o.toString());
		return m;
	}

	private static void audit(H h) {
		h.test("boss_identity: the 15 designer cards load and pass the sameness audit").run(() -> {
			Map<String, String> raw = BossIdentity.allRaw();
			h.check("15 cards loaded from data/elsewhere/boss_identity (" + BossIdentity.all().size() + ")", BossIdentity.all().size() == 15 && raw.size() == 15 && raw.keySet().equals(new HashSet<>(BossIdentityAudit.EXPECTED)));
			List<String> problems = BossIdentityAudit.check(BossIdentity.all());
			for (String p : problems) h.info("audit: " + p);
			h.check("BossIdentityAudit.check passes on the 15 real cards", problems.isEmpty());
			h.check("the raw-file audit agrees", BossIdentityAudit.checkRaw(raw).isEmpty());
			BossIdentity amir = BossIdentity.get("azmar_amir"), ames = BossIdentity.get("elsewhere:mirewick_ames"), chief = BossIdentity.get("hrafnholt_chief");
			h.check("a card reads exactly as the designer wrote it", amir != null && amir.id().equals("elsewhere:azmar_amir") && amir.kingdom().equals("azmar") && amir.slots().size() == 6
				&& amir.slots().get("reach").equals("camel_charge_14") && amir.generic().isEmpty() && amir.signature().equals(List.of("camel_charge", "javelin_volley", "dismount_lunge"))
				&& amir.telegraph().particle().equals("dust_plume") && amir.telegraph().rgb() == 0xB0362B && amir.telegraph().sound().equals("entity.camel.dash") && amir.telegraph().pitch() == 1.0F
				&& amir.lastStand().equals("final_charge") && amir.chairLastStand() == null && !amir.idle().isEmpty());
			h.check("Magnate and champion extras are read", ames != null && "lantern_rally".equals(ames.chairLastStand()) && ames.generic().equals(List.of("drink", "pearl")) && chief != null && !chief.risenKeeps().isEmpty());
			int styles = 0;
			Set<String> looks = new HashSet<>();
			for (BossIdentity c : BossIdentity.all()) {
				try {
					TelegraphStyle s = c.style(TelegraphStyle.Shape.HALO);
					if (s.colour() == c.telegraph().rgb() && s.pitch() == c.telegraph().pitch() && (c.telegraph().sound().contains(":") ? s.sound().location().toString() : s.sound().location().getPath()).equals(c.telegraph().sound())) styles++;
					looks.add(s.particle().getType() + "|" + s.colour() + "|" + s.sound().location());
				} catch (RuntimeException e) {
					h.info("style of " + c.key() + ": " + e);
				}
			}
			h.check("every card's telegraph becomes a render style and no two are alike (" + styles + ")", styles == 15 && looks.size() == 15);
			h.check("limits as counted by the Python tool: pearl 1, shield 3, eat 3, drink 3", count("pearl") == 1 && count("shield_block") == 3 && count("eat") == 3 && count("drink") == 3 && count("circle_strafe") == 2 && count("sprint_jump_crit") == 2 && count("temp_blocks") == 2);
		});
		h.test("boss_identity: a broken set fails the audit").run(() -> {
			Map<String, String> raw = BossIdentity.allRaw();
			java.util.function.BiConsumer<String, Map<String, String>> fails = (label, set) -> {
				List<String> p = BossIdentityAudit.checkRaw(set);
				if (!p.isEmpty()) h.info(label + " -> " + p.get(0));
				h.check(label + " fails", !p.isEmpty());
			};
			JsonObject amir = JsonParser.parseString(raw.get("azmar_amir")).getAsJsonObject();
			fails.accept("three shared slots", mutate(raw, "azmar_wazir", o -> {
				for (String s : new String[] {"weapons", "defence", "recovery"}) o.getAsJsonObject("slots").add(s, amir.getAsJsonObject("slots").get(s));
			}));
			h.check("exactly two shared slots still passes", BossIdentityAudit.checkRaw(mutate(raw, "azmar_wazir", o -> {
				for (String s : new String[] {"weapons", "defence"}) o.getAsJsonObject("slots").add(s, amir.getAsJsonObject("slots").get(s));
			})).isEmpty());
			fails.accept("a 4th eater", mutate(raw, "azmar_amir", o -> o.getAsJsonArray("generic").add("eat")));
			fails.accept("a 4th shield user", mutate(raw, "azmar_amir", o -> o.getAsJsonArray("generic").add("shield_block")));
			fails.accept("a 2nd pearl user", mutate(raw, "azmar_amir", o -> o.getAsJsonArray("generic").add("pearl")));
			fails.accept("a Totem last stand", mutate(raw, "hrafnholt_jarl", o -> o.addProperty("last_stand", "totem_of_undying")));
			fails.accept("a Totem mentioned anywhere", mutate(raw, "azmar_sultana", o -> o.addProperty("idle", "She lifts a golden Totem.")));
			fails.accept("a telegraph without a sound", mutate(raw, "azmar_amir", o -> o.getAsJsonObject("telegraph").remove("sound")));
			fails.accept("two bosses with one telegraph sound", mutate(raw, "azmar_amir", o -> o.getAsJsonObject("telegraph").add("sound", JsonParser.parseString(raw.get("azmar_wazir")).getAsJsonObject().getAsJsonObject("telegraph").get("sound"))));
			fails.accept("a shared signature move", mutate(raw, "azmar_amir", o -> o.getAsJsonArray("signature").set(0, JsonParser.parseString(raw.get("azmar_wazir")).getAsJsonObject().getAsJsonArray("signature").get(0))));
			Map<String, String> missing = new TreeMap<>(raw);
			missing.remove("mirewick_ames");
			fails.accept("a missing card", missing);
			h.check("a kingdom's own cards pass the limits-only check", BossIdentityAudit.checkPresent(BossIdentity.all().stream().filter(c -> c.kingdom().equals("azmar")).toList()).isEmpty()
				&& !BossIdentityAudit.check(BossIdentity.all().stream().filter(c -> c.kingdom().equals("azmar")).toList()).isEmpty());
		});
		h.test("boss_identity: same verdict as _tools/boss_audit.py on every fixture set").run(() -> {
			// the lane run points at audit_fixtures/; where only src/ travels (the merged jar) the packed copy in the jar is used
			String dir = System.getProperty("duelist.auditFixtures", "");
			JsonObject packed = packedFixtures();
			Path root = dir.isEmpty() ? null : Path.of(dir);
			Path tmp = null;
			if (root != null && Files.isDirectory(root)) {
				String diff = packed == null ? "no packed copy in the jar" : packedDiff(packed, root);
				if (diff != null) h.info("packed fixtures differ from " + root + ": " + diff + " (run tools/pack_audit_fixtures.py)");
				h.check("the packed fixtures in the jar match audit_fixtures/", diff == null);
			} else if (packed != null) {
				try {
					tmp = unpack(packed);
					root = tmp;
					h.info("fixtures from the packed copy in the jar (" + packed.getAsJsonObject("sets").size() + " sets)");
				} catch (java.io.IOException | RuntimeException e) {
					h.info("unpacking the packed fixtures: " + e);
					root = null;
				}
			} else root = null;
			if (root == null) {
				h.check("fixtures directory exists (" + dir + ")", false);
				return;
			}
			int n = 0, agree = 0, passing = 0;
			try (var sets = Files.list(root)) {
				for (Path set : sets.filter(Files::isDirectory).sorted().toList()) {
					String want = Files.readString(set.resolve("expected.txt")).trim();
					List<String> p = BossIdentityAudit.checkDir(set);
					String got = p.isEmpty() ? "PASS" : "FAIL";
					n++;
					if (got.equals(want)) agree++;
					else h.info("DISAGREE on " + set.getFileName() + ": python " + want + ", java " + got + " " + p);
					if (want.equals("PASS")) passing++;
				}
			} catch (java.io.IOException e) {
				h.info("fixtures: " + e);
			}
			if (tmp != null) deleteTree(tmp);
			h.info("fixture sets " + n + ", agree " + agree + ", passing sets " + passing);
			h.check("at least 30 fixture sets, some passing and most broken", n >= 30 && passing >= 3 && passing < n);
			h.check("Java and Python agree on every set (" + agree + "/" + n + ")", n > 0 && agree == n);
		});
		h.test("boss_identity: a card against its profile").run(() -> {
			BossIdentity hersir = BossIdentity.get("hrafnholt_hersir");
			DuelistProfile.Builder pb = H.base("hrafnholt_hersir").telegraph(hersir.style(TelegraphStyle.Shape.SPIRAL)).strafe(TestKit.strafe()).sprintCrit(TestKit.crit());
			for (String s : hersir.signature()) pb.signature(SignatureMove.of(s, s, Telegraph.Weight.SIGNATURE, 14, 100, 20F, 0, 6, (bb, t) -> {}));
			DuelistProfile good = pb.build();
			List<String> p = BossIdentityAudit.checkProfile(hersir, good);
			for (String x : p) h.info("checkProfile: " + x);
			h.check("a profile that opts into exactly the card's generics, signatures and look passes", p.isEmpty() && good.genericIds().equals(new HashSet<>(hersir.generic())));
			h.check("a profile that also eats, or uses another look, is reported", !BossIdentityAudit.checkProfile(hersir, TestKit.with(H.base("x").telegraph(hersir.style(TelegraphStyle.Shape.SPIRAL)), Move.STRAFE, Move.CRIT, Move.EAT).build()).isEmpty()
				&& BossIdentityAudit.checkProfile(hersir, H.base("y").strafe(TestKit.strafe()).sprintCrit(TestKit.crit()).build()).size() >= 2);
			h.check("weapon swap, bow and crossbow are not audited generics", H.only("z", Move.SWAP, Move.BOW).build().genericIds().isEmpty());
		});
	}

	private static int count(String generic) {
		int n = 0;
		for (BossIdentity c : BossIdentity.all()) if (c.generic().contains(generic)) n++;
		return n;
	}

	// ---- packed audit fixtures (tools/pack_audit_fixtures.py) -----------------------------------------------------

	private static final String PACKED = "/elsewhere_duelist/dev/audit_fixtures.json.gz";
	private static final java.util.regex.Pattern SAFE_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_.-]+");

	/** The fixture sets packed into the jar ({"format":1,"sets":{set:{"expected","files":{name:text}}}}), or null. */
	static @org.jspecify.annotations.Nullable JsonObject packedFixtures() {
		try (var in = SelfTestIdentity.class.getResourceAsStream(PACKED)) {
			if (in == null) return null;
			try (var r = new java.io.InputStreamReader(new java.util.zip.GZIPInputStream(in), java.nio.charset.StandardCharsets.UTF_8)) {
				JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
				return o.has("sets") && o.get("sets").isJsonObject() ? o : null;
			}
		} catch (java.io.IOException | RuntimeException e) {
			System.out.println("[duelist-selftest] INFO packed fixtures unreadable: " + e);
			return null;
		}
	}

	/** Null when the packed sets equal the fixture directory file for file (expected.txt as the verdict), else the first difference. */
	static @org.jspecify.annotations.Nullable String packedDiff(JsonObject packed, Path root) {
		JsonObject sets = packed.getAsJsonObject("sets");
		try (var list = Files.list(root)) {
			List<Path> dirs = list.filter(Files::isDirectory).sorted().toList();
			Set<String> names = new java.util.TreeSet<>();
			for (Path d : dirs) names.add(d.getFileName().toString());
			if (!names.equals(new java.util.TreeSet<>(sets.keySet()))) return "set names " + names.size() + " on disk, " + sets.size() + " packed";
			for (Path d : dirs) {
				String s = d.getFileName().toString();
				JsonObject e = sets.getAsJsonObject(s);
				if (!Files.readString(d.resolve("expected.txt")).trim().equals(e.get("expected").getAsString())) return s + ": expected verdict";
				JsonObject files = e.getAsJsonObject("files");
				Map<String, String> disk = new TreeMap<>();
				try (var fl = Files.list(d)) {
					for (Path f : fl.filter(Files::isRegularFile).toList()) {
						String fn = f.getFileName().toString();
						if (!fn.equals("expected.txt") && !fn.equals(".DS_Store")) disk.put(fn, Files.readString(f));
					}
				}
				if (!disk.keySet().equals(new java.util.TreeSet<>(files.keySet()))) return s + ": file list";
				for (var f : disk.entrySet()) if (!f.getValue().equals(files.get(f.getKey()).getAsString())) return s + "/" + f.getKey() + ": content";
			}
		} catch (java.io.IOException | RuntimeException e) {
			return "unreadable: " + e;
		}
		return null;
	}

	/** Writes the packed sets to a fresh temp directory laid out like audit_fixtures/ (names checked: no paths). */
	static Path unpack(JsonObject packed) throws java.io.IOException {
		Path tmp = Files.createTempDirectory("duelist-audit-fixtures");
		for (var s : packed.getAsJsonObject("sets").entrySet()) {
			if (!SAFE_NAME.matcher(s.getKey()).matches()) throw new java.io.IOException("bad set name " + s.getKey());
			JsonObject e = s.getValue().getAsJsonObject();
			Path d = Files.createDirectory(tmp.resolve(s.getKey()));
			Files.writeString(d.resolve("expected.txt"), e.get("expected").getAsString() + "\n");
			for (var f : e.getAsJsonObject("files").entrySet()) {
				if (!SAFE_NAME.matcher(f.getKey()).matches() || f.getKey().equals("expected.txt")) throw new java.io.IOException("bad file name " + f.getKey());
				Files.writeString(d.resolve(f.getKey()), f.getValue().getAsString());
			}
		}
		return tmp;
	}

	private static void deleteTree(Path root) {
		try (var walk = Files.walk(root)) {
			for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
		} catch (java.io.IOException e) {
			System.out.println("[duelist-selftest] INFO temp fixtures not removed: " + e);
		}
	}
}
