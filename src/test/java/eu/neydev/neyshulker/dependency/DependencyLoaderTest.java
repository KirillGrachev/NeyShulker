package eu.neydev.neyshulker.dependency;

import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Интеграционная проверка загрузчика зависимостей на живом HTTP-репозитории.
 * Доказывает весь цикл: скачивание в libs -> проверка SHA-1 -> инъекция ->
 * класс из скачанного jar действительно загружается и работает.
 */
class DependencyLoaderTest {

    private static final Logger LOGGER = Logger.getLogger("NeyShulker-DependencyLoaderTest");

    @TempDir
    Path tempDir;

    private HttpServer server;
    private final Map<String, byte[]> repository = new ConcurrentHashMap<>();
    private final AtomicInteger jarRequests = new AtomicInteger();

    @AfterEach
    void stopServer() {

        if (server != null) {
            server.stop(0);
        }

    }

    @Test
    @DisplayName("Библиотека скачивается в libs, проверяется по SHA-1 и подключается")
    void downloadsVerifiesAndInjects() throws Exception {

        byte[] jar = buildProbeJar();
        startServer(Map.of("probe/probe-lib/1.0/probe-lib-1.0.jar", jar,
                "probe/probe-lib/1.0/probe-lib-1.0.jar.sha1", sha1Hex(jar).getBytes()));

        DependencyLoader loader = loader("probe:probe-lib:1.0@" + repositoryUrl());
        loader.load();

        // Папка libs создана плагином внутри его директории данных
        Path libs = tempDir.resolve("libs");
        assertTrue(Files.isDirectory(libs), "Папка libs не создана");
        assertTrue(Files.isRegularFile(libs.resolve("probe-lib-1.0.jar")), "Jar не скачан");

        List<DependencyLoader.LoadResult> results = loader.getResults();
        assertEquals(1, results.size());
        assertFalse(results.get(0).state() == DependencyLoader.LoadState.FAILED,
                "Библиотека не подключена: " + results.get(0).details());

        // Класс из скачанного jar загружается и выполняется
        URLClassLoader isolated = loader.getIsolatedClassLoader();
        ClassLoader effective = isolated != null
                ? isolated
                : DependencyLoaderTest.class.getClassLoader();

        Class<?> probe = Class.forName("probe.Probe", true, effective);

        assertEquals("pong", probe.getMethod("ping").invoke(null));

        loader.close();

    }

    @Test
    @DisplayName("Повторный запуск не перекачивает валидный jar")
    void reusesCachedJar() throws Exception {

        byte[] jar = buildProbeJar();
        startServer(Map.of("probe/probe-lib/1.0/probe-lib-1.0.jar", jar,
                "probe/probe-lib/1.0/probe-lib-1.0.jar.sha1", sha1Hex(jar).getBytes()));

        loader("probe:probe-lib:1.0@" + repositoryUrl()).load();
        int firstRunRequests = jarRequests.get();
        assertTrue(firstRunRequests >= 1);

        loader("probe:probe-lib:1.0@" + repositoryUrl()).load();

        assertEquals(firstRunRequests, jarRequests.get(), "Jar перекачан повторно");

    }

    @Test
    @DisplayName("Несовпадение контрольной суммы отклоняет библиотеку")
    void rejectsBrokenChecksum() throws Exception {

        byte[] jar = buildProbeJar();
        startServer(Map.of("probe/probe-lib/1.0/probe-lib-1.0.jar", jar,
                "probe/probe-lib/1.0/probe-lib-1.0.jar.sha1",
                "0000000000000000000000000000000000000000".getBytes()));

        DependencyLoader loader = loader("probe:probe-lib:1.0@" + repositoryUrl());
        loader.load();

        assertEquals(DependencyLoader.LoadState.FAILED, loader.getResults().get(0).state());
        assertFalse(Files.exists(tempDir.resolve("libs").resolve("probe-lib-1.0.jar")),
                "Битый jar остался в libs");

    }

    @Test
    @DisplayName("Без объявленных библиотек папка libs не создается")
    void doesNotCreateLibsFolderWhenNoLibraries() {

        DependencyLoader loader = new DependencyLoader(LOGGER, tempDir,
                DependencyLoaderTest.class.getClassLoader(), List.of());

        loader.load();

        assertFalse(Files.exists(tempDir.resolve("libs")),
                "Пустая папка libs создана без необходимости");
        assertTrue(loader.getResults().isEmpty());

    }

    @Test
    @DisplayName("Недоступный репозиторий не роняет загрузчик")
    void survivesUnreachableRepository() {

        int deadPort = findDeadPort();

        DependencyLoader loader = loader("probe:probe-lib:1.0@http://127.0.0.1:" + deadPort + "/repo/");
        loader.load();

        assertEquals(DependencyLoader.LoadState.FAILED, loader.getResults().get(0).state());

    }

    // --- Подготовка окружения ---

    private @NotNull DependencyLoader loader(String coordinates) {
        return new DependencyLoader(LOGGER, tempDir,
                DependencyLoaderTest.class.getClassLoader(), List.of(coordinates));
    }

    private void startServer(Map<String, byte[]> content) throws Exception {

        repository.putAll(content);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/repo/", exchange -> {

            String path = exchange.getRequestURI().getPath().substring("/repo/".length());
            byte[] body = repository.get(path);

            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            if (!path.endsWith(".sha1")) {
                jarRequests.incrementAndGet();
            }

            exchange.sendResponseHeaders(200, body.length);

            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }

            exchange.close();

        });

        server.start();

    }

    private @NotNull String repositoryUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/repo/";
    }

    /**
     * Собирает jar с классом probe.Probe, которого нет в classpath тестов:
     * его загрузка возможна только из скачанного файла.
     */
    private byte[] buildProbeJar() throws Exception {

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assumeTrue(compiler != null, "Нет системного компилятора Java");

        Path sourceDir = tempDir.resolve("probe-src");
        Path classDir = tempDir.resolve("probe-classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classDir);

        Path source = sourceDir.resolve("Probe.java");
        Files.writeString(source,
                "package probe; public class Probe { public static String ping() { return \"pong\"; } }");

        assertEquals(0, compiler.run(null, null, null,
                "-d", classDir.toString(), source.toString()), "probe.Probe не скомпилировался");

        byte[] classBytes = Files.readAllBytes(classDir.resolve("probe").resolve("Probe.class"));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (JarOutputStream jar = new JarOutputStream(buffer)) {
            jar.putNextEntry(new JarEntry("probe/Probe.class"));
            jar.write(classBytes);
            jar.closeEntry();
        }

        return buffer.toByteArray();

    }

    private static @NotNull String sha1Hex(byte[] bytes) throws Exception {

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        StringBuilder builder = new StringBuilder();

        for (byte b : digest.digest(bytes)) {
            builder.append(String.format("%02x", b));
        }

        return builder.toString();

    }

    private static int findDeadPort() {

        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception exception) {
            return 1;
        }

    }
}
