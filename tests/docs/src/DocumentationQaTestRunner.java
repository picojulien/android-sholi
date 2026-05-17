package docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DocumentationQaTestRunner {

    private final List<String> failures = new ArrayList<String>();

    public static void main(String[] args) throws IOException {
        DocumentationQaTestRunner runner = new DocumentationQaTestRunner();
        runner.run();
    }

    private void run() throws IOException {
        expect("CHANGES", "release notes describe WebDAV sync release",
                "version 1.6.0",
                "webdav synchronization",
                "android 6.0/api 23",
                "encrypted",
                "conflict");

        expect("README.md", "README compatibility and features mention sync-capable support",
                "webdav synchronization",
                "android 6.0/api 23");

        expect("doc/manual.md", "English manual documents WebDAV setup and sync behavior",
                "webdav synchronization",
                "android 6.0/api 23",
                "webdav url",
                "username",
                "password or app-specific token",
                "remote file path",
                "device/user display name",
                "stored encrypted",
                "app-specific token",
                "no plaintext fallback",
                "test connection",
                "non-https",
                "manual synchronization",
                "success",
                "failure",
                "conflict",
                "local",
                "remote",
                "changed fields",
                "timestamps",
                "status",
                "deletion",
                "modifier",
                "etag",
                "blind overwrites");

        expect("doc/manual_es.md", "Spanish manual includes concise WebDAV sync update",
                "sincronización webdav",
                "android 6.0/api 23",
                "url webdav",
                "usuario",
                "contraseña o token",
                "ruta remota",
                "nombre visible",
                "cifrada",
                "probar conexión",
                "no https",
                "conflicto",
                "etag");

        if (!failures.isEmpty()) {
            for (String failure: failures) {
                System.err.println(failure);
            }
            throw new AssertionError("Documentation QA failed with " + failures.size() + " missing item(s)");
        }

        System.out.println("Documentation QA passed");
    }

    private void expect(String file, String description, String... requiredPhrases) throws IOException {
        String text = readLowercase(file);
        for (String phrase: requiredPhrases) {
            if (!text.contains(phrase.toLowerCase(Locale.US))) {
                failures.add(file + " missing '" + phrase + "' for " + description);
            }
        }
    }

    private String readLowercase(String file) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(file));
        return new String(bytes, StandardCharsets.UTF_8).toLowerCase(Locale.US);
    }
}
