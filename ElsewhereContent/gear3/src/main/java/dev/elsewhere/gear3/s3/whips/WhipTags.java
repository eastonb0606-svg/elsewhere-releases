package dev.elsewhere.gear3.s3.whips;

import dev.elsewhere.summoner.GaleRibbonItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Whip tags (GEAR.md A5 rules for every whip): a whip hit marks one target; each owner marks one target at a time, and a
 * target carries ONE whip tag: the newest wins, whoever placed it and with whatever whip (the Gale Ribbon included, see
 * {@link #onRibbonTag}). A tag lasts its whip's time ({@link WhipKind#ticks}, Tidechain {@link WhipKind#wetTicks} on a wet
 * target) plus {@link #bonusTicks} (set bonuses such as the Marshal's Command), and like the Gale Ribbon's it fades
 * {@link #IDLE_TICKS} after the owner's last swing (a hit, or a crack with right-click).
 * <p>Nothing ticks: entries expire lazily when read, and the maps never hold more than one entry per owner (cleared on
 * disconnect, on the target's death and on server stop).
 */
public final class WhipTags {
	private WhipTags() {}

	/** Gale Ribbon rule: the tag fades 3 s after the last swing. */
	public static final int IDLE_TICKS = 60;

	/** One live tag. {@code idle} = how long after {@code lastSwing} it survives. */
	public record Tag(UUID owner, UUID target, WhipKind kind, long until, long lastSwing, int idle) {
		public boolean alive(long now) {
			return now <= until && now - lastSwing <= idle;
		}
	}

	private static final Map<UUID, Tag> BY_TARGET = new HashMap<>();
	private static final Map<UUID, UUID> BY_OWNER = new HashMap<>();
	private static final List<ToIntFunction<ServerPlayer>> BONUS = new CopyOnWriteArrayList<>();

	// ---------------------------------------------------------------- API for other lanes

	/**
	 * Extra tag ticks for an owner, asked once per tag (never per tick). Set bonuses register here, e.g. the Marshal's
	 * Command (+3 s = 60 ticks): {@code WhipTags.addBonusTicks(p -> wearsCommand(p) ? 60 : 0)}. Applies to every whip,
	 * the Gale Ribbon included.
	 */
	public static void addBonusTicks(ToIntFunction<ServerPlayer> f) {
		BONUS.add(f);
	}

	public static int bonusTicks(ServerPlayer owner) {
		int n = 0;
		for (ToIntFunction<ServerPlayer> f : BONUS) n += Math.max(0, f.applyAsInt(owner));
		return n;
	}

	/** Wet = in water, in rain, or standing in a water block (Tidechain Lash: Shackled 7 s). */
	public static boolean wet(LivingEntity e) {
		return e.isInWaterOrRain() || e.level().getFluidState(e.blockPosition()).is(FluidTags.WATER);
	}

	public static int tagTicks(WhipKind k, boolean wet, int bonus) {
		return (wet ? k.wetTicks : k.ticks) + bonus;
	}

	// ---------------------------------------------------------------- tagging

	/**
	 * Marks {@code target} for {@code owner} with {@code kind}. Returns true when the mark is new (another target or
	 * another whip), false when it only refreshed the same mark.
	 */
	public static boolean tag(ServerPlayer owner, LivingEntity target, WhipKind kind) {
		long now = owner.level().getGameTime();
		Tag prev = liveTag(owner, now);
		boolean fresh = prev == null || !prev.target().equals(target.getUUID()) || prev.kind() != kind;
		int bonus = bonusTicks(owner);
		removeOwner(owner.getUUID());
		GaleRibbonItem.clear(owner.getUUID()); // the owner's own Gale Ribbon tag: newest whip wins
		removeTarget(target.getUUID());
		clearRibbonsOn(owner.level().getServer(), target, owner);
		Tag t = new Tag(owner.getUUID(), target.getUUID(), kind, now + tagTicks(kind, wet(target), bonus), now, IDLE_TICKS + bonus);
		BY_TARGET.put(t.target(), t);
		BY_OWNER.put(t.owner(), t.target());
		return fresh;
	}

	/** A Gale Ribbon hit (GaleRibbonItem.tag, via WhipMixins): the owner's gear3 tag and any gear3 tag on that target go. */
	public static void onRibbonTag(ServerPlayer owner, LivingEntity target) {
		removeOwner(owner.getUUID());
		removeTarget(target.getUUID());
		clearRibbonsOn(owner.level().getServer(), target, owner);
	}

	/** Other players' Gale Ribbon tags on {@code target} (event-driven: once per whip hit, over the online players). */
	private static void clearRibbonsOn(@Nullable MinecraftServer server, Entity target, ServerPlayer except) {
		if (server == null) return;
		for (ServerPlayer o : server.getPlayerList().getPlayers()) {
			if (o != except && GaleRibbonItem.isTagged(o, target)) GaleRibbonItem.clear(o.getUUID());
		}
	}

	/** A crack (right-click): the owner's tag survives another {@link Tag#idle} ticks. False when there is no live tag. */
	public static boolean refresh(ServerPlayer owner) {
		long now = owner.level().getGameTime();
		Tag t = liveTag(owner, now);
		if (t == null) return false;
		BY_TARGET.put(t.target(), new Tag(t.owner(), t.target(), t.kind(), t.until(), now, t.idle()));
		return true;
	}

	// ---------------------------------------------------------------- reading (lazy expiry)

	public static @Nullable Tag liveTag(ServerPlayer owner) {
		return liveTag(owner, owner.level().getGameTime());
	}

	static @Nullable Tag liveTag(ServerPlayer owner, long now) {
		UUID target = BY_OWNER.get(owner.getUUID());
		if (target == null) return null;
		Tag t = BY_TARGET.get(target);
		if (t == null || !t.owner().equals(owner.getUUID())) {
			BY_OWNER.remove(owner.getUUID());
			return null;
		}
		if (!t.alive(now)) {
			BY_TARGET.remove(target);
			BY_OWNER.remove(owner.getUUID());
			return null;
		}
		return t;
	}

	/** The whip mark {@code owner} has on {@code target}, or null. */
	public static @Nullable WhipKind kindOn(@Nullable ServerPlayer owner, @Nullable Entity target) {
		if (owner == null || target == null) return null;
		Tag t = liveTag(owner);
		return t != null && t.target().equals(target.getUUID()) ? t.kind() : null;
	}

	public static boolean isTaggedBy(@Nullable ServerPlayer owner, @Nullable Entity target) {
		return kindOn(owner, target) != null;
	}

	/** The owner's marked target if it is alive in the owner's level (the minions switch to it). */
	public static @Nullable LivingEntity target(@Nullable ServerPlayer owner) {
		if (owner == null) return null;
		Tag t = liveTag(owner);
		if (t == null || !(owner.level() instanceof ServerLevel sl)) return null;
		return sl.getEntity(t.target()) instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	/** Whoever's whip tag is on {@code target} (no expiry check; tests and debugging). */
	public static @Nullable Tag rawOn(UUID target) {
		return BY_TARGET.get(target);
	}

	// ---------------------------------------------------------------- clean-up

	public static void clear(UUID owner) {
		removeOwner(owner);
	}

	public static void clearTarget(UUID target) {
		removeTarget(target);
	}

	public static void reset() {
		BY_TARGET.clear();
		BY_OWNER.clear();
	}

	public static int size() {
		return BY_TARGET.size();
	}

	private static void removeOwner(UUID owner) {
		UUID target = BY_OWNER.remove(owner);
		if (target == null) return;
		Tag t = BY_TARGET.get(target);
		if (t != null && t.owner().equals(owner)) BY_TARGET.remove(target);
	}

	private static void removeTarget(UUID target) {
		Tag t = BY_TARGET.remove(target);
		if (t != null && target.equals(BY_OWNER.get(t.owner()))) BY_OWNER.remove(t.owner());
	}
}
