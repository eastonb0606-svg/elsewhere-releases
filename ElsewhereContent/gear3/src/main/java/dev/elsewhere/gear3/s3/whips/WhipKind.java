package dev.elsewhere.gear3.s3.whips;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;

/**
 * The four gear3 whips (GEAR.md A5, Summoner): each tags what it hits with its own mark, and the owner's minions hit a
 * marked target harder. Numbers are the GEAR.md table's:
 * <ul>
 * <li>Thornlash: Thorned 5 s, minion hits +2 flat (Gale Ribbon rules)</li>
 * <li>Tidechain Lash: Shackled 5 s (7 s if the target is wet), minion hits +20%</li>
 * <li>Sunscourge: Venom 5 s, minion hits +15% and ignore 3 armor</li>
 * <li>Echo Chord: Resonant 5 s, minion hits +25%</li>
 * </ul>
 * The colours are each whip's own lash colour from the sprite page (Ideas/overhaul/sprites.json "a"/"m").
 */
public enum WhipKind {
	THORNLASH("thornlash", "thorned", 0.0, 2.0, 0, 100, 100, 0xA8D468, ParticleTypes.CRIT),
	TIDECHAIN("tidechain_lash", "shackled", 0.20, 0.0, 0, 100, 140, 0xC9764A, ParticleTypes.SPLASH),
	SUNSCOURGE("azmar_sunscourge", "venom", 0.15, 0.0, 3, 100, 100, 0xE0A92A, ParticleTypes.WAX_ON),
	ECHO_CHORD("echo_chord", "resonant", 0.25, 0.0, 0, 100, 100, 0x3FB7C9, ParticleTypes.SCULK_CHARGE_POP);

	/** Item id (elsewhere:&lt;item&gt;), tag name (lang: message.elsewhere.whip.&lt;tag&gt;). */
	public final String item, tag;
	/** Minion hits on the marked target: x (1 + pct), then + flat. */
	public final double pct, flat;
	/** Armor points the owner's minion hits ignore on the marked target. */
	public final int armorIgnore;
	/** Tag length in ticks, dry and wet (in water or rain). */
	public final int ticks, wetTicks;
	/** Lash colour (0xRRGGBB): dust particles and the tag message. */
	public final int color;
	/** The mark's own particle (thorn pricks, sea spray, golden venom, sculk pops). */
	public final ParticleOptions mark;
	/** elsewhere:gear3.whip.&lt;item&gt;, registered by {@link Gear3Whips#init()}. */
	@Nullable SoundEvent sound;

	WhipKind(String item, String tag, double pct, double flat, int armorIgnore, int ticks, int wetTicks, int color, ParticleOptions mark) {
		this.item = item;
		this.tag = tag;
		this.pct = pct;
		this.flat = flat;
		this.armorIgnore = armorIgnore;
		this.ticks = ticks;
		this.wetTicks = wetTicks;
		this.color = color;
		this.mark = mark;
	}

	public @Nullable SoundEvent sound() {
		return sound;
	}

	/** Sound event path (elsewhere:gear3.whip.thornlash ...). */
	public String soundPath() {
		return "gear3.whip." + item;
	}

	public static @Nullable WhipKind byItem(String itemPath) {
		for (WhipKind k : values()) if (k.item.equals(itemPath)) return k;
		return null;
	}
}
