package dev.elsewhere.arsenal.spell;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * new-repeater, Clockwork Repeater: a crossbow that loads 3 arrows (hold use, 4 s, slow) and fires them in a burst (use again, one
 * every 4 ticks, slight spread). Not in the crossbow enchantment tag, so Multishot (and Quick Charge, Piercing) do not apply;
 * durability enchantments do (tag enchantable/durability).
 */
public class RepeaterItem extends Item {
	public static final int RELOAD_TICKS = dev.elsewhere.arsenal.Overhaul3.REBALANCE_ARSENAL ? 60 : 80, BURST = 3, GAP = 4, COOLDOWN = 24;
	public static final String KEY = "loaded";
	private static final Map<UUID, int[]> BURSTS = new HashMap<>(); // player -> {remaining, tickCounter}

	public RepeaterItem(Properties props) {
		super(props);
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (BURSTS.isEmpty()) return;
			BURSTS.entrySet().removeIf(en -> {
				ServerPlayer p = srv.getPlayerList().getPlayer(en.getKey());
				if (p == null || !p.isAlive()) return true;
				int[] st = en.getValue();
				if (++st[1] % GAP != 0) return false;
				fireOne(p);
				return --st[0] <= 0;
			});
		});
	}

	public static int loaded(ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		return d == null ? 0 : d.copyTag().getIntOr(KEY, 0);
	}

	public static void setLoaded(ItemStack s, int n) {
		CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putInt(KEY, n));
	}

	public static boolean bursting(Player p) {
		return BURSTS.containsKey(p.getUUID());
	}

	private static boolean hasAmmo(Player p) {
		if (p.hasInfiniteMaterials()) return true;
		int n = 0;
		for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.getItem() instanceof ArrowItem) n += s.getCount();
		return n >= BURST;
	}

	private static void takeAmmo(Player p) {
		if (p.hasInfiniteMaterials()) return;
		int need = BURST;
		for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
			if (need <= 0) break;
			if (s.getItem() instanceof ArrowItem) { int t = Math.min(need, s.getCount()); s.shrink(t); need -= t; }
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (loaded(stack) > 0) {
			if (!level.isClientSide() && player instanceof ServerPlayer sp && !bursting(player)) fireBurst(sp, stack);
			return InteractionResult.CONSUME;
		}
		if (!hasAmmo(player)) return InteractionResult.FAIL;
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	/** Starts the burst with the loaded arrows (also used by the self-test). */
	public static void fireBurst(ServerPlayer p, ItemStack stack) {
		int n = loaded(stack);
		if (n <= 0) return;
		setLoaded(stack, 0);
		BURSTS.put(p.getUUID(), new int[] {n, GAP - 1}); // first arrow next tick
		p.getCooldowns().addCooldown(stack, COOLDOWN + n * GAP);
	}

	private static void fireOne(ServerPlayer p) {
		ServerLevel level = p.level();
		ItemStack weapon = p.getMainHandItem().getItem() instanceof RepeaterItem ? p.getMainHandItem() : p.getOffhandItem();
		ItemStack ammo = new ItemStack(Items.ARROW);
		AbstractArrow a = ((ArrowItem) Items.ARROW).createArrow(level, ammo, p, weapon);
		a.shootFromRotation(p, p.getXRot(), p.getYRot(), 0F, 3.15F, 1.6F);
		a.pickup = p.hasInfiniteMaterials() ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.DISALLOWED;
		level.addFreshEntity(a);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F + p.getRandom().nextFloat() * 0.2F);
		if (weapon.getItem() instanceof RepeaterItem) weapon.hurtAndBreak(1, p, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
		return false; // an unfinished reload loads nothing
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!level.isClientSide() && entity instanceof Player p && loaded(stack) == 0 && hasAmmo(p)) {
			takeAmmo(p);
			setLoaded(stack, BURST);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
		}
		return stack;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return RELOAD_TICKS;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.CROSSBOW;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return loaded(stack) > 0;
	}
}
