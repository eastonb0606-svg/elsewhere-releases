package dev.elsewhere.rivals.mirewick.boss;

import dev.elsewhere.duelist.boss.Behaviours;
import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.SignatureMove;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.boss.TelegraphStyle;
import dev.elsewhere.duelist.kit.DamageCaps;
import dev.elsewhere.duelist.kit.DuelistProjectile;
import dev.elsewhere.duelist.kit.GroundPatch;
import dev.elsewhere.duelist.kit.ReachAttack;
import dev.elsewhere.duelist.kit.TempStructure;
import dev.elsewhere.duelist.kit.Timers;
import dev.elsewhere.kingdom.FreeCompanies;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.rivals.mirewick.MirewickContent;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The five Magnate profiles, built exactly from their v3 identity cards (RIVAL-BOSSES M1-M5, {@code data/elsewhere/boss_identity}).
 * Nothing is shared: each profile opts into only the behaviours on its card, with that card's own numbers, and carries its own
 * telegraph look. Marrow circle-strafes at chain range; Garrick eats an eel pie once; Hilde blocks with a tower shield and is
 * the only builder; Wren uses none of the generic behaviours; Cuthbert drinks once and (his own Paid Passage) pearls once.
 * Damage goes through {@code hurtRaw} so the kit's caps hold; every move is telegraphed (the kit checks the minimums on build).
 * The movement slot lives in {@link MagnateBoss#steer}, the defence, recovery and last-stand slots in {@link MagnateBoss}.
 */
public final class MagnateProfiles {
	private MagnateProfiles() {}

	private static final Map<Magnate, DuelistProfile> CACHE = new EnumMap<>(Magnate.class);
	public static final Identifier BRACE_KB = MirewickContent.id("mirewick_brace");
	/** Measured on 26.3 (SelfTest "SPEEDPROBE", flat ground, a Mob walking to a point, blocks moved over 100 ticks): attribute 0.1 gives 0.0216 blocks per tick
	 *  and attribute 0.2 gives 0.0863, so a mob's ground speed is {@code K x attribute squared} (the speed modifier and the attribute both feed the move),
	 *  with K = 2.16. HumanBoss writes {@code walkSpeed} straight into MOVEMENT_SPEED, so a profile's walkSpeed is this attribute value. */
	static final double K = 2.16;
	/** The movement attribute (HumanBoss walkSpeed) that walks {@code bpt} blocks per tick. */
	static double attr(double bpt) {
		return Math.sqrt(bpt / K);
	}
	/** Blocks per tick a mob with this movement attribute walks (inverse of {@link #attr}). */
	public static double bpt(double attribute) {
		return K * attribute * attribute;
	}
	static final int BLOCK_LIFE = 600;

	public static synchronized DuelistProfile of(Magnate m) {
		return CACHE.computeIfAbsent(m, MagnateProfiles::build);
	}

	/** The telegraph look from the card (particle, colour, sound, pitch), so {@code BossIdentityAudit.checkProfile} agrees with the JSON. */
	static TelegraphStyle look(Magnate m) {
		return switch (m) {
			case MARROW -> TelegraphStyle.of("wax_on", 0xE8C547, "block.chain.hit", TelegraphStyle.Shape.SPIRAL, 0.8F);
			case THWAITE -> TelegraphStyle.of("bubble", 0x7FA06A, "block.note_block.basedrum", TelegraphStyle.Shape.COLUMN, 0.6F);
			case SORREL -> TelegraphStyle.of("falling_water", 0x6B5A44, "block.mud_bricks.place", TelegraphStyle.Shape.RING, 0.6F);
			case TOLLIVER -> TelegraphStyle.of("note", 0x9ACD6E, "entity.parrot.ambient", TelegraphStyle.Shape.HALO, 1.8F);
			case AMES -> TelegraphStyle.of("flame", 0xE0A83A, "block.bell.use", TelegraphStyle.Shape.HALO, 1.1F);
		};
	}

	private static DuelistProfile build(Magnate m) {
		DuelistProfile.Builder b = DuelistProfile.builder("mirewick_" + m.key).duelMode(true).caps(18F, 38F).barColor(BossEvent.BossBarColor.GREEN)
			.skin("kingdom/mirewick/magnate_" + m.key).identity("mirewick_" + m.key).telegraph(look(m));
		switch (m) {
			case MARROW -> {
				b.hp(246).walkSpeed(attr(0.18)).swing(14F, 22, 8).reach(2.8).phases(0.5, 0.25).melee(() -> new ItemStack(Items.IRON_SWORD))
					.strafe(new Behaviours.Strafe(1.0F, 30, 60, 6.0, 8.0, 40));
				b.signature(callTheDebt()).signature(garnish());
			}
			case THWAITE -> {
				b.hp(246).walkSpeed(attr(0.14)).swing(16F, 28, 8).reach(5.5).phases(0.5, 0.25).melee(() -> new ItemStack(Items.DIAMOND_SPEAR))
					.food(() -> new ItemStack(Items.PUMPKIN_PIE)).eat(new Behaviours.Eat(0.10F, 60, new Behaviours.HealRule(0.55F, 3.0, 1, 120)));
				b.signature(longThrust()).signature(pikeBrace()).signature(bogDrumCall());
			}
			case SORREL -> {
				b.hp(257).walkSpeed(attr(0.10)).swing(16F, 36, 8).reach(3.0).phases(0.5, 0.25).melee(() -> new ItemStack(Items.MACE))
					.shield(new Behaviours.Shield(5, 24, 60, 12, 140F, 100, new Behaviours.Bash(8, 120, 8F, 1.0F)))
					.tempBlocks(new Behaviours.TempBlocks(8, BLOCK_LIFE, 8, 0, 0));
				b.signature(dikeWall()).signature(sluiceMaul()).signature(mudSurge());
			}
			case TOLLIVER -> {
				b.hp(236).walkSpeed(attr(0.30)).swing(12F, 20, 6).reach(2.8).phases(0.5, 0.20).melee(() -> new ItemStack(Items.IRON_AXE))
					.ranged(() -> new ItemStack(Items.BOW)).bow(new Behaviours.Bow(22, 36, 4.0, 10.0, 0.15, 9.0, 0.5, 14F))
					.swap(new Behaviours.Swap(3.5, 8.0, 7, 40, 14, 100, 0F));
				b.signature(fowlingNet()).signature(heronFlight()).signature(reedDive());
			}
			case AMES -> {
				b.hp(204).walkSpeed(attr(0.20)).swing(10F, 26, 6).reach(2.8).phases(0.5, 0.20).melee(() -> new ItemStack(Items.IRON_SHOVEL))
					.potion(HumanBoss::healingPotion).drink(new Behaviours.Drink(0.15F, new Behaviours.HealRule(0.5F, 4.0, 1, 120)))
					// Paid Passage is Cuthbert's own move (MagnateBoss.paidPassage); the kit's pearl is parked so it never fires by itself
					.pearl(new Behaviours.Pearl(12.0, 20.0, 20.0, 1_000_000, 16, 1_000_000, 1, 2F));
				b.signature(duskRite()).signature(payTheHands()).signature(duskBell());
			}
		}
		return b.build();
	}

	// ---- a signature move as data ------------------------------------------------------------------------------------------

	static final class Sig implements SignatureMove {
		final String id, label;
		final Telegraph.Weight w;
		final int windup, cd, cd2, prio;
		final float raw;
		final double min, max;
		final boolean crouch;
		final BiPredicate<MagnateBoss, LivingEntity> can;
		final BiConsumer<MagnateBoss, LivingEntity> fire;

		Sig(String id, String label, Telegraph.Weight w, int windup, int cd, int cd2, float raw, double min, double max, int prio, boolean crouch,
			BiPredicate<MagnateBoss, LivingEntity> can, BiConsumer<MagnateBoss, LivingEntity> fire) {
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
			this.can = can;
			this.fire = fire;
		}

		public String id() { return id; }
		public String label() { return label; }
		public Telegraph.Weight weight() { return w; }
		public int windup() { return windup; }
		public int cooldown() { return cd; }   // raw: the single -20 percent / defence scaling lives in MagnateBoss.setCooldown
		public float raw() { return raw; }
		public double minRange() { return min; }
		public double maxRange() { return max; }
		public int priority() { return prio; }

		@Override
		public Telegraph.Cue cue(HumanBoss boss) {
			return boss.style().cue(crouch, !crouch);
		}

		@Override
		public boolean canUse(HumanBoss boss, LivingEntity target) {
			return can.test((MagnateBoss) boss, target);
		}

		public void fire(HumanBoss boss, LivingEntity target) {
			MagnateBoss mb = (MagnateBoss) boss;
			fire.accept(mb, target);
			if (mb.phaseIndex() >= 1 && cd2 != cd) boss.setCooldown("sig:" + id, cd2);
		}
	}

	// ---- helpers -----------------------------------------------------------------------------------------------------------

	static ServerLevel lv(MagnateBoss b) {
		return (ServerLevel) b.level();
	}

	static Vec3 dirTo(MagnateBoss b, LivingEntity t) {
		Vec3 d = t.position().subtract(b.position()).multiply(1, 0, 1);
		return d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
	}

	static Vec3 ground(ServerLevel level, Vec3 around) {
		BlockPos p = BlockPos.containing(around);
		for (int i = 0; i < 6 && level.getBlockState(p).isAir(); i++) p = p.below();
		return new Vec3(around.x, p.getY() + 1.0, around.z);
	}

	static void kick(LivingEntity e, Vec3 v) {
		e.setDeltaMovement(v);
		e.needsSync = true;
	}

	/** A pearl-style hop: a safe spot {@code min} to {@code max} blocks off, never inside a block. */
	static boolean hop(MagnateBoss b, double min, double max) {
		ServerLevel level = lv(b);
		for (int tries = 0; tries < 16; tries++) {
			double ang = b.getRandom().nextDouble() * Math.PI * 2, r = min + b.getRandom().nextDouble() * (max - min);
			BlockPos p = BlockPos.containing(b.getX() + Math.cos(ang) * r, b.getY(), b.getZ() + Math.sin(ang) * r);
			if (b.home.distSqr(p) > (double) b.arenaRadius * b.arenaRadius) continue;
			for (int dy = 4; dy >= -4; dy--) {
				BlockPos q = p.offset(0, dy, 0);
				BlockState floor = level.getBlockState(q.below());
				if (floor.isSolid() && level.getBlockState(q).isAir() && level.getBlockState(q.above()).isAir() && level.getFluidState(q).isEmpty()) {
					level.sendParticles(ParticleTypes.PORTAL, b.getX(), b.getY() + 1.0, b.getZ(), 20, 0.3, 0.6, 0.3, 0.3);
					b.teleportTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
					level.playSound(null, q, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
					b.hopsDone++;
					return true;
				}
			}
		}
		return false;
	}

	// ---- Marrow: the debt-hook --------------------------------------------------------------------------------------------------

	static SignatureMove callTheDebt() {
		return new Sig("call_the_debt", "Call the Debt", Telegraph.Weight.SIGNATURE, 20, 300, 200, 12F, 2, 9, 10, false,
			(b, t) -> b.hasLineOfSight(t) && b.garnishUntil <= lv(b).getGameTime(), (b, t) -> {
				hook(b, t);
				if (b.doubleDebtActive()) Timers.after(lv(b).getServer(), 10, () -> {
					if (b.isAlive() && t.isAlive()) hook(b, t);
				});
				if (b.doubleDebtActive()) b.setCooldown("sig:call_the_debt", 100);
			});
	}

	/** One chain hook: it flies at 1.5 blocks per tick, a raised shield bounces it, otherwise raw 12 and a 6-block drag. */
	static void hook(MagnateBoss b, LivingEntity t) {
		ServerLevel level = lv(b);
		double d0 = b.distanceTo(t);
		int flight = Math.max(1, (int) Math.ceil(Math.min(d0, 9.0) / 1.5));
		for (int i = 1; i <= flight; i++) {
			final int step = i;
			Timers.after(level.getServer(), i, () -> {
				if (!b.isAlive()) return;
				double f = Math.min(1.0, step * 1.5 / Math.max(1.0, d0));
				Vec3 a = b.position().add(0, 1.2, 0), c = t.position().add(0, 1.0, 0);
				level.sendParticles(ParticleTypes.WAX_ON, a.x + (c.x - a.x) * f, a.y + (c.y - a.y) * f, a.z + (c.z - a.z) * f, 2, 0.05, 0.05, 0.05, 0.0);
				level.sendParticles(ParticleTypes.CRIT, a.x + (c.x - a.x) * f, a.y + (c.y - a.y) * f, a.z + (c.z - a.z) * f, 1, 0, 0, 0, 0.0);
			});
		}
		Timers.after(level.getServer(), flight, () -> {
			if (!b.isAlive() || !t.isAlive() || b.distanceTo(t) > 10.0 || !b.hasLineOfSight(t)) return;
			if (t.isBlocking()) {
				level.playSound(null, t.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
				b.bump("hook_bounced");
				return;
			}
			if (!b.hurtRaw(level, t, 12F, DamageCaps.SIGNATURE, level.damageSources().mobAttack(b))) return;
			b.bump("hook_hit");
			pull(b, t, 6.0, 10);
			b.pulledAt = level.getGameTime();
		});
	}

	/** Drags {@code t} up to {@code blocks} toward the boss over {@code ticks}, stopping 2 blocks away; never through blocks, into water or over a drop of 3. */
	static void pull(MagnateBoss b, LivingEntity t, double blocks, int ticks) {
		ServerLevel level = lv(b);
		Vec3 dir = b.position().subtract(t.position()).multiply(1, 0, 1);
		double dist = dir.length();
		if (dist <= 2.0) return;
		double go = Math.min(blocks, dist - 2.0);
		dir = dir.normalize();
		Vec3 dest = t.position().add(dir.scale(go));
		BlockPos dp = BlockPos.containing(dest);
		boolean floor = false;
		for (int i = 0; i <= 3 && !floor; i++) floor = level.getBlockState(dp.below(i + 1)).isSolid() || !level.getFluidState(dp.below(i)).isEmpty();
		if (!floor || !level.getFluidState(dp).isEmpty() || level.getFluidState(BlockPos.containing(t.position())).is(net.minecraft.tags.FluidTags.WATER)) return;
		if (level.clip(new net.minecraft.world.level.ClipContext(t.position().add(0, 0.5, 0), dest.add(0, 0.5, 0), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, t)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) return;
		final Vec3 step = dir.scale(go / ticks);
		for (int i = 0; i < ticks; i++) Timers.after(level.getServer(), i, () -> {
			if (!t.isAlive() || !b.isAlive() || t.distanceTo(b) <= 2.0) return;
			kick(t, new Vec3(step.x, Math.max(t.getDeltaMovement().y, 0.05), step.z));
		});
	}

	static SignatureMove garnish() {
		return new Sig("garnish", "Garnish", Telegraph.Weight.SIGNATURE, 14, 400, 400, 0F, 0, 6, 20, false,
			(b, t) -> lv(b).getGameTime() - b.pulledAt < 40, (b, t) -> b.startGarnish(lv(b), t));
	}

	// ---- Thwaite: the pike -------------------------------------------------------------------------------------------------

	static SignatureMove longThrust() {
		return new Sig("long_thrust", "Long Thrust", Telegraph.Weight.SIGNATURE, 14, 80, 80, 28F, 0, 5.5, 14, false,
			(b, t) -> !b.allyInLine(lv(b), t), (b, t) -> {
				thrust(b, t);
				if (b.phaseIndex() >= 1 && !b.secondThrust) {
					b.secondThrust = true;
					Timers.after(lv(b).getServer(), 18, () -> {
						if (b.isAlive() && t.isAlive()) thrust(b, t);
						b.secondThrust = false;
					});
				}
			});
	}

	static void thrust(MagnateBoss b, LivingEntity t) {
		ServerLevel level = lv(b);
		b.faceNow(t);
		ReachAttack.hit(level, b, t, b.pikeReach(level), 2.0, 28F, true, DamageCaps.SIGNATURE);
		Vec3 d = dirTo(b, t);
		for (int i = 1; i <= 5; i++) level.sendParticles(ParticleTypes.CRIT, b.getX() + d.x * i, b.getY() + 1.3, b.getZ() + d.z * i, 1, 0.05, 0.05, 0.05, 0.0);
	}

	static SignatureMove pikeBrace() {
		return new Sig("pike_brace", "Pike Brace", Telegraph.Weight.HEAVY, 30, 300, 200, 22F, 0, 4, 8, false, (b, t) -> true, (b, t) -> {
			b.braceUntil = lv(b).getGameTime() + 30;
			AttributeInstance kb = b.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (kb != null && !kb.hasModifier(BRACE_KB)) kb.addTransientModifier(new AttributeModifier(BRACE_KB, 1.0, AttributeModifier.Operation.ADD_VALUE));
			b.braceHit.clear();
		});
	}

	static void bracePunish(ServerLevel level, MagnateBoss b) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, b.getBoundingBox().inflate(4.0, 1.5, 4.0), x -> x instanceof Player p && !p.isCreative() && !p.isSpectator())) {
			Long last = b.braceHit.get(e.getId());
			if (last != null && b.tickCount - last < 20) continue;
			b.braceHit.put(e.getId(), (long) b.tickCount);
			b.hurtRaw(level, e, 22F, DamageCaps.SIGNATURE, level.damageSources().mobAttack(b));
		}
	}

	static void braceEnd(MagnateBoss b) {
		b.braceUntil = 0;
		AttributeInstance kb = b.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) kb.removeModifier(BRACE_KB);
	}

	static SignatureMove bogDrumCall() {
		return new Sig("bog_drum_call", "Bog-Drum Call", Telegraph.Weight.SIGNATURE, 24, 400, 400, 0F, 0, 64, 4, false, (b, t) -> b.others(lv(b), 24) > 0, (b, t) -> {
			ServerLevel level = lv(b);
			for (MagnateBoss o : MagnateBoss.all(level)) if (o != b && o.distanceToSqr(b) <= 24 * 24) o.addEffect(new MobEffectInstance(MobEffects.SPEED, 120, 0));
			level.sendParticles(ParticleTypes.NOTE, b.getX(), b.getY() + 2.2, b.getZ(), 12, 0.6, 0.2, 0.6, 1.0);
		});
	}

	// ---- Sorrel: the dikes -------------------------------------------------------------------------------------------------------

	static SignatureMove dikeWall() {
		return new Sig("dike_wall", "Dike Wall", Telegraph.Weight.HEAVY, 24, 280, 280, 0F, 3, 10, 14, false, (b, t) -> true, (b, t) -> buildWalls(b, t, b.phaseIndex() >= 1 ? 2 : 1));
	}

	/** One or two 3x3 temporary walls between her and the target (two form a corridor). She then stands behind the first. */
	static void buildWalls(MagnateBoss b, LivingEntity t, int walls) {
		ServerLevel level = lv(b);
		Vec3 d = dirTo(b, t);
		Direction f = Direction.getApproximateNearest(d.x, 0, d.z);
		for (int w = 0; w < walls; w++) {
			b.setCooldown("tempstruct", 0);
			Vec3 base = b.position().add(d.scale(2.0 + w * 3.0));
			BlockPos bp = BlockPos.containing(base);
			if (TempStructure.wall(level, b, bp, f, 3, 3, 18, BLOCK_LIFE, 0) > 0 && w == 0) {
				b.wallAt = base;
				b.wallUntil = level.getGameTime() + BLOCK_LIFE;
			}
		}
	}

	static SignatureMove sluiceMaul() {
		return new Sig("sluice_maul", "Sluice Maul", Telegraph.Weight.HEAVY, 24, 240, 200, 34F, 0, 3.5, 16, false, (b, t) -> true, (b, t) -> {
			ServerLevel level = lv(b);
			for (LivingEntity e : foes(level, b, 2.5)) {
				b.hurtRaw(level, e, 34F, DamageCaps.SIGNATURE, level.damageSources().mobAttack(b));
				e.knockback(2.0, -(e.getX() - b.getX()), -(e.getZ() - b.getZ()), level.damageSources().mobAttack(b), 0.0F);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, b.getX(), b.getY() + 0.2, b.getZ(), 6, 1.0, 0.1, 1.0, 0.0);
			level.playSound(null, b.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8F, 0.7F);
			b.openUntil = level.getGameTime() + 30;                                           // open for 30 ticks after the smash
		});
	}

	static SignatureMove mudSurge() {
		return new Sig("mud_surge", "Mud Surge", Telegraph.Weight.SIGNATURE, 16, 240, 240, 8F, 0, 6, 10, false, (b, t) -> true, (b, t) -> {
			ServerLevel level = lv(b);
			ParticleOptions mud = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
			GroundPatch.spawn(level, b, ground(level, b.position()), 3.0, 0, 60, 20, 8F, List.of(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1)), mud);
		});
	}

	// ---- Tolliver: the reeds -------------------------------------------------------------------------------------------------------

	static SignatureMove fowlingNet() {
		return new Sig("fowling_net", "Fowling Net", Telegraph.Weight.SIGNATURE, 18, 200, 200, 0F, 0, 14, 14, false, (b, t) -> b.hasLineOfSight(t), (b, t) -> {
			ServerLevel level = lv(b);
			DuelistProjectile p = DuelistProjectile.throwAt(level, b, t, new ItemStack(Items.COBWEB), 1F, 1.4F, DamageCaps.MINOR);
			b.nets.add(p);
			if (b.phaseIndex() >= 1) b.setCooldown("sig:heron_flight", 0);                      // P2: the net arrives with a Heron Flight
		});
	}

	static SignatureMove heronFlight() {
		return new Sig("heron_flight", "Heron Flight", Telegraph.Weight.SIGNATURE, 22, 100, 100, 14F, 0, 24, 12, false, (b, t) -> b.hasLineOfSight(t), (b, t) -> {
			ServerLevel level = lv(b);
			Vec3 c = t.position();
			final Vec3[] marks = new Vec3[5];
			for (int i = 0; i < 5; i++) {
				double ang = i * Math.PI * 2 / 5 + b.getRandom().nextDouble() * 0.5;
				marks[i] = ground(level, c.add(Math.cos(ang) * 3.0, 0, Math.sin(ang) * 3.0));
			}
			for (int tick = 0; tick < 20; tick += 2) Timers.after(level.getServer(), tick, () -> {
				for (Vec3 m : marks) level.sendParticles(ParticleTypes.CRIT, m.x, m.y + 0.1, m.z, 2, 0.5, 0.0, 0.5, 0.0);
			});
			Timers.after(level.getServer(), 20, () -> {
				if (!b.isAlive()) return;
				for (Vec3 m : marks) {
					level.sendParticles(ParticleTypes.POOF, m.x, m.y + 0.2, m.z, 6, 0.3, 0.1, 0.3, 0.02);
					for (LivingEntity e : foes(level, b, 64)) if (e.position().distanceToSqr(m) <= 1.2 * 1.2 + 0.3) b.hurtRaw(level, e, 14F, DamageCaps.SIGNATURE, level.damageSources().mobAttack(b));
				}
				level.playSound(null, BlockPos.containing(c), SoundEvents.ARROW_HIT, SoundSource.HOSTILE, 1.0F, 0.8F);
			});
		});
	}

	static SignatureMove reedDive() {
		return new Sig("reed_dive", "Reed Dive", Telegraph.Weight.SIGNATURE, 24, 240, 160, 0F, 0, 4, 20, true, (b, t) -> true, (b, t) -> b.reedDive(lv(b), t));
	}

	// ---- Ames: the hired hands -----------------------------------------------------------------------------------------------------

	static SignatureMove duskRite() {
		return new Sig("dusk_rite", "Dusk-Rite", Telegraph.Weight.HEAVY, 60, 360, 240, 0F, 0, 64, 30, false,
			(b, t) -> b.castsLeft > 0 && (b.healTargetBoss(lv(b)) != null || (b.others(lv(b), 64) == 0 && b.getHealth() < b.getMaxHealth() * 0.9F)),
			(b, t) -> {
				if (b.riteBroken) {
					b.riteBroken = false;
					return;
				}
				ServerLevel level = lv(b);
				MagnateBoss payer = b.healTargetBoss(level);
				LivingEntity who = payer != null ? payer : (b.others(level, 64) == 0 ? b : null);
				if (who == null) return;
				who.heal((float) (who.getMaxHealth() * CouncilRules.healFraction(who == b)));
				b.castsLeft--;
				Vec3 a = b.position().add(0, 1.5, 0), c = who.position().add(0, 1.2, 0);
				for (int i = 0; i <= 8; i++) level.sendParticles(ParticleTypes.END_ROD, a.x + (c.x - a.x) * i / 8, a.y + (c.y - a.y) * i / 8, a.z + (c.z - a.z) * i / 8, 1, 0, 0, 0, 0.0);
			});
	}

	static SignatureMove payTheHands() {
		return new Sig("pay_the_hands", "Pay the Hands", Telegraph.Weight.SIGNATURE, 24, 600, 600, 0F, 0, 64, 16, false,
			(b, t) -> b.pairsHired < 2 && b.livingHands(lv(b)) == 0, (b, t) -> b.hirePair(lv(b), t));
	}

	static SignatureMove duskBell() {
		return new Sig("dusk_bell", "Dusk Bell", Telegraph.Weight.HEAVY, 20, 200, 200, 10F, 0, 5, 12, false, (b, t) -> true, (b, t) -> {
			ServerLevel level = lv(b);
			for (LivingEntity e : foes(level, b, 6.0)) {
				b.hurtRaw(level, e, 10F, DamageCaps.SIGNATURE, level.damageSources().mobAttack(b));
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
			}
			level.playSound(null, b.blockPosition(), dev.elsewhere.rivals.mirewick.Sfx3.pick("magnate", "bell", SoundEvents.BELL_BLOCK), SoundSource.HOSTILE, 2.0F, dev.elsewhere.rivals.mirewick.Sfx3.enabled() ? 0.7F : 0.6F);
			level.sendParticles(ParticleTypes.NOTE, b.getX(), b.getY() + 1.0, b.getZ(), 18, 3.0, 0.3, 3.0, 1.0);
		});
	}

	/** Spawns one Free Company brute as a Hired Hand: 40 HP, armour 6, sword and shield, raw 12 (the war lane's blade unit). */
	static KingdomSoldier spawnHand(ServerLevel level, Vec3 at, LivingEntity target) {
		KingdomSoldier s = KingdomApi.spawnSoldier(level, FreeCompanies.BLADE, FreeCompanies.FACTION, at, null);
		if (s == null) return null;
		AttributeInstance hp = s.getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) hp.setBaseValue(40.0);
		s.setHealth(40F);
		AttributeInstance ar = s.getAttribute(Attributes.ARMOR);
		if (ar != null) ar.setBaseValue(6.0);
		AttributeInstance at2 = s.getAttribute(Attributes.ATTACK_DAMAGE);
		if (at2 != null) at2.setBaseValue(12.0);
		s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		s.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		s.setTarget(target);
		return s;
	}

	static List<LivingEntity> foes(ServerLevel level, MagnateBoss b, double r) {
		return level.getEntitiesOfClass(LivingEntity.class, b.getBoundingBox().inflate(r, 2.0, r),
			e -> e != b && e.isAlive() && ((e instanceof Player p && !p.isCreative() && !p.isSpectator())
				|| (e instanceof KingdomSoldier s && !s.factionId().equals(MirewickContent.FACTION) && !s.factionId().equals(FreeCompanies.FACTION)))).stream()
			.filter(e -> e.distanceToSqr(b) <= r * r + 0.5).toList();
	}
}
