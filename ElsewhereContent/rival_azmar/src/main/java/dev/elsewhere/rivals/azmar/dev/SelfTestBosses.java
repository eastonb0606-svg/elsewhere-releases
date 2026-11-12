package dev.elsewhere.rivals.azmar.dev;

import static dev.elsewhere.rivals.azmar.dev.SelfTest.STEPS;
import static dev.elsewhere.rivals.azmar.dev.SelfTest.check;
import static dev.elsewhere.rivals.azmar.dev.SelfTest.info;

import com.mojang.authlib.GameProfile;
import dev.elsewhere.duelist.DuelistEvents;
import dev.elsewhere.duelist.adapt.Formation;
import dev.elsewhere.duelist.adapt.LoadoutClass;
import dev.elsewhere.duelist.adapt.PlayerTrace;
import dev.elsewhere.duelist.adapt.Side;
import dev.elsewhere.duelist.adapt.Tactic;
import dev.elsewhere.duelist.blocks.DuelBlocks;
import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.kingdom_court.CourtEvents;
import dev.elsewhere.kingdom_court.CourtLife;
import dev.elsewhere.kingdom_court.CourtState;
import dev.elsewhere.kingdom_court.api.Succession;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import dev.elsewhere.rivals.azmar.AzmarContent;
import dev.elsewhere.rivals.azmar.AzmarItems;
import dev.elsewhere.rivals.azmar.boss.AzmarAmir;
import dev.elsewhere.rivals.azmar.boss.AzmarBoss;
import dev.elsewhere.rivals.azmar.boss.AzmarBosses;
import dev.elsewhere.rivals.azmar.boss.AzmarFalconer;
import dev.elsewhere.rivals.azmar.boss.AzmarSultana;
import dev.elsewhere.rivals.azmar.boss.AzmarWazir;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Boss fight tests: phases, signature moves, the Sultana's and Wazir's adaptive reads, the Amir's camel, the Falconer's falcon, no griefing, drops and the Regent rule. */
final class SelfTestBosses {
	private SelfTestBosses() {}

	static final Map<String, Integer> MOVES = new HashMap<>();
	/** Largest hit the bosses TRIED to deal (a FakePlayer is invulnerable, so landed hits cannot be counted) per boss type path. */
	static final Map<String, Float> TRIED = new HashMap<>();
	static FakePlayer fp;
	static HumanBoss boss;
	static long blockHash;
	static final String FAKE = "azmar-bosstest";

	static boolean passiveWarned;

	/** HumanBoss.passive when the duelist jar has it (newer than 0.1.0 dist); otherwise the boss simply keeps fighting. */
	static void pass(HumanBoss b, boolean on) {
		try {
			HumanBoss.class.getField("passive").setBoolean(b, on);
		} catch (ReflectiveOperationException e) {
			if (!passiveWarned) { passiveWarned = true; info("duelist jar has no HumanBoss.passive: bosses keep their AI during scripted checks"); }
		}
	}

	static int y() { return SelfTest.y; }

	/** Re-queues itself until {@code c} is true or {@code timeout} ticks pass. */
	static void poll(String name, int timeout, BooleanSupplier c) {
		long[] start = {-1};
		STEPS.add(new SelfTest.Step() {
			public int run(MinecraftServer srv, ServerLevel l) {
				if (start[0] < 0) start[0] = l.getGameTime();
				boolean ok;
				try { ok = c.getAsBoolean(); } catch (RuntimeException e) { ok = false; e.printStackTrace(); }
				if (ok) { check(name, true, null); return 0; }
				if (l.getGameTime() - start[0] > timeout) { check(name + " (timeout " + timeout + ")", false, null); return 0; }
				STEPS.add(SelfTest.idx, this);   // idx already points past this step: run again next tick
				return 0;
			}
		});
	}

	/** A step that runs a block of work once. */
	static void run(java.util.function.BiConsumer<MinecraftServer, ServerLevel> r) {
		STEPS.add((srv, l) -> { r.accept(srv, l); return 0; });
	}

	/** Runs {@code r} every tick for {@code ticks} ticks. */
	static void each(int ticks, java.util.function.Consumer<ServerLevel> r) {
		int[] n = {0};
		STEPS.add(new SelfTest.Step() {
			public int run(MinecraftServer srv, ServerLevel l) {
				r.accept(l);
				if (++n[0] < ticks) STEPS.add(SelfTest.idx, this);
				return 0;
			}
		});
	}

	static void sync(String name, BooleanSupplier c) {
		run((srv, l) -> { boolean ok; try { ok = c.getAsBoolean(); } catch (RuntimeException e) { ok = false; e.printStackTrace(); } check(name, ok, null); });
	}

	static FakePlayer player(ServerLevel l, double x, double z) {
		FakePlayer f = FakePlayer.get(l, new GameProfile(UUID.nameUUIDFromBytes(FAKE.getBytes()), "azmar-bosstest"));
		f.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
		f.setHealth(2000F);
		f.snapTo(x + 0.5, y(), z + 0.5, 0F, 0F);
		
		f.getInventory().clearContent();
		if (f.level() == l && l.getEntity(f.getUUID()) == null) l.addFreshEntity(f);
		return f;
	}

	static <T extends HumanBoss> T spawn(ServerLevel l, EntityType<T> type, double x, double z) {
		T b = type.create(l, EntitySpawnReason.COMMAND);
		b.snapTo(x + 0.5, y(), z + 0.5, 0F, 0F);
		b.finalizeSpawn(l, l.getCurrentDifficultyAt(BlockPos.containing(x, y(), z)), EntitySpawnReason.COMMAND, null);
		l.addFreshEntity(b);
		return b;
	}

	static void face(FakePlayer p, Vec3 at) {
		Vec3 d = at.subtract(p.position());
		float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
		p.snapTo(p.getX(), p.getY(), p.getZ(), yaw, 0F);
		p.setYHeadRot(yaw);
	}

	static long hash(ServerLevel l, int cx, int cz, int r) {
		long h = 1125899906842597L;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) for (int yy = y() - 4; yy < y() + 12; yy++) {
			net.minecraft.world.level.block.Block b = l.getBlockState(p.set(x, yy, z)).getBlock();
			if (b == net.minecraft.world.level.block.Blocks.GRASS_BLOCK) b = net.minecraft.world.level.block.Blocks.DIRT;   // vanilla random ticks turn grass under any temporary block to dirt: not griefing (as SelfTestWazir.natural)
			h = 31 * h + System.identityHashCode(b);
		}
		return h;
	}

	/** Keeps the fight at the arena: targets the player and brings both back when a chase has drifted (unloaded chunks stop entities ticking). */
	static void keepTarget() {
		if (boss == null || !boss.isAlive() || fp == null) return;
		boss.setTarget(fp);
		if (Math.abs(boss.getX() - 0.5) > 30 || Math.abs(boss.getZ() - 150.5) > 30) {
			boss.teleportTo(0.5, y(), 150.5);
			fp.snapTo(0.5, y(), 150.5 + 10, fp.getYRot(), 0F);
		}
	}

	/** Puts the player within {@code d} blocks of the boss when it is further away. */
	static void within(double d) {
		if (boss != null && fp != null && boss.distanceTo(fp) > d) fp.snapTo(boss.getX(), y(), boss.getZ() + d - 0.5, fp.getYRot(), 0F);
	}

	static void add0(String only) {
		hooks();
		addSuites(only);
		if (only.contains("bench")) SelfTestTactics.bench();
	}

	static void hooks() {
		DuelistEvents.MOVE.add((b, id) -> MOVES.merge(id, 1, Integer::sum));
		DuelistEvents.HIT.add((b, victim, amount, kind, landed) -> { if (kind != dev.elsewhere.duelist.kit.DamageCaps.MAGIC) TRIED.merge(b.getType().toShortString() + "|" + kind.name(), amount, Math::max); });
	}

	static void add() {
		hooks();
		addSuites("all");
	}

	/** {@code AZMAR_ONLY=sultana,wazir,...} (env) runs just those suites (sultana, wazir, falconer, amir, firepot, court); anything else runs all. */
	static void addSuites(String only) {
		boolean all = only == null || only.isBlank() || only.equals("all");
		if (all || only.contains("sultana")) SelfTestSultana.add();
		if (all || only.contains("wazir")) SelfTestWazir.add();
		if (all || only.contains("falconer")) SelfTestFalconer.add();
		if (all || only.contains("amir")) SelfTestAmir.add();
		if (all || only.contains("firepot")) firePot();
		if (all || only.contains("court")) court();
		if (all || only.contains("comp")) SelfTestComp.add();
	}

	// ---- the Naffatun fire pot against the Wazir and his Hands (kept from the first suite) ---------------------------------

	static void firePot() {
		run((srv, l) -> { LV = l; SelfTest.clean(l); blockHash = hash(l, 0, 150, 40); });
		run((srv, l) -> {
			SelfTest.clean(l);
			dev.elsewhere.rivals.azmar.FirePots.clear();
			fp = player(l, 0, 150 + 45);
			boss = spawn(l, AzmarBosses.WAZIR, 0, 150);
			pot = dev.elsewhere.kingdom.KingdomApi.spawnSoldier(l, AzmarContent.id("azmar_naffatun"), AzmarContent.FACTION, new Vec3(0.5, y(), 150 + 9.5), null);
			pot.setNoAi(true);
		});
		each(20, l -> { boss.setTarget(null); });
		run((srv, l) -> { hp0 = boss.getHealth(); dev.elsewhere.rivals.azmar.FirePots.lob(l, pot, boss.position()); });
		each(120, l -> { boss.setTarget(null); });
		run((srv, l) -> {
			StringBuilder d = new StringBuilder("boss " + hp0 + "->" + boss.getHealth() + " pot=" + pot.factionId());
			for (var h : ((AzmarWazir) boss).hands) d.append(" | hand ").append(h.factionId()).append(' ').append(h.getHealth()).append('/').append(h.getMaxHealth()).append(" tgt=").append(h.getTarget());
			boolean ok = boss.getHealth() >= hp0 - 0.01F && ((AzmarWazir) boss).hands.stream().allMatch(h -> h.getHealth() >= h.getMaxHealth() - 0.01F);
			check("a Naffatun's fire pot bursting on the Wazir and his Hands leaves them unhurt (friendly fire off for Azmar's court), and a patch burned", ok, d.toString());
		});
		run((srv, l) -> { pass(boss, false); DuelBlocks.clearAll(l); boss.discard(); SelfTest.clean(l); });
		sync("no block griefing during the Wazir fight (glyph circles are particles)", () -> hash(l0x(), 0, 150, 40) == blockHash);
	}

	static ServerLevel LV;
	static dev.elsewhere.kingdom.KingdomSoldier pot;
	static float hp0;

	static ServerLevel l0x() { return LV; }

	// ---- Court: drops, Regent, no refill --------------------------------------------------------------------------------------

	static void court() {
		List<String> kinds = new ArrayList<>();
		CourtState[] st = new CourtState[1];
		HumanBoss[] c = new HumanBoss[4];
		run((srv, l) -> {
			SelfTest.clean(l);
			CourtEvents.SUCCESSION.register((s, fac, seat, k) -> { if (fac.equals(AzmarContent.FACTION)) kinds.add(seat + ":" + k.name().toLowerCase()); });
			st[0] = CourtState.get(srv, AzmarContent.FACTION);
			st[0].resetForTests();
			fp = player(l, 0, 100);
			Ruler[] seats = {Ruler.KING, Ruler.MARSHAL, Ruler.CASTELLAN, Ruler.HUNTSMAN};
			for (int i = 0; i < 4; i++) {
				Mob m = CourtLife.spawnLeader(l, st[0], seats[i], new Vec3(-30 + i * 14 + 0.5, y(), 100.5), null);
				c[i] = m instanceof HumanBoss h ? h : null;
				if (c[i] != null) pass(c[i], true);
			}
		});
		each(5, l -> {});
		sync("the court spawns Azmar's four bosses through Leaders (king Sultana, marshal Amir, castellan Wazir, huntsman Falconer)", () -> c[0] instanceof AzmarSultana && c[1] instanceof AzmarAmir && c[2] instanceof AzmarWazir && c[3] instanceof AzmarFalconer);
		sync("holder bonus: the Sultana never exceeds the 300 health cap (225 + court bonus, clamped)", () -> c[0].getMaxHealth() >= 225F && c[0].getMaxHealth() <= 300F);
		run((srv, l) -> {
			fp.setHealth(2000F);
			c[0].setHealth(1F);
			c[0].die(l.damageSources().playerAttack(fp));
			c[0].setHealth(0F);
		});
		each(4, l -> {});
		sync("the Sultana's death is tracked by the court and she stays dead", () -> !st[0].alive(Ruler.KING));
		sync("drops follow the DropRule: first kill drops the Nemes (100 percent) and the Winged Scarab Standard roll, the Seal, Dinars 80 to 96", () -> {
			var d = CourtLife.LAST_DROPS_BY.get(AzmarContent.FACTION + "|king");
			if (d == null) return false;
			int nemes = 0, seal = 0, din = 0;
			for (ItemStack s : d) {
				if (s.is(AzmarItems.NEMES_OF_THE_URAEUS)) nemes += s.getCount();
				if (s.is(AzmarItems.SEAL_SULTANA)) seal += s.getCount();
				if (s.is(AzmarItems.DINAR)) din += s.getCount();
			}
			info("sultana drops " + d);
			return nemes == 1 && seal >= 1 && din >= 80 && din <= 96;
		});
		sync("Regent rule: the highest living Lord (the Amir) is crowned and named Regent", () -> st[0].crowned == Ruler.MARSHAL && kinds.contains("marshal:ruler_succession") && c[1].getCustomName() != null && c[1].getCustomName().getString().startsWith("Regent"));
		sync("Regent boost: the Amir carries the ruler bonus (more than his own 182) and stays at most 300", () -> c[1].getMaxHealth() > 182F && c[1].getMaxHealth() <= 300F);
		sync("the Falconer and the Wazir are not Regent", () -> c[2].getCustomName() == null || !c[2].getCustomName().getString().startsWith("Regent"));
		run((srv, l) -> {
			st[0].deadSince.put(Ruler.KING, l.getDefaultClockTime() / 24000L - 40);
			Succession.tick(l, st[0]);
		});
		each(5, l -> {});
		sync("seats are never refilled: after 40 days the Sultana's seat is still empty, no new Sultana, no heir or risen event", () ->
			!st[0].alive(Ruler.KING) && LV.getEntitiesOfClass(AzmarSultana.class, new AABB(-80, y() - 10, 40, 80, y() + 20, 160)).stream().noneMatch(Mob::isAlive)
				&& kinds.stream().noneMatch(k -> k.endsWith(":heir") || k.endsWith(":risen")));
		run((srv, l) -> {
			c[1].setHealth(1F);
			c[1].die(l.damageSources().playerAttack(fp));
			c[1].setHealth(0F);
		});
		each(4, l -> {});
		sync("the Regent dies: the next highest living Lord (the Wazir) takes over, still no seat refilled", () -> st[0].crowned == Ruler.CASTELLAN && !st[0].alive(Ruler.KING) && !st[0].alive(Ruler.MARSHAL));
		run((srv, l) -> {
			for (int i = 2; i < 4; i++) { c[i].setHealth(1F); c[i].die(l.damageSources().playerAttack(fp)); c[i].setHealth(0F); }
		});
		each(4, l -> {});
		sync("Azmar falls when all four are dead", () -> st[0].fallen && st[0].dead.size() == 4);
		run((srv, l) -> { st[0].resetForTests(); SelfTest.clean(l); });
	}
}
