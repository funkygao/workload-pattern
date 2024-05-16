package io.github.workload.metrics.noise;

import io.github.workload.BaseTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

class ZScoreDenoiserTest extends BaseTest {

    @Test
    void basic() {
        double[] ds = new double[400];
        ZScoreDenoiser denoiser = new ZScoreDenoiser(200);
        Random random = new Random();
        int c = 0;
        for (int i = 0; i < 400; ++i) {
            ds[i] = i % 10 == 0 ? 80.0 : (double) (40 + random.nextInt(20));
            double res = denoiser.denoiseRight(ds[i], 2.5);
            if (res == 80.0) {
                ++c;
            }

            log.debug("{}", res);
        }

        log.info("c: {}", c);
    }

}
