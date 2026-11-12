package dev.elsewhere.duelist.dev;

import dev.elsewhere.boss.kit.ForesightGear;
import dev.elsewhere.duelist.boss.Telegraph;
import dev.elsewhere.duelist.boss.TestDuelist;
import dev.elsewhere.duelist.foresight.Foresight;
import dev.elsewhere.duelist.foresight.ForesightBossEvent;
import dev.elsewhere.duelist.kit.GroundPatch;
import dev.elsewhere.duelist.mixin.LivingEntityInvoker;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * B5 (final review): no attack hints without foresight gear. One player holds an item of {@code #elsewhere:foresight_gear}
 * in the off hand (foresight on), one holds nothing (foresight off). In this module's own run the tag gets
 * {@code minecraft:spyglass} from the dev world pack {@code run/world/datapacks/elsewhere_foresight_test}; in the merged
 * build it holds the real foresight gear.
 */
final class SelfTestForesight {
	private SelfTestForesight() {}

	/** The first item of the foresight tag (empty if the tag is empty in this build). */
	static ItemStack foresightItem() {
		for (Holder<Item> hd : BuiltInRegistries.ITEM.getTagOrEmpty(ForesightGear.TAG)) return new ItemStack(hd.value());
		return ItemStack.EMPTY;
	}

	/** Puts a stack in the off hand and runs the equipment check the game runs every tick (a FakePlayer never ticks): it fires EQUIPMENT_CHANGE. */
	static void offhand(ServerPlayer p, ItemStack s) {
		p.setItemSlot(EquipmentSlot.OFFHAND, s);
		((LivingEntityInvoker) p).elsewhere$detectEquipmentUpdates();
	}

	static String last(List<String> l) {
		return l.isEmpty() ? "" : l.get(l.size() - 1);
	}

	static void add(H h) {
		final FakePlayer[] f = new FakePlayer[2];
		final boolean[] r = new boolean[5];
		h.test("B5 foresight: gear check and the particle gate").run(() -> {
			f[0] = h.fake(2, 2);
			f[1] = h.fake(-2, 2);
			Foresight.forget(f[0]);
			Foresight.forget(f[1]);
			offhand(f[0], foresightItem());
			double x = 0, y = H.Y + 1, z = 2;
			r[0] = h.level.sendParticles(f[0], ParticleTypes.END_ROD, false, false, x, y, z, 1, 0, 0, 0, 0);
			r[1] = h.level.sendParticles(f[1], ParticleTypes.END_ROD, false, false, x, y, z, 1, 0, 0, 0, 0);
			Foresight.hint(() -> {
				r[2] = h.level.sendParticles(f[0], ParticleTypes.END_ROD, false, false, x, y, z, 1, 0, 0, 0, 0);
				r[3] = h.level.sendParticles(f[1], ParticleTypes.END_ROD, false, false, x, y, z, 1, 0, 0, 0, 0);
			});
			Foresight.enabled = false;
			try {
				Foresight.hint(() -> r[4] = h.level.sendParticles(f[1], ParticleTypes.END_ROD, false, false, x, y, z, 1, 0, 0, 0, 0));
			} finally {
				Foresight.enabled = true;
			}
		}).check("the foresight tag has an item in this build (" + foresightItem().getHoverName().getString() + ")", () -> !foresightItem().isEmpty())
			.check("foresight off for a bare player, on with a foresight item in the off hand", () -> Foresight.sees(f[0]) && !Foresight.sees(f[1]))
			.check("outside a hint scope both players get a particle (attack effects stay visible to all)", () -> r[0] && r[1])
			.check("inside a hint scope only the player with foresight gets the particle", () -> r[2] && !r[3])
			.check("kill switch foresight_gate off: the hint reaches the player without gear too", () -> r[4]);

		final TestDuelist[] b = new TestDuelist[1];
		final List<String> toOn = new ArrayList<>(), toOff = new ArrayList<>();
		final int[] sc = new int[2], pk = new int[4];
		final Component[] full = new Component[1];
		final String[] off = new String[2];
		h.test("B5 foresight: duelist wind-up particles and the bar's move label").run(() -> {
			f[0] = h.fake(3, 3);
			f[1] = h.fake(-3, 3);
			Foresight.forget(f[0]);
			Foresight.forget(f[1]);
			offhand(f[0], foresightItem());
			toOn.clear();
			toOff.clear();
			ForesightBossEvent.sentHook = (p, c) -> {
				if (p == f[0]) toOn.add(c.getString());
				else if (p == f[1]) toOff.add(c.getString());
			};
			b[0] = h.boss(H.base("fsight").build(), 0, 0);
			b[0].passive = true;
			// both players stand in the world (level.players()), so the boss's broadcast particles really reach or skip them
			for (FakePlayer p : f) if (h.level.getEntity(p.getUUID()) == null) h.level.addFreshEntity(p);
			b[0].startSeenByPlayer(f[0]);
			b[0].startSeenByPlayer(f[1]);
			sc[0] = Foresight.scopes;
			pk[0] = Foresight.shown;
			pk[1] = Foresight.held;
			b[0].startTelegraph("foresight_slam", "Foresight Slam", 20, Telegraph.Weight.HEAVY, 0F, b[0].style().cue(false, true), true, () -> {});
		}).wait(10).run(() -> {
			sc[1] = Foresight.scopes;
			pk[2] = Foresight.shown;
			pk[3] = Foresight.held;
			full[0] = b[0].bossBarName();
			List<ForesightBossEvent> bars = ForesightBossEvent.barsOf(f[1]);
			off[0] = bars.isEmpty() ? null : bars.get(0).nameFor(f[1]).getString();
		}).check("the wind-up's telegraph particles are drawn inside a foresight hint scope", () -> {
			h.info("hint scopes in 10 wind-up ticks: " + (sc[1] - sc[0]));
			return sc[1] - sc[0] >= 5;
		}).check("foresight on: the wind-up particles reach the player with gear; foresight off: they are held back from the other", () -> {
			h.info("telegraph particle packets in 10 ticks: shown " + (pk[2] - pk[0]) + ", held " + (pk[3] - pk[1]));
			return pk[2] - pk[0] >= 5 && pk[3] - pk[1] >= 5;
		}).check("the full title (the foresight view) reads Name · Foresight Slam", () -> full[0] != null && full[0].getString().contains("· Foresight Slam"))
			.check("the move part carries the foresight mark boss_ui reads", () -> full[0] != null && full[0].getSiblings().stream().anyMatch(c -> Foresight.MARK.equals(c.getStyle().getInsertion())))
			.check("the player with foresight gear was sent Name · Foresight Slam", () -> toOn.stream().anyMatch(s -> s.contains("Foresight Slam")))
			.check("the player without foresight gear is shown the plain name and never sent the move", () -> {
				h.info("sent to the player without gear: " + toOff + ", shown " + off[0]);
				return off[0] != null && !off[0].contains("·") && toOff.stream().noneMatch(s -> s.contains("Foresight Slam"));
			}).wait(16).run(() -> off[1] = b[0].bossBarName().getString())
			.check("after the move fires the label is gone for both", () -> !off[1].contains("·") && !last(toOn).contains("Foresight Slam"))
			.cleanup(() -> {
				ForesightBossEvent.sentHook = null;
				if (b[0] != null) {
					b[0].stopSeenByPlayer(f[0]);
					b[0].stopSeenByPlayer(f[1]);
				}
				for (FakePlayer p : f) if (p != null) p.discard();
			});

		final ForesightBossEvent[] ev = new ForesightBossEvent[1];
		final List<String> seen = new ArrayList<>();
		final String[] snap = new String[3];
		final boolean[] sight = new boolean[2];
		final int[] fl = new int[2];
		h.test("B5 foresight: taking the gear off mid-label re-sends that player's bar and the client flag").run(() -> {
			f[0] = h.fake(3, -3);
			f[1] = h.fake(-3, -3);
			Foresight.forget(f[0]);
			Foresight.forget(f[1]);
			offhand(f[0], foresightItem());
			seen.clear();
			ForesightBossEvent.sentHook = (p, c) -> {
				if (p == f[0]) seen.add(c.getString());
			};
			ev[0] = new ForesightBossEvent(UUID.randomUUID(), Component.literal("Test Warlord"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
			ev[0].addPlayer(f[0]);
			ev[0].addPlayer(f[1]);
			ev[0].setLabelled(Component.literal("Test Warlord"), "Cleave");
			snap[0] = last(seen);
			fl[0] = Foresight.flags;
			offhand(f[0], ItemStack.EMPTY);
			fl[1] = Foresight.flags;
			snap[1] = last(seen);
			sight[0] = Foresight.sees(f[0]);
			offhand(f[0], foresightItem());
			snap[2] = last(seen);
			sight[1] = Foresight.sees(f[0]);
		}).check("gear on: Test Warlord · Cleave", () -> snap[0].equals("Test Warlord · Cleave"))
			.check("gear taken off: the bar is re-sent as plain Test Warlord at once (equipment event, no polling)", () -> {
				h.info("sent: " + seen);
				return !sight[0] && snap[1].equals("Test Warlord");
			})
			.check("the client flag went out on the change", () -> fl[1] > fl[0])
			.check("gear back on: Cleave shows again", () -> sight[1] && snap[2].equals("Test Warlord · Cleave"))
			.check("the player without gear is shown only Test Warlord", () -> ev[0].nameFor(f[1]).getString().equals("Test Warlord"))
			.cleanup(() -> {
				ForesightBossEvent.sentHook = null;
				if (ev[0] != null) ev[0].removeAllPlayers();
			});

		final int[] pa = new int[2];
		h.test("B5 foresight: a ground patch's pre-arm ring is a foresight hint").run(() -> {
			b[0] = h.boss(H.base("fspatch").build(), 0, 0);
			b[0].passive = true;
			GroundPatch.spawn(h.level, b[0], new Vec3(4, H.Y, 4), 2.0, 20, 40, 20, 0F, List.of(), ParticleTypes.FLAME);
			pa[0] = Foresight.scopes;
		}).wait(16).run(() -> pa[1] = Foresight.scopes)
			.check("the ring drawn before the patch arms goes through the hint scope", () -> {
				h.info("hint scopes in 16 pre-arm ticks: " + (pa[1] - pa[0]));
				return pa[1] - pa[0] >= 3;
			});
	}
}
