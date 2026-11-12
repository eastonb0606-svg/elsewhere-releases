package dev.elsewhere.kingdom.idle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.armies.routine.RoutinePoses;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Mob;

/**
 * 3.0 polish P9 (s-p9-idle): soldier idle and work poses and serf work poses, server side.
 * <p>The routine engine ({@link RoutinePoses}) decides <i>when</i> a soldier sits at the fire, salutes, sweeps, carries,
 * kneels, drinks and so on: it calls {@link RoutinePoses#set} when the soldier arrives at its activity spot and again with
 * {@code NONE} when the activity ends or the squad fights. This class is the one registered sink: it copies the pose id into
 * one synced byte on the soldier ({@link KingdomSoldier#setIdlePose}), which {@code SynchedEntityData} sends only when the value
 * changes. Nothing here ticks; the client animates the byte from game time ({@code client.idle.IdleAnim}). Serfs set their own
 * work byte from their work goal on arrival/departure ({@link #SERF_CARRY}, {@link #SERF_WORK}).
 * <p>Any other mob (e.g. townsfolk) gets the same client animations by implementing {@link Posable} and using
 * {@code client.idle.PosedAvatarState} plus {@code IdleAnim.extract} in its renderer.
 * <p><b>Kill switch</b> {@code "idle_poses": false} in {@code config/elsewhere_armies.json} (or {@code -Delsewhere.armies.idle_poses=false}):
 * the sink declines, so the routine's vanilla fallback (crouch / one arm swing) applies, and serfs go back to their old arm swing.
 */
public final class IdlePoses {
	private IdlePoses() {}

	/** Serf work poses (kingdom_villages {@code Serf}); outside the routine ids 0-12 so the two never collide. */
	public static final int SERF_CARRY = 20, SERF_WORK = 21;

	/** A mob other than a soldier that shows routine poses (one synced byte, set on change only). */
	public interface Posable {
		int idlePose();

		void setIdlePose(int pose);
	}

	public static boolean enabled = true;
	/** Counters for the self-test: pose bytes changed by the sink, poses restored from the saved tag on load. */
	public static long synced, restored;
	private static boolean done;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static void init() {
		if (done) return;
		done = true;
		load();
		RoutinePoses.register(IdlePoses::sink);
		// the routine keeps the pose as an entity tag (it survives restarts); the synced byte does not, so a soldier that was
		// sitting at the fire when the chunk was saved sits again when it loads (an event, once per load)
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (enabled && e instanceof Mob m && (m instanceof KingdomSoldier || m instanceof Posable)) restore(m);
		});
		KingdomContent.LOG.info("P9 idle poses {}", enabled ? "on" : "off (config/elsewhere_armies.json idle_poses)");
	}

	/** The routine sink: true when the mob was handled (the routine then skips its vanilla fallback). */
	public static boolean sink(Mob mob, RoutinePoses.Pose pose) {
		if (!enabled) return false;
		if (mob instanceof KingdomSoldier s) {
			if (s.idlePose() != pose.id) {
				s.setIdlePose(pose.id);
				synced++;
			}
			return true;
		}
		if (mob instanceof Posable p) {
			if (p.idlePose() != pose.id) {
				p.setIdlePose(pose.id);
				synced++;
			}
			return true;
		}
		return false;
	}

	/** Copies the routine's saved pose tag back into the synced byte (on entity load). */
	public static void restore(Mob mob) {
		RoutinePoses.Pose p = RoutinePoses.get(mob);
		if (p == RoutinePoses.Pose.NONE) return;
		if (mob instanceof KingdomSoldier s && s.idlePose() != p.id) {
			s.setIdlePose(p.id);
			restored++;
		} else if (mob instanceof Posable q && q.idlePose() != p.id) {
			q.setIdlePose(p.id);
			restored++;
		}
	}

	static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("elsewhere_armies.json");
	}

	/** Reads {@code idle_poses} (default true) and adds the key to the armies config file when it is missing. */
	static void load() {
		Path p = path();
		try {
			JsonObject o = Files.exists(p) ? GSON.fromJson(Files.readString(p), JsonObject.class) : null;
			if (o == null) o = new JsonObject();
			if (o.has("idle_poses")) enabled = o.get("idle_poses").getAsBoolean();
			else {
				o.addProperty("idle_poses", true);
				Files.createDirectories(p.getParent());
				Files.writeString(p, GSON.toJson(o));
			}
		} catch (Exception e) {
			KingdomContent.LOG.warn("elsewhere_armies.json unreadable for idle_poses ({}); idle poses stay {}", e.toString(), enabled ? "on" : "off");
		}
		if (System.getProperty("elsewhere.armies.idle_poses") != null) enabled = Boolean.getBoolean("elsewhere.armies.idle_poses");
	}
}
