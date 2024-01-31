package io.github.workload.agent;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

class LoopMonitorAgentTest {

    @Test
    @Disabled
    void attachAgent() throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String className = AgentTestEntryPoint.class.getCanonicalName();

        ProcessBuilder builder = new ProcessBuilder(
                javaBin,
                "-javaagent:../cpu-shield/target/cpu-shield-1.0.0-SNAPSHOT.jar",
                "-cp", classpath,
                className,
                "args0");

        Process process = builder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    System.err.println(line);
                }
            }
        }
        
        try (BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            input.lines().forEach(System.out::println);
        }

    }

}