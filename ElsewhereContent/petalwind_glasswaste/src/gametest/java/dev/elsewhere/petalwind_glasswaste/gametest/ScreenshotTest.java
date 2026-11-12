package dev.elsewhere.petalwind_glasswaste.gametest;

import dev.elsewhere.boss.ankhara.Ankhara;
import dev.elsewhere.boss.tempestra.EyeOfTheGale;
import dev.elsewhere.glasswaste.GlasswasteDimension;
import dev.elsewhere.petalwind.PetalwindDimension;
import dev.elsewhere.petalwind_glasswaste.Bosses;
import dev.elsewhere.petalwind_glasswaste.ModEntities;
import dev.elsewhere.petalwind_glasswaste.Reg;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import dev.elsewhere.stub.petalwind_glasswaste.SafeSpot;
import java.nio.file.Path;
import java.util.ArrayList;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Client screenshot run (`gradlew runClientGameTest`): both dimensions, the four bosses in their arenas, the mobs, the items. */
public class ScreenshotTest implements FabricClientGameTest {
	private Path dir;
	private ClientGameTestContext ctx;
	private TestSingleplayerContext world;

	@Override
	public void runTest(ClientGameTestContext context) {
		this.ctx = context;
		this.dir = Path.of(System.getProperty("petalwind_glasswaste.screenshotDir", "screenshots"));
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()) {
			this.world = sp;
			sp.getConnection().waitForChunksRender();
			context.getInput().pressKey(o -> o.keyToggleGui);
			items();
			landscape("petalwind", PetalwindDimension.LEVEL == null ? null : "petalwind", 40);
			landscape("glasswaste", "glasswaste", 25);
			boss("kazehana", PetalwindDimension.LEVEL == null);
			boss("tempestra", false);
			boss("heliodor", false);
			boss("ankhara", false);
			mobs("petalwind_mobs", true);
			mobs("glasswaste_mobs", false);
		}
	}

	private void shot(String name) {
		ctx.waitTicks(4);
		ctx.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(dir).disableCounterPrefix());
	}

	private void settle(int ticks) {
		world.getConnection().waitForChunksRender();
		ctx.waitTicks(ticks);
		world.getConnection().waitForChunksRender();
	}

	private <T> T server(java.util.function.Function<MinecraftServer, T> fn) {
		return world.getServer().computeOnServer(fn::apply);
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void look(ServerPlayer p, ServerLevel level, Vec3 eye, Vec3 target) {
		Vec3 d = target.subtract(eye);
		float yaw = (float) (Math.atan2(d.z, d.x) * 180.0 / Math.PI) - 90.0F;
		float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0 / Math.PI);
		p.teleportTo(level, eye.x, eye.y - p.getEyeHeight(), eye.z, java.util.Set.of(), yaw, pitch, true);
		p.getAbilities().flying = true;
		p.onUpdateAbilities();
	}

	private void items() {
		server(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = player(s);
			BlockPos base = p.blockPosition().offset(0, 30, 0);
			List<Item> items = new ArrayList<>(Reg.TAB_ITEMS.getOrDefault("petalwind", List.of()));
			items.addAll(Reg.TAB_ITEMS.getOrDefault("glasswaste", List.of()));
			int cols = 12, rows = (items.size() + cols - 1) / cols;
			for (int dx = -7; dx <= 7; dx++) for (int dy = -1; dy <= rows; dy++) {
				level.setBlockAndUpdate(base.offset(dx, dy, 3), Blocks.SMOOTH_SANDSTONE.defaultBlockState());
				for (int dz = -8; dz <= 2; dz++) level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
			}
			for (int i = 0; i < items.size(); i++) {
				BlockPos at = base.offset(-6 + i % cols, rows - 1 - i / cols, 2);
				GlowItemFrame frame = new GlowItemFrame(level, at, Direction.NORTH);
				frame.setItem(new ItemStack(items.get(i)));
				level.addFreshEntity(frame);
			}
			look(p, level, Vec3.atCenterOf(base.offset(0, rows / 2, -8)).add(-0.5, 0, 0), Vec3.atCenterOf(base.offset(0, rows / 2, 2)).add(-0.5, 0, 0));
			return null;
		});
		settle(20);
		shot("items");
	}

	private void landscape(String name, String dim, int height) {
		server(s -> {
			ServerLevel level = s.getLevel(dim.equals("petalwind") ? PetalwindDimension.LEVEL : GlasswasteDimension.LEVEL);
			BlockPos spot = null;
			for (int k = 0; k < 40 && spot == null; k++) {
				BlockPos probe = new BlockPos(200 + k * 160, 0, 200 + (k % 5) * 160);
				BlockPos c = SafeSpot.find(level, probe, 64);
				if (c == null) continue;
				String b = level.getBiome(c).unwrapKey().map(key -> key.identifier().getPath()).orElse("");
				if (b.equals(dim.equals("petalwind") ? "blossom_crowns" : "ember_dunes")) spot = c;
			}
			if (spot == null) spot = SafeSpot.find(level, new BlockPos(200, 0, 200), 400);
			if (spot == null) spot = new BlockPos(200, 150, 200);
			Vec3 eye = Vec3.atCenterOf(spot).add(0, height, 0);
			look(player(s), level, eye, eye.add(40, -height * 0.7, 40));
			return null;
		});
		settle(80);
		shot(name);
	}

	private void boss(String id, boolean unused) {
		server(s -> {
			boolean sky = id.equals("kazehana") || id.equals("tempestra");
			ServerLevel level = s.getLevel(sky ? PetalwindDimension.LEVEL : GlasswasteDimension.LEVEL);
			BlockPos at = new BlockPos(3000 + Bosses.IDS.indexOf(id) * 300, sky ? 170 : 0, 3000);
			level.getChunk(at.getX() >> 4, at.getZ() >> 4);
			if (!sky) at = at.atY(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()));
			BossBase b = Bosses.summon(level, id, at);
			BlockPos c = b.arenaCenter();
			Vec3 target = b.position().add(0, b.getBbHeight() * 0.5, 0);
			Vec3 eye = switch (id) {
				case "kazehana" -> Vec3.atBottomCenterOf(c).add(0, 3.5, -8);
				case "tempestra" -> Vec3.atBottomCenterOf(EyeOfTheGale.platforms(c).get(6)).add(0, 3, 0);
				case "heliodor" -> Vec3.atBottomCenterOf(c).add(-2, 4, -9);
				default -> Vec3.atBottomCenterOf(c).add(0, 9, -16);
			};
			if (b instanceof Ankhara a) {
				a.debugSurface();
				target = Vec3.atBottomCenterOf(c).add(0, 1, 0);
			}
			look(player(s), level, eye, target);
			return null;
		});
		settle(60);
		String report = server(s -> {
			StringBuilder sb = new StringBuilder();
			for (ServerLevel l : s.getAllLevels()) for (Entity e : l.getAllEntities()) {
				if (e instanceof BossBase b && b.bossId().equals(id)) sb.append(String.format("server %s at %.1f %.1f %.1f alive=%s removed=%s; ", id, e.getX(), e.getY(), e.getZ(), e.isAlive(), e.isRemoved()));
			}
			return sb.toString();
		});
		String client = ctx.computeOnClient(mc -> {
			StringBuilder sb = new StringBuilder();
			if (mc.level != null) for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof BossBase b && b.bossId().equals(id)) sb.append(String.format("client at %.1f %.1f %.1f invisible=%s; ", e.getX(), e.getY(), e.getZ(), e.isInvisible()));
			}
			if (mc.player != null) sb.append(String.format("camera %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
			return sb.toString();
		});
		System.out.println("[pg-gametest] " + report + " | " + client);
		shot(id);
	}

	private void mobs(String name, boolean petalwind) {
		server(s -> {
			ServerLevel level = s.getLevel(petalwind ? PetalwindDimension.LEVEL : GlasswasteDimension.LEVEL);
			BlockPos base = new BlockPos(5000, petalwind ? 330 : 160, petalwind ? 5000 : 5200);
			level.getChunk(base.getX() >> 4, base.getZ() >> 4);
			for (int dx = -12; dx <= 12; dx++) for (int dz = -14; dz <= 8; dz++) {
				level.setBlockAndUpdate(base.offset(dx, -1, dz), petalwind ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.SAND.defaultBlockState());
				for (int dy = 0; dy < 12; dy++) level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
			}
			List<EntityType<? extends Mob>> types = petalwind
				? List.of(ModEntities.GALE_KITE, ModEntities.BLOSSOM_BREEZE, ModEntities.CUMULUS, ModEntities.WINDWRIGHT, ModEntities.SKYWHALE)
				: List.of(ModEntities.GLASS_SCARAB, ModEntities.SANDSWIMMER, ModEntities.SUNWRIGHT_REVENANT, ModEntities.DUNE_FACTOR, ModEntities.SUN_MOTE);
			for (int i = 0; i < types.size(); i++) {
				Entity e = types.get(i).create(level, EntitySpawnReason.COMMAND);
				if (e instanceof Mob m) {
					m.snapTo(base.getX() - 8.5 + i * 4, base.getY() + (types.get(i) == ModEntities.GALE_KITE ? 2 : 0), base.getZ() + 3, 180, 0);
					m.setNoAi(true);
					m.setPersistenceRequired();
					if (m instanceof dev.elsewhere.petalwind.Cumulus cu) cu.setSize(2, true);
					level.addFreshEntity(m);
				}
			}
			look(player(s), level, Vec3.atCenterOf(base.offset(0, 3, -9)), Vec3.atCenterOf(base.offset(0, 1, 3)));
			return null;
		});
		settle(30);
		shot(name);
	}
}
