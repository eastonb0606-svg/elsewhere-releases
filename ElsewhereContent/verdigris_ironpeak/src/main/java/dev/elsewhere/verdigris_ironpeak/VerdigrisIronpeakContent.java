package dev.elsewhere.verdigris_ironpeak;

import dev.elsewhere.boss.galvanox.GalvanoxArena;
import dev.elsewhere.boss.galvanox.GalvanoxEntities;
import dev.elsewhere.boss.galvanox.GalvanoxSummon;
import dev.elsewhere.boss.hrolf.HrolfEntities;
import dev.elsewhere.boss.hrolf.HrolfSummon;
import dev.elsewhere.boss.hrolf.ProvingGroundsArena;
import dev.elsewhere.boss.thurgrim.ThurgrimArena;
import dev.elsewhere.boss.thurgrim.ThurgrimEntities;
import dev.elsewhere.boss.thurgrim.ThurgrimSummon;
import dev.elsewhere.boss.verdigrim.VerdigrimArena;
import dev.elsewhere.boss.verdigrim.VerdigrimEntities;
import dev.elsewhere.boss.verdigrim.VerdigrimSummon;
import dev.elsewhere.ironpeak.Ironpeak;
import dev.elsewhere.ironpeak.IronpeakBlocks;
import dev.elsewhere.ironpeak.IronpeakEntities;
import dev.elsewhere.ironpeak.IronpeakItems;
import dev.elsewhere.stub.verdigris_ironpeak.BossGuards;
import dev.elsewhere.stub.verdigris_ironpeak.ReturnRiftStub;
import dev.elsewhere.stub.verdigris_ironpeak.SetBonus;
import dev.elsewhere.stub.verdigris_ironpeak.SignatureGear;
import dev.elsewhere.stub.verdigris_ironpeak.StubPlayerData;
import dev.elsewhere.stub.verdigris_ironpeak.TripTrigger;
import dev.elsewhere.stub.verdigris_ironpeak.Trips;
import dev.elsewhere.verdigris_ironpeak.compat.TestMeterBridge;
import dev.elsewhere.verdigris_ironpeak.world.ProceduralStructure;
import dev.elsewhere.verdigris_sea.VerdigrisBlocks;
import dev.elsewhere.verdigris_sea.VerdigrisEntities;
import dev.elsewhere.verdigris_sea.VerdigrisItems;
import dev.elsewhere.verdigris_sea.VerdigrisSea;
import dev.elsewhere.verdigris_sea.mob.BarnacleGolem;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point of the Verdigris Sea + Ironpeak module. The core's entry point calls {@link #init()} at merge. */
public final class VerdigrisIronpeakContent {
	public static final String NS = Reg.NS;
	public static final Logger LOG = LoggerFactory.getLogger("Elsewhere/verdigris_ironpeak");
	/** Accidental-trip triggers of this module's dimensions (DIMENSIONS.md section 9). */
	public static final List<TripTrigger> TRIPS = List.of(VerdigrisSea.STORM_MINUTE, Ironpeak.PEAK);

	private VerdigrisIronpeakContent() {}

	public static void init() {
		dev.elsewhere.boss.kit.BossKit.init();
		VerdigrisIronpeakSounds.init();
		KelpwoodFeature.init();
		VerdigrisBlocks.init();
		IronpeakBlocks.init();
		VerdigrisItems.init();
		IronpeakItems.init();
		SignatureGear.init();
		ReturnRiftStub.init();
		VerdigrisEntities.init();
		dev.elsewhere.verdigris_ironpeak.s3.groups.SpawnGroups3.init(); // ARMIES 6 spawn groups (s-spawngroups, key spawn_groups_verdigris_ironpeak)
		IronpeakEntities.init();
		VerdigrimEntities.init();
		GalvanoxEntities.init();
		ThurgrimEntities.init();
		HrolfEntities.init();
		ProceduralStructure.init();
		VerdigrimArena.init();
		GalvanoxArena.init();
		ThurgrimArena.init();
		ProvingGroundsArena.init();
		WorldFeatures.init();
		StructureTraders.init();
		ArsenalHooks.init();
		VerdigrimSummon.init();
		GalvanoxSummon.init();
		ThurgrimSummon.init();
		HrolfSummon.init();
		// touch the set bonus definitions so they register
		VerdigrisSea.DIVING_SUIT.getClass();
		StubPlayerData.DATA.getClass();
		Ironpeak.CLIMBER_DATA.getClass();
		Ironpeak.MOUNTAIN_GOAT.getClass();
		EndgameGear.init();
		dev.elsewhere.verdigris_sea.ThunderTide.init();

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Reg.id("verdigris_ironpeak"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.verdigris_ironpeak"))
			.icon(() -> new ItemStack(VerdigrisItems.VERDIGRIS_GEAR))
			.displayItems((params, out) -> {
				for (Item item : Reg.ITEMS.values()) if (item != IronpeakItems.SKYIRON_SWORD) out.accept(item); // the sword stays registered but hidden (no recipe, no tab)
			})
			.build());

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			VerdigrisSea.serverTick(server);
			Ironpeak.serverTick(server);
			EndgameGear.serverTick(server);
			GalvanoxSummon.serverTick(server);
			SetBonus.serverTick(server);
			Trips.serverTick(server);
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> TestMeterBridge.registerFoods());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LightningBolt bolt) {
				VerdigrisSea.onLightning(level, bolt);
				GalvanoxSummon.onLightning(level, bolt);
			}
			Ironpeak.onMobLoad(entity, level);
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> VerdigrisSea.allowDamage(entity, source, amount));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(IronpeakItems.SUMMIT_CROWN)) {
				TestMeterBridge.forgiveDamageStress(p, taken / 2);
			}
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.BELL)) return VerdigrimSummon.onBell(level, pos, sp);
			if (state.is(Blocks.NOTE_BLOCK)) return VerdigrimSummon.onNoteBlock(level, pos, sp);
			if ((state.is(Blocks.CHEST) || state.is(Blocks.BARREL) || state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) && !sp.isCreative()) {
				for (BarnacleGolem g : level.getEntitiesOfClass(BarnacleGolem.class, new AABB(pos).inflate(10))) g.provoke(sp);
			}
			ItemStack held = sp.getItemInHand(hand);
			if (held.is(Items.BOWL) && state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT) && level.dimension() == Ironpeak.DIM) {
				return Ironpeak.brewTea(sp, held) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			if (held.is(net.minecraft.tags.ItemTags.AXES)) {
				BlockState stripped = Strip.of(state);
				if (stripped != null) {
					level.setBlock(pos, stripped, 11);
					level.playSound(null, pos, net.minecraft.sounds.SoundEvents.AXE_STRIP.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});
		LOG.info("Verdigris Sea + Ironpeak content registered: {} blocks, {} items, {} entity types", Reg.BLOCKS.size(), Reg.ITEMS.size(), Reg.ENTITIES.size());
	}

	/** Kelpwood stripping (the vanilla axe transformer is data in a vanilla file; this avoids overriding it). */
	static final class Strip {
		static BlockState of(BlockState s) {
			if (s.is(VerdigrisBlocks.KELPWOOD_LOG)) return VerdigrisBlocks.STRIPPED_KELPWOOD_LOG.withPropertiesOf(s);
			if (s.is(VerdigrisBlocks.KELPWOOD_WOOD_BLOCK)) return VerdigrisBlocks.STRIPPED_KELPWOOD_WOOD.withPropertiesOf(s);
			return null;
		}
	}

	public static boolean bossSource(net.minecraft.world.damagesource.DamageSource s) {
		return BossGuards.fromBoss(s);
	}
}
