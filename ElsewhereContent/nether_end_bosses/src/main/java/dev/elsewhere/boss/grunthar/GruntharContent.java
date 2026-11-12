package dev.elsewhere.boss.grunthar;

import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.DevCommands;
import dev.elsewhere.nether_end_bosses.GearStats;
import dev.elsewhere.nether_end_bosses.Reg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Registers Grunthar & Ashtusk, the gold bomb, the Gilded Throne, their drops and the "gold block on the throne" summon. */
public final class GruntharContent {
	public static final String ENFORCER_TAG = "elsewhere_gilded_enforcer";
	public static final String HARNESSED_TAG = "elsewhere_ashtusk_harness";
	public static EntityType<Grunthar> GRUNTHAR;
	public static EntityType<Ashtusk> ASHTUSK;
	public static EntityType<GoldBomb> GOLD_BOMB;
	public static Block GILDED_THRONE;
	public static Item GOLD_BOMB_ITEM;
	public static Item TYRANTS_TUSK;
	public static Item ASHTUSK_HARNESS;
	public static Item ASHTUSK_GLAND;
	public static Item CRIMSON_FUNGUS_ON_A_STICK;

	private GruntharContent() {}

	public static void init() {
		GRUNTHAR = Reg.entity("grunthar", EntityType.Builder.of(Grunthar::new, MobCategory.MONSTER).fireImmune().sized(1.6F, 3.0F).eyeHeight(2.6F).clientTrackingRange(10).notInPeaceful());
		ASHTUSK = Reg.entity("ashtusk", EntityType.Builder.of(Ashtusk::new, MobCategory.MONSTER).fireImmune().sized(3.0F, 3.0F).eyeHeight(2.0F).passengerAttachments(2.4F).clientTrackingRange(10).notInPeaceful());
		GOLD_BOMB = Reg.entity("gold_bomb", EntityType.Builder.<GoldBomb>of(GoldBomb::new, MobCategory.MISC).noLootTable().sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(5));
		FabricDefaultAttributeRegistry.register(GRUNTHAR, Grunthar.createAttributes());
		FabricDefaultAttributeRegistry.register(ASHTUSK, Ashtusk.createAttributes());

		GILDED_THRONE = Reg.block("gilded_throne", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL));
		GOLD_BOMB_ITEM = Reg.item("gold_bomb", new Item.Properties());
		TYRANTS_TUSK = Reg.item("tyrants_tusk", Item::new, new Item.Properties().axe(ToolMaterial.DIAMOND, GearStats.TUSK_DAMAGE - 4.0F, GearStats.TUSK_SPEED - 4.0F)
			.durability(GearStats.TUSK_DURABILITY).rarity(Rarity.RARE).fireResistant());
		ASHTUSK_HARNESS = Reg.item("ashtusk_harness", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
		ASHTUSK_GLAND = Reg.item("ashtusk_gland", p -> new Item(p) {
			@Override
			public ItemStack finishUsingItem(ItemStack itemStack, net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity entity) {
				if (entity instanceof ServerPlayer sp) onGlandEaten(sp);
				return super.finishUsingItem(itemStack, level, entity);
			}
		}, new Item.Properties().rarity(Rarity.UNCOMMON)
			.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).alwaysEdible().build()));
		CRIMSON_FUNGUS_ON_A_STICK = Reg.item("crimson_fungus_on_a_stick", new Item.Properties().stacksTo(1).durability(100));

		ArenaBuilder.BY_BOSS.put("grunthar", GruntharArena.INSTANCE);
		DevCommands.SUMMONERS.put("grunthar", (level, origin) -> summon(level, origin, null));

		// the real summon: place (use) a gold block on the Gilded Throne
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.getBlockState(hit.getBlockPos()).is(GILDED_THRONE)) return InteractionResult.PASS;
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.GOLD_BLOCK)) return InteractionResult.PASS;
			if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
			BlockPos origin = hit.getBlockPos().subtract(GruntharArena.THRONE);
			if (!server.getEntitiesOfClass(Grunthar.class, new AABB(origin).inflate(30)).isEmpty()) return InteractionResult.FAIL;
			if (!player.isCreative()) stack.shrink(1);
			summon(server, origin, player instanceof ServerPlayer sp ? sp : null);
			return InteractionResult.SUCCESS;
		});

		// Gilded Enforcers block frontal damage with their shields (explosions and fire get through)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!entity.entityTags().contains(ENFORCER_TAG) || source.getEntity() == null) return true;
			if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION) || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) return true;
			Vec3 look = Vec3.directionFromRotation(0, entity.getYRot());
			Vec3 to = source.getEntity().position().subtract(entity.position());
			if (look.dot(new Vec3(to.x, 0, to.z).normalize()) > 0.4) {
				entity.level().playSound(null, entity.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
				return false;
			}
			return true;
		});

		// Ashtusk Harness: use it on a hoglin to harness it; empty hand mounts; a crimson fungus on a stick steers it
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Hoglin hoglin) || level.isClientSide()) return InteractionResult.PASS;
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(ASHTUSK_HARNESS) && !hoglin.entityTags().contains(HARNESSED_TAG)) {
				hoglin.addTag(HARNESSED_TAG);
				hoglin.setPersistenceRequired();
				hoglin.setImmuneToZombification(true);
				if (!player.isCreative()) stack.shrink(1);
				level.playSound(null, hoglin.blockPosition(), SoundEvents.HORSE_SADDLE.value(), SoundSource.NEUTRAL, 1.0F, 0.8F);
				return InteractionResult.SUCCESS;
			}
			if (hoglin.entityTags().contains(HARNESSED_TAG) && !hoglin.isVehicle() && (stack.isEmpty() || stack.is(CRIMSON_FUNGUS_ON_A_STICK))) {
				player.startRiding(hoglin);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 2 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.getVehicle() instanceof Hoglin hoglin && hoglin.entityTags().contains(HARNESSED_TAG)) {
					hoglin.setTarget(null);
					if (p.getMainHandItem().is(CRIMSON_FUNGUS_ON_A_STICK) || p.getOffhandItem().is(CRIMSON_FUNGUS_ON_A_STICK)) {
						Vec3 goal = p.position().add(p.getLookAngle().multiply(4, 0, 4));
						hoglin.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, GearStats.HARNESS_HOGLIN_SPEED * 1.3);
					}
				}
			}
		});
	}

	/** Called from the gland's consume hook. */
	public static void onGlandEaten(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 600, 0));
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("testmeter")) {
			try {
				Class<?> tm = Class.forName("dev.testmeter.TestMeter");
				Object data = tm.getMethod("data", ServerPlayer.class).invoke(null, player);
				java.lang.reflect.Field acute = data.getClass().getField("acute");
				acute.setDouble(data, acute.getDouble(data) + GearStats.GLAND_TESTOSTERONE);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}
	}

	public static void makeEnforcer(PiglinBrute brute) {
		brute.addTag(ENFORCER_TAG);
		brute.setCustomName(net.minecraft.network.chat.Component.translatable("entity.elsewhere.gilded_enforcer"));
		brute.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		brute.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		brute.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST}) brute.setDropChance(slot, 0);
		brute.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60);
		brute.setHealth(60);
		brute.setImmuneToZombification(true);
		brute.setPersistenceRequired();
	}

	public static @Nullable Grunthar summon(ServerLevel level, BlockPos origin, @Nullable ServerPlayer by) {
		Ashtusk mount = ASHTUSK.create(level, EntitySpawnReason.TRIGGERED);
		Grunthar rider = GRUNTHAR.create(level, EntitySpawnReason.TRIGGERED);
		if (mount == null || rider == null) return null;
		mount.snapTo(origin.getX() + 0.5, origin.getY(), origin.getZ() - 2.5, 0.0F, 0.0F);
		rider.snapTo(origin.getX() + 0.5, origin.getY() + 2.5, origin.getZ() - 2.5, 0.0F, 0.0F);
		rider.setHome(origin);
		mount.setHome(origin);
		level.addFreshEntity(mount);
		level.addFreshEntity(rider);
		rider.startRiding(mount, true, false);
		rider.setMount(mount);
		mount.setRider(rider);
		rider.startFight(level, origin, by, ItemStack.EMPTY);
		level.playSound(null, origin, SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 4.0F, 0.5F);
		return rider;
	}
}
