package no.h_nh.docker_step;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.commons.io.IOUtils;

import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.thoughtworks.go.plugin.api.AbstractGoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;

import no.h_nh.docker_step.utils.DockerUtils;
import no.h_nh.docker_step.utils.MiscTools;
import no.h_nh.docker_step.utils.StepConfig;


@Extension
public class DockerStepPlugin extends AbstractGoPlugin {

  private static final String SUCCESS = "success";
  private static final String MESSAGE = "message";

  @Override
  public GoPluginApiResponse handle(GoPluginApiRequest requestMessage)
          throws UnhandledRequestTypeException {
    final JsonObject request;
    if (requestMessage.requestBody() != null) {
      request = Json.createReader(new StringReader(requestMessage.requestBody())).readObject();
    } else {
      request = null;
    }
    switch (requestMessage.requestName()) {
      case "configuration":
        return handleConfig();
      case "view":
        return handleView();
      case "validate":
        if (request == null) {
          throw new IllegalArgumentException("Request body is missing");
        }
        return handleValidate(request);
      case "execute":
        if (request == null) {
          throw new IllegalArgumentException("Request body is missing");
        }
        return handleExecute(request);
      default:
        throw new UnhandledRequestTypeException(requestMessage.requestName());
    }
  }

  @Override
  public GoPluginIdentifier pluginIdentifier() {
    return new GoPluginIdentifier("task", Collections.singletonList("1.0"));
  }

  private GoPluginApiResponse handleConfig() {
    final Map<String, Object> body = new HashMap<>();
    final String[] args = {"image", "pull", "commands", "services"};
    final Boolean[] required = {true, true, true, false};
    final String[] defaults = {null, "true", null, null};

    for (int i = 0; i < args.length; i++) {
      final Map<String, Object> element = new HashMap<>();
      element.put("required", required[i]);
      element.put("secure", false);
      if (defaults[i] != null)
        element.put("default-value", defaults[i]);
      body.put(args[i], element);
    }
    return DefaultGoPluginApiResponse.success(Json.createObjectBuilder(body).build().toString());
  }

  private GoPluginApiResponse handleView() {
    try {
      final InputStream resource = getClass().getResourceAsStream("/templates/task.template.html");
      final String template;
      if (resource != null)
        template = IOUtils.toString(resource, StandardCharsets.UTF_8);
      else
        throw new IllegalArgumentException("Missing template file for view.");

      final Map<String, Object> body = new HashMap<>();
      body.put("displayValue", "Docker Step");
      body.put("template", template);

      return DefaultGoPluginApiResponse.success(Json.createObjectBuilder(body).build().toString());
    } catch (IOException e) {
      final String body = Json.createObjectBuilder().add("exception", e.getMessage()).build().toString();
      return DefaultGoPluginApiResponse.error(body);
    }
  }

  private GoPluginApiResponse handleValidate(JsonObject request) {
    final Map<String, Object> response = new HashMap<>();
    final Map<String, String> errors = Collections.emptyMap();

    // TODO: Add validation of parameters.

    response.put("errors", errors);
    return DefaultGoPluginApiResponse.success(Json.createObjectBuilder(response).build().toString());
  }

  private GoPluginApiResponse handleExecute(JsonObject request) {
    StepConfig config = StepConfig.parse(request);

    final Map<String, Object> response = new HashMap<>();
    try {
      final long exitCode = executeStep(config);

      response.put(SUCCESS, exitCode == 0);
      response.put(MESSAGE, "Container completed with status " + exitCode);
    } catch (ImageNotFoundException infe) {
      response.put(SUCCESS, Boolean.FALSE);
      response.put(MESSAGE,"Image '" + config.image + "' not found");
    } catch (Exception e) {
      response.put(SUCCESS, Boolean.FALSE);
      response.put(MESSAGE, e.getMessage());
    }

    return DefaultGoPluginApiResponse.success(Json.createObjectBuilder(response).build().toString());
  }

  private long executeStep(StepConfig config)
          throws DockerException, InterruptedException, IOException {
    final JobConsoleLogger logger = JobConsoleLogger.getConsoleLogger();
    List<String> serviceIds = null;
    String net = null;
    try {
      if (config.doPull) {
        DockerUtils.pullImage(config.image);
        for (String serviceImage : config.services.values())
          DockerUtils.pullImage(serviceImage);
      }

      if (!config.services.isEmpty())
        net = DockerUtils.createNetwork();
      serviceIds = new ArrayList<>(config.services.size());
      for (Map.Entry<String, String> e : config.services.entrySet()) {
        serviceIds.add(DockerUtils.startService(e.getKey(), e.getValue(), config.environment, net));
      }

      final String user = MiscTools.getAgentUser();
      final String scriptPath = createScript(config.commands, config.workingDirectory);
      logger.printLine("----- Starting step commands container -----");
      final long exitCode = DockerUtils.runScript(config.image, scriptPath, config.workingDirectory,
              config.environment, user, net, config.mounts);
      logger.printLine("----- Finished step commands container -----");
      return exitCode;
    } catch (Exception e) {
      logger.printLine("Exception occurred during build");
      logException(logger, e);
      throw e;
    } finally {
      if (serviceIds != null)
        for (String service : serviceIds)
          try {
            DockerUtils.removeContainer(service);
          } catch (Exception e) {
            logger.printLine("Exception occurred while removing container");
            logException(logger, e);
          }
      if (net != null)
        try {
          DockerUtils.removeNetwork(net);
        } catch (Exception e) {
          logger.printLine("Exception occurred while removing network");
          logException(logger, e);
        }
    }
  }

  private String createScript(String[] commands, String workingDirectory) throws IOException {
    File scriptfile = MiscTools.createTempFile(workingDirectory);
    // TODO: Be able to configure the script header?
    try (Writer output = new BufferedWriter(new FileWriter(scriptfile))) {
      output.write("#! /usr/bin/env bash\n\nset -ex\n\n");
      for (String command : commands) {
        output.write(command);
        output.write("\n");
      }
    }
    Runtime.getRuntime().exec("chmod +x " + scriptfile.getAbsolutePath());
    return "./" + scriptfile.getName();
  }

  private void logException(JobConsoleLogger logger, Exception e) {
    logger.printLine(e.getMessage());
    for (StackTraceElement ste : e.getStackTrace()) {
      logger.printLine("\t" + ste.toString());
    }
  }
}
