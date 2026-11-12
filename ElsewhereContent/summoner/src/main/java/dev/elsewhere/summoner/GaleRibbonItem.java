package dev.elsewhere.summoner;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Gale Ribbon (whip): a hit tags one target for 5 s; the owner's minions switch to it, hit it for +30% and ranged minions
 * gain +4 range against it. The tag ends 3 s after the last swing (a hit, or a right-click flick). It commands nothing.
 */
public class GaleRibbonItem extends Item {
	private record Tag(UUID target, long until, long lastSwing) {}

	private static final Map<UUID, Tag> TAGS = new HashMap<>();

	public GaleRibbonItem(Properties props) {
		super(props);
	}

	public static void tag(ServerPlayer owner, LivingEntity target) {
		long now = owner.level().getGameTime();
		TAGS.put(owner.getUUID(), new Tag(target.getUUID(), now + GearStats.RIBBON_TAG_TICKS, now));
	}

	public static void clear(UUID owner) {
		TAGS.remove(owner);
	}

	private static @Nullable Tag live(ServerPlayer owner) {
		Tag t = TAGS.get(owner.getUUID());
		if (t == null) return null;
		long now = owner.level().getGameTime();
		if (now > t.until() || now - t.lastSwing() > GearStats.RIBBON_IDLE_TICKS) {
			TAGS.remove(owner.getUUID());
			return null;
		}
		return t;
	}

	public static boolean isTagged(@Nullable ServerPlayer owner, Entity target) {
		if (owner == null) return false;
		Tag t = live(owner);
		return t != null && t.target().equals(target.getUUID());
	}

	public static @Nullable LivingEntity target(ServerPlayer owner) {
		Tag t = live(owner);
		if (t == null || !(owner.level() instanceof ServerLevel sl)) return null;
		return sl.getEntity(t.target()) instanceof LivingEntity le && le.isAlive() ? le : null;
	}

	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);
		if (attacker instanceof ServerPlayer p && target.isAlive()) tag(p, target);
	}

	/** A flick keeps the tag alive while you keep swinging. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			Tag t = live(sp);
			if (t != null) TAGS.put(sp.getUUID(), new Tag(t.target(), t.until(), level.getGameTime()));
		}
		return InteractionResult.SUCCESS;
	}
}
