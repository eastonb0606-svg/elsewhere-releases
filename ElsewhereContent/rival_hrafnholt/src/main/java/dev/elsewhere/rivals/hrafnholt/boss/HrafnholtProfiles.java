package dev.elsewhere.rivals.hrafnholt.boss;

import dev.elsewhere.duelist.boss.Behaviours;
import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.FrenzyRage;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.SignatureMove;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.boss.TelegraphStyle;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.ReachAttack;
import dev.elsewhere.duelist.kit.Timers;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.hrafnholt.HrafnholtContent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The six Hrafnholt profiles (RIVAL-BOSSES v3 identity cards H1-H6) on the duelist plumbing. Nothing is shared: each boss opts into exactly the
 * generic behaviours on its card (its card's {@code generic} list) with its own numbers, has its own telegraph look (the card's particle, colour,
 * sound and pitch) and its own 2 or 3 signature moves. Movement, defence, recovery, reach control and last stand are the Hrafnholt lane's own code
 * ({@link HrafnholtBoss}, {@link HrafnholtDrive}). All damage goes through {@code hurtRaw}, so the caps hold (ordinary 18, signature 38 Lord / 40 ruler).
 * A Risen champion uses the same profile with Hird-Captain HP plus 15 percent and {@link HrafnholtBoss#moveAllowed} filtering to its 2 kept moves.
 */
public final class HrafnholtProfiles {
	private HrafnholtProfiles() {}

	private static final Map<String, DuelistProfile> CACHE = new HashMap<>();

	public static synchronized DuelistProfile of(Hrafn h, boolean risen) {
		return CACHE.computeIfAbsent(h.key + (risen ? "#risen" : ""), k -> build(h, risen));
	}

	/** The card's telegraph look (RIVAL-BOSSES v3, boss_identity/hrafnholt_*.json). */
	static TelegraphStyle style(Hrafn h) {
		return switch (h) {
			case JARL -> TelegraphStyle.of("snowflake", 0xDCE6EE, "entity.ravager.roar", TelegraphStyle.Shape.COLUMN, 0.6F);
			case HERSIR -> TelegraphStyle.of("cloud", 0x8E99A6, "event.raid.horn", TelegraphStyle.Shape.SPIRAL, 1.2F);
			case HALLWARDEN -> TelegraphStyle.of("lava", 0xC2512B, "block.anvil.land", TelegraphStyle.Shape.RING, 0.7F);
			case SKALD -> TelegraphStyle.of("enchant", 0x8FD0FF, "block.amethyst_block.chime", TelegraphStyle.Shape.HALO, 1.6F);
			case CHIEF -> TelegraphStyle.of("dust", 0xA01818, "entity.wolf.howl", TelegraphStyle.Shape.RING, 0.8F);
			case HUNTMASTER -> TelegraphStyle.of("end_rod", 0xF2F2F2, "item.goat_horn.sound.2", TelegraphStyle.Shape.HALO, 1.0F);
		};
	}

	private static DuelistProfile build(Hrafn h, boolean risen) {
		DuelistProfile.Builder b = DuelistProfile.builder(risen ? "hrafnholt_risen_" + h.key : "hrafnholt_" + h.key)
			.skin(risen ? h.risenSkinPath() : h.skinPath()).barColor(BossEvent.BossBarColor.RED).telegraph(style(h))
			.hp(risen ? Hrafn.CAPTAIN_HP * (1.0 + Hrafn.RISEN_BONUS) : h.hp);
		if (!risen) b.identity("hrafnholt_" + h.key);
		switch (h) {
			case JARL -> {
				// Nine-Notch Axe (raw 16, 1 per 28) and the raven heater: shield_block + eat on, nothing else. Glacier March is HrafnholtDrive's.
				b.ruler().walkSpeed(0.20).swing(16F, 28, 10).reach(3.2).caps(18F, 40F).melee(() -> new ItemStack(Items.DIAMOND_AXE)).phases(0.60, 0.25)
					.shield(new Behaviours.Shield(5, 90, 180, 4, 100F, 100, null))
					.eat(new Behaviours.Eat(0.10F, 60, new Behaviours.HealRule(0.60F, 4.0, 1, 120))).food(() -> new ItemStack(Items.COOKED_PORKCHOP));
				b.signature(ninthWinterSlam()).signature(oathCleave()).signature(hearthHorn());
			}
			case HERSIR -> {
				// Raven war-spear (thrust raw 15, reach 3.5, 1 per 24), no shield: circle_strafe (8 to 10, flips 50 to 90) + sprint_jump_crit.
				b.walkSpeed(0.14).swing(15F, 24, 8).reach(3.5).caps(18F, 38F).melee(() -> new ItemStack(Items.DIAMOND_SPEAR)).phases(0.50, 0.25)
					.strafe(new Behaviours.Strafe(1.5F, 50, 90, 8.0, 10.0, 6))
					.sprintCrit(new Behaviours.SprintCrit(4.0, 8, 80, 16, 1.5F));
				b.signature(raidCharge()).signature(spearVault()).signature(hornOfThreeCalls());
			}
			case HALLWARDEN -> {
				// Diamond sword (raw 15, 1 per 26) and a studded shield: shield_block only (arc 120, raise 5, bash is the Bash Chain).
				b.walkSpeed(0.10).swing(15F, 26, 8).reach(3.2).caps(18F, 38F).melee(() -> new ItemStack(Items.DIAMOND_SWORD)).phases(0.50, 0.25)
					.shield(new Behaviours.Shield(5, 60, 120, 2, 120F, 100, null));
				b.signature(bashChain()).signature(hearthWard()).signature(doorBreaker());
			}
			case SKALD -> {
				// Diamond axe (raw 16, 1 per 22) and a belt of 4 rune axes: sprint_jump_crit (3-block run-up, x1.4) + eat (mutton at 55%).
				b.walkSpeed(0.14).swing(16F, 22, 10).reach(3.2).caps(18F, 38F).melee(() -> new ItemStack(Items.DIAMOND_AXE)).phases(0.50, 0.20)
					.sprintCrit(new Behaviours.SprintCrit(3.0, 8, 100, 16, 1.4F))
					.eat(new Behaviours.Eat(0.10F, 60, new Behaviours.HealRule(0.55F, 4.0, 1, 120))).food(() -> new ItemStack(Items.COOKED_MUTTON));
				b.signature(runeThrow()).signature(threeBeatVerse()).signature(verseOfWinter());
			}
			case CHIEF -> {
				// Two iron axes (alternating raw 14, 1 per 14), Frenzy +1% per 1% HP lost up to +50%: no generic behaviour at all.
				b.walkSpeed(0.20).swing(14F, 14, 6).reach(3.2).caps(18F, 38F).melee(() -> new ItemStack(Items.IRON_AXE)).phases(0.50, 0.20)
					.frenzy(new FrenzyRage(0.5, 0.0, true, true));
				b.signature(furrowRun()).signature(peltLeap()).signature(wolfHowl());
			}
			case HUNTMASTER -> {
				// Hornbow (draw 17, raw 14), hunting axe (raw 14, 1 per 24) and two wolves: no generic behaviour. Lane Runner is HrafnholtDrive's.
				b.walkSpeed(0.18).swing(14F, 24, 6).reach(3.2).caps(18F, 38F).melee(() -> new ItemStack(Items.IRON_AXE)).phases(0.50, 0.20);
				b.signature(wolfPack()).signature(pinningArrow()).signature(peltDive());
			}
		}
		if (risen) b.swing(14F, h == Hrafn.CHIEF ? 14 : 20, 6).melee(() -> new ItemStack(Items.IRON_AXE));
		return b.build();
	}

	// ---- a signature move as data -------------------------------------------------------------------------------------------------

	static final class Sig implements SignatureMove {
		final String id, label;
		final Telegraph.Weight w;
		final int windup, cd, cd2, prio;
		final float raw;
		final double min, max;
		final boolean crouch, freeze;
		final BiPredicate<HrafnholtBoss, LivingEntity> can;
		final BiConsumer<HrafnholtBoss, LivingEntity> begin, fire;

		Sig(String id, String label, Telegraph.Weight w, int windup, int cd, int cd2, float raw, double min, double max, int prio, boolean crouch, boolean freeze,
			BiPredicate<HrafnholtBoss, LivingEntity> can, BiConsumer<HrafnholtBoss, LivingEntity> begin, BiConsumer<HrafnholtBoss, LivingEntity> fire) {
			this.id = id;
			this.label = label;
			this.w = w;
			this.windup = windup;
			this.cd = cd;
			this.cd2 = cd2;
			this.raw = raw;
			this.min = min;
			this.max = max;
			this.prio = prio;
			this.crouch = crouch;
			this.freeze = freeze;
			this.can = can;
			this.begin = begin;
			this.fire = fire;
		}

		public String id() { return id; }
		public String label() { return label; }
		public Telegraph.Weight weight() { return w; }
		public int windup() { return windup; }
		public int cooldown() { return cd; }
		public float raw() { return raw; }
		public double minRange() { return min; }
		public double maxRange() { return max; }
		public int priority() { return prio; }
		public boolean freeze() { return freeze; }

		@Override
		public Telegraph.Cue cue(HumanBoss boss) {
			return boss.style().cue(crouch, !crouch);
		}

		public boolean canUse(HumanBoss boss, LivingEntity target) {
			HrafnholtBoss b = (HrafnholtBoss) boss;
			return b.moveAllowed(id) && !b.moving() && !b.lastStandBusy() && can.test(b, target);
		}

		public void begin(HumanBoss boss, LivingEntity target) {
			begin.accept((HrafnholtBoss) boss, target);
		}

		public void fire(HumanBoss boss, LivingEntity target) {
			HrafnholtBoss hb = (HrafnholtBoss) boss;
			fire.accept(hb, target);
			int c = hb.cooldownFor(id, cd, cd2);
			boss.setCooldown("sig:" + id, c);
		}
	}

	static final BiPredicate<HrafnholtBoss, LivingEntity> ALWAYS = (b, t) -> true;
	static final BiConsumer<HrafnholtBoss, LivingEntity> NOTHING = (b, t) -> {};

	// ---- helpers --------------------------------------------------------------------------------------------------------------------

	static ServerLevel lv(HrafnholtBoss b) {
		return (ServerLevel) b.level();
	}

	static Vec3 dirTo(HrafnholtBoss b, LivingEntity t) {
		Vec3 d = t.position().subtract(b.position()).multiply(1, 0, 1);
		return d.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, b.getYRot()).multiply(1, 0, 1).normalize() : d.normalize();
	}

	static boolean hit(HrafnholtBoss b, LivingEntity e, float raw, DamageCaps kind) {
		ServerLevel level = lv(b);
		return b.hurtRaw(level, e, raw, kind, level.damageSources().mobAttack(b));
	}

	/** Knockback in blocks (about 0.45 strength per block). */
	static void push(HrafnholtBoss b, LivingEntity e, double blocks) {
		ServerLevel level = lv(b);
		e.knockback(blocks * 0.45, -(e.getX() - b.getX()), -(e.getZ() - b.getZ()), level.damageSources().mobAttack(b), 0.0F);
		e.needsSync = true;
	}

	/** Takes a player's raised shield down: item cooldown on the shield for {@code ticks}. */
	static void breakShield(LivingEntity e, int ticks) {
		if (e instanceof Player p && p.isBlocking()) {
			ItemStack use = p.getUseItem();
			p.stopUsingItem();
			p.getCooldowns().addCooldown(use, ticks);
			p.level().playSound(null, p.blockPosition(), SoundEvents.SHIELD_BREAK.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
		}
	}

	static void ring(ServerLevel level, Vec3 c, double r, net.minecraft.core.particles.ParticleOptions p) {
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24;
			level.sendParticles(p, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.02);
		}
	}

	static void slow(LivingEntity e, int ticks, int amp) {
		e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, amp));
	}

	// ---- H1 Jarl ------------------------------------------------------------------------------------------------------------------

	/** Ninth-Winter Slam: raw 34, 3-block ring, Slowness I 40, 24-tick wind-up, a 4-block leap, open 24 ticks after, cooldown 240 (P2 180). */
	static SignatureMove ninthWinterSlam() {
		return new Sig("ninth_winter_slam", "Ninth-Winter Slam", Telegraph.Weight.HEAVY, 24, 240, 180, 34F, 0, 8, 14, false, true, ALWAYS, NOTHING, (b, t) -> {
			Vec3 d = dirTo(b, t);
			double dist = Math.min(4.0, b.distanceTo(t));
			b.startLeap(b.position().add(d.scale(dist)), 0.5, 24, false, () -> {
				ServerLevel level = lv(b);
				Vec3 c = b.position();
				for (LivingEntity e : b.foes(level, c, 3.0)) {
					hit(b, e, 34F, DamageCaps.SIGNATURE);
					slow(e, 40, 0);
				}
				ring(level, c, 3.0, ParticleTypes.SNOWFLAKE);
				level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 3, 0.8, 0.1, 0.8, 0.0);
				level.playSound(null, b.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 0.6F);
				b.bump("slam_ring");
			});
		});
	}

	static void cleave(HrafnholtBoss b, LivingEntity t, boolean breakIt) {
		if (!b.isAlive() || !t.isAlive() || b.distanceTo(t) > 4.8) return;
		b.swingForAttack(InteractionHand.MAIN_HAND);
		hit(b, t, 26F, DamageCaps.SIGNATURE);
		if (breakIt) breakShield(t, 60);                                   // 3 s, once per 6 s (the move's cooldown)
		lv(b).sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + 1.2, t.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
	}

	/** Cleave of the Oath: raw 26, disables a raised player shield 60 ticks (once per 120), wind-up 16 with his own shield dropped, recovery 18. */
	static SignatureMove oathCleave() {
		return new Sig("oath_cleave", "Cleave of the Oath", Telegraph.Weight.SIGNATURE, 16, 120, 120, 26F, 0, 4.6, 12, false, true, ALWAYS, (b, t) -> b.disableShield(30), (b, t) -> {
			cleave(b, t, true);
			if (b.rageOn()) Timers.after(lv(b).getServer(), 14, () -> cleave(b, t, false));   // Winter Rage: the cleave comes in pairs
			b.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, b.rageOn() ? 6 : 18, 6, false, false));   // recovery 18 ticks
		});
	}

	/** Hearth-Horn: at 66% and 33% HP, a 30-tick uninterruptible horn; two Huskarls walk in, every Huskarl gets Strength I for 160 ticks. */
	static SignatureMove hearthHorn() {
		return new Sig("hearth_horn", "Hearth-Horn", Telegraph.Weight.SIGNATURE, 30, 60, 60, 0F, 0, 128, 100, false, true, (b, t) -> b.hornDue(), (b, t) -> b.markHornTaken(), (b, t) -> {
			if (!b.isAlive()) return;
			b.summonHuskarls(lv(b), 2);
			b.hornsBlown++;
			lv(b).playSound(null, b.blockPosition(), dev.elsewhere.rivals.hrafnholt.Sfx3.pick("hrafnholt", "horn", SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value()), SoundSource.HOSTILE, dev.elsewhere.rivals.hrafnholt.Sfx3.enabled() ? 3F : 2F, dev.elsewhere.rivals.hrafnholt.Sfx3.enabled() ? 1.0F : 0.8F);
		});
	}

	// ---- H2 Hersir ------------------------------------------------------------------------------------------------------------------

	/** Raid Charge: raw 30, knockback 2, wind-up 20 (horn, spear levelled, crouch), a 10-block sprint at 0.45 that cannot turn, ending in a lunge (+0.5 reach). Cooldown 200 (P2 140). */
	static SignatureMove raidCharge() {
		return new Sig("raid_charge", "Raid Charge", Telegraph.Weight.SIGNATURE, 20, 200, 140, 30F, 4, 20, 14, true, true, ALWAYS, NOTHING, (b, t) -> {
			Vec3 d = dirTo(b, t);                                            // locked: she cannot turn mid-charge
			b.startRush(d, 0.45, 23, 26, e -> {
				b.swingForAttack(InteractionHand.MAIN_HAND);
				hit(b, e, 30F, DamageCaps.SIGNATURE);
				push(b, e, 2.0);
				b.bump("charge_hit");
			}, () -> {
				ServerLevel level = lv(b);                                    // the end-of-charge lunge: reach 4.0 (3.5 + 0.5)
				LivingEntity tg = b.getTarget();
				if (tg != null && tg.isAlive() && b.stat("charge_hit") == 0) {
					b.swingForAttack(InteractionHand.MAIN_HAND);
					ReachAttack.hit(level, b, tg, 4.0, 0, 15F, false, DamageCaps.MINOR);
					b.bump("lunge");
				}
			});
		});
	}

	/** Spear Vault: raw 24, wind-up 20 (tip plants and bends), vaults over you from 3 to 8 blocks and lands 2 blocks behind, open 20 ticks, cooldown 300. */
	static SignatureMove spearVault() {
		return new Sig("spear_vault", "Spear Vault", Telegraph.Weight.SIGNATURE, 20, 300, 300, 24F, 3, 8, 12, false, true, ALWAYS, NOTHING, (b, t) -> {
			Vec3 d = dirTo(b, t);
			Vec3 land = t.position().add(d.scale(2.0));
			b.startLeap(land, 0.62, 20, false, () -> {
				ServerLevel level = lv(b);
				for (LivingEntity e : b.foes(level, b.position(), 2.8)) hit(b, e, 24F, DamageCaps.SIGNATURE);
				level.sendParticles(ParticleTypes.CLOUD, b.getX(), b.getY() + 0.2, b.getZ(), 10, 0.6, 0.1, 0.6, 0.05);
				b.bump("vault_land");
			});
		});
	}

	/** Horn of Three Calls: once at 50% HP, a 30-tick horn; Strength I for 160 ticks to her and every Huskarl within 16; two Huskarls flank left and right. */
	static SignatureMove hornOfThreeCalls() {
		return new Sig("horn_of_three_calls", "Horn of Three Calls", Telegraph.Weight.SIGNATURE, 30, 600, 600, 0F, 0, 128, 100, false, true, (b, t) -> b.phaseIndex() >= 1 && b.stat("horn_of_three_calls") == 0, NOTHING, (b, t) -> {
			if (!b.isAlive()) return;
			ServerLevel level = lv(b);
			b.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 0));
			Identifier huskarl = HrafnholtContent.id("hrafnholt_huskarl");
			for (KingdomSoldier s : level.getEntitiesOfClass(KingdomSoldier.class, b.getBoundingBox().inflate(16), x -> x.isAlive() && HrafnholtContent.FACTION.equals(x.factionId()) && huskarl.equals(x.unitId())))
				s.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 0));
			b.summonFlank(level, 2);
			b.hornsBlown++;
			level.playSound(null, b.blockPosition(), dev.elsewhere.rivals.hrafnholt.Sfx3.pick("hrafnholt", "horn", SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value()), SoundSource.HOSTILE, dev.elsewhere.rivals.hrafnholt.Sfx3.enabled() ? 3F : 2F, dev.elsewhere.rivals.hrafnholt.Sfx3.enabled() ? 1.12F : 1.2F);
		});
	}

	// ---- H3 Hallwarden -----------------------------------------------------------------------------------------------------------

	static void bash(HrafnholtBoss b, LivingEntity t, int n, boolean last) {
		if (!b.isAlive() || !t.isAlive() || b.shieldDisabledTicks() > 0 || b.isStaggered() || b.distanceTo(t) > 3.8) return;   // an axe on his shield ends the chain
		ServerLevel level = lv(b);
		b.swingForAttack(InteractionHand.OFF_HAND);
		hit(b, t, last ? 14F : 8F, DamageCaps.MINOR);
		push(b, t, 2.0);
		if (last) breakShield(t, 20);                                        // the last bash staggers a raised shield for 1 s
		level.playSound(null, b.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.2F, 0.7F + 0.1F * n);
		level.sendParticles(ParticleTypes.FLAME, b.getX(), b.getY() + 1.2, b.getZ(), 4 + n * 3, 0.3, 0.3, 0.3, 0.02);
		b.bump("bash_chain_hit");
	}

	/** Bash Chain: raw 8, 8, 14, wind-up 14, bashes 10 ticks apart (P2: four), the third staggers a raised shield 20 ticks. Cooldown 160 (halved while Door Barred). */
	static SignatureMove bashChain() {
		return new Sig("bash_chain", "Bash Chain", Telegraph.Weight.SIGNATURE, 14, 160, 160, 14F, 0, 3.8, 14, false, true, ALWAYS, NOTHING, (b, t) -> {
			int n = b.phaseIndex() >= 1 ? 4 : 3;
			ServerLevel level = lv(b);
			for (int i = 0; i < n; i++) {
				int k = i;
				if (i == 0) bash(b, t, 0, n == 1);
				else Timers.after(level.getServer(), i * 10, () -> bash(b, t, k, k == n - 1));
			}
		});
	}

	/** Hearth-Ward: the shield held up to 40 ticks storing 40% of what it blocks (max raw 30), then a 20-tick wind-up and a 3-block cone shockwave of the stored raw. */
	static SignatureMove hearthWard() {
		return new Sig("hearth_ward", "Hearth-Ward", Telegraph.Weight.HEAVY, 60, 400, 400, 30F, 0, 5, 8, false, true, (b, t) -> b.shieldDisabledTicks() == 0 && !b.rooted(), (b, t) -> {
			b.stored = 0F;
			b.wardUntil = b.level().getGameTime() + 40;                     // stores for the first 40 ticks, then the 20-tick wind-up
			b.forceShield(60);
		}, (b, t) -> {
			ServerLevel level = lv(b);
			float raw = Math.max(10F, b.stored);                            // raw = stored, max 30 (a dud ward still shoves)
			Vec3 fwd = Vec3.directionFromRotation(0, b.getYRot()).multiply(1, 0, 1).normalize();
			int hits = 0;
			for (LivingEntity e : b.foes(level, b.position(), 3.6)) {
				Vec3 rel = e.position().subtract(b.position()).multiply(1, 0, 1);
				if (rel.lengthSqr() > 0.01 && rel.normalize().dot(fwd) < 0.5) continue;   // a 3-block cone (60 degrees either side)
				hit(b, e, raw, DamageCaps.SIGNATURE);
				push(b, e, 3.0);
				if (e instanceof Player) hits++;
			}
			if (hits > 0) b.wardLeech();                                    // Ward Leech: 1.5% max HP per shockwave that hits a player, at most 3 a fight
			for (int i = 1; i <= 4; i++) level.sendParticles(ParticleTypes.FLAME, b.getX() + fwd.x * i, b.getY() + 0.5, b.getZ() + fwd.z * i, 5, 0.4, 0.2, 0.4, 0.05);
			level.playSound(null, b.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, 0.6F);
			b.wardBursts++;
			b.stored = 0F;
			b.wardUntil = 0;
		});
	}

	/** Door-Breaker: raw 24, knockback 5, cancels a raised shield 20 ticks, wind-up 20, an 8-block charge, recovery 28, cooldown 240. */
	static SignatureMove doorBreaker() {
		return new Sig("door_breaker", "Door-Breaker", Telegraph.Weight.HEAVY, 20, 240, 240, 24F, 4, 10, 12, true, true, (b, t) -> !b.rooted(), NOTHING, (b, t) -> {
			Vec3 d = dirTo(b, t);
			b.startRush(d, 0.40, 20, 28, e -> {
				hit(b, e, 24F, DamageCaps.SIGNATURE);
				push(b, e, 5.0);
				breakShield(e, 20);
				b.bump("door_hit");
			}, b::doorMarkHere);
		});
	}

	// ---- H4 Skald ------------------------------------------------------------------------------------------------------------------

	static void throwAxe(HrafnholtBoss b, LivingEntity t) {
		if (!b.isAlive() || !t.isAlive() || !b.hasLineOfSight(t)) return;
		b.swingForAttack(InteractionHand.MAIN_HAND);
		dev.elsewhere.duelist.kit.DuelistProjectile.throwAt(lv(b), b, t, new ItemStack(Items.STONE_AXE), 22F, 1.6F);   // the kit clamps thrown gear to the cap
		b.axeThrown();
		b.bump("axe_thrown");
	}

	/** Rune-Throw: raw 22, range 14 (4 axes on the belt, a stuck axe returns after 80 ticks), wind-up 20, the same axe not for 60 ticks. */
	static SignatureMove runeThrow() {
		return new Sig("rune_throw", "Rune-Throw", Telegraph.Weight.SIGNATURE, 20, 60, 60, 22F, 4, 16, 14, false, false, (b, t) -> b.hasLineOfSight(t) && b.axesOnBelt() > 0 && !b.verseRage(), NOTHING, (b, t) -> {
			throwAxe(b, t);
			if (b.phaseIndex() >= 1 && b.distanceTo(t) > 6 && b.axesOnBelt() >= 2) {                // phase 2: three in 60 ticks when you retreat
				Timers.after(lv(b).getServer(), 20, () -> {
					if (b.axesOnBelt() > 0 && !b.verseRage()) throwAxe(b, t);
				});
				Timers.after(lv(b).getServer(), 40, () -> {
					if (b.axesOnBelt() > 0 && !b.verseRage()) throwAxe(b, t);
				});
			}
		});
	}

	static void beat(HrafnholtBoss b, LivingEntity t, int n) {
		if (!b.isAlive() || !t.isAlive() || b.distanceTo(t) > 4.6) return;
		ServerLevel level = lv(b);
		Vec3 d = dirTo(b, t);
		b.swingForAttack(InteractionHand.MAIN_HAND);
		b.setDeltaMovement(d.x * 0.25, b.getDeltaMovement().y, d.z * 0.25);
		if (n == 2) {
			hit(b, t, 26F, DamageCaps.SIGNATURE);                            // raw 16 + 10, knockback 2
			push(b, t, 2.0);
		} else hit(b, t, 16F, DamageCaps.MINOR);
		if (n == 1) {                                                        // the rune flashes 12 ticks before beat 3
			level.sendParticles(ParticleTypes.END_ROD, b.getX(), b.getY() + 1.5, b.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
			level.playSound(null, b.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.2F, 1.4F);
		}
		b.bump("verse_beat");
	}

	/** Three-Beat Verse: raw 16, 16, 26, wind-up 14, 10 ticks between beats, the rune flashes 12 ticks before beat 3. Then he hops back and throws (Verse Skip). */
	static SignatureMove threeBeatVerse() {
		return new Sig("three_beat_verse", "Three-Beat Verse", Telegraph.Weight.SIGNATURE, 14, 100, 100, 26F, 0, 3.6, 14, false, true, ALWAYS, NOTHING, (b, t) -> {
			ServerLevel level = lv(b);
			beat(b, t, 0);
			Timers.after(level.getServer(), 10, () -> beat(b, t, 1));
			Timers.after(level.getServer(), 22, () -> beat(b, t, 2));      // the flash 12 ticks before
			Timers.after(level.getServer(), 28, () -> b.beginSkip(t));      // Verse Skip: hop back 5 blocks, throw, re-enter
		});
	}

	/** Verse of Winter: once at 50% HP, a 50-tick song (he takes +15% while singing), then +20% attack speed for 200 ticks. */
	static SignatureMove verseOfWinter() {
		return new Sig("verse_of_winter", "Verse of Winter", Telegraph.Weight.SIGNATURE, 50, 600, 600, 0F, 0, 128, 100, false, true, (b, t) -> b.phaseIndex() >= 1 && b.stat("verse_of_winter") == 0, (b, t) -> b.singing = true, (b, t) -> {
			b.singing = false;
			if (!b.isAlive()) return;
			b.startVerse(1.20, 200);
		});
	}

	// ---- H5 Chief --------------------------------------------------------------------------------------------------------------------

	/** Furrow Run: raw 22 once per pass, wind-up 16 (head down, axes crossed, a growl), a 14-block straight run that cannot turn, recovery 24, cooldown 100 (Pelt Shed 40). */
	static SignatureMove furrowRun() {
		return new Sig("furrow_run", "Furrow Run", Telegraph.Weight.SIGNATURE, 16, 100, 100, 22F, 3, 18, 14, true, true, ALWAYS, NOTHING, (b, t) -> {
			Vec3 d = dirTo(b, t);
			b.startPass(d, 0.40, 35, 24, e -> {
				b.swingForAttack(InteractionHand.MAIN_HAND);
				hit(b, e, 22F, DamageCaps.SIGNATURE);
				b.bump("furrow_hit");
			}, () -> {});
		});
	}

	/** Pelt Leap: raw 26 plus 14 on a second slam 10 ticks later, wind-up 20, a 7-block leap, open 20 ticks, cooldown 160 (P2 100); off in Pelt Shed. */
	static SignatureMove peltLeap() {
		return new Sig("pelt_leap", "Pelt Leap", Telegraph.Weight.SIGNATURE, 20, 160, 100, 26F, 3, 9, 14, true, true, (b, t) -> !b.shed(), NOTHING, (b, t) -> {
			Vec3 d = dirTo(b, t);
			double dist = Math.min(7.0, b.distanceTo(t));
			b.startLeap(b.position().add(d.scale(dist)), 0.55, 20, true, () -> {       // lands open for 20 ticks
				ServerLevel level = lv(b);
				slam(b, 26F, DamageCaps.SIGNATURE);
				Timers.after(level.getServer(), 10, () -> {                           // the double slam: raw 26 + 14
					if (b.isAlive()) slam(b, 14F, DamageCaps.MINOR);
				});
			});
		});
	}

	static void slam(HrafnholtBoss b, float raw, DamageCaps kind) {
		ServerLevel level = lv(b);
		b.swingForAttack(InteractionHand.MAIN_HAND);
		b.bump("pelt_slam");
		for (LivingEntity e : b.foes(level, b.position(), 2.6)) hit(b, e, raw, kind);
		level.sendParticles(ParticleTypes.CRIT, b.getX(), b.getY() + 0.3, b.getZ(), 14, 1.0, 0.2, 1.0, 0.2);
		level.playSound(null, b.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.2F, 0.7F);
	}

	/** Wolf Howl: once at 50% HP, a 40-tick roar, knockback 3, and every Berserker within 24 blocks gets +20% speed for 160 ticks. */
	static SignatureMove wolfHowl() {
		return new Sig("wolf_howl", "Wolf Howl", Telegraph.Weight.HEAVY, 40, 600, 600, 0F, 0, 128, 100, false, true, (b, t) -> b.phaseIndex() >= 1 && b.stat("wolf_howl") == 0, NOTHING, (b, t) -> {
			if (!b.isAlive()) return;
			ServerLevel level = lv(b);
			for (LivingEntity e : b.foes(level, b.position(), 6.0)) push(b, e, 3.0);
			Identifier berserker = HrafnholtContent.id("hrafnholt_berserker");
			for (KingdomSoldier s : level.getEntitiesOfClass(KingdomSoldier.class, b.getBoundingBox().inflate(24), x -> x.isAlive() && berserker.equals(x.unitId()))) {
				s.addEffect(new MobEffectInstance(MobEffects.SPEED, 160, 1));
			}
			b.howls++;
			ring(level, b.position(), 4.0, ParticleTypes.CLOUD);
		});
	}

	// ---- H6 Huntmaster ------------------------------------------------------------------------------------------------------------------

	/** Wolf Pack: a 24-tick whistle, then two wolves run in from behind her; one respawns once at 50% HP. They leave with the fight. */
	static SignatureMove wolfPack() {
		return new Sig("wolf_pack", "Wolf Pack", Telegraph.Weight.SIGNATURE, 24, 600, 600, 10F, 0, 40, 12, false, false, (b, t) -> b.wolvesSpawned == 0, NOTHING, (b, t) -> {
			b.spawnWolves(lv(b), 2);
			lv(b).playSound(null, b.blockPosition(), SoundEvents.WOLF_GROWL_BABY.value(), SoundSource.HOSTILE, 1.5F, 0.5F);
		});
	}

	/** Pinning Arrow: raw 16 plus Slowness I 40, draw 17 ticks (arrow tip white), cooldown 120 (P2 80), shot on the move. */
	static SignatureMove pinningArrow() {
		return new Sig("pinning_arrow", "Pinning Arrow", Telegraph.Weight.SIGNATURE, 17, 120, 80, 16F, 4, 28, 14, false, false, (b, t) -> b.hasLineOfSight(t), (b, t) -> b.drawBow(true), (b, t) -> {
			b.shootArrow(t, 16F, 0.0F, true);
			b.drawBow(false);
			b.bump("pin_arrow");
		});
	}

	/** Pelt Dive: a 16-tick crouch, a 6-block backward leap, then a shot; cooldown 200. */
	static SignatureMove peltDive() {
		return new Sig("pelt_dive", "Pelt Dive", Telegraph.Weight.SIGNATURE, 16, 200, 200, 0F, 0, 3.0, 12, true, true, ALWAYS, NOTHING, (b, t) -> {
			Vec3 d = dirTo(b, t);
			b.startLeap(b.position().subtract(d.scale(6.0)), 0.5, 0, false, () -> {
				b.bump("dive_land");
				LivingEntity tg = b.getTarget();
				if (tg != null && tg.isAlive() && b.hasLineOfSight(tg)) b.shootArrow(tg, 14F, 0.0F, false);
			});
		});
	}
}
