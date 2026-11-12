package dev.elsewhere.boss.thurgrim;

import dev.elsewhere.ironpeak.Ironpeak;
import dev.elsewhere.ironpeak.IronpeakBlocks;
import dev.elsewhere.ironpeak.IronpeakEntities;
import dev.elsewhere.ironpeak.IronpeakItems;
import dev.elsewhere.ironpeak.mob.IronclanRaider;
import dev.elsewhere.stub.verdigris_ironpeak.StubBoss;
import dev.elsewhere.stub.verdigris_ironpeak.Telegraph;
import dev.elsewhere.verdigris_ironpeak.Fx;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakSounds;
import dev.elsewhere.verdigris_ironpeak.compat.TestMeterBridge;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Thurgrim Ironjaw, King of the Summit (BOSSES.md #14, Tier IV). HP 640 base (979 with Test Meter), armor 18, toughness 8.
 * Phase 1 Challenge: Overhead Split (1.2 s red line, 45 raw + launch along the line), Kingly Roar (1 s inhale; Weakness
 * and Test Meter -80 T within 8 blocks; T above 900 resists), axe combo (3 x 24 raw). Phase 2 Clan Call: Ironclan Raiders
 * climb in every 20 s until the war drum is broken; lightning hits the obelisks (stand close for Speed and Strength, but
 * the next strike is aimed at you). Phase 3 Last Stand: gusts every 8 s push toward the edges (sneak to brace); the
 * Avalanche Split boomerang (36 raw) along a marked arc.
 */
public class ThurgrimBoss extends StubBoss {
	public static final int A_SPLIT = 1, A_ROAR = 2, A_COMBO = 3, A_GUST = 4, A_BOOMERANG = 5;
	public static final float SPLIT_RAW = 45, COMBO_RAW = 24, BOOMERANG_RAW = 36, MARKED_STRIKE_RAW = 20;

	private int splitCd = 40, roarCd = 160, comboCd = 20, gustTimer = 160, boomCd = 100, waveTimer = 60, obeliskTimer = 120;
	private Vec3 splitDir = Vec3.ZERO;
	private Vec3 gustDir = Vec3.ZERO;
	private @Nullable UUID marked;
	private int markTicks;
	private Vec3 boomFrom = Vec3.ZERO, boomTo = Vec3.ZERO;
	private final Set<UUID> boomHit = new HashSet<>();

	public ThurgrimBoss(EntityType<? extends Monster> type, Level level) {
		super(type, level, BossEvent.BossBarColor.WHITE);
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(IronpeakItems.KINGSPLITTER));
		setDropChance(EquipmentSlot.MAINHAND, 0);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 640).add(Attributes.ARMOR, 18).add(Attributes.ARMOR_TOUGHNESS, 8)
			.add(Attributes.ATTACK_DAMAGE, 24).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
			.add(Attributes.FOLLOW_RANGE, 48).add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	public String bossId() {
		return "thurgrim";
	}

	@Override
	public int tier() {
		return 4;
	}

	@Override
	protected float[] phaseThresholds() {
		return new float[] {0.65F, 0.35F};
	}

	@Override
	protected @Nullable SoundEvent phaseSound() {
		return VerdigrisIronpeakSounds.THURGRIM_ROAR.value();
	}

	@Override
	protected int sealColor() {
		return 0xD0E8FF;
	}

	@Override
	protected void chooseAttack(ServerLevel level, LivingEntity target) {
		double d = distanceToSqr(target);
		if (phase >= 2 && boomCd <= 0 && d > 6 * 6) {
			boomFrom = position().add(0, 1.5, 0);
			boomTo = target.position().add(0, 1, 0);
			startAttack(A_BOOMERANG, Component.translatable("attack.elsewhere.thurgrim.avalanche_split"), 30);
			boomCd = 160;
		} else if (roarCd <= 0 && d < 8 * 8) {
			startAttack(A_ROAR, Component.translatable("attack.elsewhere.thurgrim.roar"), 30);
			roarCd = 400;
		} else if (splitCd <= 0 && d < 12 * 12) {
			splitDir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
			startAttack(A_SPLIT, Component.translatable("attack.elsewhere.thurgrim.split"), 30);
			splitCd = 140;
		} else if (comboCd <= 0 && d < 3.5 * 3.5) {
			startAttack(A_COMBO, null, 20);
			comboCd = 80;
		}
	}

	@Override
	protected boolean tickAttack(ServerLevel level, int attack, int tick, @Nullable LivingEntity target) {
		switch (attack) {
			case A_SPLIT -> {
				Vec3 a = position(), b = a.add(splitDir.scale(14));
				if (tick < 24 && tick % 3 == 0) Telegraph.line(level, a, b, 0.75, Telegraph.RED);
				if (tick == 24) {
					level.playSound(null, blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 2.5F, 0.6F);
					for (double t = 0; t <= 14; t += 1) level.sendParticles(ParticleTypes.SNOWFLAKE, a.x + splitDir.x * t, a.y + 0.3, a.z + splitDir.z * t, 4, 0.3, 0.2, 0.3, 0.05);
					for (ServerPlayer p : fighters()) {
						Vec3 rel = p.position().subtract(a);
						double along = rel.dot(splitDir);
						double side = rel.subtract(splitDir.scale(along)).horizontalDistance();
						if (along > -1 && along < 14.5 && side < 1.5) {
							hitPlayer(level, p, SPLIT_RAW);
							p.setDeltaMovement(p.getDeltaMovement().add(splitDir.x * 1.6, 0.5, splitDir.z * 1.6));
							p.needsSync = true;
						}
					}
				}
				return tick < 34;
			}
			case A_ROAR -> {
				if (tick < 20 && tick % 4 == 0) Telegraph.circle(level, position(), 8, Telegraph.YELLOW);
				if (tick == 20) {
					level.playSound(null, blockPosition(), VerdigrisIronpeakSounds.THURGRIM_ROAR.value(), SoundSource.HOSTILE, 3.0F, 0.7F);
					for (ServerPlayer p : fighters()) {
						if (p.distanceToSqr(this) > 8 * 8) continue;
						double t = TestMeterBridge.total(p);
						if (t > 900) {
							p.sendOverlayMessage(Component.translatable("message.elsewhere.thurgrim.resist"));
							continue;
						}
						p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
						TestMeterBridge.acute(p, -80);
					}
				}
				return tick < 30;
			}
			case A_COMBO -> {
				if (tick == 6 || tick == 14 || tick == 22) {
					level.playSound(null, blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.7F);
					Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
					for (ServerPlayer p : fighters()) {
						Vec3 rel = p.position().subtract(position()).multiply(1, 0, 1);
						if (rel.length() < 4 && rel.normalize().dot(fwd) > 0.3) hitPlayer(level, p, COMBO_RAW);
					}
				}
				return tick < 28;
			}
			case A_BOOMERANG -> {
				Vec3 mid = boomFrom.add(boomTo).scale(0.5);
				Vec3 perp = new Vec3(-(boomTo.z - boomFrom.z), 0, boomTo.x - boomFrom.x).normalize().scale(4);
				if (tick < 30) {
					if (tick % 3 == 0) for (double t = 0; t <= 1; t += 0.05) {
						Vec3 p = arc(boomFrom, boomTo, perp, t);
						level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(Telegraph.RED, 1.6F), p.x, boomFrom.y - 1.2, p.z, 1, 0, 0, 0, 0);
					}
					if (tick == 0) boomHit.clear();
				} else {
					double t = (tick - 30) / 20.0;
					double u = t <= 1 ? t : 2 - t;
					Vec3 p = arc(boomFrom, boomTo, perp.scale(t <= 1 ? 1 : -1), u);
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
					level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.1);
					for (ServerPlayer pl : fighters()) {
						if (pl.position().add(0, 1, 0).distanceToSqr(p) < 1.6 * 1.6 && boomHit.add(pl.getUUID())) hitPlayer(level, pl, BOOMERANG_RAW);
					}
				}
				return tick < 70;
			}
			case A_GUST -> {
				return tick < 2;
			}
			default -> {
				return false;
			}
		}
	}

	private static Vec3 arc(Vec3 a, Vec3 b, Vec3 perp, double t) {
		return a.add(b.subtract(a).scale(t)).add(perp.scale(Math.sin(Math.PI * t)));
	}

	@Override
	protected void tickAlways(ServerLevel level) {
		splitCd--;
		roarCd--;
		comboCd--;
		boomCd--;
		if (tickCount % 10 == 0) level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 3, getZ(), 6, 6, 2, 6, 0.02);
		if (phase >= 1) clanCall(level);
		if (phase >= 2) gusts(level);
	}

	private boolean drumIntact(ServerLevel level) {
		return level.getBlockState(arenaCenter.offset(ThurgrimArena.DRUM[0], ThurgrimArena.DRUM[1], ThurgrimArena.DRUM[2])).is(IronpeakBlocks.SUMMIT_WAR_DRUM);
	}

	private void clanCall(ServerLevel level) {
		if (--waveTimer <= 0) {
			waveTimer = 400;
			if (drumIntact(level)) {
				level.playSound(null, arenaCenter, VerdigrisIronpeakSounds.WAR_DRUM.value(), SoundSource.HOSTILE, 4.0F, 0.8F);
				int cap = 4 + 2 * Math.max(0, participants.size() - 1);
				for (int i = 0; i < 2 && liveMinions(level) < cap; i++) {
					IronclanRaider r = IronpeakEntities.IRONCLAN_RAIDER.create(level, EntitySpawnReason.MOB_SUMMONED);
					if (r == null) continue;
					double a = random.nextDouble() * Math.PI * 2;
					r.snapTo(arenaCenter.getX() + 0.5 + Math.cos(a) * (ThurgrimArena.RADIUS - 1), arenaCenter.getY() + 1, arenaCenter.getZ() + 0.5 + Math.sin(a) * (ThurgrimArena.RADIUS - 1), 0, 0);
					r.finalizeSpawn(level, level.getCurrentDifficultyAt(r.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
					r.summoned = true;
					r.setPersistenceRequired();
					ServerPlayer t = randomFighter();
					if (t != null) r.setTarget(t);
					level.addFreshEntity(r);
					trackMinion(r);
				}
			}
		}
		if (--obeliskTimer <= 0) {
			obeliskTimer = 200;
			ServerPlayer m = marked == null ? null : level.getServer().getPlayerList().getPlayer(marked);
			if (m != null && isInside(m)) {
				// The marked player gets the next strike.
				markTicks = 30;
			} else {
				marked = null;
				int i = random.nextInt(ThurgrimArena.OBELISKS);
				BlockPos o = ThurgrimArena.obelisk(arenaCenter, i);
				Fx.lightning(level, Vec3.atBottomCenterOf(o.above(7)), true, null);
				for (ServerPlayer p : fighters()) {
					if (p.position().distanceToSqr(Vec3.atBottomCenterOf(o)) < 3.5 * 3.5) {
						p.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, 1));
						p.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 200, 0));
						p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
						marked = p.getUUID();
					}
				}
			}
		}
		if (markTicks > 0) {
			ServerPlayer m = marked == null ? null : level.getServer().getPlayerList().getPlayer(marked);
			markTicks--;
			if (m == null) markTicks = 0;
			else if (markTicks > 0) {
				if (markTicks % 4 == 0) Telegraph.filledCircle(level, m.position(), 1.5, Telegraph.RED);
			} else {
				Fx.lightning(level, m.position(), true, null);
				if (isInside(m)) hitPlayer(level, m, MARKED_STRIKE_RAW);
				marked = null;
			}
		}
	}

	private void gusts(ServerLevel level) {
		gustTimer--;
		if (gustTimer == 20) {
			double a = random.nextDouble() * Math.PI * 2;
			gustDir = new Vec3(Math.cos(a), 0, Math.sin(a));
			String arrow = arrowFor(gustDir);
			Telegraph.announce(barPlayers(), Component.translatable("attack.elsewhere.thurgrim.gust", arrow));
		}
		if (gustTimer < 20 && gustTimer > 0 && gustTimer % 4 == 0) {
			for (ServerPlayer p : fighters()) level.sendParticles(ParticleTypes.SNOWFLAKE, p.getX() - gustDir.x * 3, p.getY() + 1, p.getZ() - gustDir.z * 3, 10, 0.5, 0.5, 0.5, 0.1);
		}
		if (gustTimer <= 0) {
			gustTimer = 160;
			level.playSound(null, arenaCenter, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 3.0F, 0.5F);
			for (ServerPlayer p : fighters()) {
				if (p.isShiftKeyDown()) continue; // braced
				p.setDeltaMovement(p.getDeltaMovement().add(gustDir.x * 1.3, 0.25, gustDir.z * 1.3));
				p.needsSync = true;
			}
		}
	}

	static String arrowFor(Vec3 d) {
		String[] arrows = {"→", "↘", "↓", "↙", "←", "↖", "↑", "↗"};
		int i = (int) Math.round(Math.atan2(d.z, d.x) / (Math.PI / 4));
		return arrows[(i % 8 + 8) % 8];
	}

	@Override
	protected List<ItemStack> personalLoot(ServerPlayer player, boolean firstKill, RandomSource random) {
		List<ItemStack> loot = new ArrayList<>(dev.elsewhere.verdigris_ironpeak.ArsenalLoot.roll(player, bossId(), firstKill, List.of(
			dev.elsewhere.boss.kit.DropRule.signature("summit_crown", () -> new ItemStack(IronpeakItems.SUMMIT_CROWN)),
			dev.elsewhere.boss.kit.DropRule.other("kingsplitter", () -> new ItemStack(IronpeakItems.KINGSPLITTER)))));
		loot.add(new ItemStack(IronpeakItems.IRONJAW_GLAND));
				loot.add(new ItemStack(IronpeakItems.SKYIRON, 4 + random.nextInt(5)));
		Ironpeak.setVictory(player, "thurgrim");
		return loot;
	}
}
