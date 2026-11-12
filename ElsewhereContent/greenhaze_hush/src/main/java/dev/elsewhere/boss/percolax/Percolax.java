package dev.elsewhere.boss.percolax;

import dev.elsewhere.greenhaze.GearStats;
import dev.elsewhere.greenhaze.GreenLeafBridge;
import dev.elsewhere.greenhaze.GreenhazeBlocks;
import dev.elsewhere.greenhaze.GreenhazeEntities;
import dev.elsewhere.greenhaze.GreenhazeItems;
import dev.elsewhere.greenhaze.GreenhazeMobs;
import dev.elsewhere.greenhaze.GreenhazeStructures;
import dev.elsewhere.greenhaze.Haze;
import dev.elsewhere.greenhaze_hush.BossUtil;
import dev.elsewhere.greenhaze_hush.GHProjectile;
import dev.elsewhere.greenhaze_hush.world.Sink;
import dev.elsewhere.stub.greenhaze_hush.ArenaSeal;
import dev.elsewhere.stub.greenhaze_hush.BossEntity;
import dev.elsewhere.stub.greenhaze_hush.SignatureGear;
import dev.elsewhere.stub.greenhaze_hush.StubBlocks;
import dev.elsewhere.stub.greenhaze_hush.Telegraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Percolax, the Hotbox Colossus (BOSSES.md 17). Tier III, 520 base HP, armor 14, toughness 4.
 * Phase 1 Sativa Haze: Big Rip (1.5 s inhale, 60 deg cone, 44 raw), Bubble Stomp (30 raw ring, jump it), Ember Flick (3 x 6 + fire);
 * hides in thick smoke where only the glowing bowl ember can be hit.
 * Phase 2 Hotbox: vents slam shut, smoke adds +5% high every 5 s, Couch-lock roots at >= 85%; channel a lever 2 s to open a vent,
 * Percolax walks over to re-close it and takes x2 damage from behind while closing.
 * Phase 3 Strain Shift: Sativa rings / Indica pools / Hybrid both, every 15 s; it coughs after every second Big Rip (3 s, x2 damage).
 */
public class Percolax extends BossEntity {
	public static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(Percolax.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Integer> STRAIN = SynchedEntityData.defineId(Percolax.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(Percolax.class, EntityDataSerializers.BOOLEAN);
	public static final EntityDataAccessor<Boolean> EMBER = SynchedEntityData.defineId(Percolax.class, EntityDataSerializers.BOOLEAN);
	public static final int ANIM_IDLE = 0, ANIM_RIP = 1, ANIM_EXHALE = 2, ANIM_STOMP = 3, ANIM_COUGH = 4, ANIM_CLOSE = 5, ANIM_FLICK = 6;
	public static final int SATIVA = 0, INDICA = 1, HYBRID = 2;

	public static final double BASE_HP = 520, ARMOR = 14, TOUGHNESS = 4;
	public static final float BIG_RIP_RAW = 44, STOMP_RAW = 30, EMBER_RAW = 6, RING_RAW = 10;

	private int busy, rips, hideTicks, coughTicks, closingTicks, strainTicks;
	private final List<Vent> vents = new ArrayList<>();
	private Vent closing;
	private final Map<UUID, Integer> couchLock = new HashMap<>();
	private final List<Vec3> indicaPools = new ArrayList<>();
	private final List<Integer> poolTicks = new ArrayList<>();
	/** Counters for the self-test. */
	public int bigRips, stomps, flicks, ventsClosed, ventsReclosed, coughs, strainShifts, ringsFired, poolsDropped;

	static final class Vent {
		final List<BlockPos> shutters = new ArrayList<>();
		BlockPos lever;
		boolean open = true;
	}

	public Percolax(EntityType<? extends Percolax> type, Level level) {
		super(type, level, BossEvent.BossBarColor.GREEN);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HP).add(Attributes.ARMOR, ARMOR)
			.add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS).add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 48).add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(ANIM, 0);
		b.define(STRAIN, SATIVA);
		b.define(HIDDEN, false);
		b.define(EMBER, false);
	}

	public int anim() { return entityData.get(ANIM); }
	public int strain() { return entityData.get(STRAIN); }
	public boolean hidden() { return entityData.get(HIDDEN); }
	public boolean emberGlow() { return entityData.get(EMBER); }

	@Override public String bossId() { return "percolax"; }
	@Override public double baseHealth() { return BASE_HP; }
	@Override protected float[] phaseThresholds() { return new float[]{0.65F, 0.30F}; }

	// ---------------------------------------------------------------------------------------------- summon

	/** Sneak-use the altar with a lit Green Leaf joint/blunt/vape (puff-puff-pass), or 4 Haze Resin without Green Leaf. */
	public static boolean tryAltar(ServerLevel level, BlockPos altar, Player player, ItemStack stack) {
		if (!player.isShiftKeyDown()) return false;
		boolean smokable = GreenLeafBridge.isSmokable(stack);
		boolean resin = stack.is(GreenhazeItems.HAZE_RESIN) && stack.getCount() >= 4;
		if (!smokable && !resin) {
			player.sendOverlayMessage(Component.literal("The altar wants a lit joint, blunt or vape passed to it (or 4 Haze Resin).")
				.withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (active(level, altar)) return false;
		ItemStack spent = resin ? new ItemStack(GreenhazeItems.HAZE_RESIN, 4) : ItemStack.EMPTY;
		if (resin) stack.shrink(4);
		else if (stack.isDamageableItem()) stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
		level.playSound(null, altar, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.HOSTILE, 3F, 0.5F);
		Percolax boss = summon(level, altar);
		if (!spent.isEmpty()) boss.setSummonItem(spent);
		return true;
	}

	static boolean active(ServerLevel level, BlockPos center) {
		for (ArenaSeal a : ArenaSeal.ACTIVE) if (a.level == level && a.center.closerThan(center, 4)) return true;
		return false;
	}

	public static Percolax summon(ServerLevel level, BlockPos altar) {
		ArenaSeal arena = ArenaSeal.create(level, altar, 15, 2, 17);
		Percolax boss = GreenhazeEntities.PERCOLAX.create(level, EntitySpawnReason.TRIGGERED);
		boss.snapTo(altar.getX() + 0.5, altar.getY(), altar.getZ() - 6.5, 0F, 0F);
		boss.setArena(arena);
		boss.findVents(level);
		level.addFreshEntity(boss);
		arena.seal(StubBlocks.ARENA_SEAL);
		boss.playBoss(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 3F, 0.4F);
		return boss;
	}

	/** Dev: build The Great Hotbox here and summon. */
	public static Percolax buildAndSummon(ServerLevel level, BlockPos origin) {
		GreenhazeStructures.GREAT_HOTBOX.build(Sink.live(level), origin, RandomSource.create(origin.asLong()));
		return summon(level, origin);
	}

	void findVents(ServerLevel level) {
		vents.clear();
		if (arena == null) return;
		BlockPos c = arena.center;
		List<BlockPos> levers = new ArrayList<>(), shutters = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-17, -1, -17), c.offset(17, 12, 17))) {
			BlockState s = level.getBlockState(p);
			if (s.is(GreenhazeBlocks.VENT_LEVER)) levers.add(p.immutable());
			else if (s.is(GreenhazeBlocks.HOTBOX_VENT)) shutters.add(p.immutable());
		}
		for (BlockPos lever : levers) {
			Vent v = new Vent();
			v.lever = lever;
			for (BlockPos s : shutters) if (Math.abs(s.getX() - lever.getX()) <= 3 && Math.abs(s.getZ() - lever.getZ()) <= 3) v.shutters.add(s);
			vents.add(v);
		}
	}

	private void setVent(ServerLevel level, Vent v, boolean open) {
		v.open = open;
		for (BlockPos s : v.shutters) {
			BlockState st = level.getBlockState(s);
			if (st.is(GreenhazeBlocks.HOTBOX_VENT)) level.setBlock(s, st.setValue(GreenhazeBlocks.HotboxVentBlock.OPEN, open), 3);
		}
		level.playSound(null, v.lever, open ? SoundEvents.BAMBOO_WOOD_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 2F, 0.6F);
	}

	public int closedVents() {
		int n = 0;
		for (Vent v : vents) if (!v.open) n++;
		return n;
	}

	public int ventCount() {
		return vents.size();
	}

	// ---------------------------------------------------------------------------------------------- levers

	private static final Map<UUID, long[]> CHANNEL = new HashMap<>();

	/** Holding use on a lever for 2 s opens its vent. Called on every use packet (repeats while held). */
	public static void channelLever(ServerLevel level, BlockPos lever, Player player) {
		long now = level.getGameTime();
		long[] st = CHANNEL.get(player.getUUID());
		if (st == null || now - st[1] > 8 || st[2] != lever.asLong()) st = new long[]{now, now, lever.asLong()};
		st[1] = now;
		CHANNEL.put(player.getUUID(), st);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, lever.getX() + 0.5, lever.getY() + 1, lever.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.01);
		if (now - st[0] >= 40) {
			CHANNEL.remove(player.getUUID());
			for (Percolax p : level.getEntitiesOfClass(Percolax.class, new net.minecraft.world.phys.AABB(lever).inflate(32))) p.leverPulled(level, lever);
		} else if ((now - st[0]) % 10 == 0) {
			player.sendOverlayMessage(Component.literal("Opening vent... " + (int) ((now - st[0]) * 100 / 40) + "%").withStyle(ChatFormatting.GREEN));
		}
	}

	/** Test hook: instantly complete a 2 s channel on this lever. */
	public void leverPulled(ServerLevel level, BlockPos lever) {
		for (Vent v : vents) if (v.lever.equals(lever) && !v.open) {
			setVent(level, v, true);
			announce("Vent opened!", ChatFormatting.GREEN);
			if (phase >= 1 && closing == null) {
				closing = v;
				closingTicks = 0;
			}
		}
	}

	// ---------------------------------------------------------------------------------------------- fight

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		busy = 0;
		entityData.set(HIDDEN, false);
		entityData.set(ANIM, ANIM_IDLE);
		if (phase == 1) announce("Hotbox!", ChatFormatting.DARK_GREEN);
		if (phase == 2) { announce("Strain Shift", ChatFormatting.GOLD); strainTicks = 0; }
	}

	@Override
	protected void bossTick(ServerLevel level) {
		List<ServerPlayer> players = fighters();
		ServerPlayer target = players.isEmpty() ? null : BossUtil.sortedByDistance(players, this).get(0);
		if (target != null) setTarget(target);
		tickSmoke(level, players);
		tickPools(level, players);
		if (phase >= 2) tickStrain(level);
		if (coughTicks > 0) {
			coughTicks--;
			getNavigation().stop();
			if (coughTicks % 10 == 0) level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 5, getZ(), 15, 0.8, 0.8, 0.8, 0.05);
			if (coughTicks == 0) entityData.set(ANIM, ANIM_IDLE);
			return;
		}
		if (phase >= 1 && tickVents(level, players)) return;
		if (busy > 0) { busy--; return; }
		if (target == null) return;
		double dist = distanceTo(target);
		getLookControl().setLookAt(target);
		if (dist > 7) getNavigation().moveTo(target, 0.9);
		else getNavigation().stop();

		if (ready("big_rip")) bigRip(level, target);
		else if (ready("stomp") && dist < 7) bubbleStomp(level);
		else if (ready("flick")) emberFlick(level, players);
		else if (phase >= 2 && ready("strain_attack")) strainAttack(level, players);
		else if (phase >= 1 && ready("minions")) minions(level, players);
	}

	private void tickSmoke(ServerLevel level, List<ServerPlayer> players) {
		// phase 1: hides in thick smoke for 6 s out of every 14; only the glowing ember can be hit
		if (phase == 0) {
			if (hideTicks > 0) {
				hideTicks--;
				boolean ember = (hideTicks / 20) % 3 == 0;
				entityData.set(EMBER, ember);
				if (ember) addEffect(new MobEffectInstance(MobEffects.GLOWING, 25, 0, false, false));
				if (hideTicks % 2 == 0) level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 3, getZ(), 6, 2, 2.5, 2, 0.01); // P13: every 2nd tick (cosy smoke lives 4-6 s, the cloud stays thick)
				if (hideTicks == 0) { entityData.set(HIDDEN, false); entityData.set(EMBER, false); }
			} else if (ready("hide") && busy == 0) {
				hideTicks = 120;
				entityData.set(HIDDEN, true);
				cooldown("hide", 280);
				announce("Hides in the smoke", ChatFormatting.GRAY);
			}
		}
		// phase 2+: thick smoke inside the dome
		if (phase >= 1 && tickCount % 100 == 0) {
			int closed = closedVents();
			if (closed > 0) for (ServerPlayer p : players) {
				Haze.add(p, 5.0 * closed / Math.max(1, vents.size()), false);
				if (Haze.get(p) >= 85) couchLock(level, p);
			}
		}
		if (phase >= 1 && tickCount % 4 == 0 && arena != null) {
			int closed = closedVents();
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, arena.center.getX(), arena.center.getY() + 4, arena.center.getZ(),
				2 + closed * 2, 8, 3, 8, 0.005);
		}
	}

	private void couchLock(ServerLevel level, ServerPlayer p) {
		long now = level.getGameTime();
		Integer last = couchLock.get(p.getUUID());
		if (last != null && now - last < 400) return;
		couchLock.put(p.getUUID(), (int) now);
		p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 6, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 80, 250, false, false)); // no jumping out of the couch
		p.sendOverlayMessage(Component.literal("Couch-locked: you sink into the moss.").withStyle(ChatFormatting.DARK_GREEN));
	}

	/** Returns true while walking to / closing a vent (that's its whole turn). */
	private boolean tickVents(ServerLevel level, List<ServerPlayer> players) {
		if (ready("slam") && phase >= 1) {
			for (Vent v : vents) if (v.open && v != closing) {
				setVent(level, v, false);
				ventsClosed++;
				Telegraph.circle(level, Vec3.atBottomCenterOf(v.lever), 1.5, Telegraph.RED, 40);
				announce("Vent slams shut", ChatFormatting.RED);
				break;
			}
			cooldown("slam", 160);
		}
		if (closing == null) return false;
		Vec3 lever = Vec3.atBottomCenterOf(closing.lever);
		if (distanceToSqr(lever) > 9) {
			getNavigation().moveTo(lever.x, lever.y, lever.z, 1.2);
			if (++closingTicks > 300) { closing = null; return false; } // gave up
			return true;
		}
		getNavigation().stop();
		entityData.set(ANIM, ANIM_CLOSE);
		if (++closingTicks % 10 == 0) level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, lever.x, lever.y + 6, lever.z, 6, 1, 1, 1, 0.02);
		if (closingTicks > 40 + 30) {
			setVent(level, closing, false);
			ventsReclosed++;
			closing = null;
			entityData.set(ANIM, ANIM_IDLE);
		}
		return true;
	}

	public boolean isClosingVent() {
		return closing != null && anim() == ANIM_CLOSE;
	}

	private void bigRip(ServerLevel level, ServerPlayer target) {
		cooldown("big_rip", 200);
		attackDone("big_rip", 44);
		busy = 50;
		getNavigation().stop();
		float yaw = BossUtil.yawTo(position(), target.position());
		setYRot(yaw);
		yBodyRot = yaw;
		entityData.set(ANIM, ANIM_RIP);
		announce("Big Rip", ChatFormatting.DARK_GREEN);
		playBoss(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 3F, 0.5F);
		Telegraph.cone(level, position(), yaw, 30, 10, Telegraph.GREEN, 30);
		for (int i = 0; i < 6; i++) schedule(i * 5, () -> level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
			getX() + random.nextGaussian() * 3, getY() + 2, getZ() + random.nextGaussian() * 3, 6, 0.3, 0.3, 0.3, 0.05));
		schedule(30, () -> {
			bigRips++;
			rips++;
			entityData.set(ANIM, ANIM_EXHALE);
			playBoss(SoundEvents.FIRE_EXTINGUISH, 3F, 0.4F);
			Vec3 origin = position();
			for (int i = 1; i <= 10; i++) {
				double a = Math.toRadians(yaw);
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, origin.x - Math.sin(a) * i, origin.y + 1.5, origin.z + Math.cos(a) * i, 8, i * 0.15, 0.4, i * 0.15, 0.02);
			}
			for (ServerPlayer p : fighters()) {
				if (!BossUtil.inCone(origin, yaw, 30, 10, p.position())) continue;
				double d = p.position().distanceTo(origin);
				float raw = (float) (BIG_RIP_RAW * (d <= 3 ? 1 : Math.max(0.5, 1 - (d - 3) / 14)));
				hitScripted(level, p, raw);
				Haze.add(p, 15, false);
				if (!GreenhazeItems.immuneToContactHigh(p)) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
			}
			schedule(15, () -> entityData.set(ANIM, ANIM_IDLE));
			if (phase >= 2 && rips % 2 == 0) schedule(18, this::cough);
		});
	}

	private void cough() {
		coughs++;
		coughTicks = 60;
		entityData.set(ANIM, ANIM_COUGH);
		announce("Coughing fit! Core exposed", ChatFormatting.AQUA);
		playBoss(SoundEvents.PANDA_SNEEZE, 3F, 0.4F);
		if (level() instanceof ServerLevel sl) Telegraph.circle(sl, position(), 2.5, Telegraph.CYAN, 60);
	}

	private void bubbleStomp(ServerLevel level) {
		cooldown("stomp", 160);
		attackDone("stomp", 30);
		busy = 35;
		entityData.set(ANIM, ANIM_STOMP);
		announce("Bubble Stomp: jump!", ChatFormatting.YELLOW);
		Telegraph.ring(level, position(), 6, Telegraph.YELLOW, 25);
		Telegraph.ring(level, position(), 9, Telegraph.YELLOW, 25);
		schedule(25, () -> {
			stomps++;
			playBoss(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.6F);
			level.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 0.3, getZ(), 80, 5, 0.2, 5, 0.1);
			for (ServerPlayer p : fighters()) {
				if (p.distanceToSqr(this) <= 10 * 10 && p.onGround()) {
					hitScripted(level, p, STOMP_RAW);
					dev.elsewhere.greenhaze_hush.BossUtil.shove(p, 0.8, getX() - p.getX(), getZ() - p.getZ());
				}
			}
			entityData.set(ANIM, ANIM_IDLE);
		});
	}

	private void emberFlick(ServerLevel level, List<ServerPlayer> players) {
		cooldown("flick", phase >= 2 ? 140 : 100);
		attackDone("flick", 20);
		busy = 15;
		entityData.set(ANIM, ANIM_FLICK);
		announce("Ember Flick", ChatFormatting.GOLD);
		for (int i = 0; i < 3; i++) {
			ServerPlayer p = players.get(i % players.size());
			int delay = i * 5;
			schedule(delay, () -> {
				flicks++;
				GHProjectile e = new GHProjectile(GreenhazeEntities.EMBER_FLICK, level, this, new ItemStack(GreenhazeItems.EMBER), EMBER_RAW);
				e.setPos(getX(), getY() + 5.2, getZ());
				Vec3 d = p.getEyePosition().subtract(e.position());
				e.shoot(d.x, d.y + d.horizontalDistance() * 0.15, d.z, 1.1F, 4F);
				level.addFreshEntity(e);
				playBoss(SoundEvents.FIRECHARGE_USE, 1F, 1.4F);
			});
		}
		schedule(15, () -> entityData.set(ANIM, ANIM_IDLE));
	}

	private void tickStrain(ServerLevel level) {
		if (strainTicks-- <= 0) {
			strainTicks = 300;
			int s = (strain() + 1) % 3;
			if (strainShifts == 0) s = SATIVA;
			entityData.set(STRAIN, s);
			strainShifts++;
			bossBar.setColor(s == SATIVA ? BossEvent.BossBarColor.YELLOW : s == INDICA ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.GREEN);
			announce(s == SATIVA ? "Strain: Sativa" : s == INDICA ? "Strain: Indica" : "Strain: Hybrid",
				s == SATIVA ? ChatFormatting.GOLD : s == INDICA ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GREEN);
		}
	}

	private void strainAttack(ServerLevel level, List<ServerPlayer> players) {
		int s = strain();
		boolean hybrid = s == HYBRID;
		cooldown("strain_attack", hybrid ? 120 : 60);
		attackDone("strain_attack", 30);
		if (s == SATIVA || hybrid) {
			for (ServerPlayer p : players) {
				ringsFired++;
				GHProjectile ring = new GHProjectile(GreenhazeEntities.SMOKE_RING, level, this, new ItemStack(GreenhazeItems.SMOKE_RING), RING_RAW);
				ring.setPos(getX(), getY() + 4, getZ());
				Vec3 d = p.getEyePosition().subtract(ring.position());
				ring.shoot(d.x, d.y, d.z, 0.9F, 2F);
				level.addFreshEntity(ring);
			}
			playBoss(SoundEvents.BREEZE_SHOOT, 1.5F, 0.7F);
		}
		if (s == INDICA || hybrid) {
			for (ServerPlayer p : players) {
				poolsDropped++;
				Vec3 at = p.position();
				Telegraph.circle(level, at, 2.5, Telegraph.VIOLET, 100);
				indicaPools.add(at);
				poolTicks.add(100);
			}
		}
	}

	private void tickPools(ServerLevel level, List<ServerPlayer> players) {
		for (int i = indicaPools.size() - 1; i >= 0; i--) {
			int t = poolTicks.get(i) - 1;
			Vec3 at = indicaPools.get(i);
			if (t % 5 == 0) level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.3, at.z, 3, 1.5, 0.1, 1.5, 0);
			for (ServerPlayer p : players) if (p.position().distanceToSqr(at) < 2.5 * 2.5) {
				p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, t < 40 ? 6 : 2));
			}
			if (t <= 0) { indicaPools.remove(i); poolTicks.remove(i); }
			else poolTicks.set(i, t);
		}
	}

	private void minions(ServerLevel level, List<ServerPlayer> players) {
		cooldown("minions", 400);
		int cap = 2 * Math.max(1, players.size());
		int have = level.getEntitiesOfClass(GreenhazeMobs.BudBomb.class, getBoundingBox().inflate(32)).size();
		for (int i = have; i < Math.min(cap, have + 2); i++) {
			GreenhazeMobs.BudBomb b = GreenhazeEntities.BUD_BOMB.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (b == null) continue;
			b.snapTo(getX() + random.nextGaussian() * 3, getY(), getZ() + random.nextGaussian() * 3, 0F, 0F);
			level.addFreshEntity(b);
		}
		for (ServerPlayer p : players) if (Haze.get(p) >= 60 && random.nextBoolean()) {
			GreenhazeMobs.ParanoiaShade s = GreenhazeEntities.PARANOIA_SHADE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (s == null) continue;
			s.snapTo(p.getX() + random.nextGaussian() * 4, p.getY() + 2, p.getZ() + random.nextGaussian() * 4, 0F, 0F);
			s.setTarget(p);
			level.addFreshEntity(s);
		}
	}

	// ---------------------------------------------------------------------------------------------- damage rules

	@Override
	protected boolean acceptsDamage(ServerLevel level, DamageSource source) {
		return !hidden() || emberGlow();
	}

	@Override
	protected float damageMultiplier(ServerLevel level, DamageSource source, float amount) {
		float m = 1F;
		if (coughTicks > 0) m *= 2F;
		Entity attacker = source.getEntity();
		if (isClosingVent() && attacker != null && BossUtil.fromBehind(this, attacker)) m *= 2F;
		return m;
	}

	@Override
	protected void onReset(ServerLevel level) {
		for (Vent v : vents) if (!v.open) setVent(level, v, true);
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel sl) {
			for (Vent v : vents) if (!v.open) setVent(sl, v, true);
			sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 3, getZ(), 200, 1.5, 2, 1.5, 0.3);
			sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 5, getZ(), 60, 2, 2, 2, 0.05);
			sl.playSound(null, blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 3F, 0.5F);
		}
		super.die(source);
	}

	@Override
	public List<ItemStack> rollLoot(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		RandomSource r = player.getRandom();
		// bal-loot-standard: signature 100% first kill then 67%; every other unique 50% then 33% with bad-luck protection
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("phasewalker_greaves", () -> new ItemStack(SignatureGear.PHASEWALKER_GREAVES)),
			dev.elsewhere.boss.kit.DropRule.other("everflow_bong", () -> new ItemStack(GreenhazeItems.EVERFLOW_BONG)),
			dev.elsewhere.boss.kit.DropRule.other("hotbox_visor", () -> new ItemStack(GreenhazeItems.HOTBOX_VISOR)))));
		out.add(new ItemStack(GreenhazeItems.CLOUD_JAR, 2 + r.nextInt(3)));
		out.add(new ItemStack(GreenhazeItems.HAZE_RESIN, 8 + r.nextInt(9)));
		return out;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getAmbientSound() {
		return SoundEvents.BUBBLE_COLUMN_BUBBLE_POP;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource s) {
		return SoundEvents.GLASS_HIT;
	}

	@Override
	protected net.minecraft.sounds.SoundEvent getDeathSound() {
		return SoundEvents.GLASS_BREAK;
	}

	/** Drop odds (TBD by BALANCE.md). */
	public static final class LootOdds {
		public static final float UNIQUE = 0.5F;
	}

	public static void forgetPlayer(java.util.UUID id) { CHANNEL.remove(id); }

	public static void clearAll() { CHANNEL.clear(); }
}
