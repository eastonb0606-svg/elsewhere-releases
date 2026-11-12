package dev.elsewhere.duelist.kit;

import dev.elsewhere.duelist.boss.HumanBoss;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * X4 {@code Companion}: a bound ally (wolf, falcon, war camel) with a leash, respawn rules and a health bar segment.
 * The boss calls {@link #tick} from {@code extraTick}. The ally is created by the factory, kept within
 * {@code leash} blocks of the boss (it is walked back, teleported only beyond 2x the leash) and replaced after
 * {@code respawnTicks} when it dies, up to {@code maxRespawns} times. A second boss bar titled with the ally's name is a
 * {@code boss_ui} sub-bar (list its keyword in {@code boss_bars.json} {@code sub}).
 */
public final class Companion {
	private final HumanBoss boss;
	private final Function<ServerLevel, Mob> factory;
	private final double leash;
	private final int respawnTicks;
	private int respawnsLeft;
	private @Nullable Mob ally;
	private long respawnAt = -1;
	private final ServerBossEvent bar;
	public int spawned;

	public Companion(HumanBoss boss, Function<ServerLevel, Mob> factory, double leash, int respawnTicks, int maxRespawns, Component barTitle) {
		this.boss = boss;
		this.factory = factory;
		this.leash = leash;
		this.respawnTicks = respawnTicks;
		this.respawnsLeft = maxRespawns;
		this.bar = new ServerBossEvent(java.util.UUID.randomUUID(), barTitle, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
	}

	public @Nullable Mob ally() {
		return ally != null && ally.isAlive() ? ally : null;
	}

	public void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (ally != null && !ally.isAlive()) {
			ally = null;
			if (respawnsLeft > 0 && respawnAt < 0) respawnAt = now + respawnTicks;
		}
		if (ally == null && (respawnAt < 0 && spawned == 0 || respawnAt >= 0 && now >= respawnAt)) {
			if (spawned > 0) respawnsLeft--;
			respawnAt = -1;
			Mob m = factory.apply(level);
			if (m != null) {
				Vec3 p = boss.position().add(boss.getRandom().nextDouble() * 2 - 1, 0, boss.getRandom().nextDouble() * 2 - 1);
				m.setPos(p.x, p.y, p.z);
				level.addFreshEntity(m);
				ally = m;
				spawned++;
			}
		}
		if (ally != null) {
			double d = ally.distanceTo(boss);
			if (d > leash * 2) ally.teleportTo(boss.getX(), boss.getY(), boss.getZ());
			else if (d > leash && ally.getTarget() == null && now % 10 == 0) ally.getNavigation().moveTo(boss, 1.2);
			if (now % 4 == 0) bar.setProgress(Math.clamp(ally.getHealth() / ally.getMaxHealth(), 0F, 1F));
		}
		if (now % 20 == 0) {
			for (ServerPlayer p : level.players()) {
				boolean near = ally != null && p.distanceTo(boss) < 48;
				if (near) bar.addPlayer(p);
				else bar.removePlayer(p);
			}
		}
		if (ally == null) bar.removeAllPlayers();
	}

	/** Removes the ally and the bar (boss death or reset). */
	public void dismiss() {
		bar.removeAllPlayers();
		if (ally != null) ally.discard();
		ally = null;
	}
}
