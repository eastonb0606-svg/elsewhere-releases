package dev.elsewhere.rivals.azmar.boss;

import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.kingdom_court.api.Leaders;
import dev.elsewhere.rivals.azmar.AzmarContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Common base of the four Azmar bosses: court hooks, the Falconer's shared marks, safe teleports and the court roster. */
public abstract class AzmarBoss extends HumanBoss implements Leaders.LeaderBoss {
	/** Players a falcon of the Falconer has marked (until game tick): the court reads them with no delay. */
	private static final Map<UUID, Long> MARKS = new HashMap<>();

	protected AzmarBoss(EntityType<? extends HumanBoss> type, Level level, DuelistProfile profile) {
		super(type, level, profile);
	}

	// ---- the Regent (RIVALS-DECISIONS 10): the highest living Lord takes the throne, keeps their own fight, gains a slight boost ----------
	public static final float REGENT_BONUS = 0.10F;
	private static final Identifier REGENT_HP = AzmarContent.id("azmar_regent_hp");
	public boolean regent;

	/** Called once from {@link AzmarBosses#init()}: when the court crowns a Lord, that boss turns Regent at once. */
	static void initRegent() {
		dev.elsewhere.kingdom_court.CourtEvents.SUCCESSION.register((server, faction, seat, kind) -> {
			if (!faction.equals(AzmarContent.FACTION) || kind != dev.elsewhere.kingdom_court.CourtEvents.SuccessionKind.RULER_SUCCESSION) return;
			var st = dev.elsewhere.kingdom_court.CourtState.get(server, faction);
			var r = dev.elsewhere.kingdom_court.ruler.Ruler.byKey(seat);
			var id = r == null ? null : st.rulerIds.get(r);
			if (id == null) return;
			for (ServerLevel l : server.getAllLevels()) if (l.getEntity(id) instanceof AzmarBoss b) b.syncRegent(l);
		});
	}

	/** Reads the court (who is crowned) and applies or removes the Regent boost: +10 percent health and damage. A Lord only; the Sultana is the throne. */
	public void syncRegent(ServerLevel level) {
		var ref = dev.elsewhere.kingdom_court.api.Leaders.of(this);
		if (ref == null || !ref.faction().equals(AzmarContent.FACTION)) return;
		var seat = dev.elsewhere.kingdom_court.ruler.Ruler.byKey(ref.slot());
		boolean want = seat != null && seat != dev.elsewhere.kingdom_court.ruler.Ruler.KING && dev.elsewhere.kingdom_court.CourtState.get(level, AzmarContent.FACTION).crowned == seat;
		var hp = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (hp == null || want == regent && (hp.getModifier(REGENT_HP) != null) == want) return;
		float frac = getHealth() / getMaxHealth();
		hp.removeModifier(REGENT_HP);
		if (want) hp.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(REGENT_HP, REGENT_BONUS, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		regent = want;
		applyCompensation();                                       // the ruler tier (+8/+4 armour) follows the crown
		enforceHealthCap();
		setHealth(Math.min(getMaxHealth(), Math.max(1F, frac * getMaxHealth())));
	}

	// ---- RIVALS-DECISIONS 13: human bosses never exceed 300 max health; they are compensated by armour, damage, cooldowns ----------------------
	public static final float HEALTH_CAP = 300F, DAMAGE_BONUS = 0.25F, COOLDOWN_SCALE = 0.8F, DEFENCE_SCALE = 1F / 1.3F;
	private static final Identifier CAP_MOD = AzmarContent.id("azmar_health_cap"), ARMOR_MOD = AzmarContent.id("azmar_comp_armor"),
		TOUGH_MOD = AzmarContent.id("azmar_comp_toughness"), KB_MOD = AzmarContent.id("azmar_comp_kb");
	private static final java.util.Set<String> DEFENSIVE = java.util.Set.of("sig:counter_riposte", "sig:counter_brew", "sig:screen_and_step", "sig:veil_of_the_ibis", "sig:perch_post", "intercept", "dodge", "guard", "withdraw", "veil");
	/** Cooldowns whose constants already carry the 1.3x defensive factor (dust dodge 160/1.3, mirage 120/1.3): stored as given, never scaled twice. */
	private static final java.util.Set<String> PRESCALED = java.util.Set.of("dust_dodge", "mirage");

	/** True for the Sultana (a ruler gets +8 armour and +4 toughness, the Lords +6 and +3). */
	protected boolean rulerTier() {
		return regent;
	}

	/**
	 * Armour, toughness and knockback compensation as transient modifiers. Idempotent: a modifier is only (re)written when its value differs, so a
	 * call never dirties an attribute for nothing. Called on spawn and on load (the first AI step), on a phase change and when the Regent tier changes.
	 */
	private void applyCompensation() {
		var add = net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE;
		setMod(getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR), ARMOR_MOD, rulerTier() ? 8.0 : 6.0, add);
		setMod(getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS), TOUGH_MOD, rulerTier() ? 4.0 : 3.0, add);
		setMod(getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), KB_MOD, 0.4, add);
	}

	private static void setMod(net.minecraft.world.entity.ai.attributes.@Nullable AttributeInstance a, Identifier id, double amount, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation op) {
		if (a == null) return;
		var cur = a.getModifier(id);
		if (cur != null && cur.operation() == op && Math.abs(cur.amount() - amount) < 1e-9) return;
		a.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(id, amount, op));
	}

	/**
	 * Clamps max health to 300 after every other modifier (court bonus, Regent boost, Test Meter). Event-driven (RIVALS-DECISIONS 19): it runs on spawn and
	 * load, on a phase change, on a Regent change and after any change to the max-health attribute ({@link #onAttributeUpdated}), never on a timer. Idempotent:
	 * the uncapped value is worked out from the current one, and the cap modifier is only rewritten when it must change (so it cannot re-trigger itself).
	 * Healing cannot pass the cap: vanilla clamps health to max health, which is at most 300.
	 */
	public void enforceHealthCap() {
		var hp = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (hp == null) return;
		var cur = hp.getModifier(CAP_MOD);
		double v = hp.getValue();
		double raw = cur == null ? v : v / Math.max(1e-6, 1.0 + cur.amount());
		if (raw > HEALTH_CAP + 1e-6) setMod(hp, CAP_MOD, HEALTH_CAP / raw - 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		else if (cur != null) hp.removeModifier(CAP_MOD);
		if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
	}

	/** Spawn and load: transient modifiers are not saved, so the first AI step after either applies them (once). */
	private boolean compApplied;
	/** Set when the max-health attribute changed (any modifier, from any mod): the cap is checked on the next AI step. */
	private boolean capDirty;

	/** Applies the compensation and the cap now (spawn, load, phase change). */
	public void refreshCompensation() {
		applyCompensation();
		enforceHealthCap();
		compApplied = true;
		capDirty = false;
	}

	@Override
	protected void onAttributeUpdated(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		super.onAttributeUpdated(attribute);
		if (attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)) capDirty = true;   // modifying attributes inside the refresh loop is unsafe: next AI step
	}

	@Override
	protected void onPhaseChange(ServerLevel level, int newPhase) {
		super.onPhaseChange(level, newPhase);
		refreshCompensation();
	}

	/** No friendly fire: Azmar's own soldiers (and allies) never hurt an Azmar boss, the kingdom's rule for its soldiers (the Wazir's Hands shoot past him). */
	@Override
	public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float damage) {
		if (source.getEntity() instanceof dev.elsewhere.kingdom.KingdomSoldier s && !dev.elsewhere.kingdom.Factions.hostile(s.factionId(), AzmarContent.FACTION)) return false;
		return super.hurtServer(level, source, damage);
	}

	@Override
	public void setCooldown(String id, int ticks) {
		super.setCooldown(id, PRESCALED.contains(id) ? ticks : Math.round(ticks * (DEFENSIVE.contains(id) ? DEFENCE_SCALE : COOLDOWN_SCALE)));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!compApplied) {                                        // once on spawn and once after a load; the court's SUCCESSION event handles crowning
			syncRegent(level);
			refreshCompensation();
		} else if (capDirty) {                                     // the max-health attribute changed since the last step
			capDirty = false;
			enforceHealthCap();
		}
	}

	@Override
	public float damageMultiplier() {
		return super.damageMultiplier() * (regent ? 1F + REGENT_BONUS : 1F) * (1F + DAMAGE_BONUS);
	}

	public static void mark(UUID player, long untilTick) {
		MARKS.merge(player, untilTick, Math::max);
	}

	public static boolean isMarked(UUID player, long now) {
		Long t = MARKS.get(player);
		return t != null && t > now;
	}

	public static void clearMarks() {
		MARKS.clear();
	}

	@Override
	public void onCourtSpawn(Identifier faction, String slot, @Nullable BlockPos home) {
		if (home != null) setHome(home);
	}

	@Override
	public void onHolder(boolean holder) {}

	/** 3.0 (s-bossdrops): every boss always drops XP (HumanBoss leaves it at 0; each boss sets its own number). Vanilla's always-drop path, so the Chaperon reads it too. */
	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return dev.elsewhere.rivals.azmar.rewards.AzmarRewards.xp(this);
	}

	@Override
	protected boolean isAlwaysExperienceDropper() {
		return dev.elsewhere.rivals.azmar.rewards.AzmarRewards.xp(this) > 0;
	}

	/** Living Azmar court bosses within 160 blocks, by entity type path ({@code azmar_wazir}...). */
	public static boolean alive(ServerLevel level, EntityType<? extends Mob> type, Vec3 around) {
		List<? extends Mob> l = level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(around, around).inflate(160), m -> m.getType() == type && m.isAlive());
		return !l.isEmpty();
	}

	/** A standable spot near {@code want} inside the arena (two air blocks over a solid one), or null. Searches 3 up and 4 down. */
	public @Nullable Vec3 standable(ServerLevel level, Vec3 want) {
		BlockPos base = BlockPos.containing(want);
		for (int dy = 3; dy >= -4; dy--) {
			BlockPos p = base.offset(0, dy, 0);
			if (level.getBlockState(p.below()).isSolid() && level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
				&& level.getFluidState(p).isEmpty()) {
				Vec3 v = Vec3.atBottomCenterOf(p);
				if (!home.equals(BlockPos.ZERO) && home.distSqr(p) > (double) arenaRadius * arenaRadius) return null;
				return v;
			}
		}
		return null;
	}

	/** Teleports to a standable spot near {@code want}. True when it moved. */
	public boolean hop(ServerLevel level, Vec3 want) {
		Vec3 v = standable(level, want);
		if (v == null) return false;
		teleportTo(v.x, v.y, v.z);
		getNavigation().stop();
		return true;
	}
}
