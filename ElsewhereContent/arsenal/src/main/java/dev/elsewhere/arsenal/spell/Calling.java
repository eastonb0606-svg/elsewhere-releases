package dev.elsewhere.arsenal.spell;

import com.mojang.serialization.Codec;
import dev.elsewhere.arsenal.ArsenalContent;
import dev.elsewhere.arsenal.ArsenalStats;
import dev.elsewhere.arsenal.Overhaul3;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;

/**
 * Callings. 3.0 (GEAR.md A1/A2, switch {@code callings}): nothing to pick. Callings are read from what the player wears and holds, only
 * when equipment changes: Primary = the path with the most worn Leaning pieces (at least 2; a tie goes to the main-hand calling item's
 * path), Secondary = the next path with a piece, else the offhand or trinket path; the rest are Open; no Primary = Unattuned. A change
 * settles after {@link #SETTLE_TICKS} (a stored timestamp, no tick) so mid-fight swaps don't pay. Mastery {@code M = min(C, B + 0.05 *
 * pieces)}; everything derives from M on demand. With the switch off, the 2.1 piece-count rule (ARSENAL.md §3b) is used.
 */
public final class Calling {
	private Calling() {}

	public static final TagKey<Item> CASTER_ARMOR = TagKey.create(Registries.ITEM, ArsenalContent.id("caster_armor"));
	public static final TagKey<Item> SUMMONER_ARMOR = TagKey.create(Registries.ITEM, ArsenalContent.id("summoner_armor"));
	public static final TagKey<Item> VANGUARD_ARMOR = TagKey.create(Registries.ITEM, ArsenalContent.id("calling/vanguard"));
	public static final TagKey<Item> MARKSMAN_ARMOR = TagKey.create(Registries.ITEM, ArsenalContent.id("calling/marksman"));
	public static final TagKey<Item> CASTER_ARMOR_V3 = TagKey.create(Registries.ITEM, ArsenalContent.id("calling/caster"));
	public static final TagKey<Item> SUMMONER_ARMOR_V3 = TagKey.create(Registries.ITEM, ArsenalContent.id("calling/summoner"));
	public static final TagKey<Item> VANGUARD_WEAPON = TagKey.create(Registries.ITEM, ArsenalContent.id("calling_weapon/vanguard"));
	public static final TagKey<Item> MARKSMAN_WEAPON = TagKey.create(Registries.ITEM, ArsenalContent.id("calling_weapon/marksman"));
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	// ---------------------------------------------------------------- 3.0 constants (GEAR.md A1)
	public static final float LEAN_PER_PIECE = 0.05F;
	public static final float STARTER_MIN_MASTERY = 0.60F;
	public static final float FOCUS_MIN_MASTERY = 0.40F;
	public static final int PRIMARY_MIN_PIECES = 2;
	/** A derived change takes effect this long after the equipment that caused it (30 s), so swapping mid-fight doesn't pay. */
	public static final long SETTLE_TICKS = 600L;

	public enum Path {
		VANGUARD, MARKSMAN, CASTER, SUMMONER;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Path byId(String id) {
			if (id == null) return null;
			for (Path p : values()) if (p.id().equalsIgnoreCase(id.trim())) return p;
			return null;
		}

		/** True if a worn stack leans toward this path (its Leaning tag, or the 3.0 {@code calling/<path>} tag for Caster/Summoner). */
		public boolean leans(ItemStack s) {
			if (s.isEmpty()) return false;
			if (s.is(armorTag())) return true;
			return (this == CASTER && s.is(CASTER_ARMOR_V3)) || (this == SUMMONER && s.is(SUMMONER_ARMOR_V3));
		}

		public TagKey<Item> armorTag() {
			return switch (this) {
				case VANGUARD -> VANGUARD_ARMOR;
				case MARKSMAN -> MARKSMAN_ARMOR;
				case CASTER -> CASTER_ARMOR;
				case SUMMONER -> SUMMONER_ARMOR;
			};
		}
	}

	public enum Rank {
		PRIMARY(1.00F, 1.00F), SECONDARY(0.60F, 0.80F), OPEN(0.25F, 0.45F), UNATTUNED(0.40F, 0.60F);

		public final float base, ceiling;

		Rank(float base, float ceiling) {
			this.base = base;
			this.ceiling = ceiling;
		}
	}

	public static final TagKey<Item> CASTER_ITEM = TagKey.create(Registries.ITEM, ArsenalContent.id("calling_weapon/caster"));
	public static final TagKey<Item> SUMMONER_ITEM = TagKey.create(Registries.ITEM, ArsenalContent.id("calling_weapon/summoner"));
	public static final TagKey<Item> CASTER_TRINKET = TagKey.create(Registries.ITEM, ArsenalContent.id("caster_trinket"));

	/** Settled {primary, secondary} (path ordinal or -1) plus a pending pair and when it was seen; "p,s,pp,ps,since". Kept on death. */
	public record State(int primary, int secondary, int pendP, int pendS, long since) {
		static State parse(String s) {
			try {
				String[] a = s.split(",");
				return new State(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]), Long.parseLong(a[4]));
			} catch (RuntimeException e) {
				return null;
			}
		}

		String write() {
			return primary + "," + secondary + "," + pendP + "," + pendS + "," + since;
		}

		boolean pending() {
			return pendP != -2;
		}
	}

	public static final AttachmentType<String> STATE = AttachmentRegistry.create(ArsenalContent.id("calling_state"), b -> b.persistent(Codec.STRING).copyOnDeath());

	/** The settled state; promotes a pending change whose settle time has passed (server side, on demand). */
	public static State state(Player p) {
		String raw = p.getAttached(STATE);
		State st = raw == null ? null : State.parse(raw);
		if (st != null && st.pending() && p.level().getGameTime() >= st.since + SETTLE_TICKS && p instanceof ServerPlayer) {
			st = new State(st.pendP, st.pendS, -2, -2, 0);
			p.setAttached(STATE, st.write());
			sync((ServerPlayer) p, st);
		}
		return st;
	}

	/** Path of a calling item (weapon, staff, crook, trinket), or null. */
	public static Path itemPath(ItemStack s) {
		if (s.isEmpty()) return null;
		if (s.is(VANGUARD_WEAPON)) return Path.VANGUARD;
		if (s.is(MARKSMAN_WEAPON)) return Path.MARKSMAN;
		if (s.is(CASTER_ITEM) || s.is(CASTER_TRINKET)) return Path.CASTER;
		if (s.is(SUMMONER_ITEM)) return Path.SUMMONER;
		return null;
	}

	/** {primary, secondary} read from equipment right now (GEAR.md A1 rule). */
	public static int[] derive(Player p) {
		int[] n = new int[4];
		for (Path path : Path.values()) n[path.ordinal()] = pieces(p, path);
		Path main = itemPath(p.getMainHandItem()), off = itemPath(p.getOffhandItem());
		Path trinket = null;
		for (Item it : dev.elsewhere.arsenal.trinket.Trinkets.active(p)) {
			trinket = itemPath(new ItemStack(it));
			if (trinket != null) break;
		}
		int prim = -1;
		for (Path path : Path.values()) {
			int c = n[path.ordinal()];
			if (c < PRIMARY_MIN_PIECES) continue;
			if (prim < 0 || c > n[prim] || (c == n[prim] && main == path)) prim = path.ordinal();
		}
		if (prim < 0) return new int[] {-1, -1};
		int sec = -1;
		for (Path path : Path.values()) {
			int o = path.ordinal(), c = n[o];
			if (o == prim || c < 1) continue;
			if (sec < 0 || c > n[sec] || (c == n[sec] && (main == path || off == path))) sec = o;
		}
		if (sec < 0 && off != null && off.ordinal() != prim) sec = off.ordinal();
		if (sec < 0 && trinket != null && trinket.ordinal() != prim) sec = trinket.ordinal();
		if (sec < 0 && main != null && main.ordinal() != prim) sec = main.ordinal();
		return new int[] {prim, sec};
	}

	/** Re-reads the Callings from equipment (equipment change, join). {@code immediate} skips the settle time (join, self-test). */
	public static void recompute(ServerPlayer p, boolean immediate) {
		if (!Overhaul3.CALLINGS) return;
		int[] d = derive(p);
		State st = state(p);
		State next;
		if (st == null || immediate) next = new State(d[0], d[1], -2, -2, 0);
		else if (st.primary == d[0] && st.secondary == d[1]) next = st.pending() ? new State(d[0], d[1], -2, -2, 0) : st;
		else if (st.pending() && st.pendP == d[0] && st.pendS == d[1]) next = st;
		else next = new State(st.primary, st.secondary, d[0], d[1], p.level().getGameTime());
		if (st == null || !next.equals(st)) p.setAttached(STATE, next.write());
		if (st == null || st.primary != next.primary || st.secondary != next.secondary) sync(p, next);
	}

	/** Client copy of the settled pair (for tooltips and the Calling panel); the client keeps it in the same attachment, unsynced. */
	public static void sync(ServerPlayer p, State st) {
		if (st != null && ServerPlayNetworking.canSend(p, CallingPayload.TYPE)) ServerPlayNetworking.send(p, new CallingPayload(st.primary, st.secondary));
	}

	/** Client side: store a received pair on the local player. */
	public static void receive(Player local, CallingPayload pl) {
		if (local != null) local.setAttached(STATE, new State(pl.primary(), pl.secondary(), -2, -2, 0).write());
	}

	/** Ticks until a pending change takes effect (0 = none pending). */
	public static long ticksUntilSettled(Player p) {
		State st = state(p);
		if (st == null || !st.pending()) return 0;
		return Math.max(0, st.since + SETTLE_TICKS - p.level().getGameTime());
	}

	// ---------------------------------------------------------------- legacy 2.1 API (piece counts)

	/** Worn Leaning pieces for a path (3.0). */
	public static int pieces(Player p, Path path) {
		int n = 0;
		for (EquipmentSlot s : ARMOR) if (path.leans(p.getItemBySlot(s))) n++;
		return n;
	}

	public static int count(Player p, TagKey<Item> tag) {
		int n = 0;
		for (EquipmentSlot s : ARMOR) if (p.getItemBySlot(s).is(tag)) n++;
		return n;
	}

	/** Caster Calling 0–4 (worn caster pieces). */
	public static int caster(Player p) {
		return count(p, CASTER_ARMOR);
	}

	/** Summoner Calling 0–4 (worn summoner pieces). */
	public static int summoner(Player p) {
		return count(p, SUMMONER_ARMOR);
	}

	/** Caster Calling for a cast; starter items (Amethyst Wand) count as at least 2. */
	public static int casterFor(Player p, boolean starter) {
		int c = caster(p);
		return starter ? Math.max(ArsenalStats.STARTER_MIN_CALLING, c) : c;
	}

	public static float spellDamage(int calling) {
		return ArsenalStats.CALLING_SPELL_DAMAGE[Math.max(0, Math.min(4, calling))];
	}

	public static float spellCost(int calling) {
		return ArsenalStats.CALLING_SPELL_COST[Math.max(0, Math.min(4, calling))];
	}

	// ---------------------------------------------------------------- 3.0 API (GEAR.md A2)

	public static Rank rank(Player p, Path path) {
		State st = p == null ? null : state(p);
		if (st == null || st.primary < 0) return Rank.UNATTUNED;
		if (st.primary == path.ordinal()) return Rank.PRIMARY;
		if (st.secondary == path.ordinal()) return Rank.SECONDARY;
		return Rank.OPEN;
	}

	/** M = min(C, B + 0.05 * pieces) in 0..1. */
	public static float mastery(Player p, Path path) {
		Rank r = rank(p, path);
		int pieces = p == null ? 0 : pieces(p, path);
		return masteryOf(r, pieces);
	}

	public static float masteryOf(Rank r, int pieces) {
		return Math.min(r.ceiling, r.base + LEAN_PER_PIECE * pieces);
	}

	/** D(M) = 0.90 + 0.25 M. */
	public static float damageOf(float m) {
		return 0.90F + 0.25F * m;
	}

	/** Calling damage multiplier for calling-tagged items (1.0 when the switch is off). */
	public static float damageMult(Player p, Path path) {
		if (!Overhaul3.CALLINGS) return 1F;
		return damageOf(mastery(p, path));
	}

	/** As {@link #damageMult(Player, Path)}, but 1.0 against players (PvP stays calling-neutral). */
	public static float damageMult(Player p, Path path, Entity target) {
		return target instanceof Player ? 1F : damageMult(p, path);
	}

	/** Hybrid strength H = min(1, min(Ma, Mb) / 0.60). */
	public static float hybrid(Player p, Path a, Path b) {
		return Math.min(1F, Math.min(mastery(p, a), mastery(p, b)) / 0.60F);
	}

	private static float casterM(Player p, boolean starter) {
		float m = mastery(p, Path.CASTER);
		return starter ? Math.max(STARTER_MIN_MASTERY, m) : m;
	}

	/** Spell damage multiplier (replaces spellDamage(casterFor(..))). */
	public static float spellMult(Player p, boolean starter) {
		if (!Overhaul3.CALLINGS) return spellDamage(casterFor(p, starter));
		return damageOf(casterM(p, starter));
	}

	/** Glimmer cost multiplier: 1.10 - 0.20 M. */
	public static float costMult(Player p, boolean starter) {
		if (!Overhaul3.CALLINGS) return ArsenalStats.CALLING_SPELL_COST[Math.max(0, Math.min(4, casterFor(p, starter)))];
		return 1.10F - 0.20F * casterM(p, starter);
	}

	/** Max Glimmer added by Caster mastery: 20 (M - 0.40), never negative (Unattuned with no caster armor = 2.1 baseline). */
	public static float maxGlimmerBonus(Player p) {
		if (!Overhaul3.CALLINGS) return 0F;
		return Math.max(0F, 20F * (mastery(p, Path.CASTER) - 0.40F));
	}

	/** Glimmer regen multiplier from Caster mastery: 1 + 0.5 (M - 0.40), never below 1. */
	public static float regenMult(Player p) {
		if (!Overhaul3.CALLINGS) return 1F;
		return 1F + Math.max(0F, 0.5F * (mastery(p, Path.CASTER) - 0.40F));
	}

	/** Summoner mastery, or -1 when the switch is off (the summoner module then keeps its 2.1 tables). Soft-linked by CallingStub. */
	public static float summonerMastery(Player p) {
		if (!Overhaul3.CALLINGS) return -1F;
		return mastery(p, Path.SUMMONER);
	}

	/** Minion slot cap from Summoner M (before slot gear): 1 + floor(3 (M - 0.25) / 0.75), 1..4. */
	public static int slotCapOf(float m) {
		return Math.max(1, Math.min(4, 1 + (int) Math.floor(3F * (m - 0.25F) / 0.75F + 1e-4F)));
	}

	public static int minionSlotCap(Player p) {
		return Overhaul3.CALLINGS ? slotCapOf(mastery(p, Path.SUMMONER)) : 4;
	}

	/** Minion battle timer 30 + 30 M seconds. */
	public static int battleTimerTicks(Player p) {
		return Math.round(20F * (30F + 30F * mastery(p, Path.SUMMONER)));
	}

	/** Glimmer reserved per minion: 6 - 2 M. */
	public static float minionReserve(Player p) {
		if (!Overhaul3.CALLINGS) return ArsenalStats.CALLING_MINION_RESERVE[Math.max(0, Math.min(4, summoner(p)))];
		return 6F - 2F * mastery(p, Path.SUMMONER);
	}

	public static String name(Path p) {
		String id = p.id();
		return Character.toUpperCase(id.charAt(0)) + id.substring(1);
	}

	// ---------------------------------------------------------------- Vanguard weapon multiplier (switch calling_weapons)

	/** Vanguard on the full enchanted melee damage {@code amount}; 1.0 when no Vanguard weapon is held or the target is a player. */
	public static float meleeScaled(Player p, Entity target, float amount, boolean vanguardWeaponHeld) {
		return vanguardWeaponHeld ? amount * damageMult(p, Path.VANGUARD, target) : amount;
	}

	/** Arrow tag prefix: the Marksman multiplier fixed when the arrow was loosed (applied at hit time, never to players). */
	public static final String MARK_TAG = "elsewhere_mk:";

	/**
	 * Server damage entry (LivingEntityCallingMixin): Vanguard multiplies the FULL melee damage (weapon + Sharpness + crit),
	 * Marksman the full arrow damage; both are 1.0 against players (PvP stays calling-neutral, GEAR.md A1).
	 */
	public static float scaleHurt(LivingEntity target, DamageSource source, float amount) {
		if (!Overhaul3.CALLING_WEAPONS || target instanceof Player || amount <= 0F) return amount;
		Entity owner = source.getEntity();
		if (!(owner instanceof ServerPlayer p)) return amount;
		Entity direct = source.getDirectEntity();
		if (direct == p) {
			if (!source.is(DamageTypes.PLAYER_ATTACK)) return amount;
			return meleeScaled(p, target, amount, p.getMainHandItem().is(VANGUARD_WEAPON));
		}
		if (direct instanceof AbstractArrow arrow && !(direct instanceof ThrownTrident)) {
			for (String t : arrow.entityTags()) {
				if (!t.startsWith(MARK_TAG)) continue;
				try {
					return amount * Float.parseFloat(t.substring(MARK_TAG.length()));
				} catch (NumberFormatException e) {
					return amount;
				}
			}
		}
		return amount;
	}

	/** Marksman multiplier for an arrow a player looses (1.0 unless a calling-tagged ranged weapon is in hand). */
	public static float marksmanMult(Player p) {
		if (!Overhaul3.CALLING_WEAPONS) return 1F;
		if (!p.getMainHandItem().is(MARKSMAN_WEAPON) && !p.getOffhandItem().is(MARKSMAN_WEAPON)) return 1F;
		return damageMult(p, Path.MARKSMAN);
	}

	public static void init() {
		if (!Overhaul3.CALLINGS) return;
		CallingCommand.init();
		PayloadTypeRegistry.clientboundPlay().register(CallingPayload.TYPE, CallingPayload.CODEC);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> sync(newP, state(newP)));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((p, from, to) -> sync(p, state(p)));
		ServerEntityEvents.EQUIPMENT_CHANGE.register((e, slot, from, to) -> {
			if (e instanceof ServerPlayer p) {
				recompute(p, false);
				}
		});
		ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> {
			recompute(h.player, true);
			sync(h.player, state(h.player));
		});
	}
}
