package dev.elsewhere.boss.grumblegut;

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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Grumblegut, the Endless Munchies (BOSSES.md 18). Tier I, 360 base HP, armor 10, toughness 2.
 * Phase 1 Snack Attack: Gulp Charge (1.2 s paw, crumb-trail decal, 38 raw), Munch (24 raw + Hunger III 10 s), Snack Sniff
 * (heals 5% from a ripe fruit tree; dropped food baits it: chews 3 s, 1% heal, belly exposed x1.5).
 * Phase 2 Giggle Fit: fake paranoia sounds (real attacks always have decals), rotating smoke columns (+10% high), Puff-Puff-Pass ring.
 * Phase 3 Food Coma: Couch Slam (30 raw shockwave, +6 armor, slower), Yawn (1.5 s vacuum then a 38 raw chomp), naps every 20 s
 * for 4 s (x2 damage) unless someone holds food within 6 blocks.
 */
public class Grumblegut extends BossEntity {
	public static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(Grumblegut.class, EntityDataSerializers.INT);
	public static final int ANIM_IDLE = 0, ANIM_PAW = 1, ANIM_CHARGE = 2, ANIM_MUNCH = 3, ANIM_SNIFF = 4, ANIM_CHEW = 5, ANIM_SLAM = 6,
		ANIM_YAWN = 7, ANIM_NAP = 8, ANIM_PUFF = 9;
	public static final double BASE_HP = 360, ARMOR = 10, TOUGHNESS = 2;
	public static final float CHARGE_RAW = 38, MUNCH_RAW = 24, SLAM_RAW = 30, CHOMP_RAW = 38, RING_RAW = 24;
	private static final Identifier COMA_ARMOR = Identifier.fromNamespaceAndPath("elsewhere", "grumblegut_food_coma");

	private int busy, napTicks, chewTicks, chargeTicks;
	private Vec3 chargeDir;
	private BlockPos sniffTarget;
	private ItemEntity bait;
	private final Map<UUID, Integer> columnCooldown = new HashMap<>();
	private float columnAngle;
	public UUID puffTarget;
	/** Counters for the self-test. */
	public int charges, munches, sniffs, treesEaten, baitsEaten, fakeSounds, puffs, slams, yawns, naps;

	public Grumblegut(EntityType<? extends Grumblegut> type, Level level) {
		super(type, level, BossEvent.BossBarColor.GREEN);
	}

	public static AttributeSupplier.Builder attributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, BASE_HP).add(Attributes.ARMOR, ARMOR)
			.add(Attributes.ARMOR_TOUGHNESS, TOUGHNESS).add(Attributes.MOVEMENT_SPEED, 0.25).add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.FOLLOW_RANGE, 40).add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(ANIM, 0);
	}

	public int anim() { return entityData.get(ANIM); }
	private void anim(int a) { entityData.set(ANIM, a); }

	@Override public String bossId() { return "grumblegut"; }
	@Override public double baseHealth() { return BASE_HP; }
	@Override protected float[] phaseThresholds() { return new float[]{0.60F, 0.30F}; }

	// ---------------------------------------------------------------------------------------------- summon

	/** Leave a full stack of food on the snack altar; it smells it and lumbers up. */
	public static boolean tryAltar(ServerLevel level, BlockPos altar, Player player, ItemStack stack) {
		if (!stack.has(DataComponents.FOOD) || stack.getCount() < stack.getMaxStackSize()) {
			player.sendOverlayMessage(Component.literal("The snack altar wants a full stack of food.").withStyle(ChatFormatting.GRAY));
			return false;
		}
		for (ArenaSeal a : ArenaSeal.ACTIVE) if (a.level == level && a.center.closerThan(altar, 8)) return false;
		ItemStack spent = stack.copy();
		stack.shrink(stack.getCount());
		level.playSound(null, altar, SoundEvents.PANDA_EAT, SoundSource.HOSTILE, 3F, 0.4F);
		summon(level, altar.north(5)).setSummonItem(spent);
		return true;
	}

	public static Grumblegut summon(ServerLevel level, BlockPos center) {
		ArenaSeal arena = ArenaSeal.create(level, center, 18, 4, 14);
		Grumblegut boss = GreenhazeEntities.GRUMBLEGUT.create(level, EntitySpawnReason.TRIGGERED);
		boss.snapTo(center.getX() + 0.5, center.getY() + 1, center.getZ() - 12.5, 0F, 0F);
		boss.setArena(arena);
		level.addFreshEntity(boss);
		arena.seal(StubBlocks.ARENA_SEAL);
		boss.playBoss(SoundEvents.HOGLIN_ANGRY, 3F, 0.4F);
		// Lazy Gorgers wander in to steal snacks
		for (int i = 0; i < 2; i++) {
			GreenhazeMobs.LazyGorger g = GreenhazeEntities.LAZY_GORGER.create(level, EntitySpawnReason.EVENT);
			if (g == null) continue;
			g.snapTo(center.getX() + 0.5 + (i == 0 ? 11 : -11), center.getY() + 1, center.getZ() + 6.5, 0F, 0F);
			level.addFreshEntity(g);
		}
		return boss;
	}

	public static Grumblegut buildAndSummon(ServerLevel level, BlockPos origin) {
		GreenhazeStructures.BONFIRE_TERRACE.build(Sink.live(level), origin, RandomSource.create(origin.asLong()));
		return summon(level, origin);
	}

	// ---------------------------------------------------------------------------------------------- fight

	@Override
	protected void onPhaseStart(ServerLevel level, int phase) {
		busy = 0;
		napTicks = 0;
		chargeTicks = 0;
		anim(ANIM_IDLE);
		if (phase == 1) { announce("Giggle Fit", ChatFormatting.GREEN); spawnBoars(level); }
		if (phase == 2) {
			announce("Food Coma", ChatFormatting.GOLD);
			var armor = getAttribute(Attributes.ARMOR);
			if (armor != null && !armor.hasModifier(COMA_ARMOR)) armor.addPermanentModifier(new AttributeModifier(COMA_ARMOR, 6, AttributeModifier.Operation.ADD_VALUE));
			var speed = getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) speed.setBaseValue(0.18);
		}
	}

	@Override
	protected void bossTick(ServerLevel level) {
		List<ServerPlayer> players = fighters();
		ServerPlayer target = players.isEmpty() ? null : BossUtil.sortedByDistance(players, this).get(0);
		if (phase >= 1) tickColumns(level, players);
		if (phase >= 1 && tickCount % 50 == 0) paranoia(level, players);
		if (chargeTicks > 0) { tickCharge(level, players); return; }
		if (chewTicks > 0) {
			chewTicks--;
			getNavigation().stop();
			if (chewTicks % 10 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2.5, getZ(), 5, 0.5, 0.3, 0.5, 0.1);
			if (chewTicks == 0) anim(ANIM_IDLE);
			return;
		}
		if (napTicks > 0) {
			napTicks--;
			getNavigation().stop();
			if (napTicks % 20 == 0) {
				level.sendParticles(ParticleTypes.NOTE, getX(), getY() + 3.8, getZ(), 1, 0.2, 0.1, 0.2, 0);
				playBoss(SoundEvents.PANDA_SNEEZE, 1.5F, 0.3F);
			}
			for (ServerPlayer p : players) if (p.distanceToSqr(this) < 36 && holdsFood(p)) {
				napTicks = 0;
				announce("Wakes up hungry!", ChatFormatting.RED);
				startCharge(level, p);
				return;
			}
			if (napTicks == 0) anim(ANIM_IDLE);
			return;
		}
		if (busy > 0) { busy--; return; }
		// food first: dropped bait, then fruit trees
		if (tryBait(level)) return;
		if (sniffTarget != null) { tickSniff(level); return; }
		if (target == null) return;
		getLookControl().setLookAt(target);
		double dist = distanceTo(target);
		if (phase == 2 && ready("nap")) { nap(level); return; }
		if (ready("sniff") && startSniff(level)) return;
		if (ready("charge") && dist > 5) { startCharge(level, target); return; }
		if (phase == 2 && ready("slam")) { couchSlam(level); return; }
		if (phase == 2 && ready("yawn") && dist < 12) { yawn(level, target); return; }
		if (phase >= 1 && ready("puff")) { puffPuffPass(level, players); return; }
		if (dist < 4.5 && ready("munch")) { munch(level, target); return; }
		getNavigation().moveTo(target, 1.0);
	}

	static boolean holdsFood(ServerPlayer p) {
		return p.getMainHandItem().has(DataComponents.FOOD) || p.getOffhandItem().has(DataComponents.FOOD);
	}

	private void startCharge(ServerLevel level, ServerPlayer target) {
		cooldown("charge", 140);
		attackDone("charge", 44);
		getNavigation().stop();
		Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
		chargeDir = dir;
		float yaw = BossUtil.yawTo(position(), target.position());
		setYRot(yaw);
		yBodyRot = yaw;
		anim(ANIM_PAW);
		announce("Gulp Charge", ChatFormatting.YELLOW);
		Telegraph.line(level, position(), position().add(dir.scale(20)), 1.5, Telegraph.YELLOW, 24);
		playBoss(SoundEvents.HOGLIN_ANGRY, 2F, 0.6F);
		for (int i = 0; i < 4; i++) schedule(i * 6, () -> level.sendParticles(ParticleTypes.CRIT,
			getX(), getY() + 0.1, getZ(), 10, 1, 0, 1, 0.1));
		chargeTicks = 24 + 30;
	}

	private void tickCharge(ServerLevel level, List<ServerPlayer> players) {
		chargeTicks--;
		if (chargeTicks > 30) { getNavigation().stop(); return; } // pawing (1.2 s)
		if (chargeTicks == 30) { charges++; anim(ANIM_CHARGE); playBoss(SoundEvents.RAVAGER_ROAR, 2F, 1.2F); }
		setDeltaMovement(chargeDir.x * 0.75, getDeltaMovement().y, chargeDir.z * 0.75);
		needsSync = true;
		for (ServerPlayer p : players) {
			if (p.getBoundingBox().intersects(getBoundingBox().inflate(0.6))) {
				hitScripted(level, p, CHARGE_RAW);
				dev.elsewhere.greenhaze_hush.BossUtil.shove(p, 1.5, -chargeDir.x, -chargeDir.z);
				chargeTicks = Math.min(chargeTicks, 1);
			}
		}
		if (horizontalCollision) chargeTicks = Math.min(chargeTicks, 1);
		if (chargeTicks <= 0) { anim(ANIM_IDLE); busy = 10; }
	}

	private void munch(ServerLevel level, ServerPlayer target) {
		cooldown("munch", 60);
		attackDone("munch", 14);
		busy = 12;
		anim(ANIM_MUNCH);
		Telegraph.circle(level, target.position(), 1.5, Telegraph.YELLOW, 12);
		schedule(10, () -> {
			if (target.distanceToSqr(this) < 6 * 6) {
				munches++;
				hitScripted(level, target, MUNCH_RAW);
				target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 2));
				playBoss(SoundEvents.PANDA_BITE, 2F, 0.5F);
			}
			anim(ANIM_IDLE);
		});
	}

	// ---------- food AI

	private boolean tryBait(ServerLevel level) {
		if (bait == null || !bait.isAlive()) {
			bait = null;
			List<ItemEntity> food = level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(16), e -> e.getItem().has(DataComponents.FOOD));
			if (food.isEmpty()) return false;
			bait = food.get(0);
			announce("Smells a snack...", ChatFormatting.GREEN);
		}
		getNavigation().moveTo(bait, 1.2);
		if (distanceToSqr(bait) < 9) {
			bait.getItem().shrink(1);
			if (bait.getItem().isEmpty()) bait.discard();
			bait = null;
			baitsEaten++;
			chewTicks = 60;
			anim(ANIM_CHEW);
			heal(getMaxHealth() * 0.01F);
			playBoss(SoundEvents.GENERIC_EAT.value(), 2F, 0.5F);
			announce("Chewing: belly exposed!", ChatFormatting.AQUA);
			Telegraph.circle(level, position(), 2, Telegraph.CYAN, 60);
		}
		return true;
	}

	private boolean startSniff(ServerLevel level) {
		cooldown("sniff", 400);
		if (arena == null) return false;
		BlockPos best = null;
		for (BlockPos p : BlockPos.betweenClosed(arena.center.offset(-18, -4, -18), arena.center.offset(18, 3, 18))) {
			BlockState s = level.getBlockState(p);
			if (s.is(GreenhazeBlocks.MUNCHFRUIT_BUSH) && s.getValue(SweetBerryBushBlock.AGE) >= 2) {
				if (best == null || p.distSqr(blockPosition()) < best.distSqr(blockPosition())) best = p.immutable();
			}
		}
		if (best == null) return false;
		sniffs++;
		sniffTarget = best;
		anim(ANIM_SNIFF);
		announce("Snack Sniff", ChatFormatting.GREEN);
		Telegraph.circle(level, Vec3.atBottomCenterOf(best), 2.5, Telegraph.CYAN, 60);
		for (int i = 0; i < 4; i++) schedule(i * 10, () -> playBoss(SoundEvents.SNIFFER_SNIFFING, 2F, 0.6F));
		busy = 40;
		return true;
	}

	private void tickSniff(ServerLevel level) {
		Vec3 t = Vec3.atBottomCenterOf(sniffTarget);
		getNavigation().moveTo(t.x, t.y, t.z, 1.1);
		if (distanceToSqr(t) < 9 || tickCount % 200 == 0) {
			BlockState s = level.getBlockState(sniffTarget);
			if (s.is(GreenhazeBlocks.MUNCHFRUIT_BUSH) && s.getValue(SweetBerryBushBlock.AGE) >= 2) {
				// it eats the whole tree's fruit
				for (BlockPos p : BlockPos.betweenClosed(sniffTarget.offset(-2, -1, -2), sniffTarget.offset(2, 1, 2))) {
					BlockState b = level.getBlockState(p);
					if (b.is(GreenhazeBlocks.MUNCHFRUIT_BUSH)) level.setBlock(p, b.setValue(SweetBerryBushBlock.AGE, 0), 3);
				}
				treesEaten++;
				heal(getMaxHealth() * 0.05F);
				chewTicks = 40;
				anim(ANIM_CHEW);
				playBoss(SoundEvents.GENERIC_EAT.value(), 2F, 0.4F);
			} else {
				anim(ANIM_IDLE);
			}
			sniffTarget = null;
		}
	}

	// ---------- phase 2

	private void paranoia(ServerLevel level, List<ServerPlayer> players) {
		Holder<SoundEvent>[] fakes = fakeSounds();
		for (ServerPlayer p : players) {
			if (random.nextInt(3) != 0) continue;
			fakeSounds++;
			Vec3 at = p.position().add(random.nextGaussian() * 3, 0, random.nextGaussian() * 3);
			BossUtil.privateSound(p, fakes[random.nextInt(fakes.length)], at, 1F, 0.9F + random.nextFloat() * 0.2F);
		}
	}

	@SuppressWarnings("unchecked")
	private static Holder<SoundEvent>[] fakeSounds() {
		return new Holder[]{Holder.direct(SoundEvents.CREEPER_PRIMED), Holder.direct(SoundEvents.ZOMBIE_STEP),
			Holder.direct(SoundEvents.CROSSBOW_LOADING_MIDDLE.value()), Holder.direct(SoundEvents.WITCH_CELEBRATE), Holder.direct(SoundEvents.SKELETON_STEP)};
	}

	public Vec3 columnPos(int i) {
		Vec3 c = arenaCenter();
		double a = columnAngle + i * 2 * Math.PI / 3;
		return new Vec3(c.x + Math.cos(a) * 10, c.y, c.z + Math.sin(a) * 10);
	}

	private void tickColumns(ServerLevel level, List<ServerPlayer> players) {
		columnAngle += 0.006F;
		for (int i = 0; i < 3; i++) {
			Vec3 col = columnPos(i);
			if ((tickCount + i) % 4 == 0) level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, col.x, col.y + 1 + random.nextDouble() * 8, col.z, 2, 0.6, 1.5, 0.6, 0.01); // P13: every 4th tick, columns staggered, was all 3 every 3rd
			for (ServerPlayer p : players) {
				if (BossUtil.ground(p.position(), 0).distanceToSqr(BossUtil.ground(col, 0)) < 2.25) {
					int cd = columnCooldown.getOrDefault(p.getUUID(), 0);
					if (tickCount >= cd) { Haze.add(p, 10, false); columnCooldown.put(p.getUUID(), tickCount + 40); }
				}
			}
		}
	}

	private void puffPuffPass(ServerLevel level, List<ServerPlayer> players) {
		cooldown("puff", 260);
		if (players.isEmpty()) return;
		ServerPlayer target = players.get(random.nextInt(players.size()));
		busy = 40;
		anim(ANIM_PUFF);
		puffs++;
		puffTarget = target.getUUID();
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0));
		announce("Puff-Puff-Pass: " + target.getName().getString(), ChatFormatting.YELLOW);
		target.sendOverlayMessage(Component.literal("You're marked! Sneak next to a friend to pass it, or dodge.").withStyle(ChatFormatting.YELLOW));
		Vec3 col = columnPos(0);
		Telegraph.line(level, position(), target.position(), 0.6, Telegraph.YELLOW, 30);
		schedule(30, () -> {
			if (!target.isAlive()) return;
			// passing: sneaking next to an ally splits the hit
			ServerPlayer ally = null;
			if (target.isShiftKeyDown()) for (ServerPlayer o : fighters()) if (o != target && o.distanceToSqr(target) <= 9) { ally = o; break; }
			GHProjectile ring = new GHProjectile(GreenhazeEntities.SMOKE_RING, level, this, new ItemStack(GreenhazeItems.SMOKE_RING), ally != null ? RING_RAW / 2 : RING_RAW);
			ring.setPos(getX(), getY() + 2.6, getZ());
			Vec3 d = target.getEyePosition().subtract(ring.position());
			ring.shoot(d.x, d.y, d.z, 1.0F, 0F);
			level.addFreshEntity(ring);
			if (ally != null) {
				GHProjectile half = new GHProjectile(GreenhazeEntities.SMOKE_RING, level, this, new ItemStack(GreenhazeItems.SMOKE_RING), RING_RAW / 2);
				half.setPos(getX(), getY() + 2.6, getZ());
				Vec3 d2 = ally.getEyePosition().subtract(half.position());
				half.shoot(d2.x, d2.y, d2.z, 1.0F, 0F);
				level.addFreshEntity(half);
			}
			playBoss(SoundEvents.BREEZE_SHOOT, 2F, 0.5F);
			anim(ANIM_IDLE);
		});
	}

	// ---------- phase 3

	private void couchSlam(ServerLevel level) {
		cooldown("slam", 200);
		attackDone("slam", 30);
		busy = 40;
		anim(ANIM_SLAM);
		announce("Couch Slam: jump!", ChatFormatting.YELLOW);
		Telegraph.ring(level, position(), 4, Telegraph.YELLOW, 25);
		Telegraph.ring(level, position(), 8, Telegraph.YELLOW, 25);
		setDeltaMovement(0, 0.8, 0);
		needsSync = true;
		schedule(25, () -> {
			slams++;
			playBoss(SoundEvents.MACE_SMASH_GROUND_HEAVY, 3F, 0.6F);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 50, 5, 0.1, 5, 0.03);
			for (ServerPlayer p : fighters()) if (p.distanceToSqr(this) <= 64 && p.onGround()) {
				hitScripted(level, p, SLAM_RAW);
				dev.elsewhere.greenhaze_hush.BossUtil.shove(p, 1.0, getX() - p.getX(), getZ() - p.getZ());
			}
			anim(ANIM_IDLE);
		});
	}

	private void yawn(ServerLevel level, ServerPlayer target) {
		cooldown("yawn", 240);
		attackDone("yawn", 40);
		busy = 50;
		anim(ANIM_YAWN);
		announce("Yawn", ChatFormatting.RED);
		float yaw = BossUtil.yawTo(position(), target.position());
		setYRot(yaw);
		yBodyRot = yaw;
		playBoss(SoundEvents.PANDA_PRE_SNEEZE, 3F, 0.3F);
		Telegraph.cone(level, position(), yaw, 40, 5, Telegraph.RED, 30);
		for (int i = 0; i < 30; i += 2) schedule(i, () -> {
			for (ServerPlayer p : fighters()) if (p.distanceToSqr(this) < 144) {
				Vec3 pull = position().subtract(p.position()).normalize().scale(0.12);
				p.push(pull.x, 0, pull.z);
				p.needsSync = true;
			}
		});
		schedule(30, () -> {
			yawns++;
			playBoss(SoundEvents.EVOKER_FANGS_ATTACK, 3F, 0.5F);
			for (ServerPlayer p : fighters()) if (p.distanceToSqr(this) < 25 && BossUtil.inCone(position(), yaw, 40, 5, p.position())) hitScripted(level, p, CHOMP_RAW);
			anim(ANIM_IDLE);
		});
	}

	private void nap(ServerLevel level) {
		cooldown("nap", 400);
		naps++;
		napTicks = 80;
		anim(ANIM_NAP);
		announce("Food coma nap: x2 damage!", ChatFormatting.AQUA);
		Telegraph.circle(level, position(), 2, Telegraph.CYAN, 80);
	}

	private void spawnBoars(ServerLevel level) {
		int cap = 3 * Math.max(1, fighters().size());
		int have = level.getEntitiesOfClass(GreenhazeMobs.MunchieBoar.class, getBoundingBox().inflate(30)).size();
		for (int i = have; i < Math.min(cap, have + 2); i++) {
			GreenhazeMobs.MunchieBoar b = GreenhazeEntities.MUNCHIE_BOAR.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (b == null) continue;
			b.snapTo(getX() + random.nextGaussian() * 4, getY() + 0.5, getZ() + random.nextGaussian() * 4, 0F, 0F);
			level.addFreshEntity(b);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (phase >= 1 && tickCount % 600 == 0 && !inTransition()) spawnBoars(level);
	}

	// ---------------------------------------------------------------------------------------------- damage rules

	@Override
	protected float damageMultiplier(ServerLevel level, DamageSource source, float amount) {
		if (napTicks > 0) return 2F;
		if (chewTicks > 0) return 1.5F;
		return 1F;
	}

	public boolean napping() {
		return napTicks > 0;
	}

	@Override
	public List<ItemStack> rollLoot(ServerPlayer player, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		RandomSource r = player.getRandom();
		out.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("challengers_cleaver", () -> new ItemStack(SignatureGear.CHALLENGERS_CLEAVER)),
			dev.elsewhere.boss.kit.DropRule.other("endless_snack_pouch", () -> new ItemStack(GreenhazeItems.ENDLESS_SNACK_POUCH)),
			dev.elsewhere.boss.kit.DropRule.other("couchlock_club", () -> new ItemStack(GreenhazeItems.COUCHLOCK_CLUB)))));
		out.add(new ItemStack(GreenhazeItems.GRUMBLEGUT_TUSK, 1 + r.nextInt(3)));
		out.add(new ItemStack(GreenhazeItems.MUNCHIE_BACON, 4 + r.nextInt(5)));
		return out;
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel sl) {
			anim(ANIM_NAP);
			sl.playSound(null, blockPosition(), SoundEvents.PANDA_SNEEZE, SoundSource.HOSTILE, 3F, 0.3F);
		}
		super.die(source);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HOGLIN_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource s) {
		return SoundEvents.HOGLIN_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HOGLIN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.5F;
	}
}
