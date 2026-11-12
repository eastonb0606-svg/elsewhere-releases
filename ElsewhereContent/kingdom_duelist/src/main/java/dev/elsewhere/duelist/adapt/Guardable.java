package dev.elsewhere.duelist.adapt;

/** Optional: a guard mob that can take shield orders (the kingdom soldier implements it through its lane's adapter). */
public interface Guardable {
	/** Raise the shield for this many ticks regardless of threats. */
	void raiseShield(int ticks);
}
