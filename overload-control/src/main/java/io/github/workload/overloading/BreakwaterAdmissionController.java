package io.github.workload.overloading;

import io.github.workload.Workload;
import io.github.workload.annotations.Experimental;
import io.github.workload.annotations.VisibleForTesting;
import lombok.NonNull;

/**
 * Breakwater admission controller.
 *
 * <p>维护credit(token)池，通过piggyback分配给clients，它代表满足SLO前提下的服务器处理能力.</p>
 *
 * @see <a href="https://www.usenix.org/conference/osdi20/presentation/cho">Overload Control for μs-scale RPCs with Breakwater</a>
 * @see <a href="https://github.com/shenango/caladan/blob/main/breakwater/src/bw_server.c">Implementation in C by the author</a>
 */
@Experimental
class BreakwaterAdmissionController implements AdmissionController {
    // controls the overcommitment and aggressiveness of the generation of credits
    @VisibleForTesting
    volatile double alpha = 0.1;

    // controls the sensitivity of Breakwater to queue build-up
    private static final double BETA = 0.5;

    // the SLO
    private final double targetDelay = 20;

    private volatile double actualDelay;
    // represents the load the server can handle while maintaining its SLO
    // client has to wait for the server to admit a request(receive credit) before it can send it
    private volatile double credit;

    @Override
    public boolean admit(@NonNull Workload workload) {
        return false;
    }

    @Override
    public void feedback(@NonNull WorkloadFeedback feedback) {

    }

    private void speculateClientDemand() {
        // 如何分配给不同的clients? 这取决于client的需求：how many pending requests
        // speculative demand estimation is coupled with credit overcommitment to ensure that enough clients receive credits to keep the server utilized
    }

    double overcommittedCreditsPerClient(double issuedCredits, int concurrentClients) {
        return Math.max((credit - issuedCredits) / concurrentClients, 1);
    }

    double calculateAlpha(int concurrentClients) {
        // client is allowed to have more credits than its latest demand
        final double aggressiveness = 1.2;
        return Math.max(aggressiveness * concurrentClients, 1);
    }

    @VisibleForTesting
    double calculateCredits(double credit, double targetDelay, double actualDelay) {
        if (actualDelay < targetDelay) {
            credit += alpha;
        } else {
            final double err = (actualDelay - targetDelay) / targetDelay;
            // credit衰减，最多一半
            credit *= Math.max(1.0 - BETA * err, 0.5);
        }

        // Once the credit pool size is updated, re-distribute credits to clients to achieve
        // max-min fairness based on the latest demand information
        return credit;
    }
}
