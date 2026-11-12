package dev.elsewhere.verdigris_ironpeak.anim3;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Vector3f;

/**
 * 3.0 (P8) headless model check for the boss animations (s-p8-bossanims). Not used in play: only
 * {@code BossAnimCheck.main} (run by {@code _tools/bossanimcheck.sh}) calls it. Each boss model is posed through its
 * real {@code setupAnim} and every part's pose is compared: idle must move over time (breathing), every attack must
 * move the body well past the idle pose at the same instant (the lunge or swing reads at range), the hurt flinch and the death must
 * visibly change the pose. Thresholds: rotation in radians, position in model pixels / 8, scale x 2.
 */
public final class AnimProbe {
	/** Smallest idle motion that still reads as breathing (about 1.1 degrees or 0.16 px). */
	public static final float IDLE_MIN = 0.02F;
	/** Smallest attack peak beyond the idle pose (about 8.6 degrees or 1.2 px). */
	public static final float ATTACK_MIN = 0.15F;
	public static final float HURT_MIN = 0.06F;
	public static final float DEATH_MIN = 0.15F;

	/** Sets up an attack on a fresh state at {@code age}, started {@code since} ticks ago. */
	public interface Attack<S> {
		void setup(S s, float age, float since);
	}

	private final String module;
	private int pass, total;

	public AnimProbe(String module) {
		this.module = module;
	}

	public void check(String boss, String check, boolean ok, String detail) {
		total++;
		if (ok) pass++;
		System.out.println("BOSSANIM " + module + " " + boss + " " + check + " " + (ok ? "PASS" : "FAIL") + " " + detail);
	}

	public int finish() {
		System.out.println("BOSSANIM " + module + " TOTAL " + pass + "/" + total + (pass == total ? " PASS" : " FAIL"));
		return pass == total ? 0 : 1;
	}

	// ---- pose snapshots ----

	public static float[] snap(EntityModel<?> m) {
		List<ModelPart> parts = m.allParts();
		float[] o = new float[parts.size() * 10];
		for (int i = 0; i < parts.size(); i++) {
			ModelPart p = parts.get(i);
			int k = i * 10;
			o[k] = p.x; o[k + 1] = p.y; o[k + 2] = p.z;
			o[k + 3] = p.xRot; o[k + 4] = p.yRot; o[k + 5] = p.zRot;
			o[k + 6] = p.xScale; o[k + 7] = p.yScale; o[k + 8] = p.zScale;
			o[k + 9] = p.visible ? 1 : 0;
		}
		return o;
	}

	private static float partDiff(float[] a, float[] b, int i) {
		int k = i * 10;
		float d = 0;
		for (int j = 0; j < 3; j++) d = Math.max(d, Math.abs(a[k + j] - b[k + j]) / 8F);
		for (int j = 3; j < 6; j++) d = Math.max(d, Math.abs(a[k + j] - b[k + j]));
		for (int j = 6; j < 9; j++) d = Math.max(d, Math.abs(a[k + j] - b[k + j]) * 2F);
		if (a[k + 9] != b[k + 9]) d = Math.max(d, 1F);
		return d;
	}

	public static float diff(float[] a, float[] b) {
		float d = 0;
		for (int i = 0; i < a.length / 10; i++) d = Math.max(d, partDiff(a, b, i));
		return d;
	}

	/** Names of the (up to 4) parts that moved most between two snapshots. */
	public static String moved(EntityModel<?> m, float[] a, float[] b) {
		Map<ModelPart, String> names = names(m.root());
		List<ModelPart> parts = m.allParts();
		List<int[]> idx = new ArrayList<>();
		for (int i = 0; i < parts.size(); i++) if (partDiff(a, b, i) > 0.01F) idx.add(new int[] {i});
		idx.sort((x, y) -> Float.compare(partDiff(a, b, y[0]), partDiff(a, b, x[0])));
		StringBuilder sb = new StringBuilder("[");
		for (int n = 0; n < Math.min(4, idx.size()); n++) {
			if (n > 0) sb.append(',');
			sb.append(names.getOrDefault(parts.get(idx.get(n)[0]), "?"));
		}
		return sb.append(idx.size() > 4 ? ",+" + (idx.size() - 4) : "").append(']').toString();
	}

	@SuppressWarnings("unchecked")
	private static Map<ModelPart, String> names(ModelPart root) {
		Map<ModelPart, String> out = new IdentityHashMap<>();
		out.put(root, "root");
		try {
			Field f = ModelPart.class.getDeclaredField("children");
			f.setAccessible(true);
			List<ModelPart> todo = new ArrayList<>(List.of(root));
			while (!todo.isEmpty()) {
				ModelPart p = todo.remove(todo.size() - 1);
				for (Map.Entry<String, ModelPart> e : ((Map<String, ModelPart>) f.get(p)).entrySet()) {
					out.put(e.getValue(), e.getKey());
					todo.add(e.getValue());
				}
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return out;
	}

	private static String f(float v) {
		return String.format(java.util.Locale.ROOT, "%.3f", v);
	}

	public static <S extends net.minecraft.client.renderer.entity.state.EntityRenderState> float[] pose(EntityModel<S> m, S s) {
		m.setupAnim(s);
		return snap(m);
	}

	// ---- the per-boss checks ----

	/** Idle: the pose keeps changing over 4 s with nothing else going on. */
	public <S extends LivingEntityRenderState> float idle(String boss, EntityModel<S> m, Supplier<S> fresh, Consumer<S> base) {
		float[] first = null;
		float amp = 0;
		float[] best = null;
		for (int age = 100; age <= 180; age += 3) {
			S s = fresh.get();
			s.ageInTicks = age;
			base.accept(s);
			float[] p = pose(m, s);
			if (first == null) first = p;
			else if (diff(first, p) > amp) {
				amp = diff(first, p);
				best = p;
			}
		}
		check(boss, "idle", amp >= IDLE_MIN, "amplitude " + f(amp) + " moving " + (best == null ? "[]" : moved(m, first, best)));
		return amp;
	}

	/** One attack: at its peak the pose is well away from the idle pose at the same moment. */
	public <S extends LivingEntityRenderState> void attack(String boss, String name, EntityModel<S> m, Supplier<S> fresh, Consumer<S> base,
			Attack<S> atk, int lengthTicks, float idleAmp) {
		float peak = 0;
		int at = 0;
		String parts = "[]";
		for (int since = 1; since <= lengthTicks; since++) {
			float age = 200 + since;
			S idle = fresh.get();
			idle.ageInTicks = age;
			base.accept(idle);
			float[] a = pose(m, idle);
			S s = fresh.get();
			s.ageInTicks = age;
			base.accept(s);
			atk.setup(s, age, since);
			float[] b = pose(m, s);
			float d = diff(a, b);
			if (d > peak) {
				peak = d;
				at = since;
				parts = moved(m, a, b);
			}
		}
		// measured against the idle pose at the same instant, so orbiting props (rods, shell fragments) cancel out
		check(boss, "attack:" + name, peak >= ATTACK_MIN, "peak " + f(peak) + " beyond idle at +" + at + "t moving " + parts);
	}

	/** Hurt or death: the pose with {@code setup} applied differs from the plain pose at the same moment. */
	public <S extends LivingEntityRenderState> void change(String boss, String check, float min, EntityModel<S> m, Supplier<S> fresh, Consumer<S> base,
			Consumer<S> setup) {
		S a = fresh.get();
		a.ageInTicks = 300;
		base.accept(a);
		float[] pa = pose(m, a);
		S b = fresh.get();
		b.ageInTicks = 300;
		base.accept(b);
		setup.accept(b);
		float[] pb = pose(m, b);
		float d = diff(pa, pb);
		check(boss, check, d >= min, "change " + f(d) + " moving " + moved(m, pa, pb));
	}

	// ---- the whole-body flinch (Flinch3 modules) ----

	/**
	 * The flinch math, applied as LivingEntityRenderer.setupRotations' TAIL does: at its peak the top of the boss moves
	 * back (+Z: entity models face -Z in that space, as the riptide spin shows), it settles to rest by 10 ticks, and
	 * a clock of -1 does nothing.
	 */
	public void flinchMath(String label, Consumer2 apply) {
		Vector3f top = new Vector3f();
		float peakZ = 0, peakAt = 0;
		for (float c = 0; c <= 3; c += 0.25F) {
			PoseStack ps = new PoseStack();
			apply.apply(ps, c);
			ps.last().pose().transformPosition(0, 2, 0, top);
			if (top.z > peakZ) {
				peakZ = top.z;
				peakAt = c;
			}
		}
		check(label, "flinch_back", peakZ >= 0.15F, "top of a 2-block boss moves back " + f(peakZ) + " blocks at " + f(peakAt) + "t");
		PoseStack end = new PoseStack();
		apply.apply(end, 10F);
		end.last().pose().transformPosition(0, 2, 0, top);
		check(label, "flinch_settles", Math.abs(top.z) < 0.01F && Math.abs(top.y - 2) < 0.01F, "at 10t top = (" + f(top.x) + "," + f(top.y) + "," + f(top.z) + ")");
		PoseStack off = new PoseStack();
		apply.apply(off, -1F);
		off.last().pose().transformPosition(0, 2, 0, top);
		check(label, "flinch_idle_noop", top.z == 0 && top.y == 2, "clock -1 leaves the pose alone");
	}

	public interface Consumer2 {
		void apply(PoseStack ps, float clock);
	}

	/**
	 * The flinch reaches this boss: its renderer is a LivingEntityRenderer (the mixin target) whose setupRotations, if
	 * overridden anywhere below LivingEntityRenderer, still calls super; and its entity class is one the module's flinch
	 * accepts ({@code base}).
	 */
	public void flinchWiring(String boss, Class<?> renderer, String entityClass, Class<?> base) {
		boolean living = LivingEntityRenderer.class.isAssignableFrom(renderer);
		String override = null;
		for (Class<?> c = renderer; c != null && c != LivingEntityRenderer.class; c = c.getSuperclass()) {
			for (Method mm : c.getDeclaredMethods()) {
				if (mm.getName().equals("setupRotations") && !mm.isBridge() && !callsSuper(c, "setupRotations")) override = c.getSimpleName();
			}
		}
		boolean entity;
		try {
			entity = base.isAssignableFrom(Class.forName(entityClass, false, AnimProbe.class.getClassLoader()));
		} catch (ClassNotFoundException e) {
			entity = false;
		}
		check(boss, "flinch_wiring", living && override == null && entity,
			"renderer " + renderer.getSimpleName() + (living ? " is" : " is NOT") + " a LivingEntityRenderer"
				+ (override == null ? "" : ", " + override + ".setupRotations skips super") + ", entity " + (entity ? "accepted" : "NOT accepted"));
	}

	/** True if every setupRotations body in {@code c} has an INVOKESPECIAL to a super setupRotations (ASM scan). */
	private static boolean callsSuper(Class<?> c, String name) {
		try (java.io.InputStream in = c.getClassLoader().getResourceAsStream(c.getName().replace('.', '/') + ".class")) {
			if (in == null) return false;
			boolean[] ok = {true};
			new org.objectweb.asm.ClassReader(in).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
				@Override
				public org.objectweb.asm.MethodVisitor visitMethod(int acc, String n, String desc, String sig, String[] ex) {
					if (!n.equals(name) || (acc & org.objectweb.asm.Opcodes.ACC_BRIDGE) != 0) return null;
					boolean[] found = {false};
					return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
						@Override
						public void visitMethodInsn(int op, String owner, String mn, String md, boolean itf) {
							if (op == org.objectweb.asm.Opcodes.INVOKESPECIAL && mn.equals(name)) found[0] = true;
						}

						@Override
						public void visitEnd() {
							if (!found[0]) ok[0] = false;
						}
					};
				}
			}, 0);
			return ok[0];
		} catch (java.io.IOException e) {
			return false;
		}
	}
}
