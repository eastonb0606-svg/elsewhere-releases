package dev.elsewhere.duelist.boss;

import dev.elsewhere.duelist.kit.Kit;
import dev.elsewhere.duelist.kit.Move;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Per-boss data for a {@link HumanBoss}: its numbers, its loadout, its telegraph look and the behaviours it opts into.
 * <b>Nothing is on by default.</b> A behaviour (circle-strafe, sprint-jump crit, shield, weapon swap, bow, crossbow, eat,
 * drink, pearl, temporary blocks) happens only if the builder is given its {@link Behaviours} record, which carries all of
 * that behaviour's numbers. <b>No number has a default either:</b> hp, walk speed, the swing (raw, interval, wind-up), reach,
 * the phase thresholds, the damage caps, the melee weapon and the telegraph style (particle, colour, sound) are all required.
 * Build with {@link #builder(String)}; {@link Builder#build()} names whatever is missing and validates the telegraph rule and the caps.
 */
public final class DuelistProfile {
	public final String id;
	public final boolean ruler;
	public final double maxHp;
	public final double walkSpeed;
	public final float swingRaw;
	public final int swingInterval, swingWindup;
	public final double reach;
	public final Supplier<ItemStack> melee;
	public final @Nullable Supplier<ItemStack> thrown, ranged;
	public final @Nullable Supplier<ItemStack> food, potion;
	public final double[] phases;
	public final List<SignatureMove> signatures;
	public final BossEvent.BossBarColor barColor;
	public final boolean duelMode, noHeal;
	public final CourtBonus court;
	public final @Nullable FrenzyRage frenzy;
	public final TelegraphStyle style;
	/** Caps for one hit, raw (before the victim's armor): ordinary hits and signature hits. */
	public final float capMinor, capSignature;
	public final PhaseController.@Nullable LethalHook lastStand;
	public final int lastStandMax;
	/** The {@code boss_identity} id this profile was built from (default: the profile id). */
	public final String identity;
	/** Skin path under {@code textures/entity/} (default: the profile id). */
	public final String skin;

	public final Behaviours.@Nullable Strafe strafe;
	public final Behaviours.@Nullable SprintCrit crit;
	public final Behaviours.@Nullable Shield shield;
	public final Behaviours.@Nullable Swap swap;
	public final Behaviours.@Nullable Bow bow;
	public final Behaviours.@Nullable Crossbow crossbow;
	public final Behaviours.@Nullable Eat eat;
	public final Behaviours.@Nullable Drink drink;
	public final Behaviours.@Nullable Pearl pearl;
	public final Behaviours.@Nullable TempBlocks blocks;
	public final Behaviours.@Nullable ShieldBreak shieldBreak;

	private DuelistProfile(Builder b) {
		this.id = b.id;
		this.ruler = b.ruler;
		this.maxHp = b.maxHp;
		this.walkSpeed = b.walkSpeed;
		this.swingRaw = b.swingRaw;
		this.swingInterval = b.swingInterval;
		this.swingWindup = b.swingWindup;
		this.reach = b.reach;
		this.melee = b.melee;
		this.thrown = b.thrown;
		this.ranged = b.ranged;
		this.food = b.food;
		this.potion = b.potion;
		this.phases = b.phases;
		this.signatures = List.copyOf(b.signatures);
		this.barColor = b.barColor;
		this.duelMode = b.duelMode;
		this.noHeal = b.noHeal;
		this.court = b.court;
		this.frenzy = b.frenzy;
		this.style = b.style;
		this.capMinor = b.capMinor;
		this.capSignature = b.capSignature;
		this.lastStand = b.lastStand;
		this.lastStandMax = b.lastStandMax;
		this.identity = b.identity == null ? b.id : b.identity;
		this.skin = b.skin == null ? b.id : b.skin;
		this.strafe = b.strafe;
		this.crit = b.crit;
		this.shield = b.shield;
		this.swap = b.swap;
		this.bow = b.bow;
		this.crossbow = b.crossbow;
		this.eat = b.eat;
		this.drink = b.drink;
		this.pearl = b.pearl;
		this.blocks = b.blocks;
		this.shieldBreak = b.shieldBreak;
	}

	/** True if this boss opted into the behaviour. */
	public boolean has(Move m) {
		return switch (m) {
			case STRAFE -> strafe != null;
			case CRIT -> crit != null;
			case SHIELD -> shield != null;
			case SWAP -> swap != null;
			case BOW -> bow != null;
			case CROSSBOW -> crossbow != null;
			case EAT -> eat != null;
			case DRINK -> drink != null;
			case PEARL -> pearl != null;
			case BLOCKS -> blocks != null;
		};
	}

	/** Every behaviour this boss opted into. */
	public EnumSet<Move> opted() {
		EnumSet<Move> s = EnumSet.noneOf(Move.class);
		for (Move m : Move.values()) if (has(m)) s.add(m);
		return s;
	}

	/** The ids of the opted-in audited behaviours, exactly as a {@code boss_identity} "generic" list names them. */
	public Set<String> genericIds() {
		Set<String> s = new LinkedHashSet<>();
		for (Move m : opted()) if (m.generic()) s.add(m.id());
		return s;
	}

	public static Builder builder(String id) {
		return new Builder(id);
	}

	public static final class Builder {
		private final String id;
		private boolean ruler;
		private double maxHp = -1, walkSpeed = -1;
		private float swingRaw = -1F;
		private int swingInterval = -1, swingWindup = -1;
		private double reach = -1;
		private @Nullable Supplier<ItemStack> melee, food, potion;
		private @Nullable Supplier<ItemStack> thrown, ranged;
		private double @Nullable [] phases;
		private final List<SignatureMove> signatures = new ArrayList<>();
		private BossEvent.BossBarColor barColor = BossEvent.BossBarColor.RED;
		private boolean duelMode = true, noHeal;
		private CourtBonus court = CourtBonus.NONE;
		private @Nullable FrenzyRage frenzy;
		private @Nullable TelegraphStyle style;
		private float capMinor = -1F, capSignature = -1F;
		private PhaseController.@Nullable LethalHook lastStand;
		private int lastStandMax = 1;
		private @Nullable String identity, skin;
		private Behaviours.@Nullable Strafe strafe;
		private Behaviours.@Nullable SprintCrit crit;
		private Behaviours.@Nullable Shield shield;
		private Behaviours.@Nullable Swap swap;
		private Behaviours.@Nullable Bow bow;
		private Behaviours.@Nullable Crossbow crossbow;
		private Behaviours.@Nullable Eat eat;
		private Behaviours.@Nullable Drink drink;
		private Behaviours.@Nullable Pearl pearl;
		private Behaviours.@Nullable TempBlocks blocks;
		private Behaviours.@Nullable ShieldBreak shieldBreak;

		private Builder(String id) {
			this.id = id;
		}

		/** A ruler (or the Chair): no Duel mode, and the signature cap may go up to the ruler rail. */
		public Builder ruler() {
			this.ruler = true;
			this.duelMode = false;
			return this;
		}

		public Builder hp(double hp) { this.maxHp = hp; return this; }
		public Builder walkSpeed(double s) { this.walkSpeed = s; return this; }
		/** Required. Ordinary swing: raw damage (up to the minor cap), ticks between swings, wind-up ticks (at least 8 from raw 16). */
		public Builder swing(float raw, int interval, int windup) { this.swingRaw = raw; this.swingInterval = interval; this.swingWindup = windup; return this; }
		/** Required. Melee reach in blocks. */
		public Builder reach(double r) { this.reach = r; return this; }
		public Builder melee(Supplier<ItemStack> s) { this.melee = s; return this; }
		/** A throwing weapon for the middle range; needs {@link #swap}. */
		public Builder thrown(Supplier<ItemStack> s) { this.thrown = s; return this; }
		/** A bow or crossbow for the long range; needs {@link #swap} and {@link #bow} or {@link #crossbow}. */
		public Builder ranged(Supplier<ItemStack> s) { this.ranged = s; return this; }
		public Builder food(Supplier<ItemStack> s) { this.food = s; return this; }
		public Builder potion(Supplier<ItemStack> s) { this.potion = s; return this; }
		/** Required. Descending HP fractions at which the next phase starts; {@code phases()} with no values = a single phase. */
		public Builder phases(double... t) { this.phases = t; return this; }
		public Builder signature(SignatureMove m) { this.signatures.add(m); return this; }
		public Builder barColor(BossEvent.BossBarColor c) { this.barColor = c; return this; }
		public Builder duelMode(boolean b) { this.duelMode = b; return this; }
		public Builder court(CourtBonus c) { this.court = c; return this; }
		public Builder frenzy(FrenzyRage f) { this.frenzy = f; if (f.noHeal) this.noHeal = true; return this; }
		public Builder skin(String path) { this.skin = path; return this; }
		/** The {@code boss_identity} id this profile implements (default: the profile id). */
		public Builder identity(String id) { this.identity = id; return this; }
		/** Required: this boss's own telegraph look. */
		public Builder telegraph(TelegraphStyle s) { this.style = s; return this; }
		/** Required. Damage caps for this boss, raw before armor: ordinary hits (swing, bash, arrow, thrown) and signature hits (crit, signature moves, magic). At most {@link Kit#CAP_MINOR_MAX} and the ruler or Lord signature rail. */
		public Builder caps(float minor, float signature) { this.capMinor = minor; this.capSignature = signature; return this; }
		/** This boss's own last stand: runs when a hit would kill it, at most {@code maxUses} times. See {@link PhaseController.LethalHook}. */
		public Builder lastStand(PhaseController.LethalHook hook, int maxUses) { this.lastStand = hook; this.lastStandMax = maxUses; return this; }
		public Builder lastStand(PhaseController.LethalHook hook) { return lastStand(hook, 1); }

		// opt-in behaviours (each off unless called)
		public Builder strafe(Behaviours.Strafe b) { this.strafe = b; return this; }
		public Builder sprintCrit(Behaviours.SprintCrit b) { this.crit = b; return this; }
		public Builder shield(Behaviours.Shield b) { this.shield = b; return this; }
		public Builder swap(Behaviours.Swap b) { this.swap = b; return this; }
		public Builder bow(Behaviours.Bow b) { this.bow = b; return this; }
		public Builder crossbow(Behaviours.Crossbow b) { this.crossbow = b; return this; }
		public Builder eat(Behaviours.Eat b) { this.eat = b; return this; }
		public Builder drink(Behaviours.Drink b) { this.drink = b; return this; }
		public Builder pearl(Behaviours.Pearl b) { this.pearl = b; return this; }
		public Builder tempBlocks(Behaviours.TempBlocks b) { this.blocks = b; return this; }
		/** Weapon property, not a generic behaviour: this boss's hits can take a player shield down. */
		public Builder shieldBreak(Behaviours.ShieldBreak b) { this.shieldBreak = b; return this; }

		public DuelistProfile build() {
			List<String> missing = new ArrayList<>();
			if (style == null) missing.add("telegraph(style)");
			if (maxHp <= 0) missing.add("hp(n)");
			if (walkSpeed <= 0) missing.add("walkSpeed(n)");
			if (swingRaw < 0F || swingInterval < 0 || swingWindup < 0) missing.add("swing(raw, interval, windup)");
			if (reach <= 0) missing.add("reach(n)");
			if (melee == null) missing.add("melee(stack)");
			if (phases == null) missing.add("phases(...)");
			if (capMinor < 0F || capSignature < 0F) missing.add("caps(minor, signature)");
			if (eat != null && food == null) missing.add("food(stack) for eat");
			if (drink != null && potion == null) missing.add("potion(stack) for drink");
			if (!missing.isEmpty()) throw new IllegalArgumentException(id + ": nothing has a default; missing " + String.join(", ", missing));
			float sigRail = ruler ? Kit.CAP_SIGNATURE_MAX_RULER : Kit.CAP_SIGNATURE_MAX_LORD;
			if (capMinor > Kit.CAP_MINOR_MAX || capSignature > sigRail) throw new IllegalArgumentException(id + ": caps " + capMinor + "/" + capSignature + " are over the rails " + Kit.CAP_MINOR_MAX + "/" + sigRail);
			if (swingRaw <= 0F || swingRaw > capMinor) throw new IllegalArgumentException(id + ": ordinary swing raw " + swingRaw + " must be above 0 and within the minor cap " + capMinor);
			if (capSignature < capMinor) throw new IllegalArgumentException(id + ": signature cap " + capSignature + " is below the minor cap " + capMinor);
			if (swap == null && (thrown != null || bow != null || crossbow != null)) throw new IllegalArgumentException(id + ": thrown, bow and crossbow need swap(...)");
			if ((bow != null || crossbow != null) && ranged == null) throw new IllegalArgumentException(id + ": bow or crossbow needs ranged(...)");
			if (ranged != null && bow == null && crossbow == null) throw new IllegalArgumentException(id + ": ranged(...) needs bow(...) or crossbow(...)");
			if (swap != null && thrown == null && ranged == null) throw new IllegalArgumentException(id + ": swap needs a thrown or a ranged weapon");
			if (noHeal) {
				eat = null;
				drink = null;
			}
			Telegraph.check(id + ".swing", Telegraph.Weight.ORDINARY, swingRaw, swingWindup);
			for (SignatureMove m : signatures) {
				Telegraph.check(m.id(), m.weight(), m.raw(), m.windup());
				if (m.raw() > capSignature) throw new IllegalArgumentException(id + ": move " + m.id() + " raw " + m.raw() + " is over the cap " + capSignature);
			}
			return new DuelistProfile(this);
		}
	}
}
