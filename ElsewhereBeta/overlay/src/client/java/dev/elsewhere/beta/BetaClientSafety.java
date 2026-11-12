package dev.elsewhere.beta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Beta safety net: any Elsewhere entity type left without a renderer gets an invisible one instead of crashing clients. */
public final class BetaClientSafety {
	private static final Logger LOG = LoggerFactory.getLogger("ElsewhereBeta");

	private BetaClientSafety() {}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void fillMissingRenderers() {
		try {
			Map<EntityType<?>, ?> providers = null;
			for (Field f : EntityRenderers.class.getDeclaredFields()) {
				if (Map.class.isAssignableFrom(f.getType()) && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
					f.setAccessible(true);
					providers = (Map<EntityType<?>, ?>)f.get(null);
					break;
				}
			}
			if (providers == null) {
				return;
			}
			List<String> missing = new ArrayList<>();
			for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
				if (!id.getNamespace().equals("elsewhere")) {
					continue;
				}
				EntityType type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
				if (!providers.containsKey(type)) {
					missing.add(id.getPath());
					EntityRendererRegistry.register(type, NoopRenderer::new);
				}
			}
			if (!missing.isEmpty()) {
				LOG.warn("[beta] {} entity type(s) had no renderer and are drawn invisible: {}", missing.size(), missing);
			}
		} catch (Throwable t) {
			LOG.warn("[beta] renderer safety check skipped: {}", t.toString());
		}
	}
}
