package dev.elsewhere.boss.galvanox;

import dev.elsewhere.stub.verdigris_ironpeak.StubBoss;
import dev.elsewhere.stub.verdigris_ironpeak.Telegraph;
import dev.elsewhere.verdigris_ironpeak.BossShot;
import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakSounds;
import dev.elsewhere.verdigris_sea.VerdigrisEntities;
import dev.elsewhere.verdigris_sea.VerdigrisItems;
import dev.elsewhere.verdigris_sea.mob.TidewingRay;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Galvanox, the Lighthouse Engine (BOSSES.md #13, Tier IV). HP 580 base (887 with Test Meter), armor 18, toughness 6.
 * Phase 1 Patina: oxidized plates block 75% of damage until scraped off with axes; every 6 s a rod glows, lightning hits
 * it and that floor quadrant is electrified for 3 s (30 raw + 5 lightning). Overhead hammer: 45 raw after a 1.2 s raised
 * fist. Phase 2 Overcharge: chain lightning jumps between players closer than 4 blocks (12 raw per jump); Tidewing Rays
 * dive in. Phase 3 Storm Engine: a plate regrows every 15 s, and wax shells it lobs onto itself make plates unscrapable
 * for 10 s unless shot down.
 */
public class GalvanoxBoss extends StubBoss {
	public static final EntityDataAccessor<Integer> DATA_PLATES = SynchedEntityData.defineId(GalvanoxBoss.class, EntityDataSerializers.INT);
	public static final EntityDataAccessor<Boolean> DATA_WAXED = SynchedEntityData.defineId(GalvanoxBoss.class, EntityDataSerializers.BOOLEAN);
	public static final int A_HAMMER = 1, A_ARC = 2, A_CHAIN = 3, A_WAX = 4, A_BOLT = 5;
	public static final int MAX_PLATES = 8, ARC_EVERY = 120, REGROW_EVERY = 300, WAX_TICKS = 200;
	public static final float HAMMER_RAW = 45, ARC_RAW = 30, ARC_LIGHTNING_RAW = 5, CHAIN_RAW = 12, BOLT_RAW = 12, PLATE_BLOCK = 0.75F;

	private int arcTimer = 80, arcStage = -1, arcQuadrant, arcRod, electrified;
	private final Set<UUID> arcHit = new HashSet<>();
	private int chainTimer = 120, waxTimer = 200, regrowTimer = REGROW_EVERY, waxedTicks, rayTimer, hammerCooldown = 40, boltCooldown = 60;
	private final java.util.Map<UUID, Integer> scrapeCooldown = new java.util.HashMap<>();

	public GalvanoxBoss(EntityType<? extends Monster> type, Level level) {
		super(type, level, BossEvent.BossBarColor.BLUE);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 580).add(Attributes.ARMOR, 18).add(Attributes.ARMOR_TOUGHNESS, 6)
			.add(Attributes.ATTACK_DAMAGE, 15).add(Attributes.MOVEMENT_SPEED, 0.18).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 48).add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PLATES, MAX_PLATES);
		builder.define(DATA_WAXED, false);
	}

	@Override
	public String bossId() {
		return "galvanox";
	}

	@Override
	public int tier() {
		return 4;
	}

	@Override
	protected float[] phaseThresholds() {
		return new float[] {0.60F, 0.30F};
	}

	@Override
	protected @Nullable SoundEvent phaseSound() {
		return VerdigrisIronpeakSounds.GALVANOX_ROAR.value();
	}

	@Override
	protected int sealColor() {
		return 0x40FFE0;
	}

	@Override
	protected double meleeRange() {
		return 4.0;
	}

	public int plates() {
		return entityData.get(DATA_PLATES);
	}

	public void setPlates(int n) {
		entityData.set(DATA_PLATES, Math.max(0, Math.min(MAX_PLATES, n)));
	}

	public boolean isWaxed() {
		return entityData.get(DATA_WAXED);
	}

	public float incomingFactor() {
		return plates() > 0 ? 1 - PLATE_BLOCK : 1;
	}

	@Override
	protected float modifyIncoming(DamageSource source, float dmg) {
		return plates() > 0 ? dmg * (1 - PLATE_BLOCK) : dmg;
	}

	/** Scraping with an axe (vanilla copper interaction) pops off one plate. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(ItemTags.AXES)) return super.interact(player, hand, location);
		if (player instanceof ServerPlayer sp && level() instanceof ServerLevel level) scrape(level, sp, held, hand);
		return InteractionResult.SUCCESS;
	}

	public boolean scrape(ServerLevel level, ServerPlayer player, ItemStack axe, InteractionHand hand) {
		if (plates() <= 0 || !isInside(player)) return false;
		if (scrapeCooldown.getOrDefault(player.getUUID(), 0) > tickCount) return false;
		scrapeCooldown.put(player.getUUID(), tickCount + 10);
		if (isWaxed()) {
			level.playSound(null, blockPosition(), SoundEvents.WAXED_SIGN_INTERACT_FAIL, SoundSource.HOSTILE, 1.0F, 1.0F);
			player.sendOverlayMessage(Component.translatable("message.elsewhere.galvanox.waxed"));
			return false;
		}
		setPlates(plates() - 1);
		level.playSound(null, blockPosition(), SoundEvents.AXE_SCRAPE.value(), SoundSource.HOSTILE, 2.0F, 0.8F);
		level.sendParticles(ParticleTypes.SCRAPE, getX(), getY() + 3, getZ(), 30, 1.2, 2.0, 1.2, 0.5);
		axe.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
		participants.add(player.getUUID());
		if (random.nextFloat() < 0.5F) give(level, player, List.of(new ItemStack(VerdigrisItems.SCRAPED_PATINA)));
		return true;
	}

	@Override
	protected void chooseAttack(ServerLevel level, LivingEntity target) {
		double d = distanceToSqr(target);
		if (hammerCooldown <= 0 && d < 5 * 5) {
			startAttack(A_HAMMER, Component.translatable("attack.elsewhere.galvanox.hammer"), 30);
			hammerCooldown = 100;
		} else if (boltCooldown <= 0 && d > 8 * 8) {
			startAttack(A_BOLT, null, 20);
			boltCooldown = 80;
		}
	}

	@Override
	protected boolean tickAttack(ServerLevel level, int attack, int tick, @Nullable LivingEntity target) {
		switch (attack) {
			case A_HAMMER -> {
				Vec3 front = position().add(Vec3.directionFromRotation(0, getYRot()).scale(2.5));
				if (tick % 4 == 0) Telegraph.filledCircle(level, front, 3.5, Telegraph.RED);
				if (tick % 6 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 7, getZ(), 6, 0.8, 0.3, 0.8, 0.2);
				if (tick == 24) {
					level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0F, 0.5F);
					level.sendParticles(ParticleTypes.EXPLOSION, front.x, front.y + 0.3, front.z, 2, 0.5, 0, 0.5, 0);
					for (ServerPlayer p : fighters()) if (p.position().distanceToSqr(front) < 3.5 * 3.5) {
						hitPlayer(level, p, HAMMER_RAW);
						Fx.push(p, front, 0.8, 0.5);
					}
				}
				return tick < 34;
			}
			case A_BOLT -> {
				if (tick == 10 && target != null) {
					BossShot b = BossShot.create(GalvanoxEntities.ARC_BOLT, level, this, getX(), getY() + 6.5, getZ());
					b.damage = BOLT_RAW;
					Vec3 d = target.position().add(0, 1, 0).subtract(b.position());
					b.shoot(d.x, d.y, d.z, 1.2F, 1.0F);
					level.addFreshEntity(b);
				}
				return tick < 16;
			}
			case A_WAX -> {
				if (tick == 12) {
					BossShot w = BossShot.create(GalvanoxEntities.WAX_SHELL, level, this, getX(), getY() + 7.5, getZ());
					w.setDeltaMovement(random.nextDouble() * 0.1 - 0.05, 0.85, random.nextDouble() * 0.1 - 0.05);
					w.onLand = (shot, hit) -> {
						if (shot.distanceToSqr(this) < 4 * 4 || shot.position().distanceToSqr(position().add(0, 5, 0)) < 5 * 5) {
							entityData.set(DATA_WAXED, true);
							waxedTicks = WAX_TICKS;
							level.sendParticles(ParticleTypes.WAX_ON, getX(), getY() + 3, getZ(), 40, 1.2, 2, 1.2, 0.3);
						}
					};
					level.addFreshEntity(w);
					Telegraph.announce(barPlayers(), Component.translatable("attack.elsewhere.galvanox.wax"));
				}
				return tick < 20;
			}
			default -> {
				return false;
			}
		}
	}

	@Override
	protected void tickAlways(ServerLevel level) {
		if (hammerCooldown > 0) hammerCooldown--;
		if (boltCooldown > 0) boltCooldown--;
		if (waxedTicks > 0 && --waxedTicks == 0) entityData.set(DATA_WAXED, false);
		if (tickCount % 20 == 0) level.playSound(null, blockPosition(), VerdigrisIronpeakSounds.GALVANOX_HUM.value(), SoundSource.HOSTILE, 0.6F, 0.6F + plates() * 0.05F);
		tickArc(level);
		if (phase >= 1) {
			if (--chainTimer <= 0) {
				chainTimer = 160;
				chainLightning(level);
			}
			if (--rayTimer <= 0) {
				rayTimer = 600;
				int cap = 3 + 2 * Math.max(0, participants.size() - 1);
				for (int i = 0; i < 2 && liveMinions(level) < cap; i++) {
					TidewingRay ray = VerdigrisEntities.TIDEWING_RAY.create(level, EntitySpawnReason.MOB_SUMMONED);
					if (ray == null) continue;
					double a = random.nextDouble() * Math.PI * 2;
					ray.snapTo(arenaCenter.getX() + Math.cos(a) * 20, arenaCenter.getY() + 4, arenaCenter.getZ() + Math.sin(a) * 20, 0, 0);
					ray.setPersistenceRequired();
					level.addFreshEntity(ray);
					ServerPlayer t = randomFighter();
					if (t != null) ray.setTarget(t);
					trackMinion(ray);
				}
			}
		}
		if (phase >= 2) {
			if (--regrowTimer <= 0) {
				regrowTimer = REGROW_EVERY;
				if (plates() < MAX_PLATES) {
					setPlates(plates() + 1);
					level.sendParticles(ParticleTypes.WAX_ON, getX(), getY() + 3, getZ(), 20, 1, 2, 1, 0.1);
					level.playSound(null, blockPosition(), SoundEvents.COPPER_PLACE, SoundSource.HOSTILE, 2.0F, 0.6F);
				}
			}
			if (--waxTimer <= 0 && attack == 0) {
				waxTimer = 240;
				startAttack(A_WAX, null, 20);
			}
		}
	}

	// ---- lightning quadrants ------------------------------------------------------------------------------
	private void tickArc(ServerLevel level) {
		if (arcStage < 0) {
			if (--arcTimer > 0) return;
			arcTimer = ARC_EVERY;
			// Prefer the quadrant with the most players.
			int[] count = new int[4];
			for (ServerPlayer p : fighters()) count[GalvanoxArena.quadrant(arenaCenter, p.getX(), p.getZ())]++;
			int best = random.nextInt(4);
			for (int q = 0; q < 4; q++) if (count[q] > count[best]) best = q;
			arcQuadrant = best;
			List<Integer> rods = new ArrayList<>();
			for (int i = 0; i < GalvanoxArena.RODS; i++) {
				BlockPos r = GalvanoxArena.rod(arenaCenter, i);
				if (GalvanoxArena.quadrant(arenaCenter, r.getX() + 0.5, r.getZ() + 0.5) == best) rods.add(i);
			}
			arcRod = rods.isEmpty() ? 0 : rods.get(random.nextInt(rods.size()));
			arcStage = 0;
			Telegraph.announce(barPlayers(), Component.translatable("attack.elsewhere.galvanox.arc"));
			return;
		}
		arcStage++;
		BlockPos rod = GalvanoxArena.rod(arenaCenter, arcRod);
		if (arcStage < 30) {
			// The rod glows; the quadrant is marked yellow.
			if (arcStage % 2 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, rod.getX() + 0.5, rod.getY() + 1, rod.getZ() + 0.5, 5, 0.2, 0.4, 0.2, 0.1); // P13: was 4 every tick
			if (arcStage % 6 == 0) markQuadrant(level, Telegraph.YELLOW);
		} else if (arcStage == 30) {
			Fx.lightning(level, Vec3.atBottomCenterOf(rod.above()), true, null);
			electrified = 60;
			arcHit.clear();
		} else if (electrified > 0) {
			electrified--;
			if (electrified % 4 == 0) {
				for (int i = 0; i < 12; i++) {
					Vec3 p = randomInQuadrant();
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 0.2, p.z, 1, 0, 0.1, 0, 0.05);
				}
			}
			for (ServerPlayer p : fighters()) {
				if (GalvanoxArena.quadrant(arenaCenter, p.getX(), p.getZ()) != arcQuadrant || p.getY() > arenaCenter.getY() + 2.5) continue;
				if (arcHit.add(p.getUUID())) {
					hitPlayer(level, p, ARC_RAW);
					p.hurtServer(level, damageSources().lightningBolt(), dev.elsewhere.stub.verdigris_ironpeak.BossGuards.scaled(level, ARC_LIGHTNING_RAW));
				} else if (electrified % 20 == 0) {
					p.hurtServer(level, damageSources().lightningBolt(), dev.elsewhere.stub.verdigris_ironpeak.BossGuards.scaled(level, ARC_LIGHTNING_RAW));
				}
			}
		} else {
			arcStage = -1;
		}
	}

	private Vec3 randomInQuadrant() {
		double sx = (arcQuadrant & 1) == 1 ? 1 : -1, sz = (arcQuadrant & 2) == 2 ? 1 : -1;
		return new Vec3(arenaCenter.getX() + 0.5 + sx * random.nextDouble() * GalvanoxArena.RADIUS, arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5 + sz * random.nextDouble() * GalvanoxArena.RADIUS);
	}

	private void markQuadrant(ServerLevel level, int color) {
		for (int i = 0; i < 40; i++) {
			Vec3 p = randomInQuadrant();
			if (p.distanceToSqr(Vec3.atBottomCenterOf(arenaCenter.above())) < GalvanoxArena.RADIUS * GalvanoxArena.RADIUS) {
				level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(color, 1.4F), p.x, p.y + 0.1, p.z, 1, 0, 0, 0, 0);
			}
		}
	}

	public int arcQuadrant() {
		return arcQuadrant;
	}

	public boolean electrified() {
		return electrified > 0;
	}

	/** Chain lightning: hits one player, then jumps to anyone within 4 blocks of the last, up to 4 jumps. */
	public int chainLightning(ServerLevel level) {
		ServerPlayer first = randomFighter();
		if (first == null) return 0;
		Telegraph.announce(barPlayers(), Component.translatable("attack.elsewhere.galvanox.chain"));
		Set<UUID> hit = new HashSet<>();
		LivingEntity last = this;
		ServerPlayer next = first;
		int jumps = 0;
		while (next != null && jumps < 5) {
			Fx.line(level, ParticleTypes.ELECTRIC_SPARK, last.position().add(0, last.getBbHeight() * 0.6, 0), next.position().add(0, 1, 0), 0.3);
			hitPlayer(level, next, CHAIN_RAW);
			hit.add(next.getUUID());
			jumps++;
			last = next;
			next = null;
			for (ServerPlayer p : fighters()) {
				if (!hit.contains(p.getUUID()) && p.distanceToSqr(last) < 4 * 4) {
					next = p;
					break;
				}
			}
		}
		level.playSound(null, first.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.5F, 1.4F);
		return jumps;
	}

	@Override
	protected List<ItemStack> personalLoot(ServerPlayer player, boolean firstKill, RandomSource random) {
		List<ItemStack> loot = new ArrayList<>(dev.elsewhere.verdigris_ironpeak.ArsenalLoot.roll(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("galvanic_trident", () -> new ItemStack(VerdigrisItems.GALVANIC_TRIDENT)),
			dev.elsewhere.boss.kit.DropRule.other("engineers_lantern", () -> new ItemStack(VerdigrisItems.ENGINEERS_LANTERN)))));
				loot.add(new ItemStack(VerdigrisItems.SCRAPED_PATINA, GearStats.GALVANOX_PATINA_MIN + random.nextInt(GearStats.GALVANOX_PATINA_MAX - GearStats.GALVANOX_PATINA_MIN + 1)));
		return loot;
	}

	@Override
	protected void onDefeated(ServerLevel level) {
		for (int i = 0; i < 4; i++) Fx.lightning(level, position().add(random.nextDouble() * 4 - 2, 0, random.nextDouble() * 4 - 2), true, null);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putInt("Plates", plates());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		setPlates(in.getIntOr("Plates", MAX_PLATES));
	}
}
