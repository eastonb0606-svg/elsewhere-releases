package dev.elsewhere.boss.ossyra;

import dev.elsewhere.boss.kit.BossRewards;
import dev.elsewhere.boss.kit.DropRule;
import dev.elsewhere.nether_end_bosses.GearStats;
import dev.elsewhere.nether_end_bosses.NetherEndSounds;
import dev.elsewhere.stub.nether_end_bosses.AttackSpec;
import dev.elsewhere.stub.nether_end_bosses.BossArena;
import dev.elsewhere.stub.nether_end_bosses.BossFight;
import dev.elsewhere.stub.nether_end_bosses.BossPart;
import dev.elsewhere.stub.nether_end_bosses.Decals;
import dev.elsewhere.stub.nether_end_bosses.ElsewhereBoss;
import dev.elsewhere.stub.nether_end_bosses.StubFramework;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Ossyra, Mother of Shells (Tier VI, 1100 base HP, armor 20 / toughness 10) in the hollow cube atop the Matriarch Spire.
 * Closed shell = 75% less damage; she opens for 4 s after a Shell Slam (and while firing). 1 Shell (100-65%): bullet
 * storms, Shell Slam. 2 Inversion (65-30%): gravity flips every 15 s (chime + subtitle 3 s ahead), Shulklings.
 * 3 Void Bloom (30-0%): floor/ceiling panels dissolve after 2 s of reverse-portal warning; she swaps surfaces.
 */
public class Ossyra extends ElsewhereBoss {
	public static final int FLAG_OPEN = FLAG_CUSTOM;
	public static final int FLAG_INVERTED = FLAG_CUSTOM << 1;
	public static final int FLAG_ON_CEILING = FLAG_CUSTOM << 2;
	public static final float CLOSED_DAMAGE = 0.25F;
	public static final double FLIPPED_GRAVITY = -0.16; // base 0.08 -> -0.08

	private int openTicks;
	private final List<BlockPos> bloom = new ArrayList<>();

	public Ossyra(EntityType<? extends Ossyra> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.xpReward = 500;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 950.0)
			.add(Attributes.ARMOR, 18.0)
			.add(Attributes.ARMOR_TOUGHNESS, 8.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override public String bossId() { return "ossyra"; }
	@Override public int tier() { return 6; }
	@Override public BossEvent.BossBarColor barColor() { return BossEvent.BossBarColor.PURPLE; }
	@Override public BossEvent.BossBarOverlay barOverlay() { return BossEvent.BossBarOverlay.NOTCHED_10; }
	@Override public Holder<SoundEvent> music() { return NetherEndSounds.MUSIC_OSSYRA; }
	@Override protected float[] phaseThresholds() { return new float[] {0.65F, 0.30F}; }
	@Override protected SoundEvent roarSound() { return SoundEvents.SHULKER_HURT_CLOSED; }

	@Override
	protected List<AttackSpec> defaultAttacks() {
		return List.of(
			new AttackSpec("bullet_storm", "Bullet Storm", 20, 80, AttackSpec.phases(1, 2, 3), 4, 4.0F),
			new AttackSpec("shell_slam", "Shell Slam", 30, 120, AttackSpec.phases(1, 2, 3), 4, 44.0F),
			new AttackSpec("gravity_flip", "Gravity Flip", 60, 300, AttackSpec.phases(2, 3), 8, 0.0F),
			new AttackSpec("shulklings", "Shulklings", 20, 400, AttackSpec.phases(2), 3, 0.0F),
			new AttackSpec("void_bloom", "Void Bloom", 40, 200, AttackSpec.phases(3), 4, 0.0F),
			new AttackSpec("surface_swap", "Surface Swap", 20, 260, AttackSpec.phases(3), 3, 0.0F));
	}

	@Override
	protected boolean canStart(AttackSpec spec) {
		if (spec.id().equals("shulklings")) return fight() != null && fight().minionCount((ServerLevel) this.level()) < fight().minionCap(4);
		return true;
	}

	public boolean isOpen() {
		return hasFlag(FLAG_OPEN);
	}

	@Override
	protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float damage, @Nullable BossPart part) {
		return isOpen() ? damage : damage * CLOSED_DAMAGE;
	}

	@Override
	protected void bossTick(ServerLevel level) {
		Vec3 h = homeCenter();
		double y = hasFlag(FLAG_ON_CEILING) ? h.y + OssyraArena.INNER - 6.0 : h.y;
		Vec3 goal = new Vec3(h.x, y, h.z);
		this.setDeltaMovement(goal.subtract(this.position()).scale(0.2));
		if (openTicks > 0 && --openTicks == 0) setFlag(FLAG_OPEN, false);
		if (this.tickCount % 4 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY() + 3, this.getZ(), 3, 2.5, 2.5, 2.5, 0.02);
	}

	@Override
	public void travel(Vec3 input) {
		this.move(MoverType.SELF, this.getDeltaMovement());
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected boolean tickAttack(ServerLevel level, AttackSpec spec, int t) {
		return switch (spec.id()) {
			case "bullet_storm" -> bulletStorm(level, spec, t);
			case "shell_slam" -> shellSlam(level, spec, t);
			case "gravity_flip" -> gravityFlip(level, spec, t);
			case "shulklings" -> shulklings(level, spec, t);
			case "void_bloom" -> voidBloom(level, spec, t);
			case "surface_swap" -> surfaceSwap(level, spec, t);
			default -> true;
		};
	}

	private boolean bulletStorm(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) {
			setFlag(FLAG_OPEN, true);
			this.playSound(SoundEvents.SHULKER_OPEN, 3.0F, 0.6F);
		}
		if (t < spec.windup()) return false;
		int k = t - spec.windup();
		if (k % 6 == 0) {
			ServerPlayer target = nextTarget();
			if (target != null) {
				net.minecraft.world.entity.projectile.ShulkerBullet b = new net.minecraft.world.entity.projectile.ShulkerBullet(level, this, target, Direction.Axis.Y);
				b.setPos(this.getX(), this.getY() + 3.5, this.getZ());
				level.addFreshEntity(b);
				this.playSound(SoundEvents.SHULKER_SHOOT, 2.0F, 0.8F);
			}
		}
		if (k >= 48) {
			openTicks = 20;
			return true;
		}
		return false;
	}

	/** She closes (1.5 s, armor up), sends a shockwave along her surface (44 raw: jump or leave her surface), then opens for 4 s. */
	private boolean shellSlam(ServerLevel level, AttackSpec spec, int t) {
		Vec3 base = this.position();
		if (t == 0) {
			setFlag(FLAG_OPEN, false);
			this.playSound(SoundEvents.SHULKER_CLOSE, 4.0F, 0.5F);
		}
		if (t < spec.windup()) {
			if (t % 5 == 0) Decals.circle(level, base, 9.0, Decals.RED);
			return false;
		}
		this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.6F);
		level.sendParticles(ParticleTypes.END_ROD, base.x, base.y + 0.5, base.z, 60, 6, 0.2, 6, 0.1);
		boolean ceiling = hasFlag(FLAG_ON_CEILING);
		for (ServerPlayer p : targets()) {
			boolean sameSurface = ceiling ? p.getY() > this.getY() : p.getY() < this.getY() + 2;
			boolean grounded = p.onGround() || Math.abs(p.getY() - (ceiling ? homeCenter().y + OssyraArena.INNER : homeCenter().y)) < 0.8;
			if (sameSurface && grounded && p.position().distanceTo(base) < 10) hit(level, p, spec.damage());
		}
		setFlag(FLAG_OPEN, true);
		openTicks = 80;
		return true;
	}

	private boolean gravityFlip(ServerLevel level, AttackSpec spec, int t) {
		if (t == 0) {
			this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 5.0F, 0.5F);
			for (ServerPlayer p : targets()) {
				p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 60, 10));
				p.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
				p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("boss.elsewhere.gravity_flip")));
			}
		}
		if (t < spec.windup()) {
			if (t % 4 == 0) level.sendParticles(ParticleTypes.END_ROD, homeCenter().x, homeCenter().y + 6, homeCenter().z, 20, 6, 6, 6, 0.05);
			return false;
		}
		boolean inverted = !hasFlag(FLAG_INVERTED);
		setFlag(FLAG_INVERTED, inverted);
		for (ServerPlayer p : targets()) applyGravity(p, inverted);
		this.playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 4.0F, 0.6F);
		return true;
	}

	public static void applyGravity(ServerPlayer p, boolean inverted) {
		AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
		if (g == null) return;
		g.removeModifier(StubFramework.FIGHT_GRAVITY);
		if (inverted) g.addTransientModifier(new AttributeModifier(StubFramework.FIGHT_GRAVITY, FLIPPED_GRAVITY, AttributeModifier.Operation.ADD_VALUE));
	}

	private boolean shulklings(ServerLevel level, AttackSpec spec, int t) {
		if (t < spec.windup()) return false;
		BossFight fight = fight();
		if (fight == null) return true;
		Vec3 c = homeCenter();
		int n = 2 + fight.activePlayers().size();
		for (int i = 0; i < n; i++) {
			Shulker s = EntityTypes.SHULKER.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (s == null) continue;
			boolean ceiling = i % 2 == 1;
			double a = this.random.nextDouble() * Math.PI * 2;
			s.snapTo(Math.floor(c.x + Math.cos(a) * 5) + 0.5, ceiling ? c.y + OssyraArena.INNER - 1 : c.y, Math.floor(c.z + Math.sin(a) * 5) + 0.5, 0, 0);
			s.getAttribute(Attributes.SCALE).setBaseValue(0.5);
			s.setCustomName(Component.translatable("entity.elsewhere.shulkling"));
			s.setPersistenceRequired();
			fight.minion(s);
			level.addFreshEntity(s);
		}
		return true;
	}

	/** Floor and ceiling panels dissolve into the void in a telegraphed pattern (2 s of reverse-portal particles). */
	private boolean voidBloom(ServerLevel level, AttackSpec spec, int t) {
		BlockPos o = home();
		if (o == null) return true;
		if (t == 0) {
			bloom.clear();
			int half = OssyraArena.INNER / 2;
			for (int i = 0; i < 6; i++) {
				int px = this.random.nextInt(half * 2 - 3) - half + 1, pz = this.random.nextInt(half * 2 - 3) - half + 1;
				if (Math.abs(px) < 3 && Math.abs(pz) < 3) continue; // keep her pad and the pedestal
				boolean ceiling = this.random.nextBoolean();
				int y = ceiling ? o.getY() + OssyraArena.INNER : o.getY() - 1;
				for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) bloom.add(new BlockPos(o.getX() + px + dx, y, o.getZ() + pz + dz));
			}
		}
		if (t < spec.windup()) {
			if (t % 3 == 0) for (BlockPos p : bloom) level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.4, 0.6, 0.4, 0.02);
			return false;
		}
		for (BlockPos p : bloom) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
		this.playSound(SoundEvents.END_PORTAL_SPAWN, 2.0F, 1.5F);
		return true;
	}

	private boolean surfaceSwap(ServerLevel level, AttackSpec spec, int t) {
		if (t < spec.windup()) {
			level.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 3, this.getZ(), 10, 2, 2, 2, 0.5);
			return false;
		}
		setFlag(FLAG_ON_CEILING, !hasFlag(FLAG_ON_CEILING));
		Vec3 h = homeCenter();
		this.teleportTo(h.x, hasFlag(FLAG_ON_CEILING) ? h.y + OssyraArena.INNER - 6.0 : h.y, h.z);
		this.playSound(SoundEvents.SHULKER_TELEPORT, 3.0F, 0.6F);
		return true;
	}

	@Override
	public BossArena createArena(BlockPos origin) {
		return OssyraArena.arena(origin);
	}

	@Override
	public void rebuildArena(ServerLevel level, BlockPos origin) {
		OssyraArena.INSTANCE.build(level, origin, level.getRandom());
	}

	@Override
	public ItemStack defaultSummonItem() {
		return new ItemStack(net.minecraft.world.item.Items.SHULKER_SHELL);
	}

	@Override
	public List<ItemStack> rollDrops(ServerPlayer player, RandomSource random, boolean firstKill) {
		List<ItemStack> out = new ArrayList<>();
		out.addAll(BossRewards.rollUniques(player, bossId(), firstKill, List.of(
			DropRule.signature("inverted_crown", () -> new ItemStack(OssyraContent.INVERTED_CROWN)),
			DropRule.other("gravity_pearl", () -> new ItemStack(OssyraContent.GRAVITY_PEARL)))));
		out.add(new ItemStack(OssyraContent.MATRIARCHS_SHELL));
		return out;
	}
}
