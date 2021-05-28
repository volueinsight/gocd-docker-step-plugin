package no.h_nh.docker_step.utils;

import java.util.ArrayList;
import java.util.List;

import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;


public class TestConsoleLogger extends JobConsoleLogger {
    public final List<String> logLines;

    public TestConsoleLogger() {
        this.logLines = new ArrayList<>();
    }

    @Override
    public void printLine(String line) {
        this.logLines.add(line);
        // System.out.println(line);
    }
}
