import pandas as pd
import matplotlib.pyplot as plt
import sys
from io import StringIO

#=======
# config
#=======
cpu_overload_threshold = 0.7

log_data = sys.stdin.read()
df = pd.read_csv(StringIO(log_data), sep=",", names=["datetime", "thread", "cpu_and_rand", "qps", "req", "shed", "latency"])

# 处理数据
df["datetime"] = pd.to_datetime(df["datetime"])
df["seconds"] = (df["datetime"] - df["datetime"].iloc[0]).dt.total_seconds()
df["cpu"] = df["cpu_and_rand"].apply(lambda x: float(x.split(",")[0].split(":")[1]))
df["shed"] = df["shed"].apply(lambda x: int(x.split(":")[1]))
df["qps"] = df["qps"].apply(lambda x: float(x.split(":")[1]))

# 创建图表和轴，调整图表尺寸
fig, ax1 = plt.subplots(figsize=(16, 6))

color = 'tab:red'
ax1.set_xlabel('Time (seconds)')
ax1.set_ylabel('CPU Usage (%)', color=color)
ax1.plot(df["seconds"], df["cpu"], label="CPU Usage", color=color)
ax1.tick_params(axis='y', labelcolor=color)

# 绘制CPU使用率超载阈值的辅助线
ax1.axhline(y=cpu_overload_threshold, color='red', linestyle='--', linewidth=2, label='Threshold')

# 实例化第二个y轴，共享同一个x轴
ax2 = ax1.twinx()
color = 'tab:blue'
ax2.set_ylabel('QPS)', color=color)
ax2.plot(df["seconds"], df["qps"], label="QPS", color=color)
ax2.tick_params(axis='y', labelcolor=color)

# 添加第三个轴
ax3 = ax1.twinx()
color = 'tab:green'
# 通过调整轴的位置来创建第三个y轴的效果
ax3.spines['right'].set_position(('outward', 60))
ax3.set_ylabel('Shed Requests', color=color)
ax3.plot(df["seconds"], df["shed"], label="Shed Requests", color=color)
ax3.tick_params(axis='y', labelcolor=color)

# 其他设置
fig.tight_layout()  # 调整布局以防止重叠
plt.xticks(rotation=45)
fig.legend(loc="upper left", bbox_to_anchor=(0.1, 0.9))

plt.show()
