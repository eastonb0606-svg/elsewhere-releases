package dev.elsewhere.boss.strawgrim;

import dev.elsewhere.overworld_bosses.OwbItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Murder Crow (BOSSES.md supporting mobs): a small dark phantom that does not burn in daylight. Strawgrim's crows
 * circle and dive at the player it marked with Glowing. Players wearing the Scarecrow's Grin are ignored.
 */
public class MurderCrow extends Phantom {
	private @Nullable LivingEntity marked;

	public MurderCrow(EntityType<? extends MurderCrow> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 8.0).add(Attributes.ATTACK_DAMAGE, 10.0).add(Attributes.FOLLOW_RANGE, 48.0);
	}

	@Override
	public void setPhantomSize(int size) {
		super.setPhantomSize(0);
		getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0);
	}

	public void setMarked(LivingEntity target) {
		this.marked = target;
		setTarget(target);
	}

	public static boolean ignores(LivingEntity e) {
		return e instanceof Player p && p.getItemBySlot(EquipmentSlot.HEAD).is(OwbItems.SCARECROWS_GRIN);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (level() instanceof ServerLevel && this.tickCount % 10 == 0) {
			LivingEntity t = getTarget();
			if (t != null && ignores(t)) setTarget(null);
			if (this.marked != null && this.marked.isAlive() && !ignores(this.marked) && getTarget() != this.marked) setTarget(this.marked);
		}
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !ignores(target) && super.canAttack(target);
	}
}
