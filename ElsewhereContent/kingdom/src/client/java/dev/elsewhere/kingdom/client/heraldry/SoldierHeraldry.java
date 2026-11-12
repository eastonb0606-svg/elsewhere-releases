package dev.elsewhere.kingdom.client.heraldry;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.client.AvatarLayers;
import dev.elsewhere.kingdom.client.KingdomClientConfig;
import dev.elsewhere.kingdom.client.KingdomSoldierRenderer;
import dev.elsewhere.kingdom.heraldry.Heraldry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 3.0 polish P10 client side: kingdom emblems on tabards and capes on officers and captains (see {@link Heraldry}).
 * {@link #init} (from {@code KingdomClient.initClient}) registers the two cape model layers and the cape layer;
 * {@link #extract} (from {@code KingdomSoldierRenderer.extractRenderState}) fills the render state. Everything is decided
 * at extraction from data the soldier already syncs (faction, rank): no packets, no ticking, no per-frame allocation
 * after a faction's first sighting.
 * <p>LOD: emblem with the tabard (soldier-detail distance) and within 64 blocks; cape within the tabard distance and 64
 * blocks, waving within 24, never on LOW detail. Kill switches: {@code "polishEmblems": false} / {@code "polishCapes": false}
 * in {@code config/elsewhere-client.json} (read once).
 */
public final class SoldierHeraldry {
	private SoldierHeraldry() {}

	private static boolean done, loaded, emblemsOn = true, capesOn = true;
	/** Faction id -> heraldry (null values cached too). Bounded by the number of factions seen. */
	private static final Map<String, Heraldry.Style> STYLES = new HashMap<>();
	private static final Map<Identifier, RenderType> CAPE_TYPES = new IdentityHashMap<>();

	public static void init() {
		if (done) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(SoldierCapeModel.WAVE, () -> SoldierCapeModel.create(Heraldry.CAPE_SEGMENTS));
		ModelLayerRegistry.registerModelLayer(SoldierCapeModel.FLAT, () -> SoldierCapeModel.create(1));
		AvatarLayers.add(SoldierCapeLayer::new);
	}

	private static void loadSwitches() {
		if (loaded) return;
		loaded = true;
		String text = null;
		try {
			Path f = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-client.json");
			if (Files.exists(f)) text = Files.readString(f);
		} catch (Exception ignored) {
		}
		boolean[] on = Heraldry.switches(text);
		emblemsOn = on[0];
		capesOn = on[1];
	}

	private static final Map<Identifier, Identifier> GREY = new IdentityHashMap<>();

	/** A remnant's faded heraldry: the desaturated twin of a kingdom texture ({@code <kingdom>_remnant_<rest>.png}); a plain multiply cannot grey a blue. */
	static Identifier grey(Identifier t) {
		Identifier g = GREY.get(t);
		if (g == null) {
			String p = t.getPath();
			int slash = p.lastIndexOf('/') + 1, us = p.indexOf('_', slash);
			g = us < 0 ? t : Identifier.fromNamespaceAndPath(t.getNamespace(), p.substring(0, us) + "_remnant" + p.substring(us));
			GREY.put(t, g);
		}
		return g;
	}

	static Heraldry.Style style(String faction) {
		Heraldry.Style st = STYLES.get(faction);
		if (st == null && !STYLES.containsKey(faction)) {
			if (STYLES.size() > 256) STYLES.clear();
			st = Heraldry.resolve(faction);
			STYLES.put(faction, st);
		}
		return st;
	}

	/** Cull-faced cutout for a cape texture (each face of the zero-depth strip shows from its own side only). */
	static RenderType capeType(Identifier texture) {
		RenderType t = CAPE_TYPES.get(texture);
		if (t == null) {
			t = RenderTypes.entityCutoutCull(texture);
			CAPE_TYPES.put(texture, t);
		}
		return t;
	}

	/**
	 * Sets the emblem, the cape and the cape's motion inputs on a soldier's render state, from {@code
	 * KingdomSoldierRenderer.extractRenderState}. Order-independent of the tabard LOD (2.1 soldier detail or the 3.0 army
	 * render LOD): both are drawn only while the final state shows the tabard ({@code showTabard}, checked at submit by
	 * {@code TabardLayer} and {@link SoldierCapeLayer}), and the cape also needs {@code showCape} (the army LOD clears it far
	 * away, where the baked skin replaces the tabard).
	 */
	public static void extract(KingdomSoldier e, KingdomSoldierRenderer.SoldierRenderState s, float partialTicks) {
		s.emblem = null;
		s.capeTexture = null;
		s.heraldryTint = Heraldry.NO_TINT;
		if (s.isInvisible) return;
		loadSwitches();
		Heraldry.Style st = style(e.syncedFaction());
		if (st == null) return;
		double d2 = s.distanceToCameraSq;
		s.heraldryTint = st.tint();
		if (emblemsOn && Heraldry.emblemVisible(d2)) s.emblem = st.remnant() ? grey(st.kingdom().emblem) : st.kingdom().emblem;
		// a subclassed state (the court's rulers) brings its own cape layer; only plain soldiers get this one
		if (!capesOn || s.getClass() != KingdomSoldierRenderer.SoldierRenderState.class) return;
		int lod = Heraldry.capeLod(d2, KingdomClientConfig.detail == KingdomClientConfig.Detail.LOW);
		Identifier cape = lod == Heraldry.CAPE_NONE ? null : st.cape(e.rank());
		if (cape == null) return;
		if (st.remnant()) cape = grey(cape);
		s.capeTexture = cape;
		s.capeWave = lod == Heraldry.CAPE_WAVE;
		// motion: smoothed walk speed and distance (the mount's when riding), direction from the last tick's movement
		Entity vehicle = e.getVehicle();
		Entity mover = vehicle != null ? vehicle : e;
		float speed = s.walkAnimationSpeed, walk = s.walkAnimationPos;
		if (vehicle instanceof LivingEntity mount) {
			speed = mount.walkAnimation.speed(partialTicks);
			walk = mount.walkAnimation.position(partialTicks);
		}
		double dx = mover.getX() - mover.xo, dz = mover.getZ() - mover.zo;
		double h = Math.sqrt(dx * dx + dz * dz);
		float forward = 1F, side = 0F;
		if (h > 1.0E-3) {
			float yaw = s.bodyRot * Mth.DEG_TO_RAD;
			double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
			forward = (float) ((dx * fx + dz * fz) / h);
			side = (float) ((dx * fz - dz * fx) / h);
		}
		s.capeSpeed = Mth.clamp(speed, 0F, 1F);
		s.capeWalk = walk;
		s.capeLean = Heraldry.lean(speed, forward);
		s.capeLean2 = Heraldry.sideLean(speed, side);
		s.capeFlap = Heraldry.flap((float) (mover.getY() - mover.yo));
		s.capePhase = Math.floorMod(e.getId() * 37, 101); // ticks: neighbours in a rank don't wave in lockstep
	}
}
