package dev.elsewhere.kingdom.client;

import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.plate.Nameplate;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 3.0 polish P3: rank and faction nameplates over kingdom soldiers and court rulers (text and icons:
 * {@link Nameplate}). Runs at render-state extraction only, for soldiers the frustum already kept (in view); nothing per
 * tick and no packets: the rank byte, faction id and ruler name arrive as synced entity data on spawn or change.
 * <ul>
 * <li>Range and LOD: full plate within 8 blocks (rulers 16), icons only (rank mark and crest) from 8 to 16, nothing beyond.</li>
 * <li>One depth-tested text draw per plate ({@code isDiscrete}): hidden behind walls and other mobs, never drawn through them.</li>
 * <li>Hidden while the soldier crouches (resting at night, sneaking), is invisible or dead, while the HUD is hidden (F1),
 * and for a soldier a player has named (vanilla's tag stays).</li>
 * <li>Built plates are cached per faction (soldiers) and per name (rulers), so a frame allocates nothing for them.</li>
 * </ul>
 * Kill switch: {@code "polishNameplates": false} in config/elsewhere-client.json (re-read on joining a world).
 */
public final class SoldierNameplates {
	private SoldierNameplates() {}

	/** Kept for callers of the 3.0 polish2 version: the outer range. */
	public static final double RANGE = Nameplate.RANGE;
	private static Boolean enabled;
	private static boolean hooked;
	/** Set by GUI portraits (kingdom_court's trader dialogue) around their entity draw: no plate, no vanilla tag. */
	public static boolean suppress;

	/** Cached soldier plates per synced faction id: index rank * 3 + lod. Rebuilt when the tint changes. */
	private static final Map<String, Plates> SOLDIER = new HashMap<>();
	/** Cached ruler plates keyed by the synced custom-name instance (stable until the name changes). */
	private static final Map<Component, RulerPlate> RULER = new IdentityHashMap<>();

	private static final class Plates {
		final int tint;
		final Component[] byRankLod = new Component[(Nameplate.CAPTAIN + 1) * 3 * 2]; // x2: day and night variants

		Plates(int tint) {
			this.tint = tint;
		}
	}

	private record RulerPlate(String faction, int rank, int tint, boolean night, Component plate) {}

	static boolean enabled() {
		if (!hooked) {
			hooked = true;
			ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
				enabled = null;
				SOLDIER.clear();
				RULER.clear();
			});
		}
		if (enabled == null) {
			boolean on = true;
			try {
				var f = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-client.json");
				if (Files.exists(f)) {
					var o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
					if (o.has("polishNameplates")) on = o.get("polishNameplates").getAsBoolean();
				}
			} catch (Exception ignored) {}
			enabled = on;
		}
		return enabled;
	}

	/** Sets (or leaves alone) the name tag on a soldier's render state. Called from the soldier renderer's extract, after the tabard colour is set. */
	public static void apply(KingdomSoldier e, AvatarRenderState state) {
		if (!(state instanceof KingdomSoldierRenderer.SoldierRenderState s)) return;
		s.plate = Nameplate.NONE;
		if (suppress) {
			state.nameTag = null; // a GUI portrait (the trader dialogue) never carries a world nameplate
			return;
		}
		if (!enabled() || e.isInvisible() || !e.isAlive() || e.isCrouching() || e.isDiscrete()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden() || e == mc.getCameraEntity()) return;
		int rank = e.rank();
		Component name = null;
		if (rank >= Nameplate.LORD) name = e.getCustomName();
		else if (e.hasCustomName()) return; // a player-named soldier keeps vanilla's tag
		int lod = Nameplate.lod(rank, state.distanceToCameraSq);
		if (lod == Nameplate.NONE) return;
		// L257.1: hidden when occluded. Line of sight from the camera to the head or the body; a captain fully behind a pillar gets no plate at all.
		boolean headClear = clearTo(mc, e, e.getBbHeight() + 0.3);
		if (!headClear && !clearTo(mc, e, e.getBbHeight() * 0.5)) return;
		boolean night = mc.level != null && mc.level.isDarkOutside();
		String fid = e.syncedFaction();
		if (fid.isEmpty()) return;
		Component plate = rank >= Nameplate.LORD ? rulerPlate(rank, fid, s.tabardColor, name, night) : soldierPlate(rank, fid, s.tabardColor, lod, night);
		state.nameTag = plate;
		Vec3 a = e.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, e.getYRot());
		Vec3 att = a != null ? a : new Vec3(0.0, e.getBbHeight() + 0.5, 0.0);
		state.nameTagAttachment = att.add(0.0, declutter(mc, e, att, plate), 0.0);
		// Vanilla's discrete draw is one depth-tested pass at half alpha (the faint plate the QA judges flagged). A soldier with a clear line of sight
		// from the camera gets the normal full-alpha draw instead; a plate behind a wall or a block stays discrete so it never shows through.
		state.isDiscrete = !(lod == Nameplate.FULL && headClear);
		s.plate = (byte) lod;
	}

	/** True when no block lies between the camera and the point {@code dy} above the soldier's feet. */
	private static boolean clearTo(Minecraft mc, KingdomSoldier e, double dy) {
		if (mc.level == null || mc.gameRenderer == null) return false;
		Vec3 from = mc.gameRenderer.mainCamera().position();
		Vec3 to = e.position().add(0.0, dy, 0.0);
		return mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.MISS;
	}

	// ---- screen-space declutter (R-labels: plates must never draw over each other) -------------------------------------

	/** Plates placed so far this frame, in view space: u (right), v (up) per unit depth, half-width and half-height likewise. */
	private static final java.util.ArrayList<double[]> PLACED = new java.util.ArrayList<>();
	private static final java.util.ArrayList<Integer> PLACED_IDS = new java.util.ArrayList<>();
	/** Text height of a plate in blocks (9 px at vanilla's 0.025 scale) plus a gap. */
	private static final double PLATE_H = 0.25, STEP = 0.27;

	/**
	 * Returns the extra height (blocks) this plate is lifted so it overlaps no plate placed earlier this frame. Plates are compared as
	 * they project on screen (lateral and vertical offset divided by depth, sizes likewise), so it holds at 6, 12 and 18 blocks; the
	 * later plate stacks upward. A new frame starts when an entity is extracted a second time.
	 */
	private static double declutter(Minecraft mc, KingdomSoldier e, Vec3 attach, Component plate) {
		Entity cam = mc.getCameraEntity();
		if (cam == null) return 0.0;
		Integer id = e.getId();
		if (PLACED_IDS.contains(id)) {
			PLACED.clear();
			PLACED_IDS.clear();
		}
		PLACED_IDS.add(id);
		Vec3 from = mc.gameRenderer.mainCamera().position();
		double yaw = Math.toRadians(cam.getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -fz, rz = fx; // forward and right on the horizontal plane
		double relX = e.getX() + attach.x - from.x, relY = e.getY() + attach.y - from.y, relZ = e.getZ() + attach.z - from.z;
		double depth = relX * fx + relZ * fz;
		if (depth < 0.5) return 0.0;
		double u = (relX * rx + relZ * rz) / depth;
		double halfW = (mc.font.width(plate) + 4) * 0.025 * 0.5 / depth;
		double halfH = PLATE_H * 0.5 / depth;
		double lift = 0.0;
		for (int pass = 0; pass < 6; pass++) {
			double v = (relY + lift) / depth;
			boolean hit = false;
			for (double[] p : PLACED) {
				if (Math.abs(u - p[0]) < halfW + p[2] && Math.abs(v - p[1]) < halfH + p[3]) {
					hit = true;
					break;
				}
			}
			if (!hit) break;
			lift += STEP;
		}
		PLACED.add(new double[] {u, (relY + lift) / depth, halfW, halfH});
		return lift;
	}

	static Component soldierPlate(int rank, String fid, int argb, int lod, boolean night) {
		Plates p = SOLDIER.get(fid);
		if (p == null || p.tint != argb) {
			p = new Plates(argb);
			SOLDIER.put(fid, p);
		}
		int i = (Math.min(rank, Nameplate.CAPTAIN) * 3 + lod) * 2 + (night ? 1 : 0);
		Component c = p.byRankLod[i];
		if (c == null) c = p.byRankLod[i] = Nameplate.text(rank, fid, argb, null, lod, night);
		return c;
	}

	static Component rulerPlate(int rank, String fid, int argb, Component name, boolean night) {
		if (name == null) return soldierPlateless(rank, fid, argb);
		RulerPlate r = RULER.get(name);
		if (r == null || r.night() != night || r.rank() != rank || r.tint() != argb || !r.faction().equals(fid)) {
			if (RULER.size() > 64) RULER.clear(); // names change rarely; keep the map small
			r = new RulerPlate(fid, rank, argb, night, Nameplate.text(rank, fid, argb, name, Nameplate.FULL, night));
			RULER.put(name, r);
		}
		return r.plate();
	}

	/** A ruler without a synced name (should not happen; the court always names them): the kingdom's own title. */
	private static Component soldierPlateless(int rank, String fid, int argb) {
		return Nameplate.text(rank, fid, argb, null, Nameplate.FULL);
	}

	/** Plate text for the client gametest/screenshots and the old polish2 callers. */
	public static Component text(int rank, String factionId, int argb) {
		return Nameplate.text(rank, factionId, argb, null, Nameplate.FULL);
	}
}
