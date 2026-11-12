package dev.elsewhere.nether_end_bosses.anim3;

import dev.elsewhere.boss.grunthar.Ashtusk;
import dev.elsewhere.boss.solaxis.Solaxis;
import dev.elsewhere.bossart.aphelion.AphelionArtModel;
import dev.elsewhere.bossart.grunthar.AshtuskArtModel;
import dev.elsewhere.bossart.grunthar.AshtuskModelLayer;
import dev.elsewhere.bossart.grunthar.GruntharArtModel;
import dev.elsewhere.bossart.grunthar.GruntharModelLayer;
import dev.elsewhere.bossart.mournmaw.MournmawArtModel;
import dev.elsewhere.bossart.mournmaw.MournmawArtModelLayer;
import dev.elsewhere.bossart.nether.HurtAnim;
import dev.elsewhere.bossart.ossyra.OssyraArtModel;
import dev.elsewhere.bossart.ossyra.OssyraArtRenderState;
import dev.elsewhere.bossart.solaxis.SolaxisArtModel;
import dev.elsewhere.bossart.solaxis.SolaxisArtModelLayer;
import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.model.EntityModel;

/**
 * 3.0 (P8) headless animation model check for the six nether_end bosses (s-p8-bossanims). Run with
 * {@code _tools/bossanimcheck.sh nether_end_bosses}; never loaded in play. Per boss: idle breathing, every attack
 * the boss can roll (each must move the body well past its idle pose), the hurt flinch and the death. Plus the
 * {@code bossAnimations3} kill switch (off = no flinch and none of the 3.0 attack fill-ins).
 */
public final class BossAnimCheck {
	private BossAnimCheck() {}

	public static void main(String[] args) {
		AnimProbe p = new AnimProbe("nether_end_bosses");
		solaxis(p);
		grunthar(p);
		ashtusk(p);
		mournmaw(p);
		ossyra(p);
		aphelion(p);
		killSwitch(p);
		System.exit(p.finish());
	}

	/** Attack driven by the synced attack id plus the attackAnim clock (BossRenderer copies both). */
	private static <S extends BossRenderState> AnimProbe.Attack<S> byId(String id) {
		return (s, age, since) -> {
			s.attack = id;
			s.attackAge = since;
			s.attackAnim.start((int) (age - since));
		};
	}

	private static <S extends HurtAnim.State> void common(AnimProbe p, String boss, EntityModel<S> m, Supplier<S> fresh, Consumer<S> base, float idleAmp) {
		p.change(boss, "hurt", AnimProbe.HURT_MIN, m, fresh, base, s -> s.hurtAge = 2F);
		p.change(boss, "death", AnimProbe.DEATH_MIN, m, fresh, base, s -> s.deathTime = 12F);
	}

	private static void solaxis(AnimProbe p) {
		String b = "solaxis";
		SolaxisArtModel m = new SolaxisArtModel(SolaxisArtModelLayer.create().bakeRoot());
		Supplier<SolaxisArtModel.State> f = SolaxisArtModel.State::new;
		Consumer<SolaxisArtModel.State> base = s -> { };
		float idle = p.idle(b, m, f, base);
		p.attack(b, "rod_volley", m, f, base, byId("rod_volley"), 34, idle);
		p.attack(b, "eclipse_orb", m, f, base, byId("eclipse_orb"), 32, idle);
		p.attack(b, "solar_flare", m, f, base, byId("solar_flare"), 32, idle);
		p.attack(b, "supernova", m, f, base, (s, age, since) -> { s.attack = "supernova"; s.corePulse = true; s.flags |= Solaxis.FLAG_CORE_PULSE; }, 20, idle);
		common(p, b, m, f, base, idle);
	}

	private static void grunthar(AnimProbe p) {
		String b = "grunthar";
		GruntharArtModel m = new GruntharArtModel(GruntharModelLayer.create().bakeRoot());
		Supplier<GruntharArtModel.State> f = GruntharArtModel.State::new;
		Consumer<GruntharArtModel.State> base = s -> { };
		float idle = p.idle(b, m, f, base);
		p.attack(b, "axe_cleave", m, f, base, byId("axe_cleave"), 24, idle);
		p.attack(b, "gold_bombs", m, f, base, byId("gold_bombs"), 20, idle);
		p.attack(b, "call_enforcers", m, f, base, byId("call_enforcers"), 48, idle);
		p.attack(b, "molten_hoard", m, f, base, byId("molten_hoard"), 46, idle);
		p.attack(b, "greed", m, f, base, (s, age, since) -> s.flags |= dev.elsewhere.boss.grunthar.Grunthar.FLAG_GREED, 20, idle);
		common(p, b, m, f, base, idle);
	}

	private static void ashtusk(AnimProbe p) {
		String b = "ashtusk";
		AshtuskArtModel m = new AshtuskArtModel(AshtuskModelLayer.create().bakeRoot());
		Supplier<BossRenderState> f = HurtAnim.State::new;
		Consumer<BossRenderState> base = s -> { };
		float idle = p.idle(b, m, f, base);
		p.attack(b, "charge", m, f, base, (s, age, since) -> { s.attack = "charge"; s.flags |= since < 24 ? Ashtusk.FLAG_PAWING : Ashtusk.FLAG_CHARGING; }, 40, idle);
		p.attack(b, "tusk_toss", m, f, base, byId("tusk_toss"), 24, idle);
		p.change(b, "hurt", AnimProbe.HURT_MIN, m, f, base, s -> ((HurtAnim.State) s).hurtAge = 2F);
		p.change(b, "death", AnimProbe.DEATH_MIN, m, f, base, s -> s.deathTime = 12F);
	}

	private static void mournmaw(AnimProbe p) {
		String b = "mournmaw";
		MournmawArtModel m = new MournmawArtModel(MournmawArtModelLayer.create().bakeRoot());
		Supplier<BossRenderState> f = HurtAnim.State::new;
		Consumer<BossRenderState> base = s -> { };
		float idle = p.idle(b, m, f, base);
		for (String id : new String[] {"wail", "tear_rain", "soul_fireball", "chain_whip", "mourners", "grief_storm"}) {
			p.attack(b, id, m, f, base, byId(id), 52, idle);
		}
		p.change(b, "hurt", AnimProbe.HURT_MIN, m, f, base, s -> ((HurtAnim.State) s).hurtAge = 2F);
		p.change(b, "death", AnimProbe.DEATH_MIN, m, f, base, s -> s.deathTime = 12F);
	}

	private static void ossyra(AnimProbe p) {
		String b = "ossyra";
		OssyraArtModel m = new OssyraArtModel(OssyraArtModel.createLayer().bakeRoot());
		Supplier<OssyraArtRenderState> f = OssyraArtRenderState::new;
		Consumer<OssyraArtRenderState> base = s -> { };
		float idle = p.idle(b, m, f, base);
		for (String id : new String[] {"bullet_storm", "shell_slam", "gravity_flip", "shulklings", "void_bloom", "surface_swap"}) {
			p.attack(b, id, m, f, base, byId(id), 60, idle);
		}
		common(p, b, m, f, base, idle);
	}

	private static void aphelion(AnimProbe p) {
		String b = "aphelion";
		AphelionArtModel m = new AphelionArtModel(AphelionArtModel.createLayer().bakeRoot());
		Supplier<AphelionArtModel.State> f = AphelionArtModel.State::new;
		Consumer<AphelionArtModel.State> base = s -> { };
		float idle = p.idle(b, m, f, base);
		for (String id : new String[] {"coil", "void_breath", "bite", "devour_dive", "voidborn", "collapse", "crystal_feast"}) {
			p.attack(b, id, m, f, base, byId(id), 60, idle);
		}
		common(p, b, m, f, base, idle);
	}

	/** bossAnimations3 off: the hurt clock is never set and the 3.0 attack fill-ins stay still. */
	private static void killSwitch(AnimProbe p) {
		try {
			java.lang.reflect.Field en = HurtAnim.class.getDeclaredField("enabled");
			en.setAccessible(true);
			Object was = en.get(null);
			en.set(null, null);
			System.setProperty("elsewhere.bossAnimations3", "false");
			boolean off = !HurtAnim.enabled();
			MournmawArtModel m = new MournmawArtModel(MournmawArtModelLayer.create().bakeRoot());
			BossRenderState idle = new HurtAnim.State();
			idle.ageInTicks = 230;
			float[] a = AnimProbe.pose(m, idle);
			BossRenderState rain = new HurtAnim.State();
			rain.ageInTicks = 230;
			rain.attack = "tear_rain";
			rain.attackAnim.start(200);
			float d = AnimProbe.diff(a, AnimProbe.pose(m, rain));
			System.clearProperty("elsewhere.bossAnimations3");
			en.set(null, was);
			p.check("all", "killswitch_bossAnimations3", off && d < 0.001F, "enabled()=" + !off + " with the switch off; tear_rain change " + d);
		} catch (ReflectiveOperationException e) {
			p.check("all", "killswitch_bossAnimations3", false, e.toString());
		}
	}
}
