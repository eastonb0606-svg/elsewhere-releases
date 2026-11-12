package dev.elsewhere.verdigris_ironpeak.anim3;

import dev.elsewhere.boss.hrolf.GrimhornEntity;
import dev.elsewhere.bossart.galvanox.GalvanoxArtModel;
import dev.elsewhere.bossart.galvanox.GalvanoxArtRenderer;
import dev.elsewhere.bossart.galvanox.GalvanoxArtState;
import dev.elsewhere.bossart.hrolf.GrimhornMesh;
import dev.elsewhere.bossart.hrolf.GrimhornModel;
import dev.elsewhere.bossart.hrolf.GrimhornRenderState;
import dev.elsewhere.bossart.hrolf.GrimhornRenderer;
import dev.elsewhere.bossart.hrolf.HrolfMesh;
import dev.elsewhere.bossart.hrolf.HrolfModel;
import dev.elsewhere.bossart.hrolf.HrolfRenderState;
import dev.elsewhere.bossart.hrolf.HrolfRenderer;
import dev.elsewhere.bossart.thurgrim.ThurgrimMesh;
import dev.elsewhere.bossart.thurgrim.ThurgrimModel;
import dev.elsewhere.bossart.thurgrim.ThurgrimRenderState;
import dev.elsewhere.bossart.thurgrim.ThurgrimRenderer;
import dev.elsewhere.bossart.verdigrim.VerdigrimArtModel;
import dev.elsewhere.bossart.verdigrim.VerdigrimArtRenderer;
import dev.elsewhere.bossart.verdigrim.VerdigrimArtState;
import dev.elsewhere.stub.verdigris_ironpeak.StubBoss;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * 3.0 (P8) headless animation model check for the five verdigris_ironpeak bosses (s-p8-bossanims). Run with
 * {@code _tools/bossanimcheck.sh verdigris_ironpeak}; never loaded in play. Per boss: idle breathing, every attack
 * clock the renderer drives must move the body well past the idle pose, the death pose, and the hurt flinch wiring;
 * plus the flinch math and the {@code bossAnimations3} kill switch.
 */
public final class BossAnimCheck {
	private BossAnimCheck() {}

	private static <S> AnimProbe.Attack<S> clock(java.util.function.Function<S, net.minecraft.world.entity.AnimationState> f, Consumer<S> also) {
		return (s, age, since) -> {
			also.accept(s);
			f.apply(s).start((int) (age - since));
		};
	}

	public static void main(String[] args) {
		AnimProbe p = new AnimProbe("verdigris_ironpeak");

		Map<String, AnimProbe.Attack<VerdigrimArtState>> verd = new LinkedHashMap<>();
		verd.put("slam", clock(s -> s.slam, s -> s.attack = 1));
		verd.put("note", clock(s -> s.note, s -> s.attack = 2));
		verd.put("flood", clock(s -> s.flood, s -> s.attack = 3));
		verd.put("arpeggio", clock(s -> s.arpeggio, s -> s.attack = 4));
		verd.put("chord", clock(s -> s.chord, s -> s.attack = 5));
		verd.put("phase_roar", clock(s -> s.roar, s -> { }));
		verd.put("stunned", (s, age, since) -> s.stunned = true);
		boss(p, "verdigrim", new VerdigrimArtModel(VerdigrimArtModel.createBodyLayer().bakeRoot()), VerdigrimArtState::new, verd,
			VerdigrimArtRenderer.class, "dev.elsewhere.boss.verdigrim.VerdigrimBoss", StubBoss.class);

		Map<String, AnimProbe.Attack<GalvanoxArtState>> galv = new LinkedHashMap<>();
		galv.put("hammer", clock(s -> s.hammer, s -> s.attack = 1));
		galv.put("bolt", clock(s -> s.bolt, s -> s.attack = 5));
		galv.put("wax", clock(s -> s.wax, s -> s.attack = 3));
		galv.put("phase_roar", clock(s -> s.roar, s -> { }));
		galv.put("stunned", (s, age, since) -> s.stunned = true);
		boss(p, "galvanox", new GalvanoxArtModel(GalvanoxArtModel.createBodyLayer().bakeRoot()), GalvanoxArtState::new, galv,
			GalvanoxArtRenderer.class, "dev.elsewhere.boss.galvanox.GalvanoxBoss", StubBoss.class);

		Map<String, AnimProbe.Attack<ThurgrimRenderState>> thur = new LinkedHashMap<>();
		thur.put("avalanche_split", clock(s -> s.split, s -> s.attack = 1));
		thur.put("combo", clock(s -> s.combo, s -> s.attack = 2));
		thur.put("boomerang", clock(s -> s.boomerang, s -> s.attack = 3));
		thur.put("command", clock(s -> s.command, s -> s.attack = 4));
		thur.put("phase_roar", clock(s -> s.roar, s -> s.transition = true));
		boss(p, "thurgrim", new ThurgrimModel(ThurgrimMesh.create().bakeRoot()), ThurgrimRenderState::new, thur,
			ThurgrimRenderer.class, "dev.elsewhere.boss.thurgrim.ThurgrimBoss", StubBoss.class);

		Map<String, AnimProbe.Attack<HrolfRenderState>> hrolf = new LinkedHashMap<>();
		hrolf.put("saddle_swing", clock(s -> s.swing, s -> s.attack = 1));
		hrolf.put("overhead", clock(s -> s.overhead, s -> s.attack = 2));
		hrolf.put("kick", clock(s -> s.kick, s -> s.attack = 3));
		hrolf.put("warcry", clock(s -> s.warcry, s -> s.attack = 4));
		hrolf.put("rally", clock(s -> s.rally, s -> s.transition = true));
		hrolf.put("horn", (s, age, since) -> { s.attack = 5; s.hornMillis = (long) (since * 50); });
		boss(p, "hrolf", new HrolfModel(HrolfMesh.create().bakeRoot()), HrolfRenderState::new, hrolf,
			HrolfRenderer.class, "dev.elsewhere.boss.hrolf.HrolfBoss", StubBoss.class);

		Map<String, AnimProbe.Attack<GrimhornRenderState>> goat = new LinkedHashMap<>();
		goat.put("paw_windup", (s, age, since) -> s.goatState = GrimhornEntity.WINDUP);
		goat.put("charge", (s, age, since) -> s.goatState = GrimhornEntity.CHARGE);
		goat.put("stunned", (s, age, since) -> s.goatState = GrimhornEntity.STUNNED);
		boss(p, "grimhorn", new GrimhornModel(GrimhornMesh.create().bakeRoot()), GrimhornRenderState::new, goat,
			GrimhornRenderer.class, GrimhornEntity.class.getName(), GrimhornEntity.class);

		p.flinchMath("all", Flinch3::apply);
		killSwitch(p);
		System.exit(p.finish());
	}

	private static <S extends LivingEntityRenderState> void boss(AnimProbe p, String b, EntityModel<S> m, Supplier<S> f,
			Map<String, AnimProbe.Attack<S>> attacks, Class<?> renderer, String entity, Class<?> base) {
		Consumer<S> none = s -> { };
		p.idle(b, m, f, none);
		attacks.forEach((name, atk) -> p.attack(b, name, m, f, none, atk, 80, 0F));
		p.change(b, "death", AnimProbe.DEATH_MIN, m, f, none, s -> s.deathTime = 12F);
		p.flinchWiring(b, renderer, entity, base);
	}

	private static void killSwitch(AnimProbe p) {
		try {
			java.lang.reflect.Field en = Flinch3.class.getDeclaredField("enabled");
			en.setAccessible(true);
			Object was = en.get(null);
			en.set(null, null);
			boolean on = Flinch3.enabled();
			en.set(null, null);
			System.setProperty("elsewhere.bossAnimations3", "false");
			boolean off = !Flinch3.enabled();
			// with the switch off Grimhorn's 3.0 death fill-in stays out (vanilla tilt only)
			GrimhornModel m = new GrimhornModel(GrimhornMesh.create().bakeRoot());
			GrimhornRenderState a = new GrimhornRenderState();
			a.ageInTicks = 300;
			float[] pa = AnimProbe.pose(m, a);
			GrimhornRenderState d = new GrimhornRenderState();
			d.ageInTicks = 300;
			d.deathTime = 12F;
			float change = AnimProbe.diff(pa, AnimProbe.pose(m, d));
			System.clearProperty("elsewhere.bossAnimations3");
			en.set(null, was);
			p.check("all", "killswitch_bossAnimations3", on && off && change < AnimProbe.DEATH_MIN,
				"default on=" + on + ", off with the switch=" + off + ", Grimhorn death fill-in change with it off " + change);
		} catch (ReflectiveOperationException e) {
			p.check("all", "killswitch_bossAnimations3", false, e.toString());
		}
	}
}
