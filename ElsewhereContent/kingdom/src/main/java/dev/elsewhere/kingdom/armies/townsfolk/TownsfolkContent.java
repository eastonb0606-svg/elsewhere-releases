package dev.elsewhere.kingdom.armies.townsfolk;

import dev.elsewhere.kingdom.KingdomContent;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Townsfolk (ARMIES.md 2.5, s-townsfolk): registers {@code elsewhere:townsfolk} (always, so client and server registries match whatever
 * the switch says), the {@code kingdom_townsfolk} data, the LOD manager, {@code /kingdom townsfolk} and the self-test
 * ({@code -Delsewhere.townsfolk.selftest=true}). Kill switch {@code "townsfolk"} in {@code config/elsewhere_armies.json}. Called once
 * from {@code Armies.init()}.
 */
public final class TownsfolkContent {
	private TownsfolkContent() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		TownsfolkConfig.load();
		Identifier id = KingdomContent.id("townsfolk");
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
		@SuppressWarnings("unchecked")
		EntityType<Townsfolk> type = BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? (EntityType<Townsfolk>) BuiltInRegistries.ENTITY_TYPE.getValue(id)
			: Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Townsfolk>of(Townsfolk::new, MobCategory.MISC)
				.sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(4).noLootTable().build(key));
		FabricDefaultAttributeRegistry.register(type, Townsfolk.createAttributes());
		TownsfolkManager.TYPE = type;
		TownsfolkRoles.init();
		TownsfolkManager.init();
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("kingdom").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("townsfolk").executes(c -> {
				var src = c.getSource();
				ServerLevel lv = src.getServer().overworld();
				src.sendSuccess(() -> Component.literal("Townsfolk " + (TownsfolkConfig.active() ? "on" : "off") + ": " + TownsfolkManager.liveTotal() + " live (server cap "
					+ TownsfolkManager.SERVER_CAP + "), hour " + TownsfolkManager.hour(lv) + ", day " + TownsfolkManager.day(lv)), false);
				TownsfolkState st = TownsfolkState.get(lv);
				for (Towns.Town t : Towns.all()) {
					TownsfolkState.Rec r = st.data.towns.get(t.key);
					String g = t.graph == null ? (TownsfolkManager.building(t.key) ? "graph building" : "no graph yet") : "graph " + t.graph.size() + " nodes, " + t.graph.edges() + " edges";
					src.sendSuccess(() -> Component.literal(" " + t + ": residents " + (r == null ? "?" : r.logical) + "/" + t.maxResidents() + ", live " + t.live.size()
						+ "/" + TownsfolkManager.cap(t) + ", pending " + TownsfolkManager.pending(t.key) + ", " + g), false);
				}
				src.sendSuccess(() -> Component.literal(" spawned " + TownsfolkManager.spawned + ", folded far/home/other " + TownsfolkManager.foldedFar + "/" + TownsfolkManager.foldedHome
					+ "/" + TownsfolkManager.foldedOther + ", deaths " + TownsfolkManager.deaths + ", evaluations " + TownsfolkManager.evaluations + ", replans " + TownsfolkManager.replans), false);
				for (var k : TownsfolkRoles.all().values()) {
					var p = TownsfolkRoles.problems(k);
					if (!p.isEmpty()) src.sendSuccess(() -> Component.literal(" " + k.faction() + " PROBLEMS " + p), false);
				}
				return 1;
			}))));
		if (Boolean.getBoolean(TownsfolkConfig.SELFTEST)) TownsfolkSelfTest.register();
		KingdomContent.LOG.info("Townsfolk registered (switch {}, active {})", TownsfolkConfig.enabled, TownsfolkConfig.active());
	}
}
