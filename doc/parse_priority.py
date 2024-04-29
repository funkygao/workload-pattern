#!/usr/bin/env python3

def calculate_b_u_from_p(p):
    """
    根据P值计算B和U值。
    P值是一个14位的整数，其中高7位是B值，低7位是U值。
    """
    PRIORITY_BITS = 7
    MAX_7BIT_VALUE = (1 << PRIORITY_BITS) - 1
    
    if p > (1 << (PRIORITY_BITS * 2)) - 1 or p < 0:
        raise ValueError("P值超出范围")
    
    b = (p >> PRIORITY_BITS) & MAX_7BIT_VALUE
    u = p & MAX_7BIT_VALUE
    return b, u

def main():
    print("请输入P值（一个14位的整数）：", end="")
    try:
        p = int(input())
        b, u = calculate_b_u_from_p(p)
        print(f"P: {p}, B: {b}, U: {u}")
    except ValueError as e:
        print("输入错误或P值超出范围：", e)

if __name__ == "__main__":
    main()

