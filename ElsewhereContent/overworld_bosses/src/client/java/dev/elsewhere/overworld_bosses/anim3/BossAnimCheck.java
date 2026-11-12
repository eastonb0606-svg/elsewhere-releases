package dev.elsewhere.overworld_bosses.anim3;

import dev.elsewhere.boss.gulpmother.GulpmotherEntity;
import dev.elsewhere.boss.neferkhet.NeferkhetEntity;
import dev.elsewhere.boss.rimeback.RimebackEntity;
import dev.elsewhere.boss.strawgrim.StrawgrimEntity;
import dev.elsewhere.boss.vorlagh.VorlaghEntity;
import dev.elsewhere.bossart.gulpmother.GulpmotherArtMesh;
import dev.elsewhere.bossart.gulpmother.GulpmotherArtModel;
import dev.elsewhere.bossart.gulpmother.GulpmotherArtRenderer;
import dev.elsewhere.bossart.neferkhet.NeferkhetArtMesh;
import dev.elsewhere.bossart.neferkhet.NeferkhetArtModel;
import dev.elsewhere.bossart.neferkhet.NeferkhetArtRenderer;
import dev.elsewhere.bossart.overworld_a.OwaRenderState;
import dev.elsewhere.bossart.rimeback.RimebackModel;
import dev.elsewhere.bossart.rimeback.RimebackRenderState;
import dev.elsewhere.bossart.rimeback.RimebackRenderer;
import dev.elsewhere.bossart.strawgrim.StrawgrimArtMesh;
import dev.elsewhere.bossart.strawgrim.StrawgrimArtModel;
import dev.elsewhere.bossart.strawgrim.StrawgrimArtRenderer;
import dev.elsewhere.bossart.vorlagh.VorlaghModel;
import dev.elsewhere.bossart.vorlagh.VorlaghRenderState;
import dev.elsewhere.bossart.vorlagh.VorlaghRenderer;
import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.model.EntityModel;

/**
 * 3.0 (P8) headless animation model check for the five overworld bosses (s-p8-bossanims). Run with
 * {@code _tools/bossanimcheck.sh overworld_bosses}; never loaded in play. Per boss: idle breathing, every synced
 * action (wind-up and, where the model has one, the strike follow-through) must move the body well past the idle pose,
 * the death pose, and the hurt flinch wiring (renderer and entity reach {@link Flinch3}); plus the flinch math and the
 * {@code bossAnimations3} kill switch.
 */
public final class BossAnimCheck {
	private BossAnimCheck() {}

	public static void main(String[] args) {
		AnimProbe p = new AnimProbe("overworld_bosses");
		owa(p, "gulpmother", new GulpmotherArtModel(GulpmotherArtMesh.create().bakeRoot()), GulpmotherArtModel.State::new,
			new String[] {"tongue", "flop", "swallow", "spit", "eat", "emerge"},
			new int[] {GulpmotherEntity.ACT_TONGUE, GulpmotherEntity.ACT_FLOP, GulpmotherEntity.ACT_SWALLOW, GulpmotherEntity.ACT_SPIT, GulpmotherEntity.ACT_EAT, GulpmotherEntity.ACT_EMERGE},
			GulpmotherArtRenderer.class, GulpmotherEntity.class.getName());
		owa(p, "strawgrim", new StrawgrimArtModel(StrawgrimArtMesh.create().bakeRoot()), StrawgrimArtModel.State::new,
			new String[] {"reap", "furrow", "crows", "volley", "hop", "ignite"},
			new int[] {StrawgrimEntity.ACT_REAP, StrawgrimEntity.ACT_FURROW, StrawgrimEntity.ACT_CROWS, StrawgrimEntity.ACT_VOLLEY, StrawgrimEntity.ACT_HOP, StrawgrimEntity.ACT_IGNITE},
			StrawgrimArtRenderer.class, StrawgrimEntity.class.getName());
		owa(p, "neferkhet", new NeferkhetArtModel(NeferkhetArtMesh.create().bakeRoot()), NeferkhetArtModel.State::new,
			new String[] {"lance", "sink", "curse", "swing", "eclipse", "sarcophagus"},
			new int[] {NeferkhetEntity.ACT_LANCE, NeferkhetEntity.ACT_SINK, NeferkhetEntity.ACT_CURSE, NeferkhetEntity.ACT_SWING, NeferkhetEntity.ACT_ECLIPSE, NeferkhetEntity.ACT_SARCO},
			NeferkhetArtRenderer.class, NeferkhetEntity.class.getName());
		rimeback(p);
		vorlagh(p);
		p.flinchMath("all", Flinch3::apply);
		killSwitch(p);
		System.exit(p.finish());
	}

	private static <S extends OwaRenderState> void owa(AnimProbe p, String b, EntityModel<S> m, Supplier<S> f, String[] names, int[] acts,
			Class<?> renderer, String entity) {
		Consumer<S> base = s -> s.idle.start(0);
		p.idle(b, m, f, base);
		for (int i = 0; i < acts.length; i++) {
			int act = acts[i];
			p.attack(b, names[i], m, f, base, (s, age, since) -> {
				s.action = act;
				s.actions[act].start((int) (age - since));
			}, 70, 0F);
		}
		p.change(b, "death", AnimProbe.DEATH_MIN, m, f, base, s -> s.deathTicks = 12F);
		p.flinchWiring(b, renderer, entity, ElsewhereBoss.class);
	}

	private static void rimeback(AnimProbe p) {
		String b = "rimeback";
		RimebackModel m = new RimebackModel(RimebackModel.createLayer().bakeRoot());
		Supplier<RimebackRenderState> f = RimebackRenderState::new;
		Consumer<RimebackRenderState> base = s -> { };
		p.idle(b, m, f, base);
		String[] names = {"hurl", "throw", "slam", "breath", "roar", "stomp"};
		int[] acts = {RimebackEntity.ACT_HURL, RimebackEntity.ACT_THROW, RimebackEntity.ACT_SLAM, RimebackEntity.ACT_BREATH, RimebackEntity.ACT_ROAR, RimebackEntity.ACT_STOMP};
		for (int i = 0; i < acts.length; i++) {
			int act = acts[i];
			p.attack(b, names[i], m, f, base, (s, age, since) -> { s.action = act; s.actionMillis = (long) (since * 50); }, 70, 0F);
		}
		for (int act : new int[] {RimebackEntity.ACT_HURL, RimebackEntity.ACT_SLAM, RimebackEntity.ACT_STOMP}) {
			p.attack(b, names[act - 1] + "_strike", m, f, base, (s, age, since) -> { s.follow = act; s.followMillis = (long) (since * 50); }, 40, 0F);
		}
		p.change(b, "death", AnimProbe.DEATH_MIN, m, f, base, s -> s.deathTime = 12F);
		p.flinchWiring(b, RimebackRenderer.class, RimebackEntity.class.getName(), ElsewhereBoss.class);
	}

	private static void vorlagh(AnimProbe p) {
		String b = "vorlagh";
		VorlaghModel m = new VorlaghModel(VorlaghModel.createLayer().bakeRoot());
		Supplier<VorlaghRenderState> f = VorlaghRenderState::new;
		Consumer<VorlaghRenderState> base = s -> { };
		p.idle(b, m, f, base);
		p.attack(b, "ink", m, f, base, (s, age, since) -> { s.action = VorlaghEntity.ACT_INK; s.actionMillis = (long) (since * 50); }, 70, 0F);
		p.attack(b, "ink_jet", m, f, base, (s, age, since) -> { s.follow = VorlaghEntity.ACT_INK; s.followMillis = (long) (since * 50); }, 40, 0F);
		p.attack(b, "whirl", m, f, base, (s, age, since) -> { s.action = VorlaghEntity.ACT_ROAR; s.actionMillis = (long) (since * 50); }, 70, 0F);
		p.attack(b, "bite", m, f, base, (s, age, since) -> { s.action = VorlaghEntity.ACT_BITE; s.actionMillis = (long) (since * 50); }, 70, 0F);
		p.change(b, "death", AnimProbe.DEATH_MIN, m, f, base, s -> s.deathTime = 12F);
		p.flinchWiring(b, VorlaghRenderer.class, VorlaghEntity.class.getName(), ElsewhereBoss.class);
	}

	/** bossAnimations3 off: Flinch3.enabled() is false, so clock() never starts a flinch. */
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
