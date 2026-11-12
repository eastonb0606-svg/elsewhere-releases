package dev.elsewhere.kingdom.armies.routine;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Per squad: its post and the last place the routine sent it. Kept as saved data (overworld) as well as the soldier tags, because a
 * squad that goes virtual (LOD ring 3) comes back as new entities without tags; without this the routine would take the squad's
 * own objective for another lane's order and leave it alone for good. Written only when the routine gives an order.
 */
public final class RoutineState extends SavedData {
	public static final Codec<RoutineState> CODEC = CompoundTag.CODEC.xmap(RoutineState::load, RoutineState::save);
	public static final SavedDataType<RoutineState> TYPE = new SavedDataType<>(Identifier.fromNamespaceAndPath("elsewhere", "kingdom_routine"),
		RoutineState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	final Map<UUID, BlockPos> posts = new HashMap<>();
	final Map<UUID, BlockPos> goals = new HashMap<>();

	public static RoutineState get(ServerLevel lv) {
		return lv.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public @Nullable BlockPos post(UUID squad) {
		return posts.get(squad);
	}

	public @Nullable BlockPos goal(UUID squad) {
		return goals.get(squad);
	}

	void set(UUID squad, BlockPos post, BlockPos goal) {
		if (post.equals(posts.get(squad)) && goal.equals(goals.get(squad))) return;
		posts.put(squad, post.immutable());
		goals.put(squad, goal.immutable());
		setDirty();
	}

	void forget(UUID squad) {
		if (posts.remove(squad) != null | goals.remove(squad) != null) setDirty();
	}

	public static RoutineState load(CompoundTag t) {
		RoutineState s = new RoutineState();
		CompoundTag p = t.getCompoundOrEmpty("posts"), g = t.getCompoundOrEmpty("goals");
		for (String k : p.keySet()) {
			try {
				UUID id = UUID.fromString(k);
				s.posts.put(id, BlockPos.of(p.getLongOr(k, 0L)));
				if (g.contains(k)) s.goals.put(id, BlockPos.of(g.getLongOr(k, 0L)));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return s;
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag(), p = new CompoundTag(), g = new CompoundTag();
		posts.forEach((k, v) -> p.putLong(k.toString(), v.asLong()));
		goals.forEach((k, v) -> g.putLong(k.toString(), v.asLong()));
		t.put("posts", p);
		t.put("goals", g);
		return t;
	}
}
