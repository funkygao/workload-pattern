import pandas as pd
import matplotlib.pyplot as plt
import sys
from io import StringIO


# 从标准输入读取日志数据
log_data = sys.stdin.read()

# 使用pandas读取数据
df = pd.read_csv(StringIO(log_data), sep=",", names=["datetime", "thread", "cpu_and_rand", "qps", "req", "shed", "latency"])

# 处理数据
df["datetime"] = pd.to_datetime(df["datetime"])
df["cpu"] = df["cpu_and_rand"].apply(lambda x: float(x.split(",")[0].split(":")[1]))
df["shed"] = df["shed"].apply(lambda x: int(x.split(":")[1]))
df["qps"] = df["qps"].apply(lambda x: float(x.split(":")[1]))

# 绘制图表
plt.figure(figsize=(14, 8))

# CPU 使用率
plt.subplot(3, 1, 1)
plt.plot(df["datetime"], df["cpu"], label="CPU Usage", color="red")
plt.ylabel("CPU Usage")
plt.title("CPU Usage, QPS, and Shed Requests Over Time")
plt.xticks(rotation=45)
plt.legend()

# QPS
plt.subplot(3, 1, 2)
plt.plot(df["datetime"], df["qps"], label="QPS", color="blue")
plt.ylabel("QPS")
plt.xticks(rotation=45)
plt.legend()

# 被抛弃的请求数
plt.subplot(3, 1, 3)
plt.plot(df["datetime"], df["shed"], label="Shed Requests", color="green")
plt.ylabel("Shed Requests")
plt.xlabel("Time")
plt.xticks(rotation=45)
plt.legend()

plt.tight_layout()
plt.show()

