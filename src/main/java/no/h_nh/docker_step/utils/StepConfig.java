package no.h_nh.docker_step.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;


/**
 * Helper class to hold the configuration of a step in a sane format.
 */
public class StepConfig {
    public final String image;
    public final boolean doPull;
    public final String[] commands;
    public final Map<String, String> services;
    public final String workingDirectory;
    public final Map<String, String> environment;
    public final String[] mounts;

    private StepConfig(String image, boolean doPull, String[] commands, Map<String, String> services,
            String workingDirectory, Map<String, String> environment, String[] mounts) {
        this.image = image;
        this.doPull = doPull;
        this.commands = commands;
        this.services = Collections.unmodifiableMap(services);
        this.workingDirectory = workingDirectory;
        this.environment = Collections.unmodifiableMap(environment);
        this.mounts = mounts;
    }

    public static StepConfig parse(JsonObject request) {
        JsonObject config = request.getJsonObject("config");
        JsonObject context = request.getJsonObject("context");

        String image = getValue(config, "image");
        boolean doPull = getValue(config, "pull").equalsIgnoreCase("true");
        String[] commands = getListValue(config, "commands");
        Map<String, String> services = getMapFromListValue(config, "services");
        Map<String, String> environment = getMapValue(context, "environmentVariables");
        Path wd = Paths.get(System.getProperty("user.dir"), context.getString("workingDirectory"));
        String workingDirectory = wd.toAbsolutePath().toString();
        String[] mounts = getListValue(config, "mounts");

        return new StepConfig(image, doPull, commands, services, workingDirectory, environment, mounts);
    }

    private static String getValue(JsonObject object, String key) {
        return object.getJsonObject(key).getString("value");
    }

    private static String[] getListValue(JsonObject object, String key) {
        final String multiLine = object.getJsonObject(key).getString("value");
        if (multiLine == null)
            return new String[0];
        return multiLine.split("\\r?\\n");
    }

    private static Map<String, String> getMapFromListValue(JsonObject object, String key) {
        final String multiLine = object.getJsonObject(key).getString("value");
        if (multiLine == null)
            return Collections.emptyMap();

        Map<String, String> res = new HashMap<>();
        for (String line : multiLine.split("\\r?\\n")) {
            String[] keyVal = line.split(";", 2);
            if (keyVal.length == 2)
                res.put(keyVal[0], keyVal[1]);
        }
        return res;
    }

    private static Map<String, String> getMapValue(JsonObject object, String key) {
        Map<String, String> res = new HashMap<>();
        for (Map.Entry<String, JsonValue> e : object.getJsonObject(key).entrySet()) {
            res.put(e.getKey(), ((JsonString)e.getValue()).getString());
        }
        return res;
    }
}
