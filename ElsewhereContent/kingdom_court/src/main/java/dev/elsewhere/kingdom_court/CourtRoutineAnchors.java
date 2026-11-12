package dev.elsewhere.kingdom_court;

import dev.elsewhere.kingdom.armies.routine.RoutineAnchors;
import dev.elsewhere.kingdom_court.world.CastleLayout;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The routine anchors of a court realm (ARMIES.md 2.6, s-routines): each {@code where} id of the four day schedules mapped onto
 * the castle layout's named places (yard, hall, gate, towns, walls, the road), so the garrisons and townsfolk do their day at the
 * right place in their own realm. Answers only for squads within 160 blocks of their own realm's centre (villages and field
 * squads use their own anchors); the routine engine checks every place (loaded, dry, standable) before using it. Town places
 * pick the town on the asking squad's side. Layout lanes that move a place add or change a case here.
 */
public final class CourtRoutineAnchors {
	private CourtRoutineAnchors() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		RoutineAnchors.register(new RoutineAnchors.Provider() {
			@Override
			public @Nullable BlockPos anchor(ServerLevel lv, Identifier faction, @Nullable BlockPos near, String where) {
				BlockPos o = site(lv, faction);
				if (o == null || near != null && near.distSqr(o) > 160 * 160) return null;
				return place(o, near, where);
			}

			@Override
			public List<BlockPos> anchors(ServerLevel lv, Identifier faction, String where) {
				BlockPos o = site(lv, faction);
				if (o == null) return List.of();
				return switch (where) {
					case "lantern_posts", "avenue_lamps", "braziers", "cookfires", "fc_fires", "bonfire", "lamps" -> CastleLayout.braziers(o);
					default -> List.of();
				};
			}
		});
	}

	static @Nullable BlockPos site(ServerLevel lv, Identifier faction) {
		if (!lv.dimension().equals(Level.OVERWORLD)) return null;
		CourtState st = CourtState.peek(lv.getServer(), faction);
		return st == null ? null : st.site;
	}

	/** The castle layout's place for a where id; null when the layout has none (the engine then uses its own fallback). */
	public static @Nullable BlockPos place(BlockPos o, @Nullable BlockPos near, String where) {
		boolean west = near == null || near.getX() <= o.getX();
		return switch (where) {
			case "barracks" -> CastleLayout.barracks(o);
			case "hall", "longhall", "guildhall", "diwan", "court" -> CastleLayout.hall(o);
			case "throne", "palace", "hall_focus", "diwan_focus", "guildhall_focus", "palace_focus" -> CastleLayout.throneFront(o);
			case "hall_door" -> CastleLayout.hall(o).offset(0, 0, 6);
			case "kitchens" -> CastleLayout.hall(o).offset(5, 0, 4);
			case "chapel", "temple", "temple_steps" -> CastleLayout.bell(o);
			case "temple_focus", "temple_steps_focus", "chapel_focus" -> CastleLayout.bell(o).offset(0, 0, -6);
			case "yard", "parade_ground", "wrestling_ring", "dike_field" -> CastleLayout.yard(o);
			case "yard_focus", "parade_ground_focus" -> CastleLayout.hall(o);
			case "quintain" -> CastleLayout.yard(o).offset(6, 0, 0);
			case "butts" -> CastleLayout.yard(o).offset(-4, 0, -6);
			case "stables", "shade" -> CastleLayout.stables(o);
			case "gate", "palace_gate", "post" -> CastleLayout.gateInside(o);
			case "palace_gate_focus" -> CastleLayout.hall(o);
			case "walls", "shore_watch" -> CastleLayout.wallTop(o, west);
			case "avenue" -> CastleLayout.at(o, 0, 0, 10);
			case "avenue_focus" -> CastleLayout.hall(o);
			case "square", "bazaar", "quays", "market" -> CastleLayout.town(o, west);
			case "workshops", "forge", "tavern", "taverns", "counting_house" -> CastleLayout.town(o, west).offset(0, 0, -10);
			case "caravanserai" -> CastleLayout.town(o, west).offset(0, 0, 10);
			case "thing_stone", "holmgang_ring" -> CastleLayout.town(o, west).offset(8, 0, -8);
			case "tilting_field", "lists" -> CastleLayout.at(o, 0, 0, 37);
			case "tilting_field_focus", "lists_focus" -> CastleLayout.gateInside(o);
			case "road" -> CastleLayout.at(o, west ? -30 : 30, 0, 36);
			case "road_far" -> CastleLayout.at(o, west ? -80 : 80, 0, 36);
			case "fields", "polders", "peat" -> CastleLayout.at(o, west ? -50 : 50, 0, 38);
			case "jetty", "boats", "shipyard", "strand", "shore", "river", "canals", "sluices" -> CastleLayout.at(o, west ? -44 : 44, 0, 34);
			case "fc_camp" -> CastleLayout.at(o, -40, 0, 42);
			case "grove" -> CastleLayout.at(o, 44, 0, 44);
			default -> null;
		};
	}
}
