package dev.elsewhere.stub.verdigris_ironpeak;

import dev.elsewhere.verdigris_ironpeak.Reg;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * STUB per-player data the core will own: first-kill records per boss and the accidental-trip state.
 * Stored as a persistent Fabric attachment (copied on death).
 */
public final class StubPlayerData {
	public static final AttachmentType<CompoundTag> DATA = AttachmentRegistry.create(Reg.id("vi_stub_player"),
		b -> b.persistent(CompoundTag.CODEC).copyOnDeath().initializer(CompoundTag::new));

	private StubPlayerData() {}

	public static CompoundTag get(ServerPlayer p) {
		CompoundTag t = p.getAttachedOrCreate(DATA);
		return t;
	}

	public static void save(ServerPlayer p, CompoundTag t) {
		p.setAttached(DATA, t);
	}

	public static CompoundTag section(ServerPlayer p, String name) {
		CompoundTag t = get(p);
		if (!t.contains(name)) t.put(name, new CompoundTag());
		return t.getCompoundOrEmpty(name);
	}

	/** Records a kill; returns true on this player's first kill of that boss. */
	public static boolean recordKill(ServerPlayer p, String bossId) {
		CompoundTag kills = section(p, "boss_kills");
		int n = kills.getIntOr(bossId, 0);
		kills.putInt(bossId, n + 1);
		return n == 0;
	}

	public static int kills(ServerPlayer p, String bossId) {
		return section(p, "boss_kills").getIntOr(bossId, 0);
	}
}
