package dev.elsewhere.duelist.boss;

/**
 * Opt-in fighting behaviours. A boss that does not pass one of these to its {@link DuelistProfile} builder does none of
 * it. Every record carries every number of its behaviour; nothing here has a default and nothing is shared between
 * bosses. Ticks are 1/20 s, distances are blocks, raw damage is before the victim's armor.
 */
public final class Behaviours {
	private Behaviours() {}

	/** Circle-strafing in melee: speed multiplier while strafing, a direction flip every {@code flipMin..flipMax} ticks, the melee spacing band, and the longest it may stand still. */
	public record Strafe(float speedMult, int flipMin, int flipMax, double spacingMin, double spacingMax, int maxStillTicks) {}

	/** Sprint-jump crit: crouch wind-up, run-up distance, cooldown, the stagger a hit on it mid-air causes, the damage multiplier while falling. */
	public record SprintCrit(double runUp, int crouchTicks, int cooldown, int staggerTicks, float multiplier) {}

	/** Shield bash (part of {@link Shield}): wind-up, cooldown, raw damage, knockback. A bash never disables a shield. */
	public record Bash(int windup, int cooldown, float raw, float knockback) {}

	/**
	 * Shield blocking with a real vanilla shield: raise delay, how long it is held ({@code holdMin..holdMax}), the lowered time
	 * between raises, the block arc, how long an axe hit disables it, and an optional bash (null = none).
	 */
	public record Shield(int raiseDelay, int holdMin, int holdMax, int lowerMin, float arcDegrees, int axeDisableTicks, Bash bash) {}

	/** Shield-breaking: this boss's own axe (or whatever it wields that disables shields) takes a player's shield down for {@code ticks}, at most once per {@code cooldown} ticks. Without it the boss never disables a shield. */
	public record ShieldBreak(int ticks, int cooldown) {}

	/**
	 * Weapon swap by range: melee inside {@code meleeRange}, a thrown weapon out to {@code thrownRange}, a ranged weapon beyond.
	 * {@code swapTicks} to change, {@code minGap} between swaps, the thrown wind-up, cooldown and raw damage.
	 */
	public record Swap(double meleeRange, double thrownRange, int swapTicks, int minGap, int throwWindup, int throwCooldown, float thrownRaw) {}

	/** Human bow: draw time, ticks between shots, spread ({@code spreadBase} up to {@code spreadFlatRange}, then {@code spreadPerBlock} per block, capped), lead fraction, raw damage. */
	public record Bow(int draw, int minGap, double spreadBase, double spreadFlatRange, double spreadPerBlock, double spreadCap, double lead, float raw) {
		public double spreadAt(double dist) {
			return Math.min(spreadCap, spreadBase + Math.max(0, dist - spreadFlatRange) * spreadPerBlock);
		}
	}

	/** Crossbow: load time, longest it holds a loaded bolt, spread, ticks between shots, raw damage. */
	public record Crossbow(int load, int holdMax, double spread, int minGap, float raw) {}

	/** When a heal may start: below {@code below} of max HP, nobody within {@code clearRadius}, at most {@code maxUses}, and a hit that interrupts it locks the next try for {@code lockout} ticks. */
	public record HealRule(float below, double clearRadius, int maxUses, int lockout) {}

	/** Eating: heals {@code heal} of max HP spread over {@code spreadTicks}. */
	public record Eat(float heal, int spreadTicks, HealRule rule) {}

	/** Drinking a potion: heals {@code heal} of max HP at once. */
	public record Drink(float heal, HealRule rule) {}

	/** Ender pearl hop: only when the target is {@code minDist}..{@code maxRange} away and unreachable for {@code unreachableTicks}; lands within {@code landRadius}; {@code fallRaw} damage. */
	public record Pearl(double minDist, double maxRange, double landRadius, int unreachableTicks, int windup, int cooldown, int max, float fallRaw) {}

	/** Temporary blocks: one every {@code interval} ticks, each lasting {@code lifetime}, at most {@code maxActive}, bridges up to {@code bridgeMax} long and pillars up to {@code pillarMax} high. */
	public record TempBlocks(int interval, int lifetime, int maxActive, int bridgeMax, int pillarMax) {}
}
