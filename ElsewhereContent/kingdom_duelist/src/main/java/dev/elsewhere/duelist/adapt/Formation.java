package dev.elsewhere.duelist.adapt;

/** The five guard formations (B3). Duration and cooldown in ticks (0 = no limit). */
public enum Formation {
	/** Banner overhead. A jackal-shield wall of 3 to 4 shields 3 blocks ahead of the leader, advancing at 80 percent speed. */
	SCREEN(80, 400),
	/** Blade down. Circles the player at 6 blocks and tightens 1 block a second down to 3. */
	RING(120, 0),
	/** Blade swept sideways. Splits to the player's flanks, 5 blocks left and right. */
	OPEN(60, 0),
	/** Banner lowered. Returns to within 3 blocks of the leader, until the next signal. */
	WITHDRAW(0, 0),
	/** Two khopesh taps on the shield. The hook guard rushes the player's shield; one attempt per 160 ticks. */
	HOOK(60, 160);

	public final int duration, cooldown;

	Formation(int duration, int cooldown) {
		this.duration = duration;
		this.cooldown = cooldown;
	}
}
