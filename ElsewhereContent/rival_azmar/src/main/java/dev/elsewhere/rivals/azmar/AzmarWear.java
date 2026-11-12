package dev.elsewhere.rivals.azmar;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

/** Worn-item abilities: Uraeus Ward (nemes), Sandrunner speed on sand, Ibis calling (villager-trade XP). */
public final class AzmarWear {
	private AzmarWear() {}

	public static final Identifier SAND_SPEED = AzmarContent.id("sandrunner_speed");
	private static boolean awarding;
	private static final java.util.Map<java.util.UUID, Float> HEALTH_BEFORE = new java.util.HashMap<>();
	public static final double SAND_BONUS = 0.10;
	public static final float WARD_BASE = 0.10F, WARD_PER_SEAL = 0.01F, WARD_MAX = 0.12F;

	/** Number of distinct Azmar Lord Seals in the inventory (each counts once). */
	public static int sealCount(Player p) {
		int n = 0;
		for (var item : new net.minecraft.world.item.Item[] {AzmarItems.SEAL_SULTANA, AzmarItems.SEAL_WAZIR, AzmarItems.SEAL_FALCONER, AzmarItems.SEAL_AMIR})
			if (p.getInventory().contains(s -> s.is(item))) n++;
		return n;
	}

	/** Damage reduction fraction of the Uraeus Ward for this entity right now (0 when not in open sky by day or not worn). */
	public static float wardFraction(LivingEntity e) {
		if (!e.getItemBySlot(EquipmentSlot.HEAD).is(AzmarItems.NEMES_OF_THE_URAEUS)) return 0F;
		if (!(e.level() instanceof ServerLevel lv)) return 0F;
		BlockPos p = e.blockPosition();
		if (lv.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ()) > p.getY() + 1 || !lv.isBrightOutside()) return 0F;
		float f = WARD_BASE + (e instanceof Player pl ? WARD_PER_SEAL * sealCount(pl) : 0F);
		return Math.min(f, WARD_MAX);
	}

	static void init() {
		// Uraeus Ward: heal back the warded share of damage that was just taken (no damage-modify event exists)
		// Only health actually lost is healed back (damage soaked by absorption never heals).
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity.getItemBySlot(EquipmentSlot.HEAD).is(AzmarItems.NEMES_OF_THE_URAEUS)) HEALTH_BEFORE.put(entity.getUUID(), entity.getHealth());
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Float before = HEALTH_BEFORE.remove(entity.getUUID());
			float f = wardFraction(entity);
			if (before == null || f <= 0F || taken <= 0F || !entity.isAlive()) return;
			float lost = before - entity.getHealth();
			if (lost > 0F) entity.heal(Math.min(taken * f, lost));
		});
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (srv.getTickCount() % 5 != 0) return;
			for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
				AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed == null) continue;
				boolean on = false;
				ItemStack boots = p.getItemBySlot(EquipmentSlot.FEET);
				if (boots.is(AzmarItems.SANDRUNNER_SANDALS) && p.onGround()) {
					BlockState b = p.level().getBlockState(p.blockPosition().below());
					on = b.is(BlockTags.SAND) || b.is(Blocks.RED_SAND) || b.is(BlockTags.TERRACOTTA);
				}
				if (on) speed.addOrUpdateTransientModifier(new AttributeModifier(SAND_SPEED, SAND_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				else if (speed.getModifier(SAND_SPEED) != null) speed.removeModifier(SAND_SPEED);
			}
		});
		// Ibis Crest: +25 percent experience from villager trades (an orb spawned next to a villager with an Ibis-wearing player close by)
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (awarding || !(entity instanceof net.minecraft.world.entity.ExperienceOrb orb) || orb.tickCount > 0 || !(level instanceof ServerLevel sl)) return;
			boolean villager = !level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, orb.getBoundingBox().inflate(2.5)).isEmpty();
			if (!villager) return;
			for (Player p : level.getEntitiesOfClass(Player.class, orb.getBoundingBox().inflate(8)))
				if (p.getItemBySlot(EquipmentSlot.HEAD).is(AzmarItems.IBIS_CREST)) {
					awarding = true;
					try {
						net.minecraft.world.entity.ExperienceOrb.award(sl, orb.position(), Math.max(1, Math.round(orb.getValue() * 0.25F)));
					} finally {
						awarding = false;
					}
					return;
				}
		});
	}
}
