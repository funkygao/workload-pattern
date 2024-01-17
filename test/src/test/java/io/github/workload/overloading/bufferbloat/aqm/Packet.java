package io.github.workload.overloading.bufferbloat.aqm;

public class Packet {
    private final int id;
    private final long arrivalTime;

    public Packet(int id) {
        this.id = id;
        arrivalTime = System.currentTimeMillis();
    }

    public long sojournTime(long nowMs) {
        return nowMs - arrivalTime;
    }

    @Override
    public String toString() {
        return String.valueOf(id);
    }

}
