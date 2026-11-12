package dev.elsewhere.minion;

import dev.elsewhere.stub.summoner.SummonerAttributes;
import dev.elsewhere.stub.summoner.TestMeterHalf;
import dev.elsewhere.summoner.GearStats;
import dev.elsewhere.summoner.SummonerEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;

/** Public minion API for other modules (arsenal's Glimmer reserve, bosses, QA). */
public final class MinionApi {
	private MinionApi() {}

	/** Minion hits: direct entity = the minion, causing entity = the owner (kills, XP, loot share count as the owner's). */
	public static final ResourceKey<DamageType> MINION_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("elsewhere", "minion"));
	public static final int GLIMMER_RESERVE_PER_MINION = GearStats.GLIMMER_RESERVE_PER_MINION;
	/** Last damage a minion dealt (self-test reads it). */
	public static float LAST_HIT_DAMAGE;

	public static int activeCount(ServerPlayer owner) {
		return MinionManager.count(owner);
	}

	/** Glimmer reserved from the owner's max by live minions: 8/7/6/5/4 each by Summoner Calling 0..4 (rev 3). */
	public static int reservedGlimmer(ServerPlayer owner) {
		int r = 0;
		for (MinionEntity m : MinionManager.minions(owner)) r += MinionTuning.reserve(owner, m);
		return r;
	}

	public static boolean isMinion(Entity e) {
		return e instanceof MinionEntity;
	}

	public static int maxSlots(Player owner) {
		double v = attr(owner, SummonerAttributes.slots(), GearStats.BASE_MINION_SLOTS);
		int cap = Math.min(Math.min(GearStats.HARD_SLOT_CAP, dev.elsewhere.summoner.SummonerConfig.maxMinionsPerPlayer()), MinionTuning.slotCap(owner));
		return Math.max(0, Math.min(cap, (int) Math.floor(v + 1.0E-6)));
	}

	public static double damageMultiplier(Player owner) {
		return attr(owner, SummonerAttributes.damage(), 1.0);
	}

	private static double attr(Player p, Holder<Attribute> h, double fallback) {
		if (h == null) return fallback;
		AttributeInstance inst = p.getAttribute(h);
		return inst == null ? fallback : inst.getValue();
	}

	/** base x minion_damage x Summoner Calling x Test Meter half bonus x Well Fed, then +1 flat on the owner's Herded target. */
	public static double minionDamage(ServerPlayer owner, MinionEntity minion, LivingEntity target, double base) {
		double d = base * damageMultiplier(owner) * TestMeterHalf.multiplier(owner) * MinionTuning.damageMultiplier(owner, minion);
		if (SummonerEffects.WELL_FED != null && minion.hasEffect(SummonerEffects.WELL_FED)) d *= 1.0 + GearStats.WELL_FED_DAMAGE;
		if (dev.elsewhere.summoner.GaleRibbonItem.isTagged(owner, target)) d *= 1.0 + GearStats.RIBBON_DAMAGE_BONUS;
		if (Herding.isHerdedBy(owner, target)) d += GearStats.HERDED_BONUS;
		return d;
	}

	public static DamageSource damageSource(ServerLevel level, MinionEntity minion, ServerPlayer owner) {
		Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(MINION_DAMAGE);
		return new DamageSource(type, minion, owner);
	}
}
