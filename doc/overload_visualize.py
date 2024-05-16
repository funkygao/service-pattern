import pandas as pd
import matplotlib.pyplot as plt
import sys
from io import StringIO

#=======
# config
#=======
cpu_overload_threshold = 75  # 以百分比表示

log_data = sys.stdin.read()

# 读取数据
df = pd.read_csv(StringIO(log_data), sep=r"\s+(?=\[)", engine='python', names=["datetime_thread", "log"], usecols=[0, 1])

# 处理数据
df[["datetime", "thread"]] = df["datetime_thread"].str.extract(r"(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (.*)")
df = df.dropna(subset=['datetime'])
df["datetime"] = pd.to_datetime(df["datetime"], format="%Y-%m-%d %H:%M:%S,%f")
df["seconds"] = (df["datetime"] - df["datetime"].iloc[0]).dt.total_seconds()
df["cpu"] = df["log"].str.extract(r"cpu:(\d\.\d+)")[0].astype(float) * 100  # 转换为百分比
df["smooth"] = df["log"].str.extract(r"smooth:(\d\.\d+)")[0].astype(float) * 100  # 转换为百分比
df["qps"] = df["log"].str.extract(r"qps:(\d+\.\d+)")[0].astype(float)
df["req"] = df["log"].str.extract(r"req:(\d+)")[0].astype(int)
df["shed"] = df["log"].str.extract(r"shed:(\d+)")[0].astype(int)
df["latency"] = df["log"].str.extract(r"latency:(\d+)")[0].astype(int)
df["exhausted"] = df["log"].str.extract(r"exhausted:(\w+)")[0] == 'true'

# 创建两个图表
fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(16, 10))

# 绘图函数，返回线条对象
def plot_with_legend(ax, x, y, label, color, marker=None, linestyle='-', linewidth=2, alpha=1.0):
    line, = ax.plot(x, y, label=label, color=color, marker=marker, linestyle=linestyle, linewidth=linewidth, alpha=alpha)
    line.set_picker(5)  # 5 points tolerance for clicking
    return line


# 第一个图表：CPU Usage, Smooth, Shed
ax1.set_xlabel('Time (seconds)')
ax1.set_ylabel('CPU Usage (%)')
ax1.plot(df["seconds"], df["cpu"], label="CPU Usage", color='red', marker='o', markevery=50, linestyle='--', alpha=0.2)  # CPU Usage为红色，添加标记，增加标记间隔
ax1.plot(df["seconds"], df["smooth"], label="Smoothed (Used for Overload)", color='blue')
ax1.axhline(y=cpu_overload_threshold, color='gray', linestyle='--', linewidth=2, label='CPU Threshold')  # Threshold为灰色

# 创建第二个y轴
ax1_shed = ax1.twinx()
ax1_shed.set_ylabel('Shed Requests', color='tab:green')
ax1_shed.plot(df["seconds"], df["shed"], label="Shed Requests", color='tab:green')
ax1_shed.tick_params(axis='y', labelcolor='tab:green')

# 第一个图表的图例
ax1.legend(loc="upper left")
ax1_shed.legend(loc="upper right")

# 第二个图表：QPS and Latency with dual y-axis
ax2.set_xlabel('Time (seconds)')
ax2.set_ylabel('QPS', color='tab:blue')
ax2.plot(df["seconds"], df["qps"], label="QPS", color='tab:blue')
ax2.tick_params(axis='y', labelcolor='tab:blue')

# 创建第二个y轴
ax2_latency = ax2.twinx()
ax2_latency.set_ylabel('Latency (ms)', color='tab:red')
ax2_latency.plot(df["seconds"], df["latency"], label="Latency", color='tab:red')
ax2_latency.tick_params(axis='y', labelcolor='tab:red')

# 第二个图表的图例
ax2.legend(loc="upper left")
ax2_latency.legend(loc="upper right")

# 图表设置
fig.tight_layout()

# 图例点击事件处理函数
def on_pick(event):
    legline = event.artist
    origline = legline_to_origline[legline]
    vis = not origline.get_visible()
    origline.set_visible(vis)
    legline.set_alpha(1.0 if vis else 0.2)
    fig.canvas.draw()

# 创建图例到原始线条的映射
legline_to_origline = {}
for ax in [ax1, ax2, ax1_shed, ax2_latency]:
    for legline, origline in zip(ax.get_legend().get_lines(), ax.get_lines()):
        legline.set_picker(5)  # 5 pts tolerance for clicks
        legline_to_origline[legline] = origline

# 连接事件处理函数
fig.canvas.mpl_connect('pick_event', on_pick)

plt.show()

