# -*- coding: utf-8 -*-
"""一键重新生成全部在用配图（等价于原 gen_images.py 的 __main__）。
单独生成某一张：python <图片名>.py
"""
import os
import sys
import importlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import save

ACTIVE = [
    "b_cell",
    "b_dna",
    "b_ecosys",
    "b_meiosis",
    "b_photo",
    "b_reflex",
    "b_resp",
    "c_argue",
    "c_bond",
    "c_cell",
    "c_electro",
    "c_ethanol",
    "c_mole",
    "c_titration",
    "e_clause",
    "e_contour",
    "e_globe",
    "e_monsoon",
    "e_plate",
    "e_pressure",
    "e_water",
    "g_curve",
    "h_modern",
    "h_timeline",
    "h_war",
    "m_deriv",
    "m_deriv2",
    "m_exp",
    "m_hyper",
    "m_log",
    "m_normal",
    "m_parabola",
    "m_prob",
    "m_quad",
    "m_seq",
    "m_solid",
    "m_trig",
    "m_unit",
    "m_vector",
    "p_ac",
    "p_capacitor",
    "p_circuit",
    "p_circular",
    "p_efield",
    "p_em",
    "p_equilibrium",
    "p_gas",
    "p_interference",
    "p_lorentz",
    "p_momentum",
    "p_projectile",
    "p_refract",
    "p_slope",
    "p_transformer",
    "p_vt",
    "p_wave",
]


if __name__ == "__main__":
    total = 0
    n = 0
    for name in ACTIVE:
        mod = importlib.import_module(name)
        c = mod.build()
        _, size = save(c, name)
        total += size
        n += 1
    print("共 %d 张，合计 %.1f KB" % (n, total / 1024.0))
