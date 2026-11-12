package dev.elsewhere.nether_end_bosses.gametest;

import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.DevCommands;
import dev.elsewhere.nether_end_bosses.Reg;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Client screenshot run ({@code ./gradlew runClientGameTest}): every item, then each boss in its arena mid-attack. */
public class NetherEndScreenshotTest implements FabricClientGameTest {
	private Path dir;
	private ClientGameTestContext ctx;
	private TestSingleplayerContext world;

	@Override
	public void runTest(ClientGameTestContext context) {
		this.ctx = context;
		this.dir = Path.of(System.getProperty("nether_end_bosses.screenshotDir", "screenshots"));
		try (TestSingleplayerContext sp = context.worldBuilder()
			.adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
			.create()) {
			this.world = sp;
			sp.getConnection().waitForChunksRender();
			sp.getServer().runOnServer(s -> s.setDifficulty(Difficulty.NORMAL, true));
			context.getInput().pressKey(o -> o.keyToggleGui);
			items();
			boss("solaxis", Level.NETHER, new BlockPos(0, 70, 300), "rod_volley", new Vec3(0, 6, 10), new Vec3(0, 5, 0));
			boss("grunthar", Level.NETHER, new BlockPos(200, 70, 300), "gold_bombs", new Vec3(-3, 4, 9), new Vec3(0, 2, -2));
			boss("mournmaw", Level.NETHER, new BlockPos(400, 70, 300), "tear_rain", new Vec3(0, 6, 17), new Vec3(0, 14, 0));
			boss("ossyra", Level.END, new BlockPos(1400, 90, 0), "bullet_storm", new Vec3(-5, 4, 6), new Vec3(0, 3, 0));
			boss("aphelion", Level.END, new BlockPos(1600, 90, 0), "void_breath", new Vec3(-12, 10, 22), new Vec3(0, 6, 0));
		}
	}

	private void shot(String name) {
		this.ctx.waitTicks(4);
		this.ctx.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(this.dir).disableCounterPrefix());
	}

	private void settle(int ticks) {
		this.world.getConnection().waitForChunksRender();
		this.ctx.waitTicks(ticks);
	}

	private <T> T server(java.util.function.Function<MinecraftServer, T> fn) {
		return this.world.getServer().computeOnServer(fn::apply);
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
			List<Item> items = Reg.ITEMS;
			for (int dx = -6; dx <= 6; dx++) {
				for (int dy = -2; dy <= 3; dy++) {
					level.setBlockAndUpdate(base.offset(dx, dy, 3), Blocks.BLACKSTONE.defaultBlockState());
					for (int dz = -6; dz <= 2; dz++) level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
				}
			}
			for (int i = 0; i < items.size() && i < 40; i++) {
				BlockPos at = base.offset(-5 + i % 10, 3 - i / 10 * 1, 2);
				GlowItemFrame frame = new GlowItemFrame(level, at, Direction.NORTH);
				frame.setItem(new ItemStack(items.get(i)));
				level.addFreshEntity(frame);
			}
			look(p, level, Vec3.atCenterOf(base.offset(0, 1, -5)).add(0.5, 0, 0), Vec3.atCenterOf(base.offset(0, 1, 2)).add(0.5, 0, 0));
			return null;
		});
		settle(20);
		shot("items");
	}

	private void boss(String id, ResourceKey<Level> dim, BlockPos origin, String attack, Vec3 eyeOffset, Vec3 lookOffset) {
		server(s -> {
			ServerLevel level = s.getLevel(dim);
			ServerPlayer p = player(s);
			Vec3 o = Vec3.atBottomCenterOf(origin);
			look(p, level, o.add(eyeOffset), o.add(lookOffset));
			return null;
		});
		settle(30);
		server(s -> {
			ServerLevel level = s.getLevel(dim);
			ArenaBuilder.BY_BOSS.get(id).build(level, origin, level.getRandom());
			ElsewhereBoss boss = DevCommands.SUMMONERS.get(id).apply(level, origin);
			if (boss != null && boss.fight() != null) boss.fight().join(player(s));
			return null;
		});
		settle(60);
		server(s -> {
			ServerLevel level = s.getLevel(dim);
			for (ElsewhereBoss b : level.getEntitiesOfClass(ElsewhereBoss.class, new net.minecraft.world.phys.AABB(origin).inflate(40))) {
				if (b.primary() == b) b.forceAttack(attack);
			}
			return null;
		});
		ctx.waitTicks(35);
		shot(id);
		server(s -> {
			ServerLevel level = s.getLevel(dim);
			for (ElsewhereBoss b : level.getEntitiesOfClass(ElsewhereBoss.class, new net.minecraft.world.phys.AABB(origin).inflate(40))) {
				if (b.fight() != null && b.primary() == b) b.fight().reset(level);
			}
			return null;
		});
		ctx.waitTicks(5);
	}
}
