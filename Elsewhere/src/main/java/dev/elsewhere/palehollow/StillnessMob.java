package dev.elsewhere.palehollow;

/** A mob that moves only when unwatched (Creaking Husk, Mother Creak). The Unblinking Eye freezes them. */
public interface StillnessMob {
	void stillnessFreeze(int ticks);
}
