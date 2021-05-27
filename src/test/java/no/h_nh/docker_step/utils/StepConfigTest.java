package no.h_nh.docker_step.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;

import javax.json.Json;
import javax.json.JsonObject;

import org.junit.Test;


public class StepConfigTest {

    @Test
    public void parse() {
        JsonObject request = Json.createObjectBuilder()
                .add("config", Json.createObjectBuilder()
                        .add("image", Json.createObjectBuilder()
                                .add("value", "image:tag")
                                .build())
                        .add("pull", Json.createObjectBuilder()
                                .add("value", "true")
                                .build())
                        .add("commands", Json.createObjectBuilder()
                                .add("value", "echo hi\nls")
                                .build())
                        .add("services", Json.createObjectBuilder()
                                .add("value", "serv1;img1:tag1\nserv2;img2:tag2")
                                .build())
                        .build())
                .add("context", Json.createObjectBuilder()
                        .add("workingDirectory", "pipelines/test")
                        .add("environmentVariables", Json.createObjectBuilder()
                                .add("ENV1", "value1")
                                .add("ENV2", "value2")
                                .build())
                        .build())
                .build();
        StepConfig config = StepConfig.parse(request);

        assertEquals("Unexpected image", config.image, "image:tag");
        assertTrue("Pull should be true", config.doPull);
        assertEquals("Wrong number of command lines", config.commands.length, 2);
        assertEquals("First command line is wrong", config.commands[0], "echo hi");
        assertEquals("Second command line is wrong", config.commands[1], "ls");
        assertEquals("Wrong number of services", config.services.size(), 2);
        assertTrue("Service 1 missing", config.services.containsKey("serv1"));
        assertEquals("Service 1 image is wrong", config.services.get("serv1"), "img1:tag1");
        assertTrue("Service 2 missing", config.services.containsKey("serv2"));
        assertEquals("Service 2 image is wrong", config.services.get("serv2"), "img2:tag2");

        String workingDir = Paths.get(System.getProperty("user.dir"), "pipelines/test")
                .toAbsolutePath().toString();
        assertEquals("Wrong workingDir", config.workingDirectory, workingDir);
        assertEquals("Wrong number of environment vars", config.environment.size(), 2);
        assertTrue("Env1 missing", config.environment.containsKey("ENV1"));
        assertEquals("Env1 value wrong", config.environment.get("ENV1"), "value1");
        assertTrue("Env2 missing", config.environment.containsKey("ENV2"));
        assertEquals("Env2 value wrong", config.environment.get("ENV2"), "value2");
    }
}
