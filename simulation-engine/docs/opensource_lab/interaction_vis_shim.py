# 실험용 shim: matplotlib>=3.6 에서 제거된 canvas.set_window_title 을 원본 수정 없이 보완하고,
# 원본 main_visualize_data.py 를 헤드리스로 실행해 첫 프레임 PNG를 저장한다.
import sys, runpy, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.backend_bases import FigureCanvasBase
FigureCanvasBase.set_window_title = lambda self, t: None
plt.show = lambda *a, **k: (plt.gcf().savefig(OUT), sys.exit(0))
script, scenario, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
sys.argv = [script, scenario]
import os; sys.path.insert(0, os.path.dirname(os.path.abspath(script)))
runpy.run_path(script, run_name="__main__")
