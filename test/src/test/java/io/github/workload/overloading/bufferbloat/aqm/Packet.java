package io.github.workload.overloading.bufferbloat.aqm;

public class Packet {
    private final int id;
    private long arrivalTime;

    public Packet(int id) {
        this.id = id;
    }

    public void enqueue(long when) {
        arrivalTime = when;
    }

    public long sojournTime(long now) {
        return now - arrivalTime;
    }

    public long arrivalTime() {
        return arrivalTime;
    }

    @Override
    public String toString() {
        return String.valueOf(id);
    }

}
