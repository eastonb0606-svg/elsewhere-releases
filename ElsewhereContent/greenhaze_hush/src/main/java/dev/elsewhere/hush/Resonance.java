package dev.elsewhere.hush;

import com.mojang.serialization.Codec;
import dev.elsewhere.greenhaze_hush.Reg;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * The Resonance meter (DIMENSIONS.md 6), 0-100%, synced to its owner for the HUD waveform bar.
 * Sprint +1/s, jump +2, mining +3/block, hit +4, eat +5, chest +6, Green Leaf cough +25. It decays 2/s while sneaking
 * and 4/s on wool. At 50% Echo Stalkers home in; at 100% "The Hush answers": a Warden digs out beside you (Auris near the Engine).
 * Gear multiplies noise (Hushcloak x0.5, full Hushweave x0.25, Muted x0); Silent Monasteries (wool floor under a roof) and a
 * nearby Ossuary Chorister's hymn stop it rising.
 */
public final class Resonance {
	private Resonance() {}

	public static final AttachmentType<Float> RESONANCE = AttachmentRegistry.create(Reg.id("resonance"),
		b -> b.persistent(Codec.FLOAT).initializer(() -> 0F).syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

	private static final Map<UUID, Integer> LAST_JUMPS = new HashMap<>();
	private static final Map<UUID, Long> LAST_EAT = new HashMap<>();
	private static final Map<UUID, Long> ANSWER_COOLDOWN = new HashMap<>();
	/** Hook for Auris: return true if it answered this player (near the Listening Engine). */
	public static java.util.function.Predicate<ServerPlayer> engineAnswer = p -> false;
	public static int wardensSummoned;
	private static int tick;

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Resonance::tick);
	}

	public static float get(ServerPlayer p) {
		return p.getAttachedOrElse(RESONANCE, 0F);
	}

	public static void set(ServerPlayer p, float v) {
		p.setAttached(RESONANCE, Math.max(0F, Math.min(100F, v)));
	}

	/** Add noise-driven resonance (gear, Muted, monastery and chorister rules apply). */
	public static void add(ServerPlayer p, float amount) {
		if (amount <= 0 || !Hush.isHush(p.level())) return;
		double f = HushItems.noiseFactor(p);
		if (f <= 0 || inMonastery(p) || HushMobs.OssuaryChorister.masks(p)) return;
		if (p.isShiftKeyDown() && HushItems.unheard(p)) return; // Unheard: Resonance doesn't rise while sneaking
		set(p, get(p) + (float) (amount * f * HushSilence.factor(p.level())));
		check(p);
	}

	/** Add resonance regardless of gear (Sonar Horn, shrieks, killing a Chorister). */
	public static void addRaw(ServerPlayer p, float amount) {
		if (!Hush.isHush(p.level())) return;
		set(p, get(p) + amount);
		check(p);
	}

	static void onEvent(ServerPlayer p, Holder<GameEvent> e) {
		if (e.is(GameEvent.BLOCK_DESTROY)) add(p, GearStats.RES_MINE);
		else if (e.is(GameEvent.CONTAINER_OPEN)) add(p, GearStats.RES_CHEST);
		else if (e.is(GameEvent.EAT) || e.is(GameEvent.DRINK)) {
			long now = p.level().getGameTime();
			Long last = LAST_EAT.get(p.getUUID());
			if (last == null || now - last > 40) { LAST_EAT.put(p.getUUID(), now); add(p, GearStats.RES_EAT); }
		}
	}

	/** Hit +4: called from the attack hook. */
	public static void onHit(ServerPlayer p) {
		if (HushItems.isEchoTool(p.getMainHandItem())) return; // Echo tools: hits and mining make no vibrations or Resonance
		add(p, GearStats.RES_HIT);
	}

	/** Green Leaf cough +25 (a smoke use in the Hush coughs with Green Leaf's typical chance). */
	public static void onGreenLeafUse(ServerPlayer p) {
		if (Hush.isHush(p.level()) && p.getRandom().nextDouble() < GearStats.GREEN_LEAF_COUGH_CHANCE) {
			p.level().playSound(null, p.blockPosition(), SoundEvents.PANDA_SNEEZE, SoundSource.PLAYERS, 1.2F, 0.7F);
			add(p, GearStats.RES_COUGH);
			HushNoise.post(p.level(), p.position(), p, 3);
		}
	}

	public static boolean onWool(ServerPlayer p) {
		BlockState below = p.level().getBlockState(p.blockPosition().below());
		BlockState at = p.level().getBlockState(p.blockPosition());
		return below.is(BlockTags.WOOL) || at.is(BlockTags.WOOL_CARPETS) || below.is(HushBlocks.HUSHWEAVE) || at.is(HushBlocks.HUSHWEAVE_CARPET);
	}

	/** Silent Monastery: wool underfoot and a roof within 6 blocks. */
	public static boolean inMonastery(ServerPlayer p) {
		if (!onWool(p)) return false;
		BlockPos c = p.blockPosition();
		for (int dy = 2; dy <= 6; dy++) if (!p.level().getBlockState(c.above(dy)).isAir()) return true;
		return false;
	}

	private static void tick(MinecraftServer server) {
		tick++;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Math.floorMod(tick + p.getId(), 20) != 0) continue;
			int jumps = p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));
			Integer lastJumps = LAST_JUMPS.put(p.getUUID(), jumps);
			float r = get(p);
			if (!Hush.isHush(p.level())) {
				if (r > 0) set(p, r - 5F);
				continue;
			}
			if (p.isSpectator() || p.isCreative()) continue;
			if (lastJumps != null && jumps > lastJumps) add(p, GearStats.RES_JUMP * Math.min(5, jumps - lastJumps));
			if (p.isSprinting()) add(p, GearStats.RES_SPRINT_PER_S);
			r = get(p);
			if (onWool(p)) set(p, r - GearStats.RES_WOOL_DECAY_PER_S);
			else if (p.isShiftKeyDown()) set(p, r - GearStats.RES_SNEAK_DECAY_PER_S);
		}
	}

	private static void check(ServerPlayer p) {
		if (get(p) >= GearStats.RES_ANSWER) answer(p);
	}

	/** "The Hush answers": a Warden digs out beside you (or Auris, near the Listening Engine). */
	public static void answer(ServerPlayer p) {
		ServerLevel level = p.level();
		long now = level.getGameTime();
		Long cd = ANSWER_COOLDOWN.get(p.getUUID());
		if (cd != null && now - cd < 1200) return;
		ANSWER_COOLDOWN.put(p.getUUID(), now);
		set(p, 60F);
		if (engineAnswer.test(p)) return;
		p.sendSystemMessage(Component.literal("The Hush answers.").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
		SpawnUtil.trySpawnMob(EntityTypes.WARDEN, EntitySpawnReason.TRIGGERED, level, p.blockPosition(), 20, 5, 6,
			SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, false).ifPresent(w -> {
				wardensSummoned++;
				w.increaseAngerAt(p, 80, false);
			});
	}

	public static void resetCooldown(ServerPlayer p) {
		ANSWER_COOLDOWN.remove(p.getUUID());
	}

	/** fix-stability: per-player state dies with the player's session, and every map with the world. */
	public static void forgetPlayer(java.util.UUID id) {
		LAST_JUMPS.remove(id);
		LAST_EAT.remove(id);
		ANSWER_COOLDOWN.remove(id);
	}

	public static void clearAll() {
		LAST_JUMPS.clear();
		LAST_EAT.clear();
		ANSWER_COOLDOWN.clear();
	}

	public static int trackedPlayers() { return LAST_JUMPS.size() + LAST_EAT.size() + ANSWER_COOLDOWN.size(); }
}
