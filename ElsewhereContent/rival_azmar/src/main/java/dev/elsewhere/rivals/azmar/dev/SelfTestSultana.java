package dev.elsewhere.rivals.azmar.dev;

import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.each;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.face;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.keepTarget;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.pass;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.player;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.poll;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.run;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.spawn;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.sync;
import static dev.elsewhere.rivals.azmar.dev.SelfTestBosses.y;

import dev.elsewhere.duelist.adapt.Side;
import dev.elsewhere.duelist.adapt.Tactic;
import dev.elsewhere.duelist.identity.BossIdentity;
import dev.elsewhere.duelist.identity.BossIdentityAudit;
import dev.elsewhere.duelist.kit.Move;
import dev.elsewhere.rivals.azmar.boss.AzmarBoss;
import dev.elsewhere.rivals.azmar.boss.AzmarBosses;
import dev.elsewhere.rivals.azmar.boss.AzmarSultana;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Sultana v3 card checks: identity match, no Totem, Scarab Shell, Halo Flare fairness, counters, Sun Elixir gating, Arc Dance, no block changes. */
final class SelfTestSultana {
	private SelfTestSultana() {}

	static AzmarSultana s;
	static long hash0;
	static final int[] STILL = {0, 0};
	static final Vec3[] LAST = {null};
	static float hpBefore;
	static final AzmarBoss[] CAP_LORD = {null};
	static final net.minecraft.resources.Identifier CAP_COURT = dev.elsewhere.rivals.azmar.AzmarContent.id("azmar_test_court"), CAP_BOOST = dev.elsewhere.rivals.azmar.AzmarContent.id("azmar_test_regent"),
		CAP_METER = dev.elsewhere.rivals.azmar.AzmarContent.id("azmar_test_meter");

	static AzmarSultana fresh(net.minecraft.server.level.ServerLevel l) {
		SelfTest.clean(l);
		SelfTestBosses.fp = player(l, 0, 150 + 9);
		s = spawn(l, AzmarBosses.SULTANA, 0, 150);
		SelfTestBosses.boss = s;
		s.setTarget(SelfTestBosses.fp);
		return s;
	}

	static void add() {
		// ---- card, profile, no Totem ----
		run((srv, l) -> { hash0 = SelfTestBosses.hash(l, 0, 150, 40); fresh(l); });
		sync("Sultana v3: profile matches boss_identity/azmar_sultana.json (generic, signatures, telegraph look)", () -> {
			try (var in = AzmarSultana.class.getResourceAsStream("/data/elsewhere/boss_identity/azmar_sultana.json")) {
				if (in == null) return false;
				BossIdentity card = BossIdentity.parse("azmar_sultana", new String(in.readAllBytes(), StandardCharsets.UTF_8));
				var problems = BossIdentityAudit.checkProfile(card, AzmarSultana.PROFILE);
				if (!problems.isEmpty()) SelfTest.info("Sultana card problems: " + problems);
				return problems.isEmpty();
			} catch (java.io.IOException e) {
				return false;
			}
		});
		sync("Sultana v3: only generic behaviour is drink; no shield, strafe, crit, pearl or swap", () -> AzmarSultana.PROFILE.genericIds().equals(Set.of("drink")) && !s.on(Move.SHIELD) && !s.on(Move.STRAFE) && !s.on(Move.CRIT) && !s.on(Move.PEARL));
		sync("Sultana v3: no Totem (no kit last stand registered; the Scarab Shell is her own)", () -> !s.phase.hasLastStand());
		sync("Sultana v3: Sun Elixir is off until a bad trade is refused", () -> !s.on(Move.DRINK));
		sync("Sultana: base max health 225, never above the 300 cap", () -> Math.abs(s.getMaxHealth() - 225F) < 0.01F && s.getMaxHealth() <= AzmarBoss.HEALTH_CAP);
		run((srv, l) -> {
			// the court's +12 per living Lord (all three: +36) and a +10 percent Regent-style boost, on her and on the Amir (the highest Lord, the Regent-to-be)
			for (AzmarBoss b : new AzmarBoss[] {s, CAP_LORD[0] = spawn(l, AzmarBosses.AMIR, 6, 150)}) {
				var hp = b.getAttribute(Attributes.MAX_HEALTH);
				hp.addOrUpdateTransientModifier(new AttributeModifier(CAP_COURT, 36.0, AttributeModifier.Operation.ADD_VALUE));
				hp.addOrUpdateTransientModifier(new AttributeModifier(CAP_BOOST, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				hp.addOrUpdateTransientModifier(new AttributeModifier(CAP_METER, 0.51, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));   // Test Meter x1.51
				b.setHealth(b.getMaxHealth());
			}
			CAP_LORD[0].regent = true;
		});
		each(25, l -> keepTarget());   // the cap is re-applied every 20 ticks
		run((srv, l) -> SelfTest.check("health cap: Sultana and a Regent Amir stay at or under 300 max health with court, Regent and Test Meter boosts",
			s.getMaxHealth() <= AzmarBoss.HEALTH_CAP + 0.01F && CAP_LORD[0].getMaxHealth() <= AzmarBoss.HEALTH_CAP + 0.01F && s.getHealth() <= s.getMaxHealth() && CAP_LORD[0].getHealth() <= CAP_LORD[0].getMaxHealth(),
			"sultana " + s.getMaxHealth() + " amir " + CAP_LORD[0].getMaxHealth()));
		run((srv, l) -> SelfTest.check("health cap clamps to 300, not below: boosted past the cap, both sit at 299 to 300 max health",
			s.getMaxHealth() >= AzmarBoss.HEALTH_CAP - 1F && CAP_LORD[0].getMaxHealth() >= AzmarBoss.HEALTH_CAP - 1F,
			"sultana " + s.getMaxHealth() + " amir " + CAP_LORD[0].getMaxHealth() + " (an ADD_VALUE cap under TOTAL multipliers over-shrinks)"));
		run((srv, l) -> { if (CAP_LORD[0] != null) CAP_LORD[0].discard(); });

		// ---- Sun Elixir only after a refused bad trade ----
		run((srv, l) -> { s.setHealth(s.getMaxHealth() * 0.5F); });
		each(30, l -> keepTarget());
		sync("Sun Elixir: still unavailable at 50 percent HP without a bad trade", () -> !s.on(Move.DRINK));
		run((srv, l) -> s.refuseBadTrade(l, SelfTestBosses.fp));
		sync("Sun Elixir: available once she has refused a bad trade (below 60 percent)", () -> s.on(Move.DRINK));
		run((srv, l) -> fresh(l));
		poll("Sun Elixir setup: she has seen the player (guard and reader up)", 60, () -> { keepTarget(); return s.reader != null; });
		run((srv, l) -> {
			s.setHealth(s.getMaxHealth() * 0.5F);
			hpBefore = s.getHealth();
			SelfTestBosses.fp.snapTo(s.getX(), y(), s.getZ() + 14, 0F, 0F);
			s.refuseBadTrade(l, SelfTestBosses.fp);
		});
		poll("Sun Elixir: drunk once after the refusal (32 ticks, nobody within 4 blocks)", 260, () -> { keepTarget(); return s.healUses() >= 1 && !s.isHealing(); });
		run((srv, l) -> SelfTest.check("Sun Elixir heals 15 percent of max HP at once, no vanilla potion extra", Math.abs((s.getHealth() - hpBefore) / s.getMaxHealth() - 0.15F) < 0.006F,
			"healed " + (s.getHealth() - hpBefore) + " of max " + s.getMaxHealth()));
		run((srv, l) -> s.refuseBadTrade(l, SelfTestBosses.fp));
		sync("Sun Elixir: one flask only; a second refusal does not offer another", () -> !s.on(Move.DRINK));

		// ---- Arc Dance: she never stands still more than 10 ticks, no block edits ----
		run((srv, l) -> {
			fresh(l);
			STILL[0] = 0;
			STILL[1] = 0;
			LAST[0] = null;
		});
		each(160, l -> {
			keepTarget();
			face(SelfTestBosses.fp, s.position());
			if (s.idle() && LAST[0] != null && s.position().subtract(LAST[0]).multiply(1, 0, 1).length() < 0.01 && s.getTarget() != null) {
				STILL[0]++;
				STILL[1] = Math.max(STILL[1], STILL[0]);
			} else STILL[0] = 0;
			LAST[0] = s.position();
		});
		sync("Arc Dance: she never stands still more than 10 ticks while idle in a fight", () -> STILL[1] <= 10);
		sync("Sultana fight changes no block (no griefing)", () -> SelfTestBosses.hash(s.level() instanceof net.minecraft.server.level.ServerLevel l ? l : null, 0, 150, 40) == hash0);

		// ---- Halo Flare: blinds a player facing her only ----
		run((srv, l) -> {
			fresh(l);
			pass(s, true);
			SelfTestBosses.fp.removeAllEffects();
			SelfTestBosses.fp.snapTo(s.getX(), y(), s.getZ() + 6, 0F, 0F);
			face(SelfTestBosses.fp, s.position());
			s.fireSignature(AzmarSultana.HALO_FLARE, SelfTestBosses.fp);
		});
		each(26, l -> face(SelfTestBosses.fp, s.position()));
		sync("Halo Flare (24-tick tell) blinds a player facing her", () -> s.stat("halo_flare") >= 1 && SelfTestBosses.fp.hasEffect(MobEffects.BLINDNESS));
		run((srv, l) -> {
			SelfTestBosses.fp.removeAllEffects();
			SelfTestBosses.fp.snapTo(s.getX(), y(), s.getZ() + 6, 0F, 0F);   // yaw 0 faces +z: away from her
			s.fireSignature(AzmarSultana.HALO_FLARE, SelfTestBosses.fp);
		});
		each(26, l -> { SelfTestBosses.fp.snapTo(SelfTestBosses.fp.getX(), y(), SelfTestBosses.fp.getZ(), 0F, 0F); SelfTestBosses.fp.setYHeadRot(0F); });
		sync("Halo Flare does not blind a player who looks away", () -> !SelfTestBosses.fp.hasEffect(MobEffects.BLINDNESS));

		// ---- repeated tactic: the third counts, a mix does not ----
		run((srv, l) -> fresh(l));
		poll("reader exists and read is done (falcon mark)", 80, () -> { dev.elsewhere.rivals.azmar.boss.AzmarBoss.mark(SelfTestBosses.fp.getUUID(), s.level().getGameTime() + 400); keepTarget(); return s.reader != null && s.reader.readDone(s.level().getGameTime()); });
		run((srv, l) -> {
			long now = l.getGameTime();
			s.reader.record(Tactic.COMBO, Side.FRONT, 3, now, SelfTestBosses.fp);
			s.reader.record(Tactic.COMBO, Side.FRONT, 3, now + 5, SelfTestBosses.fp);
			s.reader.record(Tactic.COMBO, Side.FRONT, 3, now + 10, SelfTestBosses.fp);
		});
		sync("the third identical combo queues a counter", () -> s.reader.pendingCount() >= 1 || s.reader.countersFired() >= 1);
		run((srv, l) -> fresh(l));
		poll("reader exists and read is done (mixed tactics)", 80, () -> { dev.elsewhere.rivals.azmar.boss.AzmarBoss.mark(SelfTestBosses.fp.getUUID(), s.level().getGameTime() + 400); keepTarget(); return s.reader != null && s.reader.readDone(s.level().getGameTime()); });
		run((srv, l) -> {
			long now = l.getGameTime();
			s.reader.record(Tactic.COMBO, Side.FRONT, 3, now, SelfTestBosses.fp);
			s.reader.record(Tactic.RANGED_POKE, Side.FRONT, 1, now + 5, SelfTestBosses.fp);
			s.reader.record(Tactic.COMBO, Side.LEFT, 3, now + 10, SelfTestBosses.fp);
			s.reader.record(Tactic.PEARL_IN, Side.FRONT, 1, now + 15, SelfTestBosses.fp);
		});
		sync("three different tactics (or sides) queue no counter", () -> s.reader.pendingCount() == 0 && s.reader.countersFired() == 0);

		// ---- Scarab Shell at 25 percent ----
		run((srv, l) -> {
			fresh(l);
			s.debugSetHealUses(1);   // the Sun Elixir is spent: the shell checks measure the shell alone
			s.setHealth(s.getMaxHealth() * 0.30F);
		});
		each(5, l -> keepTarget());
		run((srv, l) -> s.hurtServer(l, l.damageSources().magic(), s.getMaxHealth() * 0.08F));   // 30 -> 22 percent: crosses 25
		poll("Scarab Shell: roll-up starts at 25 percent (phase 2)", 40, () -> { keepTarget(); return s.phaseIndex() == 2 && s.shellApplyAt > 0; });
		run((srv, l) -> hpBefore = s.getHealth());
		run((srv, l) -> s.hurtServer(l, l.damageSources().magic(), 50F));
		sync("Scarab Shell: invulnerable during the 24-tick roll-up", () -> s.getHealth() == hpBefore);
		poll("Scarab Shell: forms after 24 ticks and absorbs 45 (20 percent of base HP)", 60, () -> { keepTarget(); return s.shellUntil > 0 && Math.abs(s.getAbsorptionAmount() - AzmarSultana.SHELL_ABSORB) < 0.5F; });
		// a Mirage Step gives 8 i-frames and every hurt starts the 20-tick hit cooldown: each test hit waits for a clean window
		each(21, l -> keepTarget());
		poll("Scarab Shell: a clean hit window opens (no Mirage i-frames)", 60, () -> {
			keepTarget();
			var sl = (net.minecraft.server.level.ServerLevel) s.level();
			if (s.isInvulnerableTo(sl, sl.damageSources().magic())) return false;
			hpBefore = s.getHealth();
			s.hurtServer(sl, sl.damageSources().magic(), 30F);
			return true;
		});
		run((srv, l) -> SelfTest.check("Scarab Shell: 30 damage is absorbed, health untouched", s.getHealth() == hpBefore && s.getAbsorptionAmount() < 20F && s.getAbsorptionAmount() > 10F,
			"hp " + s.getHealth() + " before " + hpBefore + " abs " + s.getAbsorptionAmount() + " shellUntil " + s.shellUntil + " hurtTime " + s.hurtTime));
		each(21, l -> keepTarget());
		poll("Scarab Shell: a second clean hit window opens", 60, () -> {
			keepTarget();
			var sl = (net.minecraft.server.level.ServerLevel) s.level();
			if (s.isInvulnerableTo(sl, sl.damageSources().magic())) return false;
			s.hurtServer(sl, sl.damageSources().magic(), 40F);
			return true;
		});
		poll("Scarab Shell: breaks once the 45 are spent; she does not heal and does not roll up again", 60, () -> {
			keepTarget();
			return s.shellBroke >= 1 && s.getAbsorptionAmount() == 0F && s.getHealth() <= hpBefore && s.shellApplyAt == 0 && s.phaseIndex() == 2;
		});
	}
}
