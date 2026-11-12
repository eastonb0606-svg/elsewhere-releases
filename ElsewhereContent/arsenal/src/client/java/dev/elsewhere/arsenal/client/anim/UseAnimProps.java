package dev.elsewhere.arsenal.client.anim;

import com.mojang.serialization.MapCodec;
import dev.elsewhere.arsenal.ArsenalContent;
import dev.elsewhere.arsenal.client.anim.mixin.ConditionalPropsAccessor;
import dev.elsewhere.arsenal.client.anim.mixin.RangePropsAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;


/**
 * Item model properties for the weapon states (used in {@code assets/elsewhere/items/*.json}). Each one reads state
 * the client already has, only when the item is drawn, and returns the plain state while {@code use_anims} is off.
 * <ul>
 * <li>{@code elsewhere:attack_charge} (range): the holder's attack recharge 0..1 for the main-hand stack, else 0.</li>
 * <li>{@code elsewhere:cast_cooldown} (range): the item cooldown fraction left (1 just cast, 0 ready).</li>
 * <li>{@code elsewhere:channel_time} (range): ticks the stack has been held in use, else 0.</li>
 * <li>{@code elsewhere:lunge_ready} (condition): the holder sprints with this stack in the main hand.</li>
 * <li>{@code elsewhere:channeling} (condition): the holder is using this stack.</li>
 * </ul>
 */
public final class UseAnimProps {
	private UseAnimProps() {}

	private static boolean done;

	public static void register() {
		if (done) return;
		done = true;
		try {
			RangePropsAccessor.elsewhere$idMapper().put(ArsenalContent.id("attack_charge"), AttackCharge.CODEC);
			RangePropsAccessor.elsewhere$idMapper().put(ArsenalContent.id("cast_cooldown"), CastCooldown.CODEC);
			RangePropsAccessor.elsewhere$idMapper().put(ArsenalContent.id("channel_time"), ChannelTime.CODEC);
			ConditionalPropsAccessor.elsewhere$idMapper().put(ArsenalContent.id("lunge_ready"), LungeReady.CODEC);
			ConditionalPropsAccessor.elsewhere$idMapper().put(ArsenalContent.id("channeling"), Channeling.CODEC);
		} catch (Throwable t) {
			// accessor mixin not applied: logged; the weapon item models then fail to load (missing model), never a crash
			System.err.println("[elsewhere] weapon item-model properties not registered: " + t);
		}
	}

	static LivingEntity living(ItemOwner owner) {
		return owner == null ? null : owner.asLivingEntity();
	}

	public record AttackCharge() implements RangeSelectItemModelProperty {
		static final MapCodec<AttackCharge> CODEC = MapCodec.unit(new AttackCharge());

		@Override
		public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
			if (!UseAnims.ON || !(living(owner) instanceof Player p) || p.getMainHandItem() != stack) return 0F;
			return p.getAttackStrengthScale(0F);
		}

		@Override
		public MapCodec<AttackCharge> type() {
			return CODEC;
		}
	}

	public record CastCooldown() implements RangeSelectItemModelProperty {
		static final MapCodec<CastCooldown> CODEC = MapCodec.unit(new CastCooldown());

		@Override
		public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
			if (!UseAnims.ON || !(living(owner) instanceof Player p)) return 0F;
			return p.getCooldowns().getCooldownPercent(stack, 0F);
		}

		@Override
		public MapCodec<CastCooldown> type() {
			return CODEC;
		}
	}

	public record ChannelTime() implements RangeSelectItemModelProperty {
		static final MapCodec<ChannelTime> CODEC = MapCodec.unit(new ChannelTime());

		@Override
		public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
			LivingEntity e = living(owner);
			if (!UseAnims.ON || e == null || !e.isUsingItem() || e.getUseItem() != stack) return 0F;
			return e.getTicksUsingItem();
		}

		@Override
		public MapCodec<ChannelTime> type() {
			return CODEC;
		}
	}

	public record LungeReady() implements ConditionalItemModelProperty {
		static final MapCodec<LungeReady> CODEC = MapCodec.unit(new LungeReady());

		@Override
		public boolean get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext ctx) {
			return UseAnims.ON && owner != null && owner.isSprinting() && owner.getMainHandItem() == stack;
		}

		@Override
		public MapCodec<LungeReady> type() {
			return CODEC;
		}
	}

	public record Channeling() implements ConditionalItemModelProperty {
		static final MapCodec<Channeling> CODEC = MapCodec.unit(new Channeling());

		@Override
		public boolean get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext ctx) {
			return UseAnims.ON && owner != null && owner.isUsingItem() && owner.getUseItem() == stack;
		}

		@Override
		public MapCodec<Channeling> type() {
			return CODEC;
		}
	}
}
