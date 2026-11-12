package dev.elsewhere.boss.aphelion;

import dev.elsewhere.boss.ossyra.OssyraContent;
import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.DevCommands;
import dev.elsewhere.nether_end_bosses.GearStats;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Registers Aphelion, void breath, the Halo Altar, the drops, and the "dragon egg on the altar with 4 end crystals" summon. */
public final class AphelionContent {
	public static EntityType<Aphelion> APHELION;
	public static EntityType<VoidBreath> VOID_BREATH;
	public static Block HALO_ALTAR;
	public static Block APHELION_HEART;
	public static Item VOID_BREATH_ITEM;
	public static Item VOID_SCALE;
	public static Item VOID_SCALE_ELYTRA;
	public static Item WYRMFANG;

	private record Pool(ServerLevel level, ElsewhereBoss boss, Vec3 at, float damage, int[] ticks) {}

	private static final List<Pool> POOLS = new ArrayList<>();

	private AphelionContent() {}

	public static void init() {
		APHELION = Reg.entity("aphelion", EntityType.Builder.of(Aphelion::new, MobCategory.MONSTER).fireImmune().sized(4.0F, 3.0F).eyeHeight(1.8F).clientTrackingRange(16).updateInterval(1).notInPeaceful());
		VOID_BREATH = Reg.entity("void_breath", EntityType.Builder.<VoidBreath>of(VoidBreath::new, MobCategory.MISC).noLootTable().sized(0.8F, 0.8F).clientTrackingRange(6).updateInterval(5));
		FabricDefaultAttributeRegistry.register(APHELION, Aphelion.createAttributes());
		HALO_ALTAR = Reg.block("halo_altar", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).noLootTable().lightLevel(s -> 9).sound(SoundType.STONE));
		APHELION_HEART = Reg.block("aphelion_heart", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F).lightLevel(s -> 15).sound(SoundType.AMETHYST));
		VOID_BREATH_ITEM = Reg.item("void_breath", new Item.Properties());
		VOID_SCALE = Reg.item("void_scale", new Item.Properties().rarity(Rarity.EPIC));
		VOID_SCALE_ELYTRA = Reg.item("void_scale_elytra", new Item.Properties().durability(GearStats.VOID_ELYTRA_DURABILITY).rarity(Rarity.EPIC)
			.component(DataComponents.GLIDER, Unit.INSTANCE)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
				.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id("void_scale_elytra"))).setDamageOnHurt(false).build())
			.repairable(Items.PHANTOM_MEMBRANE)
			.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ARMOR, new AttributeModifier(Reg.id("void_scale_elytra_armor"), GearStats.VOID_ELYTRA_ARMOR, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
				.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(Reg.id("void_scale_elytra_toughness"), GearStats.VOID_ELYTRA_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
				.build()));
		WYRMFANG = Reg.item("wyrmfang", new Item.Properties().spear(ToolMaterial.NETHERITE, 1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F)
			.durability(GearStats.WYRMFANG_DURABILITY).rarity(Rarity.EPIC));

		ArenaBuilder.BY_BOSS.put("aphelion", AphelionArena.INSTANCE);
		DevCommands.SUMMONERS.put("aphelion", (level, origin) -> {
			placeCrystals(level, origin);
			return summon(level, origin, null, ItemStack.EMPTY);
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.getBlockState(hit.getBlockPos()).is(HALO_ALTAR)) return InteractionResult.PASS;
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.DRAGON_EGG)) return InteractionResult.PASS;
			if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
			BlockPos origin = hit.getBlockPos().subtract(AphelionArena.ALTAR);
			if (!OssyraContent.dragonKilled(server)) {
				player.sendSystemMessage(Component.translatable("boss.elsewhere.needs_dragon").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			if (server.getEntitiesOfClass(EndCrystal.class, new AABB(origin).inflate(12)).size() < 4) {
				player.sendSystemMessage(Component.translatable("boss.elsewhere.needs_crystals").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			if (!server.getEntitiesOfClass(Aphelion.class, new AABB(origin).inflate(40)).isEmpty()) return InteractionResult.FAIL;
			ItemStack egg = stack.copyWithCount(1);
			stack.shrink(1); // the egg is held by the fight and returned afterwards
			summon(server, origin, player instanceof ServerPlayer sp ? sp : null, egg);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickPools();
			if (server.getTickCount() % 10 == 0) for (ServerPlayer p : server.getPlayerList().getPlayers()) wyrmfangReach(p);
		});
	}

	private static final net.minecraft.resources.Identifier WYRMFANG_REACH = Reg.id("wyrmfang_reach");

	/** ARSENAL.md: Wyrmfang adds +1 entity interaction range while held (attribute looked up by id; skipped if absent). */
	public static void wyrmfangReach(ServerPlayer p) {
		net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(net.minecraft.resources.Identifier.withDefaultNamespace("entity_interaction_range")).ifPresent(reach -> {
			net.minecraft.world.entity.ai.attributes.AttributeInstance inst = p.getAttribute(reach);
			if (inst == null) return;
			if (p.getMainHandItem().is(WYRMFANG)) {
				inst.addOrUpdateTransientModifier(new AttributeModifier(WYRMFANG_REACH, GearStats.WYRMFANG_REACH, AttributeModifier.Operation.ADD_VALUE));
			} else if (inst.getModifier(WYRMFANG_REACH) != null) {
				inst.removeModifier(WYRMFANG_REACH);
			}
		});
	}

	public static void placeCrystals(ServerLevel level, BlockPos origin) {
		for (int[] c : AphelionArena.CRYSTALS) {
			EndCrystal crystal = EntityTypes.END_CRYSTAL.create(level, EntitySpawnReason.TRIGGERED);
			if (crystal == null) continue;
			crystal.snapTo(origin.getX() + c[0] + 0.5, origin.getY() + 5, origin.getZ() + c[1] + 0.5, 0, 0);
			crystal.setShowBottom(false);
			level.addFreshEntity(crystal);
		}
	}

	public static @Nullable Aphelion summon(ServerLevel level, BlockPos origin, @Nullable ServerPlayer by, ItemStack egg) {
		Aphelion boss = APHELION.create(level, EntitySpawnReason.TRIGGERED);
		if (boss == null) return null;
		boss.snapTo(origin.getX() + 0.5, origin.getY() - 10, origin.getZ() + 12.5, 0, 0);
		boss.setHome(origin);
		level.addFreshEntity(boss);
		boss.startFight(level, origin, by, egg);
		boss.playSound(dev.elsewhere.nether_end_bosses.Sfx3.pick("aphelion", "roar", SoundEvents.ENDER_DRAGON_GROWL), 6.0F, dev.elsewhere.nether_end_bosses.Sfx3.enabled() ? 0.9F : 0.5F);
		return boss;
	}

	static void addPool(ServerLevel level, ElsewhereBoss boss, Vec3 at, float damage) {
		POOLS.add(new Pool(level, boss, at, damage, new int[] {120}));
	}

	private static void tickPools() {
		for (Iterator<Pool> it = POOLS.iterator(); it.hasNext();) {
			Pool p = it.next();
			int t = --p.ticks()[0];
			if (t <= 0 || p.boss().isRemoved()) {
				it.remove();
				continue;
			}
			if (t % 4 == 0) p.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.at().x, p.at().y + 0.3, p.at().z, 6, 1.5, 0.1, 1.5, 0.01);
			if (t % 10 == 0) {
				for (Player pl : p.level().getEntitiesOfClass(Player.class, new AABB(BlockPos.containing(p.at())).inflate(2.5, 1.5, 2.5))) {
					pl.hurtServer(p.level(), p.boss().source(StubFramework.BOSS_MAGIC, null), ElsewhereBoss.scaled(p.level(), p.damage()));
				}
			}
		}
	}
}
