package no.h_nh.docker_step;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.request.DefaultGoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;

import no.h_nh.docker_step.utils.DockerUtils;
import no.h_nh.docker_step.utils.MiscTools;
import no.h_nh.docker_step.utils.TestConsoleLogger;


@RunWith(PowerMockRunner.class)
@PrepareForTest({JobConsoleLogger.class, DockerUtils.class, IOUtils.class, MiscTools.class})
public class DockerStepPluginTest {

  @Test
  public void pluginIdentifier() {
    GoPluginIdentifier identifier = new DockerStepPlugin().pluginIdentifier();
    assertEquals("Wrong type", "task", identifier.getExtension());
    assertEquals("Wrong version", Collections.singletonList("1.0"), identifier.getSupportedExtensionVersions());
  }

  @Test
  public void handleConfiguration() throws Exception {
    GoPluginApiResponse response = new DockerStepPlugin().handle(
        new DefaultGoPluginApiRequest(null, null, "configuration"));

    assertEquals("Expected successful response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
  }

  @Test
  public void handleView() throws Exception {
    GoPluginApiResponse response = new DockerStepPlugin().handle(
        new DefaultGoPluginApiRequest(null, null, "view"));

    assertEquals("Expected successful response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
    String expected = new String(Files.readAllBytes(Paths.get(
        getClass().getResource("/templates/task.template.html").toURI())));
    String actual = Json.createReader(new StringReader(response.responseBody())).readObject().getString("template");
    assertEquals("HTML content doesn't match", expected, actual);
  }

  @Test
  public void handleViewError() throws Exception {
    PowerMockito.mockStatic(IOUtils.class);
    when(IOUtils.toString(any(InputStream.class), any(Charset.class))).thenThrow(new IOException("TESTERROR"));

    GoPluginApiResponse response = new DockerStepPlugin().handle(
        new DefaultGoPluginApiRequest(null, null, "view"));

    assertEquals("Expect 5xx response", DefaultGoPluginApiResponse.INTERNAL_ERROR, response.responseCode());
    assertEquals("Wrong body", "TESTERROR",
        Json.createReader(new StringReader(response.responseBody())).readObject().getString("exception"));
  }

  @Test
  public void handleValidate() throws Exception {
    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "validate");
    Map<String, Object> body = new HashMap<>();
    Map<String, String> image = new HashMap<>();
    image.put("value", "ubuntu:latest");
    body.put("IMAGE", image);
    request.setRequestBody(Json.createObjectBuilder(body).build().toString());

    GoPluginApiResponse response = new DockerStepPlugin().handle(request);

    assertEquals("Expected successful response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
    JsonObject errors = Json.createReader(new StringReader(response.responseBody()))
        .readObject().getJsonObject("errors");
    assertEquals("Expected no errors", 0, errors.size());
  }

  @Test
  public void handleExecute() throws Exception {
    TestConsoleLogger logger = new TestConsoleLogger();
    PowerMockito.mockStatic(JobConsoleLogger.class);
    when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

    PowerMockito.mockStatic(DockerUtils.class);
    when(DockerUtils.createNetwork()).thenReturn("test_net");
    when(DockerUtils.startService(anyString(), anyString(), anyMap(), anyString())).thenReturn("123");
    when(DockerUtils.runScript(anyString(), anyString(), anyString(), anyMap(), anyString(), anyString(), any())).thenReturn(0L);

    PowerMockito.mockStatic(File.class);
    when(File.createTempFile(anyString(), anyString(), any(File.class))).thenReturn(new File("/dev/null"));

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    JsonObject requestBody = Json.createObjectBuilder()
            .add("config", Json.createObjectBuilder()
                    .add("image", Json.createObjectBuilder()
                            .add("value", "ubuntu:latest")
                            .build())
                    .add("pull", Json.createObjectBuilder()
                            .add("value", "true")
                            .build())
                    .add("commands", Json.createObjectBuilder()
                            .add("value", "Hello\nWorld\n")
                            .build())
                    .add("services", Json.createObjectBuilder()
                            .add("value", "serv1;debian:test\n")
                            .build())
                    .add("mounts", Json.createObjectBuilder()
                            .add("value", "/cache:/cache\n")
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
    request.setRequestBody(requestBody.toString());
    final GoPluginApiResponse response = new DockerStepPlugin().handle(request);

    PowerMockito.verifyStatic(DockerUtils.class);
    DockerUtils.pullImage("ubuntu:latest");
    DockerUtils.createNetwork();
    Map<String, String> envs = new HashMap<>();
    envs.put("ENV1", "value1");
    envs.put("ENV2", "value2");
    DockerUtils.startService("serv1", "debian:test", envs, null);
    DockerUtils.runScript(eq("ubuntu:latest"), anyString(),
            eq(Paths.get(System.getProperty("user.dir"), "pipelines/test").toAbsolutePath().toString()),
            eq(envs), anyString(), anyString(),
            eq(new String[]{"/cache:/cache"}));
    DockerUtils.removeContainer("123");
    DockerUtils.removeNetwork("test_net");
    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
        response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected success", Boolean.TRUE, responseBody.getBoolean("success"));
    assertEquals("Wrong message", "Container completed with status 0",
        Json.createReader(new StringReader(response.responseBody())).readObject().getString("message"));
    assertEquals("Should se 2 lines of output", logger.logLines.size(), 2);
  }

  @Test
  public void handleExecuteNoPull() throws Exception {
    TestConsoleLogger logger = new TestConsoleLogger();
    PowerMockito.mockStatic(JobConsoleLogger.class);
    when(JobConsoleLogger.getConsoleLogger()).thenReturn(logger);

    PowerMockito.mockStatic(DockerUtils.class);
    when(DockerUtils.startService(anyString(), anyString(), anyMap(), anyString())).thenReturn("123");
    when(DockerUtils.runScript(anyString(), anyString(), anyString(), anyMap(), anyString(), anyString(), any())).thenReturn(0L);

    PowerMockito.mockStatic(File.class);
    when(File.createTempFile(anyString(), anyString(), any(File.class))).thenReturn(new File("/dev/null"));

    DefaultGoPluginApiRequest request = new DefaultGoPluginApiRequest(null, null, "execute");
    JsonObject requestBody = Json.createObjectBuilder()
            .add("config", Json.createObjectBuilder()
                    .add("image", Json.createObjectBuilder()
                            .add("value", "ubuntu:latest")
                            .build())
                    .add("pull", Json.createObjectBuilder()
                            .add("value", "false")
                            .build())
                    .add("commands", Json.createObjectBuilder()
                            .add("value", "Hello\nWorld\n")
                            .build())
                    .add("services", Json.createObjectBuilder()
                            .add("value", "serv1;debian:test\n")
                            .build())
                    .add("mounts", Json.createObjectBuilder()
                            .add("value", "/cache:/cache\n")
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
    request.setRequestBody(requestBody.toString());
    final GoPluginApiResponse response = new DockerStepPlugin().handle(request);

    PowerMockito.verifyStatic(DockerUtils.class, never());
    DockerUtils.pullImage("ubuntu:latest");
    Map<String, String> envs = new HashMap<>();
    envs.put("ENV1", "value1");
    envs.put("ENV2", "value2");
    DockerUtils.startService("serv1", "debian:test", envs, null);
    DockerUtils.runScript(eq("ubuntu:latest"), anyString(),
            eq(Paths.get(System.getProperty("user.dir"), "pipelines/test").toAbsolutePath().toString()),
            eq(envs), anyString(), anyString(), eq(new String[]{"/cache:/cache"}));
    DockerUtils.removeContainer("123");
    assertEquals("Expected 2xx response", DefaultGoPluginApiResponse.SUCCESS_RESPONSE_CODE,
            response.responseCode());
    final JsonObject responseBody = Json.createReader(new StringReader(response.responseBody())).readObject();
    assertEquals("Expected success", Boolean.TRUE, responseBody.getBoolean("success"));
    assertEquals("Wrong message", "Container completed with status 0",
            Json.createReader(new StringReader(response.responseBody())).readObject().getString("message"));
    assertEquals("Should se 2 lines of output", logger.logLines.size(), 2);
  }
}
