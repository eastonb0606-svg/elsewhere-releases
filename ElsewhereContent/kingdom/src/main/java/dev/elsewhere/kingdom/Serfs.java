package dev.elsewhere.kingdom;

import dev.elsewhere.kingdom.war.WarState;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * The Serf tier of each kingdom (RIVALS-DECISIONS 3.2). Aldermark: Serfs. Hrafnholt: Thralls (double the count, 18 HP (RIVALS-DECISIONS 14), a third of them take stone axes as the levy when
 * their village is attacked). Azmar: Hired Porters (few in villages, one or two with every caravan). Mirewick: Peat-Cutters, paid by the Council: when the Counting-House is raided
 * they strike for 3 days ({@link #strike}) and village rebuilding stops. The faction file's {@code society.serfs} wins over the built-in defaults here; the villages module
 * does the spawning and behaviour and asks this class what to spawn.
 */
public final class Serfs {
	private Serfs() {}

	public static final int STRIKE_DAYS = 3;

	/** One kingdom's serf tier. {@code type} is the unit or entity id the villages module spawns. */
	public record Variant(Identifier type, float countMultiplier, double health, Optional<FactionDef.Levy> levy, boolean paidByCouncil, int caravanMin, int caravanMax) {}

	public static Variant variant(Identifier faction) {
		FactionDef d = Factions.def(Factions.ledgerOf(faction));
		FactionDef.Serfs s = d == null ? FactionDef.Serfs.DEFAULT : d.society().serfs();
		String p = faction.getPath();
		Identifier type = s.type().orElse(null);
		if (type == null) {
			type = switch (p) {
				case "hrafnholt" -> KingdomContent.id("thrall");
				case "azmar" -> KingdomContent.id("porter");
				case "mirewick" -> KingdomContent.id("peat_cutter");
				default -> KingdomContent.id("serf");
			};
			float mul = switch (p) {
				case "hrafnholt" -> 2.0F;
				case "azmar" -> 0.5F;
				default -> 1.0F;
			};
			double hp = switch (p) {
				case "hrafnholt" -> 18.0;
				case "mirewick" -> 18.0;
				default -> 18.0;
			};
			Optional<FactionDef.Levy> levy = p.equals("hrafnholt") ? Optional.of(new FactionDef.Levy(0.33F, Identifier.withDefaultNamespace("stone_axe"))) : Optional.empty();
			return new Variant(type, mul, hp, levy, p.equals("mirewick"), p.equals("azmar") ? 1 : 0, p.equals("azmar") ? 2 : 0);
		}
		return new Variant(type, s.countMultiplier(), s.health(), s.levy(), p.equals("mirewick"), p.equals("azmar") ? 1 : 0, p.equals("azmar") ? 2 : 0);
	}

	/** How many serfs a village of this faction gets when an Aldermark village would have {@code base}. */
	public static int countFor(Identifier faction, int base) {
		if (base <= 0) return 0;
		return Math.max(1, Math.round(base * variant(faction).countMultiplier()));
	}

	/** How many of {@code present} serfs pick up the levy weapon when their village is attacked (0 for a faction without a levy). */
	public static int levyCount(Identifier faction, int present) {
		Optional<FactionDef.Levy> l = variant(faction).levy();
		return l.isEmpty() ? 0 : (int) Math.floor(present * l.get().fraction() + 1e-6);
	}

	/** The levy weapon item id, or null. */
	public static Identifier levyWeapon(Identifier faction) {
		return variant(faction).levy().map(FactionDef.Levy::weapon).orElse(null);
	}

	/** Porters walking with a caravan beside the pack camel: 1-2 for Azmar, otherwise 0. */
	public static int caravanPorters(Identifier faction, RandomSource r) {
		Variant v = variant(faction);
		return v.caravanMax() <= 0 ? 0 : v.caravanMin() + r.nextInt(v.caravanMax() - v.caravanMin() + 1);
	}

	/** The Counting-House (or any paymaster) was raided: that faction's council-paid diggers walk off for {@link #STRIKE_DAYS} days. */
	public static void strike(MinecraftServer server, Identifier faction) {
		WarState ws = WarState.get(server.overworld());
		long until = server.overworld().getGameTime() / Standing.DAY + STRIKE_DAYS;
		ws.data.strikeUntil.merge(faction.toString(), until, Math::max);
		ws.setDirty();
	}

	/** True while the faction's serfs are on strike: no rebuilding. */
	public static boolean isOnStrike(MinecraftServer server, Identifier faction) {
		WarState ws = WarState.get(server.overworld());
		Long u = ws.data.strikeUntil.get(faction.toString());
		return u != null && u > server.overworld().getGameTime() / Standing.DAY;
	}

	public static boolean isOnStrike(ServerLevel any, Identifier faction) {
		return isOnStrike(any.getServer(), faction);
	}
}
