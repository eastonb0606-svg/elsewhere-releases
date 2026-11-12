package dev.elsewhere.duelist.dev;

import dev.elsewhere.duelist.boss.Behaviours.*;
import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.kit.Move;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * TEST ONLY. One set of numbers for every behaviour so the self-test can exercise each behaviour on its own. These are
 * the numbers of the retired shared kit; no boss uses them (each boss sets its own). They live here, not in the module API.
 */
public final class TestKit {
	private TestKit() {}

	public static Strafe strafe() { return new Strafe(0.9F, 16, 40, 2.5, 3.5, 10); }
	public static SprintCrit crit() { return new SprintCrit(3.0, 8, 80, 16, 1.5F); }
	public static Bash bash() { return new Bash(10, 80, 8F, 2F); }
	public static Shield shield() { return new Shield(5, 12, 30, 16, 120F, 100, bash()); }
	public static ShieldBreak shieldBreak() { return new ShieldBreak(60, 120); }
	public static Swap swap() { return new Swap(3.5, 9.0, 7, 40, 10, 60, 14F); }
	public static Bow bow() { return new Bow(20, 30, 3.0, 12.0, 0.12, 8.0, 0.5, 14F); }
	public static Crossbow crossbow() { return new Crossbow(25, 40, 1.5, 30, 14F); }
	public static HealRule healRule() { return new HealRule(0.60F, 4.0, 2, 120); }
	public static Eat eat() { return new Eat(0.10F, 60, healRule()); }
	public static Drink drink() { return new Drink(0.15F, healRule()); }
	public static Pearl pearl() { return new Pearl(20, 40, 4, 60, 16, 600, 3, 2F); }
	public static TempBlocks blocks() { return new TempBlocks(8, 600, 8, 6, 5); }

	/** Turns {@code m} on in a builder with the test numbers (and the weapons it needs). */
	public static DuelistProfile.Builder with(DuelistProfile.Builder b, Move... moves) {
		for (Move m : moves) {
			switch (m) {
				case STRAFE -> b.strafe(strafe());
				case CRIT -> b.sprintCrit(crit());
				case SHIELD -> b.shield(shield());
				case SWAP -> b.swap(swap()).thrown(() -> new ItemStack(Items.TRIDENT));
				case BOW -> b.bow(bow()).ranged(() -> new ItemStack(Items.BOW));
				case CROSSBOW -> b.crossbow(crossbow()).ranged(() -> new ItemStack(Items.CROSSBOW));
				case EAT -> b.eat(eat()).food(() -> new ItemStack(Items.COOKED_BEEF));
				case DRINK -> b.drink(drink()).potion(HumanBoss::healingPotion);
				case PEARL -> b.pearl(pearl());
				case BLOCKS -> b.tempBlocks(blocks());
			}
		}
		return b;
	}

	/** Every behaviour on (for the performance test; the ranged weapon is a bow). */
	public static DuelistProfile.Builder all(DuelistProfile.Builder b) {
		return with(b, Move.STRAFE, Move.CRIT, Move.SHIELD, Move.SWAP, Move.BOW, Move.EAT, Move.DRINK, Move.PEARL, Move.BLOCKS);
	}
}
