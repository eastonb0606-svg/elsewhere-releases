package dev.elsewhere.rivals.mirewick.rewards;

import dev.elsewhere.kingdom_court.CourtDrops;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.rivals.mirewick.MirewickContent;
import dev.elsewhere.rivals.mirewick.boss.Magnate;
import dev.elsewhere.rivals.mirewick.boss.MagnateBoss;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 (s-bossdrops): every Mirewick Magnate (Marrow, Thwaite, Sorrel, Tolliver, Ames, and their heirs) pays its XP and its designed drops
 * whenever it dies, in any context (a court leader, a hand-summoned boss, a duel-mode yield, the release suite's direct spawn). Before this a
 * Magnate outside the court dropped nothing and no Magnate ever dropped XP (HumanBoss sets 0 XP; only the court rolled the faction table).
 * <ul>
 * <li><b>XP</b> ({@link #xp}): {@code MagnateBoss} answers vanilla's always-drop path with it (the Chancellor's Chaperon reads the same
 * number). Whoever holds the Chair pays {@link #RULER_XP}, any other Magnate {@link #LORD_XP} (the boss kit's XP floor: DECISIONS-2.0
 * "every boss always drops XP").</li>
 * <li><b>Faction drops</b> (the Magnate's unique by DropRule, its Seal, the Oathkeeper's Seal, Scrip): the court rolls them for the leader it
 * seats ({@code CourtLife.afterDeath}); every other death rolls the same table here, exactly once. The check runs in an AFTER_DEATH phase
 * ordered before the court's listener, so it reads the seat as the court is about to. The Chair table (Coronet, Standard) stays with
 * {@code MagnateDrops}.</li>
 * <li><b>GEAR.md A5 sources</b> (gear3 items, player kills only, honouring doMobLoot, looked up by id, skipped when gear3 is absent): the
 * Chair-holder Lanternfang Estoc 50%, the Wildfowler (Tolliver) Fowler's Bandolier 30%, the Lord Almoner (Ames) Almoner's Chalice 50%.
 * The Will-o'-Lantern (Chair-holder 30%) is rolled by its own gear3 code.</li>
 * </ul>
 * Event-driven only. Kill switch {@code rival_boss_rewards} ({@code config/elsewhere-overhaul3.json} or
 * {@code -Delsewhere.overhaul3.rival_boss_rewards=false}): back to 2.x (no XP; only the court's own drops).
 */
public final class MirewickRewards {
	private MirewickRewards() {}

	public static final String CONFIG_KEY = "rival_boss_rewards";
	public static final int LORD_XP = 400, RULER_XP = 600;
	public static final Identifier PHASE = MirewickContent.id("boss_rewards_before_court");

	/** One GEAR.md A5 source line: a gear3 item id, its chance per kill and the stack size. */
	public record Extra(String item, float chance, int count) {}

	public static final Map<Magnate, List<Extra>> EXTRAS = new EnumMap<>(Magnate.class);
	/** Whoever holds the Chair when killed, on top of their own lines. */
	public static final List<Extra> CHAIR = List.of(new Extra("mirewick_lanternfang_estoc", 0.5F, 1));

	static {
		EXTRAS.put(Magnate.TOLLIVER, List.of(new Extra("mirewick_fowlers_bandolier", 0.3F, 1)));
		EXTRAS.put(Magnate.AMES, List.of(new Extra("mirewick_almoners_chalice", 0.5F, 1)));
	}

	private static @Nullable Boolean enabled;
	private static boolean inited;
	public static int fallbackRolls, extrasDropped;
	public static List<ItemStack> lastFallback = List.of(), lastExtras = List.of();

	public static void init() {
		if (inited) return;
		inited = true;
		ServerLivingEntityEvents.AFTER_DEATH.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.AFTER_DEATH.register(PHASE, (dead, src) -> {
			if (dead instanceof MagnateBoss b && b.level() instanceof ServerLevel level) onDeath(level, b, src);
		});
	}

	// ---- kill switch ---------------------------------------------------------------------------------------------------------------

	public static boolean enabled() {
		Boolean on = enabled;
		if (on == null) enabled = on = readFlag();
		return on;
	}

	public static void setEnabledForTest(@Nullable Boolean on) {
		enabled = on;
	}

	private static boolean readFlag() {
		String sys = System.getProperty("elsewhere.overhaul3." + CONFIG_KEY);
		if (sys != null) return !"false".equalsIgnoreCase(sys);
		try {
			var f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (java.nio.file.Files.isRegularFile(f)) {
				var o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
				if (o.has(CONFIG_KEY)) return o.get(CONFIG_KEY).getAsBoolean();
			}
		} catch (Exception e) {
			// missing or malformed config keeps the default (on)
		}
		return true;
	}

	// ---- XP ------------------------------------------------------------------------------------------------------------------------

	/** XP this Magnate drops on death; {@code chairHolder} = it holds (or held, at the moment of death) the Chair. 0 with the switch off. */
	public static int xp(MagnateBoss b, boolean chairHolder) {
		if (!enabled()) return 0;
		return chairHolder ? RULER_XP : LORD_XP;
	}

	// ---- drops ---------------------------------------------------------------------------------------------------------------------

	/** True when the court will roll this death's drops itself (the same test as {@code CourtLife.afterDeath}); read before the court's listener runs. */
	public static boolean courtRolls(ServerLevel level, LivingEntity dead) {
		Leaders.Ref ref = Leaders.of(dead);
		if (ref == null) return false;
		Ruler who = Ruler.byKey(ref.slot());
		if (who == null) return false;
		CourtState st = CourtState.get(level, ref.faction());
		UUID want = st.rulerIds.get(who);
		return st.alive(who) && (want == null || want.equals(dead.getUUID()));
	}

	public static @Nullable ServerPlayer killer(LivingEntity dead, DamageSource src) {
		if (src.getEntity() instanceof ServerPlayer p) return p;
		if (dead.getLastHurtByPlayerMemoryTime() > 0 && dead.getLastHurtByPlayer() instanceof ServerPlayer p) return p;
		return null;
	}

	/** The faction-table drops for a death the court does not handle (empty when it does). The seat comes from the tag, else the Magnate's own. */
	public static List<ItemStack> fallbackDrops(ServerLevel level, MagnateBoss b, @Nullable ServerPlayer killer) {
		if (courtRolls(level, b)) return List.of();
		Leaders.Ref ref = Leaders.of(b);
		Ruler slot = Ruler.byKey(ref != null ? ref.slot() : b.m.courtSlot);
		if (slot == null) return List.of();
		CourtState st = CourtState.get(level.getServer(), ref != null ? ref.faction() : MirewickContent.FACTION);
		return CourtDrops.rollRival(level, st, slot, b.position(), killer);
	}

	/** The GEAR.md lines of this Magnate (plus the Chair line when it holds the Chair). */
	public static List<Extra> lines(Magnate m, boolean chairHolder) {
		List<Extra> out = new ArrayList<>(EXTRAS.getOrDefault(m, List.of()));
		if (chairHolder) out.addAll(CHAIR);
		return out;
	}

	public static List<Extra> rollExtraLines(List<Extra> lines, RandomSource rnd) {
		List<Extra> out = new ArrayList<>();
		for (Extra e : lines) if (rnd.nextFloat() < e.chance()) out.add(e);
		return out;
	}

	public static List<ItemStack> rollExtras(List<Extra> lines, RandomSource rnd) {
		List<ItemStack> out = new ArrayList<>();
		for (Extra e : rollExtraLines(lines, rnd)) {
			Item item = item(e.item());
			if (item != null) out.add(new ItemStack(item, e.count()));
		}
		return out;
	}

	public static @Nullable Item item(String path) {
		Identifier id = MirewickContent.id(path);
		return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
	}

	static void onDeath(ServerLevel level, MagnateBoss b, DamageSource src) {
		if (!enabled()) return;
		try {
			ServerPlayer killer = killer(b, src);
			List<ItemStack> fallback = fallbackDrops(level, b, killer);
			if (!fallback.isEmpty()) fallbackRolls++;
			// this listener runs before the court's, so the Chair flag is still the one the Magnate died holding
			List<ItemStack> extras = killer != null && Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_DROPS)) ? rollExtras(lines(b.m, b.chair), level.getRandom()) : List.of();
			extrasDropped += extras.size();
			lastFallback = fallback;
			lastExtras = extras;
			drop(level, b, fallback);
			drop(level, b, extras);
		} catch (RuntimeException e) {
			MirewickContent.LOG.warn("boss rewards failed for {}: {}", b.m.key, e.toString());
		}
	}

	static void drop(ServerLevel level, LivingEntity at, List<ItemStack> items) {
		for (ItemStack s : items) {
			ItemEntity ie = new ItemEntity(level, at.getX(), at.getY() + 0.5, at.getZ(), s);
			ie.setDefaultPickUpDelay();
			level.addFreshEntity(ie);
		}
	}
}
