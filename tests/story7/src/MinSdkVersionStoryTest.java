package story7;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public final class MinSdkVersionStoryTest {

    private static final Pattern MIN_SDK_23 = Pattern.compile("(?m)^\\s*minSdkVersion\\s+23\\b");

    private MinSdkVersionStoryTest() {
    }

    public static void run() throws IOException {
        String buildFile = new String(
                Files.readAllBytes(Paths.get("sholi/build.gradle")),
                StandardCharsets.UTF_8);

        if (!MIN_SDK_23.matcher(buildFile).find()) {
            throw new AssertionError("Expected sholi/build.gradle to define minSdkVersion 23");
        }

        if (buildFile.contains("minSdkVersion 14")
                || buildFile.contains("minSdkVersion 15")
                || buildFile.contains("minSdkVersion 16")
                || buildFile.contains("minSdkVersion 17")
                || buildFile.contains("minSdkVersion 18")
                || buildFile.contains("minSdkVersion 19")
                || buildFile.contains("minSdkVersion 20")
                || buildFile.contains("minSdkVersion 21")
                || buildFile.contains("minSdkVersion 22")) {
            throw new AssertionError("Found an unsupported minSdkVersion lower than 23");
        }
    }
}
