package dev.elsewhere.kingdom.armies.impostor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.FactionDefs;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomLod;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.armies.ArmiesConfig;
import dev.elsewhere.kingdom.armies.ArmyScale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rank impostors (ARMIES.md 3.5 / 3.6): the back ranks of big armies drawn by one cheap entity each ({@link RankEntity},
 * {@code elsewhere:army_rank}), so a 150-a-side battle shows its whole host while the server ticks at most the live caps.
 * <ul>
 * <li><b>Reserve ranks:</b> a wave battle's reserve stands in up to {@link #PER_SIDE} ranks 14-22 blocks behind its front, marches up as the
 * front moves and refills from the reserve every 30 s. A wave breaks the rank nearest the front out, one soldier per tick, each at his
 * figure's spot with the unit and health the figure carried. Azmar's line relief walks wounded men back into a rank.</li>
 * <li><b>R3 crowd:</b> a battle squad more than 104 blocks from every player for 100 ticks folds into a rank (the squad's own virtual record
 * keeps UUIDs and health); folded ranks fight by dice every 20 ticks and unfold when a player comes within 80.</li>
 * <li><b>Never scenery:</b> a player's hit breaks the figure out into a real soldier who takes the hit.</li>
 * <li><b>Budgets:</b> {@link #PER_AREA} ranks per battle area, {@link #PER_SERVER} per server, at most 16 figures each; no chunk is ever
 * force-loaded (a rank reaching unloaded ground goes abstract).</li>
 * </ul>
 * Kill switch {@code "rank_impostors"} in {@code config/elsewhere_armies.json} (default on; system property
 * {@code elsewhere.armies.rank_impostors}); off, or the whole overhaul off, or another module's self-test running: no rank ever spawns and
 * wave reserves are plain numbers (3.0 without impostors). The entity type is registered either way so worlds and servers always agree.
 */
public final class Ranks {
	private Ranks() {}

	public static final int PER_SIDE = 6, PER_AREA = 12, PER_SERVER = 36;
	public static final double AREA = 64;
	public static EntityType<RankEntity> RANK;
	/** The kill switch after config, system property, the overhaul switch and the other-self-test rule. */
	public static boolean enabled;
	/** The raw {@code rank_impostors} key. */
	public static boolean configKey = true;
	static final Set<RankEntity> LIVE = new HashSet<>();
	/** Counters (self-test, {@code /kingdom armies ranks}). */
	public static long formed, brokeOut, hitBreakOuts, figureDeaths, abstracted, removed, folded, unfolded, refilled, reliefFolded, diceRolls, breakRefused;
	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		register();
		readConfig();
		enabled = configKey && ArmiesConfig.enabled && !ArmiesConfig.otherSelfTest();
		if (enabled) {
			dev.elsewhere.kingdom.armies.RankWaves.install();
			ServerLifecycleEvents.SERVER_STOPPING.register(srv -> LIVE.clear());
			commands();
			KingdomContent.LOG.info("Armies 3.0: rank impostors on (kill switch: rank_impostors)");
		} else {
			KingdomContent.LOG.info("Armies 3.0: rank impostors off (rank_impostors {}, overhaul {})", configKey, ArmiesConfig.enabled);
		}
		if (Boolean.getBoolean("elsewhere.armies.selftest") && !ArmiesConfig.otherSelfTest()) dev.elsewhere.kingdom.armies.ImpostorSelfTest.register();
	}

	@SuppressWarnings("unchecked")
	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, KingdomContent.id("army_rank"));
		if (BuiltInRegistries.ENTITY_TYPE.containsKey(key.identifier())) RANK = (EntityType<RankEntity>) BuiltInRegistries.ENTITY_TYPE.getValue(key.identifier());
		else RANK = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<RankEntity>of(RankEntity::new, MobCategory.MISC).sized(1.0F, 2.0F)
			.fireImmune().noSummon().noLootTable().clientTrackingRange(10).updateInterval(10).build(key));
	}

	static void readConfig() {
		Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere_armies.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			JsonObject o = Files.exists(p) ? gson.fromJson(Files.readString(p), JsonObject.class) : null;
			if (o == null) o = new JsonObject();
			if (o.has("rank_impostors")) configKey = o.get("rank_impostors").getAsBoolean();
			else {
				o.addProperty("rank_impostors", true);
				Files.createDirectories(p.getParent());
				Files.writeString(p, gson.toJson(o));
			}
		} catch (Exception e) {
			KingdomContent.LOG.warn("elsewhere_armies.json unreadable for rank_impostors ({}); keeping {}", e.toString(), configKey);
		}
		if (System.getProperty("elsewhere.armies.rank_impostors") != null) configKey = Boolean.getBoolean("elsewhere.armies.rank_impostors");
	}

	// ---- kingdom looks (from army.scale; Aldermark lines, Hrafnholt wall and wedge, Azmar blocks, Mirewick loose watches) ----------

	/** Figures in one full rank of this kingdom: its formation width x depth, at most 16 (Aldermark 10, Hrafnholt 16, Azmar 16, Mirewick 12). */
	public static int capacity(Identifier faction) {
		ArmyScale s = ArmyScale.of(faction);
		if (s == null) return 12;
		return Math.max(4, Math.min(RankLayout.MAX, s.formation().width() * Math.max(1, s.formation().depth())));
	}

	/** The shape a kingdom's ranks hold in: a square block when its formation is square (Azmar), else its battle line. */
	public static RankLayout.Shape holdShape(Identifier faction) {
		ArmyScale s = ArmyScale.of(faction);
		if (s == null) return RankLayout.Shape.BLOCK;
		return s.formation().width() == s.formation().depth() ? RankLayout.Shape.BLOCK : RankLayout.Shape.LINE;
	}

	/** The shape it marches in, by wave style: sergeant columns, the surge's boar's snout, the relief's unbroken block, the ragged line. */
	public static RankLayout.Shape marchShape(Identifier faction) {
		ArmyScale s = ArmyScale.of(faction);
		if (s == null) return RankLayout.Shape.COLUMN;
		return switch (s.wave().style()) {
			case "surge" -> RankLayout.Shape.WEDGE;
			case "relief" -> RankLayout.Shape.BLOCK;
			case "ragged" -> RankLayout.Shape.LINE;
			default -> RankLayout.Shape.COLUMN;
		};
	}

	/** Forms an empty rank of a kingdom at {@code pos} facing {@code yaw}; null when a budget is full or the spot is not loaded. */
	public static @Nullable RankEntity create(ServerLevel lv, Identifier faction, Vec3 pos, float yaw, RankEntity.Kind kind, @Nullable RankController c) {
		if (!enabled || !lv.isLoaded(BlockPos.containing(pos)) || !roomFor(lv, pos)) return null;
		RankEntity r = RANK.create(lv, EntitySpawnReason.EVENT);
		if (r == null) return null;
		r.faction = faction;
		r.kind = kind;
		r.controller = c;
		r.bornTick = lv.getGameTime();
		ArmyScale s = ArmyScale.of(faction);
		int width = s != null ? s.formation().width() : 4;
		float spacing = s != null ? (float) s.formation().spacing() : 1.25F;
		float jitter = s != null ? (float) s.formation().jitter() : 0F;
		r.setFormation(holdShape(faction), capacity(faction), width, spacing, jitter);
		int tab = Factions.get(faction).tabardColor;
		FactionDef def = FactionDefs.get(faction);
		r.setColours(tab, def != null ? def.tabard().trim() : tab);
		r.snapTo(pos.x, pos.y, pos.z, yaw, 0F);
		r.setYRot(yaw);
		if (!lv.addFreshEntity(r)) return null;
		LIVE.add(r);
		formed++;
		return r;
	}

	/** Budgets: at most {@link #PER_SERVER} ranks, {@link #PER_AREA} within {@link #AREA} blocks. */
	public static boolean roomFor(ServerLevel lv, Vec3 pos) {
		LIVE.removeIf(Entity::isRemoved);
		if (LIVE.size() >= PER_SERVER) return false;
		int near = 0;
		for (RankEntity r : LIVE) if (r.level() == lv && r.position().distanceToSqr(pos) < AREA * AREA) near++;
		return near < PER_AREA;
	}

	/** Live ranks (all levels). */
	public static List<RankEntity> live() {
		LIVE.removeIf(Entity::isRemoved);
		return new ArrayList<>(LIVE);
	}

	/** Figures shown by all live ranks. */
	public static int figuresShown() {
		int n = 0;
		for (RankEntity r : live()) n += r.count();
		return n;
	}

	// ---- break-out ---------------------------------------------------------------------------------------------------------------

	/** One queued figure of {@code r} becomes a real soldier (called every tick while its queue is not empty). */
	static void breakOutNext(ServerLevel lv, RankEntity r) {
		Integer slot = r.breaking.poll();
		if (slot == null) return;
		Figure f = r.figure(slot);
		if (f == null) {
			if (r.breaking.isEmpty() && r.state() == RankEntity.State.BREAK) r.setState(RankEntity.State.HOLD);
			return;
		}
		KingdomSoldier s = spawnFigure(lv, r, slot);
		if (s == null) {
			// the caps are full: every figure still queued stays in the rank and its head goes back to the reserve
			breakRefused++;
			if (r.controller != null) {
				r.controller.breakFailed(lv, r, f);
				for (Integer k : r.breaking) {
					Figure g = r.figure(k);
					if (g != null) r.controller.breakFailed(lv, r, g);
				}
			}
			r.cancelBreakOut();
			return;
		}
		r.removeFigure(slot);
		joinBreakSquad(lv, r, s);
		brokeOut++;
		if (r.controller != null) r.controller.brokeOut(lv, r, f, s, true);
		if (r.breaking.isEmpty() && r.state() == RankEntity.State.BREAK) r.setState(RankEntity.State.HOLD);
		if (r.count() == 0) r.discard();
	}

	/**
	 * Spawns the soldier a figure stands for at the figure's own spot (on the ground there, or at the rank when that spot is a cliff or not
	 * loaded), with its unit, faction and saved health, facing the rank's way. Every spawn cap still binds: null when refused.
	 */
	public static @Nullable KingdomSoldier spawnFigure(ServerLevel lv, RankEntity r, int slot) {
		Figure f = r.figure(slot);
		if (f == null) return null;
		float yaw = r.getYRot();
		Vec3 p = r.slotPos(slot, yaw);
		BlockPos col = BlockPos.containing(p);
		double y = r.getY();
		if (lv.isLoaded(col)) {
			int gy = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
			if (Math.abs(gy - r.getY()) <= 3 && lv.getFluidState(new BlockPos(col.getX(), gy - 1, col.getZ())).isEmpty()) y = gy;
			else p = r.position();
		} else {
			p = r.position();
		}
		KingdomSoldier s = KingdomApi.spawnSoldier(lv, f.unit, r.faction, new Vec3(p.x, y, p.z), null);
		if (s == null) return null;
		s.setHealth(Math.min(Math.max(1F, f.health), s.getMaxHealth()));
		s.setYRot(yaw);
		s.setYHeadRot(yaw);
		s.setYBodyRot(yaw);
		return s;
	}

	/** Puts a broken-out soldier into the rank's break-out squad (made with its first soldier, a new one when full). */
	static void joinBreakSquad(ServerLevel lv, RankEntity r, KingdomSoldier s) {
		KingdomState ks = KingdomState.get(lv);
		Squad q = r.breakSquad != null ? ks.squad(r.breakSquad) : null;
		if (q == null || !q.hasRoom()) {
			q = KingdomApi.createSquad(lv, r.faction, Squad.Formation.LINE);
			r.breakSquad = q.id();
			if (r.controller == null) q.dismiss(); // scenery after a battle: the soldier goes home when out of sight
		}
		if (!KingdomApi.join(q, s)) {
			q = KingdomApi.createSquad(lv, r.faction, Squad.Formation.LINE);
			r.breakSquad = q.id();
			if (r.controller == null) q.dismiss();
			KingdomApi.join(q, s);
		}
	}

	// ---- hits: a player's blade or arrow breaks the figure out; anything else wounds it ---------------------------------------------

	static boolean hurt(ServerLevel lv, RankEntity r, DamageSource src, float amount) {
		if (r.isRemoved() || r.presentMask() == 0 || amount <= 0) return false;
		Entity attacker = src.getEntity(), direct = src.getDirectEntity();
		boolean byPlayer = attacker instanceof Player p && !p.isSpectator() || attacker instanceof OwnableEntity o && o.getOwner() instanceof Player;
		int slot;
		if (direct != null && direct != attacker) slot = r.nearestSlot(direct.position(), 2.5); // an arrow or thrown thing: where it struck
		else if (attacker != null) {
			slot = r.slotOnRay(attacker.getEyePosition(), attacker.getViewVector(1F), 8, 1.0); // a blade: the figure under the crosshair
			if (slot < 0 && !byPlayer) slot = r.nearestSlot(attacker.position(), 4);
		} else {
			Vec3 sp = src.getSourcePosition();
			slot = sp != null ? r.nearestSlot(sp, 4) : -1;
		}
		if (slot < 0) return false; // between the figures: a miss
		Figure f = r.figure(slot);
		if (f == null) return false;
		if (byPlayer) {
			hitBreakOuts++;
			if (r.kind == RankEntity.Kind.FOLDED && r.controller != null) {
				UUID id = f.uuid;
				if (r.controller.unfold(lv, r)) {
					if (id != null && lv.getEntity(id) instanceof KingdomSoldier s) s.hurtServer(lv, src, amount);
					return true;
				}
			} else {
				boolean queued = r.breaking.remove(Integer.valueOf(slot));
				KingdomSoldier s = spawnFigure(lv, r, slot);
				if (s != null) {
					r.removeFigure(slot);
					joinBreakSquad(lv, r, s);
					brokeOut++;
					if (r.controller != null) r.controller.brokeOut(lv, r, f, s, queued);
					s.hurtServer(lv, src, amount); // he takes the hit (and turns on whoever struck it)
					if (r.count() == 0) r.discard();
					return true;
				}
				if (queued) r.breaking.addFirst(slot);
			}
			// no room for one more live soldier here: the figure takes the hit itself
		}
		wound(lv, r, slot, amount);
		return true;
	}

	/** Wounds a figure inside the rank; it dies at 0 (its head is lost). Fresh figures start from their unit's health. */
	public static void wound(ServerLevel lv, RankEntity r, int slot, float amount) {
		Figure f = r.figure(slot);
		if (f == null) return;
		if (f.health >= Figure.FULL) f.health = (float) Factions.unit(r.faction, f.unit).stats().health();
		f.health -= amount;
		if (f.health <= 0) killFigure(lv, r, slot);
		else if (r.controller != null) r.controller.figureWounded(lv, r, f);
	}

	/** A figure dies inside the rank (its head is lost). */
	public static void killFigure(ServerLevel lv, RankEntity r, int slot) {
		boolean queued = r.breaking.remove(Integer.valueOf(slot));
		Figure f = r.removeFigure(slot);
		if (f == null) return;
		figureDeaths++;
		if (r.controller != null) r.controller.figureDied(lv, r, f, queued);
		if (r.count() == 0 && r.reserved == 0) r.discard();
	}

	// ---- after a battle and at the edges ----------------------------------------------------------------------------------------------

	/** Lets a rank go: it walks to {@code to} (routing when beaten) and leaves when nobody watches; a hit still breaks a figure out. */
	public static void loosen(RankEntity r, Vec3 to, boolean rout) {
		r.controller = null;
		r.kind = RankEntity.Kind.LOOSE;
		r.squad = null;
		r.breakSquad = null;
		r.reserved = 0;
		r.cancelBreakOut();
		float yaw = (float) Math.toDegrees(Math.atan2(-(to.x - r.getX()), to.z - r.getZ()));
		r.pace = rout ? 0.17F : 0.12F;
		r.setShape(rout ? RankLayout.Shape.LINE : marchShape(r.faction));
		if (rout) r.setFormation(RankLayout.Shape.LINE, r.slots(), r.width(), r.spacing(), Math.max(r.jitter(), 0.9F)); // a beaten rank loses its order
		r.marchTo(to, yaw);
		r.setState(rout ? RankEntity.State.ROUT : RankEntity.State.MARCH);
		r.bornTick = r.level().getGameTime();
	}

	/** Loose ranks (every 20 ticks): gone once out of everyone's sight, or 60 s after the battle when nobody is close. */
	static void looseTick(ServerLevel lv, RankEntity r) {
		long age = lv.getGameTime() - r.bornTick;
		boolean seen = KingdomLod.observerNear(lv, r.getX(), r.getZ(), 160);
		if (!seen || age > 1200 && !KingdomLod.observerNear(lv, r.getX(), r.getZ(), 48) || r.goal == null && !KingdomLod.observerNear(lv, r.getX(), r.getZ(), 64)) r.discard();
	}

	/** A rank that reached unloaded ground (battles never force-load chunks): its heads stay with their owner, the entity goes. */
	static void abstractRank(ServerLevel lv, RankEntity r) {
		abstracted++;
		r.discard();
	}

	static void onRemoved(ServerLevel lv, RankEntity r) {
		LIVE.remove(r);
		removed++;
		if (r.controller != null) {
			RankController c = r.controller;
			r.controller = null;
			c.removed(lv, r);
		}
	}

	public static void resetCounters() {
		formed = brokeOut = hitBreakOuts = figureDeaths = abstracted = removed = folded = unfolded = refilled = reliefFolded = diceRolls = breakRefused = 0;
		RankEntity.tickNanos = RankEntity.ticks = 0;
	}

	static void commands() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("kingdom").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("armies").then(Commands.literal("ranks").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				List<RankEntity> l = live();
				int figs = 0;
				for (RankEntity r : l) figs += r.count();
				final int shown = figs;
				double us = RankEntity.ticks == 0 ? 0 : RankEntity.tickNanos / 1000.0 / RankEntity.ticks;
				c.getSource().sendSuccess(() -> Component.literal("Rank impostors " + (enabled ? "on" : "off") + ": " + l.size() + " ranks showing " + shown + " soldiers (budget "
					+ PER_AREA + " per battle area, " + PER_SERVER + " per server); " + String.format("%.1f", us) + " us per rank tick"), false);
				c.getSource().sendSuccess(() -> Component.literal("formed " + formed + ", refills " + refilled + ", broke out " + brokeOut + " (" + hitBreakOuts + " by hits, "
					+ breakRefused + " refused by caps), died in rank " + figureDeaths + ", relief folded " + reliefFolded + ", R3 folded " + folded + " / unfolded " + unfolded
					+ ", dice rounds " + diceRolls + ", went abstract " + abstracted), false);
				return Command.SINGLE_SUCCESS;
			})))));
	}
}
