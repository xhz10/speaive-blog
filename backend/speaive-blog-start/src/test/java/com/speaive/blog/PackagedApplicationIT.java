package com.speaive.blog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Testcontainers
class PackagedApplicationIT {
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("speaive_blog_packaged_application_test")
            .withUsername("speaive")
            .withPassword("speaive-test-db-password");

    @TempDir
    Path temporaryDirectory;

    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void packagedApplicationStartsAndReportsHealthy() throws Exception {
        Path applicationJar = Path.of(requiredProperty("packaged.application.jar")).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(applicationJar), () -> "Packaged application JAR not found: " + applicationJar);

        int port = availablePort();
        Path dataDirectory = Files.createDirectories(temporaryDirectory.resolve("data"));
        Path logFile = temporaryDirectory.resolve("packaged-application.log");
        Process application = null;
        try {
            application = startApplication(applicationJar, dataDirectory, logFile, port);
            awaitHealthy(application, logFile, port);
        } finally {
            stop(application);
        }
    }

    private static Process startApplication(
            Path applicationJar,
            Path dataDirectory,
            Path logFile,
            int port
    ) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable().toString(),
                "-jar",
                applicationJar.toString()
        );
        builder.redirectErrorStream(true);
        builder.redirectOutput(logFile.toFile());
        builder.environment().put("SPRING_DATASOURCE_URL", POSTGRES.getJdbcUrl());
        builder.environment().put("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername());
        builder.environment().put("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword());
        builder.environment().put("SPEAIVE_BACKEND_HOST", "127.0.0.1");
        builder.environment().put("SPEAIVE_BACKEND_PORT", Integer.toString(port));
        builder.environment().put("SPEAIVE_DATA_DIR", dataDirectory.toString());
        builder.environment().put("SPEAIVE_IMPORT_DIR", dataDirectory.resolve("inbox").toString());
        builder.environment().put("SPEAIVE_IMPORT_ENABLED", "false");
        return builder.start();
    }

    private static void awaitHealthy(Process application, Path logFile, int port) throws Exception {
        URI healthEndpoint = URI.create("http://127.0.0.1:" + port + "/actuator/health");
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        HttpRequest request = HttpRequest.newBuilder(healthEndpoint)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            if (!application.isAlive()) {
                int exitCode = application.exitValue();
                fail("Packaged application exited before becoming healthy with code " + exitCode
                        + System.lineSeparator() + readLogs(logFile));
            }
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"")) {
                    return;
                }
            } catch (IOException ignored) {
                // The socket is expected to reject connections until the embedded server is listening.
            }
            Thread.sleep(200);
        }

        fail("Packaged application did not become healthy within " + STARTUP_TIMEOUT
                + System.lineSeparator() + readLogs(logFile));
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        }
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + name);
        }
        return value;
    }

    private static String readLogs(Path logFile) {
        try {
            return Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "<no process log>";
        } catch (IOException exception) {
            return "<failed to read process log: " + exception.getMessage() + ">";
        }
    }

    private static void stop(Process application) throws InterruptedException {
        if (application == null || !application.isAlive()) {
            return;
        }
        application.destroy();
        if (!application.waitFor(10, TimeUnit.SECONDS)) {
            application.destroyForcibly();
            if (!application.waitFor(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Packaged application process did not terminate");
            }
        }
    }
}
