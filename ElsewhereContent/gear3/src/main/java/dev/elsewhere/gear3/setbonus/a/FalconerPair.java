package dev.elsewhere.gear3.setbonus.a;

import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import dev.elsewhere.minion.MinionEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;

/**
 * Falconer pair (Azmar Falconer Kilt + Sandrunner Sandals, Marksman + Summoner), pair bonus (GEAR.md A5): your arrow hits
 * mark the target so your minions deal +15% to it for 4 s; your minions' hits mark it so your arrows deal +10% for 4 s;
 * both x H(marksman, summoner). Marks are per owner and live in a weak map on the target (expiry is a stored game time,
 * read on the next hit). Never against players.
 */
public final class FalconerPair {
	private FalconerPair() {}

	public static final float MINION_BONUS = 0.15F, ARROW_BONUS = 0.10F;
	public static final int MARK_TICKS = 80;
	public static SetBonus BONUS;
	/** target -> owner -> {arrow mark until (boosts minions), minion mark until (boosts arrows)} */
	private static final WeakHashMap<LivingEntity, Map<UUID, long[]>> MARKS = new WeakHashMap<>();

	static void init() {
		BONUS = SetBonuses.register(SetBonus.of("azmar_falconer")
			.piece(EquipmentSlot.LEGS, "azmar_falconer_kilt").piece(EquipmentSlot.FEET, "azmar_sandrunner_sandals")
			.hybrid(Calling.Path.MARKSMAN, Calling.Path.SUMMONER).details(3));
		SetBonuses.DAMAGE.add(FalconerPair::damage);
		SetBonuses.AFTER_HIT.add(FalconerPair::hit);
	}

	static boolean arrow(Entity direct) {
		return direct instanceof AbstractArrow && !(direct instanceof ThrownTrident);
	}

	private static long[] marks(LivingEntity target, UUID owner, boolean create) {
		Map<UUID, long[]> m = MARKS.get(target);
		if (m == null) {
			if (!create) return null;
			MARKS.put(target, m = new HashMap<>(2));
		}
		return create ? m.computeIfAbsent(owner, u -> new long[] {Long.MIN_VALUE, Long.MIN_VALUE}) : m.get(owner);
	}

	/** Arrow-marked by {@code owner} (your minions deal more) right now. */
	public static boolean arrowMarked(LivingEntity target, ServerPlayer owner) {
		long[] mk = marks(target, owner.getUUID(), false);
		return mk != null && mk[0] > target.level().getGameTime();
	}

	/** Minion-marked for {@code owner} (your arrows deal more) right now. */
	public static boolean minionMarked(LivingEntity target, ServerPlayer owner) {
		long[] mk = marks(target, owner.getUUID(), false);
		return mk != null && mk[1] > target.level().getGameTime();
	}

	public static float damage(LivingEntity victim, DamageSource src, float amount) {
		if (victim instanceof Player || !(src.getEntity() instanceof ServerPlayer p)) return amount;
		Entity direct = src.getDirectEntity();
		boolean arrow = arrow(direct), minion = direct instanceof MinionEntity;
		if (!arrow && !minion) return amount;
		float pw = SetBonuses.power(p, BONUS);
		if (pw <= 0F) return amount;
		if (arrow && minionMarked(victim, p)) return amount * (1F + ARROW_BONUS * pw);
		if (minion && arrowMarked(victim, p)) return amount * (1F + MINION_BONUS * pw);
		return amount;
	}

	public static void hit(LivingEntity victim, DamageSource src, float taken) {
		if (victim instanceof Player || !victim.isAlive() || !(src.getEntity() instanceof ServerPlayer p)) return;
		Entity direct = src.getDirectEntity();
		boolean arrow = arrow(direct), minion = direct instanceof MinionEntity;
		if ((!arrow && !minion) || SetBonuses.power(p, BONUS) <= 0F) return;
		long until = victim.level().getGameTime() + MARK_TICKS;
		long[] mk = marks(victim, p.getUUID(), true);
		if (arrow) mk[0] = until;
		else mk[1] = until;
		if (victim.level() instanceof ServerLevel sl)
			sl.sendParticles(arrow ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.WAX_ON, victim.getX(), victim.getY() + victim.getBbHeight() + 0.3, victim.getZ(), 5, 0.2, 0.1, 0.2, 0.02);
	}

	/** Self-test: forget every mark. */
	public static void clearMarks() {
		MARKS.clear();
	}
}
