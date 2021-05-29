package no.h_nh.docker_step.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;


/**
 * Some small static utility functions as needed.
 */
public class MiscTools {

    /**
     * Retrieve the user the agent runs as, which should be used inside docker as well.
     *
     * @return String suitable as user argument to docker.
     */
    public static String getAgentUser() {
        try {
            Process proc = Runtime.getRuntime()
                    .exec(new String[] {"sh", "-c", "printf %d:%d $(id -u) $(id -g)"});
            if (proc.waitFor() == 0) {
                return IOUtils.toString(proc.getInputStream(), StandardCharsets.UTF_8);
            }
        } catch (IOException | InterruptedException e) {
            // Ignore
        }
        return null;
    }

    public static File createTempFile(String workingDirectory) throws IOException {
        return File.createTempFile("commands", ".sh", new File(workingDirectory));
    }
}
