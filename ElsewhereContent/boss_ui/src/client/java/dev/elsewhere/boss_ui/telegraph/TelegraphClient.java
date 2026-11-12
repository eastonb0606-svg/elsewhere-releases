package dev.elsewhere.boss_ui.telegraph;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elsewhere.boss.kit.TelegraphNet;
import dev.elsewhere.hud.ElsewhereHud;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the P2 telegraphs: receives {@link TelegraphNet.Shape} payloads into a fixed 8-slot
 * {@link TelegraphStore} and draws every visible shape in ONE translucent quad batch per frame (vanilla
 * {@code debug_quads}: position + colour, no texture, no depth write). Culled by distance (LOD 24/48/64 blocks) and by
 * the camera frustum. Nothing is allocated per frame: the slots, the bounds box and the geometry renderer are reused.
 * Player switch: {@code /elsewhere_hud telegraphs auto|always|hidden} (Hidden turns them off); reduce motion stops the pulse.
 */
public final class TelegraphClient {
	private TelegraphClient() {}

	public static final String METER = "telegraphs";
	public static final TelegraphStore STORE = new TelegraphStore();
	private static ClientLevel storeLevel;
	private static boolean initDone;

	/** Per-frame inputs for the shared geometry renderer (set right before submitting; read at flush the same frame). */
	private static final int[] LODS = new int[TelegraphStore.CAP];
	private static double camX, camY, camZ, now;
	private static boolean motion;
	private static final MutableBox BOX = new MutableBox();

	private static VertexConsumer target;
	private static PoseStack.Pose targetPose;
	private static final TelegraphMesh.Sink SINK = (x, y, z, argb) -> target.addVertex(targetPose, x, y, z).setColor(argb);
	private static final SubmitNodeCollector.CustomGeometryRenderer RENDERER = (pose, consumer) -> {
		target = consumer;
		targetPose = pose;
		TelegraphStore.Slot[] slots = STORE.slots;
		for (int i = 0; i < slots.length; i++) {
			if (LODS[i] >= 0) TelegraphMesh.emit(slots[i], camX, camY, camZ, now, LODS[i], motion, SINK);
		}
		target = null;
		targetPose = null;
	};

	public static void init() {
		if (initDone) return;
		initDone = true;
		TelegraphNet.init();
		ElsewhereHud.registerMeter(METER, METER);
		ClientPlayNetworking.registerGlobalReceiver(TelegraphNet.Shape.TYPE, (payload, context) -> receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			STORE.clear();
			storeLevel = null;
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(TelegraphClient::submit);
	}

	private static void receive(TelegraphNet.Shape s) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) return;
		if (level != storeLevel) {
			STORE.clear();
			storeLevel = level;
		}
		STORE.accept(s, level.getGameTime());
	}

	private static void submit(LevelRenderContext ctx) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || level != storeLevel || STORE.count() == 0) return;
		if (ElsewhereHud.mode(METER) == ElsewhereHud.Mode.HIDDEN) return;
		long gameTime = level.getGameTime();
		STORE.expire(gameTime);
		CameraRenderState cam = ctx.levelState().cameraRenderState;
		Vec3 pos = cam.pos;
		Frustum frustum = cam.cullFrustum;
		boolean any = false;
		TelegraphStore.Slot[] slots = STORE.slots;
		for (int i = 0; i < slots.length; i++) {
			TelegraphStore.Slot s = slots[i];
			int lod = s.used ? TelegraphMesh.lod(s.distSqr(pos.x, pos.y, pos.z)) : -1;
			if (lod >= 0 && frustum != null && !frustum.isVisible(BOX.of(s))) lod = -1;
			LODS[i] = lod;
			any |= lod >= 0;
		}
		if (!any) return;
		camX = pos.x;
		camY = pos.y;
		camZ = pos.z;
		now = gameTime + ctx.levelState().worldPartialTicks;
		motion = !ElsewhereHud.reduceMotion();
		ctx.submitNodeCollector().submitCustomGeometry(ctx.poseStack(), RenderTypes.debugQuads(), RENDERER);
	}

	/** One reusable AABB-shaped box for the frustum test (AABB is immutable, so it is rebuilt only when the slot changes). */
	private static final class MutableBox {
		private final AABB[] cache = new AABB[TelegraphStore.CAP];
		private final TelegraphStore.Slot[] owner = new TelegraphStore.Slot[TelegraphStore.CAP];
		private final long[] stamp = new long[TelegraphStore.CAP];

		AABB of(TelegraphStore.Slot s) {
			int i = indexOf(s);
			long st = s.start * 31 + s.id;
			if (cache[i] == null || owner[i] != s || stamp[i] != st) {
				cache[i] = new AABB(s.minX, s.minY, s.minZ, s.maxX, s.maxY, s.maxZ);
				owner[i] = s;
				stamp[i] = st;
			}
			return cache[i];
		}

		private static int indexOf(TelegraphStore.Slot s) {
			TelegraphStore.Slot[] slots = STORE.slots;
			for (int i = 0; i < slots.length; i++) if (slots[i] == s) return i;
			return 0;
		}
	}
}
