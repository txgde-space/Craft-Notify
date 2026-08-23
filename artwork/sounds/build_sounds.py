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
        x = np.clip(x, 0.0, 1.0)
        x = np.where(x >= 1.0, 1.0, 1.0 - np.power(2.0, -10.0 * x))
    elif ease == "in_quart":
        x = np.clip(x, 0.0, 1.0) ** 4
    freq = f0 + (f1 - f0) * x
    phase = 2.0 * np.pi * np.cumsum(freq) / SR
    return np.sin(phase)


def biquad_lowpass(x: np.ndarray, cutoff: float, q: float = 0.72) -> np.ndarray:
    w0 = 2.0 * np.pi * cutoff / SR
    cosw = np.cos(w0)
    sinw = np.sin(w0)
    alpha = sinw / (2.0 * q)
    b0 = (1.0 - cosw) / 2.0
    b1 = 1.0 - cosw
    b2 = (1.0 - cosw) / 2.0
    a0 = 1.0 + alpha
    a1 = -2.0 * cosw
    a2 = 1.0 - alpha
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    y = np.empty_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i, v in enumerate(x):
        yi = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        y[i] = yi
        x2, x1, y2, y1 = x1, v, y1, yi
    return y


def rising_bandpass(n: int, f_start: float, f_end: float, q: float = 0.8) -> np.ndarray:
    """Soft whoosh: noise through a smoothly rising bandpass."""
    src = RNG.normal(0.0, 1.0, n)
    y = np.empty(n)
    x1 = x2 = y1 = y2 = 0.0
    b0 = b1 = b2 = a1 = a2 = 0.0
    last_f = -1.0
    for i in range(n):
        progress = i / max(n - 1, 1)
        smooth = progress * progress * (3.0 - 2.0 * progress)
        f = f_start + (f_end - f_start) * smooth
        f = float(np.clip(f, 60.0, 2800.0))
        if abs(f - last_f) > 2.0:
            w0 = 2.0 * np.pi * f / SR
            cosw = np.cos(w0)
            sinw = np.sin(w0)
            alpha = sinw / (2.0 * q)
            a0 = 1.0 + alpha
            b0 = alpha / a0
            b1 = 0.0
            b2 = -alpha / a0
            a1 = -2.0 * cosw / a0
            a2 = (1.0 - alpha) / a0
            last_f = f
        v = src[i]
        yi = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        y[i] = yi
        x2, x1, y2, y1 = x1, v, y1, yi
    mag = float(np.max(np.abs(y))) + 1e-9
    return y / mag


def brown(n: int) -> np.ndarray:
    w = RNG.normal(0.0, 1.0, n)
    y = np.cumsum(w)
    y -= np.mean(y)
    mag = float(np.max(np.abs(y))) + 1e-9
    return y / mag


def bell(t: np.ndarray, freq: float, tau: float) -> np.ndarray:
    env = exp_decay(t, tau)
    sig = (
        1.00 * sine(t, freq)
        + 0.28 * sine(t, freq * 2.0)
        + 0.10 * sine(t, freq * 3.0)
        + 0.18 * sine(t, freq * 0.5)
    )
    return sig * env


def place(dst: np.ndarray, src: np.ndarray, at: float) -> None:
    i = int(at * SR)
    if i >= len(dst) or i < 0:
        return
    n = min(len(src), len(dst) - i)
    dst[i : i + n] += src[:n]


def saturate(x: np.ndarray, drive: float = 1.04) -> np.ndarray:
    return np.tanh(x * drive)


def normalize(x: np.ndarray, peak: float = 0.78) -> np.ndarray:
    x = x - np.mean(x)
    mag = float(np.max(np.abs(x))) + 1e-9
    return x / mag * peak


def write_ogg(name: str, samples: np.ndarray) -> None:
    samples = biquad_lowpass(samples, 4200.0, q=0.65)
    samples = normalize(samples)
    samples *= fade(len(samples), 0.008, 0.04)
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


def chime(freq: float, tau: float = 0.32, amp: float = 0.62) -> np.ndarray:
    return bell(time(int(0.78 * SR)), freq, tau) * amp


def make_charge() -> np.ndarray:
    duration = 2.08
    n = int(duration * SR)
    t = time(n)
    out = np.zeros(n)

    drone_f = 78.0 + 36.0 * (t / duration)
    drone = 0.18 * sine(t, drone_f) + 0.07 * sine(t, drone_f * 2.0)
    drone *= 0.30 + 0.70 * (t / duration)
    out += drone

    warmth = 0.05 * sweep_sine(n, 196.0, 330.0)
    out += warmth * (t / duration)

    rumble = biquad_lowpass(brown(n), 260.0) * 0.028 * (0.4 + 0.6 * t / duration)
    out += rumble

    for at, freq in ((0.00, 329.63), (0.65, 392.00), (1.30, 493.88)):
        place(out, chime(freq), at)

    out *= fade(n, 0.01, 0.18)
    return saturate(out, 1.03)


def make_beam() -> np.ndarray:
    """A single clean upward whoosh for the beam launch."""
    duration = 0.74
    n = int(duration * SR)
    t = time(n)
    out = np.zeros(n)

    whoosh = rising_bandpass(n, 380.0, 2900.0, q=0.58)
    whoosh = biquad_lowpass(whoosh, 3400.0, q=0.62)
    body = biquad_lowpass(whoosh, 1450.0, q=0.70)
    peak_time = 0.29
    attack = np.sin(0.5 * np.pi * np.clip(t / peak_time, 0.0, 1.0)) ** 0.78
    release = np.where(t <= peak_time, 1.0, np.exp(-(t - peak_time) / 0.18))
    envelope = attack * release
    out += (0.76 * whoosh + 0.28 * body) * envelope
    return saturate(out, 1.48)


def make_success() -> np.ndarray:
    duration = 0.90
    out = np.zeros(int(duration * SR))
    place(out, chime(392.00, tau=0.32, amp=0.50), 0.00)
    place(out, chime(493.88, tau=0.34, amp=0.58), 0.08)
    return saturate(out, 1.03)


def make_fail() -> np.ndarray:
    duration = 0.52
    n = int(duration * SR)
    t = time(n)
    drop = sweep_sine(n, 196.00, 87.31)
    fifth = sweep_sine(n, 146.83, 65.41)
    out = (0.78 * drop + 0.34 * fifth) * exp_decay(t, 0.16)
    out *= fade(n, 0.008, 0.08)
    return saturate(out, 1.06)


def main() -> None:
    write_ogg("charge", make_charge())
    write_ogg("beam", make_beam())
    write_ogg("success", make_success())
    write_ogg("fail", make_fail())


if __name__ == "__main__":
    main()
