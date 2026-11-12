package dev.elsewhere.kingdom.tactics;

import java.util.Map;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Per-soldier instruction written by a {@link SquadBrain} and executed by {@link OrderGoal}. Ticks are absolute game ticks. */
public record Order(Types.OrderKind kind, @Nullable Vec3 pos, @Nullable UUID target, long expiresTick, long issuedTick, long reactAtTick) {
	public boolean live(long now) {
		return now < expiresTick;
	}

	public boolean ready(long now) {
		return now >= reactAtTick && now < expiresTick;
	}

	/** What a behaviour returns: orders per soldier, an optional signal, and flags. Orders with a zero expiry are given the behaviour's default by the brain. */
	public record Orders(Map<UUID, Order> perSoldier, @Nullable Signal signal, SquadFlags flags) {
		public static final Orders NONE = new Orders(Map.of(), null, SquadFlags.NONE);

		public static Orders of(Map<UUID, Order> m, @Nullable Signal s) {
			return new Orders(m, s, SquadFlags.NONE);
		}
	}

	public record SquadFlags(boolean withdrawing, boolean engaging) {
		public static final SquadFlags NONE = new SquadFlags(false, false);
	}
}
