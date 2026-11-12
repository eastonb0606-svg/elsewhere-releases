package dev.elsewhere.palehollow.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsewhere.Elsewhere;
import dev.elsewhere.registry.ModParticles;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.Level;

/** Death-protection effect of the Resin Heart Locket: roots everything attacking the wearer. */
public record RootAttackersEffect(float radius, int ticks) implements ConsumeEffect {
	public static final MapCodec<RootAttackersEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.FLOAT.fieldOf("radius").forGetter(RootAttackersEffect::radius),
		Codec.INT.fieldOf("ticks").forGetter(RootAttackersEffect::ticks)
	).apply(i, RootAttackersEffect::new));
	public static final StreamCodec<RegistryFriendlyByteBuf, RootAttackersEffect> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.FLOAT, RootAttackersEffect::radius, ByteBufCodecs.VAR_INT, RootAttackersEffect::ticks, RootAttackersEffect::new);
	public static final ConsumeEffect.Type<RootAttackersEffect> TYPE = Registry.register(BuiltInRegistries.CONSUME_EFFECT_TYPE,
		Elsewhere.id("root_attackers"), new ConsumeEffect.Type<>(CODEC, STREAM_CODEC));

	@Override
	public ConsumeEffect.Type<RootAttackersEffect> getType() {
		return TYPE;
	}

	@Override
	public boolean apply(final Level level, final ItemStack stack, final LivingEntity user) {
		if (!(level instanceof ServerLevel server)) {
			return false;
		}
		boolean any = false;
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, user.getBoundingBox().inflate(this.radius), e -> e != user && e.isAlive() && !(e instanceof dev.elsewhere.core.boss.ElsewhereBoss))) {
			boolean attacking = (e instanceof Mob mob && mob.getTarget() == user) || user.getLastHurtByMob() == e;
			if (attacking) {
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, this.ticks, 9, false, true));
				server.sendParticles(ModParticles.RESIN_DUST, e.getX(), e.getY() + 0.2, e.getZ(), 20, 0.4, 0.1, 0.4, 0.02);
				any = true;
			}
		}
		return any;
	}
}
