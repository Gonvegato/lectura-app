#!/usr/bin/env python3
"""Saca el icono adaptable de Marfil (fondo, primer plano y monocromo) de docs/iconos/marfil-original.jpg.

La imagen original es una tarjeta crema con esquinas redondas sobre un fondo oscuro; Android pone sus propias esquinas, así que:
  - la tarjeta se detecta (píxeles claros) y se mete un 9 % por cada lado;
  - el fondo es el degradado de la tarjeta (el interior muy desenfocado, sin líneas);
  - el primer plano son solo las líneas de la flor (lo que es más oscuro que el fondo), con algo más de contraste, escaladas para
    que la flor quede dentro de la zona segura (66 % central);
  - el monocromo es la misma flor en negro (para los iconos con tema).
Uso: python3 herramientas/icono-marfil.py     (necesita Pillow y numpy)
"""
import numpy as np
from PIL import Image, ImageFilter

RAIZ = __file__.rsplit('/herramientas/', 1)[0]
SRC = RAIZ + '/docs/iconos/marfil-original.jpg'
RES = RAIZ + '/app/src/main/res/drawable-nodpi/'
C = 432
LINEA = np.array([122., 108., 88.])

im = Image.open(SRC).convert('RGB')
lum = np.asarray(im).astype(float) @ np.array([.299, .587, .114])
m = lum > 190
ys = np.where(m.any(1))[0]; xs = np.where(m.any(0))[0]
x0, x1, y0, y1 = xs[0], xs[-1], ys[0], ys[-1]
ins = int((x1 - x0) * 0.09)
inter = im.crop((x0 + ins, y0 + ins, x1 - ins, y1 - ins)).resize((1200, 1200), Image.LANCZOS)
L = np.asarray(inter).astype(float) @ np.array([.299, .587, .114])
bg = inter.filter(ImageFilter.GaussianBlur(120))
bgL = np.asarray(bg).astype(float) @ np.array([.299, .587, .114])
alfa = np.clip(np.clip((bgL - L) / (bgL - LINEA @ np.array([.299, .587, .114])), 0, 1) * 1.45, 0, 1)

bg.resize((C, C), Image.BICUBIC).save(RES + 'ic_launcher_marfil_bg.png')

def capa(color):
    out = Image.new('RGBA', (C, C), (0, 0, 0, 0))
    esc = int(C * 0.66)
    al = Image.fromarray((alfa * 255).astype(np.uint8)).resize((esc, esc), Image.LANCZOS)
    col = Image.new('RGBA', (esc, esc), tuple(int(v) for v in color) + (255,)); col.putalpha(al)
    out.paste(col, ((C - esc) // 2, (C - esc) // 2), col)
    return out

capa(LINEA).save(RES + 'ic_launcher_marfil_fg.png')
capa((0, 0, 0)).save(RES + 'ic_launcher_marfil_mono.png')
print('Hecho: fondo, primer plano y monocromo en', RES)
