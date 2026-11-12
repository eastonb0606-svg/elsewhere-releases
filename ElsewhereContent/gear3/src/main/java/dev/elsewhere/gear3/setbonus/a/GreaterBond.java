package dev.elsewhere.gear3.setbonus.a;

import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.setbonus.SetBonus;
import dev.elsewhere.gear3.setbonus.SetBonuses;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Elderbark Regalia (mantle, leggings, boots + the summoner module's Elder Bark Crown), <b>Greater Bond</b> (GEAR.md A5,
 * Elite): your 2-slot minions (War-Yak, Echo Reliquary) have +30% max health and deal +15% damage. A 2-slot minion is a
 * minion whose entity type is in {@code #elsewhere:two_slot_minions} (or added to {@link #TWO_SLOT} by its owner lane).
 * The health modifier is set when such a minion joins the world and when the set turns on or off (health keeps its
 * fraction); the damage is one multiply on the minion's hit. Nothing ticks.
 */
public final class GreaterBond {
	private GreaterBond() {}

	public static final double HEALTH = 0.30;
	public static final float DAMAGE = 0.15F;
	public static final Identifier HEALTH_ID = Gear3Content.id("setbonus/greater_bond.health");
	public static final TagKey<EntityType<?>> TWO_SLOT_TAG = TagKey.create(Registries.ENTITY_TYPE, Gear3Content.id("two_slot_minions"));
	/** 2-slot minion types registered in code (alternative to the tag). */
	public static final Set<EntityType<?>> TWO_SLOT = new HashSet<>();
	public static SetBonus BONUS;

	static void init() {
		BONUS = SetBonuses.register(SetBonus.of("greater_bond")
			.piece(EquipmentSlot.HEAD, "elder_bark_crown").piece(EquipmentSlot.CHEST, "elderbark_mantle")
			.piece(EquipmentSlot.LEGS, "elderbark_leggings").piece(EquipmentSlot.FEET, "elderbark_boots")
			.details(2).onChange(GreaterBond::changed));
		SetBonuses.DAMAGE.add(GreaterBond::damage);
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (SetBonuses.anyone() && e instanceof MinionEntity m && twoSlot(m)) {
				ServerPlayer o = m.ownerPlayer();
				if (o != null) apply(m, SetBonuses.power(o, BONUS));
			}
		});
	}

	public static boolean twoSlot(Entity e) {
		return e instanceof MinionEntity && (TWO_SLOT.contains(e.getType()) || e.getType().builtInRegistryHolder().is(TWO_SLOT_TAG));
	}

	static void changed(ServerPlayer p, boolean on) {
		float pw = on ? SetBonuses.power(p, BONUS) : 0F;
		for (MinionEntity m : MinionManager.minions(p)) if (twoSlot(m)) apply(m, pw);
	}

	/** Sets the +30% x power max-health modifier (0 removes it), keeping the minion's health fraction. */
	public static void apply(MinionEntity m, float power) {
		AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
		if (hp == null) return;
		double want = HEALTH * power;
		AttributeModifier cur = hp.getModifier(HEALTH_ID);
		if (cur == null ? want <= 0.0 : cur.amount() == want) return;
		float frac = m.getMaxHealth() > 0F ? m.getHealth() / m.getMaxHealth() : 1F;
		hp.removeModifier(HEALTH_ID);
		if (want > 0.0) hp.addTransientModifier(new AttributeModifier(HEALTH_ID, want, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		if (m.isAlive()) m.setHealth(Math.max(1F, Math.min(m.getMaxHealth(), frac * m.getMaxHealth())));
	}

	static float damage(LivingEntity victim, DamageSource src, float amount) {
		if (victim instanceof Player || !(src.getDirectEntity() instanceof MinionEntity m) || !twoSlot(m) || !(src.getEntity() instanceof ServerPlayer o)) return amount;
		float pw = SetBonuses.power(o, BONUS);
		return pw > 0F ? amount * (1F + DAMAGE * pw) : amount;
	}
}
