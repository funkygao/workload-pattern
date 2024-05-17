import pandas as pd
import matplotlib.pyplot as plt
from io import StringIO
import sys

# Overload Threshold
OVERLOAD_THRESHOLD = 75

def parse_log_data(log_data):
    # 读取数据
    df = pd.read_csv(StringIO(log_data), sep=r"\s+(?=\[)", engine='python', names=["datetime_thread", "log"])

    # 处理数据
    df[["datetime", "thread"]] = df["datetime_thread"].str.extract(r"(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (.*)")
    df["datetime"] = pd.to_datetime(df["datetime"], format="%Y-%m-%d %H:%M:%S,%f")
    df["seconds"] = (df["datetime"] - df["datetime"].iloc[0]).dt.total_seconds()
    df["cpu"] = df["log"].str.extract(r"cpu:(\d\.\d+)")[0].astype(float) * 100  # 转换为百分比
    df["smooth"] = df["log"].str.extract(r"smooth:(\d\.\d+)")[0].astype(float) * 100  # 转换为百分比
    df["qps"] = df["log"].str.extract(r"qps:(\d+\.\d+)")[0].astype(float)
    df["shed"] = df["log"].str.extract(r"shed:(\d+)")[0].astype(int)
    df["qps_total"] = df["qps"] + df["shed"]  # qps + shed
    df["latency"] = df["log"].str.extract(r"latency:(\d+)")[0].astype(int)
    df["exhausted"] = df["log"].str.extract(r"exhausted:(\w+)")[0] == 'true'
    return df

def plot_metrics(df):
    # 设置图的大小
    plt.figure(figsize=(14, 7))

    # 上面的图
    plt.subplot(2, 1, 1)
    plt.title("CPU, Smooth, QPS, Shed")
    plt.plot(df["seconds"], df["cpu"], linestyle='--', alpha=0.5, label='CPU')
    plt.plot(df["seconds"], df["smooth"], color='red', label='Smooth')
    plt.plot(df["seconds"], df["qps_total"], color='blue', label='QPS (Total)')
    plt.plot(df["seconds"], df["shed"], color='orange', label='Shed')
    plt.axhline(OVERLOAD_THRESHOLD, color='gray', linestyle='--', linewidth=2, alpha=0.9)
    plt.legend(loc='upper right', bbox_to_anchor=(1, 1))
    plt.ylabel("Percentage / Count")
    #plt.grid(True)

    # 下面的图
    plt.subplot(2, 1, 2)
    plt.title("Latency")
    plt.plot(df["seconds"], df["latency"], color='green', label='Latency')
    plt.legend(loc='upper right', bbox_to_anchor=(1, 1))
    plt.ylabel("Latency (ms)")
    plt.xlabel("Seconds")
    plt.grid(True)

    # 调整布局
    plt.tight_layout()

    # 显示图
    plt.show()

def main():
    log_data = sys.stdin.read()
    df = parse_log_data(log_data)
    plot_metrics(df)

if __name__ == "__main__":
    main()

