package dev.elsewhere.boss.solaxis;

import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.GearStats;
import dev.elsewhere.nether_end_bosses.Reg;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Registers Solaxis, its projectiles, the Pyre Cradle core, its drops, and the "break all 4 spawners" summon. */
public final class SolaxisContent {
	public static EntityType<Solaxis> SOLAXIS;
	public static EntityType<PyreBolt> PYRE_BOLT;
	public static EntityType<SunOrb> SUN_ORB;
	public static Block PYRE_CRADLE_CORE;
	public static Block SOLAXIS_EMBER;
	public static Item SUN_ORB_ITEM;
	public static Item NINEFOLD_ROD;
	public static Item PYRE_CROWN;

	public static final ResourceKey<EquipmentAsset> PYRE_CROWN_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id("pyre_crown"));

	private SolaxisContent() {}

	public static void init() {
		SOLAXIS = Reg.entity("solaxis", EntityType.Builder.of(Solaxis::new, MobCategory.MONSTER).fireImmune().sized(3.5F, 6.0F).eyeHeight(4.6F).clientTrackingRange(10).notInPeaceful());
		PYRE_BOLT = Reg.entity("pyre_bolt", EntityType.Builder.<PyreBolt>of(PyreBolt::new, MobCategory.MISC).noLootTable().sized(0.4F, 0.4F).clientTrackingRange(6).updateInterval(5));
		SUN_ORB = Reg.entity("sun_orb", EntityType.Builder.<SunOrb>of(SunOrb::new, MobCategory.MISC).noLootTable().sized(1.2F, 1.2F).clientTrackingRange(6).updateInterval(5));
		FabricDefaultAttributeRegistry.register(SOLAXIS, Solaxis.createAttributes());

		PYRE_CRADLE_CORE = Reg.block("pyre_cradle_core", PyreCradleCoreBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
			.strength(-1.0F, 3600000.0F).noLootTable().lightLevel(s -> 12).sound(SoundType.NETHER_BRICKS));
		SOLAXIS_EMBER = Reg.block("solaxis_ember", SolaxisEmberBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW)
			.strength(0.3F).lightLevel(s -> GearStats.SOLAXIS_EMBER_LIGHT).noOcclusion().sound(SoundType.AMETHYST_CLUSTER));
		SUN_ORB_ITEM = Reg.item("sun_orb", new Item.Properties());
		NINEFOLD_ROD = Reg.item("ninefold_rod", NinefoldRodItem::new, new Item.Properties().stacksTo(1).durability(GearStats.NINEFOLD_ROD_USES).rarity(Rarity.RARE)
			.useCooldown(GearStats.NINEFOLD_ROD_USE_DELAY_S).fireResistant());
		PYRE_CROWN = Reg.item("pyre_crown", PyreCrownItem::new, new Item.Properties().stacksTo(1).durability(407).rarity(Rarity.RARE).fireResistant()
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setAsset(PYRE_CROWN_ASSET)
				.setEquipSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GOLD).build())
			.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(Reg.id("pyre_crown_armor"), GearStats.PYRE_CROWN_ARMOR, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(Reg.id("pyre_crown_toughness"), GearStats.PYRE_CROWN_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
				.build()));

		// Pyre Crown: burning (fire ticks) doesn't hurt you; fire blocks and lava still do
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(source.is(net.minecraft.world.damagesource.DamageTypes.ON_FIRE) && entity.getItemBySlot(EquipmentSlot.HEAD).is(PYRE_CROWN)));
		ArenaBuilder.BY_BOSS.put("solaxis", SolaxisArena.INSTANCE);
		dev.elsewhere.nether_end_bosses.DevCommands.SUMMONERS.put("solaxis", (level, origin) -> {
			for (int[] s : SolaxisArena.SPAWNERS) level.removeBlock(origin.offset(s[0], s[1], s[2]), false);
			int[] s = SolaxisArena.SPAWNERS[0];
			return onSpawnerBroken(level, origin.offset(s[0], s[1], s[2]), null);
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level instanceof ServerLevel server && state.is(Blocks.SPAWNER)) onSpawnerBroken(server, pos, player instanceof ServerPlayer sp ? sp : null);
		});
	}

	/** Breaking the last of the 4 spawners around a pyre core wakes Solaxis. */
	public static @Nullable Solaxis onSpawnerBroken(ServerLevel level, BlockPos spawner, @Nullable ServerPlayer by) {
		BlockPos core = findCore(level, spawner);
		if (core == null) return null;
		for (int[] s : SolaxisArena.SPAWNERS) {
			if (level.getBlockState(core.offset(s[0], s[1], s[2])).is(Blocks.SPAWNER)) return null;
		}
		if (!level.getEntitiesOfClass(Solaxis.class, new AABB(core).inflate(SolaxisArena.RADIUS + 4)).isEmpty()) return null;
		return summon(level, core, by);
	}

	private static @Nullable BlockPos findCore(ServerLevel level, BlockPos near) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = -4; dy <= 1; dy++) for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
			BlockState state = level.getBlockState(p.set(near.getX() + dx, near.getY() + dy, near.getZ() + dz));
			if (state.is(PYRE_CRADLE_CORE)) return p.immutable();
		}
		return null;
	}

	public static Solaxis summon(ServerLevel level, BlockPos core, @Nullable ServerPlayer by) {
		Solaxis boss = SOLAXIS.create(level, EntitySpawnReason.TRIGGERED);
		boss.snapTo(core.getX() + 0.5, core.getY() + 4.5, core.getZ() + 0.5, 0.0F, 0.0F);
		boss.setHome(core);
		level.addFreshEntity(boss);
		boss.startFight(level, core, by, ItemStack.EMPTY);
		return boss;
	}
}
