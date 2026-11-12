package dev.elsewhere.gametest;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.boss.mother_creak.ElderTrunkArena;
import dev.elsewhere.boss.mother_creak.MotherCreak;
import dev.elsewhere.boss.selenne.MoonringArena;
import dev.elsewhere.boss.selenne.Selenne;
import dev.elsewhere.boss.selenne.SelenneSummon;
import dev.elsewhere.core.riftruin.ReturnRifts;
import dev.elsewhere.core.travel.RiftTravel;
import dev.elsewhere.core.travel.SafeLanding;
import dev.elsewhere.palehollow.PaleHollowDimension;
import dev.elsewhere.palehollow.entity.CreakingHusk;
import dev.elsewhere.palehollow.entity.EyeblossomWisp;
import dev.elsewhere.registry.ModBlocks;
import dev.elsewhere.registry.ModEntities;
import dev.elsewhere.registry.ModItems;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Client screenshot run ({@code ./gradlew runClientGameTest}): Wayfinder items, Pale Hollow landscape, a Return Rift
 * beam, the rift effect, both mobs and both bosses. PNGs land in {@code Elsewhere/screenshots/}.
 */
public class ElsewhereScreenshotTest implements FabricClientGameTest {
	private Path dir;
	private ClientGameTestContext ctx;
	private TestSingleplayerContext world;

	@Override
	public void runTest(final ClientGameTestContext context) {
		this.ctx = context;
		this.dir = Path.of(System.getProperty("elsewhere.screenshotDir", "screenshots"));
		try (TestSingleplayerContext sp = context.worldBuilder()
			.adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
			.create()) {
			this.world = sp;
			sp.getConnection().waitForChunksRender();
			context.getInput().pressKey(o -> o.keyToggleGui);
			this.items();
			this.landscape();
			this.returnRift();
			this.rift();
			this.mobs();
			this.motherCreak();
			this.selenne();
		}
	}

	private void shot(final String name) {
		this.ctx.waitTicks(4);
		this.ctx.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(this.dir).disableCounterPrefix());
	}

	private void settle(final int ticks) {
		this.world.getConnection().waitForChunksRender();
		this.ctx.waitTicks(ticks);
	}

	private <T> T server(final java.util.function.Function<MinecraftServer, T> fn) {
		return this.world.getServer().computeOnServer(fn::apply);
	}

	private static ServerPlayer player(final MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void look(final ServerPlayer p, final ServerLevel level, final Vec3 eye, final Vec3 target) {
		Vec3 d = target.subtract(eye);
		float yaw = (float)(Math.atan2(d.z, d.x) * 180.0 / Math.PI) - 90.0F;
		float pitch = (float)(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0 / Math.PI);
		p.teleportTo(level, eye.x, eye.y - p.getEyeHeight(), eye.z, java.util.Set.of(), yaw, pitch, true);
		p.getAbilities().flying = true;
		p.onUpdateAbilities();
	}

	// ------------------------------------------------------------------ shots
	private void items() {
		this.server(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = player(s);
			BlockPos base = p.blockPosition().offset(0, 30, 0);
			List<Item> items = List.of(ModItems.WAYFINDER, ModItems.WAYFINDER_PALE_HOLLOW, ModItems.FRAYED_WAYFINDER, ModItems.HEARTWOOD_SHARD,
				ModItems.PALE_BARK, ModItems.PALE_SAP, ModItems.UNBLINKING_EYE, ModItems.HEARTWOOD_EFFIGY, ModItems.MOONRING_SEED,
				ModItems.RESIN_HEART_LOCKET, ModItems.HEARTWOOD_GREATBOW, ModItems.EYEBLOSSOM_CIRCLET, ModItems.MOON_EYE,
				ModBlocks.RESIN_HEART.asItem(), ModBlocks.HEARTWOOD_LOG.asItem(), ModBlocks.MOONRING_STONE.asItem());
			for (int dx = -4; dx <= 4; dx++) {
				for (int dy = -1; dy <= 3; dy++) {
					level.setBlockAndUpdate(base.offset(dx, dy, 3), Blocks.PALE_OAK_PLANKS.defaultBlockState());
					for (int dz = -5; dz <= 2; dz++) {
						level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					}
				}
			}
			for (int i = 0; i < items.size(); i++) {
				BlockPos at = base.offset(-3 + i % 8, 2 - i / 8 * 2, 2);
				GlowItemFrame frame = new GlowItemFrame(level, at, Direction.NORTH);
				frame.setItem(new ItemStack(items.get(i)));
				level.addFreshEntity(frame);
			}
			look(p, level, Vec3.atCenterOf(base.offset(0, 1, -4)).add(0.5, 0.0, 0.0), Vec3.atCenterOf(base.offset(0, 1, 2)).add(0.5, 0.0, 0.0));
			return null;
		});
		this.settle(20);
		this.shot("01_items");
	}

	private BlockPos hollowLanding;

	private void landscape() {
		this.hollowLanding = this.server(s -> {
			ServerPlayer p = player(s);
			RiftTravel.travelNow(p, PaleHollowDimension.LEVEL);
			return p.blockPosition();
		});
		this.settle(40);
		this.server(s -> {
			ServerLevel level = s.getLevel(PaleHollowDimension.LEVEL);
			ServerPlayer p = player(s);
			BlockPos b = this.hollowLanding;
			look(p, level, Vec3.atCenterOf(b).add(0, 28, 0), Vec3.atCenterOf(b).add(60, 4, 40));
			return null;
		});
		this.settle(60);
		this.shot("02_pale_hollow_landscape");
		this.server(s -> {
			ServerLevel level = s.getLevel(PaleHollowDimension.LEVEL);
			ServerPlayer p = player(s);
			BlockPos b = this.hollowLanding;
			look(p, level, Vec3.atCenterOf(b).add(0, 1.2, 0), Vec3.atCenterOf(b).add(20, 6, 14));
			return null;
		});
		this.settle(30);
		this.shot("03_pale_hollow_forest_floor");
	}

	private void returnRift() {
		this.server(s -> {
			ServerLevel level = s.getLevel(PaleHollowDimension.LEVEL);
			ServerPlayer p = player(s);
			BlockPos rift = ReturnRifts.nearestRift(level, this.hollowLanding.getX(), this.hollowLanding.getZ());
			level.getChunk(rift.getX() >> 4, rift.getZ() >> 4);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rift.getX(), rift.getZ());
			for (int yy = level.getMinY(); yy < level.getMaxY(); yy++) {
				if (level.getBlockState(new BlockPos(rift.getX(), yy, rift.getZ())).is(ModBlocks.RETURN_RIFT)) {
					y = yy;
					break;
				}
			}
			Vec3 r = new Vec3(rift.getX() + 0.5, y + 1, rift.getZ() + 0.5);
			look(p, level, r.add(18, 9, 12), r.add(0, 6, 0));
			return null;
		});
		this.settle(100);
		this.settle(100);
		this.shot("04_return_rift");
	}

	private void rift() {
		this.server(s -> {
			ServerPlayer p = player(s);
			ServerLevel overworld = s.overworld();
			BlockPos at = new BlockPos(0, 0, 0);
			overworld.getChunk(40 >> 4, 40 >> 4);
			int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 40, 40);
			look(p, overworld, new Vec3(40.5, y + 1.6, 40.5), new Vec3(40.5, y + 1.6, 45.5));
			return at;
		});
		this.settle(30);
		this.server(s -> {
			ServerPlayer p = player(s);
			RiftTravel.openWayfinderRift(p, PaleHollowDimension.LEVEL);
			// step back so the rift stays open in view without taking us
			ServerLevel level = s.overworld();
			Vec3 rift = RiftTravel.lastRift.position();
			Vec3 eye = p.getEyePosition();
			Vec3 back = eye.add(eye.subtract(rift.add(0, 1.3, 0)).normalize().scale(3.0));
			look(p, level, new Vec3(back.x, eye.y, back.z), rift.add(0, 1.3, 0));
			return null;
		});
		this.ctx.waitTicks(45);
		this.shot("05_rift_opening");
		this.ctx.waitTicks(40);
		this.shot("06_rift_open");
	}

	private void mobs() {
		this.server(s -> {
			ServerLevel level = s.getLevel(PaleHollowDimension.LEVEL);
			ServerPlayer p = player(s);
			BlockPos b = this.hollowLanding;
			Vec3 husk = SafeLanding.find(level, b.getX() + 6, b.getZ(), 8, 150, Blocks.PALE_OAK_PLANKS);
			CreakingHusk h = ModEntities.CREAKING_HUSK.create(level, EntitySpawnReason.COMMAND);
			h.snapTo(husk.x, husk.y, husk.z, 90.0F, 0.0F);
			h.setNoAi(true);
			h.setYHeadRot(90.0F);
			h.yBodyRot = 90.0F;
			level.addFreshEntity(h);
			EyeblossomWisp w = ModEntities.EYEBLOSSOM_WISP.create(level, EntitySpawnReason.COMMAND);
			w.snapTo(husk.x, husk.y + 1.5, husk.z + 3.0, 90.0F, 0.0F);
			w.setNoAi(true);
			level.addFreshEntity(w);
			look(p, level, new Vec3(husk.x - 5.5, husk.y + 2.2, husk.z + 1.5), new Vec3(husk.x, husk.y + 1.6, husk.z + 1.5));
			return null;
		});
		this.settle(30);
		this.shot("07_creaking_husk_and_wisp");
		this.server(s -> {
			ServerLevel level = s.getLevel(PaleHollowDimension.LEVEL);
			ServerPlayer p = player(s);
			EyeblossomWisp w = level.getEntitiesOfClass(EyeblossomWisp.class, p.getBoundingBox().inflate(20)).getFirst();
			look(p, level, w.position().add(-2.2, 0.6, 0.0), w.position().add(0, 0.5, 0));
			return null;
		});
		this.settle(10);
		this.shot("08_eyeblossom_wisp_closeup");
	}

	private void motherCreak() {
		this.server(s -> {
			ServerLevel level = s.getLevel(PaleHollowDimension.LEVEL);
			ServerPlayer p = player(s);
			BlockPos top = new BlockPos(this.hollowLanding.getX() + 120, 0, this.hollowLanding.getZ());
			level.getChunk(top.getX() >> 4, top.getZ() >> 4);
			top = new BlockPos(top.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top.getX(), top.getZ()), top.getZ());
			MotherCreak boss = ElderTrunkArena.buildAndSpawn(level, top, null);
			boss.wake(level, 1);
			boss.setNoAi(true);
			Vec3 at = boss.position();
			look(p, level, at.add(0.5, 1.7, -8.0), at.add(0, 5.0, 0));
			return null;
		});
		this.settle(50);
		this.shot("09_mother_creak_elder_trunk");
	}

	private void selenne() {
		this.server(s -> {
			ServerLevel level = s.getLevel(PaleHollowDimension.LEVEL);
			ServerPlayer p = player(s);
			BlockPos c = new BlockPos(this.hollowLanding.getX() - 140, 0, this.hollowLanding.getZ() + 40);
			level.getChunk(c.getX() >> 4, c.getZ() >> 4);
			c = new BlockPos(c.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ()), c.getZ());
			MoonringArena.build(level, c);
			SelenneSummon.summon(level, c, null);
			look(p, level, new Vec3(c.getX() + 0.5, c.getY() + 3.5, c.getZ() + 9.0), new Vec3(c.getX() + 0.5, c.getY() + 7.5, c.getZ() + 0.5));
			return null;
		});
		this.settle(60);
		this.shot("10_selenne_moonring");
		Elsewhere.LOG.info("[screenshots] done");
	}
}
