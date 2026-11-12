package dev.elsewhere.arsenal.spell;

import dev.elsewhere.arsenal.ArsenalAttributes;
import dev.elsewhere.arsenal.ArsenalContent;
import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The four caster weapons of ARSENAL.md §4a. */
public final class CasterItems {
	private CasterItems() {}

	/** Amethyst Wand: a 4-damage amethyst bolt (starter: counts as Caster Calling 2+). */
	public static class AmethystWand extends CasterItem {
		public AmethystWand(Properties p) {
			super(p, ArsenalStats.WAND_DELAY_TICKS, true);
		}

		@Override
		public int rawCost(Player p) {
			return ArsenalStats.WAND_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			MagicBolt b = MagicBolt.create(level, p, 0xA77BF3);
			b.damage = ArsenalStats.WAND_DAMAGE;
			b.starter = true;
			b.moonlit = moonlit;
			b.pierce = (int) ArsenalAttributes.value(p, ArsenalAttributes.PROJECTILE_PIERCE);
			b.lifetime = (int) Math.ceil(ArsenalStats.WAND_RANGE / ArsenalStats.WAND_SPEED);
			b.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, ArsenalStats.WAND_SPEED, 0);
			level.addFreshEntity(b);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
		}
	}

	/** Sunlance Staff: an instant 24-block light lance, up to 3 targets, fire 2 s, through glass. */
	public static class SunlanceStaff extends CasterItem {
		public SunlanceStaff(Properties p) {
			super(p, ArsenalStats.SUNLANCE_DELAY_TICKS, false);
		}

		@Override
		public int rawCost(Player p) {
			Level l = p.level();
			BlockPos pos = BlockPos.containing(p.getEyePosition());
			boolean sky = l.canSeeSky(pos);
			if (l.isRainingAt(pos) || !sky && l.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) == 0 || !l.isBrightOutside())
				return ArsenalStats.SUNLANCE_COST_DARK;
			return sky ? ArsenalStats.SUNLANCE_COST_DAY : ArsenalStats.SUNLANCE_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			SpellLines.Line line = SpellLines.trace(level, p, ArsenalStats.SUNLANCE_RANGE, 0, true);
			lastTargets = line.targets().size();
			lastHurt = 0;
			int n = 0;
			for (LivingEntity t : line.targets()) {
				if (Spells.friendly(p, t) && !(t instanceof Player)) continue;
				if (n++ >= ArsenalStats.SUNLANCE_TARGETS) break;
				if (Spells.hurt(p, p, t, Spells.damage(p, t, ArsenalStats.SUNLANCE_DAMAGE, false, moonlit))) lastHurt++;
				t.igniteForSeconds(ArsenalStats.SUNLANCE_FIRE_SECONDS);
			}
			Vec3 s = line.start().add(p.getLookAngle().normalize().scale(1.5)).add(0, -0.2, 0); // start ahead of the camera
			SpellLines.particles(level, s, line.end(), new DustParticleOptions(0xFFD86B, 0.8F), 0.4);
			SpellLines.particles(level, s, line.end(), ParticleTypes.END_ROD, 2.0);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.8F);
		}

		public static int lastTargets, lastHurt;
	}

	/** Stormcoil Staff (rev 3): chain lightning, 8 + arcs of 5 to 2 (wet: 4) more mobs within 5 blocks. */
	public static class StormcoilStaff extends CasterItem {
		public StormcoilStaff(Properties p) {
			super(p, ArsenalStats.STORMCOIL_DELAY_TICKS, false);
		}

		@Override
		public int rawCost(Player p) {
			return ArsenalStats.STORMCOIL_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			SpellLines.Line line = SpellLines.trace(level, p, ArsenalStats.STORMCOIL_RANGE, 0, false);
			LivingEntity first = null;
			for (LivingEntity t : line.targets()) {
				if (!Spells.friendly(p, t)) {
					first = t;
					break;
				}
			}
			Vec3 from = line.start().add(p.getLookAngle().normalize().scale(1.5)).add(0, -0.2, 0); // start ahead of the camera
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
			if (first == null) {
				SpellLines.particles(level, from, line.end(), ParticleTypes.ELECTRIC_SPARK, 0.5);
				return;
			}
			SpellLines.particles(level, from, first.getBoundingBox().getCenter(), ParticleTypes.ELECTRIC_SPARK, 0.4);
			Spells.hurt(p, p, first, Spells.damage(p, first, ArsenalStats.STORMCOIL_DAMAGE, false, moonlit));
			List<LivingEntity> struck = new ArrayList<>();
			struck.add(first);
			int arcs = first.isInWaterOrRain() ? ArsenalStats.STORMCOIL_ARCS_WET : ArsenalStats.STORMCOIL_ARCS;
			LivingEntity cur = first;
			for (int i = 0; i < arcs; i++) {
				LivingEntity next = null;
				double best = ArsenalStats.STORMCOIL_ARC_RADIUS * ArsenalStats.STORMCOIL_ARC_RADIUS;
				for (Entity e : level.getEntities(cur, cur.getBoundingBox().inflate(ArsenalStats.STORMCOIL_ARC_RADIUS))) {
					if (!(e instanceof LivingEntity le) || struck.contains(le) || Spells.friendly(p, e)
						|| e instanceof net.minecraft.world.entity.decoration.ArmorStand) continue;
					double d = e.distanceToSqr(cur);
					if (d < best) {
						best = d;
						next = le;
					}
				}
				if (next == null) break;
				SpellLines.particles(level, cur.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), ParticleTypes.ELECTRIC_SPARK, 0.3);
				Spells.hurt(p, p, next, Spells.damage(p, next, ArsenalStats.STORMCOIL_ARC_DAMAGE, false, false));
				struck.add(next);
				cur = next;
			}
			lastArcs = struck.size() - 1;
			if (p.isInWater()) {
				for (LivingEntity s : struck) {
					if (s.distanceTo(p) <= ArsenalStats.STORMCOIL_SELF_ARC_RADIUS) {
						p.hurtServer(level, level.damageSources().lightningBolt(), ArsenalStats.STORMCOIL_SELF_ARC_DAMAGE);
						break;
					}
				}
			}
		}

		public static int lastArcs;
	}

	/** Sculk Resonator: charge 1.25 s, release a 20-block sonic boom: 14 damage ignoring half armor, knockback 1.5. */
	public static class SculkResonator extends CasterItem {
		public SculkResonator(Properties p) {
			super(p, 0, false);
		}

		@Override
		public int rawCost(Player p) {
			return ArsenalStats.RESONATOR_COST;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer sp && Glimmer.current(sp) < Glimmer.cost(sp, rawCost(sp)) && !sp.isCreative()) {
				Glimmer.trySpend(sp, rawCost(sp)); // fails: flash + clink
				return InteractionResult.FAIL;
			}
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.BOW;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return 72000;
		}

		@Override
		public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
			if (!(entity instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return false;
			if (getUseDuration(stack, entity) - remaining < ArsenalStats.RESONATOR_CHARGE_TICKS) return false;
			InteractionHand hand = sp.getUsedItemHand();
			return castNow(sl, sp, stack, hand);
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			SpellLines.Line line = SpellLines.trace(level, p, ArsenalStats.RESONATOR_RANGE, 0, false);
			Vec3 dir = p.getLookAngle().normalize();
			for (LivingEntity t : line.targets()) {
				if (Spells.friendly(p, t) && !(t instanceof Player)) continue;
				AttributeInstance armor = t.getAttribute(Attributes.ARMOR);
				var id = ArsenalContent.id("resonator_pierce");
				if (armor != null) armor.addOrUpdateTransientModifier(new AttributeModifier(id, -ArsenalStats.RESONATOR_ARMOR_IGNORED,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				try {
					Spells.hurt(p, p, t, Spells.damage(p, t, ArsenalStats.RESONATOR_DAMAGE, false, moonlit));
				} finally {
					if (armor != null) armor.removeModifier(id);
				}
				t.push(dir.x * ArsenalStats.RESONATOR_KNOCKBACK, 0.25, dir.z * ArsenalStats.RESONATOR_KNOCKBACK);
				t.needsSync = true;
			}
			SpellLines.particles(level, line.start().add(dir.scale(1.5)).add(0, -0.2, 0), line.end(), ParticleTypes.SONIC_BOOM, 1.5);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F, 1.2F);
			level.gameEvent(p, GameEvent.EXPLODE, p.position());
		}

		static AABB box(Vec3 v) {
			return new AABB(v, v);
		}
	}
}
