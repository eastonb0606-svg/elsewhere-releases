package dev.elsewhere.kingdom.armies.impostor;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.UnitType;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * One soldier carried by a rank impostor (server only): his unit, his saved health (wounded men keep their wounds; the dead stay dead) and,
 * for a soldier folded out of the field, his UUID. A figure that breaks out becomes a real soldier of that unit, at its own spot, with that
 * health.
 */
public final class Figure {
	public final Identifier unit;
	public float health;
	public final @Nullable UUID uuid;
	/** Index (0-3) of this figure's look in the rank's synced look list. */
	int look;

	public Figure(Identifier unit, float health, @Nullable UUID uuid) {
		this.unit = unit;
		this.health = health;
		this.uuid = uuid;
	}

	/**
	 * The figure's appearance as the client bakes it into one 64x64 LOD skin (ARMIES.md 3.7): the unit skin, the four armor item ids and the
	 * leather dye, from the faction's own roster ({@link Factions#unit}). Encoded {@code skin;head;chest;legs;feet;dye} (empty = no piece).
	 */
	public static String look(Identifier faction, Identifier unit) {
		UnitType u = Factions.unit(faction, unit);
		UnitType.Armor a = u.armor();
		return u.skin() + ";" + a.head().map(Identifier::toString).orElse("") + ";" + a.chest().map(Identifier::toString).orElse("") + ";"
			+ a.legs().map(Identifier::toString).orElse("") + ";" + a.feet().map(Identifier::toString).orElse("") + ";" + u.armorDye().orElse(-1);
	}
}
