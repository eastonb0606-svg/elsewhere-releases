package dev.elsewhere.core.anim3;

import dev.elsewhere.bossart.mother_creak.MotherCreakArtModel;
import dev.elsewhere.bossart.mother_creak.MotherCreakArtRenderer;
import dev.elsewhere.bossart.mother_creak.MotherCreakArtState;
import dev.elsewhere.bossart.selenne.SelenneArtModel;
import dev.elsewhere.bossart.selenne.SelenneArtRenderer;
import dev.elsewhere.bossart.selenne.SelenneArtState;
import dev.elsewhere.core.boss.ElsewhereBoss;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * 3.0 (P8) headless animation model check for the two core bosses, Selenne and Mother Creak (s-p8-bossanims). Run
 * with {@code _tools/bossanimcheck.sh core}; never loaded in play. Per boss: idle breathing (awake and, for Mother
 * Creak, dormant), every attack clock the renderer drives must move the body well past the idle pose, the death pose,
 * and the hurt flinch wiring; plus the flinch math and the {@code bossAnimations3} kill switch.
 */
public final class BossAnimCheck {
	private BossAnimCheck() {}

	public static void main(String[] args) {
		AnimProbe p = new AnimProbe("core");

		Map<String, AnimProbe.Attack<SelenneArtState>> sel = new LinkedHashMap<>();
		sel.put("open", (s, age, since) -> s.open.start((int) (age - since)));
		sel.put("close", (s, age, since) -> s.close.start((int) (age - since)));
		sel.put("petals", (s, age, since) -> s.petals.start((int) (age - since)));
		sel.put("tear", (s, age, since) -> s.tear.start((int) (age - since)));
		sel.put("beam", (s, age, since) -> s.beam.start((int) (age - since)));
		sel.put("full_moon", (s, age, since) -> s.fullMoon = true);
		boss(p, "selenne", new SelenneArtModel(SelenneArtModel.createBodyLayer().bakeRoot()), SelenneArtState::new, s -> s.awake = true, sel,
			SelenneArtRenderer.class, "dev.elsewhere.boss.selenne.Selenne");

		Map<String, AnimProbe.Attack<MotherCreakArtState>> mc = new LinkedHashMap<>();
		mc.put("twitch", (s, age, since) -> s.twitch.start((int) (age - since)));
		mc.put("claw", (s, age, since) -> s.claw.start((int) (age - since)));
		mc.put("burst", (s, age, since) -> s.burst.start((int) (age - since)));
		mc.put("stun", (s, age, since) -> s.stun.start((int) (age - since)));
		mc.put("roar", (s, age, since) -> s.roar.start((int) (age - since)));
		boss(p, "mother_creak", new MotherCreakArtModel(MotherCreakArtModel.createBodyLayer().bakeRoot()), MotherCreakArtState::new, s -> s.awake = true, mc,
			MotherCreakArtRenderer.class, "dev.elsewhere.boss.mother_creak.MotherCreak");
		MotherCreakArtModel dm = new MotherCreakArtModel(MotherCreakArtModel.createBodyLayer().bakeRoot());
		p.idle("mother_creak_dormant", dm, MotherCreakArtState::new, s -> s.awake = false);

		p.flinchMath("all", Flinch3::apply);
		killSwitch(p);
		System.exit(p.finish());
	}

	private static <S extends LivingEntityRenderState> void boss(AnimProbe p, String b, EntityModel<S> m, Supplier<S> f, Consumer<S> base,
			Map<String, AnimProbe.Attack<S>> attacks, Class<?> renderer, String entity) {
		p.idle(b, m, f, base);
		attacks.forEach((name, atk) -> p.attack(b, name, m, f, base, atk, 80, 0F));
		p.change(b, "death", AnimProbe.DEATH_MIN, m, f, base, s -> s.deathTime = 12F);
		p.flinchWiring(b, renderer, entity, ElsewhereBoss.class);
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
