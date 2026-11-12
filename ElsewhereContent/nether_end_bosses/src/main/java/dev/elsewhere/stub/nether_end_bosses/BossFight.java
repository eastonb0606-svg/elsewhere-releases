package dev.elsewhere.stub.nether_end_bosses;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Stub of the core's fight controller, owned by the boss's primary body.
 * Seal + snapshot on begin; participants join by standing in the arena; leaving for 30 s drops you out;
 * everyone gone resets the boss and restores the arena; a win restores the arena and rolls loot per player.
 */
public final class BossFight {
	public static final int DROPOUT_TICKS = 600;
	public static final int SCALING_WINDOW_TICKS = 600;
	public static final int SURVIVE_FOR_LOOT_TICKS = 1200;
	public static final Identifier PARTY_SCALING = Identifier.fromNamespaceAndPath("elsewhere", "party_scaling");
	public static final String FIGHT_TAG_PREFIX = "elsewhere_fight_";

	public static final class Participant {
		public final ServerPlayer player;
		public float damage;
		public int ticksInside;
		public int ticksOutside;
		public Vec3 lastSafe;
		public boolean freeVoidReturnUsed;

		Participant(ServerPlayer player) {
			this.player = player;
			this.lastSafe = player.position();
		}
	}

	private final ElsewhereBoss boss;
	private static final Map<UUID, Long> HALO_RESCUES = new java.util.HashMap<>();
	public final BossArena arena;
	private final @Nullable UUID summoner;
	private ItemStack returnItem;
	private final Map<UUID, Participant> participants = new LinkedHashMap<>();
	private final List<ServerBossEvent> bars = new ArrayList<>();
	public final ServerBossEvent mainBar;
	private int age;
	private float totalDamage;
	private Component flash;
	private int flashTicks;
	private boolean over;
	private int peakPlayers = 1;
	/** A spectator or creative player is inside the arena: the fight does not reset for lack of participants (QA: bosses vanished while observed). */
	private boolean viewersInside;
	/** Set by the self-test so drop-outs and resets happen without waiting. */
	public static int dropoutTicksOverride = -1;

	BossFight(ElsewhereBoss boss, BossArena arena, @Nullable ServerPlayer summoner, ItemStack returnItem) {
		this.boss = boss;
		this.arena = arena;
		this.summoner = summoner == null ? null : summoner.getUUID();
		this.returnItem = returnItem.copy();
		if (returnItem.isEmpty() && !boss.pendingReturnItem.isEmpty()) this.returnItem = boss.pendingReturnItem;
		this.mainBar = new ServerBossEvent(UUID.randomUUID(), boss.getDisplayName(), boss.barColor(), boss.barOverlay());
		this.mainBar.setDarkenScreen(boss.level().dimension() == net.minecraft.world.level.Level.END);
		bars.add(mainBar);
	}

	public ElsewhereBoss boss() {
		return boss;
	}

	public ItemStack returnItem() {
		return returnItem;
	}

	public String fightTag() {
		return FIGHT_TAG_PREFIX + boss.getUUID().toString().substring(0, 8);
	}

	/** Adds an extra bar (rider/mount sub-bar, DPS timer ...). */
	public ServerBossEvent addBar(Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
		ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), name, color, overlay);
		for (Participant p : participants.values()) bar.addPlayer(p.player);
		bars.add(bar);
		return bar;
	}

	public void removeBar(ServerBossEvent bar) {
		bar.removeAllPlayers();
		bars.remove(bar);
	}

	/** Who sees the bars: anyone inside the arena volume (survival, creative and spectators; only non-creative, non-spectators become participants). */
	public static boolean barViewer(ServerPlayer p, BossArena arena) {
		return p.isAlive() && !p.isRemoved() && arena.contains(p);
	}

	/** Adds the bars for every player inside the arena and removes them from non-participants who left. */
	private void syncViewers(ServerLevel level) {
		boolean extra = false;
		for (ServerPlayer p : level.players()) {
			boolean in = barViewer(p, arena);
			boolean part = participants.containsKey(p.getUUID());
			if (in && !part) extra = true;
			for (ServerBossEvent bar : bars) {
				if (in) bar.addPlayer(p);
				else if (!part) bar.removePlayer(p);
			}
		}
		this.viewersInside = extra;
	}

	void begin(ServerLevel level) {
		arena.snapshot(level);
		arena.seal(level, 12000);
		dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightStart(boss.getUUID(), boss.bossId(), summoner, returnItem.isEmpty() ? boss.defaultSummonItem() : returnItem,
			level.dimension().identifier().toString(), boss.blockPosition().getX(), boss.blockPosition().getY(), boss.blockPosition().getZ());
		for (ServerPlayer p : level.players()) if (arena.contains(p) && !p.isSpectator()) join(p);
		syncViewers(level);
		level.playSound(null, boss.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
	}

	void resume(ServerLevel level) {
		// no snapshot: a reset rebuilds the arena from the boss's generator instead
	}

	public void join(ServerPlayer player) {
		if (participants.containsKey(player.getUUID())) return;
		participants.put(player.getUUID(), new Participant(player));
		for (ServerBossEvent bar : bars) bar.addPlayer(player);
		if (age <= SCALING_WINDOW_TICKS) rescale();
	}

	public void leave(ServerPlayer player) {
		Participant p = participants.remove(player.getUUID());
		if (p != null) for (ServerBossEvent bar : bars) bar.removePlayer(player);
		onLeave(player);
	}

	private void onLeave(ServerPlayer player) {
		AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
		if (gravity != null) {
			gravity.removeModifier(StubFramework.FIGHT_GRAVITY);
		}
	}

	/** HP ×(1 + 0.5 per extra player) for players present at the start or joining within 30 s. Keeps the HP ratio. */
	private void rescale() {
		int n = Math.max(1, participants.size());
		if (n <= peakPlayers && age > 0) return;
		peakPlayers = Math.max(peakPlayers, n);
		List<ElsewhereBoss> bodies = bodies();
		for (ElsewhereBoss b : bodies) {
			AttributeInstance max = b.getAttribute(Attributes.MAX_HEALTH);
			if (max == null) continue;
			float ratio = b.getHealth() / b.getMaxHealth();
			max.removeModifier(PARTY_SCALING);
			if (peakPlayers > 1) max.addPermanentModifier(new AttributeModifier(PARTY_SCALING, 0.5 * (peakPlayers - 1), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			b.setHealth(b.getMaxHealth() * ratio);
		}
	}

	/** All bodies sharing this fight (rider + mount for linked bosses). */
	public List<ElsewhereBoss> bodies() {
		List<ElsewhereBoss> out = new ArrayList<>();
		out.add(boss);
		if (boss instanceof LinkedBodies linked) out.addAll(linked.linkedBodies());
		return out;
	}

	public List<ServerPlayer> activePlayers() {
		List<ServerPlayer> out = new ArrayList<>();
		for (Participant p : participants.values()) {
			if (p.player.isAlive() && !p.player.isRemoved() && !p.player.isSpectator() && p.player.level() == boss.level() && p.ticksOutside == 0) out.add(p.player);
		}
		return out;
	}

	public Map<UUID, Participant> participants() {
		return participants;
	}

	public @Nullable Participant participant(Player player) {
		return participants.get(player.getUUID());
	}

	public void recordDamage(@Nullable Entity attacker, float dealt) {
		totalDamage += dealt;
		if (attacker instanceof ServerPlayer p) {
			Participant part = participants.get(p.getUUID());
			if (part == null && arena.contains(p)) {
				join(p);
				part = participants.get(p.getUUID());
			}
			if (part != null) part.damage += dealt;
		}
	}

	/** Shows text after the boss name on the bar for a while (attack names, phase changes). */
	public void flash(Component text, int ticks) {
		this.flash = text;
		this.flashTicks = ticks;
	}

	public void tick(ServerLevel level) {
		if (over) return;
		age++;
		if (age % 10 == 0) {
			syncViewers(level);
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && !p.isCreative() && arena.contains(p) && !participants.containsKey(p.getUUID())) join(p);
			}
		}
		int dropout = dropoutTicksOverride >= 0 ? dropoutTicksOverride : DROPOUT_TICKS;
		List<ServerPlayer> gone = new ArrayList<>();
		for (Participant p : participants.values()) {
			ServerPlayer player = p.player;
			if (player.isRemoved() || !player.isAlive() || player.level() != level) {
				gone.add(player);
				continue;
			}
			if (arena.fellIntoVoid(player)) {
				voidReturn(level, p);
				continue;
			}
			if (arena.contains(player)) {
				p.ticksInside++;
				p.ticksOutside = 0;
				if (player.onGround()) p.lastSafe = player.position();
			} else if (++p.ticksOutside >= dropout) {
				player.sendSystemMessage(Component.translatable("boss.elsewhere.dropped_out").withStyle(ChatFormatting.GRAY));
				gone.add(player);
			}
		}
		for (ServerPlayer p : gone) leave(p);
		if (age <= SCALING_WINDOW_TICKS && age % 20 == 0) rescale();
		if (participants.isEmpty() && age > 40 && !viewersInside) {
			reset(level);
			return;
		}
		updateBars();
	}

	private void updateBars() {
		float ratio = boss.getHealth() / boss.getMaxHealth();
		mainBar.setProgress(Math.max(0, Math.min(1, ratio)));
		if (flashTicks > 0) {
			flashTicks--;
			mainBar.setName(Component.empty().append(boss.getDisplayName()).append(Component.literal("  ·  ").withStyle(ChatFormatting.GRAY))
				.append(flash.copy().withStyle(ChatFormatting.BOLD)));
		} else {
			mainBar.setName(boss.getDisplayName());
		}
	}

	/** Void arenas: "return to the ledge" for 8 HP instead of a death. The Halo of the Outer End makes one per fight free. */
	public void voidReturn(ServerLevel level, Participant p) {
		ServerPlayer player = p.player;
		Vec3 to = p.lastSafe != null && arena.contains(p.lastSafe) ? p.lastSafe : arena.ledge;
		player.teleportTo(level, to.x, to.y + 0.1, to.z, java.util.Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		long now = level.getGameTime();
		Long last = HALO_RESCUES.get(player.getUUID());
		boolean rescue = player.getItemBySlot(EquipmentSlot.HEAD).is(StubFramework.HALO_OF_THE_OUTER_END) && (last == null || now - last >= StubFramework.HALO_RESCUE_COOLDOWN_TICKS);
		if (rescue) {
			HALO_RESCUES.put(player.getUUID(), now);
			player.sendSystemMessage(Component.translatable("boss.elsewhere.halo_return").withStyle(ChatFormatting.LIGHT_PURPLE));
			player.hurtServer(level, boss.source(StubFramework.VOID_RETURN, null), StubFramework.HALO_RESCUE_DAMAGE);
		} else {
			player.hurtServer(level, boss.source(StubFramework.VOID_RETURN, null), 8.0F);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	/** Everyone left or died: restore the arena, remove the boss and its minions, give back the summoning item. */
	public void reset(ServerLevel level) {
		if (over) return;
		over = true;
		cleanup(level);
		// bal-boss-retry: the summon item goes back to the summoner (after the 30 s retry delay, persisted by the kit)
		if (dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightWiped(boss.getUUID(), "wipe")) returnItem = ItemStack.EMPTY;
		else giveBackSummonItem(level);
		boss.onFightReset(level);
	}

	/** Win: restore the arena, roll loot for every eligible participant. {@code mercy} switches to the mercy table. */
	public void victory(ServerLevel level, @Nullable String variant) {
		if (over) return;
		over = true;
		List<Participant> eligible = new ArrayList<>();
		for (Participant p : participants.values()) {
			boolean dealtEnough = totalDamage > 0 && p.damage >= totalDamage * 0.05F;
			if (dealtEnough || p.ticksInside >= SURVIVE_FOR_LOOT_TICKS) eligible.add(p);
		}
		if (eligible.isEmpty()) {
			// nobody qualifies (e.g. killed by a command): the top damage dealer, else everyone, still gets a roll
			eligible.addAll(participants.values());
		}
		for (Participant p : eligible) PersonalLoot.deliver(level, boss, p.player, variant);
		lastLootRecipients = eligible.size();
		lastXp = dev.elsewhere.boss.kit.BossRewards.awardXp(level, dropPoint(), boss.bossXp());
		dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightWon(boss.getUUID());
		cleanup(level);
		giveBackSummonItem(level);
	}

	public int lastLootRecipients;
	public int lastXp;

	/** Where loot and XP land: the ledge, so nothing falls into the void when a flying boss dies over it. */
	public Vec3 dropPoint() {
		return arena.ledge;
	}

	private void giveBackSummonItem(ServerLevel level) {
		if (returnItem.isEmpty()) return;
		ServerPlayer owner = summoner == null ? null : level.getServer().getPlayerList().getPlayer(summoner);
		Vec3 at = owner != null && owner.level() == level ? owner.position() : arena.ledge;
		ItemEntity item = new ItemEntity(level, at.x, at.y + 0.5, at.z, returnItem.copy());
		item.setUnlimitedLifetime();
		item.setNoPickUpDelay();
		level.addFreshEntity(item);
		returnItem = ItemStack.EMPTY;
	}

	private void cleanup(ServerLevel level) {
		for (ServerBossEvent bar : bars) bar.removeAllPlayers();
		for (Participant p : participants.values()) onLeave(p.player);
		String tag = fightTag();
		for (Entity e : level.getEntities((Entity) null, arena.box().inflate(48), e -> e.entityTags().contains(tag))) e.discard();
		// boss projectiles still in flight would land after the restore (soul fire, magma ...): remove them first
		List<ElsewhereBoss> owners = bodies();
		for (Entity e : level.getEntities((Entity) null, arena.box().inflate(48),
			e -> e instanceof net.minecraft.world.entity.projectile.Projectile pr && owners.contains(pr.getOwner()))) e.discard();
		for (Entity e : level.getEntities((Entity) null, arena.box().inflate(8), e -> e instanceof net.minecraft.world.entity.item.FallingBlockEntity)) e.discard();
		if (arena.hasSnapshot()) {
			arena.restore(level);
		} else if (boss.home() != null) {
			boss.rebuildArena(level, boss.home());
		}
	}

	public boolean isOver() {
		return over;
	}

	public int age() {
		return age;
	}

	/** Tags a minion as part of this fight (cleaned up on reset/win, blocked from hurting the boss). */
	public <T extends LivingEntity> T minion(T mob) {
		mob.addTag(Guards.MINION_TAG);
		mob.addTag(fightTag());
		return mob;
	}

	public int minionCount(ServerLevel level) {
		String tag = fightTag();
		return level.getEntities((Entity) null, arena.box().inflate(16), e -> e.isAlive() && e.entityTags().contains(tag) && e instanceof LivingEntity).size();
	}

	/** Minion cap scaled per player. */
	public int minionCap(int perPlayer) {
		return perPlayer * Math.max(1, activePlayers().size());
	}

	public BlockPos origin() {
		return arena.center;
	}
}
