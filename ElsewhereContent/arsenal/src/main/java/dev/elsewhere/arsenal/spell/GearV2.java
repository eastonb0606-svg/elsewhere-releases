package dev.elsewhere.arsenal.spell;

import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Phase 3 caster gear: Mirrorglass Prism (channelled beam) and Hushward Bell (absorb ward that silences casters). */
public final class GearV2 {
	private GearV2() {}

	public static final int PRISM_COST = 3, PRISM_DURABILITY = 600, PRISM_RANGE = 20, PRISM_RAMP_TICKS = dev.elsewhere.arsenal.Overhaul3.REBALANCE_ARSENAL ? 50 : 60;
	public static final float PRISM_DPS_LOW = dev.elsewhere.arsenal.Overhaul3.REBALANCE_ARSENAL ? 5F : 4F, PRISM_DPS_HIGH = 12F;
	public static final int BELL_COST = 8, BELL_DURABILITY = 400, BELL_CHARGE_TICKS = 10, BELL_WARD_TICKS = 120, BELL_ABSORB_AMPLIFIER = 4, BELL_RADIUS = 10;

	private record Beam(UUID target, int ticksOn) {}

	private static final Map<UUID, Beam> BEAMS = new HashMap<>();
	/** Player UUID -> game time the ward ends. */
	private static final Map<UUID, Long> WARDS = new HashMap<>();

	// ------------------------------------------------------------------ Mirrorglass Prism

	/** Hold to channel: 4 damage/s, 12 damage/s after 3 s on the same target; 3 Glimmer/s; the beam breaks without line of sight. */
	public static class MirrorglassPrism extends CasterItem {
		public MirrorglassPrism(Properties p) {
			super(p, 0, false);
		}

		@Override
		public int rawCost(Player p) {
			return PRISM_COST;
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer sp && Glimmer.current(sp) < Glimmer.cost(sp, PRISM_COST) && !sp.isCreative()) {
				Glimmer.trySpend(sp, PRISM_COST); // fails: flash + clink
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
		public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
			if (!(user instanceof ServerPlayer sp) || !(level instanceof ServerLevel)) return;
			if (!channelTick(sp, stack, getUseDuration(stack, user) - remaining, sp.getUsedItemHand())) sp.stopUsingItem();
		}

		@Override
		public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
			BEAMS.remove(entity.getUUID());
			return false;
		}

		/** One channel tick (self-test calls it directly). False when the beam must stop (out of Glimmer). */
		public boolean channelTick(ServerPlayer sp, ItemStack stack, int tick, InteractionHand hand) {
			ServerLevel level = (ServerLevel) sp.level();
			if (tick % 20 == 0) {
				if (!Glimmer.trySpend(sp, PRISM_COST, false)) {
					BEAMS.remove(sp.getUUID());
					return false;
				}
				if (tick > 0) stack.hurtAndBreak(1, sp, hand);
			}
			SpellLines.Line line = SpellLines.trace(level, sp, PRISM_RANGE, 1, false);
			if (tick % 2 == 0) SpellLines.particles(level, SpellLines.handStart(sp), line.end(), ParticleTypes.END_ROD, 1.2); // P13: end rods linger 3 s; was every tick at 0.8
			LivingEntity t = null;
			for (LivingEntity c : line.targets()) if (!(Spells.friendly(sp, c) && !(c instanceof Player))) t = c;
			if (t == null) {
				BEAMS.remove(sp.getUUID()); // line of sight lost: the ramp starts over
				return true;
			}
			Beam b = BEAMS.get(sp.getUUID());
			int on = b != null && b.target().equals(t.getUUID()) ? b.ticksOn() + 1 : 0;
			BEAMS.put(sp.getUUID(), new Beam(t.getUUID(), on));
			if (tick % 10 == 0) {
				float dps = on >= PRISM_RAMP_TICKS ? PRISM_DPS_HIGH : PRISM_DPS_LOW;
				Spells.hurt(sp, sp, t, Spells.damage(sp, t, dps / 2F, false, false));
			}
			return true;
		}

		public static int ticksOnTarget(ServerPlayer sp) {
			Beam b = BEAMS.get(sp.getUUID());
			return b == null ? 0 : b.ticksOn();
		}
	}

	// ------------------------------------------------------------------ Hushward Bell

	/** Hold 0.5 s, 8 Glimmer: 6 s ward absorbing 20 damage that silences mob spell-casting and sound attacks nearby. */
	public static class HushwardBell extends CasterItem {
		public HushwardBell(Properties p) {
			super(p, 0, false);
		}

		@Override
		public int rawCost(Player p) {
			return BELL_COST;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer sp) {
				if (wardActive(sp)) return InteractionResult.FAIL;
				if (Glimmer.current(sp) < Glimmer.cost(sp, BELL_COST) && !sp.isCreative()) {
					Glimmer.trySpend(sp, BELL_COST);
					return InteractionResult.FAIL;
				}
			}
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.BLOCK;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return BELL_CHARGE_TICKS;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (entity instanceof ServerPlayer sp && level instanceof ServerLevel sl) castNow(sl, sp, stack, sp.getUsedItemHand());
			return stack;
		}

		@Override
		public boolean castNow(ServerLevel sl, ServerPlayer sp, ItemStack stack, InteractionHand hand) {
			if (wardActive(sp)) return false; // reusing it while the ward is up is refused
			return super.castNow(sl, sp, stack, hand);
		}

		@Override
		protected void cast(ServerLevel level, ServerPlayer p, ItemStack stack, boolean moonlit) {
			WARDS.put(p.getUUID(), level.getGameTime() + BELL_WARD_TICKS);
			p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, BELL_WARD_TICKS, BELL_ABSORB_AMPLIFIER, false, true, true));
			level.playSound(null, p.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1, p.getZ(), 20, 0.8, 0.8, 0.8, 0.02);
		}
	}

	public static boolean wardActive(ServerPlayer p) {
		Long end = WARDS.get(p.getUUID());
		if (end == null) return false;
		if (p.level().getGameTime() > end || !p.isAlive()) {
			WARDS.remove(p.getUUID());
			return false;
		}
		return true;
	}

	public static void clearWard(ServerPlayer p) {
		WARDS.remove(p.getUUID());
	}

	/**
	 * True if {@code pos} is inside a live Hushward ward. Boss and mob modules can ask this before a spell cast or a
	 * sound-based attack and skip it (integration hook; the built-in effect below covers Evokers/Illusioners and Wardens).
	 */
	public static boolean isSilenced(Level level, Vec3 pos) {
		if (!(level instanceof ServerLevel sl)) return false;
		for (ServerPlayer p : sl.players()) if (covers(p, pos)) return true;
		return false;
	}

	/** True if this player's live ward covers {@code pos}. */
	public static boolean covers(ServerPlayer p, Vec3 pos) {
		return wardActive(p) && p.position().distanceToSqr(pos) <= (double) BELL_RADIUS * BELL_RADIUS;
	}

	static void tickWards(net.minecraft.server.MinecraftServer server) {
		if (WARDS.isEmpty()) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!wardActive(p)) continue;
			ServerLevel l = (ServerLevel) p.level();
			for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(p.blockPosition()).inflate(BELL_RADIUS))) {
				if (m instanceof SpellcasterIllager || m instanceof Warden) {
					if (m.getTarget() == p) m.setTarget(null);
					if (m instanceof Warden w) w.clearAnger(p);
				}
			}
		}
	}

	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(GearV2::tickWards);
	}
}
