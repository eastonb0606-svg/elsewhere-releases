package dev.elsewhere.rivals.azmar;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Falconer's falcon: a hooded gold-and-lapis falcon (own model and texture on the client), a parrot underneath for its flight.
 * It never tames, never sits on a shoulder, never imitates mobs and never drops anything; its marks and dives are the Falconer's.
 * {@code elsewhere:azmar_falcon}, 20 HP (two hits with a good weapon).
 */
public final class AzmarFalcon extends Parrot {
	public static EntityType<AzmarFalcon> TYPE;
	/** Every living falcon (the field profile's falcon intel reads this; no world scan). */
	public static final java.util.Set<AzmarFalcon> ALL = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static boolean done;
	/** A Falcon Storm falcon: 10 HP, one hit kills it, and it expires on its own after 10 s. */
	public boolean storm;

	/** Spawns a falcon at {@code at} with {@code hp} health (20 for the Falconer's own, 10 for a storm falcon). Null if the type could not be created. */
	public static AzmarFalcon spawn(ServerLevel level, net.minecraft.world.phys.Vec3 at, float hp, boolean storm) {
		AzmarFalcon f = TYPE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
		if (f == null) return null;
		f.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		f.setHealth(hp);
		f.storm = storm;
		f.setCustomName(net.minecraft.network.chat.Component.literal(storm ? "Storm Falcon" : "Falcon of the Falconer"));
		f.setPersistenceRequired();
		f.snapTo(at.x, at.y, at.z, 0F, 0F);
		level.addFreshEntity(f);
		return f;
	}

	public AzmarFalcon(EntityType<? extends Parrot> type, Level level) {
		super(type, level);
	}

	public static void init() {
		if (done) return;
		done = true;
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("elsewhere", "azmar_falcon"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<AzmarFalcon>of(AzmarFalcon::new, MobCategory.MISC).sized(0.5F, 0.7F).eyeHeight(0.5F)
			.clientTrackingRange(10).noLootTable().build(key));
		FabricDefaultAttributeRegistry.register(TYPE, Parrot.createAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.FLYING_SPEED, 0.6));
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new WaterAvoidingRandomFlyingGoal(this, 1.0));
		this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 10.0F));
		this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;   // no taming, no feeding
	}

	@Override
	public boolean isFood(net.minecraft.world.item.ItemStack stack) {
		return false;
	}

	@Override
	public boolean isPartyParrot() {
		return false;
	}

	@Override
	public void setRecordPlayingNearby(net.minecraft.core.BlockPos pos, boolean playing) {}

	@Override
	public SoundEvent getAmbientSound() {
		return SoundEvents.PARROT_IMITATE_PHANTOM;   // a thin raptor cry
	}

	@Override
	public float getVoicePitch() {
		return 1.7F;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide() && isAlive()) {
			ALL.add(this);
			if (storm && tickCount > 200) discard();
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		ALL.remove(this);
		super.remove(reason);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return super.hurtServer(level, source, damage);
	}
}
