package no.h_nh.docker_step.utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.spotify.docker.client.exceptions.ImagePullFailedException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.NetworkConfig;
import com.spotify.docker.client.messages.NetworkCreation;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;


/**
 * Contains various utility methods for interacting with the Docker daemon.
 */
public class DockerUtils {

  static DockerClient dockerClient = null;

  private DockerUtils() {}

  static synchronized DockerClient getDockerClient() {
    if (dockerClient == null) {
      dockerClient = new DefaultDockerClient(
              System.getProperty("dockerstep.dockerhost", "unix:///var/run/docker.sock"));
    }
    return dockerClient;
  }

  /**
   * Pulls the specified image.
   *
   * @param image Image to pull.
   * @throws DockerException If an error occurs.
   * @throws InterruptedException If the process is interrupted.
   */
  public static void pullImage(String image) throws DockerException, InterruptedException {
    final JobConsoleLogger logger = JobConsoleLogger.getConsoleLogger();
    logger.printLine("Pulling image: " + image);
    // basic logic for ProgressHandler pulled from LoggingPullHandler in docker-client
    getDockerClient().pull(image, pm -> {
      final String err = pm.error();
      if (err != null) {
        if (err.contains("404") || err.toLowerCase().contains("not found")) {
          throw new ImageNotFoundException(image, pm.toString());
        } else {
          throw new ImagePullFailedException(image, pm.toString());
        }
      } else {
        StringBuilder message = new StringBuilder().append(pm.status());
        if ("Downloading".equals(pm.status()) || "Extracting".equals(pm.status())) {
          message.append(" ");
          message.append(pm.progress());
        }
        logger.printLine(message.toString());
      }
    });
  }


  /**
   * Starts a service container with a given name.
   * @param name    Name to be known as.
   * @param image   Image to create the container from.
   * @param envVars Environment of container
   * @param network Network to attach to
   * @return Id of container created.
   * @throws DockerException If an error occurs creating the container.
   * @throws InterruptedException If the process is interrupted.
   */
  public static String startService(String name, String image, Map<String, String> envVars,
          String network) throws DockerException, InterruptedException {
    final JobConsoleLogger logger = JobConsoleLogger.getConsoleLogger();
    logger.printLine("Starting service '" + name + "' from image: " + image);

    final List<String> env = new ArrayList<>(envVars.size());
    for (Map.Entry<String, String> entry : envVars.entrySet())
      env.add(entry.getKey() + "=" + entry.getValue());
    final ContainerConfig config = ContainerConfig.builder().image(image).env(env).build();
    final ContainerCreation container = getDockerClient().createContainer(config, name);

    final List<String> warnings = container.warnings();
    if (warnings != null && !warnings.isEmpty())
      for (String warning : warnings)
        logger.printLine("WARNING: " + warning);

    final String id = container.id();
    logger.printLine("Created container: " + name + "/" + id);
    if (network != null) {
      getDockerClient().connectToNetwork(id, network);
      logger.printLine("Attached to network: " + network);
    }
    getDockerClient().startContainer(id);
    logger.printLine("Started container: "+ id);

    // TODO: Find a way to print logs from the serivce container.

    return id;
  }


  /**
   * Runs a script in a container.
   *
   * @param image      Image to create the container from.
   * @param script     Relative path to script file
   * @param workingDir Working directory to be bind mounted into the container.
   * @param envVars    Environment
   * @param user       Uid:gid to run as
   * @return Exit code of script
   * @throws DockerException If an error occurs creating the container.
   * @throws InterruptedException If the process is interrupted.
   */
  public static long runScript(String image, String script, String workingDir,
          Map<String, String> envVars, String user, String network)
          throws DockerException, InterruptedException {
    final JobConsoleLogger logger = JobConsoleLogger.getConsoleLogger();
    logger.printLine("Creating container for script with image: " + image);

    String id = null;
    try {
      final List<String> env = new ArrayList<>(envVars.size());
      for (Map.Entry<String, String> entry : envVars.entrySet())
        env.add(entry.getKey() + "=" + entry.getValue());
      final ContainerConfig config = ContainerConfig.builder()
              .image(image).cmd(script).workingDir("/app").user(user).env(env)
              .attachStdin(true).attachStdout(true).attachStderr(true)
              .hostConfig(HostConfig.builder().appendBinds(workingDir + ":/app").build())
              .build();
      final ContainerCreation container = getDockerClient().createContainer(config);

      final List<String> warnings = container.warnings();
      if (warnings != null && !warnings.isEmpty())
        for (String warning : warnings)
          logger.printLine("WARNING: " + warning);

      id = container.id();
      logger.printLine("Created container: " + id);
      if (network != null) {
        getDockerClient().connectToNetwork(id, network);
        logger.printLine("Attached to network: " + network);
      }
      getDockerClient().startContainer(id);
      logger.printLine("Started container: " + id);

      final List<DockerClient.LogsParam> logParams = new ArrayList<>();
      logParams.add(DockerClient.LogsParam.follow());
      logParams.add(DockerClient.LogsParam.stdout());
      logParams.add(DockerClient.LogsParam.stderr());
      try (final LogStream logStream =
                   getDockerClient().logs(id, logParams.toArray(new DockerClient.LogsParam[0]))) {
        while (logStream.hasNext()) {
          final String logMessage = StringUtils.chomp(StandardCharsets.UTF_8.decode(logStream.next().content()).toString());
          for (String logLine : logMessage.split("\n")) {
            logger.printLine(logLine);
          }
        }
      }

      final Long exitStatus = getDockerClient().waitContainer(id).statusCode();
      if (exitStatus == null) {
        throw new IllegalStateException("Exit code of container is null");
      }
      logger.printLine("Container '" + id + "' exited with status " + exitStatus);
      return exitStatus;
    } finally {
      if (id != null)
        removeContainer(id);
    }
  }

  /**
   * Stops and removes the specified container and it's volumes ('docker rm -v containerId').
   * This will wait one minute before issuing SIGKILL to the container.
   *
   * @param containerId ID of container to remove.
   * @throws DockerException If an occurs removing the container.
   * @throws InterruptedException If the process is interrupted.
   */
  public static void removeContainer(String containerId)
          throws DockerException, InterruptedException {
    final JobConsoleLogger logger = JobConsoleLogger.getConsoleLogger();
    logger.printLine("Stopping container: " + containerId);
    getDockerClient().stopContainer(containerId, 60);

    logger.printLine("Removing container: " + containerId);
    getDockerClient().removeContainer(containerId, RemoveContainerParam.removeVolumes());
  }

  /**
   * Create a (private) network for attaching container and services to.
   * This is neeed so they see each other and can see each other by name.
   *
   * @return Identifier of the network created.
   */
  public static String createNetwork() throws DockerException, InterruptedException {
    final JobConsoleLogger logger = JobConsoleLogger.getConsoleLogger();
    logger.printLine("Creating services network.");
    final NetworkConfig config = NetworkConfig.builder().name("step_services").build();
    final NetworkCreation network = getDockerClient().createNetwork(config);

    final String warning = network.warnings();
    if (warning != null)
      logger.printLine("WARNING: " + warning);

    final String id = network.id();
    logger.printLine("Network created: " + id);
    return id;
  }

  /**
   * Remove a (private) network.
   * @param networkId network to remove.
   */
  public static void removeNetwork(String networkId) throws DockerException, InterruptedException {
    final JobConsoleLogger logger = JobConsoleLogger.getConsoleLogger();
    getDockerClient().removeNetwork(networkId);
    logger.printLine("Removed network: " + networkId);
  }
}
