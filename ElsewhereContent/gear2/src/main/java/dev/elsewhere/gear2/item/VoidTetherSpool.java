package dev.elsewhere.gear2.item;

import dev.elsewhere.gear2.Gear2Items;
import dev.elsewhere.gear2.Gear2Stats;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * gear-trinket3-b Void Tether Spool: right-click (no anchor yet) or sneak + right-click sets the anchor where you stand;
 * right-click within 64 blocks snaps you back to it (3 min cooldown, 4 hunger points). Never across dimensions.
 */
public class VoidTetherSpool extends Item {
	public VoidTetherSpool(Properties p) {
		super(p);
	}

	public enum Result { ANCHORED, SNAPPED, OTHER_DIMENSION, TOO_FAR, TOO_HUNGRY }

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		Result r = act(sp, player.getItemInHand(hand), player.isShiftKeyDown());
		return r == Result.ANCHORED || r == Result.SNAPPED ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
	}

	public static Result act(ServerPlayer p, ItemStack stack, boolean sneaking) {
		ServerLevel level = p.level();
		GlobalPos anchor = stack.get(Gear2Items.TETHER_ANCHOR);
		if (sneaking || anchor == null) {
			stack.set(Gear2Items.TETHER_ANCHOR, GlobalPos.of(level.dimension(), p.blockPosition()));
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 0.2, p.getZ(), 20, 0.3, 0.1, 0.3, 0.02);
			level.playSound(null, p.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.8F, 1.2F);
			p.sendOverlayMessage(Component.translatable("message.elsewhere.void_tether_spool.anchored"));
			return Result.ANCHORED;
		}
		if (!anchor.dimension().equals(level.dimension())) {
			p.sendOverlayMessage(Component.translatable("message.elsewhere.void_tether_spool.dimension"));
			return Result.OTHER_DIMENSION;
		}
		Vec3 to = Vec3.atBottomCenterOf(anchor.pos());
		if (to.distanceTo(p.position()) > Gear2Stats.TETHER_RANGE) {
			p.sendOverlayMessage(Component.translatable("message.elsewhere.void_tether_spool.far", (int) Gear2Stats.TETHER_RANGE));
			return Result.TOO_FAR;
		}
		boolean free = p.getAbilities().instabuild;
		if (!free && p.getFoodData().getFoodLevel() < Gear2Stats.TETHER_HUNGER) {
			p.sendOverlayMessage(Component.translatable("message.elsewhere.void_tether_spool.hungry"));
			return Result.TOO_HUNGRY;
		}
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.05);
		p.teleportTo(to.x, to.y, to.z);
		p.resetFallDistance();
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 1, to.z, 30, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, anchor.pos(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.8F);
		if (!free) p.getFoodData().setFoodLevel(p.getFoodData().getFoodLevel() - Gear2Stats.TETHER_HUNGER);
		p.getCooldowns().addCooldown(stack, Gear2Stats.TETHER_COOLDOWN);
		return Result.SNAPPED;
	}
}
