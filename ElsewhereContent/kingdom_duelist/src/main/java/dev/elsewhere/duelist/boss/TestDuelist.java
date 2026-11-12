package dev.elsewhere.duelist.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Two test duelists ({@code elsewhere:duelist_test}, {@code elsewhere:duelist_test_ruler}) with deliberately different
 * configurations: the first circle-strafes and sprint-jump crits and winds up in a spiral of ember smoke; the second
 * holds its ground and winds up in a ring of violet sparks. They exist for the self-test and the screenshots and as a
 * model for a faction boss. The tests replace the profile through {@link #NEXT}.
 */
public class TestDuelist extends HumanBoss {
	public static final TelegraphStyle EMBER_LOOK = new TelegraphStyle(ParticleTypes.FLAME, 0xFF6A1A, SoundEvents.BLAZE_SHOOT, 0.9F, TelegraphStyle.Shape.SPIRAL);
	public static final TelegraphStyle VIOLET_LOOK = new TelegraphStyle(ParticleTypes.WITCH, 0x9B30FF, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3F, TelegraphStyle.Shape.RING);

	public static final DuelistProfile PROFILE = DuelistProfile.builder("duelist_test")
		.hp(120).walkSpeed(0.3).swing(16F, 14, 8).reach(3.0).phases(0.66, 0.33).caps(18F, 38F)
		.melee(() -> new ItemStack(Items.IRON_SWORD))
		.strafe(new Behaviours.Strafe(0.9F, 16, 40, 2.5, 3.5, 10))
		.sprintCrit(new Behaviours.SprintCrit(3.0, 8, 80, 16, 1.5F))
		.telegraph(EMBER_LOOK)
		.barColor(BossEvent.BossBarColor.YELLOW)
		.skin("duelist_test")
		.build();

	public static final DuelistProfile RULER_PROFILE = DuelistProfile.builder("duelist_test_ruler")
		.ruler().hp(200).walkSpeed(0.26).swing(18F, 18, 12).reach(3.2).phases(0.5).caps(18F, 40F)
		.melee(() -> new ItemStack(Items.DIAMOND_SWORD))
		.telegraph(VIOLET_LOOK)
		.barColor(BossEvent.BossBarColor.PURPLE)
		.skin("duelist_test")
		.build();

	/** Test only: the profile the next {@code TestDuelist} constructed will use (then cleared). */
	public static DuelistProfile NEXT;

	private static DuelistProfile chooseProfile(DuelistProfile dflt) {
		DuelistProfile p = NEXT != null ? NEXT : dflt;
		NEXT = null;
		return p;
	}

	public TestDuelist(EntityType<? extends HumanBoss> type, Level level) {
		super(type, level, chooseProfile(PROFILE));
	}

	protected TestDuelist(EntityType<? extends HumanBoss> type, Level level, DuelistProfile p) {
		super(type, level, p);
	}

	public static class Ruler extends TestDuelist {
		public Ruler(EntityType<? extends HumanBoss> type, Level level) {
			super(type, level, chooseProfile(RULER_PROFILE));
		}
	}
}
