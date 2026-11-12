package dev.elsewhere.gear3;

import dev.elsewhere.arsenal.ArsenalAttributes;
import dev.elsewhere.arsenal.trinket.Trinkets;
import dev.elsewhere.stub.arsenal.BossCheck;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Trinket hooks. Nothing here ticks: every method runs on one game event (a hit, an effect being applied, a bow release, an arrow
 * being used up) and re-reads the player's active trinkets at that moment (Arsenal's {@link Trinkets#isActive}).
 */
public final class Gear3Hooks {
	private Gear3Hooks() {}

	public static final double LENS_FROM = 16.0, LENS_PER_BLOCK = 0.01, LENS_MAX = 0.20;
	public static final float QUIVER_DRAW = 0.85F, BANDOLIER_SAVE = 0.40F, SIGIL_BONUS = 1.30F;
	public static final double OATH_TOUGHNESS = 1.0, OATH_KILL_HEAL = 1.0;
	private static final Identifier OATH_T = Gear3Content.id("gear3/oath_ring.toughness"), OATH_K = Gear3Content.id("gear3/oath_ring.kill_heal");
	public static final Set<Holder<MobEffect>> CROWD_CONTROL = Set.of(MobEffects.SLOWNESS, MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.MINING_FATIGUE, MobEffects.NAUSEA,
		MobEffects.DARKNESS, MobEffects.LEVITATION);

	/** Rangefinder Lens factor for an arrow hit from {@code distance} blocks: +1% per block beyond 16, max +20%. */
	public static float lensFactor(double distance) {
		return (float) (1.0 + Math.min(LENS_MAX, Math.max(0.0, distance - LENS_FROM) * LENS_PER_BLOCK));
	}

	static boolean active(Player p, Item trinket) {
		return trinket != null && Trinkets.isActive(p, trinket);
	}

	/** Called for the victim and for the attacker of every hit (server side), before the damage is worked out. */
	public static float modifyIncoming(LivingEntity victim, DamageSource source, float amount) {
		if (victim.level().isClientSide()) return amount;
		if (victim instanceof Player p) refreshOath(p);
		Entity attacker = source.getEntity();
		if (attacker instanceof Player a) refreshOath(a);
		if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getOwner() instanceof Player shooter && active(shooter, Gear3Items.RANGEFINDER))
			return amount * lensFactor(shooter.distanceTo(victim));
		return amount;
	}

	// ---------------------------------------------------------------- Oath Ring: holding a Vanguard weapon: +1 toughness, +1 kill heal

	public static boolean oathWanted(Player p) {
		return active(p, Gear3Items.OATH_RING) && p.getMainHandItem().is(Gear3Items.itemTag("elsewhere:calling_weapon/vanguard"));
	}

	public static void refreshOath(Player p) {
		boolean on = oathWanted(p);
		apply(p, Attributes.ARMOR_TOUGHNESS, OATH_T, on ? OATH_TOUGHNESS : 0);
		apply(p, ArsenalAttributes.KILL_HEAL, OATH_K, on ? OATH_KILL_HEAL : 0);
	}

	private static void apply(Player p, Holder<Attribute> attr, Identifier id, double amount) {
		AttributeInstance inst = p.getAttribute(attr);
		if (inst == null) return;
		AttributeModifier cur = inst.getModifier(id);
		if (amount == 0) {
			if (cur != null) inst.removeModifier(id);
		} else if (cur == null || cur.amount() != amount) {
			inst.removeModifier(id);
			inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	// ---------------------------------------------------------------- Quickdraw Quiver: draw time -15%

	/** New "ticks left" for a bow release so that the elapsed draw time counts 1/0.85 as long. */
	public static int bowTimeLeft(int timeLeft, int useDuration, LivingEntity user) {
		if (!(user instanceof Player p) || !active(p, Gear3Items.QUIVER)) return timeLeft;
		int used = useDuration - timeLeft;
		return useDuration - Math.round(used / QUIVER_DRAW);
	}

	public static int crossbowDuration(int original, LivingEntity user) {
		if (!(user instanceof Player p) || !active(p, Gear3Items.QUIVER)) return original;
		return Math.max(1, Math.round(original * QUIVER_DRAW));
	}

	// ---------------------------------------------------------------- Fowler's Bandolier: tipped and spectral arrows 40% not used

	public static boolean bandolierSaves(LivingEntity shooter, ItemStack ammo) {
		if (!(shooter instanceof Player p) || p.level().isClientSide() || !ammo.is(Items.TIPPED_ARROW) && !ammo.is(Items.SPECTRAL_ARROW)) return false;
		return active(p, Gear3Items.BANDOLIER) && p.getRandom().nextFloat() < BANDOLIER_SAVE;
	}

	// ---------------------------------------------------------------- Binding Sigil: your crowd-control durations +30% on non-bosses

	public static MobEffectInstance sigil(LivingEntity target, MobEffectInstance e, Entity source) {
		if (!(source instanceof Player p) || target.level().isClientSide() || !CROWD_CONTROL.contains(e.getEffect()) || BossCheck.isBoss(target)) return e;
		if (!active(p, Gear3Items.SIGIL)) return e;
		return new MobEffectInstance(e.getEffect(), Math.round(e.getDuration() * SIGIL_BONUS), e.getAmplifier(), e.isAmbient(), e.isVisible(), e.showIcon());
	}
}
