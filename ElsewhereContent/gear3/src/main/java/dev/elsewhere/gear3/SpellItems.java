package dev.elsewhere.gear3;

import dev.elsewhere.arsenal.spell.CasterItem;
import dev.elsewhere.arsenal.spell.SpellLines;
import dev.elsewhere.arsenal.spell.Spells;
import dev.elsewhere.stub.arsenal.BossCheck;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Gear3 spell items on arsenal's {@link CasterItem} (Glimmer cost, use delay and durability are handled there). */
public final class SpellItems {
	private SpellItems() {}

	// numbers from GEAR.md A5
	public static final int GRIMOIRE_COST = 6, GRIMOIRE_DELAY = 20, CHALICE_COST = 8, CHALICE_DELAY = 40, KA_COST = 10, KA_DELAY = 60, LANTERN_COST = 2, LANTERN_DELAY = 10,
		BLINK_COST = 12, BLINK_DELAY = 40, EMBER_COST = 8, EMBER_DELAY = 24, FROST_COST = 7, FROST_DELAY = 20, ORRERY_COST = 20, ORRERY_DELAY = 60;
	public static final float GRIMOIRE_DAMAGE = 4, CHALICE_HEAL = 4, KA_ABSORB = 6, EMBER_DAMAGE = 10, STAR_DAMAGE = 9, FROST_DPS = 3;
	public static final double GRIMOIRE_RANGE = 20, BLINK_RANGE = 8, EMBER_RANGE = 24, EMBER_RADIUS = 3, FROST_LENGTH = 10, ORRERY_RANGE = 32, ORRERY_RADIUS = 4;

	/** First living target on the player's line of sight within range (not friendly), or null. */
	static LivingEntity firstTarget(ServerLevel level, ServerPlayer p, double range) {
		for (LivingEntity t : SpellLines.trace(level, p, range, 0, false).targets()) if (!Spells.friendly(p, t)) return t;
		return null;
	}

	/** Where the line of sight ends (first block or range). */
	static Vec3 aimPoint(ServerLevel level, ServerPlayer p, double range) {
		return SpellLines.trace(level, p, range, 0, false).end();
	}

	static boolean isBoss(LivingEntity e) {
		return BossCheck.isBoss(e);
	}

	public static class AmberGrimoire extends CasterItem {
		public AmberGrimoire(Properties p) {
			super(p, GRIMOIRE_DELAY, false);
		}

		@Override
		public int rawCost(Player p) {
			return GRIMOIRE_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			SpellLines.Line line = SpellLines.trace(level, p, GRIMOIRE_RANGE, 0, false);
			SpellLines.particles(level, p.getEyePosition().add(p.getLookAngle()), line.end(), ParticleTypes.WAX_ON, 1.0);
			for (LivingEntity t : line.targets()) {
				if (Spells.friendly(p, t)) continue;
				amberLock(p, t, Spells.damage(p, t, GRIMOIRE_DAMAGE, false, moonlit));
				break;
			}
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		}

		public static void amberLock(ServerPlayer p, LivingEntity t, float damage) {
			Spells.hurt(p, p, t, damage);
			if (isBoss(t)) {
				t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 0), p);
			} else {
				t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3), p);
				t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1), p);
			}
		}
	}

	public static class AlmonersChalice extends CasterItem {
		public AlmonersChalice(Properties p) {
			super(p, CHALICE_DELAY, false);
		}

		@Override
		public int rawCost(Player p) {
			return CHALICE_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			Vec3 at = aimPoint(level, p, 12);
			splash(level, p, at);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
		}

		/** Heals players and the caster's minions within 3 blocks of {@code at}; +25% with the Almoner's Cowl on the head. Returns how many were healed. */
		public static int splash(ServerLevel level, ServerPlayer p, Vec3 at) {
			float heal = CHALICE_HEAL;
			ItemStack head = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
			if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(head.getItem()).getPath().equals("mirewick_almoners_cowl")) heal *= 1.25F;
			int n = 0;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3.0))) {
				if (e.position().distanceTo(at) > 3.0 + e.getBbWidth()) continue;
				boolean ally = e instanceof Player || e instanceof OwnableEntity o && o.getOwner() == p;
				if (!ally || !e.isAlive()) continue;
				e.heal(heal);
				level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
				n++;
			}
			return n;
		}
	}

	public static class KaSigil extends CasterItem {
		public KaSigil(Properties p) {
			super(p, KA_DELAY, false);
		}

		@Override
		public int rawCost(Player p) {
			return KA_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			Player target = p;
			if (!p.isShiftKeyDown()) {
				for (LivingEntity t : SpellLines.trace(level, p, 20, 0, false).targets()) if (t instanceof Player o && o != p) {
					target = o;
					break;
				}
			}
			ward(target);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.5F);
		}

		/** Absorption 6 HP for 8 s. */
		public static void ward(Player target) {
			target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 1, false, true));
			target.setAbsorptionAmount(KA_ABSORB);
		}
	}

	public static class GlimmerLantern extends CasterItem {
		public GlimmerLantern(Properties p) {
			super(p, LANTERN_DELAY, false);
		}

		@Override
		public int rawCost(Player p) {
			return LANTERN_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			SpellLines.Line line = SpellLines.trace(level, p, 24, 1, false);
			// the air block in front of where the line ends (never replaces a block)
			Vec3 dir = p.getLookAngle().normalize();
			BlockPos pos = BlockPos.containing(line.end().subtract(dir.scale(0.3)));
			if (!Gear3LightBlocks.place(p, level, pos)) {
				BlockPos alt = BlockPos.containing(line.end().subtract(dir.scale(1.0)));
				Gear3LightBlocks.place(p, level, alt);
			}
		}
	}

	public static class Blinkstone extends CasterItem {
		public Blinkstone(Properties p) {
			super(p, BLINK_DELAY, false);
		}

		@Override
		public int rawCost(Player p) {
			return BLINK_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			if (BossCheck.inBossFight(p)) return;
			blink(level, p);
		}

		/** Short teleport along the view, stopping before any block; returns the distance moved. */
		public static double blink(ServerLevel level, ServerPlayer p) {
			Vec3 from = p.position(), dir = p.getLookAngle().normalize();
			Vec3 best = from;
			for (double d = 0.5; d <= BLINK_RANGE; d += 0.5) {
				Vec3 at = from.add(dir.scale(d));
				if (!level.noCollision(p, p.getBoundingBox().move(at.subtract(from)))) break;
				best = at;
			}
			if (best.equals(from)) return 0;
			level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 20, 0.3, 0.5, 0.3, 0.1);
			p.teleportTo(best.x, best.y, best.z);
			level.sendParticles(ParticleTypes.PORTAL, best.x, best.y + 1, best.z, 20, 0.3, 0.5, 0.3, 0.1);
			level.playSound(null, best.x, best.y, best.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
			return from.distanceTo(best);
		}
	}

	public static class EmberfallRod extends CasterItem {
		public EmberfallRod(Properties p) {
			super(p, EMBER_DELAY, false);
		}

		@Override
		public int rawCost(Player p) {
			return EMBER_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			SpellLines.Line line = SpellLines.trace(level, p, EMBER_RANGE, 1, false);
			Vec3 at = line.targets().isEmpty() ? line.end() : line.targets().get(0).position();
			burst(level, p, at, moonlit);
		}

		/** 10 damage to every non-friendly mob within 3 blocks, fire 3 s; never breaks or lights a block. Returns how many were hit. */
		public static int burst(ServerLevel level, ServerPlayer p, Vec3 at, boolean moonlit) {
			SpellLines.particles(level, p.getEyePosition().add(p.getLookAngle()), at, ParticleTypes.FLAME, 1.0);
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.8F);
			int n = 0;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(EMBER_RADIUS))) {
				if (Spells.friendly(p, e) || e.position().distanceTo(at) > EMBER_RADIUS + e.getBbWidth() / 2) continue;
				Spells.hurt(p, p, e, Spells.damage(p, e, EMBER_DAMAGE, false, moonlit));
				e.igniteForSeconds(3);
				n++;
			}
			return n;
		}
	}

	public static class FrostwakeScepter extends CasterItem {
		public FrostwakeScepter(Properties p) {
			super(p, FROST_DELAY, false);
		}

		@Override
		public int rawCost(Player p) {
			return FROST_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			SpellLines.Line line = SpellLines.trace(level, p, FROST_LENGTH, 0, false);
			SpellLines.particles(level, p.getEyePosition().add(p.getLookAngle()), line.end(), ParticleTypes.SNOWFLAKE, 0.5);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
			for (LivingEntity t : line.targets()) {
				if (Spells.friendly(p, t)) continue;
				chill(level, p, t, moonlit);
			}
		}

		/** Slowness II 3 s and 3 freeze damage per second for 3 s (1 tick now, then at 1 s and 2 s). */
		public static float chill(ServerLevel level, ServerPlayer p, LivingEntity t, boolean moonlit) {
			t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), p);
			t.setTicksFrozen(Math.max(t.getTicksFrozen(), 100));
			// s-compat-testmeter: every tick is the full spell damage (spell power, Calling, Test Meter half bonus, recast scale), not raw 3
			final float tick = Spells.damage(p, t, FROST_DPS, false, moonlit);
			Spells.hurt(p, p, t, tick);
			for (int s = 1; s <= 2; s++) Gear3Scheduler.after(level.getServer(), s * 20, () -> {
				if (t.isAlive()) Spells.hurt(p, p, t, tick);
			});
			return tick;
		}
	}

	public static class StarfallOrrery extends CasterItem {
		public StarfallOrrery(Properties p) {
			super(p, ORRERY_DELAY, false);
		}

		@Override
		public int rawCost(Player p) {
			return ORRERY_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			Vec3 center = aimPoint(level, p, ORRERY_RANGE);
			final int hurtAtCast = p.getLastHurtByMobTimestamp();
			level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 1, center.z, 30, ORRERY_RADIUS / 2, 0.5, ORRERY_RADIUS / 2, 0.02);
			// 2 s channel, then five stars; being hurt during the channel cancels it
			Gear3Scheduler.after(level.getServer(), 40, () -> {
				if (!p.isAlive() || p.getLastHurtByMobTimestamp() != hurtAtCast) return;
				stars(level, p, center, moonlit);
			});
		}

		/** Five 9-damage stars at random points in the 4-block circle; each hits mobs within 1.5 blocks of where it lands. */
		public static int stars(ServerLevel level, ServerPlayer p, Vec3 center, boolean moonlit) {
			int hits = 0;
			for (int i = 0; i < 5; i++) {
				double a = level.getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(level.getRandom().nextDouble()) * ORRERY_RADIUS;
				Vec3 at = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
				level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y + 1, at.z, 12, 0.2, 0.6, 0.2, 0.05);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8F, 1.4F);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.5))) {
					if (Spells.friendly(p, e)) continue;
					if (Spells.hurt(p, p, e, Spells.damage(p, e, STAR_DAMAGE, false, moonlit))) hits++;
				}
			}
			return hits;
		}
	}

	public static List<Class<? extends CasterItem>> all() {
		return List.of(AmberGrimoire.class, AlmonersChalice.class, KaSigil.class, GlimmerLantern.class, Blinkstone.class, EmberfallRod.class, FrostwakeScepter.class, StarfallOrrery.class);
	}
}
