package dev.elsewhere.kingdom.client.lod;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.armies.lod.RenderLod;
import dev.elsewhere.kingdom.client.KingdomClientConfig;
import dev.elsewhere.kingdom.client.KingdomSoldierRenderer;
import dev.elsewhere.kingdom.client.NpcSkin;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/**
 * Distance tiers for rank-and-file soldiers (ARMIES.md 3.7), applied while the soldier's render state is extracted (once per frame per
 * visible soldier; nothing runs per tick). The rules and numbers are {@link RenderLod}'s:
 * <ul>
 * <li>FULL (24 blocks or less, and among the nearest 24/48/72 by soldier detail): everything.</li>
 * <li>NEAR (24-48, or close but past the full-detail cap): armour trims dropped (one pass less per trimmed piece), cape static.</li>
 * <li>FAR (48-96): the baked LOD skin ({@link LodSkins}) replaces skin, armour, trims and tabard in one texture; no cape; only banners
 * and spears stay in hand. A soldier whose look doesn't match a baked skin keeps its armour (no trims) and tabard instead.</li>
 * <li>BEYOND (past 96 x the entity-distance slider): not drawn ({@link #culled}); the server shows battles that far as rank impostors.</li>
 * </ul>
 * Only plain {@link KingdomSoldierRenderer.SoldierRenderState}s take part: renderers with their own state subclass (court rulers, unique
 * NPCs) keep the 2.1 detail rules. Kill switch: {@link RenderLod#enabled()}.
 */
public final class SoldierLod {
	private SoldierLod() {}

	static final RenderLod.Budget BUDGET = new RenderLod.Budget();
	/** Entity id -> frame << 8 | drawnFull << 3 | (distance tier + 1). */
	private static final Int2LongOpenHashMap LAST = new Int2LongOpenHashMap();
	private static long frame;
	private static final int[] counting = new int[4];
	/** Soldiers drawn per tier in the last finished frame [FULL, NEAR, FAR, BEYOND-culled], plus FAR ones without a baked skin. */
	public static final int[] lastFrame = new int[4];
	public static int lastFarFallback, lastCulled;
	private static int farFallback, culled;

	/**
	 * Applies the tier to a soldier's render state; call after the vanilla extraction. Returns false when the LOD is off or the state opts
	 * out, and the caller then applies the 2.1 "soldier detail" rules instead.
	 */
	public static boolean apply(KingdomSoldier e, AvatarRenderState state) {
		if (!RenderLod.enabled() || state.getClass() != KingdomSoldierRenderer.SoldierRenderState.class) return false;
		KingdomSoldierRenderer.SoldierRenderState s = (KingdomSoldierRenderer.SoldierRenderState) state;
		double d2 = state.distanceToCameraSq;
		int id = e.getId();
		long memo = LAST.getOrDefault(id, 0L);
		int prev = (int) (memo & 7L) - 1;
		boolean wasFull = (memo & 8L) != 0;
		int distTier = RenderLod.tier(d2, prev);
		int tier = distTier;
		if (tier == RenderLod.FULL) {
			BUDGET.offer((float) d2);
			if (!BUDGET.grant((float) d2, wasFull)) tier = RenderLod.NEAR;
		}
		if (tier == RenderLod.BEYOND) tier = RenderLod.FAR; // drawn this frame (the cull check runs first); treat as far
		LAST.put(id, frame << 8 | (tier == RenderLod.FULL ? 8L : 0L) | (distTier + 1));
		s.lodTier = tier;
		counting[tier]++;
		switch (tier) {
			case RenderLod.FULL -> {
				// everything, as at 2.1 High
				s.showTabard = true;
				overlay(state, true);
			}
			case RenderLod.NEAR -> {
				s.showTabard = true;
				overlay(state, true);
				stripTrims(state);
				state.capeFlap = state.capeLean = state.capeLean2 = 0.0F; // static cape
			}
			default -> far(e, s);
		}
		return true;
	}

	private static void far(KingdomSoldier e, KingdomSoldierRenderer.SoldierRenderState s) {
		Identifier baked = LodSkins.baked(s.texture, s.tabardColor, s.headEquipment, s.chestEquipment, s.legsEquipment, s.feetEquipment);
		s.showCape = false;
		if (baked != null) {
			NpcSkin.apply(s, baked);
			s.headEquipment = s.chestEquipment = s.legsEquipment = s.feetEquipment = ItemStack.EMPTY;
			s.showTabard = false; // painted into the baked skin
			overlay(s, true); // the baked armour lives on the outer layer
		} else {
			farFallback++;
			stripTrims(s);
			s.showTabard = true;
			overlay(s, false);
		}
		// held items: only banners and spears (identity and silhouette); arm poses stay, so archers still draw and shields still rise
		if (!keepInHand(s.rightHandItemStack)) s.rightHandItemState.clear();
		if (!keepInHand(s.leftHandItemStack)) s.leftHandItemState.clear();
		if (!s.headItem.isEmpty() && !e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(ItemTags.BANNERS)) s.headItem.clear();
	}

	static boolean keepInHand(ItemStack st) {
		return !st.isEmpty() && (st.is(ItemTags.BANNERS) || st.is(ItemTags.SPEARS));
	}

	private static void overlay(AvatarRenderState s, boolean on) {
		s.showHat = s.showJacket = s.showLeftPants = s.showRightPants = s.showLeftSleeve = s.showRightSleeve = on;
	}

	/** The state's equipment stacks are per-frame copies (vanilla copies them in extraction), so removing the trim here is safe. */
	private static void stripTrims(AvatarRenderState s) {
		if (s.headEquipment.has(DataComponents.TRIM)) s.headEquipment.remove(DataComponents.TRIM);
		if (s.chestEquipment.has(DataComponents.TRIM)) s.chestEquipment.remove(DataComponents.TRIM);
		if (s.legsEquipment.has(DataComponents.TRIM)) s.legsEquipment.remove(DataComponents.TRIM);
		if (s.feetEquipment.has(DataComponents.TRIM)) s.feetEquipment.remove(DataComponents.TRIM);
	}

	/**
	 * Culling for the soldier renderer's {@code shouldRender}: true (do not draw) for a rank-and-file soldier past 96 blocks (times the
	 * entity-distance slider). Never true for opted-out renderers or with the LOD off.
	 */
	public static boolean culled(Object renderer, KingdomSoldier e, double camX, double camY, double camZ) {
		if (!RenderLod.enabled() || renderer.getClass() != KingdomSoldierRenderer.class) return false;
		double lim = RenderLod.drawDistance(Minecraft.getInstance().options.entityDistanceScaling().get());
		if (e.distanceToSqr(camX, camY, camZ) <= lim * lim) return false;
		culled++;
		return true;
	}

	/** End of the level extraction (once per frame): next frame's full-detail threshold, stats, and a memo sweep every ~600 frames. */
	static void endFrame() {
		BUDGET.endFrame(RenderLod.fullDetailCap(KingdomClientConfig.detail.ordinal()));
		System.arraycopy(counting, 0, lastFrame, 0, 4);
		java.util.Arrays.fill(counting, 0);
		lastFrame[RenderLod.BEYOND] = lastCulled = culled;
		lastFarFallback = farFallback;
		culled = farFallback = 0;
		frame++;
		if ((frame & 511) == 0 && !LAST.isEmpty()) {
			long stale = frame - 512;
			LAST.int2LongEntrySet().removeIf((Int2LongMap.Entry en) -> (en.getLongValue() >>> 8) < stale);
		}
	}

	/** Level change: forget per-entity memos (entity ids are reused). */
	static void reset() {
		LAST.clear();
	}
}
