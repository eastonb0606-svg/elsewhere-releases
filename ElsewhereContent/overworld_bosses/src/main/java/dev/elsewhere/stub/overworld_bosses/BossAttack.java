package dev.elsewhere.stub.overworld_bosses;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;

/**
 * One scheduled boss attack: cooldown, wind-up (telegraph), fire, active time. Built with a small fluent API so
 * each boss lists its kit in one place. Timings come from {@link BossTuning} (JSON) with code defaults.
 * STUB: the core's framework owns the real scheduler; keep boss logic in the lambdas, not here.
 */
public final class BossAttack<B extends ElsewhereBoss> {
	public enum Danger { DODGE, BIG, INTERACT }

	public final String id;
	public Component name;
	public int cooldown = 100;
	public int windup = 20;
	public int active = 0;
	public int phaseMask = 0b111;
	public int weight = 1;
	public Danger danger = Danger.DODGE;
	public boolean showName = true;
	Predicate<B> canStart = b -> b.getTarget() != null;
	Consumer<B> onStart = b -> {};
	BiConsumer<B, Integer> onWindup = (b, t) -> {};
	Consumer<B> onFire = b -> {};
	BiConsumer<B, Integer> onActive = (b, t) -> {};
	Consumer<B> onEnd = b -> {};
	long readyAt;

	private BossAttack(String id) {
		this.id = id;
	}

	public static <B extends ElsewhereBoss> BossAttack<B> of(String bossId, String id) {
		BossAttack<B> a = new BossAttack<>(id);
		a.name = Component.translatable("attack.elsewhere." + bossId + "." + id);
		return a;
	}

	public BossAttack<B> cooldown(int ticks) { this.cooldown = ticks; return this; }
	public BossAttack<B> windup(int ticks) { this.windup = ticks; return this; }
	public BossAttack<B> active(int ticks) { this.active = ticks; return this; }
	/** Phases are 1-based: phases(1, 2) means phase 1 and 2. */
	public BossAttack<B> phases(int... phases) {
		this.phaseMask = 0;
		for (int p : phases) this.phaseMask |= 1 << (p - 1);
		return this;
	}
	public BossAttack<B> weight(int weight) { this.weight = weight; return this; }
	public BossAttack<B> danger(Danger danger) { this.danger = danger; return this; }
	public BossAttack<B> silent() { this.showName = false; return this; }
	public BossAttack<B> when(Predicate<B> p) { this.canStart = p; return this; }
	public BossAttack<B> onStart(Consumer<B> c) { this.onStart = c; return this; }
	public BossAttack<B> onWindup(BiConsumer<B, Integer> c) { this.onWindup = c; return this; }
	public BossAttack<B> onFire(Consumer<B> c) { this.onFire = c; return this; }
	public BossAttack<B> onActive(BiConsumer<B, Integer> c) { this.onActive = c; return this; }
	public BossAttack<B> onEnd(Consumer<B> c) { this.onEnd = c; return this; }

	public boolean inPhase(int phase) {
		return (this.phaseMask & (1 << (phase - 1))) != 0;
	}

	/** Applies JSON tuning ("attacks": {"<id>": {"cooldown": .., "windup": .., "active": ..}}). */
	void tune(BossTuning tuning) {
		this.cooldown = tuning.attackInt(this.id, "cooldown", this.cooldown);
		this.windup = tuning.attackInt(this.id, "windup", this.windup);
		this.active = tuning.attackInt(this.id, "active", this.active);
		// boss feel: big attacks get a 25% longer wind-up so the tell is readable (a tuning JSON windup is scaled too)
		if (this.danger == Danger.BIG) this.windup = Math.round(this.windup * 1.25F);
	}
}
