package io.github.workload.overloading.bufferbloat.aqm;

public class Packet {
    private final int id;
    private long arrivalTime;
    private boolean shouldDrop = false;

    public Packet(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public void enqueue(long when) {
        arrivalTime = when;
    }

    public long sojournTime(long now) {
        return now - arrivalTime;
    }

    public int size() {
        return id;
    }

    public long arrivalTime() {
        return arrivalTime;
    }

    public Packet drop() {
        shouldDrop = true;
        return this;
    }

    public boolean shouldDrop() {
        return shouldDrop;
    }

    @Override
    public String toString() {
        return "Packet(id=" + id + ")";
    }

    /**
     * TCP四元组的哈希值.
     *
     * <p>ip，端口</p>
     */
    public int quadruples() {
        return hashCode();
    }

}
