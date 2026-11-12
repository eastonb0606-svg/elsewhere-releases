package dev.elsewhere.greenhaze_hush.anim3;

import dev.elsewhere.bossart.auris.AurisModel;
import dev.elsewhere.bossart.auris.AurisRenderState;
import dev.elsewhere.bossart.auris.AurisRenderer;
import dev.elsewhere.bossart.grumblegut.GrumblegutArtModel;
import dev.elsewhere.bossart.grumblegut.GrumblegutArtRenderer;
import dev.elsewhere.bossart.grumblegut.GrumblegutArtState;
import dev.elsewhere.bossart.lithara.LitharaModel;
import dev.elsewhere.bossart.lithara.LitharaRenderState;
import dev.elsewhere.bossart.lithara.LitharaRenderer;
import dev.elsewhere.bossart.percolax.PercolaxArtModel;
import dev.elsewhere.bossart.percolax.PercolaxArtRenderer;
import dev.elsewhere.bossart.percolax.PercolaxArtState;
import dev.elsewhere.stub.greenhaze_hush.BossEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/**
 * 3.0 (P8) headless animation model check for the four greenhaze_hush bosses (s-p8-bossanims). Run with
 * {@code _tools/bossanimcheck.sh greenhaze_hush}; never loaded in play. Per boss: idle breathing, every attack and
 * set-piece clock the renderer drives must move the body well past the idle pose, the death pose, and the hurt flinch
 * wiring; plus the flinch math and the {@code bossAnimations3} kill switch.
 */
public final class BossAnimCheck {
	private BossAnimCheck() {}

	public static void main(String[] args) {
		AnimProbe p = new AnimProbe("greenhaze_hush");

		Map<String, Function<AurisRenderState, AnimationState>> auris = new LinkedHashMap<>();
		auris.put("listen", s -> s.listenState);
		auris.put("charge", s -> s.chargeState);
		auris.put("smash", s -> s.smashState);
		auris.put("deafened", s -> s.deafState);
		auris.put("roar", s -> s.roarState);
		boss(p, "auris", new AurisModel(AurisModel.createLayer().bakeRoot()), AurisRenderState::new, s -> s.idleState.start(0), auris,
			AurisRenderer.class, "dev.elsewhere.boss.auris.Auris");

		Map<String, Function<LitharaRenderState, AnimationState>> lithara = new LinkedHashMap<>();
		lithara.put("sing", s -> s.singState);
		lithara.put("conduct", s -> s.conductState);
		lithara.put("shattered", s -> s.shatteredState);
		lithara.put("psalm", s -> s.psalmState);
		lithara.put("rain", s -> s.rainState);
		lithara.put("shards", s -> s.shardsState);
		lithara.put("vex", s -> s.vexState);
		lithara.put("roar", s -> s.roarState);
		boss(p, "lithara", new LitharaModel(LitharaModel.createLayer().bakeRoot()), LitharaRenderState::new, s -> s.idleState.start(0), lithara,
			LitharaRenderer.class, "dev.elsewhere.boss.lithara.Lithara");

		Map<String, Function<PercolaxArtState, AnimationState>> percolax = new LinkedHashMap<>();
		percolax.put("big_rip", s -> s.bigRip);
		percolax.put("exhale", s -> s.exhale);
		percolax.put("stomp", s -> s.stomp);
		percolax.put("flick", s -> s.flick);
		percolax.put("cough", s -> s.cough);
		percolax.put("close", s -> s.close);
		percolax.put("roar", s -> s.roar);
		boss(p, "percolax", new PercolaxArtModel(PercolaxArtModel.createBodyLayer().bakeRoot()), PercolaxArtState::new, s -> s.idle.start(0), percolax,
			PercolaxArtRenderer.class, "dev.elsewhere.boss.percolax.Percolax");

		Map<String, Function<GrumblegutArtState, AnimationState>> grumblegut = new LinkedHashMap<>();
		grumblegut.put("paw", s -> s.paw);
		grumblegut.put("charge", s -> s.charge);
		grumblegut.put("munch", s -> s.munch);
		grumblegut.put("sniff", s -> s.sniff);
		grumblegut.put("chew", s -> s.chew);
		grumblegut.put("slam", s -> s.slam);
		grumblegut.put("yawn", s -> s.yawn);
		grumblegut.put("nap", s -> s.nap);
		grumblegut.put("puff", s -> s.puff);
		grumblegut.put("roar", s -> s.roar);
		boss(p, "grumblegut", new GrumblegutArtModel(GrumblegutArtModel.createBodyLayer().bakeRoot()), GrumblegutArtState::new, s -> s.idle.start(0), grumblegut,
			GrumblegutArtRenderer.class, "dev.elsewhere.boss.grumblegut.Grumblegut");

		p.flinchMath("all", Flinch3::apply);
		killSwitch(p);
		System.exit(p.finish());
	}

	private static <S extends LivingEntityRenderState> void boss(AnimProbe p, String b, EntityModel<S> m, Supplier<S> f, Consumer<S> base,
			Map<String, Function<S, AnimationState>> attacks, Class<?> renderer, String entity) {
		p.idle(b, m, f, base);
		attacks.forEach((name, clock) -> p.attack(b, name, m, f, base, (s, age, since) -> clock.apply(s).start((int) (age - since)), 70, 0F));
		p.change(b, "death", AnimProbe.DEATH_MIN, m, f, base, s -> s.deathTime = 12F);
		p.flinchWiring(b, renderer, entity, BossEntity.class);
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
