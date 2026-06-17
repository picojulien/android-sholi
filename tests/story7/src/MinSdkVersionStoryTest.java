package story7;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MinSdkVersionStoryTest {

    private static final Pattern MIN_SDK_PATTERN =
            Pattern.compile("(?m)^\\s*minSdkVersion\\s+(\\d+)\\b");

    private static final Pattern SECURITY_CRYPTO_DEPENDENCY = Pattern.compile(
            "androidx\\.security:security-crypto:[^'\\\"]+");

    private MinSdkVersionStoryTest() {
    }

    public static void run() throws IOException {
        String buildFile = readUtf8("sholi/build.gradle");
        String changes = readUtf8("CHANGES");

        int minSdk = extractMinSdkVersion(buildFile);
        if (minSdk != 23) {
            throw new AssertionError("Expected minSdkVersion to be 23 but was " + minSdk);
        }

        if (minSdk <= 22) {
            throw new AssertionError("Devices API 14-22 would still be supported");
        }

        if (!SECURITY_CRYPTO_DEPENDENCY.matcher(buildFile).find()) {
            throw new AssertionError(
                    "Expected AndroidX Security Crypto dependency for encrypted credentials");
        }

        assertContains(
                buildFile,
                "verifyStory7MinSdk",
                "Gradle-evaluated minSdk verification task");
        assertContains(
                buildFile,
                "android.defaultConfig.minSdkVersion.apiLevel",
                "Gradle-evaluated minSdk API level");
        assertContains(changes, "Android 6.0/API 23", "compatibility release note");
    }

    private static int extractMinSdkVersion(String buildFile) {
        Matcher matcher = MIN_SDK_PATTERN.matcher(buildFile);
        if (!matcher.find()) {
            throw new AssertionError("Could not find minSdkVersion in sholi/build.gradle");
        }
        return Integer.parseInt(matcher.group(1));
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
