package dev.elsewhere.kingdom_court.trade;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.Standing;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Shops;
import dev.elsewhere.kingdom_court.npc.DiplomatNpc;
import dev.elsewhere.kingdom_court.trade.TradeData.Line;
import dev.elsewhere.kingdom_court.trade.TradeData.Stock;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Azmar trades by order and control (RIVALS-DECISIONS 12): posted prices behind a Market Inspector, commissions made to order, caravans and Letters of Credit.
 * <ul>
 * <li><b>Posted prices</b>: no haggling, no drift. Stock unlocks by Standing: Neutral {@code common}, Friendly {@code workshop}, Allied {@code royal}.</li>
 * <li><b>Commissions</b> (lines with {@code commission: true}): choose the item and a trim colour, pay half, take a Commission Slip, collect after 3 in-game days for the rest.
 *     The item carries the buyer's name in its lore.</li>
 * <li><b>Caravans</b>: every 4th day a caravan (a Caravan Master, Hired Porters, camels) stops for one day and sells the rotating rare stock. Killing mobs or enemy soldiers that
 *     attack it pays 2-6 Dinars and +3 Standing.</li>
 * <li><b>Letters of Credit</b>: Dinars deposited at any treasury NPC (Inspector, Envoy, Caravan Master) are held server-side and drawn at any other; death does not touch them.</li>
 * </ul>
 */
public final class AzmarTrade {
	private AzmarTrade() {}

	public static final int COMMISSION_DAYS = 3, CARAVAN_EVERY_DAYS = 4, BOUNTY_MIN = 2, BOUNTY_MAX = 6, BOUNTY_STANDING = 3, BOUNTY_CAP_PER_VISIT = 6;
	public static final String CARAVAN_TAG = "kingdom_caravan", MASTER_TAG = "kingdom_caravan_master";

	// ---- posted prices and tiers ------------------------------------------------------------------------------------------------

	public static int tierRank(String tier) {
		return switch (tier) {
			case "workshop" -> 1;
			case "royal" -> 2;
			default -> 0;
		};
	}

	/** The highest tier this player may buy from: Neutral (and Wary) 0, Friendly 1, Allied 2. */
	public static int unlockedRank(ServerPlayer p, Identifier faction) {
		return switch (Trade.band(p, faction)) {
			case FRIENDLY -> 1;
			case ALLIED -> 2;
			default -> 0;
		};
	}

	public static String tierNeeds(String tier) {
		return switch (tierRank(tier)) {
			case 1 -> "Friendly";
			case 2 -> "Allied";
			default -> "Neutral";
		};
	}

	public record Quote(Line line, int price, boolean unlocked, String needs, int remaining) {}

	/** The posted price: the line price times the Standing discount (Friendly 0.95, Allied 0.85), never anything else. */
	public static int postedPrice(ServerPlayer p, Identifier faction, Line line) {
		return Trade.scaled(line.price(), Trade.standingDiscount(p, faction));
	}

	public static List<Quote> quotes(ServerPlayer p, Identifier faction) {
		ServerLevel l = (ServerLevel) p.level();
		int rank = unlockedRank(p, faction);
		List<Quote> out = new ArrayList<>();
		for (Line line : Trade.stock(l, faction).lines()) {
			if (line.itemOrNull() == null) continue;
			out.add(new Quote(line, postedPrice(p, faction, line), tierRank(line.tier()) <= rank, tierNeeds(line.tier()), Trade.remaining(l, p, faction, line)));
		}
		return out;
	}

	static @Nullable Line lineAt(Stock st, int index) {
		for (Line x : st.lines()) if (x.index() == index) return x;
		return null;
	}

	public static Trade.Result buy(ServerPlayer p, Identifier faction, int index) {
		ServerLevel l = (ServerLevel) p.level();
		Line line = lineAt(Trade.stock(l, faction), index);
		if (line == null || line.itemOrNull() == null) return Trade.Result.NO_SUCH_LINE;
		if (Trade.hostile(p, faction)) return Trade.Result.HOSTILE;
		if (tierRank(line.tier()) > unlockedRank(p, faction)) return Trade.Result.TIER_LOCKED;
		if (line.commission()) return Trade.Result.COMMISSION_ONLY;
		if (Trade.remaining(l, p, faction, line) == 0) return Trade.Result.WEEKLY_LIMIT;
		if (!Trade.hasExtras(p, line)) return Trade.Result.CANT_AFFORD;
		int price = postedPrice(p, faction, line);
		if (!Shops.take(p, faction, price)) return Trade.Result.CANT_AFFORD;
		Trade.takeExtras(p, line);
		Trade.deliver(p, faction, line);
		return Trade.Result.OK;
	}

	// ---- commissions ------------------------------------------------------------------------------------------------------------

	public static final List<String> COLOURS = List.of("quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald", "diamond", "lapis", "amethyst", "resin");

	static String ck(String id) {
		return "comm|" + id;
	}

	/** Record layout (strings joined by tab): owner, ready tick, balance, item id, count, colour, owner name, faction. */
	record Commission(String id, UUID owner, long ready, int balance, Identifier item, int count, String colour, String name, Identifier faction) {
		String encode() {
			return owner + "\t" + ready + "\t" + balance + "\t" + item + "\t" + count + "\t" + colour + "\t" + name + "\t" + faction;
		}

		static @Nullable Commission decode(String id, @Nullable String s) {
			if (s == null) return null;
			String[] a = s.split("\t");
			if (a.length < 8) return null;
			return new Commission(id, UUID.fromString(a[0]), Long.parseLong(a[1]), Integer.parseInt(a[2]), Identifier.parse(a[3]), Integer.parseInt(a[4]), a[5], a[6], Identifier.parse(a[7]));
		}
	}

	/** Orders a masterwork: pays half the posted price, receives a Commission Slip. {@code colour} is a trim colour (see {@link #COLOURS}); anything else is "plain". */
	public static Trade.Result order(ServerPlayer p, Identifier faction, int index, String colour) {
		ServerLevel l = (ServerLevel) p.level();
		Line line = lineAt(Trade.stock(l, faction), index);
		if (line == null || line.itemOrNull() == null) return Trade.Result.NO_SUCH_LINE;
		if (Trade.hostile(p, faction)) return Trade.Result.HOSTILE;
		if (!line.commission()) return Trade.Result.NOT_COMMISSION;
		if (tierRank(line.tier()) > unlockedRank(p, faction)) return Trade.Result.TIER_LOCKED;
		if (Trade.remaining(l, p, faction, line) == 0) return Trade.Result.WEEKLY_LIMIT;
		if (!Trade.hasExtras(p, line)) return Trade.Result.CANT_AFFORD;
		int price = postedPrice(p, faction, line), half = (price + 1) / 2;
		if (!Shops.take(p, faction, half)) return Trade.Result.CANT_AFFORD;
		Trade.takeExtras(p, line);
		String c = COLOURS.contains(colour.toLowerCase()) ? colour.toLowerCase() : "plain";
		long ready = Trade.now(l) + COMMISSION_DAYS * Trade.DAY;
		String id = UUID.randomUUID().toString();
		Commission cm = new Commission(id, p.getUUID(), ready, price - half, line.item(), line.count(), c, p.getGameProfile().name(), faction);
		Trade.state(l).putString(ck(id), cm.encode());
		Trade.give(p, slip(cm, l));
		if (line.weeklyLimit() > 0) Trade.addUsed(l, p, faction, line.key());
		Standing.onTrade(p, faction);
		return Trade.Result.OK;
	}

	static ItemStack slip(Commission c, ServerLevel l) {
		CompoundTag d = new CompoundTag();
		d.putString("id", c.id());
		d.putString("owner", c.owner().toString());
		d.putLong("ready", c.ready());
		d.putInt("balance", c.balance());
		ItemStack made = new ItemStack(BuiltInRegistries.ITEM.getValue(c.item()));
		long readyDay = Math.floorDiv(c.ready(), Trade.DAY);
		List<Component> lore = List.of(
			Component.literal("Commission for " + c.name()),
			Component.literal(made.getHoverName().getString() + (c.count() > 1 ? " x" + c.count() : "") + ", trim " + c.colour()),
			Component.literal("Ready from day " + readyDay + ". Balance due: " + c.balance() + "."));
		return TradeItems.withData(TradeItems.COMMISSION_SLIP, d, lore);
	}

	public static int daysLeft(ServerLevel l, long ready) {
		return (int) Math.max(0, Math.ceilDiv(ready - Trade.now(l), Trade.DAY));
	}

	/** Collects the first Commission Slip in the pack (main hand first): ready after 3 days, pay the balance, the item carries your name. */
	public static Trade.Result collect(ServerPlayer p) {
		ServerLevel l = (ServerLevel) p.level();
		ItemStack slip = TradeItems.find(p, TradeItems.COMMISSION_SLIP);
		if (slip == null) return Trade.Result.NO_SLIP;
		String id = TradeItems.data(slip).getStringOr("id", "");
		Commission c = Commission.decode(id, Trade.state(l).getString(ck(id)));
		if (c == null) return Trade.Result.NO_SLIP;
		if (!c.owner().equals(p.getUUID())) return Trade.Result.NOT_OWNER;
		if (Trade.now(l) < c.ready()) return Trade.Result.NOT_READY;
		if (!Shops.take(p, c.faction(), c.balance())) return Trade.Result.CANT_AFFORD;
		slip.shrink(1);
		Trade.state(l).remove(ck(id));
		Trade.give(p, made(l, c));
		return Trade.Result.OK;
	}

	static ItemStack made(ServerLevel l, Commission c) {
		ItemStack s = new ItemStack(BuiltInRegistries.ITEM.getValue(c.item()), c.count());
		List<Component> lore = new ArrayList<>();
		ItemLore old = s.get(DataComponents.LORE);
		if (old != null) lore.addAll(old.lines());
		lore.add(Component.literal("Made for " + c.name() + " at the Royal Workshop"));
		if (!"plain".equals(c.colour())) lore.add(Component.literal("Trim: " + c.colour()));
		s.set(DataComponents.LORE, new ItemLore(lore));
		if (!"plain".equals(c.colour())) trim(l, s, c.colour(), c.faction());
		return s;
	}

	/** Applies the trim to trimmable armor (the faction's pattern if registered, else Sentry). Returns true when a trim was added. */
	static boolean trim(ServerLevel l, ItemStack s, String colour, Identifier faction) {
		if (!s.has(DataComponents.EQUIPPABLE) || !s.is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR)) return false;
		var mats = l.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
		var pats = l.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
		Holder<TrimMaterial> m = mats.get(net.minecraft.resources.ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.withDefaultNamespace(colour))).orElse(null);
		Identifier pid = Identifier.fromNamespaceAndPath("elsewhere", faction.getPath());
		Holder<TrimPattern> pt = pats.get(net.minecraft.resources.ResourceKey.create(Registries.TRIM_PATTERN, pid)).orElse(pats.get(net.minecraft.resources.ResourceKey.create(Registries.TRIM_PATTERN, Identifier.withDefaultNamespace("sentry"))).orElse(null));
		if (m == null || pt == null) return false;
		s.set(DataComponents.TRIM, new ArmorTrim(m, pt));
		return true;
	}

	/** Open commissions of a player (slips the server still honours). */
	public static int openCommissions(ServerLevel l, UUID owner) {
		int n = 0;
		for (var e : Trade.state(l).strings.entrySet()) if (e.getKey().startsWith("comm|") && e.getValue().startsWith(owner.toString())) n++;
		return n;
	}

	// ---- Letters of Credit ------------------------------------------------------------------------------------------------------

	static String creditKey(UUID u) {
		return "credit|" + u;
	}

	public static int balance(ServerLevel l, UUID u) {
		return (int) Trade.state(l).getLong(creditKey(u), 0);
	}

	/** Deposits Dinars at a treasury: held server-side in the player's name (kept through death) and recorded on the Letter of Credit. */
	public static Trade.Result deposit(ServerPlayer p, Identifier faction, int amount) {
		ServerLevel l = (ServerLevel) p.level();
		if (amount <= 0) return Trade.Result.BAD_AMOUNT;
		if (Trade.hostile(p, faction)) return Trade.Result.HOSTILE;
		if (!Shops.take(p, faction, amount)) return Trade.Result.CANT_AFFORD;
		Trade.state(l).putLong(creditKey(p.getUUID()), balance(l, p.getUUID()) + amount);
		updateLetter(p, true);
		return Trade.Result.OK;
	}

	public static Trade.Result withdraw(ServerPlayer p, Identifier faction, int amount) {
		ServerLevel l = (ServerLevel) p.level();
		if (amount <= 0) return Trade.Result.BAD_AMOUNT;
		if (balance(l, p.getUUID()) < amount) return Trade.Result.CANT_AFFORD;
		Trade.state(l).putLong(creditKey(p.getUUID()), balance(l, p.getUUID()) - amount);
		Shops.give(p, faction, amount);
		updateLetter(p, false);
		return Trade.Result.OK;
	}

	/** Keeps the Letter of Credit's lore at the current balance; {@code issue} gives a letter when the player has none (a lost letter is reissued free at any treasury). */
	public static void updateLetter(ServerPlayer p, boolean issue) {
		ServerLevel l = (ServerLevel) p.level();
		ItemStack s = TradeItems.find(p, TradeItems.LETTER_OF_CREDIT);
		if (s == null && issue) {
			s = new ItemStack(TradeItems.LETTER_OF_CREDIT);
			Trade.give(p, s);
			s = TradeItems.find(p, TradeItems.LETTER_OF_CREDIT);
			if (s == null) return;
		}
		if (s == null) return;
		s.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Held in the name of " + p.getGameProfile().name()), Component.literal("Balance: " + balance(l, p.getUUID()) + " Dinars"))));
	}

	// ---- caravans -----------------------------------------------------------------------------------------------------------------

	/** Where the villages lane says Azmar's villages are; null/empty falls back to the castle towns. Set by code or found by reflection ({@code VillagesApi.villageCenters}). */
	public interface VillageProvider { List<BlockPos> villages(ServerLevel level, Identifier faction); }

	public static VillageProvider villageProvider = AzmarTrade::reflectVillages;

	@SuppressWarnings("unchecked")
	static List<BlockPos> reflectVillages(ServerLevel level, Identifier faction) {
		try {
			Object r = Class.forName("dev.elsewhere.kingdom_villages.VillagesApi").getMethod("villageCenters", ServerLevel.class, Identifier.class).invoke(null, level, faction);
			return r instanceof List<?> ? (List<BlockPos>) r : List.of();
		} catch (ReflectiveOperationException | LinkageError e) {
			return List.of();
		}
	}

	public static boolean caravanDay(ServerLevel l) {
		return Math.floorMod(Trade.day(l), (long) CARAVAN_EVERY_DAYS) == 0;
	}

	/** Which rotating stock this caravan sells (the day count divided by 4, round the sets). */
	public static int rotation(ServerLevel l, Identifier faction) {
		int sets = Trade.stock(l, faction).caravan().size();
		return sets == 0 ? 0 : (int) Math.floorMod(Math.floorDiv(Trade.day(l), (long) CARAVAN_EVERY_DAYS), (long) sets);
	}

	public static List<Line> caravanLines(ServerLevel l, Identifier faction) {
		var sets = Trade.stock(l, faction).caravan();
		return sets.isEmpty() ? List.of() : sets.get(rotation(l, faction));
	}

	public static List<Quote> caravanQuotes(ServerPlayer p, Identifier faction) {
		ServerLevel l = (ServerLevel) p.level();
		List<Quote> out = new ArrayList<>();
		for (Line line : caravanLines(l, faction)) {
			if (line.itemOrNull() == null) continue;
			out.add(new Quote(line, postedPrice(p, faction, line), true, "", Trade.remaining(l, p, faction, line)));
		}
		return out;
	}

	public static Trade.Result buyCaravan(ServerPlayer p, Identifier faction, int index) {
		ServerLevel l = (ServerLevel) p.level();
		if (!caravanDay(l)) return Trade.Result.CLOSED;
		Line line = null;
		for (Line x : caravanLines(l, faction)) if (x.index() == index) line = x;
		if (line == null || line.itemOrNull() == null) return Trade.Result.NO_SUCH_LINE;
		if (Trade.hostile(p, faction)) return Trade.Result.HOSTILE;
		if (Trade.remaining(l, p, faction, line) == 0) return Trade.Result.WEEKLY_LIMIT;
		if (!Trade.hasExtras(p, line)) return Trade.Result.CANT_AFFORD;
		if (!Shops.take(p, faction, postedPrice(p, faction, line))) return Trade.Result.CANT_AFFORD;
		Trade.takeExtras(p, line);
		Trade.deliver(p, faction, line);
		return Trade.Result.OK;
	}

	/** Where today's caravan stops: one of the faction's villages (rotating), else one of its castle towns. Null without a realm. */
	public static @Nullable BlockPos caravanSite(ServerLevel l, Identifier faction) {
		long n = Math.floorDiv(Trade.day(l), (long) CARAVAN_EVERY_DAYS);
		List<BlockPos> vs = villageProvider == null ? List.of() : villageProvider.villages(l, faction);
		if (vs != null && !vs.isEmpty()) return vs.get((int) Math.floorMod(n, (long) vs.size()));
		CourtState st = CourtState.peek(l.getServer(), faction);
		if (st == null || st.site == null) return null;
		return CastleLayout.town(st.site, n % 2 == 0);
	}

	public static boolean isCaravan(net.minecraft.world.entity.Entity e) {
		return e.entityTags().contains(CARAVAN_TAG);
	}

	/** Spawns a caravan at {@code at} for {@code faction}: the master, 1-2 Hired Porters (when the villages module is present) and two camels. Returns the members. */
	public static List<net.minecraft.world.entity.Entity> spawnCaravan(ServerLevel l, Identifier faction, BlockPos at) {
		List<net.minecraft.world.entity.Entity> out = new ArrayList<>();
		var role = dev.elsewhere.kingdom_court.api.Diplomacy.Role.CARAVAN_MASTER;
		if (role.type != null) {
			DiplomatNpc m = role.type.create(l, EntitySpawnReason.MOB_SUMMONED);
			if (m != null) {
				m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
				m.setFaction(faction);
				m.setUnit(role.unitId());
				m.setCustomName(Component.translatable("entity.elsewhere." + role.path));
				m.addTag(CARAVAN_TAG);
				m.addTag(TradeEvents.DAY_TAG + Trade.day(l));
				m.addTag(MASTER_TAG);
				m.finalizeSpawn(l, l.getCurrentDifficultyAt(at), EntitySpawnReason.MOB_SUMMONED, null);
				if (l.addFreshEntity(m)) out.add(m);
			}
		}
		for (int i = 0; i < 2; i++) {
			var c = EntityTypes.CAMEL.create(l, EntitySpawnReason.MOB_SUMMONED);
			if (c == null) continue;
			c.snapTo(at.getX() + 0.5 + (i == 0 ? -2.5 : 2.5), at.getY(), at.getZ() + 2.5, 0F, 0F);
			c.addTag(CARAVAN_TAG);
			c.addTag(TradeEvents.DAY_TAG + Trade.day(l));
			c.setPersistenceRequired();
			if (l.addFreshEntity(c)) out.add(c);
		}
		int porters = dev.elsewhere.kingdom.Serfs.caravanPorters(faction, l.getRandom());
		EntityType<?> serf = BuiltInRegistries.ENTITY_TYPE.containsKey(Trade.id("serf")) ? BuiltInRegistries.ENTITY_TYPE.getValue(Trade.id("serf")) : null;
		for (int i = 0; serf != null && i < porters; i++) {
			var e = serf.create(l, EntitySpawnReason.MOB_SUMMONED);
			if (e == null) continue;
			try {
				e.getClass().getField("variant").set(e, "porter");
				e.getClass().getField("faction").set(e, faction.toString());
			} catch (ReflectiveOperationException ex) {
				e.discard();
				continue;
			}
			e.snapTo(at.getX() + 0.5 + (i == 0 ? -1.0 : 1.0), at.getY(), at.getZ() + 4.0, 0F, 0F);
			e.addTag(CARAVAN_TAG);
			e.addTag(TradeEvents.DAY_TAG + Trade.day(l));
			if (e instanceof Mob mob) mob.setPersistenceRequired();
			if (l.addFreshEntity(e)) out.add(e);
		}
		return out;
	}

	public static List<LivingEntity> members(ServerLevel l, BlockPos near, double radius) {
		return l.getEntitiesOfClass(LivingEntity.class, new AABB(near).inflate(radius), e -> e.isAlive() && isCaravan(e));
	}

	/** True when {@code victim} is attacking (or lingering at) a caravan member: it targets one, hit one recently, or an enemy stands within 12 blocks of one. */
	public static boolean attacksCaravan(ServerLevel l, LivingEntity victim) {
		if (isCaravan(victim)) return false;
		boolean hostile = victim instanceof Enemy || victim.getType().getCategory() == MobCategory.MONSTER || victim instanceof dev.elsewhere.kingdom.KingdomSoldier;
		if (!hostile) return false;
		if (victim instanceof dev.elsewhere.kingdom.KingdomSoldier ks && !Factions.hostile(Trade.id("azmar"), ks.factionId())) return false;
		if (victim instanceof Mob m && m.getTarget() != null && isCaravan(m.getTarget())) return true;
		LivingEntity hit = victim.getLastHurtMob();
		if (hit != null && isCaravan(hit) && victim.tickCount - victim.getLastHurtMobTimestamp() < 200) return true;
		return !members(l, victim.blockPosition(), 12).isEmpty();
	}

	/**
	 * A player killed {@code victim}. If it was attacking a caravan within 48 blocks of the Caravan Master (or any member) the master pays 2-6 Dinars and Standing +3 (at most
	 * {@link #BOUNTY_CAP_PER_VISIT} payouts per player per caravan day). Returns the Dinars paid.
	 */
	public static int onKill(ServerLevel l, LivingEntity victim, ServerPlayer killer, Identifier faction, RandomSource rnd) {
		if (!attacksCaravan(l, victim) || Trade.hostile(killer, faction)) return 0;
		if (members(l, killer.blockPosition(), 48).isEmpty()) return 0;
		String key = "carbounty|" + killer.getUUID() + "|" + Trade.day(l);
		TradeState ts = Trade.state(l);
		if (ts.getLong(key, 0) >= BOUNTY_CAP_PER_VISIT) return 0;
		ts.addLong(key, 1);
		int n = BOUNTY_MIN + rnd.nextInt(BOUNTY_MAX - BOUNTY_MIN + 1);
		Shops.give(killer, faction, n);
		Standing.add(l, killer.getUUID(), faction, BOUNTY_STANDING);
		killer.sendOverlayMessage(Component.literal("The caravan master pays you " + n + " Dinars."));
		return n;
	}
}
