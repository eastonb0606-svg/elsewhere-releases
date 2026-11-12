package dev.elsewhere.duelist.dev;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.duelist.DuelistContent;
import dev.elsewhere.duelist.boss.DuelistProfile;
import dev.elsewhere.duelist.boss.TestDuelist;
import dev.elsewhere.duelist.kit.Move;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Self-test harness: arena, actors, a tiny tick-driven script runner and the pass/fail counters. */
public final class H {
	public static final int Y = 100, R = 48;
	public final MinecraftServer server;
	public final ServerLevel level;
	public int pass, fail;
	public final List<String> failures = new ArrayList<>();
	private final List<Case> cases = new ArrayList<>();
	private int caseIdx = -1;
	private Case cur;
	private int stepIdx;
	private long caseStart;
	public String name = "";

	public H(MinecraftServer server) {
		this.server = server;
		this.level = server.overworld();
	}

	public long now() {
		return level.getGameTime();
	}

	// ---- results --------------------------------------------------------------------------------------------------

	public void check(String label, boolean ok) {
		if (ok) {
			pass++;
			System.out.println("[duelist-selftest] PASS " + name + ": " + label);
		} else {
			fail++;
			failures.add(name + ": " + label);
			System.out.println("[duelist-selftest] FAIL " + name + ": " + label);
		}
	}

	public void info(String s) {
		System.out.println("[duelist-selftest] INFO " + name + ": " + s);
	}

	// ---- arena ----------------------------------------------------------------------------------------------------

	public void buildArena() {
		for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
			level.setBlock(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState(), 2);
			for (int y = Y; y < Y + 12; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
			for (int y = Y - 6; y < Y - 1; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
		}
	}

	/** Restores the flat floor and clears the air above in the box (undoes any carved chasm, pool or tower). */
	public void flatten(int x0, int x1, int z0, int z1) {
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
			level.setBlock(new BlockPos(x, Y - 1, z), Blocks.STONE.defaultBlockState(), 2);
			for (int y = Y; y < Y + 12; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
			for (int y = Y - 6; y < Y - 1; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
		}
	}

	/** A hash of every block in the arena box (floor to 12 above): changes if any block anywhere changed. */
	public long arenaHash() {
		long h = 1125899906842597L;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) for (int y = Y - 6; y < Y + 12; y++) {
			BlockState s = level.getBlockState(p.set(x, y, z));
			h = 31 * h + System.identityHashCode(s.getBlock()) * 7L + (s.getFluidState().isEmpty() ? 0 : 1);
		}
		return h;
	}

	public int countBlocks(net.minecraft.world.level.block.Block b) {
		int n = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) for (int y = Y - 6; y < Y + 12; y++) if (level.getBlockState(p.set(x, y, z)).is(b)) n++;
		return n;
	}

	public void clearEntities() {
		AABB box = new AABB(-R - 20, Y - 20, -R - 20, R + 20, Y + 40, R + 20);
		for (Entity e : level.getEntities((Entity) null, box, x -> !(x instanceof net.minecraft.world.entity.player.Player))) e.discard();
	}

	// ---- actors ---------------------------------------------------------------------------------------------------

	public Husk dummy(double x, double z) {
		Husk h = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		h.setNoAi(true);
		h.setSilent(true);
		h.setPersistenceRequired();
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		h.setHealth(1000F);
		h.setPos(x, Y, z);
		level.addFreshEntity(h);
		return h;
	}

	/** A profile with nothing opted in (plumbing only): iron sword, a test telegraph look, 600 HP, and the test numbers every profile must give. */
	public static DuelistProfile.Builder base(String id) {
		return DuelistProfile.builder(id).hp(600).walkSpeed(0.3).swing(16F, 14, 8).reach(3.0).phases(0.66, 0.33).caps(18F, 38F)
			.melee(() -> new ItemStack(Items.IRON_SWORD)).telegraph(TestDuelist.EMBER_LOOK);
	}

	/** A profile with only the listed behaviours on, with the test numbers from {@link TestKit}. */
	public static DuelistProfile.Builder only(String id, Move... keep) {
		return TestKit.with(base(id), keep);
	}

	public TestDuelist boss(DuelistProfile p, double x, double z) {
		TestDuelist.NEXT = p;
		TestDuelist b = DuelistContent.TEST_DUELIST.create(level, EntitySpawnReason.COMMAND);
		TestDuelist.NEXT = null;
		((dev.elsewhere.duelist.mixin.MobAccessor) b).elsewhere$targetSelector().removeAllGoals(g -> true);
		b.setPos(x, Y, z);
		b.setYRot(0);
		level.addFreshEntity(b);
		return b;
	}

	public TestDuelist ruler(DuelistProfile p, double x, double z) {
		TestDuelist.NEXT = p;
		TestDuelist b = DuelistContent.TEST_RULER.create(level, EntitySpawnReason.COMMAND);
		TestDuelist.NEXT = null;
		((dev.elsewhere.duelist.mixin.MobAccessor) b).elsewhere$targetSelector().removeAllGoals(g -> true);
		b.setPos(x, Y, z);
		level.addFreshEntity(b);
		return b;
	}

	private int fakeN;

	public FakePlayer fake(double x, double z) {
		FakePlayer f = FakePlayer.get(level, new GameProfile(UUID.nameUUIDFromBytes(("duelist-fake" + fakeN++).getBytes()), "DuelFake" + fakeN));
		f.setPos(x, Y, z);
		return f;
	}

	// ---- script runner --------------------------------------------------------------------------------------------

	public interface Step {
		boolean tick();
	}

	public final class Case {
		final String name;
		final List<Step> steps = new ArrayList<>();
		Runnable cleanup = () -> {};

		Case(String name) {
			this.name = name;
		}

		public Case run(Runnable r) {
			steps.add(() -> {
				r.run();
				return true;
			});
			return this;
		}

		public Case wait(int ticks) {
			int[] c = {0};
			steps.add(() -> ++c[0] >= ticks);
			return this;
		}

		/** Waits until {@code c} is true; FAIL on timeout (and moves on). */
		public Case until(String label, int timeout, BooleanSupplier c) {
			long[] start = {-1};
			steps.add(() -> {
				if (start[0] < 0) start[0] = now();
				if (c.getAsBoolean()) {
					H.this.check(label, true);
					return true;
				}
				if (now() - start[0] > timeout) {
					H.this.check(label + " (timeout " + timeout + ")", false);
					return true;
				}
				return false;
			});
			return this;
		}

		public Case check(String label, BooleanSupplier c) {
			steps.add(() -> {
				boolean ok;
				try {
					ok = c.getAsBoolean();
				} catch (RuntimeException e) {
					e.printStackTrace();
					ok = false;
				}
				H.this.check(label, ok);
				return true;
			});
			return this;
		}

		public Case each(int ticks, Runnable r) {
			int[] c = {0};
			steps.add(() -> {
				r.run();
				return ++c[0] >= ticks;
			});
			return this;
		}

		public Case cleanup(Runnable r) {
			this.cleanup = r;
			return this;
		}
	}

	/** Env DUELIST_ONLY=text runs only the cases whose name contains the text (fast iteration). */
	public Case test(String name) {
		Case c = new Case(name);
		String only = System.getenv("DUELIST_ONLY");
		if (only == null || only.isEmpty() || name.contains(only)) cases.add(c);
		return c;
	}

	public void start(Runnable onDone) {
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (cases.isEmpty()) return;
			int guard = 0;
			while (guard++ < 200) {
				if (cur == null) {
					if (++caseIdx >= cases.size()) {
						cases.clear();
						onDone.run();
						return;
					}
					cur = cases.get(caseIdx);
					name = cur.name;
					stepIdx = 0;
					caseStart = now();
					System.out.println("[duelist-selftest] CASE " + cur.name);
				}
				if (stepIdx >= cur.steps.size()) {
					try {
						cur.cleanup.run();
					} catch (RuntimeException e) {
						e.printStackTrace();
					}
					clearEntities();
					cur = null;
					continue;
				}
				boolean done;
				try {
					done = cur.steps.get(stepIdx).tick();
				} catch (RuntimeException e) {
					e.printStackTrace();
					check("exception in step " + stepIdx, false);
					done = true;
				}
				if (!done) return;
				stepIdx++;
			}
		});
	}

	public static float dist(LivingEntity a, LivingEntity b) {
		return a.distanceTo(b);
	}
}
