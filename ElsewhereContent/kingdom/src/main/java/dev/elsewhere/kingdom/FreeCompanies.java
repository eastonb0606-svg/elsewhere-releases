package dev.elsewhere.kingdom;

import dev.elsewhere.kingdom.war.WarSim;
import dev.elsewhere.kingdom.war.WarState;
import dev.elsewhere.kingdom.war.WarApi;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Free Companies (RIVALS-DECISIONS 8.3): neutral sellsword bands, faction {@code elsewhere:free_company}. A kingdom with {@code hire_max} greater than 0 in its
 * brain tuning (Mirewick) hires them daily while at war or rich and pays them per head per day; when its treasury runs dry they {@linkplain #desert desert} (40 percent
 * of the time to the enemy it is at war with). A player can {@linkplain #outbid outbid} a company, and a Friendly or better player can be hired as a sellsword
 * ({@link #startContract}). The company's numbers live in the war model (hired heads at base stats, no veterancy); here are the named bands, the contracts and the stance rules.
 * <p>Real company soldiers use the units {@link #BLADE}, {@link #BOLT} and {@link #CAPTAIN} (mixed vanilla gear) and are spawned by the war layer's battles in place of part of
 * the hiring kingdom's soldiers. The faction is neutral to players and at war with the enemies of whoever employs it ({@link #refreshStances}).
 */
public final class FreeCompanies {
	private FreeCompanies() {}

	public static final Identifier FACTION = KingdomContent.id("free_company");
	public static final Identifier BLADE = KingdomContent.id("free_company_blade"), BOLT = KingdomContent.id("free_company_bolt"), CAPTAIN = KingdomContent.id("free_company_captain");
	static final String[] NAMES = {"The Grey Company", "The Marsh Dogs", "The Lantern Blades", "The Reed Cutters", "The Silt Wolves"};
	static final String[] CAPTAINS = {"Captain Bram Oakes", "Captain Joss Veller", "Captain Nell Harrow", "Captain Dirk Pellin", "Captain Maud Sedge"};

	/**
	 * The unit a hired company member really fields: Mirewick's {@code mirewick_fc_*} units (their own {@code fc_*} skins) when that module is loaded, else the base
	 * company unit. Blade becomes a brute 1 time in 4, otherwise a sellsword; Bolt a crossbow; Captain a captain.
	 */
	public static Identifier skinned(net.minecraft.util.RandomSource r, Identifier base) {
		String path = base.equals(BLADE) ? (r.nextInt(4) == 0 ? "fc_brute" : "fc_sellsword") : base.equals(BOLT) ? "fc_crossbow" : base.equals(CAPTAIN) ? "fc_captain" : null;
		if (path == null) return base;
		Identifier alt = KingdomContent.id("mirewick_" + path);
		return UnitTypes.exists(alt) ? alt : base;
	}

	static void register() {
		if (!Factions.exists(FACTION)) Factions.register(FACTION, 0x808080, false);
	}

	public static List<WarState.Company> companies(MinecraftServer server) {
		return WarState.get(server.overworld()).data.companies;
	}

	/** The employed companies of a kingdom. */
	public static List<WarState.Company> companiesOf(MinecraftServer server, Identifier employer) {
		List<WarState.Company> out = new ArrayList<>();
		for (WarState.Company c : companies(server)) if (c.employer.equals(employer.toString()) && !c.deserted) out.add(c);
		return out;
	}

	/** Hired heads of a kingdom (the model's number). */
	public static double heads(MinecraftServer server, Identifier employer) {
		List<WarState.Company> l = companiesOf(server, employer);
		double h = 0;
		for (WarState.Company c : l) h += c.heads;
		return h;
	}

	/**
	 * {@code employer} hires {@code heads} more sellswords now (a new band, or the first band when none) and pays the hire cost from its treasury. False when the kingdom is
	 * not in the war model, cannot afford it, or already holds its {@code hire_max}.
	 */
	public static boolean hire(MinecraftServer server, Identifier employer, int heads) {
		WarSim s = dev.elsewhere.kingdom.war.WarLayer.sim(server);
		int k = s == null ? -1 : dev.elsewhere.kingdom.war.WarLayer.participants(server).indexOf(employer);
		if (k < 0 || heads <= 0) return false;
		double room = s.br[k].hireMax - s.Hm[k];
		double cost = s.br[k].hireCost * heads;
		if (room < 1 || s.money[k] < cost) return false;
		int n = (int) Math.min(heads, room);
		s.Hm[k] += n;
		s.money[k] -= s.br[k].hireCost * n;
		syncFromSim(server.overworld(), s, dev.elsewhere.kingdom.war.WarLayer.participants(server));
		refreshStances(server);
		WarState.get(server.overworld()).setDirty();
		return true;
	}

	/** A band gives up its contract: its heads leave the employer; with {@code toEnemy} non-null half of them join that kingdom's army (the model's desertion). */
	public static void desert(MinecraftServer server, String companyId, @Nullable Identifier toEnemy) {
		WarSim s = dev.elsewhere.kingdom.war.WarLayer.sim(server);
		List<Identifier> ids = dev.elsewhere.kingdom.war.WarLayer.participants(server);
		for (WarState.Company c : companies(server)) {
			if (!c.id.equals(companyId) || c.deserted) continue;
			int k = ids.indexOf(Identifier.tryParse(c.employer));
			if (s != null && k >= 0) s.Hm[k] = Math.max(0, s.Hm[k] - c.heads);
			int e = toEnemy == null ? -1 : ids.indexOf(toEnemy);
			if (s != null && e >= 0) s.H[e] += c.heads * 0.5;
			c.deserted = true;
			c.employer = "";
			c.against = toEnemy == null ? "" : toEnemy.toString();
			WarState.get(server.overworld()).setDirty();
		}
		refreshStances(server);
	}

	/** What a captain wants to change sides: ten days of the company's pay. */
	public static int outbidPrice(WarState.Company c) {
		return (int) Math.ceil(c.heads * c.payPerHead * 10);
	}

	/**
	 * A player pays {@code scrip} to a company's captain. At or above {@link #outbidPrice} the company leaves its employer and half of its heads join {@code enemyChoice}
	 * (WAR-AI section 5). The caller removes the coins. Returns true when the company turned.
	 */
	public static boolean outbid(ServerPlayer p, String companyId, int scrip, Identifier enemyChoice) {
		MinecraftServer srv = p.level().getServer();
		for (WarState.Company c : companies(srv)) {
			if (!c.id.equals(companyId) || c.deserted || scrip < outbidPrice(c)) continue;
			desert(srv, companyId, enemyChoice);
			return true;
		}
		return false;
	}

	// ---- player contracts ----------------------------------------------------------------------------------------------

	/** Hires a Friendly or better player as a sellsword for {@code ticks}: kills of {@code enemy} soldiers are counted for pay. False if Standing is too low. */
	public static boolean startContract(ServerPlayer p, Identifier employer, Identifier enemy, long ticks) {
		if (Standing.get(p.level(), p.getUUID(), employer) < 30) return false;
		WarState ws = WarState.get(p.level());
		WarState.Contract c = new WarState.Contract();
		c.player = p.getUUID().toString();
		c.employer = employer.toString();
		c.enemy = enemy.toString();
		c.untilTick = p.level().getServer().overworld().getGameTime() + ticks;
		ws.data.contracts.removeIf(x -> x.player.equals(c.player) && x.employer.equals(c.employer));
		ws.data.contracts.add(c);
		ws.setDirty();
		return true;
	}

	/** The kills a player has made for {@code employer} under a live contract and not yet paid out (court pays {@code kills x 2} Scrip then calls {@link #markPaid}). */
	public static int contractKills(ServerLevel l, UUID player, Identifier employer) {
		for (WarState.Contract c : WarState.get(l).data.contracts) if (c.player.equals(player.toString()) && c.employer.equals(employer.toString()) && !c.paid) return c.kills;
		return 0;
	}

	public static void markPaid(ServerLevel l, UUID player, Identifier employer) {
		WarState ws = WarState.get(l);
		ws.data.contracts.removeIf(c -> c.player.equals(player.toString()) && c.employer.equals(employer.toString()));
		ws.setDirty();
	}

	static void onSoldierDied(ServerLevel level, KingdomSoldier s, net.minecraft.world.damagesource.DamageSource src) {
		if (!(src.getEntity() instanceof ServerPlayer p)) return;
		WarState ws = WarState.get(level);
		if (ws.data.contracts.isEmpty()) return;
		long now = level.getServer().overworld().getGameTime();
		for (WarState.Contract c : ws.data.contracts) {
			if (c.paid || !c.player.equals(p.getUUID().toString()) || c.untilTick < now) continue;
			if (c.enemy.equals(Factions.ledgerOf(s.factionId()).toString())) {
				c.kills++;
				ws.setDirty();
			}
		}
	}

	// ---- daily upkeep and stances ----------------------------------------------------------------------------------------

	/** Splits the model's hired heads of every hiring kingdom over named bands (up to three), keeping their ids stable. */
	public static void syncFromSim(ServerLevel overworld, WarSim s, List<Identifier> ids) {
		WarState ws = WarState.get(overworld);
		long now = overworld.getGameTime();
		boolean changed = false;
		for (int k = 0; k < ids.size(); k++) {
			if (s.br == null || s.br[k].hireMax <= 0) continue;
			String emp = ids.get(k).toString();
			List<WarState.Company> mine = new ArrayList<>();
			for (WarState.Company c : ws.data.companies) if (c.employer.equals(emp) && !c.deserted) mine.add(c);
			double total = s.Hm[k];
			int want = total < 1 ? 0 : Math.min(3, 1 + (int) (total / 16));
			while (mine.size() < want) {
				WarState.Company c = new WarState.Company();
				int idx = ws.data.companies.size();
				c.id = UUID.nameUUIDFromBytes((emp + "|" + idx + "|" + now).getBytes()).toString();
				c.name = NAMES[idx % NAMES.length];
				c.captain = CAPTAINS[idx % CAPTAINS.length];
				c.employer = emp;
				c.payPerHead = s.eff != null ? s.eff[k].hirePay : s.br[k].hirePay;
				c.hiredTick = now;
				ws.data.companies.add(c);
				mine.add(c);
				changed = true;
			}
			while (mine.size() > want) {
				WarState.Company c = mine.remove(mine.size() - 1);
				c.deserted = true;
				c.employer = "";
				changed = true;
			}
			for (WarState.Company c : mine) {
				double h = want == 0 ? 0 : total / want;
				if (Math.abs(c.heads - h) > 1e-6) {
					c.heads = h;
					changed = true;
				}
			}
		}
		ws.data.companies.removeIf(c -> c.deserted && now - c.hiredTick > 40 * Standing.DAY);
		if (changed) ws.setDirty();
	}

	/** Daily upkeep: expire old contracts and refresh the stances. */
	public static void dailyTick(ServerLevel overworld, int days) {
		WarState ws = WarState.get(overworld);
		long now = overworld.getGameTime();
		ws.data.contracts.removeIf(c -> c.untilTick + 7 * Standing.DAY < now);
		refreshStances(overworld.getServer());
	}

	/**
	 * The Free Company faction is at peace with everyone except the enemies of its employers: it is allied with an employer and at war with every kingdom that employer is
	 * at war with (per the war model). Called after hiring, desertion and each war day.
	 */
	public static void refreshStances(MinecraftServer server) {
		if (!Factions.exists(FACTION)) return;
		List<Identifier> ids = dev.elsewhere.kingdom.war.WarLayer.participants(server);
		for (Factions.Faction f : Factions.all()) {
			if (f.id.equals(FACTION) || f.parent() != null) continue;
			Factions.Stance want = Factions.Stance.PEACE;
			for (WarState.Company c : companies(server)) {
				if (c.deserted || c.employer.isEmpty() || c.heads <= 0) continue;
				Identifier emp = Identifier.tryParse(c.employer);
				if (emp == null) continue;
				if (emp.equals(f.id)) want = Factions.Stance.ALLIED;
				else if (want != Factions.Stance.ALLIED && WarApi.relation(server, emp, f.id) == 2) want = Factions.Stance.WAR;
			}
			if (Factions.stance(FACTION, f.id) != want) KingdomApi.setStance(server, FACTION, f.id, want);
		}
	}
}
