package dev.elsewhere.gear3.setbonus.a;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import dev.elsewhere.minion.MinionEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Hearthguard set (helm, greaves, boots + Hrafnholt's Hearth-Ward Mail), <b>Hold the Line</b> (GEAR.md A5, Tank): while
 * sneaking or with a shield raised, +4 armor, and allies within 4 blocks take 10% less damage. Allies = other players
 * (on your scoreboard team, or either of you has none), your minions and your tamed animals; one holder's cut applies, they
 * don't stack. Event-driven: the armor modifier and the holder list change only on a stance change (sneak toggled, item
 * use started or stopped) or an equipment change; a hit looks only at the current holders (usually none).
 */
public final class HoldTheLine {
	private HoldTheLine() {}

	public static final double ARMOR = 4.0, ALLY_RANGE = 4.0;
	public static final float ALLY_CUT = 0.10F;
	public static final Identifier ARMOR_ID = Gear3Content.id("setbonus/hold_the_line.armor");
	public static SetBonus BONUS;
	/** Players holding the line right now (set on and sneaking or shield up). */
	static final Map<UUID, ServerPlayer> HOLDING = new LinkedHashMap<>();

	static void init() {
		BONUS = SetBonuses.register(SetBonus.of("hold_the_line")
			.piece(EquipmentSlot.HEAD, "hrafnholt_hearthguard_helm").piece(EquipmentSlot.CHEST, "hrafnholt_hearth_ward_mail")
			.piece(EquipmentSlot.LEGS, "hrafnholt_hearthguard_greaves").piece(EquipmentSlot.FEET, "hrafnholt_hearthguard_boots")
			.details(3).onChange((p, on) -> refresh(p)));
		SetBonuses.STANCE.add(HoldTheLine::refresh);
		SetBonuses.DAMAGE.add(HoldTheLine::damage);
	}

	static boolean shieldUp(Player p) {
		return p.isUsingItem() && p.getUseItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	/** Holding the line: the set is on and the player sneaks or has a shield raised. */
	public static boolean holding(ServerPlayer p) {
		return p.isAlive() && SetBonuses.active(p, BONUS) && (p.isShiftKeyDown() || shieldUp(p));
	}

	public static boolean isHolder(ServerPlayer p) {
		return HOLDING.containsKey(p.getUUID());
	}

	/** Re-reads the stance: sets or clears the +4 armor modifier and the holder entry. */
	public static void refresh(ServerPlayer p) {
		boolean on = holding(p);
		double want = on ? ARMOR * SetBonuses.power(p, BONUS) : 0.0;
		AttributeInstance a = p.getAttribute(Attributes.ARMOR);
		if (a != null) {
			AttributeModifier cur = a.getModifier(ARMOR_ID);
			if (want <= 0.0) {
				if (cur != null) a.removeModifier(ARMOR_ID);
			} else if (cur == null || cur.amount() != want) {
				a.removeModifier(ARMOR_ID);
				a.addTransientModifier(new AttributeModifier(ARMOR_ID, want, AttributeModifier.Operation.ADD_VALUE));
			}
		}
		boolean was = HOLDING.containsKey(p.getUUID());
		if (on) HOLDING.put(p.getUUID(), p);
		else HOLDING.remove(p.getUUID());
		if (on && !was) p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.4F, 0.7F);
	}

	static float damage(LivingEntity victim, DamageSource src, float amount) {
		if (HOLDING.isEmpty()) return amount;
		for (ServerPlayer h : HOLDING.values()) {
			if (h == victim || h.isRemoved() || !h.isAlive() || h.level() != victim.level() || src.getEntity() == h) continue;
			if (h.distanceToSqr(victim) > ALLY_RANGE * ALLY_RANGE || !ally(h, victim)) continue;
			return amount * Math.max(0F, 1F - ALLY_CUT * SetBonuses.power(h, BONUS));
		}
		return amount;
	}

	/** Other players (same team, or either has none), the holder's minions and tamed animals. */
	public static boolean ally(ServerPlayer h, LivingEntity v) {
		if (v instanceof Player o) return h.getTeam() == null || o.getTeam() == null || h.isAlliedTo(o);
		if (v instanceof MinionEntity m) return m.isOwnedBy(h);
		return v instanceof TamableAnimal t && t.isOwnedBy(h);
	}
}
