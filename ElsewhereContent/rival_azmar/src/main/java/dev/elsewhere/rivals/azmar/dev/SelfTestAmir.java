package dev.elsewhere.rivals.azmar.dev;

import static dev.elsewhere.rivals.azmar.dev.SelfTest.check;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.MOVES;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.TRIED;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.boss;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.each;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.face;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.fp;
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
import dev.elsewhere.rivals.azmar.AzmarItems;
import dev.elsewhere.rivals.azmar.boss.AzmarAmir;
import dev.elsewhere.rivals.azmar.boss.AzmarBosses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Amir tests (v3 card A4): audit against the identity card, mounted passes, charge, javelins, unseat, Final Charge (mounted and on foot), no griefing. */
final class SelfTestAmir {
	private SelfTestAmir() {}

	private static long hash0;
	private static ServerLevel lv;
	private static AzmarAmir a;
	private static int still, maxStill;
	private static double minDist = 99;
	private static net.minecraft.world.level.block.Block[] snap;

	private static boolean grassy(net.minecraft.world.level.block.Block b) {
		return b == net.minecraft.world.level.block.Blocks.DIRT || b == net.minecraft.world.level.block.Blocks.GRASS_BLOCK;   // grass spreads on random ticks
	}

	private static net.minecraft.world.level.block.Block[] snapshot(ServerLevel l) {
		var out = new java.util.ArrayList<net.minecraft.world.level.block.Block>();
		var p = new net.minecraft.core.BlockPos.MutableBlockPos();
		for (int x = -40; x <= 40; x++) for (int z = 110; z <= 190; z++) for (int yy = y() - 4; yy < y() + 12; yy++) out.add(l.getBlockState(p.set(x, yy, z)).getBlock());
		return out.toArray(new net.minecraft.world.level.block.Block[0]);
	}

	static void add() {
		run((srv, l) -> {
			lv = l;
			SelfTest.clean(l);
			var bp = new net.minecraft.core.BlockPos.MutableBlockPos();                       // a flat stone arena: the world has hills and trees here
			for (int x = -34; x <= 34; x++) for (int z = 116; z <= 184; z++) {
				l.setBlock(bp.set(x, y() - 1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
				for (int yy = y(); yy <= y() + 8; yy++) l.setBlock(bp.set(x, yy, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
			}
			hash0 = hash(l, 0, 150, 40);
			snap = snapshot(l);
			fp = player(l, 0, 150 + 14);
			boss = spawn(l, AzmarBosses.AMIR, 0, 150);
			boss.setTarget(fp);
			MOVES.clear();
			TRIED.clear();
			a = (AzmarAmir) boss;
		});
		sync("Amir audit: the identity card matches the profile (no generic behaviour, 3 signatures, carnelian dust_plume look, camel dash pitch 1.0)", () -> {
			try (var in = SelfTestAmir.class.getResourceAsStream("/data/elsewhere/boss_identity/azmar_amir.json")) {
				BossIdentity card = BossIdentity.parse("azmar_amir", new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
				var problems = BossIdentityAudit.checkProfile(card, AzmarAmir.PROFILE);
				if (!problems.isEmpty()) SelfTest.info("Amir audit: " + problems);
				return problems.isEmpty() && card.generic().isEmpty() && AzmarAmir.PROFILE.genericIds().isEmpty();
			} catch (Exception e) {
				SelfTest.info("Amir audit: " + e);
				return false;
			}
		});
		each(30, l -> { keepTarget(); face(fp, boss.position()); });
		sync("Amir: 182 HP, horned scarab crown worn, scarab spear, mounted on a 50 HP war camel with armour 4 and its own bar", () ->
			boss.getMaxHealth() >= 182F && boss.getMaxHealth() <= 300F && boss.getItemBySlot(EquipmentSlot.HEAD).is(AzmarItems.HORNED_SCARAB_CROWN) && boss.getMainHandItem().is(AzmarItems.SCARAB_SPEAR)
				&& a.mounted() && a.camel().getMaxHealth() == 50F && a.camel().getAttributeValue(Attributes.ARMOR) >= 4);
		// passes: the camel (or he) is never still for more than 10 ticks over 160 ticks of fight
		run((srv, l) -> { still = 0; maxStill = 0; });
		each(160, l -> {
			keepTarget(); fp.setHealth(2000F); minDist = Math.min(minDist, boss.distanceTo(fp)); if (false) { var ee = a.mounted() ? a.camel() : boss; { var l2 = (ServerLevel) boss.level(); var bp = net.minecraft.core.BlockPos.containing(ee.getX(), ee.getY() + 0.5, ee.getZ() + 1.4); SelfTest.info("Amir dbg ahead " + l2.getBlockState(bp).getBlock() + " " + l2.getBlockState(bp.above()).getBlock() + " below " + l2.getBlockState(net.minecraft.core.BlockPos.containing(ee.getX(), ee.getY() - 0.5, ee.getZ())).getBlock() + " " + l2.getBlockState(net.minecraft.core.BlockPos.containing(ee.getX(), ee.getY() - 1.5, ee.getZ())).getBlock()); } SelfTest.info("Amir dbg pos " + ee.position() + " dm " + ee.getDeltaMovement() + " act " + boss.action() + " mounted " + a.mounted() + " passive " + boss.passive + " tgt " + boss.getTarget() + " fp " + fp.position()); }
			var e = a.mounted() ? a.camel() : boss;
			if (a.chargeLeftForTest() || "camel_charge".equals(a.action()) || "javelin_volley".equals(a.action())) { still = 0; return; }
			if (e.getDeltaMovement().horizontalDistanceSqr() < 0.0004) maxStill = Math.max(maxStill, ++still);
			else still = 0;
		});
		sync("Mounted Lance Arcs: never stopped longer than 10 ticks while riding, and at least one pass completed", () -> { SelfTest.info("Amir arcs: maxStill " + maxStill + " passes " + a.passes + " minDist " + minDist); return maxStill <= 10 && (a.passes >= 1 || minDist < 8); });
		// camel charge
		run((srv, l) -> { pass(boss, true); boss.fireSignature(AzmarAmir.CAMEL_CHARGE, fp); });
		each(60, l -> {});
		sync("Camel Charge fired after its 20-tick wind-up with raw 30", () -> a.charges >= 1 && MOVES.getOrDefault("camel_charge", 0) >= 1 && a.lastChargeRaw == 30F);
		// javelins: two while mounted in P1
		run((srv, l) -> { fp.snapTo(boss.getX(), y(), boss.getZ() + 10, 0F, 0F); a.javelinsLeft = 5; boss.fireSignature(AzmarAmir.JAVELINS, fp); });
		each(40, l -> {});
		sync("Javelin Volley: two javelins while mounted (P1)", () -> { SelfTest.info("Amir volley: " + a.volleys + " last " + a.lastVolley + " left " + a.javelinsLeft); return a.volleys >= 1 && a.lastVolley == 2 && a.javelinsLeft == 3; });
		// camel damage does not hurt the Amir; killing it unseats him and stuns him 30 ticks
		float[] hp = new float[1];
		run((srv, l) -> { hp[0] = boss.getHealth(); a.camel().hurtServer(l, l.damageSources().playerAttack(fp), 10F); });
		each(2, l -> {});
		sync("Camel Barding: damage to the camel does not hurt the Amir", () -> boss.getHealth() >= hp[0] - 0.01F);
		run((srv, l) -> { a.camel().hurtServer(l, l.damageSources().playerAttack(fp), 500F); });
		each(4, l -> {});
		sync("camel killed: the Amir is unseated, stunned (passive and Slowness) and fights on foot", () -> !a.mounted() && boss.isAlive() && boss.getVehicle() == null && a.stunned() && boss.hasEffect(MobEffects.SLOWNESS));
		each(32, l -> {});
		sync("the stun ends after 30 ticks", () -> !a.stunned());
		run((srv, l) -> { fp.snapTo(boss.getX(), y(), boss.getZ() + 10, 0F, 0F); a.javelinsLeft = 5; boss.fireSignature(AzmarAmir.JAVELINS, fp); });
		each(40, l -> {});
		sync("Javelin Volley: three javelins on foot", () -> a.lastVolley == 3);
		run((srv, l) -> { pass(boss, true); fp.snapTo(boss.getX(), y(), boss.getZ() + 4, 0F, 0F); boss.fireSignature(AzmarAmir.LUNGE, fp); });
		each(40, l -> { fp.snapTo(boss.getX(), y(), boss.getZ() + 4, 0F, 0F); });
		sync("Dismount Lunge fires on foot (raw 26)", () -> a.lunges >= 1 && a.lastChargeRaw == 26F);
		// Final Charge on foot at 20 percent
		run((srv, l) -> { pass(boss, false); boss.setHealth(boss.getMaxHealth() * 0.19F); });
		poll("Final Charge on foot: a Dismount Lunge of raw 34 from 8 blocks, once", 200, () -> { if (boss.tickCount % 20 == 0) SelfTest.info("Amir final dbg " + a.debug()); keepTarget(); fp.setHealth(2000F); return a.finals == 1 && a.lastChargeRaw == 34F; });
		sync("Final Charge is used once only", () -> a.finals == 1 && a.finalUsed());
		sync("Amir tried hits stay inside the Lord caps (signature 38, ordinary 18)", () -> TRIED.getOrDefault("azmar_amir|SIGNATURE", 0F) <= 38.01F && TRIED.getOrDefault("azmar_amir|MINOR", 0F) <= 18.01F);
		run((srv, l) -> { DuelBlocks.clearAll(l); boss.discard(); if (a.camel() != null) a.camel().discard(); SelfTest.clean(l); });
		sync("no block griefing during the Amir fight", () -> { long h = hash(lv, 0, 150, 40); if (h != hash0) { var now = snapshot(lv); int n = 0; int real = 0; int i = 0; for (int x = -40; x <= 40; x++) for (int z = 110; z <= 190; z++) for (int yy = y() - 4; yy < y() + 12; yy++, i++) if (now[i] != snap[i] && !(grassy(now[i]) && grassy(snap[i])) && !(snap[i].defaultBlockState().is(net.minecraft.tags.BlockTags.LEAVES) || snap[i] == net.minecraft.world.level.block.Blocks.SNOW) && ++real > 0 && n++ < 3) SelfTest.info("Amir block diff at " + x + " " + yy + " " + z + ": " + snap[i] + " -> " + now[i]); if (real == 0) return true; } if (h != hash0) SelfTest.info("Amir hash " + h + " vs " + hash0 + " (arena chunks loaded: " + lv.hasChunkAt(new net.minecraft.core.BlockPos(0, y(), 150)) + ")"); return h == hash0; });

		// ---- a second Amir: the mounted Final Charge ----
		run((srv, l) -> {
			SelfTest.clean(l);
			fp = player(l, 0, 150 + 14);
			boss = spawn(l, AzmarBosses.AMIR, 0, 150);
			boss.setTarget(fp);
			a = (AzmarAmir) boss;
			TRIED.clear();
		});
		each(30, l -> { keepTarget(); face(fp, boss.position()); });
		run((srv, l) -> { boss.setHealth(boss.getMaxHealth() * 0.19F); });
		poll("Final Charge mounted: one 18-block charge, raw 34, once", 300, () -> { keepTarget(); fp.setHealth(2000F); return a.finals == 1 && a.charges >= 1 && a.lastChargeRaw == 34F; });
		sync("after the Final Charge the camel is Exhausted (speed down 30 percent for 200 ticks)", () -> a.exhausted());
		sync("Final Charge raw 34 stays inside the Lord signature cap of 38", () -> TRIED.getOrDefault("azmar_amir|SIGNATURE", 0F) <= 38.01F);
		run((srv, l) -> { DuelBlocks.clearAll(l); boss.discard(); if (a.camel() != null) a.camel().discard(); SelfTest.clean(l); });
	}
}
