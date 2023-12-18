package me.andreroldan.featherpng;

import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

class PngOptimizerTest {
    @Test
    void test() throws Exception {
        PngOptimizer optimizer = new PngOptimizer();
        PngImage optimized = optimizer.optimize(PngImage.read(Paths.get("src/test/resources/optimizer/serrano.png")));
        try (OutputStream output = Files.newOutputStream(Paths.get("src/test/resources/serrano-optimized.png"))) {
            optimized.writeDataOutputStream(output);
        }
    }
}
