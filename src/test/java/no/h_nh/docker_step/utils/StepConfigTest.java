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
                        .add("mounts", Json.createObjectBuilder()
                                .add("value", "/cache:/cache\n/misc:/misc:ro")
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

        assertEquals("Unexpected image", "image:tag", config.image);
        assertTrue("Pull should be true", config.doPull);
        assertEquals("Wrong number of command lines", 2, config.commands.length);
        assertEquals("First command line is wrong", "echo hi", config.commands[0]);
        assertEquals("Second command line is wrong", "ls", config.commands[1]);
        assertEquals("Wrong number of services", 2, config.services.size());
        assertTrue("Service 1 missing", config.services.containsKey("serv1"));
        assertEquals("Service 1 image is wrong", "img1:tag1", config.services.get("serv1"));
        assertTrue("Service 2 missing", config.services.containsKey("serv2"));
        assertEquals("Service 2 image is wrong", "img2:tag2", config.services.get("serv2"));
        assertEquals("Wrong number of mounts", 2, config.mounts.length);
        assertEquals("Mount 1 is wrong", "/cache:/cache", config.mounts[0]);
        assertEquals("Mount 2 is wrong", "/misc:/misc:ro", config.mounts[1]);

        String workingDir = Paths.get(System.getProperty("user.dir"), "pipelines/test")
                .toAbsolutePath().toString();
        assertEquals("Wrong workingDir", workingDir, config.workingDirectory);
        assertEquals("Wrong number of environment vars", 2, config.environment.size());
        assertTrue("Env1 missing", config.environment.containsKey("ENV1"));
        assertEquals("Env1 value wrong", "value1", config.environment.get("ENV1"));
        assertTrue("Env2 missing", config.environment.containsKey("ENV2"));
        assertEquals("Env2 value wrong", "value2", config.environment.get("ENV2"));
    }
}
