# Generate the soundtrack: D major, 100 BPM, music and effects mixed as one piece.
import numpy as np, wave, sys
from scipy.signal import butter, sosfilt, fftconvolve

SR = 44100; DUR = 24.0; N = int(SR * DUR)
rng = np.random.default_rng(3)
t = np.arange(N) / SR
BEAT = 0.6; BAR = 4 * BEAT
hz = lambda m: 440.0 * 2 ** ((m - 69) / 12)

def env(n, a, d):
    """Attack then exponential decay envelope of n samples."""
    x = np.arange(n) / SR
    return np.minimum(x / max(a, 1e-4), 1.0) * np.exp(-x / d)

def add(buf, start, sig, gain=1.0):
    i = int(start * SR)
    if i >= len(buf): return
    sig = sig[: len(buf) - i]
    buf[i:i + len(sig)] += sig * gain

def lp(x, f, order=2): return sosfilt(butter(order, f, 'low', fs=SR, output='sos'), x)
def bp(x, lo, hi): return sosfilt(butter(2, [lo, hi], 'band', fs=SR, output='sos'), x)

music = np.zeros(N); fx = np.zeros(N); drums = np.zeros(N)

# Chords per bar: D, Bm, G, A (MIDI notes).
chords = [[50, 57, 62, 66, 69, 76], [47, 54, 59, 62, 66, 73], [43, 50, 55, 59, 62, 69], [45, 52, 57, 61, 64, 71]]

# Pad over the whole piece, a soft swell in the hook, quieter in the privacy scene.
pad = np.zeros(N)
nb = int(np.ceil(DUR / BAR))
for b in range(nb):
    ch = chords[b % 4] if b < 8 else chords[0]
    s0 = b * BAR; n = int((BAR + 1.2) * SR)
    x = np.arange(n) / SR
    e = np.minimum(x / 0.5, 1) * np.clip((BAR + 1.2 - x) / 1.2, 0, 1)
    sig = np.zeros(n)
    for m in ch:
        for det in (-0.12, 0.12):
            f = hz(m + det / 1.0)
            sig += np.sin(2 * np.pi * f * x + rng.random() * 6) + 0.25 * np.sin(4 * np.pi * f * x)
    add(pad, s0, sig * e / len(ch))
pad = lp(pad, 2400)
padGain = np.interp(t, [0, 2.8, 3.3, 17.2, 17.6, 20.4, 20.8, 22.5, 24], [0.25, 0.55, 0.42, 0.42, 0.5, 0.5, 0.55, 0.5, 0.0])
music += pad * padGain * 0.5

# Plucked arpeggio (scenes 2-5 and outro).
def pluck(f, d=0.5):
    n = int(1.4 * SR); x = np.arange(n) / SR
    s = np.sin(2 * np.pi * f * x) + 0.4 * np.sin(4 * np.pi * f * x) * np.exp(-x / 0.12) + 0.15 * np.sin(6 * np.pi * f * x) * np.exp(-x / 0.06)
    return s * env(n, 0.004, d)
arp = np.zeros(N)
pattern = [0, 2, 3, 4, 3, 2, 4, 5]
for k in range(int(DUR / (BEAT / 2))):
    st = k * BEAT / 2
    if not (3.3 <= st < 17.3 or 20.7 <= st < 22.6): continue
    b = int(st // BAR); ch = chords[b % 4] if st < 17.3 else chords[0]
    m = ch[pattern[k % 8]] + 12
    add(arp, st, pluck(hz(m), 0.35), 0.9 if k % 2 == 0 else 0.6)
music += lp(arp, 5000) * 0.16

# Light beat (scenes 2-5): soft kick on beats, shaker on 8ths.
def kick():
    n = int(0.35 * SR); x = np.arange(n) / SR
    f = 45 + 75 * np.exp(-x / 0.04)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.002, 0.11)
def shaker():
    n = int(0.09 * SR)
    return bp(rng.standard_normal(n), 5000, 11000) * env(n, 0.006, 0.025)
for k in range(int(DUR / (BEAT / 2))):
    st = k * BEAT / 2
    if not (4.2 <= st < 17.2): continue
    if k % 2 == 0: add(drums, st, kick(), 0.55)
    add(drums, st, shaker(), 0.10 if k % 2 else 0.05)
music += drums * 0.5

# Bass on the root of each bar (scenes 2-5).
for b in range(nb):
    st = b * BAR
    if not (3.3 <= st < 17.2): continue
    n = int(BAR * SR); x = np.arange(n) / SR
    f = hz(chords[b % 4][0] - 12)
    add(music, st, lp(np.sin(2 * np.pi * f * x) + 0.3 * np.sin(4 * np.pi * f * x), 300) * env(n, 0.02, 1.4), 0.22)

# ---- Effects (tuned to D, mixed under the music) ----
def whoosh(dur, lo, hi):
    n = int(dur * SR); x = np.linspace(0, 1, n)
    return bp(rng.standard_normal(n), lo, hi) * np.sin(np.pi * x) ** 2
def bell(f, d=0.9):
    n = int(2.0 * SR); x = np.arange(n) / SR
    return (np.sin(2 * np.pi * f * x) + 0.35 * np.sin(2 * np.pi * f * 2.0 * x) * np.exp(-x / 0.3)) * env(n, 0.003, d)

for i in range(15):                    # tiles falling, very soft air
    add(fx, 0.05 + i * 0.075, whoosh(0.7, 900 + i * 60, 3500), 0.035)
add(fx, 3.3, whoosh(1.0, 400, 3000), 0.12)       # tiles fly into the phone
add(fx, 4.1, bell(hz(81)) + 0.6 * bell(hz(86)), 0.10)   # title chime A5 + D6
for s0 in (7.95, 9.55):                # page swipes
    add(fx, s0, whoosh(0.5, 1500, 6000), 0.09)
for s0 in (11.62, 11.8):               # double-tap
    add(fx, s0, pluck(hz(86), 0.05), 0.08)
add(fx, 11.88, bell(hz(90), 0.7) + 0.5 * bell(hz(93), 0.6), 0.08)  # heart: F#6 + A6
for i, m in enumerate([74, 78, 81, 86]):          # wall tiles slot in
    add(fx, 14.55 + i * 0.2, pluck(hz(m), 0.3), 0.10)
add(fx, 17.2, whoosh(0.8, 300, 2000), 0.08)
add(fx, 17.85, bell(hz(62), 1.6), 0.06)           # privacy: low D
add(fx, 20.7, bell(hz(74), 1.8) + 0.6 * bell(hz(78), 1.8) + 0.5 * bell(hz(81), 1.8), 0.07)  # outro resolve

# Same room for both: short reverb tail.
n_ir = int(1.6 * SR); ir = rng.standard_normal(n_ir) * np.exp(-np.arange(n_ir) / SR / 0.45); ir = lp(ir, 4000); ir /= np.sqrt(np.sum(ir ** 2))
dry = music + fx
wet = fftconvolve(dry, ir)[:N]
mix = dry * 0.82 + wet * 0.28
mix = lp(mix, 12000, 1)
mix *= np.interp(t, [0, 0.05, 22.8, 24], [0, 1, 1, 0])
mix = np.tanh(mix / np.max(np.abs(mix)) * 1.2) * 0.85      # gentle limiting
st = np.stack([mix, np.roll(mix, int(0.004 * SR)) * 0.98], 1)
with wave.open(sys.argv[1], 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((st * 32767).astype('<i2').tobytes())
print("ok")
