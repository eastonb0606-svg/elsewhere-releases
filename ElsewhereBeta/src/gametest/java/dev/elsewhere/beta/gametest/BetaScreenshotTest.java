package dev.elsewhere.beta.gametest;

import dev.elsewhere.core.travel.RiftTravel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Beta client screenshot pass: every Elsewhere item on item-frame walls, every block on a floor, each dimension from
 * its landing spot, and each boss on a platform. PNGs go to ElsewhereBeta/screenshots/.
 */
public class BetaScreenshotTest implements FabricClientGameTest {
	private static final List<String> BOSSES = List.of("mother_creak", "selenne", "kazehana", "tempestra", "heliodor", "ankhara", "verdigrim",
		"galvanox", "thurgrim", "hrolf", "percolax", "grumblegut", "auris", "lithara", "gulpmother", "strawgrim", "neferkhet", "rimeback",
		"vorlagh", "grunthar", "solaxis", "mournmaw", "ossyra", "aphelion");
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
			this.cmd("time set noon");
			this.cmd("weather clear 100000");
			this.step("items", this::items);
			this.step("blocks", this::blocks);
			this.step("dimensions", this::dimensions);
			this.step("bosses", this::bosses);
		}
	}

	private void step(final String name, final Runnable r) {
		try {
			r.run();
		} catch (Throwable t) {
			System.err.println("[beta-gametest] step " + name + " failed: " + t);
			t.printStackTrace();
		}
	}

	private void cmd(final String c) {
		this.server(s -> {
			try {
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack(), c);
			} catch (Throwable ignored) {
				// cosmetic only
			}
			return null;
		});
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
		p.teleportTo(level, eye.x, eye.y - p.getEyeHeight(), eye.z, Set.of(), yaw, pitch, true);
		p.getAbilities().flying = true;
		p.onUpdateAbilities();
	}

	private static List<Identifier> ids(final net.minecraft.core.Registry<?> reg) {
		return reg.keySet().stream().filter(k -> k.getNamespace().equals("elsewhere")).sorted().toList();
	}

	// ------------------------------------------------------------------ items: 12 x 6 walls of glow item frames
	private void items() {
		List<Item> all = new ArrayList<>();
		for (Identifier id : ids(BuiltInRegistries.ITEM)) {
			all.add(BuiltInRegistries.ITEM.getValue(id));
		}
		int per = 72;
		for (int page = 0; page * per < all.size(); page++) {
			final int pg = page;
			this.server(s -> {
				ServerLevel level = s.overworld();
				ServerPlayer p = player(s);
				BlockPos base = new BlockPos(0, 200, 0);
				for (Entity e : level.getEntitiesOfClass(GlowItemFrame.class, new AABB(base).inflate(20))) {
					e.discard();
				}
				for (int dx = -7; dx <= 7; dx++) {
					for (int dy = -2; dy <= 7; dy++) {
						level.setBlockAndUpdate(base.offset(dx, dy, 3), Blocks.CALCITE.defaultBlockState());
						for (int dz = -9; dz <= 2; dz++) {
							level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
						}
					}
				}
				for (int i = 0; i < per && pg * per + i < all.size(); i++) {
					BlockPos at = base.offset(-6 + i % 12, 6 - i / 12, 2);
					GlowItemFrame frame = new GlowItemFrame(level, at, Direction.NORTH);
					frame.setItem(new ItemStack(all.get(pg * per + i)));
					level.addFreshEntity(frame);
				}
				look(p, level, Vec3.atCenterOf(base.offset(0, 3, -8)), Vec3.atCenterOf(base.offset(0, 3, 2)));
				return null;
			});
			this.settle(20);
			this.shot(String.format("items_%02d", page + 1));
		}
	}

	// ------------------------------------------------------------------ blocks: a floor of every block
	private void blocks() {
		List<Block> all = new ArrayList<>();
		for (Identifier id : ids(BuiltInRegistries.BLOCK)) {
			all.add(BuiltInRegistries.BLOCK.getValue(id));
		}
		int per = 60;
		for (int page = 0; page * per < all.size(); page++) {
			final int pg = page;
			this.server(s -> {
				ServerLevel level = s.overworld();
				ServerPlayer p = player(s);
				BlockPos base = new BlockPos(100, 200, 0);
				for (int dx = -2; dx <= 22; dx++) {
					for (int dz = -2; dz <= 14; dz++) {
						level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.GRASS_BLOCK.defaultBlockState());
						for (int dy = 0; dy <= 4; dy++) {
							level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
						}
					}
				}
				for (int i = 0; i < per && pg * per + i < all.size(); i++) {
					BlockPos at = base.offset((i % 10) * 2, 0, (i / 10) * 2);
					try {
						level.setBlockAndUpdate(at, all.get(pg * per + i).defaultBlockState());
					} catch (Throwable ignored) {
						// shown as a gap
					}
				}
				look(p, level, Vec3.atCenterOf(base.offset(9, 9, -7)), Vec3.atCenterOf(base.offset(9, 0, 6)));
				return null;
			});
			this.settle(20);
			this.shot(String.format("blocks_%02d", page + 1));
		}
	}

	// ------------------------------------------------------------------ dimensions
	private void dimensions() {
		List<ResourceKey<Level>> dims = this.server(s -> {
			List<ResourceKey<Level>> out = new ArrayList<>();
			for (ResourceKey<Level> k : s.levelKeys()) {
				if (k.identifier().getNamespace().equals("elsewhere")) {
					out.add(k);
				}
			}
			return out;
		});
		int i = 0;
		for (ResourceKey<Level> key : dims) {
			i++;
			final int n = i;
			BlockPos landing = this.server(s -> {
				ServerPlayer p = player(s);
				p.teleportTo(s.overworld(), 300.0 * n, 120, 300.0 * n, Set.of(), 0, 0, true);
				RiftTravel.travelNow(p, key);
				return p.blockPosition();
			});
			this.settle(60);
			this.server(s -> {
				ServerLevel level = s.getLevel(key);
				ServerPlayer p = player(s);
				look(p, level, Vec3.atCenterOf(landing).add(0, 1.2, 0), Vec3.atCenterOf(landing).add(30, 3, 20));
				return null;
			});
			this.settle(60);
			this.shot("dim_" + key.identifier().getPath() + "_ground");
			this.server(s -> {
				ServerLevel level = s.getLevel(key);
				ServerPlayer p = player(s);
				look(p, level, Vec3.atCenterOf(landing).add(0, 30, 0), Vec3.atCenterOf(landing).add(70, 5, 50));
				return null;
			});
			this.settle(60);
			this.shot("dim_" + key.identifier().getPath() + "_vista");
		}
		this.server(s -> {
			RiftTravel.travelNow(player(s), null);
			return null;
		});
		this.settle(20);
	}

	// ------------------------------------------------------------------ bosses on a sky platform
	private void bosses() {
		List<Identifier> bossIds = new ArrayList<>();
		for (Identifier id : ids(BuiltInRegistries.ENTITY_TYPE)) {
			if (BOSSES.stream().anyMatch(b -> id.getPath().startsWith(b))) {
				bossIds.add(id);
			}
		}
		for (Identifier id : bossIds) {
			Boolean ok = this.server(s -> {
				ServerLevel level = s.overworld();
				ServerPlayer p = player(s);
				BlockPos base = new BlockPos(-200, 220, 0);
				for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(base).inflate(40), e -> !(e instanceof ServerPlayer))) {
					e.discard();
				}
				for (int dx = -14; dx <= 14; dx++) {
					for (int dz = -14; dz <= 14; dz++) {
						level.setBlockAndUpdate(base.offset(dx, -1, dz), Blocks.SMOOTH_STONE.defaultBlockState());
						for (int dy = 0; dy <= 20; dy++) {
							level.setBlockAndUpdate(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
						}
					}
				}
				EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
				Entity e = type.create(level, EntitySpawnReason.COMMAND);
				if (e == null) {
					return false;
				}
				e.snapTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 180.0F, 0.0F);
				if (e instanceof Mob m) {
					m.setNoAi(true);
					m.setYHeadRot(180.0F);
					m.yBodyRot = 180.0F;
				}
				level.addFreshEntity(e);
				double size = Math.max(2.0, Math.max(e.getBbWidth(), e.getBbHeight()));
				double dist = 3.0 + size * 1.6;
				look(p, level, new Vec3(base.getX() + 0.5 + dist * 0.35, base.getY() + e.getBbHeight() * 0.6 + 1.0, base.getZ() + 0.5 - dist),
					new Vec3(base.getX() + 0.5, base.getY() + e.getBbHeight() * 0.5, base.getZ() + 0.5));
				return true;
			});
			if (ok != null && ok) {
				this.settle(30);
				this.shot("boss_" + id.getPath());
			}
		}
	}
}
