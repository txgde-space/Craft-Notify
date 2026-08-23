#!/usr/bin/env python3
"""Synthesize Craft Notify block sounds (charge / beam / success / fail)."""

from __future__ import annotations

import subprocess
import wave
from pathlib import Path

import numpy as np

SR = 44100
ROOT = Path(__file__).resolve().parent
OUT = ROOT.parents[1] / "src/main/resources/assets/craft_notify/sounds"
RNG = np.random.default_rng(20260823)


def time(n: int) -> np.ndarray:
    return np.arange(n, dtype=np.float64) / SR


def fade(n: int, attack: float, release: float) -> np.ndarray:
    env = np.ones(n, dtype=np.float64)
    a = min(n, max(1, int(attack * SR)))
    r = min(n, max(1, int(release * SR)))
    env[:a] *= np.linspace(0.0, 1.0, a, endpoint=True)
    env[-r:] *= np.linspace(1.0, 0.0, r, endpoint=True)
    return env


def exp_decay(t: np.ndarray, tau: float) -> np.ndarray:
    return np.exp(-t / max(tau, 1e-4))


def sine(t: np.ndarray, freq: float, phase: float = 0.0) -> np.ndarray:
    return np.sin(2.0 * np.pi * freq * t + phase)


def sweep_sine(n: int, f0: float, f1: float, ease: str = "lin") -> np.ndarray:
    t = time(n)
    x = t / max(t[-1], 1e-9)
    if ease == "out_expo":
        x = 1.0 - np.power(2.0, -10.0 * np.clip(x, 0.0, 1.0))
        x = np.where(t / max(t[-1], 1e-9) >= 1.0, 1.0, x)
    elif ease == "in_quart":
        x = np.clip(x, 0.0, 1.0) ** 4
    freq = f0 + (f1 - f0) * x
    phase = 2.0 * np.pi * np.cumsum(freq) / SR
    return np.sin(phase)


def onepole(x: np.ndarray, cutoff: float) -> np.ndarray:
    rc = 1.0 / (2.0 * np.pi * cutoff)
    a = (1.0 / SR) / (rc + 1.0 / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc)
        y[i] = acc
    return y


def highpass(x: np.ndarray, cutoff: float) -> np.ndarray:
    return x - onepole(x, cutoff)


def noise(n: int) -> np.ndarray:
    return RNG.uniform(-1.0, 1.0, n)


def bell(t: np.ndarray, freq: float, tau: float, inharmonic: float = 0.0) -> np.ndarray:
    env = exp_decay(t, tau)
    sig = (
        1.00 * sine(t, freq)
        + 0.42 * sine(t, freq * (2.0 + inharmonic))
        + 0.22 * sine(t, freq * (3.01 + inharmonic))
        + 0.10 * sine(t, freq * 4.21)
        + 0.06 * sine(t, freq * 5.43)
    )
    return sig * env


def place(dst: np.ndarray, src: np.ndarray, at: float) -> None:
    i = int(at * SR)
    if i >= len(dst) or i < 0:
        return
    n = min(len(src), len(dst) - i)
    dst[i : i + n] += src[:n]


def saturate(x: np.ndarray, drive: float = 1.15) -> np.ndarray:
    return np.tanh(x * drive)


def normalize(x: np.ndarray, peak: float = 0.86) -> np.ndarray:
    x = x - np.mean(x)
    mag = float(np.max(np.abs(x))) + 1e-9
    return x / mag * peak


def write_ogg(name: str, samples: np.ndarray) -> None:
    samples = normalize(samples)
    samples *= fade(len(samples), 0.004, 0.02)
    wav = ROOT / f"{name}.wav"
    ogg = OUT / f"{name}.ogg"
    OUT.mkdir(parents=True, exist_ok=True)
    pcm = np.clip(samples, -1.0, 1.0)
    pcm = (pcm * 32767.0).astype(np.int16)
    with wave.open(str(wav), "w") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SR)
        handle.writeframes(pcm.tobytes())
    subprocess.run(
        ["ffmpeg", "-y", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5", str(ogg)],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    wav.unlink(missing_ok=True)
    print(f"wrote {ogg.relative_to(OUT.parent.parent.parent)}  {len(samples) / SR:.2f}s")


def make_charge() -> np.ndarray:
    duration = 2.08
    n = int(duration * SR)
    t = time(n)
    out = np.zeros(n)

    drone_f = 92.0 + 48.0 * (t / duration)
    drone = 0.18 * sine(t, drone_f) + 0.07 * sine(t, drone_f * 2.01)
    drone *= 0.25 + 0.75 * (t / duration)
    out += drone

    crackle = highpass(noise(n), 1800.0)
    pulses = (RNG.random(n) > 0.97).astype(np.float64)
    pulses = onepole(pulses, 40.0)
    out += 0.07 * crackle * (0.2 + 0.8 * t / duration) * (0.35 + pulses)

    hum = 0.05 * sweep_sine(n, 220.0, 410.0)
    out += hum * (t / duration)

    notes = [(0.00, 392.00), (0.65, 523.25), (1.30, 659.25)]
    for at, freq in notes:
        length = int(0.62 * SR)
        bt = time(length)
        tone = bell(bt, freq, tau=0.22, inharmonic=0.018) * 0.55
        tone += 0.12 * sine(bt, freq * 0.5) * exp_decay(bt, 0.18)
        place(out, tone, at)

    spark = highpass(noise(int(0.08 * SR)), 2500.0) * fade(int(0.08 * SR), 0.002, 0.05)
    for at in (0.00, 0.65, 1.30):
        place(out, spark * 0.22, at)

    return saturate(out, 1.05)


def make_beam() -> np.ndarray:
    duration = 3.05
    n = int(duration * SR)
    t = time(n)
    out = np.zeros(n)

    attack_n = int(0.32 * SR)
    whoosh = highpass(noise(attack_n), 400.0)
    whoosh = onepole(whoosh, 2200.0)
    whoosh *= fade(attack_n, 0.01, 0.18)
    sweep = sweep_sine(attack_n, 420.0, 2400.0, ease="out_expo")
    place(out, 0.55 * whoosh + 0.42 * sweep, 0.0)

    boom_n = int(0.22 * SR)
    boom = sine(time(boom_n), 68.0) * exp_decay(time(boom_n), 0.09)
    place(out, 0.35 * boom, 0.0)

    body = (
        0.22 * sweep_sine(n, 880.0, 520.0, ease="in_quart")
        + 0.16 * sweep_sine(n, 1320.0, 700.0, ease="in_quart")
        + 0.10 * sweep_sine(n, 1760.0, 880.0, ease="in_quart")
    )
    shrink = 1.0 - np.clip((t - 0.30) / 2.70, 0.0, 1.0) ** 4
    shrink = np.clip(shrink, 0.0, 1.0)
    out += body * shrink

    shimmer = highpass(noise(n), 4000.0) * 0.045 * shrink
    out += shimmer

    air = 0.08 * sine(t, 2340.0) * shrink * fade(n, 0.02, 0.4)
    out += air
    return saturate(out, 1.08)


def make_success() -> np.ndarray:
    duration = 0.85
    n = int(duration * SR)
    out = np.zeros(n)
    place(out, bell(time(int(0.85 * SR)), 783.99, 0.20, 0.01) * 0.70, 0.00)
    place(out, bell(time(int(0.75 * SR)), 1174.66, 0.24, 0.012) * 0.55, 0.09)
    sparkle = sine(time(int(0.4 * SR)), 2349.3) * exp_decay(time(int(0.4 * SR)), 0.08)
    place(out, sparkle * 0.12, 0.04)
    return out


def make_fail() -> np.ndarray:
    duration = 0.50
    n = int(duration * SR)
    t = time(n)
    drop = sweep_sine(n, 233.08, 98.00)
    fifth = sweep_sine(n, 174.61, 73.42)
    grit = highpass(noise(n), 700.0) * exp_decay(t, 0.07) * 0.08
    out = (0.72 * drop + 0.38 * fifth) * exp_decay(t, 0.14) + grit
    out *= fade(n, 0.004, 0.06)
    return saturate(out, 1.12)


def main() -> None:
    write_ogg("charge", make_charge())
    write_ogg("beam", make_beam())
    write_ogg("success", make_success())
    write_ogg("fail", make_fail())


if __name__ == "__main__":
    main()
