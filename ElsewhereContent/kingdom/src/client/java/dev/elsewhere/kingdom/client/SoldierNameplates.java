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
import net.minecraft.world.entity.EntityAttachment;
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

	/** Cached soldier plates per synced faction id: index rank * 3 + lod. Rebuilt when the tint changes. */
	private static final Map<String, Plates> SOLDIER = new HashMap<>();
	/** Cached ruler plates keyed by the synced custom-name instance (stable until the name changes). */
	private static final Map<Component, RulerPlate> RULER = new IdentityHashMap<>();

	private static final class Plates {
		final int tint;
		final Component[] byRankLod = new Component[(Nameplate.CAPTAIN + 1) * 3];

		Plates(int tint) {
			this.tint = tint;
		}
	}

	private record RulerPlate(String faction, int rank, int tint, Component plate) {}

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
		if (!enabled() || e.isInvisible() || !e.isAlive() || e.isCrouching() || e.isDiscrete()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden() || e == mc.getCameraEntity()) return;
		int rank = e.rank();
		Component name = null;
		if (rank >= Nameplate.LORD) name = e.getCustomName();
		else if (e.hasCustomName()) return; // a player-named soldier keeps vanilla's tag
		int lod = Nameplate.lod(rank, state.distanceToCameraSq);
		if (lod == Nameplate.NONE) return;
		String fid = e.syncedFaction();
		if (fid.isEmpty()) return;
		Component plate = rank >= Nameplate.LORD ? rulerPlate(rank, fid, s.tabardColor, name) : soldierPlate(rank, fid, s.tabardColor, lod);
		state.nameTag = plate;
		Vec3 a = e.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, e.getYRot());
		state.nameTagAttachment = a != null ? a : new Vec3(0.0, e.getBbHeight() + 0.5, 0.0);
		state.isDiscrete = true; // one NORMAL (depth-tested) draw instead of vanilla's see-through pair
		s.plate = (byte) lod;
	}

	static Component soldierPlate(int rank, String fid, int argb, int lod) {
		Plates p = SOLDIER.get(fid);
		if (p == null || p.tint != argb) {
			p = new Plates(argb);
			SOLDIER.put(fid, p);
		}
		int i = Math.min(rank, Nameplate.CAPTAIN) * 3 + lod;
		Component c = p.byRankLod[i];
		if (c == null) c = p.byRankLod[i] = Nameplate.text(rank, fid, argb, null, lod);
		return c;
	}

	static Component rulerPlate(int rank, String fid, int argb, Component name) {
		if (name == null) return soldierPlateless(rank, fid, argb);
		RulerPlate r = RULER.get(name);
		if (r == null || r.rank() != rank || r.tint() != argb || !r.faction().equals(fid)) {
			if (RULER.size() > 64) RULER.clear(); // names change rarely; keep the map small
			r = new RulerPlate(fid, rank, argb, Nameplate.text(rank, fid, argb, name, Nameplate.FULL));
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
