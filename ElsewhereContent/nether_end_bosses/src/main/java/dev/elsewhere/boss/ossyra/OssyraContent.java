package dev.elsewhere.boss.ossyra;

import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.DevCommands;
import dev.elsewhere.nether_end_bosses.GearStats;
import dev.elsewhere.nether_end_bosses.Reg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Registers Ossyra, the Matriarch Pedestal, her drops, and the "shulker shell on the pedestal (dragon killed)" summon. */
public final class OssyraContent {
	public static final Identifier PEARL_GRAVITY = Reg.id("gravity_pearl");
	public static final String PEARL_UNTIL = "elsewhere_gravity_pearl_until_";
	public static EntityType<Ossyra> OSSYRA;
	public static Block MATRIARCH_PEDESTAL;
	public static Item INVERTED_CROWN;
	public static Item GRAVITY_PEARL;
	public static Item MATRIARCHS_SHELL;

	private OssyraContent() {}

	public static void init() {
		OSSYRA = Reg.entity("ossyra", EntityType.Builder.of(Ossyra::new, MobCategory.MONSTER).fireImmune().sized(6.0F, 6.0F).eyeHeight(3.5F).clientTrackingRange(10).notInPeaceful());
		FabricDefaultAttributeRegistry.register(OSSYRA, Ossyra.createAttributes());
		MATRIARCH_PEDESTAL = Reg.block("matriarch_pedestal", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(-1.0F, 3600000.0F).noLootTable().lightLevel(s -> 7).sound(SoundType.STONE));
		INVERTED_CROWN = Reg.item("inverted_crown", new Item.Properties().stacksTo(1).durability(407).rarity(Rarity.EPIC)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_DIAMOND)
				.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id("inverted_crown"))).build())
			.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(Reg.id("inverted_crown_armor"), GearStats.INVERTED_CROWN_ARMOR, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(Reg.id("inverted_crown_toughness"), GearStats.INVERTED_CROWN_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.add(Attributes.GRAVITY, new AttributeModifier(Reg.id("inverted_crown_gravity"), GearStats.INVERTED_CROWN_GRAVITY, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.HEAD)
				.add(Attributes.SAFE_FALL_DISTANCE, new AttributeModifier(Reg.id("inverted_crown_safe_fall"), GearStats.INVERTED_CROWN_SAFE_FALL, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.build()));
		GRAVITY_PEARL = Reg.item("gravity_pearl", p -> new Item(p) {
			@Override
			public InteractionResult use(Level level, Player player, InteractionHand hand) {
				if (player instanceof ServerPlayer sp && !toggleGravity(sp)) return InteractionResult.FAIL;
				player.getCooldowns().addCooldown(player.getItemInHand(hand), (int) (GearStats.GRAVITY_PEARL_USE_DELAY_S * 20));
				return InteractionResult.SUCCESS;
			}
		}, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).useCooldown(GearStats.GRAVITY_PEARL_USE_DELAY_S));
		MATRIARCHS_SHELL = Reg.item("matriarchs_shell", new Item.Properties().rarity(Rarity.RARE));

		ArenaBuilder.BY_BOSS.put("ossyra", OssyraArena.INSTANCE);
		DevCommands.SUMMONERS.put("ossyra", (level, origin) -> summon(level, origin, null));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.getBlockState(hit.getBlockPos()).is(MATRIARCH_PEDESTAL)) return InteractionResult.PASS;
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.SHULKER_SHELL)) return InteractionResult.PASS;
			if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
			if (!dragonKilled(server)) {
				player.sendSystemMessage(Component.translatable("boss.elsewhere.needs_dragon").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			BlockPos origin = hit.getBlockPos().subtract(OssyraArena.PEDESTAL);
			if (!server.getEntitiesOfClass(Ossyra.class, new AABB(origin).inflate(24)).isEmpty()) return InteractionResult.FAIL;
			if (!player.isCreative()) stack.shrink(1);
			summon(server, origin, player instanceof ServerPlayer sp ? sp : null);
			return InteractionResult.SUCCESS;
		});
		// Inverted Crown: Levitation immunity. Gravity Pearl: ends its low gravity after 20 s.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) return;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
				if (g != null && g.getModifier(PEARL_GRAVITY) != null && p.tickCount > pearlUntil(p)) g.removeModifier(PEARL_GRAVITY);
			}
		});
	}

	private static final java.util.Map<java.util.UUID, Integer> PEARL = new java.util.HashMap<>();

	private static int pearlUntil(ServerPlayer p) {
		return PEARL.getOrDefault(p.getUUID(), 0);
	}

	/** Gravity Pearl: toggles your gravity to 0.02 for 20 s. */
	static boolean toggleGravity(ServerPlayer p) {
		AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
		if (g == null) return false;
		if (g.getModifier(PEARL_GRAVITY) != null) {
			g.removeModifier(PEARL_GRAVITY);
			return true;
		}
		for (dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss b : p.level().getEntitiesOfClass(dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss.class, p.getBoundingBox().inflate(40))) {
			if (b.fight() != null && b.fight().arena.contains(p)) return false; // not usable inside a boss arena
		}
		if (!dev.elsewhere.stub.nether_end_bosses.GlimmerStub.trySpend(p, GearStats.GRAVITY_PEARL_GLIMMER)) return false;
		g.addTransientModifier(new AttributeModifier(PEARL_GRAVITY, GearStats.GRAVITY_PEARL_GRAVITY_FACTOR - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		PEARL.put(p.getUUID(), p.tickCount + GearStats.GRAVITY_PEARL_TICKS);
		p.level().playSound(null, p.blockPosition(), SoundEvents.ENDER_PEARL_THROW, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	public static boolean dragonKilled(ServerLevel level) {
		ServerLevel end = level.getServer().getLevel(Level.END);
		EnderDragonFight fight = end == null ? null : end.getDragonFight();
		return fight != null && fight.hasPreviouslyKilledDragon();
	}

	public static @Nullable Ossyra summon(ServerLevel level, BlockPos origin, @Nullable ServerPlayer by) {
		Ossyra boss = OSSYRA.create(level, EntitySpawnReason.TRIGGERED);
		if (boss == null) return null;
		boss.snapTo(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
		boss.setHome(origin);
		level.addFreshEntity(boss);
		boss.startFight(level, origin, by, ItemStack.EMPTY);
		return boss;
	}
}
