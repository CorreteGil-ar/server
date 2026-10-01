"""Sonidos propios del paquete, sintetizados con numpy y codificados a OGG con ffmpeg.

Disparos por categoría (estampido + cuerpo + golpe grave + cola de eco), silenciada, lanzadores,
motores de vehículos, rotor, reactor, cañón y explosiones. Cada evento tiene 3 variantes que el
cliente elige al azar. Todo en mono (así el juego los ubica en el espacio).

Si ffmpeg no está, se saltea: los .ogg ya generados quedan en el repo.
"""
import json
import shutil
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

SR = 22050


def _t(seg):
    return np.arange(int(seg * SR)) / SR


def _lp(x, fc):
    """Pasabajos de un polo (con dos pasadas para que corte más)."""
    a = np.exp(-2 * np.pi * fc / SR)
    for _ in range(2):
        y = np.empty_like(x)
        acc = 0.0
        for i, v in enumerate(x):
            acc = (1 - a) * v + a * acc
            y[i] = acc
        x = y
    return x


def _hp(x, fc):
    return x - _lp(x, fc)


def _env(n, ataque, caida):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(ataque, 1e-4)) * np.exp(-t / caida)


def _eco(x, largo, nivel, rng, fc=1800):
    """Cola de reverberación: convolución con ruido que decae (por FFT)."""
    n = int(largo * SR)
    ir = rng.uniform(-1, 1, n) * np.exp(-np.arange(n) / SR / (largo / 4))
    ir = _lp(ir, fc)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    m = len(x) + n
    tam = 1 << (m - 1).bit_length()
    y = np.fft.irfft(np.fft.rfft(x, tam) * np.fft.rfft(ir, tam), tam)[:m]
    salida = np.zeros(m)
    salida[:len(x)] += x
    return salida + nivel * y / (np.max(np.abs(y)) + 1e-9) * np.max(np.abs(x))


def _normalizar(x, pico=0.95, saturacion=1.6):
    x = np.tanh(x / (np.max(np.abs(x)) + 1e-9) * saturacion)
    return x / (np.max(np.abs(x)) + 1e-9) * pico


def disparo(rng, chasquido, fc_cuerpo, caida_cuerpo, f_golpe, caida_golpe, eco, nivel_eco):
    dur = 0.35 + caida_golpe * 2
    n = int(dur * SR)
    t = np.arange(n) / SR
    crack = _hp(rng.uniform(-1, 1, n), 2500) * _env(n, 0.0003, chasquido)
    cuerpo = _lp(rng.uniform(-1, 1, n), fc_cuerpo) * _env(n, 0.001, caida_cuerpo) * 3.0
    fase = 2 * np.pi * np.cumsum(f_golpe * (0.55 + 0.45 * np.exp(-t / 0.05))) / SR
    golpe = np.sin(fase) * _env(n, 0.002, caida_golpe) * 1.2
    x = crack * 0.9 + cuerpo + golpe
    return _normalizar(_eco(x, eco, nivel_eco, rng))


DISPAROS = {
    "pistola": (0.008, 2600, 0.035, 115, 0.06, 0.5, 0.20),
    "subfusil": (0.006, 2300, 0.030, 105, 0.05, 0.4, 0.16),
    "fusil": (0.010, 1900, 0.055, 85, 0.09, 0.8, 0.24),
    "ametralladora": (0.012, 1600, 0.065, 72, 0.11, 0.9, 0.26),
    "escopeta": (0.016, 1300, 0.100, 62, 0.16, 1.0, 0.26),
    "tirador": (0.014, 1500, 0.085, 66, 0.15, 1.3, 0.30),
    "francotirador": (0.020, 1100, 0.140, 52, 0.24, 1.8, 0.34),
    "vehiculo_ametralladora": (0.012, 1500, 0.07, 70, 0.12, 0.9, 0.28),
}


def silenciada(rng):
    n = int(0.25 * SR)
    cuerpo = _lp(rng.uniform(-1, 1, n), 800) * _env(n, 0.001, 0.022) * 3
    t = np.arange(n) / SR
    clic = np.sin(2 * np.pi * 3200 * t) * _env(n, 0.0002, 0.004) * 0.5
    corredera = np.roll(np.sin(2 * np.pi * 2100 * t) * _env(n, 0.0002, 0.006) * 0.35, int(0.045 * SR))
    return _normalizar(_eco(cuerpo + clic + corredera, 0.15, 0.06, rng), 0.8, 1.2)


def lanzagranadas(rng):
    n = int(0.35 * SR)
    t = np.arange(n) / SR
    golpe = np.sin(2 * np.pi * np.cumsum(150 * (0.5 + 0.5 * np.exp(-t / 0.04))) / SR) * _env(n, 0.002, 0.06)
    cuerpo = _lp(rng.uniform(-1, 1, n), 700) * _env(n, 0.001, 0.04) * 2
    return _normalizar(_eco(golpe + cuerpo, 0.4, 0.15, rng))


def lanzacohetes(rng):
    n = int(1.1 * SR)
    t = np.arange(n) / SR
    bang = _lp(rng.uniform(-1, 1, n), 1500) * _env(n, 0.001, 0.06) * 3
    silbido = _hp(rng.uniform(-1, 1, n), 900) * np.minimum(1, t / 0.05) * np.exp(-t / 0.45) * 0.8
    return _normalizar(_eco(bang + silbido, 1.0, 0.25, rng))


def explosion(rng, dur, fc, caida, f_golpe):
    n = int(dur * SR)
    t = np.arange(n) / SR
    ruido = _lp(rng.uniform(-1, 1, n), fc) * _env(n, 0.003, caida) * 3
    chispas = _hp(rng.uniform(-1, 1, n), 2000) * (rng.random(n) < 0.004) * _env(n, 0.01, caida * 0.8) * 4
    golpe = np.sin(2 * np.pi * np.cumsum(f_golpe * (0.5 + 0.5 * np.exp(-t / 0.1))) / SR) * _env(n, 0.004, caida * 0.6) * 1.5
    return _normalizar(_eco(ruido + chispas + golpe, dur * 0.8, 0.3, rng, fc=900), 0.95, 1.8)


def motor(rng, f, dur, golpeteo):
    """Diesel: pulsos de explosión a f Hz con armónicos y jitter; con orugas, golpeteo metálico."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    jit = 1 + 0.03 * _lp(rng.uniform(-1, 1, n), 5) * 20
    fase = 2 * np.pi * np.cumsum(f * jit) / SR
    x = sum(np.sin(k * fase) / k ** 1.2 for k in range(1, 9))
    x += _lp(rng.uniform(-1, 1, n), 300) * 0.6
    if golpeteo:
        paso = int(0.07 * SR)
        for i in range(0, n, paso):
            m = min(n - i, int(0.02 * SR))
            x[i:i + m] += np.sin(2 * np.pi * 1700 * t[:m]) * np.exp(-t[:m] / 0.004) * 0.6
    borde = np.minimum(1, np.minimum(t, dur - t) / 0.05)
    return _normalizar(x * borde, 0.8, 1.4)


def rotor(rng):
    n = int(0.25 * SR)
    t = np.arange(n) / SR
    golpe = _lp(rng.uniform(-1, 1, n), 450) * np.exp(-((t - 0.06) / 0.025) ** 2) * 3
    turbina = np.sin(2 * np.pi * 1150 * t) * 0.08
    return _normalizar(golpe + turbina, 0.85, 1.3)


def reactor(rng):
    n = int(0.6 * SR)
    t = np.arange(n) / SR
    rugido = _lp(_hp(rng.uniform(-1, 1, n), 300), 2500) * 1.2
    silbido = np.sin(2 * np.pi * (3100 + 40 * np.sin(2 * np.pi * 6 * t)) * t) * 0.15
    borde = np.minimum(1, np.minimum(t, 0.6 - t) / 0.06)
    return _normalizar((rugido + silbido) * borde, 0.8, 1.2)


def canon(rng):
    x = explosion(rng, 2.2, 700, 0.35, 45)
    n = len(x)
    crack = _hp(rng.uniform(-1, 1, n), 2000) * _env(n, 0.0003, 0.012)
    return _normalizar(x + crack, 0.95, 1.8)


def eventos():
    """nombre del evento -> función(rng) que devuelve el sonido."""
    ev = {f"arma.{k}": (lambda rng, p=p: disparo(rng, *p)) for k, p in DISPAROS.items() if not k.startswith("vehiculo")}
    ev["arma.silenciada"] = silenciada
    ev["arma.lanzagranadas"] = lanzagranadas
    ev["arma.lanzacohetes"] = lanzacohetes
    ev["vehiculo.ametralladora"] = lambda rng: disparo(rng, *DISPAROS["vehiculo_ametralladora"])
    ev["vehiculo.canon"] = canon
    ev["vehiculo.motor_orugas"] = lambda rng: motor(rng, 27, 0.5, True)
    ev["vehiculo.motor_ruedas"] = lambda rng: motor(rng, 46, 0.4, False)
    ev["vehiculo.rotor"] = rotor
    ev["vehiculo.reactor"] = reactor
    ev["explosion.grande"] = lambda rng: explosion(rng, 2.6, 380, 0.7, 38)
    ev["explosion.chica"] = lambda rng: explosion(rng, 1.3, 900, 0.3, 60)
    return ev


def _ogg(muestras, destino):
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        ruta_wav = tmp.name
    with wave.open(ruta_wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(muestras, -1, 1) * 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", ruta_wav, "-c:a", "libvorbis", "-q:a", "3",
                    "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(destino)], check=True)
    Path(ruta_wav).unlink()


def main(pack):
    if shutil.which("ffmpeg") is None:
        print("sonidos: sin ffmpeg, quedan los .ogg del repo")
        return
    raiz = Path(pack) / "assets" / "tresmodos"
    carpeta = raiz / "sounds"
    if carpeta.exists():
        shutil.rmtree(carpeta)
    definicion = {}
    for i, (nombre, crear) in enumerate(sorted(eventos().items())):
        archivos = []
        for v in range(3):
            rng = np.random.default_rng(1000 * i + v)
            ruta = nombre.replace(".", "/") + f"_{v + 1}"
            destino = carpeta / f"{ruta}.ogg"
            destino.parent.mkdir(parents=True, exist_ok=True)
            _ogg(crear(rng), destino)
            archivos.append(f"tresmodos:{ruta}")
        definicion[nombre] = {"sounds": archivos}
    (raiz / "sounds.json").write_text(json.dumps(definicion, indent=1) + "\n", encoding="utf-8")
    print(f"sonidos: {len(definicion)} eventos")


if __name__ == "__main__":
    main(Path(__file__).resolve().parent.parent / "paquete-recursos")
