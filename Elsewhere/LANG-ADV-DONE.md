# Lang and advancements: done

1. Lang: 177 keys appended to Elsewhere's en_us.json (none removed or changed): items, blocks, the 22 missing boss and 4 mob names, dimension and compass names, message and set keys, and 86 advancement title/desc keys.
2. Advancements: 43 new files in data/elsewhere/advancement/ (root, dim/ x7, boss/ x24, boss/all_trophies, gear/ x6, wrong_turn, anchored, bounty, companion). recipes/wayfinder.json was not touched.
3. Code must award these ids: elsewhere:wrong_turn, elsewhere:anchored, elsewhere:bounty, elsewhere:boss/all_trophies (all impossible; AccidentalTrips, Anchors, Bounties and Trophies already call grant for the first three and the last).
4. elsewhere:companion is impossible and hidden, and no code grants it yet: add Trophies.grant(owner, "companion") where a companion is tamed.
5. Dimension advancements (dim/*) use minecraft:changed_dimension and need no code. Boss advancements (boss/*) use player_killed_entity; Trophies.grant also awards them, which is harmless.
6. Endgame sets: read as the six TierGear items (gear/*), each inventory_changed with one piece. No code needed.
7. Hidden: wrong_turn and companion.
8. Not built: lantern (LanternEvent is not in core), so no advancement file exists for it.
9. New English text was written for the four dimensions with no existing name (Petalwind, Glasswaste, Greenhaze, The Hush); boss names came from the ElsewhereBeta snapshot lang files.
10. Checked: all 43 advancement JSON files and en_us.json parse, and every advancement title/desc key exists. Not run in game.
