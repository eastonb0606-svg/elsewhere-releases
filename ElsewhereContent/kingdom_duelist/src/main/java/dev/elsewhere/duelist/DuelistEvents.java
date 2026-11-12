package dev.elsewhere.duelist;

import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.kit.DamageCaps;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;

/** Lightweight listener lists for the self-tests and the faction lanes (no Fabric event objects needed). */
public final class DuelistEvents {
	private DuelistEvents() {}

	public interface Hit {
		void onHit(HumanBoss boss, LivingEntity victim, float amount, DamageCaps kind, boolean landed);
	}

	public interface MoveFired {
		void onMove(HumanBoss boss, String moveId);
	}

	public interface Yielded {
		void onYield(HumanBoss boss);
	}

	public static final List<Hit> HIT = new ArrayList<>();
	public static final List<MoveFired> MOVE = new ArrayList<>();
	public static final List<Yielded> YIELD = new ArrayList<>();
}
