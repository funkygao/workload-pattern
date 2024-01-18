package io.github.workload.overloading.bufferbloat.aqm;

interface QueueDiscipline {
    void enqueue(Packet packet);

    Packet dequeue();
}
