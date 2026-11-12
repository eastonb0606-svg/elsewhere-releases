"""Calling tags for every gear3 item (GEAR.md A1/A2/A5), owned by s-callingtags. Pure stdlib.

One table, CALLING, maps each of the 82 GEAR.md A5 ids to the calling-related item tags it belongs to (elsewhere: namespace):
  calling/<path>          Leaning armor (Calling.Path.leans; caster/summoner use the 3.0 calling/caster and calling/summoner tags,
                          never the 2.1 caster_armor / summoner_armor tags, which still carry the 2.1 cons when callings is off)
  calling_weapon/<path>   calling items held in the main hand or off hand (Calling.itemPath; vanguard = melee multiplier,
                          marksman = arrow multiplier when in either hand)
  caster_trinket          a trinket whose active path is Caster (Calling.derive trinket rule)
An empty list means the item is in no calling tag on purpose (see the comments).

Used by build_catalogue.py (apply: the catalogue "calling"/"tags" fields of catalogue items) and gen_resources.py (extra: tag lines
for ids that are not in the catalogue yet, written as {"id": ..., "required": false}). The self-test re-checks the loaded game tags
against its own copy of this table (dev.elsewhere.gear3.s3.callingtags.CallingSpec), so a drift on either side fails a check.
"""

V, M, C, S = "vanguard", "marksman", "caster", "summoner"


def lean(*paths):
    return ["calling/" + p for p in paths]


def held(*paths):
    return ["calling_weapon/" + p for p in paths]


CALLING = {
    # ---- Vanguard (melee)
    "galeplate_helmet": lean(V), "galeplate_chestplate": lean(V), "galeplate_leggings": lean(V), "galeplate_boots": lean(V),
    "mirewick_lanternfang_estoc": held(V),
    "aldermark_parrying_buckler": [],  # shield: deals no damage, so calling_weapon/vanguard would only add a misleading x1.15 tooltip
    "hrafnholt_hearthguard_helm": lean(V), "hrafnholt_hearthguard_greaves": lean(V), "hrafnholt_hearthguard_boots": lean(V),
    "hrafnholt_skjaldborg_shield": [],  # shield (as above)
    "bellbreaker_maul": held(V),
    "hrafnholt_ulfhednar_mask": lean(V), "hrafnholt_ulfhednar_breeches": lean(V),
    "hrafnholt_huginns_bite": held(V),
    "hrafnholt_mead_horn": [],  # a drink, not a weapon
    "hrafnholt_runeseax": held(V, C),  # GEAR.md: "in both calling_weapon/vanguard and the caster focus tag"; itemPath reads Vanguard first
    "azmar_glyphmail_hood": lean(V, C), "azmar_glyphmail_hauberk": lean(V, C),
    "aldermark_outrider_chaps": lean(V, M), "aldermark_outrider_boots": lean(V, M),
    "aldermark_oath_ring": [],  # trinket: in calling_weapon/vanguard the ring would satisfy its own "while holding a Vanguard weapon"
    # ---- Marksman (ranged)
    "ridgewatch_hood": lean(M), "ridgewatch_coat": lean(M), "ridgewatch_leggings": lean(M), "ridgewatch_boots": lean(M),
    "aphelion_starpiercer": held(M),
    "rangefinder_lens": [],  # trinket, active in the off hand: in calling_weapon/marksman it would scale a vanilla bow's arrows
    "hrafnholt_hurlbat": held(M),  # throwable
    "gale_sling": held(M),  # throwable launcher
    "aldermark_quickdraw_quiver": [],  # trinket (as rangefinder_lens)
    "sunglass_arrow": [],  # ammo (like kingdom's bodkin_bolt): an off-hand stack in calling_weapon/marksman would scale a vanilla bow
    "echo_arrow": [],  # ammo
    "tidebrass_harpoon_bolt": [],  # ammo
    "mirewick_mire_snare": [],  # placed trap, not a bow, crossbow or throwable
    "mirewick_fowlers_bandolier": [],  # trinket (as rangefinder_lens)
    "prism_bow": held(M, C),  # M+C hybrid; itemPath reads Marksman first
    "glasswaste_starsight_visor": lean(M, C), "glasswaste_prism_mantle": lean(M, C),
    "azmar_falconer_kilt": lean(M, S),
    # ---- Caster (spells; every CasterItem is a Caster calling item, as in arsenal and gear2)
    "sunscribe_hood": lean(C), "sunscribe_robe": lean(C), "sunscribe_leggings": lean(C), "sunscribe_boots": lean(C),
    "emberfall_rod": held(C), "starfall_orrery": held(C), "amber_grimoire": held(C), "frostwake_scepter": held(C),
    "hush_binding_sigil": ["caster_trinket"],  # Control trinket: the trinket rule makes Caster its path
    "choirlight_censer": held(C), "mirewick_almoners_chalice": held(C), "azmar_ka_sigil": held(C),
    "mirewick_will_o_lantern": held(C, S),  # C+S hybrid; itemPath reads Caster first
    "mirewick_lamplighter_hood": lean(C, S), "mirewick_lamplighter_coat": lean(C, S),
    "glimmer_lantern": held(C), "blinkstone": held(C),  # Glimmer utilities (CasterItems: Caster cost scaling)
    # ---- Summoner (minions, whips, crooks, totems, standards)
    "thornlash": held(S), "tidechain_lash": held(S), "azmar_sunscourge": held(S), "echo_chord": held(S),
    "mothlight_staff": held(S), "bramble_totem": held(S), "herdcaller_pennant": held(S),
    "ironpeak_war_yak_horn": held(S), "echo_reliquary": held(S),
    "elderbark_mantle": lean(S), "elderbark_leggings": lean(S), "elderbark_boots": lean(S),
    "court_marshals_helm": lean(S), "court_marshals_tabard": lean(S), "court_marshals_tassets": lean(S),
    "mirewick_lantern_crook": held(S),
    "clockwork_sentry": held(S), "azmar_sunglass_obelisk": held(S),
    # V+S hybrid, Summoner section: Summoner only. calling_weapon/vanguard would win Calling.itemPath (Vanguard is read first) and
    # turn the horn into a Vanguard melee weapon; its x H(vanguard, summoner) is computed by the horn itself.
    "hrafnholt_wolfcall_horn": held(S),
    "hrafnholt_hird_leggings": lean(V, S), "hrafnholt_hird_boots": lean(V, S),
    # ---- Shared materials and tools: no Calling
    "gear3_forging_template": [], "gear3_ascension_shard": [], "aldermark_masons_chisel": [], "tidebrass_dredge": [],
    "mirewick_lantern_sickle": [],
}

assert len(CALLING) == 82, len(CALLING)


def apply(items):
    """build_catalogue.py: set each catalogue item's "calling" (and the caster_trinket tag) from CALLING. Returns warning lines."""
    notes = []
    for it in items:
        i = it["id"]
        if i not in CALLING:
            notes.append(f"calling_tags: {i} is not a GEAR.md A5 id; add it to tools/calling_tags.py CALLING (and the self-test CallingSpec)")
            continue
        armor = it.get("kind") == "armor"
        want = CALLING[i]
        prefix = "calling/" if armor else "calling_weapon/"
        paths = [t[len(prefix):] for t in want if t.startswith(prefix)]
        other = [t for t in want if not t.startswith("calling/") and not t.startswith("calling_weapon/")]
        wrong = [t for t in want if t not in other and not t.startswith(prefix)]
        if wrong:
            raise SystemExit(f"calling_tags: {i} ({it.get('kind')}) cannot carry {wrong}")
        if it.get("calling", []) != paths and it.get("calling"):
            notes.append(f"calling_tags: {i} calling {it.get('calling')} -> {paths}")
        if paths:
            it["calling"] = paths
        else:
            it.pop("calling", None)
        tags = [t for t in it.get("tags", []) if t not in ("elsewhere:caster_trinket",)]
        tags += ["elsewhere:" + t for t in other]
        if tags:
            it["tags"] = tags
        else:
            it.pop("tags", None)
    return notes


def extra(tags, own):
    """gen_resources.py: every GEAR.md id's tag lines, also for ids not in the catalogue (yet; gen_resources writes those as
    required: false). Catalogue ids already got theirs from "calling" (duplicates are dropped), so an item appended to the
    catalogue after build_catalogue's apply() line is still tagged. `own` is unused but kept for the call signature."""
    for i, want in CALLING.items():
        for t in want:
            tags.setdefault("elsewhere:" + t, []).append("elsewhere:" + i)
