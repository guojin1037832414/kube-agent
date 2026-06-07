package com.atlas.contract;

import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.defaults.DefaultValueRegistry;
import com.atlas.tool.impl.NimCreateTool;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.21-80 guard for {@code nim_create} defaults and intent metadata.
 *
 * <p>The UI/form defaults are useful for drafts, but they must never become release evidence,
 * confirmation evidence, or an HTTP write shortcut.</p>
 */
class M521NimCreateDefaultsIntentHoldContractTest {

    private static final Path DEFAULTS_YML = Path.of("src/main/resources/defaults.yml");
    private static final Path INTENTS_YML = Path.of("src/main/resources/intents.yml");
    private static final Path NIM_CREATE_TOOL = Path.of(
        "src/main/java/com/atlas/tool/impl/NimCreateTool.java");

    private static final Set<String> NIM_CREATE_FORM_DEFAULT_KEYS = Set.of(
        "gpuPercentLimits",
        "replicas",
        "enableWebSsh"
    );

    private static final Set<String> FORBIDDEN_CONTROL_KEYS = Set.of(
        "apikey",
        "ngcapikey",
        "nvaieapikey",
        "token",
        "secret",
        "password",
        "authorization",
        "headers",
        "httpmethod",
        "apiendpoints",
        "operationtype",
        "requiresconfirmation",
        "httpclient",
        "apiendpoint",
        "sideeffect",
        "nextsideeffectifexecuted",
        "approved",
        "confirmed",
        "hitlconfirmed",
        "hitlconfirmation",
        "safetopost",
        "writepermitted",
        "writeexecutionallowed",
        "realhttpexecutionallowed",
        "releaseeligible",
        "releasedecision",
        "releasecredential",
        "releasecredentialissued",
        "validationresult",
        "creationgate",
        "trustedpolicysnapshot",
        "trustedpolicysource",
        "auditprepared",
        "auditreceipt",
        "auditreceiptprepared",
        "receiptstatus",
        "receiptid",
        "durablewriteexecutorreport",
        "nimcreatereleased",
        "codereleaseswitch",
        "codereleaseswitchopened",
        "codereleaseswitchdigest",
        "sourceguardinstalled",
        "backendquerysourceallowedforrelease",
        "syslogbackfillsourceallowed",
        "writeattempted",
        "writeexecuted",
        "postwritereadinesstriggered",
        "deploymentid",
        "deploymentuid",
        "organizationid",
        "orgid",
        "userid",
        "role",
        "roles",
        "sysadmin",
        "issysorg",
        "licensevalid",
        "nvaielicensevalid",
        "nvaielicenseverified",
        "fallbacktool",
        "usefallback"
    );

    @Test
    void defaultsYml_nimCreateDefaultsShouldRemainFormDraftOnly()
        throws IOException {
        Map<String, Object> root = readYaml(DEFAULTS_YML);
        Map<String, Object> defaults = objectMap(root.get("defaults"));
        Map<String, Object> nimCreateDefaults = objectMap(defaults.get("nim_create"));

        assertThat(nimCreateDefaults)
            .containsOnlyKeys(NIM_CREATE_FORM_DEFAULT_KEYS)
            .containsEntry("gpuPercentLimits", 100)
            .containsEntry("replicas", 1)
            .containsEntry("enableWebSsh", true);
        assertNoForbiddenControlKeys("defaults.yml nim_create", nimCreateDefaults.keySet());
    }

    @Test
    void intentsYml_nimCreateMetadataShouldNotExposeReleaseOrConfirmationClaims()
        throws IOException {
        Map<String, Object> root = readYaml(INTENTS_YML);
        Map<String, Object> intents = objectMap(root.get("intents"));
        Map<String, Object> nimCreateIntent = objectMap(intents.get("nim_create"));
        List<Map<String, Object>> parameters = listOfMaps(nimCreateIntent.get("parameters"));
        List<String> parameterNames = parameters.stream()
            .map(parameter -> text(parameter.get("name")))
            .toList();

        assertThat(nimCreateIntent)
            .containsEntry("agent", "deploy")
            .containsEntry("level", "p1");
        assertThat(parameterNames)
            .contains("name", "model", "gpuPercentLimits");

        assertNoForbiddenControlKeys("intents.yml nim_create", nimCreateIntent.keySet());
        assertNoForbiddenControlKeys("intents.yml nim_create parameters", parameterNames);
    }

    @Test
    void nimCreateToolEntry_shouldNotOptIntoDefaultInjectionWhilePlaceholderIsHeld()
        throws IOException {
        String source = Files.readString(NIM_CREATE_TOOL, StandardCharsets.UTF_8);

        assertThat(source)
            .doesNotContain("@WithDefaults")
            .doesNotContain("DefaultValueApplier")
            .doesNotContain("DefaultValueRegistry");
    }

    @Test
    @SuppressWarnings("unchecked")
    void filledNimCreateDefaults_shouldStillExecuteAsUnsupportedPlaceholder() {
        DefaultValueRegistry registry = new DefaultValueRegistry();
        registry.load();

        Map<String, Object> callerParams = new LinkedHashMap<>();
        callerParams.put("name", "llama-nim");
        callerParams.put("model", "llama");
        callerParams.put("safeToPost", true);
        callerParams.put("writePermitted", true);
        callerParams.put("releaseEligible", true);
        callerParams.put("writeExecutionAllowed", true);
        callerParams.put("releaseDecision", Map.of("accepted", true));
        callerParams.put("confirmed", true);

        Map<String, Object> filledParams = registry.apply("nim_create", callerParams);

        assertThat(filledParams)
            .containsEntry("gpuPercentLimits", 100)
            .containsEntry("replicas", 1)
            .containsEntry("enableWebSsh", true);

        Map<String, Object> result = new NimCreateTool().execute(filledParams);

        assertThat(result)
            .containsEntry(AtlasToolResult.KEY_SUCCESS, false)
            .containsEntry(AtlasToolResult.KEY_ERROR_CODE, "UNSUPPORTED_BACKEND_OPERATION");

        Map<String, Object> data = (Map<String, Object>) result.get(AtlasToolResult.KEY_DATA);
        Map<String, Object> stateMachine = (Map<String, Object>) data.get("stateMachine");
        List<String> ignoredKeys = listOfMaps(stateMachine.get("ignoredCallerClaims")).stream()
            .map(claim -> text(claim.get("key")))
            .toList();

        assertThat(stateMachine)
            .containsEntry("state", "HELD")
            .containsEntry("writePermitted", false)
            .containsEntry("sideEffect", "NONE")
            .containsEntry("directPreviewReuseAllowed", false)
            .containsEntry("fallbackWriteAllowed", false)
            .containsEntry("backendQuerySourceAllowedForRelease", false)
            .containsEntry("sysLogBackfillSourceAllowed", false);
        assertThat(ignoredKeys)
            .contains(
                "safeToPost",
                "writePermitted",
                "releaseEligible",
                "writeExecutionAllowed",
                "releaseDecision",
                "confirmed"
            );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYaml(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            assertThat(loaded).as(path + " should parse as a YAML map").isInstanceOf(Map.class);
            return objectMap(loaded);
        }
    }

    private Map<String, Object> objectMap(Object raw) {
        assertThat(raw).isInstanceOf(Map.class);
        Map<?, ?> rawMap = (Map<?, ?>) raw;
        Map<String, Object> copy = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private List<Map<String, Object>> listOfMaps(Object raw) {
        assertThat(raw).isInstanceOf(List.class);
        return ((List<?>) raw).stream()
            .map(this::objectMap)
            .toList();
    }

    private void assertNoForbiddenControlKeys(String source, Collection<String> keys) {
        List<String> violations = keys.stream()
            .filter(key -> FORBIDDEN_CONTROL_KEYS.contains(normalizeKey(key)))
            .toList();
        assertThat(violations)
            .as("%s must stay UI/form metadata only", source)
            .isEmpty();
    }

    private String normalizeKey(String key) {
        return key == null
            ? ""
            : key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
