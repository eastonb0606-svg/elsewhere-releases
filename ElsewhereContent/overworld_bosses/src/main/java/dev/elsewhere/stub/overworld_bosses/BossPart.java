package dev.elsewhere.stub.overworld_bosses;

/**
 * A separate entity that is part of a boss (a detached head, a tentacle). Damage it takes counts for the boss
 * (participation, guards); the lethal-hit guard covers damage it deals. STUB: the core's multipart support.
 */
public interface BossPart {
	ElsewhereBoss parentBoss();
}
