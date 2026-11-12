package dev.elsewhere.ironpeak.mob;

import dev.elsewhere.ironpeak.IronpeakEntities;
import dev.elsewhere.ironpeak.IronpeakItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Highland Yak: a shaggy, passive mountain cow. Milk it with a bucket for Yak Milk. Drops Yak Meat and leather. */
public class HighlandYak extends AbstractCow {
	public HighlandYak(EntityType<? extends AbstractCow> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return AbstractCow.createAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.MOVEMENT_SPEED, 0.18).add(Attributes.STEP_HEIGHT, 1.1);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		if (held.is(Items.BUCKET) && !isBaby()) {
			player.playSound(SoundEvents.COW_MILK, 1.0F, 0.8F);
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(IronpeakItems.YAK_MILK)));
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return IronpeakEntities.HIGHLAND_YAK.create(level, EntitySpawnReason.BREEDING);
	}
}
