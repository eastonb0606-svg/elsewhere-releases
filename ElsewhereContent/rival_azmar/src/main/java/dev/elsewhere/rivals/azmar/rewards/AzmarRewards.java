package dev.elsewhere.rivals.azmar.rewards;

import dev.elsewhere.kingdom_court.CourtDrops;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.rivals.azmar.AzmarContent;
import dev.elsewhere.rivals.azmar.boss.AzmarAmir;
import dev.elsewhere.rivals.azmar.boss.AzmarBoss;
import dev.elsewhere.rivals.azmar.boss.AzmarFalconer;
import dev.elsewhere.rivals.azmar.boss.AzmarSultana;
import dev.elsewhere.rivals.azmar.boss.AzmarWazir;
import java.util.ArrayList;
import java.util.List;
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
 * 3.0 (s-bossdrops): every Azmar boss (Sultana, Amir, Grand Wazir, Royal Falconer) pays its XP and its designed drops whenever it dies, in
 * any context (a court leader, a hand-summoned boss, a duel-mode yield, the release suite's direct spawn). Before this an Azmar boss
 * outside the court dropped nothing, and no Azmar boss ever dropped XP (HumanBoss sets 0 XP; only the court rolled the faction table).
 * <ul>
 * <li><b>XP</b> ({@link #xp}): {@code AzmarBoss} answers vanilla's always-drop path with it (the Chancellor's Chaperon reads the same number).
 * The throne (the Sultana, or a Lord crowned Regent) pays {@link #RULER_XP}, a Lord {@link #LORD_XP} (the boss kit's XP floor:
 * DECISIONS-2.0 "every boss always drops XP").</li>
 * <li><b>Faction drops</b> (the boss's unique by DropRule, its Seal, the Oathkeeper's Seal, Dinars; the Sultana's treasury and emeralds): the
 * court rolls them for the leader it seats ({@code CourtLife.afterDeath}); every other death rolls the same table here, exactly once. The
 * check runs in an AFTER_DEATH phase ordered before the court's listener, so it reads the seat as the court is about to.</li>
 * <li><b>GEAR.md A5 sources</b> (gear3 items, player kills only, honouring doMobLoot, looked up by id, skipped when gear3 is absent): Sultana
 * Ka Sigil 30%, Amir Sunscourge 50%, Royal Falconer Falconer's Kilt 50%. (The Glyphmail pair is a Royal Workshop commission, not a drop.)</li>
 * </ul>
 * Event-driven only. Kill switch {@code rival_boss_rewards} ({@code config/elsewhere-overhaul3.json} or
 * {@code -Delsewhere.overhaul3.rival_boss_rewards=false}): back to 2.x (no XP; only the court's own drops).
 */
public final class AzmarRewards {
	private AzmarRewards() {}

	public static final String CONFIG_KEY = "rival_boss_rewards";
	public static final int LORD_XP = 400, RULER_XP = 600;
	public static final Identifier PHASE = AzmarContent.id("boss_rewards_before_court");

	/** One GEAR.md A5 source line: a gear3 item id, its chance per kill and the stack size. */
	public record Extra(String item, float chance, int count) {}

	public static final List<Extra> SULTANA = List.of(new Extra("azmar_ka_sigil", 0.3F, 1));
	public static final List<Extra> AMIR = List.of(new Extra("azmar_sunscourge", 0.5F, 1));
	public static final List<Extra> FALCONER = List.of(new Extra("azmar_falconer_kilt", 0.5F, 1));

	private static @Nullable Boolean enabled;
	private static boolean inited;
	public static int fallbackRolls, extrasDropped;
	public static List<ItemStack> lastFallback = List.of(), lastExtras = List.of();

	public static void init() {
		if (inited) return;
		inited = true;
		ServerLivingEntityEvents.AFTER_DEATH.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		ServerLivingEntityEvents.AFTER_DEATH.register(PHASE, (dead, src) -> {
			if (dead instanceof AzmarBoss b && b.level() instanceof ServerLevel level) onDeath(level, b, src);
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

	public static boolean ruler(AzmarBoss b) {
		return b instanceof AzmarSultana || b.regent;
	}

	public static int xp(AzmarBoss b) {
		if (!enabled()) return 0;
		return ruler(b) ? RULER_XP : LORD_XP;
	}

	// ---- drops ---------------------------------------------------------------------------------------------------------------------

	/** The court seat a boss plays when it is not court-tagged (AzmarBosses binds king = Sultana, marshal = Amir, castellan = Wazir, huntsman = Falconer). */
	public static @Nullable String slotOf(AzmarBoss b) {
		if (b instanceof AzmarSultana) return "king";
		if (b instanceof AzmarAmir) return "marshal";
		if (b instanceof AzmarWazir) return "castellan";
		if (b instanceof AzmarFalconer) return "huntsman";
		return null;
	}

	public static List<Extra> extras(AzmarBoss b) {
		if (b instanceof AzmarSultana) return SULTANA;
		if (b instanceof AzmarAmir) return AMIR;
		if (b instanceof AzmarFalconer) return FALCONER;
		return List.of();
	}

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

	/** The faction-table drops for a death the court does not handle (empty when it does). */
	public static List<ItemStack> fallbackDrops(ServerLevel level, AzmarBoss b, @Nullable ServerPlayer killer) {
		if (courtRolls(level, b)) return List.of();
		Leaders.Ref ref = Leaders.of(b);
		String key = ref != null ? ref.slot() : slotOf(b);
		Ruler slot = key == null ? null : Ruler.byKey(key);
		if (slot == null) return List.of();
		CourtState st = CourtState.get(level.getServer(), ref != null ? ref.faction() : AzmarContent.FACTION);
		return CourtDrops.rollRival(level, st, slot, b.position(), killer);
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
		Identifier id = AzmarContent.id(path);
		return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
	}

	static void onDeath(ServerLevel level, AzmarBoss b, DamageSource src) {
		if (!enabled()) return;
		try {
			ServerPlayer killer = killer(b, src);
			List<ItemStack> fallback = fallbackDrops(level, b, killer);
			if (!fallback.isEmpty()) fallbackRolls++;
			List<ItemStack> extras = killer != null && Boolean.TRUE.equals(level.getGameRules().get(GameRules.MOB_DROPS)) ? rollExtras(extras(b), level.getRandom()) : List.of();
			extrasDropped += extras.size();
			lastFallback = fallback;
			lastExtras = extras;
			drop(level, b, fallback);
			drop(level, b, extras);
		} catch (RuntimeException e) {
			AzmarContent.LOG.warn("boss rewards failed for {}: {}", BuiltInRegistries.ENTITY_TYPE.getKey(b.getType()), e.toString());
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
