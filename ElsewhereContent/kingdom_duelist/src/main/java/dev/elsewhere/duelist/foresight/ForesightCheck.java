package dev.elsewhere.duelist.foresight;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.boss.kit.ForesightGear;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.boss.Telegraph;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Self-test helper for the rival modules (B5): one real boss of a type, passive, with two players standing in the world beside
 * it, one holding foresight gear in the off hand and one with nothing. {@link #begin} sets them up; a few ticks later (the
 * chunk is ticking) {@link #probe} starts a heavy wind-up in the boss's own telegraph look; about 12 ticks after that
 * {@link #finish} reports whether its particles reached only the player with gear and whether the bar's move label went only
 * to that player, then removes the boss and the players.
 */
public final class ForesightCheck {
	public record Line(String name, boolean ok, String detail) {}

	public static final String PROBE_ID = "foresight_probe", PROBE_LABEL = "Foresight Probe";
	private static int n;

	private final HumanBoss boss;
	private final FakePlayer on, off;
	private final String path;
	private final ServerLevel level;
	private final int cx, cz;
	private final boolean wasForced;
	private int shown0, held0, tick0;
	private final List<String> toOn = new ArrayList<>(), toOff = new ArrayList<>();

	private ForesightCheck(ServerLevel level, int cx, int cz, boolean wasForced, HumanBoss boss, FakePlayer on, FakePlayer off, String path) {
		this.level = level;
		this.cx = cx;
		this.cz = cz;
		this.wasForced = wasForced;
		this.boss = boss;
		this.on = on;
		this.off = off;
		this.path = path;
		ForesightBossEvent.sentHook = (p, c) -> {
			if (p == on) toOn.add(c.getString());
			else if (p == off) toOff.add(c.getString());
		};
		boss.startSeenByPlayer(on);
		boss.startSeenByPlayer(off);
	}

	/** Starts the probe wind-up (20 ticks, heavy, the boss's own telegraph style). Wait ~12 ticks, then {@link #finish}. */
	public void probe() {
		shown0 = Foresight.shown;
		held0 = Foresight.held;
		tick0 = boss.tickCount;
		boss.startTelegraph(PROBE_ID, PROBE_LABEL, 20, Telegraph.Weight.HEAVY, 0F, boss.style().cue(false, true), true, () -> {});
	}

	/** The first item of {@code #elsewhere:foresight_gear} (empty if the tag is empty in this build). */
	public static ItemStack foresightItem() {
		for (Holder<Item> hd : BuiltInRegistries.ITEM.getTagOrEmpty(ForesightGear.TAG)) return new ItemStack(hd.value());
		return ItemStack.EMPTY;
	}

	/** Spawns a passive boss of {@code type} at block (x, z) on the surface with the two players. Wait ~5 ticks, then {@link #probe}. */
	public static ForesightCheck begin(ServerLevel l, EntityType<? extends HumanBoss> type, int x, int z) {
		boolean was = l.getForceLoadedChunks().contains(ChunkPos.pack(x >> 4, z >> 4));
		if (!was) l.setChunkForced(x >> 4, z >> 4, true);
		l.getChunk(x >> 4, z >> 4);
		int y = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
		FakePlayer on = fake(l, x + 3.5, y, z + 0.5, foresightItem());
		FakePlayer off = fake(l, x - 2.5, y, z + 0.5, ItemStack.EMPTY);
		HumanBoss b = type.create(l, EntitySpawnReason.COMMAND);
		b.snapTo(x + 0.5, y, z + 0.5, 0F, 0F);
		b.passive = true;
		l.addFreshEntity(b);
		return new ForesightCheck(l, x >> 4, z >> 4, was, b, on, off, BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
	}

	private static FakePlayer fake(ServerLevel l, double x, double y, double z, ItemStack offhand) {
		FakePlayer f = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes(("foresight-check" + n++).getBytes()), "ForesightCheck" + n));
		f.snapTo(x, y, z, 0F, 0F);
		f.setItemSlot(EquipmentSlot.OFFHAND, offhand);
		Foresight.refresh(f);
		if (l.getEntity(f.getUUID()) == null) l.addFreshEntity(f);   // in level.players(): the boss's broadcast particles really reach or skip it
		return f;
	}

	/** Reads the results and removes the boss and both players. */
	public List<Line> finish() {
		int shown = Foresight.shown - shown0, held = Foresight.held - held0;
		List<Line> out = new ArrayList<>();
		out.add(new Line("B5 foresight " + path + ": the player with foresight gear sees, the one without does not", Foresight.sees(on) && !Foresight.sees(off),
			"gear " + on.getOffhandItem().getHoverName().getString()));
		out.add(new Line("B5 foresight " + path + ": wind-up telegraph particles reach the player with gear and are held from the one without",
			shown >= 5 && held >= 5, "shown " + shown + ", held " + held + " in " + (boss.tickCount - tick0) + " boss ticks"));
		boolean onGot = toOn.stream().anyMatch(s -> s.contains(PROBE_LABEL)), offGot = toOff.stream().anyMatch(s -> s.contains(PROBE_LABEL));
		out.add(new Line("B5 foresight " + path + ": the bar's move label goes only to the player with gear", onGot && !offGot,
			"with gear " + toOn + ", without " + toOff));
		ForesightBossEvent.sentHook = null;
		boss.stopSeenByPlayer(on);
		boss.stopSeenByPlayer(off);
		boss.discard();
		on.discard();
		off.discard();
		Foresight.forget(on);
		Foresight.forget(off);
		if (!wasForced) level.setChunkForced(cx, cz, false);
		return out;
	}
}
