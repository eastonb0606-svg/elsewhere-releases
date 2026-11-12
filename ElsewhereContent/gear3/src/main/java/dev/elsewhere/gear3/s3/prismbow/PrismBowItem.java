package dev.elsewhere.gear3.s3.prismbow;

import dev.elsewhere.arsenal.ArsenalAttributes;
import dev.elsewhere.arsenal.Overhaul3;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.spell.Calling;
import dev.elsewhere.arsenal.spell.MagicBolt;
import dev.elsewhere.gear3.Gear3Hooks;
import dev.elsewhere.gear3.Gear3Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Prism Bow (GEAR.md A5, Marksman table: {@code prism_bow}, M+C hybrid, Arcane archer, G4; 3.0 swarm s-prismbow).
 * "bow, draw 1.0 s | full draw spends 2 Glimmer for a light arrow: 8 x spell power, no gravity 24 blocks, x H".
 * <ul>
 * <li>A vanilla bow in every other way (1.0 s draw, ammo rules, enchantments, the Quickdraw Quiver through BowItem's
 * releaseUsing hook, the Fowler's Bandolier through useAmmo, the vanilla pull frames and aim pose).</li>
 * <li>Released at full draw (power 1.0, the Quiver counted) with the Glimmer for it: spends {@link #GLIMMER_COST} raw Glimmer
 * (spell cost and the Caster calling scale it, like any spell), and looses a light arrow instead of the nocked arrow, which
 * is not used. The light arrow is an arsenal {@link MagicBolt}: {@link #LIGHT_DAMAGE} x H base, then the spell damage rules
 * (spell power, Caster calling, Moonlit, Test Meter half bonus, Spells.DAMAGE_MULT hooks), pierce from
 * {@code elsewhere:projectile_pierce}, no gravity, gone after {@link #LIGHT_RANGE} blocks. H = {@code Calling.hybrid(Marksman,
 * Caster)} (1.0 with the callings switch off).</li>
 * <li>Not enough Glimmer, a short draw, or arrows held in the other hand: it shoots the arrow, exactly like a bow. The other
 * hand rule lets you choose tipped, spectral or Power-enchanted real arrows over the light one; nothing is spent.</li>
 * </ul>
 * Nothing ticks: everything happens in releaseUsing. Kill switch: key {@value #KILL_KEY} in
 * {@code config/elsewhere-overhaul3.json} (or {@code -Delsewhere.overhaul3.prism_bow=false}); off, it is a plain bow.
 */
public class PrismBowItem extends BowItem {
	public static final String ID = "prism_bow";
	public static final String KILL_KEY = "prism_bow";
	/** Kill switch, read once (the self-test flips it to check the off state). */
	public static boolean ENABLED = Overhaul3.on(KILL_KEY);
	/** Raw Glimmer cost of a light arrow (scaled by Glimmer.cost like a spell). */
	public static final int GLIMMER_COST = 2;
	/** Light arrow base damage before H and the spell damage rules. */
	public static final float LIGHT_DAMAGE = 8.0F;
	/** Launch speed: a full-draw arrow's (vanilla power 1.0 x 3.0). */
	public static final float LIGHT_SPEED = 3.0F;
	/** The light arrow flies dead straight this far, then fades. */
	public static final double LIGHT_RANGE = 24.0;
	/** MagicBolt lifetime: it moves on ticks 1..lifetime+1, so (lifetime + 1) x speed = the range. */
	public static final int LIGHT_LIFETIME = (int) Math.ceil(LIGHT_RANGE / LIGHT_SPEED) - 1;
	/** White-gold (the bow's full-draw frame). */
	public static final int LIGHT_COLOR = 0xFFE9A8;

	public PrismBowItem(Properties properties) {
		super(properties.enchantable(1).repairable(Gear3Items.repairTag(ID)));
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
		if (level instanceof ServerLevel sl && entity instanceof ServerPlayer p && lightReady(stack, p, timeLeft) && Glimmer.trySpend(p, GLIMMER_COST)) {
			loose(sl, p, stack);
			return true;
		}
		return super.releaseUsing(stack, level, entity, timeLeft);
	}

	/** True when this release becomes a light arrow (before spending): switch on, ammo to draw, full draw, no arrows in the other hand, the Glimmer. */
	public boolean lightReady(ItemStack stack, ServerPlayer p, int timeLeft) {
		if (!ENABLED || p.getProjectile(stack).isEmpty() || !fullDraw(stack, p, timeLeft) || otherHandArrows(p, stack)) return false;
		return p.isCreative() || Glimmer.current(p) + 1.0E-4F >= Glimmer.cost(p, GLIMMER_COST);
	}

	/** Full power at this release, with the Quickdraw Quiver counted the way BowItem.releaseUsing's hook counts it. */
	public boolean fullDraw(ItemStack stack, LivingEntity user, int timeLeft) {
		int duration = getUseDuration(stack, user);
		return getPowerForTime(duration - Gear3Hooks.bowTimeLeft(timeLeft, duration, user)) >= 1.0F;
	}

	/** The other hand (the one not holding this bow) holds arrows the bow can shoot. */
	public boolean otherHandArrows(Player p, ItemStack bow) {
		ItemStack other = p.getItemInHand(hand(p, bow) == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		return !other.isEmpty() && getSupportedHeldProjectiles().test(other);
	}

	/** Hybrid strength H(Marksman, Caster); 1.0 when the callings switch is off (no hybrid rule then). */
	public static float hybrid(Player p) {
		return Overhaul3.CALLINGS ? Calling.hybrid(p, Calling.Path.MARKSMAN, Calling.Path.CASTER) : 1.0F;
	}

	/** The light arrow, after the Glimmer is spent. Returns it (the self-test reads it). */
	public MagicBolt loose(ServerLevel level, ServerPlayer p, ItemStack stack) {
		MagicBolt b = MagicBolt.create(level, p, LIGHT_COLOR);
		b.damage = LIGHT_DAMAGE * hybrid(p);
		b.moonlit = Glimmer.consumeMoonlit(p);
		b.pierce = (int) ArsenalAttributes.value(p, ArsenalAttributes.PROJECTILE_PIERCE);
		b.lifetime = LIGHT_LIFETIME;
		b.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, LIGHT_SPEED, 0.0F);
		level.addFreshEntity(b);
		stack.hurtAndBreak(1, p, hand(p, stack));
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
		p.awardStat(Stats.ITEM_USED.get(this));
		return b;
	}

	private static InteractionHand hand(Player p, ItemStack bow) {
		return p.getOffhandItem() == bow ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}
}
