package dev.elsewhere.rivals.mirewick.boss;

import java.util.ArrayList;
import java.util.List;

/**
 * The heirs of the five Magnate families (RIVALS-DECISIONS 10): each family has 2 heirs, a relative with the same surname and a new
 * first name, who inherit the money, the seat's kit and the vote leaning 7 in-game days after the holder dies; after the third holder dies
 * the seat stays empty for good. The court draws the first name from the list given to {@code Succession.registerHeirNames} at index
 * {@code rank * 3 + generation} (generation 1 or 2), so {@link #courtNameList()} lays the names out on that grid. The same names are in the
 * faction file's {@code society.succession.heirs} block and in the lang file ({@code mirewick.heir.<slot>.<n>}).
 */
public final class Heirs {
	private Heirs() {}

	public static final int PER_FAMILY = 2;
	public static final long DELAY_DAYS = 7;

	/** First names by court slot, in order of succession (the first heir, then the second). */
	public static String[] firstNames(Magnate m) {
		return switch (m) {
			case MARROW -> new String[] {"Perpetua", "Hester"};
			case THWAITE -> new String[] {"Osbert", "Maud"};
			case SORREL -> new String[] {"Ivo", "Agnes"};
			case TOLLIVER -> new String[] {"Piers", "Linnet"};
			case AMES -> new String[] {"Jory", "Matilda"};
		};
	}

	public static String surname(Magnate m) {
		return switch (m) {
			case MARROW -> "Marrow";
			case THWAITE -> "Thwaite";
			case SORREL -> "Sorrel";
			case TOLLIVER -> "Tolliver";
			case AMES -> "Ames";
		};
	}

	/** Court seat rank of a Magnate's slot (king 0, marshal 1, castellan 2, master_at_arms 3, huntsman 4, chancellor 5). */
	public static int rank(Magnate m) {
		return switch (m.courtSlot) {
			case "marshal" -> 1;
			case "castellan" -> 2;
			case "master_at_arms" -> 3;
			case "huntsman" -> 4;
			default -> 5;
		};
	}

	/** The full name of heir {@code generation} (1 or 2) of a family. */
	public static String fullName(Magnate m, int generation) {
		return firstNames(m)[generation - 1] + " " + surname(m);
	}

	/** The list for {@code Succession.registerHeirNames}: slot {@code rank * 3 + generation} holds that heir's first name (the rest are fillers never drawn). */
	public static List<String> courtNameList() {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < 18; i++) out.add("Heir");
		for (Magnate m : Magnate.values()) for (int g = 1; g <= PER_FAMILY; g++) out.set(rank(m) * 3 + g, firstNames(m)[g - 1]);
		return out;
	}
}
