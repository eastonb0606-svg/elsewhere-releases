package dev.elsewhere.gear3.s3.galesling;

import dev.elsewhere.gear3.Gear3Items;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;

/**
 * The Gale Sling item (catalogue mechanic {@code "gale_sling"}). A {@link ProjectileWeaponItem} so {@link Player#getProjectile}
 * finds its ammo the vanilla way: a held stack (off hand first) wins, then the inventory in slot order; creative needs none.
 * Uses the trident's wind-up (arm raised, shaking at full charge; the vanilla first-person charge is exactly 10 ticks), and the
 * item model whirls the pouch overhead while it is used ({@code minecraft:use_cycle}).
 */
public class GaleSlingItem extends ProjectileWeaponItem {
	public static final Predicate<ItemStack> AMMO = s -> s.is(Items.COBBLESTONE) || s.is(Items.GRAVEL) || s.is(Items.WIND_CHARGE);
	public static final int USE_TICKS = 72000;

	public GaleSlingItem(Properties props) {
		super(props.repairable(Gear3Items.repairTag(GaleSling.ID)));
	}

	@Override
	public Predicate<ItemStack> getAllSupportedProjectiles() {
		return AMMO;
	}

	@Override
	public int getDefaultProjectileRange() {
		return GaleSling.RANGE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return USE_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TRIDENT;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.getProjectile(player.getItemInHand(hand)).isEmpty()) return InteractionResult.FAIL;
		player.startUsingItem(hand);
		if (!level.isClientSide())
			level.playSound(null, player.getX(), player.getY(), player.getZ(), GaleSling.WHIRL, SoundSource.PLAYERS, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		return InteractionResult.CONSUME;
	}

	@Override
	public boolean releaseUsing(ItemStack sling, Level level, LivingEntity user, int timeLeft) {
		if (!(user instanceof Player p)) return false;
		float power = GaleSling.power(getUseDuration(sling, user) - timeLeft);
		if (power < GaleSling.MIN_POWER) return false;
		ItemStack ammo = p.getProjectile(sling);
		if (ammo.isEmpty()) return false;
		if (level instanceof ServerLevel sl) loose(sl, p, p.getUsedItemHand(), sling, ammo, power);
		p.awardStat(Stats.ITEM_USED.get(this));
		return true;
	}

	/**
	 * Server: one shot with {@code ammo} at charge {@code power}. A wind charge flies as a vanilla wind charge; anything else
	 * (cobblestone, gravel, or the creative stand-in when no ammo is carried) as a {@link SlingStone}. Uses 1 durability and
	 * 1 ammo (none with infinite materials). Returns the projectile, already added to the level.
	 */
	public static Projectile loose(ServerLevel level, Player p, InteractionHand hand, ItemStack sling, ItemStack ammo, float power) {
		Projectile shot;
		if (ammo.is(Items.WIND_CHARGE)) {
			WindCharge charge = new WindCharge(p, level, p.position().x(), p.getEyePosition().y(), p.position().z());
			charge.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, GaleSling.WIND_SPEED * power, GaleSling.INACCURACY);
			shot = charge;
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
		} else {
			SlingStone stone = new SlingStone(level, p, ammo.is(Items.GRAVEL), power, GaleSling.marksmanMult(p));
			stone.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, GaleSling.STONE_SPEED * power, GaleSling.INACCURACY);
			shot = stone;
		}
		level.addFreshEntity(shot);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), GaleSling.LOOSE, SoundSource.PLAYERS, 0.8F, 0.85F + 0.3F * power);
		sling.hurtAndBreak(1, p, hand.asEquipmentSlot());
		if (!p.hasInfiniteMaterials()) {
			ammo.shrink(1);
			if (ammo.isEmpty()) p.getInventory().removeItem(ammo);
		}
		return shot;
	}

	@Override
	protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, LivingEntity target) {
		projectile.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot() + angle, 0.0F, velocity, inaccuracy);
	}
}
