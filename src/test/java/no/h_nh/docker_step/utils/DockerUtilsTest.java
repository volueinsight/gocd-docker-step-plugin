package no.h_nh.docker_step.utils;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doAnswer;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.LogMessage;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.spotify.docker.client.exceptions.ImagePullFailedException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerExit;
import com.spotify.docker.client.messages.NetworkCreation;
import com.spotify.docker.client.messages.ProgressMessage;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;


@RunWith(PowerMockRunner.class)
@PrepareForTest({JobConsoleLogger.class, DefaultDockerClient.class})
public class DockerUtilsTest {

    @Test
    public void pullImage() throws Exception {
        TestConsoleLogger logger = new TestConsoleLogger();
        PowerMockito.mockStatic(JobConsoleLogger.class);
        when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

        final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
        doAnswer(i -> {
            ((ProgressHandler) i.getArgument(1))
                    .progress(ProgressMessage.builder().status("Downloading").progress("DL1").build());
            ((ProgressHandler) i.getArgument(1))
                    .progress(ProgressMessage.builder().status("Extracting").progress("E1").build());
            ((ProgressHandler) i.getArgument(1))
                    .progress(ProgressMessage.builder().status("Image pulled").build());
            return null;
        }).when(dockerClient).pull(anyString(), any(ProgressHandler.class));
        DockerUtils.dockerClient = dockerClient;

        DockerUtils.pullImage("busybox:latest");

        assertEquals("Wrong number of lines", 4, logger.logLines.size());
        assertEquals("Console log incorrect", "Pulling image: busybox:latest", logger.logLines.get(0));
        assertEquals("Console log incorrect", "Downloading DL1", logger.logLines.get(1));
        assertEquals("Console log incorrect", "Extracting E1", logger.logLines.get(2));
        assertEquals("Console log incorrect", "Image pulled", logger.logLines.get(3));
    }

    @Test(expected = ImageNotFoundException.class)
    public void pullBadImage() throws Exception {
        TestConsoleLogger logger = new TestConsoleLogger();
        PowerMockito.mockStatic(JobConsoleLogger.class);
        when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

        final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
        doAnswer(i -> {
            ((ProgressHandler) i.getArgument(1))
                    .progress(ProgressMessage.builder().error("404 not found").build());
            return null;
        }).when(dockerClient).pull(anyString(), any(ProgressHandler.class));
        DockerUtils.dockerClient = dockerClient;

        DockerUtils.pullImage("bad:image");
    }

    @Test(expected = ImagePullFailedException.class)
    public void pullImageError() throws Exception {
        TestConsoleLogger logger = new TestConsoleLogger();
        PowerMockito.mockStatic(JobConsoleLogger.class);
        when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

        final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
        doAnswer(i -> {
            ((ProgressHandler) i.getArgument(1))
                    .progress(ProgressMessage.builder().error("Server error").build());
            return null;
        }).when(dockerClient).pull(anyString(), any(ProgressHandler.class));
        DockerUtils.dockerClient = dockerClient;

        DockerUtils.pullImage("busybox:latest");
    }

    @Test
    public void startService() throws Exception {
        final TestConsoleLogger logger = new TestConsoleLogger();
        PowerMockito.mockStatic(JobConsoleLogger.class);
        when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

        final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
        when(dockerClient.createContainer(any(ContainerConfig.class), anyString())).thenReturn(
                ContainerCreation.builder().id("123").build());
        doNothing().when(dockerClient).startContainer(anyString());
        DockerUtils.dockerClient = dockerClient;

        final Map<String, String> envs = new HashMap<>();
        envs.put("ENV1", "value1");
        envs.put("ENV2", "value2");

        final String id = DockerUtils.startService("serv1", "busybox:latest", envs, null);

        assertEquals("Wrong ID returned", "123", id);
        assertEquals("Wrong number of lines", 3, logger.logLines.size());
        assertEquals("Console log incorrect", "Starting service 'serv1' from image: busybox:latest", logger.logLines.get(0));
        assertEquals("Console log incorrect", "Created container: serv1/123", logger.logLines.get(1));
        assertEquals("Console log incorrect", "Started container: 123", logger.logLines.get(2));
        ArgumentCaptor<ContainerConfig> containerConfig = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerClient).createContainer(containerConfig.capture(), anyString());
        assertEquals("Image wrong", "busybox:latest", containerConfig.getValue().image());
        assertThat("Environment vars not correct", containerConfig.getValue().env(),
                hasItems("ENV1=value1", "ENV2=value2"));
    }

    @Test(expected = DockerException.class)
    public void startServiceError() throws Exception {
        final TestConsoleLogger logger = new TestConsoleLogger();
        PowerMockito.mockStatic(JobConsoleLogger.class);
        when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

        final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
        when(dockerClient.createContainer(any(ContainerConfig.class), anyString())).thenThrow(new DockerException("FAIL"));
        DockerUtils.dockerClient = dockerClient;

        DockerUtils.startService("serv1", "bad:image", Collections.emptyMap(), null);
    }

    @Test
    public void runScript() throws Exception {
        final TestConsoleLogger logger = new TestConsoleLogger();
        PowerMockito.mockStatic(JobConsoleLogger.class);
        when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

        final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
        when(dockerClient.createContainer(any(ContainerConfig.class))).thenReturn(
                ContainerCreation.builder().id("123").build());
        doNothing().when(dockerClient).startContainer(anyString());

        FakeLogStream logStream = new FakeLogStream();
        logStream.add("Script result line 1");
        logStream.add("Script result line 2");
        when(dockerClient.logs(anyString(), any())).thenReturn(logStream);
        when(dockerClient.waitContainer("123")).thenReturn(ContainerExit.create(0L));
        DockerUtils.dockerClient = dockerClient;

        final Map<String, String> envs = new HashMap<>();
        envs.put("ENV1", "value1");
        envs.put("ENV2", "value2");

        final String[] mounts = new String[] {"/cache:/cache", "/misc:/misc:ro"};

        final long exitCode = DockerUtils.runScript("busybox:latest", "tmpscript.sh",
                "/some-dir", envs, "10:20", null, mounts);

        assertEquals("Wrong exit code", 0, exitCode);
        assertEquals("Wrong number of lines", 8, logger.logLines.size());
        assertEquals("Console log incorrect",
                "Creating container for script with image: busybox:latest", logger.logLines.get(0));
        assertEquals("Console log incorrect", "Created container: 123", logger.logLines.get(1));
        assertEquals("Console log incorrect", "Started container: 123", logger.logLines.get(2));
        assertEquals("Console log incorrect", "Script result line 1", logger.logLines.get(3));
        assertEquals("Console log incorrect", "Script result line 2", logger.logLines.get(4));
        assertEquals("Console log incorrect",
                "Container '123' exited with status 0", logger.logLines.get(5));
        assertEquals("Console log incorrect", "Stopping container: 123", logger.logLines.get(6));
        assertEquals("Console log incorrect", "Removing container: 123", logger.logLines.get(7));
        ArgumentCaptor<ContainerConfig> containerConfig = ArgumentCaptor.forClass(ContainerConfig.class);
        verify(dockerClient).createContainer(containerConfig.capture());
        assertEquals("Image wrong", "busybox:latest", containerConfig.getValue().image());
        assertEquals("Working dir incorrect", "/working", containerConfig.getValue().workingDir());
        assertEquals("Number of bind mounts wrong", 3, containerConfig.getValue().hostConfig().binds().size());
        assertEquals("Bind mount not correct", "/some-dir:/working", containerConfig.getValue().hostConfig().binds().get(0));
        assertEquals("Bind mount2 not correct", "/cache:/cache", containerConfig.getValue().hostConfig().binds().get(1));
        assertEquals("Bind mount3 not correct", "/misc:/misc:ro", containerConfig.getValue().hostConfig().binds().get(2));
        assertThat("Environment vars not correct", containerConfig.getValue().env(),
                hasItems("ENV1=value1", "ENV2=value2"));
    }

    @Test
    public void removeContainer() throws Exception {
        final TestConsoleLogger logger = new TestConsoleLogger();
        PowerMockito.mockStatic(JobConsoleLogger.class);
        when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

        final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
        doNothing().when(dockerClient).removeContainer(anyString(), any());
        DockerUtils.dockerClient = dockerClient;

        DockerUtils.removeContainer("123");

        assertEquals("Wrong number of lines output", 2, logger.logLines.size());
        assertEquals("Console log incorrect", "Stopping container: 123", logger.logLines.get(0));
        assertEquals("Console log incorrect", "Removing container: 123", logger.logLines.get(1));
    }

    @Test
    public void createNetwork() throws Exception {
        final TestConsoleLogger logger = new TestConsoleLogger();
        PowerMockito.mockStatic(JobConsoleLogger.class);
        when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

        final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
        final NetworkCreation network = mock(NetworkCreation.class);
        when(network.warnings()).thenReturn(null);
        when(network.id()).thenReturn("123");
        when(dockerClient.createNetwork(any())).thenReturn(network);
        DockerUtils.dockerClient = dockerClient;

        final String id = DockerUtils.createNetwork();
        assertEquals("Wrong network id", "123", id);
    }

    @Test
    public void removeNetwork() throws Exception {
        final TestConsoleLogger logger = new TestConsoleLogger();
        PowerMockito.mockStatic(JobConsoleLogger.class);
        when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

        final DefaultDockerClient dockerClient = mock(DefaultDockerClient.class);
        doNothing().when(dockerClient).removeNetwork(anyString());
        DockerUtils.dockerClient = dockerClient;

        DockerUtils.removeNetwork("123");
    }

    // Helper class to inject "results" from running a script.
    static class FakeLogStream implements LogStream {
        private final LinkedList<String> lines = new LinkedList<>();

        public void add(String line) {
            lines.addLast(line);
        }

        @Override
        public String readFully() {
            StringBuilder builder = new StringBuilder();
            while (!lines.isEmpty())
                builder.append(lines.removeFirst());
            return builder.toString();
        }

        @Override
        public void attach(OutputStream stdout, OutputStream stderr) throws IOException {
        }

        @Override
        public void attach(OutputStream stdout, OutputStream stderr, boolean closeAtEof) throws IOException {
        }

        @Override
        public void close() {
        }

        @Override
        public boolean hasNext() {
            return !lines.isEmpty();
        }

        @Override
        public LogMessage next() {
            return new LogMessage(1,
                    ByteBuffer.wrap(lines.removeFirst().getBytes(StandardCharsets.UTF_8)));
        }
    }
}
