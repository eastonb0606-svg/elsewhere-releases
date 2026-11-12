package dev.elsewhere.arsenal.spell;

import dev.elsewhere.arsenal.ArsenalItems;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** new-glimmer-motes: mobs killed by your spells or minions have a 20% chance to drop a mote worth 3 Glimmer on pickup. */
public final class GlimmerMotes {
	private GlimmerMotes() {}

	public static final float CHANCE = 0.20F;
	public static final float RESTORE = 3.0F;

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (!(victim.level() instanceof ServerLevel level)) return;
			if (shouldDrop(source, victim, level.getRandom().nextFloat())) {
				ItemEntity e = new ItemEntity(level, victim.getX(), victim.getY() + 0.4, victim.getZ(), new ItemStack(ArsenalItems.GLIMMER_MOTE));
				e.setGlowingTag(true);
				e.setPickUpDelay(10);
				level.addFreshEntity(e);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (srv.getTickCount() % 2 != 0) return;
			for (ServerPlayer p : srv.getPlayerList().getPlayers()) pickup(p);
		});
	}

	/** The player credited for a spell kill or a minion kill, or null. */
	public static @Nullable ServerPlayer credit(DamageSource source) {
		Entity k = source.getEntity();
		if (k instanceof ServerPlayer sp && source.is(Spells.SPELL)) return sp;
		if (k instanceof OwnableEntity o && k.getClass().getName().startsWith("dev.elsewhere.minion.") && o.getOwner() instanceof ServerPlayer sp) return sp;
		return null;
	}

	/** roll = a random number in [0,1); testable without randomness. */
	public static boolean shouldDrop(DamageSource source, LivingEntity victim, float roll) {
		if (victim instanceof Player || !(victim instanceof net.minecraft.world.entity.Mob)) return false;
		return credit(source) != null && roll < CHANCE;
	}

	/** Collects motes within reach: +3 Glimmer each. Returns how many were picked up. */
	public static int pickup(ServerPlayer p) {
		if (p.isSpectator() || !(p.level() instanceof ServerLevel level)) return 0;
		int n = 0;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(1.5, 1.0, 1.5), i -> i.getItem().is(ArsenalItems.GLIMMER_MOTE) && !i.hasPickUpDelay())) {
			n += e.getItem().getCount();
			Glimmer.restore(p, RESTORE * e.getItem().getCount());
			e.discard();
		}
		if (n > 0) level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
		return n;
	}
}
