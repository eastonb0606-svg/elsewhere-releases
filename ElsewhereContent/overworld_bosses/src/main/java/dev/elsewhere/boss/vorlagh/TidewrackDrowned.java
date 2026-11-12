package dev.elsewhere.boss.vorlagh;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;

/**
 * Tidewrack Drowned: Vorlagh's boarders, also found in deep oceans and around the Wreck Ring. They drop the Siren
 * Conch (loot table {@code elsewhere:entities/tidewrack_drowned}).
 */
public class TidewrackDrowned extends Drowned {
	public TidewrackDrowned(EntityType<? extends TidewrackDrowned> type, Level level) {
		super(type, level);
	}

	/** Site-only mob: never spawns naturally while the server's overworldContent switch is off. */
	@Override
	public boolean checkSpawnRules(net.minecraft.world.level.LevelAccessor level, net.minecraft.world.entity.EntitySpawnReason reason) {
		if ((reason == net.minecraft.world.entity.EntitySpawnReason.NATURAL || reason == net.minecraft.world.entity.EntitySpawnReason.CHUNK_GENERATION
			|| reason == net.minecraft.world.entity.EntitySpawnReason.STRUCTURE) && !dev.elsewhere.overworld_bosses.OwbConfig.overworldContent()) return false;
		return super.checkSpawnRules(level, reason);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 26.0).add(Attributes.ARMOR, 3.0).add(Attributes.ATTACK_DAMAGE, 4.0);
	}
}
