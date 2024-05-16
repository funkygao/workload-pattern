import pandas as pd
import matplotlib.pyplot as plt
import sys
from io import StringIO

#=======
# config
#=======
cpu_overload_threshold = 0.7 * 100  # 以百分比表示

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

# 第一个图表：CPU Usage, Smooth, Shed
ax1.set_xlabel('Time (seconds)')
ax1.set_ylabel('CPU Usage (%)')
ax1.plot(df["seconds"], df["cpu"], label="CPU Usage", color='red', marker='o', markevery=50, linestyle='--', alpha=0.2)  # CPU Usage为红色，添加标记，增加标记间隔
ax1.plot(df["seconds"], df["smooth"], label="Smoothed (Used for Overload)", color='blue')
ax1.axhline(y=cpu_overload_threshold, color='gray', linestyle='--', linewidth=2, label='CPU Threshold (70%)')  # Threshold为灰色

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

plt.show()

