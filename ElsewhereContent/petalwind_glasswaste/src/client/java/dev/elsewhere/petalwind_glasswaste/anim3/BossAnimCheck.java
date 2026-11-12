package dev.elsewhere.petalwind_glasswaste.anim3;

import dev.elsewhere.boss.kazehana.Kazehana;
import dev.elsewhere.bossart.ankhara.AnkharaModel;
import dev.elsewhere.bossart.ankhara.AnkharaRenderState;
import dev.elsewhere.bossart.ankhara.AnkharaRenderer;
import dev.elsewhere.bossart.heliodor.HeliodorModel;
import dev.elsewhere.bossart.heliodor.HeliodorRenderState;
import dev.elsewhere.bossart.heliodor.HeliodorRenderer;
import dev.elsewhere.bossart.kazehana.KazehanaGeometry;
import dev.elsewhere.bossart.kazehana.KazehanaModel;
import dev.elsewhere.bossart.kazehana.KazehanaRenderState;
import dev.elsewhere.bossart.kazehana.KazehanaRenderer;
import dev.elsewhere.bossart.tempestra.TempestraGeometry;
import dev.elsewhere.bossart.tempestra.TempestraModel;
import dev.elsewhere.bossart.tempestra.TempestraRenderState;
import dev.elsewhere.bossart.tempestra.TempestraRenderer;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * 3.0 (P8) headless animation model check for the four petalwind_glasswaste bosses (s-p8-bossanims). Run with
 * {@code _tools/bossanimcheck.sh petalwind_glasswaste}; never loaded in play. Per boss: idle breathing, every attack
 * clock the renderer drives must move the body well past the idle pose, the death pose, and the hurt flinch wiring;
 * plus the flinch math and the {@code bossAnimations3} kill switch.
 */
public final class BossAnimCheck {
	private BossAnimCheck() {}

	public static void main(String[] args) {
		AnimProbe p = new AnimProbe("petalwind_glasswaste");

		Map<String, AnimProbe.Attack<KazehanaRenderState>> kaze = new LinkedHashMap<>();
		kaze.put("iaido", (s, age, since) -> { s.duelState = Kazehana.S_IAIDO; s.iaido.start((int) (age - since)); });
		kaze.put("dash", (s, age, since) -> { s.duelState = Kazehana.S_DASH; s.dash.start((int) (age - since)); });
		kaze.put("combo", (s, age, since) -> { s.duelState = Kazehana.S_COMBO; s.combo.start((int) (age - since)); });
		kaze.put("parry", (s, age, since) -> { s.duelState = Kazehana.S_PARRY; s.parry.start((int) (age - since)); });
		kaze.put("gale", (s, age, since) -> { s.duelState = Kazehana.S_GALE; s.gale.start((int) (age - since)); });
		kaze.put("stunned", (s, age, since) -> { s.duelState = Kazehana.S_STUNNED; s.stun.start((int) (age - since)); });
		kaze.put("phase_roar", (s, age, since) -> s.phase.start((int) (age - since)));
		kaze.put("bow", (s, age, since) -> s.duelState = Kazehana.S_BOW);
		boss(p, "kazehana", new KazehanaModel(KazehanaGeometry.createBodyLayer().bakeRoot(), false), KazehanaRenderState::new, kaze,
			KazehanaRenderer.class, "dev.elsewhere.boss.kazehana.Kazehana");

		Map<String, AnimProbe.Attack<TempestraRenderState>> temp = new LinkedHashMap<>();
		temp.put("volley", (s, age, since) -> s.volley.start((int) (age - since)));
		temp.put("downburst", (s, age, since) -> s.downburst.start((int) (age - since)));
		temp.put("bolt", (s, age, since) -> s.bolt.start((int) (age - since)));
		temp.put("strike", (s, age, since) -> s.strike.start((int) (age - since)));
		temp.put("phase_roar", (s, age, since) -> s.phaseRoar.start((int) (age - since)));
		boss(p, "tempestra", new TempestraModel(TempestraGeometry.createBodyLayer().bakeRoot()), TempestraRenderState::new, temp,
			TempestraRenderer.class, "dev.elsewhere.boss.tempestra.Tempestra");

		Map<String, AnimProbe.Attack<HeliodorRenderState>> helio = new LinkedHashMap<>();
		helio.put("lance", (s, age, since) -> s.lance.start((int) (age - since)));
		helio.put("slam", (s, age, since) -> s.slam.start((int) (age - since)));
		helio.put("crack", (s, age, since) -> s.crack.start((int) (age - since)));
		helio.put("vent", (s, age, since) -> s.vent.start((int) (age - since)));
		helio.put("sweep", (s, age, since) -> s.sweep.start((int) (age - since)));
		helio.put("phase_roar", (s, age, since) -> s.roarTime = since / 20F);
		boss(p, "heliodor", new HeliodorModel(HeliodorModel.createLayer().bakeRoot()), HeliodorRenderState::new, helio,
			HeliodorRenderer.class, "dev.elsewhere.boss.heliodor.Heliodor");

		Map<String, AnimProbe.Attack<AnkharaRenderState>> ankh = new LinkedHashMap<>();
		ankh.put("erupt", (s, age, since) -> s.erupt.start((int) (age - since)));
		ankh.put("lunge_windup", (s, age, since) -> s.windup.start((int) (age - since)));
		ankh.put("lunge", (s, age, since) -> s.lunge.start((int) (age - since)));
		ankh.put("stun", (s, age, since) -> s.stun.start((int) (age - since)));
		ankh.put("phase_roar", (s, age, since) -> s.roarTime = since / 20F);
		boss(p, "ankhara", new AnkharaModel(AnkharaModel.createLayer().bakeRoot()), AnkharaRenderState::new, ankh,
			AnkharaRenderer.class, "dev.elsewhere.boss.ankhara.Ankhara");

		p.flinchMath("all", Flinch3::apply);
		killSwitch(p);
		System.exit(p.finish());
	}

	private static <S extends LivingEntityRenderState> void boss(AnimProbe p, String b, EntityModel<S> m, Supplier<S> f,
			Map<String, AnimProbe.Attack<S>> attacks, Class<?> renderer, String entity) {
		Consumer<S> base = s -> { };
		p.idle(b, m, f, base);
		attacks.forEach((name, atk) -> p.attack(b, name, m, f, base, atk, 70, 0F));
		p.change(b, "death", AnimProbe.DEATH_MIN, m, f, base, s -> s.deathTime = 12F);
		p.flinchWiring(b, renderer, entity, BossBase.class);
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
			System.clearProperty("elsewhere.bossAnimations3");
			en.set(null, was);
			p.check("all", "killswitch_bossAnimations3", on && off, "default on=" + on + ", off with the switch=" + off);
		} catch (ReflectiveOperationException e) {
			p.check("all", "killswitch_bossAnimations3", false, e.toString());
		}
	}
}
