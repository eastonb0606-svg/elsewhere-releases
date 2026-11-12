package dev.elsewhere.duelist.kit;

import dev.elsewhere.duelist.boss.HumanBoss;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Khopesh hook support. A khopesh hit on a raised shield hooks it down, with the numbers its owner lane sets in
 * {@link #params} (how long, once per attacker per N ticks, once per squad per M ticks). <b>Off until a lane sets
 * {@code params}</b>: there are no default numbers. Items in the tag {@code elsewhere:khopesh} hook automatically once it
 * is on (the Azmar lane tags its khopesh family); anything else can call {@link #hook} directly.
 */
public final class ShieldHook {
	private ShieldHook() {}

	public static final TagKey<Item> KHOPESH = TagKey.create(Registries.ITEM, dev.elsewhere.duelist.DuelistContent.id("khopesh"));
	/** Maps an attacker to its squad key (default: its own UUID; HumanBoss uses {@code squadKey}). The war lane can replace it. */
	public static Function<LivingEntity, Object> squadKeyOf = a -> a instanceof HumanBoss b ? b.squadKey : a.getUUID();
	/** The hook's numbers: how long the shield is down, once per attacker per {@code attackerCooldown}, once per squad per {@code squadCooldown}. The Azmar lane sets these for its khopesh. */
	public record Params(int disableTicks, int attackerCooldown, int squadCooldown) {}

	/** Null (the start state) = the hook is off. */
	public static volatile @org.jspecify.annotations.Nullable Params params;
	/** Which held items count as a khopesh. */
	public static Predicate<ItemStack> isHook = s -> s.is(KHOPESH);

	private static final Map<UUID, Long> ATTACKER = new HashMap<>();
	private static final Map<Object, Long> SQUAD = new HashMap<>();
	private static boolean inited;
	/** Hooks done since start (tests). */
	public static int hooks;

	public static synchronized void init() {
		if (inited) return;
		inited = true;
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, taken, blocked) -> {
			if (!blocked || !(source.getDirectEntity() instanceof LivingEntity a) || !(victim.level() instanceof ServerLevel l)) return;
			if (isHook.test(a.getMainHandItem())) hook(l, a, squadKeyOf.apply(a), victim);
		});
	}

	/** Hooks {@code victim}'s raised shield down. Returns true when the hook landed (shield was up and the limits allowed it). */
	public static boolean hook(ServerLevel level, LivingEntity attacker, Object squadKey, LivingEntity victim) {
		Params params = ShieldHook.params;
		if (params == null) return false;
		if (!victim.isBlocking() && !(victim instanceof HumanBoss hb && hb.shieldUp())) return false;
		long now = level.getGameTime();
		if (ATTACKER.getOrDefault(attacker.getUUID(), -1000L) + params.attackerCooldown() > now) return false;
		if (SQUAD.getOrDefault(squadKey, -1000L) + params.squadCooldown() > now) return false;
		ATTACKER.put(attacker.getUUID(), now);
		SQUAD.put(squadKey, now);
		if (ATTACKER.size() > 256) ATTACKER.values().removeIf(t -> t + params.attackerCooldown() < now);
		if (SQUAD.size() > 256) SQUAD.values().removeIf(t -> t + params.squadCooldown() < now);
		disable(level, victim, params.disableTicks());
		hooks++;
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.HOSTILE, 1F, 1.3F);
		return true;
	}

	/** Takes {@code victim}'s shield down for {@code ticks} (player: an item cooldown, like an axe does). */
	public static void disable(ServerLevel level, LivingEntity victim, int ticks) {
		if (victim instanceof HumanBoss b) {
			b.disableShield(ticks);
			return;
		}
		ItemStack with = victim.getItemBlockingWith();
		victim.stopUsingItem();
		if (victim instanceof Player p) {
			for (ItemStack s : new ItemStack[] {p.getMainHandItem(), p.getOffhandItem()}) if (s.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) p.getCooldowns().addCooldown(s, ticks);
			if (with != null && !with.isEmpty()) p.getCooldowns().addCooldown(with, ticks);
		}
	}

	public static void reset() {
		ATTACKER.clear();
		SQUAD.clear();
		hooks = 0;
	}
}
