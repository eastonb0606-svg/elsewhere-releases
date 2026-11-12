package dev.elsewhere.gear3.s3.whips;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Whip visuals and sound. One burst per event (a hit, a crack, a minion hit on a marked target), sent with the vanilla
 * particle packet, so the client draws them and only players within the vanilla particle range get them. Nothing loops.
 */
public final class WhipFx {
	private WhipFx() {}

	/** Lash points drawn from the hand to the tip on a crack. */
	static final int LASH_POINTS = 7;

	static DustParticleOptions dust(WhipKind k, float scale) {
		return new DustParticleOptions(k.color, scale);
	}

	/** Where the lash tip lands on a crack: full reach along the view, or the first block in the way. */
	public static Vec3 tip(ServerPlayer p) {
		double reach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(reach));
		BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	private static void sound(ServerLevel l, Vec3 at, WhipKind k, float volume, float pitch) {
		SoundEvent s = k.sound();
		if (s != null) l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
	}

	/** Right-click crack: the lash snaps out to the tip (a line of the whip's colour), a spark and the crack at the tip. */
	public static void crack(ServerPlayer p, WhipKind k) {
		ServerLevel l = p.level();
		Vec3 tip = tip(p);
		Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(0, -0.35, 0);
		DustParticleOptions d = dust(k, 0.7F);
		for (int i = 1; i <= LASH_POINTS; i++) {
			Vec3 at = hand.lerp(tip, i / (double) LASH_POINTS);
			l.sendParticles(d, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		l.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 5, 0.06, 0.06, 0.06, 0.2);
		l.sendParticles(k.mark, tip.x, tip.y, tip.z, 2, 0.05, 0.05, 0.05, 0.02);
		sound(l, tip, k, 0.9F, 0.95F + p.getRandom().nextFloat() * 0.1F);
	}

	/** A whip hit: the crack on the target and the mark's burst; a new mark also names itself on the owner's action bar. */
	public static void tagged(ServerPlayer owner, LivingEntity t, WhipKind k, boolean fresh) {
		ServerLevel l = owner.level();
		double x = t.getX(), y = t.getY() + t.getBbHeight() * 0.6, z = t.getZ(), w = t.getBbWidth() * 0.4, h = t.getBbHeight() * 0.3;
		l.sendParticles(dust(k, 1.0F), x, y, z, fresh ? 10 : 4, w, h, w, 0);
		l.sendParticles(k.mark, x, y, z, fresh ? 6 : 2, w, h, w, 0.02);
		sound(l, t.position().add(0, t.getBbHeight() * 0.5, 0), k, 0.8F, 1.05F + owner.getRandom().nextFloat() * 0.1F);
		if (fresh) owner.sendOverlayMessage(Component.translatable("message.elsewhere.whip." + k.tag, t.getDisplayName()).withStyle(s -> s.withColor(k.color)));
	}

	/** A minion hit landed on a marked target. */
	public static void minionHit(LivingEntity t, WhipKind k) {
		if (!(t.level() instanceof ServerLevel l)) return;
		l.sendParticles(dust(k, 0.8F), t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 3, t.getBbWidth() * 0.3, t.getBbHeight() * 0.25, t.getBbWidth() * 0.3, 0);
	}
}
