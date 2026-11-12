package dev.elsewhere.verdigris_sea;

import dev.elsewhere.stub.verdigris_ironpeak.StubBoss;
import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Stormcaller Harpoon and its upgrade, the Galvanic Trident (BALANCE.md final). Thrown hits call lightning (the Harpoon
 * only in the Verdigris Sea, the Galvanic Trident anywhere, in any weather), 8 s per player. Melee hits reel non-boss
 * mobs 4 blocks toward you, 4 s per player. The cooldowns are internal, so they never block throwing.
 */
public class StormcallerHarpoonItem extends TridentItem {
	private final boolean everywhere;
	private final int cooldownTicks;
	private static final Map<UUID, Long> LIGHTNING = new HashMap<>(), REEL = new HashMap<>();

	public StormcallerHarpoonItem(boolean everywhere, int cooldownTicks, Properties properties) {
		super(properties);
		this.everywhere = everywhere;
		this.cooldownTicks = cooldownTicks;
	}

	public boolean worksIn(ServerLevel level) {
		return everywhere || Fx.in(level, Fx.VERDIGRIS_SEA);
	}

	private static boolean ready(Map<UUID, Long> map, Entity who, long now, int cooldown) {
		if (who == null) return true;
		Long last = map.get(who.getUUID());
		if (last != null && now - last < cooldown) return false;
		map.put(who.getUUID(), now);
		return true;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (!(attacker.level() instanceof ServerLevel level) || target instanceof StubBoss) return;
		if (!ready(REEL, attacker, level.getGameTime(), GearStats.HARPOON_REEL_COOLDOWN)) return;
		Vec3 toward = attacker.position().subtract(target.position()).multiply(1, 0, 1);
		if (toward.lengthSqr() > 1.0E-3) {
			Vec3 pull = toward.normalize().scale(Math.min(4.0, toward.length() - 1.0) * 0.32);
			target.setDeltaMovement(target.getDeltaMovement().add(pull.x, 0.15, pull.z));
			target.needsSync = true;
		}
	}

	/** Called by the thrown-trident hook when a thrown Harpoon / Galvanic Trident hits an entity. */
	public void thrownHit(ServerLevel level, LivingEntity target, @Nullable Entity owner) {
		if (!worksIn(level) || !ready(LIGHTNING, owner, level.getGameTime(), cooldownTicks)) return;
		Fx.lightning(level, target.position(), false, owner instanceof ServerPlayer sp ? sp : null);
	}
}
