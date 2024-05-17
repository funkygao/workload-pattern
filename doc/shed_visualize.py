import pandas as pd
import matplotlib.pyplot as plt
from io import StringIO
import sys
from matplotlib.widgets import CheckButtons

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
    fig, ax = plt.subplots(2, 1, figsize=(18, 10))
    
    # 双Y坐标轴
    ax1 = ax[0]
    ax2 = ax1.twinx()
    
    # 绘制线条
    lines = []
    lines += ax1.plot(df["seconds"], df["cpu"], linestyle=':', alpha=0.25, label='CPU', color='purple')
    lines += ax1.plot(df["seconds"], df["smooth"], color='red', label='Smooth')
    lines += ax2.plot(df["seconds"], df["qps_total"], linestyle='--', alpha=0.5, color='blue', label='QPS (Total)')
    lines += ax2.plot(df["seconds"], df["shed"], color='orange', label='Shed')
    
    ax1.axhline(OVERLOAD_THRESHOLD, color='gray', linestyle='dashed', linewidth=2, alpha=0.7)
    
    # 设置图例
    ax1.legend(loc='upper left')
    ax2.legend(loc='upper right')
    
    # 设置标签
    ax1.set_ylabel("Percentage")
    ax2.set_ylabel("Count")
    ax1.set_title("CPU, Smooth, QPS, Shed")
    
    # Latency图
    ax[1].plot(df["seconds"], df["latency"], color='green', label='Latency')
    ax[1].set_title("Latency")
    ax[1].set_ylabel("Latency (ms)")
    ax[1].set_xlabel("Seconds")
    ax[1].legend(loc='upper right', bbox_to_anchor=(1, 1))
    ax[1].grid(True)

    # 检查按钮
    rax = plt.axes([0.02, 0.4, 0.1, 0.15], facecolor='lightgoldenrodyellow')
    labels = [str(line.get_label()) for line in lines]
    visibility = [line.get_visible() for line in lines]
    check = CheckButtons(rax, labels, visibility)

    def func(label):
        index = labels.index(label)
        lines[index].set_visible(not lines[index].get_visible())
        plt.draw()

    check.on_clicked(func)

    plt.tight_layout()
    plt.show()

def main():
    log_data = sys.stdin.read()
    df = parse_log_data(log_data)
    plot_metrics(df)

if __name__ == "__main__":
    main()

