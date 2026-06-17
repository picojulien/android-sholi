package story7;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApplicationIdStoryTest {

    private static final String FORK_APPLICATION_ID = "io.github.picojulien.sholidav";
    private static final String ORIGINAL_APPLICATION_ID = "name.soulayrol.rhaa.sholi";

    private static final Pattern DEFAULT_CONFIG_PATTERN =
            Pattern.compile("(?s)defaultConfig\\s*\\{(.*?)\\n\\s*\\}");
    private static final Pattern APPLICATION_ID_PATTERN =
            Pattern.compile("(?m)^\\s*applicationId\\s+['\\\"]([^'\\\"]+)['\\\"]\\s*$");

    private ApplicationIdStoryTest() {
    }

    public static void run() throws IOException {
        String buildFile = readUtf8("sholi/build.gradle");
        String manifest = readUtf8("sholi/src/main/AndroidManifest.xml");

        String applicationId = extractApplicationId(buildFile);
        if (!FORK_APPLICATION_ID.equals(applicationId)) {
            throw new AssertionError(
                    "Expected applicationId " + FORK_APPLICATION_ID + " but was " + applicationId);
        }

        if (ORIGINAL_APPLICATION_ID.equals(applicationId)) {
            throw new AssertionError("Fork must not install as original ShoLi package");
        }

        assertContains(
                manifest,
                "package=\"name.soulayrol.rhaa.sholi\"",
                "manifest implementation package remains stable");
        assertContains(
                manifest,
                "android:name=\"name.soulayrol.rhaa.sholi.MainActivity\"",
                "launcher activity keeps an absolute implementation class name");
    }

    private static String extractApplicationId(String buildFile) {
        Matcher defaultConfigMatcher = DEFAULT_CONFIG_PATTERN.matcher(buildFile);
        if (!defaultConfigMatcher.find()) {
            throw new AssertionError("Could not find defaultConfig in sholi/build.gradle");
        }

        Matcher applicationIdMatcher = APPLICATION_ID_PATTERN.matcher(defaultConfigMatcher.group(1));
        if (!applicationIdMatcher.find()) {
            throw new AssertionError("Could not find applicationId in sholi/build.gradle defaultConfig");
        }
        return applicationIdMatcher.group(1);
    }

    private static String readUtf8(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void assertContains(String content, String expected, String label) {
        if (!content.contains(expected)) {
            throw new AssertionError("Expected " + label + " to contain " + expected);
        }
    }
}
