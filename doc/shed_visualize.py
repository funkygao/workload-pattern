import pandas as pd
import matplotlib.pyplot as plt
import mplcursors
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
    fig, ax = plt.subplots(2, 1, figsize=(18, 10), constrained_layout=True, gridspec_kw={'height_ratios': [3, 1]})
    
    # 双Y坐标轴
    ax1 = ax[0]
    ax2 = ax1.twinx()
    
    # 绘制线条
    loosely_dotted = (0, (1, 1))  # 1像素的线段，后面跟着10像素的空白
    densely_dotted = (0, (1, 1))
    loosely_dashed = (0, (5, 10))
    lines = []
    cpu_line, = ax1.plot(df["seconds"], df["cpu"], linestyle=loosely_dotted, alpha=0.25, label='CPU', color='purple')
    smooth_line, = ax1.plot(df["seconds"], df["smooth"], color='red', label='Smooth')
    qps_total_line, = ax2.plot(df["seconds"], df["qps_total"], linestyle='dashed', alpha=0.5, color='blue', label='QPS (Total)')
    shed_line, = ax2.plot(df["seconds"], df["shed"], color='orange', label='Shed')
    lines += [cpu_line, smooth_line, qps_total_line, shed_line]
    
    ax1.axhline(OVERLOAD_THRESHOLD, color='gray', linestyle='dashdot', linewidth=2, alpha=0.7)
    
    # 在smooth线上稀疏地标记对应的y值 每隔30个数据点标记一次
    #for i in range(0, len(df["seconds"]), 30):
    #    ax1.text(df["seconds"][i], df["smooth"][i], f'{df["smooth"][i]:.0f}', color='red', fontsize=8, alpha=0.75)

    # 在Shed线上稀疏地标记对应的y值
    shed_mark_interval = 10
    shed_last_marked_index = -shed_mark_interval
    for i in range(len(df["seconds"])):
        if df["shed"][i] > 0 and (i - shed_last_marked_index) >= shed_mark_interval:
            ax2.text(df["seconds"][i] + 0.3, df["shed"][i], f'{df["shed"][i]:.0f}', color='orange', fontsize=10, alpha=1.0)
            shed_last_marked_index = i
    
    # 在图表中标记exhausted为True的点
    exhausted_points = df[df['exhausted']]
    ax1.scatter(exhausted_points['seconds'], exhausted_points['smooth'], s=50, color='black', label='Exhausted', marker='o', alpha=0.6)

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

    # check buttons
    rax = plt.axes([0.08, 0.15, 0.1, 0.15], facecolor='lightgoldenrodyellow')
    labels = [str(line.get_label()) for line in lines]
    visibility = [line.get_visible() for line in lines]
    check = CheckButtons(rax, labels, visibility)

    def on_clicked(label):
        index = labels.index(label)
        lines[index].set_visible(not lines[index].get_visible())
        plt.draw()

    check.on_clicked(on_clicked)

    # 添加悬停功能
    cursor = mplcursors.cursor(hover=True)

    @cursor.connect("add")
    def on_add(sel):
        # 强制将index转换为整数
        index = int(sel.index)  # 获取当前点的索引，并确保它是整数
        # 获取当前点对应的所有指标的值
        time_point = df.iloc[index]['seconds']
        cpu_value = df.iloc[index]['cpu']
        smooth_value = df.iloc[index]['smooth']
        shed_value = df.iloc[index]['shed']
        qps_total_value = df.iloc[index]['qps_total']
    
        # 设置annotation的文本为所有指标的值
        text = f"Time: {time_point:.2f}s\nCPU: {cpu_value:.2f}%\nSmooth: {smooth_value:.2f}%\nShed: {shed_value}\nQPS (Total): {qps_total_value:.2f}"
    
        # 设置annotation的文本
        sel.annotation.set(text=text, position=(20, 20), bbox=dict(boxstyle="round,pad=0.6", fc="yellow", ec="black", lw=1, alpha=0.5))
        sel.annotation.xy = (time_point, sel.target[1])  # 设置annotation的位置为当前点的位置
        sel.annotation.get_bbox_patch().set_alpha(0.5)


    plt.show()

def main():
    log_data = sys.stdin.read()
    df = parse_log_data(log_data)
    plot_metrics(df)

if __name__ == "__main__":
    main()

