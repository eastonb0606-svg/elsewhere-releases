package dev.elsewhere.rivals.hrafnholt.rewards;

import dev.elsewhere.kingdom_court.CourtDrops;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.rivals.hrafnholt.HrafnholtContent;
import dev.elsewhere.rivals.hrafnholt.boss.Hrafn;
import dev.elsewhere.rivals.hrafnholt.boss.HrafnholtBoss;
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
 * 3.0 (s-bossdrops): every Hrafnholt boss, the six seats and the five Risen champions, pays its XP and its designed drops whenever it
 * dies, in any context (a court leader, a hand-summoned boss, a duel-mode yield, the release suite's direct spawn).
 * <ul>
 * <li><b>XP</b> ({@link #xp}): {@code HrafnholtBoss} answers vanilla's always-drop path with it, so the orbs fall on every death and the
 * Chancellor's Chaperon bonus reads the same number. The throne (High Jarl, or the champion holding the ruler bonus) pays
 * {@link #RULER_XP}, a champion {@link #LORD_XP} (the boss kit's XP floor: DECISIONS-2.0 "every boss always drops XP").</li>
 * <li><b>Faction drops</b> (the seat's unique by DropRule, its Seal, the Oathkeeper's Seal, Hacksilver; the Jarl's treasury): the court
 * rolls them for the leader it seats ({@code CourtLife.afterDeath}). Every other death (an untracked boss, a stale leader the court no
 * longer seats) rolls the same table here, exactly once. The check runs in an AFTER_DEATH phase ordered before the court's listener, so
 * it reads the seat as the court is about to.</li>
 * <li><b>GEAR.md A5 sources</b> (gear3 items, player kills only, honouring doMobLoot, looked up by id and skipped when gear3 is absent):
 * Hallwarden: Hearthguard helm, greaves and boots 50% each, Skjaldborg Round-Shield 30%; Gudrun Ulfhednar: Ulfhednar mask and
 * breeches 50% each; High Jarl: Huginn's Bite 30%; Weapon-Skald: Runeseax 50%. The Risen champion of a seat rolls the seat's lines. The
 * Hurlbat (Huntmaster) and the Wolf-Call Horn (Gudrun) are rolled by their own gear3 code.</li>
 * </ul>
 * Event-driven only (one AFTER_DEATH listener that returns at once for anything that is not a Hrafnholt boss). Kill switch
 * {@code rival_boss_rewards} ({@code config/elsewhere-overhaul3.json} or {@code -Delsewhere.overhaul3.rival_boss_rewards=false}): no XP, no
 * gear3 lines, and the 2.x untracked roll in {@code HrafnholtBoss.onDefeated} comes back.
 */
public final class HrafnholtRewards {
	private HrafnholtRewards() {}

	public static final String CONFIG_KEY = "rival_boss_rewards";
	/** XP of a champion (a Lord seat or a Risen champion) and of the throne. */
	public static final int LORD_XP = 400, RULER_XP = 600;
	/** The AFTER_DEATH phase this class listens in: before {@link Event#DEFAULT_PHASE}, where the court rolls its leaders' drops. */
	public static final Identifier PHASE = HrafnholtContent.id("boss_rewards_before_court");

	/** One GEAR.md A5 source line: a gear3 item id, its chance per kill and the stack size. */
	public record Extra(String item, float chance, int count) {}

	public static final Map<Hrafn, List<Extra>> EXTRAS = new EnumMap<>(Hrafn.class);

	static {
		EXTRAS.put(Hrafn.HALLWARDEN, List.of(new Extra("hrafnholt_hearthguard_helm", 0.5F, 1), new Extra("hrafnholt_hearthguard_greaves", 0.5F, 1),
			new Extra("hrafnholt_hearthguard_boots", 0.5F, 1), new Extra("hrafnholt_skjaldborg_shield", 0.3F, 1)));
		EXTRAS.put(Hrafn.CHIEF, List.of(new Extra("hrafnholt_ulfhednar_mask", 0.5F, 1), new Extra("hrafnholt_ulfhednar_breeches", 0.5F, 1)));
		EXTRAS.put(Hrafn.JARL, List.of(new Extra("hrafnholt_huginns_bite", 0.3F, 1)));
		EXTRAS.put(Hrafn.SKALD, List.of(new Extra("hrafnholt_runeseax", 0.5F, 1)));
	}

	private static @Nullable Boolean enabled;
	private static boolean inited;
	/** Test counters: untracked or stale deaths this class rolled the faction table for, and gear3 lines dropped. */
	public static int fallbackRolls, extrasDropped;
	/** Test hook: what the last death handled here dropped (faction-table fallback, then gear3 lines). */
	public static List<ItemStack> lastFallback = List.of(), lastExtras = List.of();

	public static void init() {
		if (inited) return;
		inited = true;
		ServerLivingEntityEvents.AFTER_DEATH.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.AFTER_DEATH.register(PHASE, (dead, src) -> {
			if (dead instanceof HrafnholtBoss b && b.level() instanceof ServerLevel level) onDeath(level, b, src);
		});
	}

	// ---- kill switch ---------------------------------------------------------------------------------------------------------------

	public static boolean enabled() {
		Boolean on = enabled;
		if (on == null) enabled = on = readFlag();
		return on;
	}

	/** Self-test hook: force the switch (null = read the config again). */
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

	/** The throne: the High Jarl's seat, or whoever holds the ruler bonus now (a successor champion). */
	public static boolean ruler(HrafnholtBoss b) {
		return (b.h == Hrafn.JARL && !b.isRisen()) || b.holder;
	}

	/** XP this boss drops on death (0 with the switch off: 2.x). */
	public static int xp(HrafnholtBoss b) {
		if (!enabled()) return 0;
		return ruler(b) ? RULER_XP : LORD_XP;
	}

	// ---- drops ---------------------------------------------------------------------------------------------------------------------

	/**
	 * True when the court will roll this death's drops itself: the same test as {@code CourtLife.afterDeath} (a tagged leader whose seat is
	 * alive and held by this entity, or not bound to another one). Read before the court's listener runs.
	 */
	public static boolean courtRolls(ServerLevel level, LivingEntity dead) {
		Leaders.Ref ref = Leaders.of(dead);
		if (ref == null) return false;
		Ruler who = Ruler.byKey(ref.slot());
		if (who == null) return false;
		CourtState st = CourtState.get(level, ref.faction());
		UUID want = st.rulerIds.get(who);
		return st.alive(who) && (want == null || want.equals(dead.getUUID()));
	}

	/** The player credited with the kill: the attacker, else the player who last hurt the boss (vanilla's 5-second memory). */
	public static @Nullable ServerPlayer killer(LivingEntity dead, DamageSource src) {
		if (src.getEntity() instanceof ServerPlayer p) return p;
		if (dead.getLastHurtByPlayerMemoryTime() > 0 && dead.getLastHurtByPlayer() instanceof ServerPlayer p) return p;
		return null;
	}

	/** The faction-table drops for a death the court does not handle (empty when it does). The seat comes from the tag, else the boss's own slot. */
	public static List<ItemStack> fallbackDrops(ServerLevel level, HrafnholtBoss b, @Nullable ServerPlayer killer) {
		if (courtRolls(level, b)) return List.of();
		Leaders.Ref ref = Leaders.of(b);
		Ruler slot = Ruler.byKey(ref != null ? ref.slot() : b.h.courtSlot);
		if (slot == null) return List.of();
		CourtState st = CourtState.get(level.getServer(), ref != null ? ref.faction() : HrafnholtContent.FACTION);
		return CourtDrops.rollRival(level, st, slot, b.position(), killer);
	}

	/** The GEAR.md lines of a seat that hit on one kill (pure, one draw per line). */
	public static List<Extra> rollExtraLines(Hrafn h, RandomSource rnd) {
		List<Extra> out = new ArrayList<>();
		for (Extra e : EXTRAS.getOrDefault(h, List.of())) if (rnd.nextFloat() < e.chance()) out.add(e);
		return out;
	}

	/** The GEAR.md lines of a seat rolled once, as stacks (items that are not registered, e.g. gear3 switched off, are skipped). */
	public static List<ItemStack> rollExtras(Hrafn h, RandomSource rnd) {
		List<ItemStack> out = new ArrayList<>();
		for (Extra e : rollExtraLines(h, rnd)) {
			Item item = item(e.item());
			if (item != null) out.add(new ItemStack(item, e.count()));
		}
		return out;
	}

	public static @Nullable Item item(String path) {
		Identifier id = HrafnholtContent.id(path);
		return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
	}

	static void onDeath(ServerLevel level, HrafnholtBoss b, DamageSource src) {
		if (!enabled()) return;
		try {
			ServerPlayer killer = killer(b, src);
			List<ItemStack> fallback = fallbackDrops(level, b, killer);
			if (!fallback.isEmpty()) fallbackRolls++;
			List<ItemStack> extras = killer != null && Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_DROPS)) ? rollExtras(b.h, level.getRandom()) : List.of();
			extrasDropped += extras.size();
			lastFallback = fallback;
			lastExtras = extras;
			drop(level, b, fallback);
			drop(level, b, extras);
		} catch (RuntimeException e) {
			HrafnholtContent.LOG.warn("boss rewards failed for {}: {}", b.h.key, e.toString());
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
