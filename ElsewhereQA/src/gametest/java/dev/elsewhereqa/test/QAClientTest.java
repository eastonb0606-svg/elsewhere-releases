package dev.elsewhereqa.test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client pass for the Elsewhere beta: every dimension in 4 directions (+ sky), the survival HUD in every dimension with
 * the Frayed Wayfinder in hand, every creative tab, every boss mid-fight (two frames) on a platform and in its real
 * arena, every non-boss Elsewhere mob, the Wayfinders in hand, and a real right-click Wayfinder trip (rift visual,
 * arrival, way home). A client-side model lookup for every elsewhere item. Screenshots go to ElsewhereQA/screenshots/.
 *
 * Optional sections are toggled by system properties so the same test serves beta 1 and beta 2:
 * elsewhereqa.sections = comma list of: items,tabs,dims,bosses,arenas,mobs,wayfinder,glimmer (default: all).
 */
public class QAClientTest implements FabricClientGameTest {
	static final Logger LOG = LoggerFactory.getLogger("ElsewhereQA-client");
	static final String NS = "elsewhere";
	static final List<String> BOSSES = List.of("mother_creak", "selenne", "kazehana", "tempestra", "heliodor", "ankhara", "verdigrim",
		"galvanox", "thurgrim", "hrolf", "percolax", "grumblegut", "auris", "lithara", "gulpmother", "strawgrim", "neferkhet", "rimeback", "vorlagh",
		"grunthar", "solaxis", "mournmaw", "ossyra", "aphelion");
	private Path out;
	private String sections;

	private boolean on(final String s) {
		return sections == null || sections.isBlank() || sections.contains(s);
	}

	@Override
	public void runTest(final ClientGameTestContext ctx) {
		if ("release".equals(System.getProperty("elsewhereqa.client")) || "fps".equals(System.getProperty("elsewhereqa.client")) || "gallery".equals(System.getProperty("elsewhereqa.client")) || "calib".equals(System.getProperty("elsewhereqa.client")) || "shots".equals(System.getProperty("elsewhereqa.client"))) {
			return; // ReleaseClientTest runs instead
		}
		String shots = System.getProperty("elsewhereqa.shots");
		sections = System.getProperty("elsewhereqa.sections");
		out = shots != null ? Path.of(shots) : net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("../../../screenshots").normalize();
		try {
			Files.createDirectories(out);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		ctx.getInput().resizeWindow(1280, 720);
		ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
		LOG.info("[qa-client] gui scale option {} -> {}", ctx.computeOnClient(mc -> mc.options.guiScale().get()),
			ctx.computeOnClient(mc -> mc.getWindow().getGuiScale()));

		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			sp.getConnection().waitForChunksRender();
			server.runCommand("gamerule random_tick_speed 0");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			server.runCommand("gamemode creative @a");
			server.runCommand("effect give @a resistance infinite 255 true");
			server.runCommand("effect give @a saturation infinite 255 true");
			server.runCommand("effect give @a regeneration infinite 255 true");

			// 1. Client-side model lookup for every elsewhere item.
			if (on("items")) {
				List<String> missing = ctx.computeOnClient(mc -> {
					List<String> m = new ArrayList<>();
					for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
						if (!id.getNamespace().equals(NS)) {
							continue;
						}
						ItemStack st = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
						Identifier model = st.get(DataComponents.ITEM_MODEL);
						if (model == null || mc.getModelManager().getItemModel(model) instanceof MissingItemModel) {
							m.add(id.getPath());
						}
					}
					return m;
				});
				LOG.info("[qa-client] {} items with a missing item model: {}", missing.size(), missing);
				// every elsewhere item, 36 per page, in the survival inventory screen (icons at default GUI scale)
				List<Identifier> all = server.computeOnServer(s -> BuiltInRegistries.ITEM.keySet().stream()
					.filter(k -> k.getNamespace().equals(NS)).sorted((a, b) -> a.getPath().compareTo(b.getPath())).toList());
				server.runCommand("gamemode survival @a");
				for (int page = 0; page * 36 < all.size(); page++) {
					final int from = page * 36;
					server.runOnServer(s -> {
						ServerPlayer p = s.getPlayerList().getPlayers().get(0);
						p.getInventory().clearContent();
						for (int k = 0; k < 36 && from + k < all.size(); k++) {
							p.getInventory().setItem(k, new ItemStack(BuiltInRegistries.ITEM.getValue(all.get(from + k))));
						}
					});
					ctx.waitTicks(3);
					ctx.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
					ctx.waitTicks(3);
					shot(ctx, String.format(Locale.ROOT, "items_%02d", page + 1));
					ctx.setScreen(() -> null);
					LOG.info("[qa-client] items page {}: {}", page + 1, all.subList(from, Math.min(all.size(), from + 36)));
				}
				server.runCommand("clear @a");
				server.runCommand("gamemode creative @a");
			}

			// 1b. JEI (only when JEI is loaded): item list, attunement recipes, an info page.
			boolean jei = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jei");
			LOG.info("[qa-client] JEI loaded: {}; fabric-api {}", jei, net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("fabric-api")
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?"));
			if (jei && on("jei")) {
				server.runCommand("gamemode survival @a");
				JeiShots.run(ctx, n -> shot(ctx, n));
				server.runCommand("gamemode creative @a");
			}

			// 2. Creative tabs with every new item.
			int tabCount = 0;
			if (on("tabs")) {
				List<Identifier> tabs = ctx.computeOnClient(mc -> BuiltInRegistries.CREATIVE_MODE_TAB.keySet().stream()
					.filter(k -> k.getNamespace().startsWith(NS)).sorted().toList());
				tabCount = tabs.size();
				LOG.info("[qa-client] elsewhere creative tabs: {}", tabs);
				ctx.setScreen(() -> new CreativeModeInventoryScreen(Minecraft.getInstance().player, Minecraft.getInstance().player.connection.enabledFeatures(), true));
				ctx.waitTicks(5);
				for (Identifier tabId : tabs) {
					ctx.runOnClient(mc -> {
						try {
							CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(tabId);
							Method sel = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
							sel.setAccessible(true);
							sel.invoke(mc.gui.screen(), tab);
						} catch (ReflectiveOperationException e) {
							throw new RuntimeException(e);
						}
					});
					ctx.waitTicks(3);
					shot(ctx, "tab_" + tabId.getPath());
					// page 2+ if the tab scrolls
					for (int page = 1; page <= 3; page++) {
						ctx.getInput().scroll(-5);
						ctx.waitTicks(2);
						shot(ctx, "tab_" + tabId.getPath() + "_p" + (page + 1));
					}
				}
				ctx.setScreen(() -> null);
			}

			// 3. Wayfinders in hand (Overworld, survival, first person).
			if (on("wayfinder")) {
				server.runCommand("gamemode survival @a");
				server.runCommand("clear @a");
				String[] wf = {"wayfinder", "wayfinder_pale_hollow", "frayed_wayfinder", "wayfinder_petalwind", "wayfinder_glasswaste",
					"wayfinder_greenhaze", "wayfinder_ironpeak", "wayfinder_verdigris_sea", "wayfinder_the_hush"};
				for (int k = 0; k < wf.length; k++) {
					server.runCommand("item replace entity @a " + (k < 9 ? "hotbar." + k : "inventory." + (k - 9)) + " with elsewhere:" + wf[k]);
				}
				for (int k = 0; k < 9; k++) {
					final int slot = k;
					ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(slot));
					ctx.waitTicks(12);
					shot(ctx, "hand_" + wf[k]);
				}
			}

			// 4. Every dimension, 4 directions + sky, then the survival HUD with the Frayed Wayfinder in hand.
			List<ResourceKey<Level>> dims = server.computeOnServer(s -> s.levelKeys().stream()
				.filter(k -> k.identifier().getNamespace().equals(NS)).sorted((a, b) -> a.identifier().toString().compareTo(b.identifier().toString())).toList());
			if (on("dims")) {
				for (ResourceKey<Level> key : dims) {
					String name = key.identifier().getPath();
					server.runCommand("gamemode creative @a");
					String where = server.computeOnServer(s -> {
						ServerPlayer p = s.getPlayerList().getPlayers().get(0);
						try {
							Class<?> rt = Class.forName("dev.elsewhere.core.travel.RiftTravel");
							Method m = rt.getMethod("travelNow", ServerPlayer.class, ResourceKey.class);
							m.invoke(null, p, key);
						} catch (ReflectiveOperationException e) {
							ServerLevel lvl = s.getLevel(key);
							lvl.getChunk(0, 0);
							int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
							p.teleportTo(lvl, 0.5, Math.max(y, lvl.getMinY() + 1), 0.5, java.util.Set.of(), 0, 0, true);
						}
						return p.level().dimension().identifier() + " " + p.blockPosition().toShortString();
					});
					LOG.info("[qa-client] in {} at {}", name, where);
					ctx.waitTicks(10);
					String clientDim = ctx.computeOnClient(mc -> mc.level.dimension().identifier().toString());
					LOG.info("[qa-client] client level now {}", (Object)clientDim);
					server.runCommand("gamemode spectator @a");
					ctx.waitTicks(20);
					sp.getConnection().waitForChunksRender();
					String[] dirs = {"south", "west", "north", "east"};
					for (int d = 0; d < 4; d++) {
						server.runCommand(String.format(Locale.ROOT, "execute as @a at @s run tp @s ~ ~ ~ %d 8", d * 90));
						ctx.waitTicks(10);
						sp.getConnection().waitForChunksRender();
						ctx.waitTicks(10);
						shot(ctx, "dim_" + name + "_" + dirs[d]);
					}
					server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 -35");
					ctx.waitTicks(15);
					shot(ctx, "dim_" + name + "_sky");
					// survival HUD (meters build up over a few seconds), Frayed Wayfinder in hand
					server.runCommand("gamemode survival @a");
					server.runCommand("item replace entity @a hotbar.0 with elsewhere:frayed_wayfinder");
					ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
					server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 45 10");
					ctx.waitTicks(100);
					shot(ctx, "hud_" + name);
					if (on("hudtoggle")) {
						clientCmd(ctx, "elsewhere_hud all always");
						ctx.waitTicks(40);
						shot(ctx, "hud_" + name + "_always");
						hudAllHidden(ctx, true);
						ctx.waitTicks(50);
						shot(ctx, "hud_" + name + "_allhidden");
						hudAllHidden(ctx, false);
						clientCmd(ctx, "elsewhere_hud all auto");
						ctx.waitTicks(30);
					}
					server.runCommand("gamemode creative @a");
					server.computeOnServer(s -> {
						ServerPlayer p = s.getPlayerList().getPlayers().get(0);
						try {
							Class<?> rt = Class.forName("dev.elsewhere.core.travel.RiftTravel");
							rt.getMethod("travelNow", ServerPlayer.class, ResourceKey.class).invoke(null, p, null);
						} catch (ReflectiveOperationException e) {
							p.teleportTo(s.overworld(), 0.5, 100, 0.5, java.util.Set.of(), 0, 0, true);
						}
						return true;
					});
					ctx.waitTicks(10);
				}
			}

			// 5. Every boss mid-fight on a stone platform far out in the Overworld (two frames).
			List<Identifier> bossIds = server.computeOnServer(s -> {
				List<Identifier> l = new ArrayList<>();
				for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
					if (id.getNamespace().equals(NS) && BOSSES.contains(id.getPath())) {
						l.add(id);
					}
				}
				l.sort((a, b) -> a.toString().compareTo(b.toString()));
				return l;
			});
			server.runCommand("gamemode survival @a");
			server.runCommand("clear @a");
			if (on("bosses")) {
				int i = 0;
				for (Identifier bid : bossIds) {
					int bx = 20000 + i * 200, bz = 20000;
					i++;
					double dist = server.computeOnServer(s -> {
						ServerLevel lvl = s.overworld();
						platform(lvl, bx, 120, bz, 20);
						EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(bid);
						Entity e = type.create(lvl, EntitySpawnReason.COMMAND);
						if (e == null) {
							return -1.0;
						}
						e.snapTo(bx + 0.5, 120, bz + 0.5, 180.0F, 0.0F);
						if (e instanceof Mob mob) {
							try {
								mob.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(new BlockPos(bx, 120, bz)), EntitySpawnReason.COMMAND, null);
							} catch (Throwable ignored) {
								// plain spawn still counts
							}
						}
						lvl.addFreshEntity(e);
						double d = Math.max(7.0, e.getBbWidth() * 1.6 + e.getBbHeight() * 1.1 + 3);
						ServerPlayer p = s.getPlayerList().getPlayers().get(0);
						p.teleportTo(lvl, bx + 0.5, 120, bz + 0.5 - d, java.util.Set.of(), 0.0F, 0.0F, true);
						if (e instanceof LivingEntity le) {
							le.hurtServer(lvl, lvl.damageSources().playerAttack(p), 1.0F);
							if (le instanceof Mob mob) {
								mob.setTarget(p);
							}
						}
						return d;
					});
					if (dist < 0) {
						LOG.info("[qa-client] boss {} could not be created", bid);
						continue;
					}
					ctx.waitTicks(10);
					sp.getConnection().waitForChunksRender();
					ctx.getInput().lookAt(new BlockPos(bx, 121, bz));
					ctx.waitTicks(30);
					shot(ctx, "boss_" + bid.getPath());
					logRenderer(ctx, bid, "boss");
					lookAtBoss(ctx, server, bid);
					ctx.waitTicks(50);
					shot(ctx, "boss_" + bid.getPath() + "_b");
					server.runOnServer(s -> {
						ServerLevel lvl = s.overworld();
						for (Entity e : lvl.getEntitiesOfClass(Entity.class, new AABB(new BlockPos(bx, 120, bz)).inflate(96), e -> !(e instanceof ServerPlayer))) {
							e.discard();
						}
					});
				}
			}
			// 6. Every boss in its real arena via the module dev command (as the player), mid-fight, then the view behind
			//    the player (arena seal walls).
			if (on("arenas")) {
				String[][] arenaCmds = {
					{"__core", "selenne", "mother_creak"},
					{"petalwind_glasswaste", "kazehana", "tempestra", "heliodor", "ankhara"},
					{"verdigris_ironpeak", "verdigrim", "galvanox", "thurgrim", "hrolf"},
					{"greenhaze_hush", "percolax", "grumblegut", "auris", "lithara"},
					{"overworld_bosses", "gulpmother", "strawgrim", "neferkhet", "rimeback", "vorlagh"},
					{"nether_end_bosses", "grunthar", "solaxis", "mournmaw", "ossyra", "aphelion"}};
				for (String[] mod : arenaCmds) {
					for (int k = 1; k < mod.length; k++) {
						String bossId = mod[k];
						String cmd = bossId.equals("selenne") ? "summon elsewhere:selenne ~ ~2 ~10"
							: mod[0].equals("__core") ? "elsewhere arena " + bossId : "elsewhere_dev_" + mod[0] + " boss " + bossId;
						String res0 = server.computeOnServer(s -> {
							ServerPlayer p = s.getPlayerList().getPlayers().get(0);
							try {
								var src = s.createCommandSourceStack().withEntity(p).withPosition(p.position()).withLevel(p.level()).withSuppressedOutput();
								var parse = s.getCommands().getDispatcher().parse(cmd, src);
								if (parse.getReader().canRead()) {
									return "unparsed";
								}
								int rc = s.getCommands().getDispatcher().execute(parse);
								EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(NS, bossId));
								Entity best = null;
								for (ServerLevel l : s.getAllLevels()) {
									for (Entity e : l.getEntities(type, e -> e.isAlive())) {
										if (best == null || e.tickCount < best.tickCount) {
											best = e;
										}
									}
								}
								if (best == null) {
									return "rc=" + rc + " no boss";
								}
								if (best.level() != p.level() || best.distanceTo(p) > 40) {
									p.teleportTo((ServerLevel)best.level(), best.getX(), best.getY(), best.getZ() - 10, java.util.Set.of(), 0, 0, true);
								}
								if (best instanceof LivingEntity le) {
									le.hurtServer((ServerLevel)best.level(), best.level().damageSources().playerAttack(p), 1.0F);
								}
								return "ok " + best.blockPosition().getX() + " " + best.blockPosition().getY() + " " + best.blockPosition().getZ() + " in "
									+ best.level().dimension().identifier() + " player " + p.blockPosition().toShortString();
							} catch (Throwable t) {
								return "threw " + t;
							}
						});
						String res = res0;
						LOG.info("[qa-client] arena {}: {}", bossId, res);
						if (res.contains("no boss")) {
							// some summons build the arena over several ticks and spawn the boss at the end
							for (int tries = 0; tries < 4 && !res.startsWith("ok"); tries++) {
								ctx.waitTicks(40);
								res = server.computeOnServer(s -> {
									ServerPlayer p = s.getPlayerList().getPlayers().get(0);
									EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(NS, bossId));
									for (ServerLevel l : s.getAllLevels()) {
										for (Entity e : l.getEntities(type, e -> e.isAlive())) {
											if (e.level() != p.level() || e.distanceTo(p) > 40) {
												p.teleportTo((ServerLevel)e.level(), e.getX(), e.getY(), e.getZ() - 10, java.util.Set.of(), 0, 0, true);
											}
											return "ok " + e.blockPosition().getX() + " " + e.blockPosition().getY() + " " + e.blockPosition().getZ() + " (late)";
										}
									}
									return "still no boss";
								});
							}
							LOG.info("[qa-client] arena {} after waiting: {}", bossId, res);
						}
						if (!res.startsWith("ok")) {
							continue;
						}
						Identifier bid = Identifier.fromNamespaceAndPath(NS, bossId);
						ctx.waitTicks(20);
						sp.getConnection().waitForChunksRender();
						lookAtBoss(ctx, server, bid);
						ctx.waitTicks(40);
						lookAtBoss(ctx, server, bid);
						ctx.waitTick();
						shot(ctx, "arena_" + bossId);
						ctx.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 180.0F));
						ctx.waitTicks(10);
						shot(ctx, "arena_" + bossId + "_behind");
						// close-up from 2.2 x its size, same height, straight on
						server.runOnServer(s -> {
							EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(bid);
							ServerPlayer p = s.getPlayerList().getPlayers().get(0);
							for (Entity e : ((ServerLevel)p.level()).getEntities(type, e -> e.isAlive())) {
								double d = Math.max(4.0, e.getBbWidth() * 1.4 + e.getBbHeight() * 0.9 + 1.5);
								p.teleportTo((ServerLevel)e.level(), e.getX(), e.getY(), e.getZ() - d, java.util.Set.of(), 0.0F, 0.0F, true);
								LOG.info("[qa-client] close-up {} at {} {} {} size {}x{} invisible={} from {}", bossId, (int)e.getX(), (int)e.getY(), (int)e.getZ(),
									e.getBbWidth(), e.getBbHeight(), e.isInvisible(), (int)d);
								break;
							}
						});
						ctx.waitTicks(5);
						lookAtBoss(ctx, server, bid);
						ctx.waitTicks(2);
						shot(ctx, "arena_" + bossId + "_close");

						server.runOnServer(s -> {
							EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(bid);
							for (ServerLevel l : s.getAllLevels()) {
								for (Entity e : l.getEntities(type, e -> true)) {
									e.discard();
								}
							}
							ServerPlayer p = s.getPlayerList().getPlayers().get(0);
							p.teleportTo(s.overworld(), 0.5, 120, 0.5, java.util.Set.of(), 0, 0, true);
						});
						ctx.waitTicks(10);
					}
				}
			}

			// 7. Every non-boss living Elsewhere entity, one at a time, NoAI, on a platform.
			if (on("mobs")) {
				List<Identifier> mobIds = server.computeOnServer(s -> {
					List<Identifier> l = new ArrayList<>();
					ServerLevel lvl = s.overworld();
					for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
						if (!id.getNamespace().equals(NS) || BOSSES.contains(id.getPath())) {
							continue;
						}
						try {
							Entity e = BuiltInRegistries.ENTITY_TYPE.getValue(id).create(lvl, EntitySpawnReason.COMMAND);
							if (e instanceof LivingEntity) {
								l.add(id);
							}
							if (e != null) {
								e.discard();
							}
						} catch (Throwable t) {
							LOG.info("[qa-client] entity {} threw on create: {}", id, t.toString());
						}
					}
					l.sort((a, b) -> a.toString().compareTo(b.toString()));
					return l;
				});
				LOG.info("[qa-client] {} non-boss living elsewhere entity types: {}", mobIds.size(), mobIds);
				int bx = 30000, bz = 30000;
				server.runOnServer(s -> platform(s.overworld(), bx, 120, bz, 12));
				for (Identifier mid : mobIds) {
					double dist = server.computeOnServer(s -> {
						ServerLevel lvl = s.overworld();
						Entity e = BuiltInRegistries.ENTITY_TYPE.getValue(mid).create(lvl, EntitySpawnReason.COMMAND);
						if (e == null) {
							return -1.0;
						}
						e.snapTo(bx + 0.5, 120, bz + 0.5, 180.0F, 0.0F);
						if (e instanceof Mob mob) {
							try {
								mob.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(new BlockPos(bx, 120, bz)), EntitySpawnReason.COMMAND, null);
							} catch (Throwable ignored) {
								// fine
							}
							mob.setNoAi(true);
						}
						e.setNoGravity(false);
						lvl.addFreshEntity(e);
						double d = Math.max(3.5, e.getBbWidth() * 1.6 + e.getBbHeight() * 1.1 + 1.5);
						ServerPlayer p = s.getPlayerList().getPlayers().get(0);
						p.teleportTo(lvl, bx + 0.5, 120, bz + 0.5 - d, java.util.Set.of(), 0.0F, 0.0F, true);
						return d;
					});
					if (dist < 0) {
						continue;
					}
					ctx.waitTicks(8);
					lookAtBoss(ctx, server, mid);
					ctx.waitTicks(4);
					shot(ctx, "mob_" + mid.getPath());
					logRenderer(ctx, mid, "mob");
					server.runOnServer(s -> {
						for (Entity e : s.overworld().getEntitiesOfClass(Entity.class, new AABB(new BlockPos(bx, 120, bz)).inflate(40), e -> !(e instanceof ServerPlayer))) {
							e.discard();
						}
					});
				}
			}

			// 7b. Beta 2: Glimmer bar modes, every caster and summoner item used for real, /elsewhere trips off.
			if (on("glimmer")) {
				glimmer(ctx, sp, server);
			}

			// 7c. Targeted re-check: Crag Roc / Tidewing Ray visible, Grunthar bars gone after removal, a minion summon + Crook order.
			if (on("recheck")) {
				recheck(ctx, sp, server);
			}

			// 7d. 0.4.0-beta targeted checks (V04Checks): crook enchantments, Pale Hollow extras, HUD at GUI scale 2/3, Heat toggles,
			//     Ankhara poses, Nether/End arenas from world generation + their real summons, Wayfinder home from inside a dimension.
			if (on("crook")) {
				V04Checks.crook(server);
			}
			if (on("palehollow")) {
				V04Checks.paleHollow(ctx, sp, server, n -> shot(ctx, n));
			}
			if (on("hudscale")) {
				V04Checks.hudScale(ctx, sp, server, n -> shot(ctx, n));
			}
			if (on("heat")) {
				V04Checks.heat(ctx, sp, server, n -> shot(ctx, n));
			}
			if (on("ankhara")) {
				V04Checks.ankhara(ctx, sp, server, n -> shot(ctx, n));
			}
			if (on("arenagen")) {
				V04Checks.arenaGen(ctx, sp, server, n -> shot(ctx, n), Long.getLong("elsewhereqa.netherBudgetMs", 100000L),
					Long.getLong("elsewhereqa.endBudgetMs", 70000L));
			}
			if (on("wayhome")) {
				V04Checks.wayHome(ctx, sp, server, n -> shot(ctx, n));
			}

			// 8. A real Wayfinder trip by right-click: rift visual, arrival, use again to go home.
			if (on("wayfinder")) {
				server.runOnServer(s -> {
					ServerLevel lvl = s.overworld();
					platform(lvl, 0, 120, 40, 8);
					ServerPlayer p = s.getPlayerList().getPlayers().get(0);
					p.teleportTo(lvl, 0.5, 120, 40.5, java.util.Set.of(), 0.0F, 10.0F, true);
				});
				server.runCommand("gamemode survival @a");
				server.runCommand("item replace entity @a hotbar.0 with elsewhere:wayfinder_pale_hollow");
				ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
				ctx.waitTicks(20);
				ctx.getInput().pressKey(o -> o.keyUse);
				ctx.waitTicks(15);
				shot(ctx, "trip_rift_opening");
				ctx.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 180.0F));
				ctx.waitTicks(10);
				shot(ctx, "trip_rift_opening_behind");
				ctx.waitTicks(80);
				sp.getConnection().waitForChunksRender();
				String dimNow = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).level().dimension().identifier().toString());
				LOG.info("[qa-client] after right-click Wayfinder trip: player in {}", dimNow);
				shot(ctx, "trip_arrival");
				int cd = ctx.waitFor(mc -> !mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem()), 1400);
				LOG.info("[qa-client] Wayfinder cooldown after arrival: {} ticks", cd);
				ctx.getInput().pressKey(o -> o.keyUse);
				ctx.waitTicks(15);
				shot(ctx, "trip_home_rift");
				ctx.waitTicks(80);
				sp.getConnection().waitForChunksRender();
				String dimHome = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).level().dimension().identifier().toString());
				LOG.info("[qa-client] after using the Wayfinder again: player in {}", dimHome);
				LOG.info("[qa-client] V04 {} wayfinder home from the Pale Hollow (real trip, second right-click): went {} -> {}",
					dimNow.contains("pale_hollow") && dimHome.equals("minecraft:overworld") ? "PASS" : "FAIL", dimNow, dimHome);
				shot(ctx, "trip_home");
			}
			LOG.info("[qa-client] done: {} dims, {} bosses, {} tabs", dims.size(), bossIds.size(), tabCount);
		}
	}

	private void glimmer(final ClientGameTestContext ctx, final TestSingleplayerContext sp, final TestServerContext server) {
		int bx = 0, bz = 80;
		server.runOnServer(s -> {
			ServerLevel lvl = s.overworld();
			platform(lvl, bx, 120, bz, 14);
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			p.teleportTo(lvl, bx + 0.5, 120, bz - 6.5, java.util.Set.of(), 0.0F, 10.0F, true);
		});
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("time set 6000");
		java.util.function.Consumer<Integer> dummies = n -> server.runOnServer(s -> {
			ServerLevel lvl = s.overworld();
			for (Entity e : lvl.getEntitiesOfClass(Entity.class, new AABB(new BlockPos(bx, 120, bz)).inflate(30), e -> !(e instanceof ServerPlayer))) {
				e.discard();
			}
			for (int k = 0; k < n; k++) {
				Mob z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(lvl, EntitySpawnReason.COMMAND);
				z.snapTo(bx - 3 + k * 3 + 0.5, 120, bz + 4.5, 180.0F, 0.0F);
				z.setNoAi(true);
				z.setPersistenceRequired();
				z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400);
				z.setHealth(400);
				lvl.addFreshEntity(z);
			}
		});
		dummies.accept(3);
		ctx.waitTicks(10);
		ctx.getInput().lookAt(new BlockPos(bx, 121, bz + 4));
		// Glimmer bar: Auto while holding a caster, Auto idle (fades), Always idle, Hidden while holding
		String caster = "elsewhere:amethyst_wand";
		server.runCommand("item replace entity @a hotbar.0 with " + caster);
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		clientCmd(ctx, "elsewhere_hud glimmer auto");
		ctx.waitTicks(20);
		shot(ctx, "glimmer_auto_holding");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(4));
		ctx.waitTicks(200);
		shot(ctx, "glimmer_auto_idle");
		clientCmd(ctx, "elsewhere_hud glimmer always");
		ctx.waitTicks(10);
		shot(ctx, "glimmer_always_idle");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		clientCmd(ctx, "elsewhere_hud glimmer hidden");
		ctx.waitTicks(10);
		shot(ctx, "glimmer_hidden_holding");
		clientCmd(ctx, "elsewhere_hud glimmer auto");
		LOG.info("[qa-client] glimmer mode after commands: {}", hudMode(ctx, "glimmer"));
		// every caster / summoner item, used for real at the dummies
		List<String> use = server.computeOnServer(s -> {
			List<String> l = new ArrayList<>();
			for (String id : List.of("amethyst_wand", "sunlance_staff", "sculk_resonator", "glimmerstone_focus", "stormcoil_staff",
				"rootcall_staff", "kitestring_staff", "brassback_key", "herding_crook", "bell_of_the_flock", "glimmer_tonic", "trail_biscuit")) {
				if (BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath(NS, id))) {
					l.add(id);
				} else {
					LOG.info("[qa-client] caster/summoner item elsewhere:{} is not registered", id);
				}
			}
			return l;
		});
		for (String id : use) {
			server.runCommand("item replace entity @a hotbar.0 with elsewhere:" + id);
			server.runCommand("effect give @a elsewhere:clarity 1 0 true");
			ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
			ctx.getInput().lookAt(new BlockPos(bx, 121, bz + 4));
			ctx.waitTicks(25);
			float before = ctx.computeOnClient(mc -> glimmerNow());
			ctx.getInput().holdKeyFor(o -> o.keyUse, 30);
			ctx.waitTicks(3);
			shot(ctx, "cast_" + id);
			ctx.waitTicks(30);
			float after = ctx.computeOnClient(mc -> glimmerNow());
			String minions = server.computeOnServer(s -> {
				StringBuilder b = new StringBuilder();
				for (Entity e : s.overworld().getEntitiesOfClass(Entity.class, new AABB(new BlockPos(bx, 120, bz)).inflate(30),
					e -> !(e instanceof ServerPlayer) && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(NS))) {
					b.append(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()).append(' ');
				}
				return b.toString().trim();
			});
			LOG.info("[qa-client] used elsewhere:{}: glimmer {} -> {}; elsewhere entities near: [{}]", id, before, after, minions);
			shot(ctx, "cast_" + id + "_after");
			if (!minions.isEmpty()) {
				Identifier first = Identifier.fromNamespaceAndPath(NS, minions.split(" ")[0]);
				logRenderer(ctx, first, "minion");
			}
			dummies.accept(3);
			server.runCommand("effect give @a elsewhere:clarity 1 0 true");
		}
		// /elsewhere trips off as the player (any player may run it), with chat visible
		ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.FULL));
		clientCmd(ctx, "elsewhere trips off");
		ctx.waitTicks(10);
		shot(ctx, "trips_off_chat");
		clientCmd(ctx, "elsewhere trips");
		ctx.waitTicks(10);
		shot(ctx, "trips_status_chat");
		clientCmd(ctx, "elsewhere trips on");
		ctx.waitTicks(5);
		String tripsCheck = server.computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			var src = p.createCommandSourceStack().withPermission(net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS).withSuppressedOutput();
			StringBuilder b = new StringBuilder();
			try {
				Method en = Class.forName("dev.elsewhere.beta.GlobalTrips").getMethod("playerEnabled", ServerPlayer.class);
				var parse = s.getCommands().getDispatcher().parse("elsewhere trips off", src);
				b.append("non-op parse ok=").append(!parse.getReader().canRead() && parse.getExceptions().isEmpty());
				int rc = s.getCommands().getDispatcher().execute(parse);
				b.append(" rc=").append(rc).append(" enabled after off=").append(en.invoke(null, p));
				s.getCommands().getDispatcher().execute("elsewhere trips on", src);
				b.append(" after on=").append(en.invoke(null, p));
			} catch (Throwable t) {
				b.append(" threw ").append(t);
			}
			return b.toString();
		});
		LOG.info("[qa-client] /elsewhere trips as a non-op player: {}", tripsCheck);
		ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
		server.runOnServer(s -> {
			for (Entity e : s.overworld().getEntitiesOfClass(Entity.class, new AABB(new BlockPos(bx, 120, bz)).inflate(40), e -> !(e instanceof ServerPlayer))) {
				e.discard();
			}
		});
	}

	private int bossBars(final ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			try {
				Object ov = mc.gui.hud.getBossOverlay();
				java.lang.reflect.Field f = ov.getClass().getDeclaredField("events");
				f.setAccessible(true);
				return ((java.util.Map<?, ?>)f.get(ov)).size();
			} catch (Throwable t) {
				return -1;
			}
		});
	}

	private void recheck(final ClientGameTestContext ctx, final TestSingleplayerContext sp, final TestServerContext server) {
		int bx = 40000, bz = 40000;
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runOnServer(s -> platform(s.overworld(), bx, 120, bz, 16));
		// 1. fliers
		for (String id : List.of("crag_roc", "tidewing_ray")) {
			Identifier mid = Identifier.fromNamespaceAndPath(NS, id);
			server.runOnServer(s -> {
				ServerLevel lvl = s.overworld();
				Entity e = BuiltInRegistries.ENTITY_TYPE.getValue(mid).create(lvl, EntitySpawnReason.COMMAND);
				e.snapTo(bx + 0.5, 121, bz + 0.5, 180.0F, 0.0F);
				if (e instanceof Mob mob) {
					mob.setNoAi(true);
				}
				e.setNoGravity(true);
				lvl.addFreshEntity(e);
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				p.teleportTo(lvl, bx + 0.5, 120, bz + 0.5 - 6, java.util.Set.of(), 0.0F, 0.0F, true);
			});
			ctx.waitTicks(15);
			lookAtBoss(ctx, server, mid);
			ctx.waitTicks(3);
			shot(ctx, "recheck_" + id);
			logRenderer(ctx, mid, "recheck");
			server.runOnServer(s -> {
				for (Entity e : s.overworld().getEntitiesOfClass(Entity.class, new AABB(new BlockPos(bx, 120, bz)).inflate(30), e -> !(e instanceof ServerPlayer))) {
					e.discard();
				}
			});
			ctx.waitTicks(3);
		}
		// 2. Grunthar: bars while alive, gone after it is removed without dying
		int before = bossBars(ctx);
		Identifier gid = Identifier.fromNamespaceAndPath(NS, "grunthar");
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			var src = s.createCommandSourceStack().withEntity(p).withPosition(p.position()).withLevel(p.level()).withSuppressedOutput();
			s.getCommands().performPrefixedCommand(src, "elsewhere_dev_nether_end_bosses boss grunthar");
		});
		ctx.waitTicks(40);
		server.runOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			for (ServerLevel l : s.getAllLevels()) {
				for (Entity e : l.getEntities(BuiltInRegistries.ENTITY_TYPE.getValue(gid), e -> e.isAlive())) {
					if (e.level() != p.level() || e.distanceTo(p) > 30) {
						p.teleportTo((ServerLevel)e.level(), e.getX(), e.getY(), e.getZ() - 8, java.util.Set.of(), 0, 0, true);
					}
					((LivingEntity)e).hurtServer((ServerLevel)e.level(), e.level().damageSources().playerAttack(p), 1.0F);
				}
			}
		});
		ctx.waitTicks(40);
		int during = bossBars(ctx);
		shot(ctx, "recheck_grunthar_alive");
		server.runOnServer(s -> {
			for (ServerLevel l : s.getAllLevels()) {
				for (Entity e : l.getEntities(BuiltInRegistries.ENTITY_TYPE.getValue(gid), e -> true)) {
					e.discard();
				}
				for (Entity e : l.getEntities(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(NS, "ashtusk")), e -> true)) {
					e.discard();
				}
			}
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			p.teleportTo(s.overworld(), bx + 0.5, 120, bz + 0.5, java.util.Set.of(), 0, 0, true);
		});
		ctx.waitTicks(40);
		int after = bossBars(ctx);
		shot(ctx, "recheck_grunthar_removed");
		LOG.info("[qa-client] RECHECK boss bars: before {}, Grunthar alive {}, 2 s after removal {} -> {}", before, during, after,
			after <= before ? "PASS" : "FAIL (bars left on screen)");
		// 3. minion summon + Crook order
		server.runOnServer(s -> {
			ServerLevel lvl = s.overworld();
			Mob z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(lvl, EntitySpawnReason.COMMAND);
			z.snapTo(bx + 0.5, 120, bz + 2.5, 180.0F, 0.0F);
			z.setNoAi(true);
			z.setPersistenceRequired();
			lvl.addFreshEntity(z);
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			p.teleportTo(lvl, bx + 0.5, 120, bz + 0.5, java.util.Set.of(), 0.0F, 0.0F, true);
		});
		server.runCommand("time set 18000");
		server.runCommand("item replace entity @a hotbar.0 with elsewhere:rootcall_staff");
		server.runCommand("item replace entity @a hotbar.1 with elsewhere:herding_crook");
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(10);
		ctx.getInput().lookAt(new BlockPos(bx, 120, bz + 3));
		ctx.getInput().holdKeyFor(o -> o.keyUse, 30);
		ctx.waitTicks(20);
		String minions = server.computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			for (Entity e : s.overworld().getEntitiesOfClass(Entity.class, new AABB(new BlockPos(bx, 120, bz)).inflate(30),
				e -> !(e instanceof ServerPlayer) && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(NS))) {
				b.append(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()).append(' ');
			}
			return b.toString().trim();
		});
		shot(ctx, "recheck_minion_summoned");
		// Crook: look at the zombie and use the crook
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
		ctx.waitTicks(5);
		lookAtBoss(ctx, server, Identifier.withDefaultNamespace("zombie"));
		ctx.waitTicks(2);
		ctx.getInput().pressKey(o -> o.keyAttack);
		ctx.waitTicks(40);
		String targets = server.computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			for (Entity e : s.overworld().getEntitiesOfClass(Mob.class, new AABB(new BlockPos(bx, 120, bz)).inflate(30),
				e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals(NS))) {
				Mob m = (Mob)e;
				b.append(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()).append("->")
					.append(m.getTarget() == null ? "none" : BuiltInRegistries.ENTITY_TYPE.getKey(m.getTarget().getType()).getPath()).append(' ');
			}
			var zs = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class, new AABB(new BlockPos(bx, 120, bz)).inflate(30));
			b.append("| zombie hp ").append(zs.isEmpty() ? "dead/none" : String.valueOf(zs.get(0).getHealth()));
			return b.toString();
		});
		shot(ctx, "recheck_crook_order");
		LOG.info("[qa-client] RECHECK minion summon: [{}] -> {}; after Crook order: {}", minions, minions.isEmpty() ? "FAIL" : "PASS", targets);
		server.runCommand("time set 6000");
		server.runOnServer(s -> {
			for (Entity e : s.overworld().getEntitiesOfClass(Entity.class, new AABB(new BlockPos(bx, 120, bz)).inflate(40), e -> !(e instanceof ServerPlayer))) {
				e.discard();
			}
		});
		server.runCommand("clear @a");
	}

	/** Client mirror of the player's Glimmer (reflection; -1 if arsenal isn't loaded). */
	private static float glimmerNow() {
		try {
			Class<?> c = Class.forName("dev.elsewhere.arsenal.glimmer.Glimmer$ClientMirror");
			return ((Number)c.getField("current").get(null)).floatValue();
		} catch (Throwable t) {
			return -1F;
		}
	}

	static String hudMode(final ClientGameTestContext ctx, final String meter) {
		return ctx.computeOnClient(mc -> {
			try {
				Class<?> c = Class.forName("dev.elsewhere.hud.ElsewhereHud");
				return String.valueOf(c.getMethod("mode", String.class).invoke(null, meter));
			} catch (Throwable t) {
				return "n/a (" + t + ")";
			}
		});
	}

	static void hudAllHidden(final ClientGameTestContext ctx, final boolean v) {
		ctx.runOnClient(mc -> {
			try {
				Class.forName("dev.elsewhere.hud.ElsewhereHud").getMethod("setAllHidden", boolean.class).invoke(null, v);
			} catch (Throwable t) {
				LOG.info("[qa-client] ElsewhereHud.setAllHidden unavailable: {}", t.toString());
			}
		});
	}

	/** Sends a command the way the chat box does (client commands are intercepted by Fabric). */
	static void clientCmd(final ClientGameTestContext ctx, final String cmd) {
		ctx.runOnClient(mc -> mc.player.connection.sendCommand(cmd));
		ctx.waitTicks(2);
		LOG.info("[qa-client] sent /{}", cmd);
	}

	/** Logs the client renderer and model class for the nearest client entity of this type (custom model or vanilla?). */
	private static void logRenderer(final ClientGameTestContext ctx, final Identifier id, final String what) {
		String r = ctx.computeOnClient(mc -> {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
			Entity best = null;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e.getType() == type && (best == null || e.distanceTo(mc.player) < best.distanceTo(mc.player))) {
					best = e;
				}
			}
			if (best == null) {
				return "no client entity";
			}
			String where = String.format(Locale.ROOT, "dist %.1f dy %.1f invisible=%s ", best.distanceTo(mc.player), best.getY() - mc.player.getY(), best.isInvisible());
			var ren = mc.getEntityRenderDispatcher().getRenderer(best);
			String model = ren instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?> lr ? lr.getModel().getClass().getName() : "-";
			return where + ren.getClass().getName() + " model " + model;
		});
		LOG.info("[qa-client] renderer {} {}: {}", what, id.getPath(), r);
	}

	private static void platform(final ServerLevel lvl, final int bx, final int y, final int bz, final int r) {
		lvl.getChunk(bx >> 4, bz >> 4);
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				lvl.setBlock(new BlockPos(bx + dx, y - 1, bz + dz), (dx + dz) % 2 == 0 ? Blocks.STONE.defaultBlockState()
					: Blocks.ANDESITE.defaultBlockState(), 2);
				for (int dy = 0; dy < 12; dy++) {
					lvl.setBlock(new BlockPos(bx + dx, y + dy, bz + dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	/** Point the client camera at the newest live entity of this type (its body centre). */
	private static void lookAtBoss(final ClientGameTestContext ctx, final TestServerContext server, final Identifier id) {
		double[] c = server.computeOnServer(s -> {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			Entity best = null;
			for (Entity e : ((ServerLevel)p.level()).getEntities(type, e -> e.isAlive())) {
				if (best == null || e.distanceTo(p) < best.distanceTo(p)) {
					best = e;
				}
			}
			if (best == null) {
				return null;
			}
			return new double[] {best.getX(), best.getY() + best.getBbHeight() * 0.5, best.getZ()};
		});
		if (c == null) {
			return;
		}
		ctx.runOnClient(mc -> {
			var eye = mc.player.getEyePosition();
			double dx = c[0] - eye.x, dy = c[1] - eye.y, dz = c[2] - eye.z;
			double h = Math.sqrt(dx * dx + dz * dz);
			float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			float pitch = (float)(-Math.toDegrees(Math.atan2(dy, h)));
			mc.player.setYRot(yaw);
			mc.player.setXRot(pitch);
			mc.player.yRotO = yaw;
			mc.player.xRotO = pitch;
		});
	}

	private void shot(final ClientGameTestContext ctx, final String name) {
		ctx.runOnClient(mc -> {
			mc.gui.toastManager().clear();
			if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
				mc.gui.hud.getChat().clearMessages(false);
			}
		});
		ctx.waitTick();
		Path file = ctx.takeScreenshot(TestScreenshotOptions.of("qa_" + name).withDestinationDir(out).disableCounterPrefix());
		LOG.info("[qa-client] screenshot {}", file);
	}
}
