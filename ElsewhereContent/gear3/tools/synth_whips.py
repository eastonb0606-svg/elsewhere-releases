#!/usr/bin/env python3
"""s-whips (3.0 swarm): the four gear3 whip cracks, synthesised (no samples).

Run: ROOT/_tools/venv/bin/python -I ElsewhereContent/gear3/tools/synth_whips.py
Writes mono 44.1 kHz OGG/Vorbis (mono so Minecraft attenuates them by distance) to
src/main/resources/assets/elsewhere/sounds/gear3/whip/<id>_<n>.ogg, two variants per whip.
Each crack = a rising air swish (the loop travelling down the lash) + the supersonic crack at the tip (a sharp N-wave
click with a short bright tail), then the whip's own layer:
  thornlash         dry thorn snap and a leafy rustle (Swarm, Greenhaze)
  tidechain_lash    copper chain links jingling and a spray of sea water (Tidewright, verdigris)
  azmar_sunscourge  a cobra's venom hiss and a thin brass shimmer (Azmar)
  echo_chord        a resonant chord that echoes the crack back twice (the Hush, sculk)
Deterministic: every sound seeds its RNG from its name.
"""
import os
import zlib

import numpy as np
import soundfile as sf

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "src", "main", "resources", "assets", "elsewhere", "sounds", "gear3", "whip")
RNG = np.random.default_rng(1)


def seed(name):
    global RNG
    RNG = np.random.default_rng(zlib.crc32(name.encode()))


def n_of(d):
    return int(d * SR)


def noise(d):
    return RNG.uniform(-1, 1, n_of(d))


def band(x, lo, hi):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    g = np.clip((f - lo * 0.7) / (lo * 0.3 + 1e-9), 0, 1) * np.clip((hi * 1.3 - f) / (hi * 0.3 + 1e-9), 0, 1)
    return np.fft.irfft(X * g, len(x))


def place(dst, src, at):
    i = int(at * SR)
    m = min(len(src), len(dst) - i)
    if m > 0:
        dst[i:i + m] += src[:m]
    return dst


def swish(d, lo=500, hi=3500):
    """Air swish: noise through a band that sweeps up while it swells (the loop running down the lash)."""
    n = n_of(d)
    t = np.arange(n) / n
    out = np.zeros(n)
    seg = 512
    raw = noise(d + 0.05)
    for i in range(0, n, seg):
        c = lo + (hi - lo) * (i / n) ** 1.6
        out[i:i + seg] = band(raw[i:i + seg + 512], c * 0.6, c * 1.6)[:len(out[i:i + seg])]
    env = np.sin(np.pi * np.clip(t * 1.15, 0, 1)) ** 2 * (0.25 + 0.75 * t)
    return out * env


def crack(bright=1.0):
    """The tip breaking the sound barrier: an N-wave (up, down) of ~1.5 ms plus a 25 ms bright noise tail."""
    n = n_of(0.06)
    x = np.zeros(n)
    w = int(0.0015 * SR)
    x[:w] = np.linspace(1.0, -1.0, w)
    tail = band(noise(0.06), 1800 * bright, 12000) * np.exp(-np.arange(n) / (0.006 * SR))
    snap = band(noise(0.06), 300, 2500) * np.exp(-np.arange(n) / (0.012 * SR)) * 0.5
    return x + tail * 1.4 + snap


def ping(freq, d, decay):
    t = np.arange(n_of(d)) / SR
    return np.sin(2 * np.pi * freq * t) * np.exp(-t / decay)


def layer(kind, v):
    if kind == "thornlash":
        # thorn snap (short dry twig crack) + leaf rustle bursts
        out = np.zeros(n_of(0.35))
        snap = band(noise(0.03), 1500, 6000) * np.exp(-np.arange(n_of(0.03)) / (0.004 * SR))
        place(out, snap * 0.8, 0.012 + 0.004 * v)
        for i in range(5):
            r = band(noise(0.04), 2500, 7000) * np.hanning(n_of(0.04)) * (0.22 - 0.03 * i)
            place(out, r, 0.03 + 0.035 * i + RNG.uniform(0, 0.01))
        return out
    if kind == "tidechain_lash":
        # copper links: inharmonic metallic pings in quick succession, then a spray of water
        out = np.zeros(n_of(0.45))
        base = 2300 + 180 * v
        for i in range(4):
            f = base * (1 + 0.37 * i) * RNG.uniform(0.97, 1.03)
            p = ping(f, 0.18, 0.035) + 0.5 * ping(f * 2.76, 0.18, 0.02)
            place(out, p * (0.32 - 0.05 * i), 0.01 + 0.028 * i)
        spray = band(noise(0.3), 900, 6000) * np.exp(-np.arange(n_of(0.3)) / (0.08 * SR)) * 0.28
        place(out, spray * (0.6 + 0.4 * np.abs(band(noise(0.3), 5, 40)) * 6), 0.03)
        return out
    if kind == "azmar_sunscourge":
        # venom hiss (sibilant, slow decay) + a thin brass shimmer
        out = np.zeros(n_of(0.6))
        hiss = band(noise(0.55), 4000, 11000)
        e = np.minimum(np.arange(n_of(0.55)) / (0.03 * SR), 1.0) * np.exp(-np.arange(n_of(0.55)) / (0.16 * SR))
        place(out, hiss * e * 0.42, 0.02)
        t = np.arange(n_of(0.4)) / SR
        f0 = 587.0 * (1.0 + 0.02 * v)
        brass = sum((0.6 / k) * np.sign(np.sin(2 * np.pi * f0 * k * t)) for k in (1, 2, 3)) * np.exp(-t / 0.09)
        place(out, band(brass, 900, 5000) * 0.12, 0.01)
        return out
    if kind == "echo_chord":
        # a resonant chord (A-E-A, slightly detuned) that rings, and two echoes of the crack itself
        out = np.zeros(n_of(0.9))
        for f, a in ((440.0, 0.22), (659.3, 0.16), (880.0, 0.12), (1318.5, 0.06)):
            place(out, ping(f * (1.0 + 0.004 * v), 0.8, 0.22) * a, 0.0)
        c = crack(0.8)
        place(out, c * 0.30, 0.16)
        place(out, band(c, 600, 5000) * 0.14, 0.32)
        return out
    raise ValueError(kind)


def whip(kind, v):
    seed(f"{kind}-{v}")
    lead = 0.16 + 0.02 * v
    sw = swish(lead, 450 + 60 * v, 3200 + 300 * v)
    total = lead + 0.95
    out = np.zeros(n_of(total))
    place(out, sw * 0.55, 0.0)
    place(out, crack(1.0 + 0.1 * v), lead)
    place(out, layer(kind, v), lead)
    out = out[: n_of(lead + {"thornlash": 0.4, "tidechain_lash": 0.5, "azmar_sunscourge": 0.62, "echo_chord": 0.92}[kind])]
    fade = n_of(0.02)
    out[-fade:] *= np.linspace(1, 0, fade)
    out = np.tanh(out / np.max(np.abs(out)) * 2.5)  # soft limiter: the crack stays sharp, the whip's own layer comes up
    return (out / np.max(np.abs(out)) * 0.84).astype(np.float32)


def main():
    os.makedirs(OUT, exist_ok=True)
    for kind in ("thornlash", "tidechain_lash", "azmar_sunscourge", "echo_chord"):
        for v in (1, 2):
            p = os.path.join(OUT, f"{kind}_{v}.ogg")
            sf.write(p, whip(kind, v), SR, format="OGG", subtype="VORBIS")
            print("wrote", os.path.relpath(p, HERE), f"{os.path.getsize(p)} bytes")


if __name__ == "__main__":
    main()
