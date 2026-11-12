package dev.elsewhere.palehollow.entity;

import dev.elsewhere.registry.ModEntities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * new-companions: the Hollow Stag (Pale Hollow), the Docile Glass Scarab (Glasswaste) and the Haze Ferret (Greenhaze).
 * Tamed with a favourite food (1 in 3 per feed). They follow, sit on use, and every 10 s sense their dimension's ore within
 * 12 blocks of their owner (particles over the ore + an action-bar line). They never fight and are not minions.
 */
public class CompanionEntity extends TamableAnimal {
	public enum Kind {
		STAG("elsewhere:amber_ore", Items.APPLE),
		SCARAB("elsewhere:sunstone_ore", Items.SUGAR),
		FERRET("elsewhere:trichome_quartz_ore", Items.SWEET_BERRIES);

		public final String ore;
		public final Item food;

		Kind(final String ore, final Item food) {
			this.ore = ore;
			this.food = food;
		}
	}

	public static final int SENSE_INTERVAL = 200;
	public static final int SENSE_RADIUS = 12;
	/** Last ore the companion sensed (self-test hook). */
	public @Nullable BlockPos lastSensed;

	public CompanionEntity(final EntityType<? extends TamableAnimal> type, final Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 18.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.TEMPT_RANGE, 10.0);
	}

	public Kind kind() {
		EntityType<?> t = this.getType();
		return t == ModEntities.HAZE_FERRET ? Kind.FERRET : t == ModEntities.DOCILE_SCARAB ? Kind.SCARAB : Kind.STAG;
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
		this.goalSelector.addGoal(3, new PanicGoal(this, 1.4));
		this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.15, 8.0F, 2.5F));
		this.goalSelector.addGoal(5, new TemptGoal(this, 1.1, this::isFood, false));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	@Override
	public boolean isFood(final ItemStack stack) {
		return stack.is(this.kind().food);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(final ServerLevel level, final AgeableMob partner) {
		return null;
	}

	@Override
	public boolean canFallInLove() {
		return false;
	}

	@Override
	public InteractionResult mobInteract(final Player player, final InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!this.isTame() && this.isFood(stack)) {
			if (this.level() instanceof ServerLevel sl) {
				stack.consume(1, player);
				if (this.random.nextInt(3) == 0) {
					this.tame(player);
					if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
						dev.elsewhere.core.trophy.Trophies.grant(sp, "companion");
						dev.elsewhere.core.atlas.Atlas.discover(sp, "companion:" + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(this.getType()).getPath());
					}
					this.setOrderedToSit(false);
					sl.broadcastEntityEvent(this, (byte)7);
				} else {
					sl.broadcastEntityEvent(this, (byte)6);
				}
			}
			return InteractionResult.SUCCESS;
		}
		if (this.isTame() && this.isOwnedBy(player) && hand == InteractionHand.MAIN_HAND && !this.isFood(stack)) {
			if (!this.level().isClientSide()) {
				this.setOrderedToSit(!this.isOrderedToSit());
				this.setInSittingPose(this.isOrderedToSit());
				this.getNavigation().stop();
			}
			return InteractionResult.SUCCESS;
		}
		if (this.isTame() && this.isFood(stack) && this.getHealth() < this.getMaxHealth()) {
			stack.consume(1, player);
			this.heal(4.0F);
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel sl && this.isTame() && this.tickCount % SENSE_INTERVAL == 0) {
			LivingEntity owner = this.getOwner();
			if (owner instanceof ServerPlayer p && p.level() == sl && p.distanceToSqr(this) < 16 * 16) {
				senseOre(sl, p);
			}
		}
	}

	/** Looks for the nearest ore of its kind within {@link #SENSE_RADIUS} blocks; marks it with particles and tells the owner. */
	public @Nullable BlockPos senseOre(final ServerLevel level, final @Nullable ServerPlayer owner) {
		Identifier id = Identifier.tryParse(this.kind().ore);
		if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
			return null;
		}
		Block ore = BuiltInRegistries.BLOCK.getValue(id);
		BlockPos origin = this.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos q : BlockPos.betweenClosed(origin.offset(-SENSE_RADIUS, -SENSE_RADIUS, -SENSE_RADIUS), origin.offset(SENSE_RADIUS, SENSE_RADIUS, SENSE_RADIUS))) {
			if (level.getBlockState(q).is(ore)) {
				double d = q.distSqr(origin);
				if (d < bestD) {
					bestD = d;
					best = q.immutable();
				}
			}
		}
		this.lastSensed = best;
		if (best == null) {
			return null;
		}
		level.sendParticles(ParticleTypes.WAX_ON, best.getX() + 0.5, best.getY() + 0.5, best.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.0);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.4F);
		if (owner != null) {
			int dist = (int)Math.round(Math.sqrt(bestD));
			String dir = best.getY() < origin.getY() - 2 ? "below" : best.getY() > origin.getY() + 2 ? "above" : "level";
			owner.sendOverlayMessage(Component.translatable("message.elsewhere.companion.sense", this.getDisplayName(),
				Component.translatable(ore.getDescriptionId()), dist, Component.translatable("message.elsewhere.companion." + dir)).withStyle(ChatFormatting.GOLD));
		}
		return best;
	}

	public static List<EntityType<CompanionEntity>> types() {
		return List.of(ModEntities.HOLLOW_STAG, ModEntities.DOCILE_SCARAB, ModEntities.HAZE_FERRET);
	}

	static {
		// keep Blocks referenced for the dev-time ore fallback in self-tests
		Blocks.AIR.getClass();
	}
}
