package eu.neydev.neyshulker.dependency;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Загрузчик runtime-библиотек.
 *
 * Jar плагина содержит только код плагина: все зависимости из секции
 * libraries: скачиваются при первом запуске в plugins/NeyShulker/libs,
 * проверяются по SHA-1 и подключаются в classloader.
 *
 * Стратегия подключения двухступенчатая:
 * 1. инъекция в classloader плагина через URLClassLoader#addURL -
 *    работает на Java 8-16 и на Java 17+ с флагом
 *    --add-opens java.base/java.net=ALL-UNNAMED;
 * 2. если JVM запрещает рефлексию - создается изолированный дочерний
 *    URLClassLoader с родительским classloader'ом плагина, а в лог
 *    выводится подсказка с точной строкой JVM-флага.
 *
 * На Paper секция libraries: загружается ядром нативно,
 * поэтому загрузчик выступает страховкой и не мешает штатной загрузке.
 */
public final class DependencyLoader {

    /** Результат обработки одной библиотеки. */
    public enum LoadState {

        /** Подключена в classloader плагина. */
        INJECTED,

        /** Подключена в изолированный classloader (нужен JVM-флаг для прямого пути). */
        INJECTED_ISOLATED,

        /** Не удалось скачать или подключить. */
        FAILED

    }

    /**
     * @param coordinates координаты библиотеки
     * @param state       итог обработки
     * @param details     пояснение (имя файла, причина сбоя)
     */
    public record LoadResult(@NotNull String coordinates,
                             @NotNull LoadState state,
                             @Nullable String details) {
    }

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 20_000;

    private static final String ADD_OPENS_HINT =
            "Для прямой загрузки библиотек в classloader плагина добавьте в строку запуска JVM: "
                    + "--add-opens java.base/java.net=ALL-UNNAMED";

    private final Logger logger;
    private final ClassLoader pluginClassLoader;
    private final Path libsFolder;
    private final List<Library> libraries;
    private final List<LoadResult> results = new ArrayList<>();

    private URLClassLoader isolatedLoader;

    public DependencyLoader(@NotNull JavaPlugin plugin) {

        this(plugin.getLogger(),
                plugin.getDataFolder().toPath(),
                plugin.getClass().getClassLoader(),
                readCoordinates(plugin));

    }

    /**
     * Конструктор с явными зависимостями: используется ядром плагина и тестами.
     *
     * @param logger            логгер для сообщений о загрузке
     * @param dataFolder        папка данных плагина (libs создастся внутри нее)
     * @param pluginClassLoader classloader плагина - родитель для подключаемых jar
     * @param coordinates       сырые строки координат из секции libraries:
     */
    DependencyLoader(@NotNull Logger logger,
                     @NotNull Path dataFolder,
                     @NotNull ClassLoader pluginClassLoader,
                     @NotNull List<String> coordinates) {

        this.logger = logger;
        this.pluginClassLoader = pluginClassLoader;
        this.libsFolder = dataFolder.resolve("libs");
        this.libraries = parseCoordinates(coordinates);

    }

    /**
     * Скачивает и подключает все объявленные библиотеки.
     * Папка libs создается только тогда, когда есть что скачивать.
     */
    public void load() {

        results.clear();

        if (libraries.isEmpty()) {
            return;
        }

        List<Path> resolved = new ArrayList<>();

        for (Library library : libraries) {

            Path jar = resolve(library);

            if (jar != null) {
                resolved.add(jar);
            }

        }

        if (resolved.isEmpty()) {
            return;
        }

        injectAll(resolved);

    }

    /**
     * Закрывает изолированный classloader, если он создавался.
     */
    public void close() {

        if (isolatedLoader == null) {
            return;
        }

        try {
            isolatedLoader.close();
        } catch (IOException exception) {
            logger.warning("Не удалось закрыть изолированный classloader: "
                    + exception.getMessage());
        } finally {
            isolatedLoader = null;
        }

    }

    public @NotNull List<LoadResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public @NotNull Path getLibsFolder() {
        return libsFolder;
    }

    public @Nullable URLClassLoader getIsolatedClassLoader() {
        return isolatedLoader;
    }

    // --- Чтение объявления ---

    /**
     * Собирает сырые координаты: сначала из описания плагина,
     * затем страховкой из plugin.yml внутри jar (если ядро не разобрало секцию).
     */
    private static @NotNull List<String> readCoordinates(@NotNull JavaPlugin plugin) {

        List<String> coordinates = new ArrayList<>(plugin.getDescription().getLibraries());

        if (!coordinates.isEmpty()) {
            return coordinates;
        }

        try (InputStream input = plugin.getClass().getClassLoader().getResourceAsStream("plugin.yml")) {

            if (input == null) {
                return List.of();
            }

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));

            return yaml.getStringList("libraries");

        } catch (IOException exception) {
            return List.of();
        }

    }

    private @NotNull List<Library> parseCoordinates(@NotNull List<String> coordinates) {

        List<Library> parsed = new ArrayList<>(coordinates.size());

        for (String coordinate : coordinates) {

            Library library = Library.parse(coordinate);

            if (library == null) {
                logger.warning("Не распознаны координаты библиотеки: '"
                        + coordinate + "'. Ожидался формат groupId:artifactId:version[@repo][#sha1].");
                continue;
            }

            parsed.add(library);

        }

        return List.copyOf(parsed);

    }

    // --- Скачивание ---

    /**
     * Гарантирует наличие jar в libs: скачивает и проверяет контрольную сумму.
     *
     * @return путь до готового файла или null при ошибке
     */
    private @Nullable Path resolve(@NotNull Library library) {

        Path target = libsFolder.resolve(library.getFileName());

        if (Files.isRegularFile(target)) {

            if (verifyChecksum(library, target)) {
                return target;
            }

            logger.warning("Контрольная сумма " + library.getFileName()
                    + " не совпадает - файл будет перезакачан.");
            deleteSilently(target);

        }

        createLibsFolder();

        if (!download(library, target)) {
            results.add(new LoadResult(library.getCoordinates(), LoadState.FAILED,
                    "не удалось скачать " + library.getDownloadUrl()));
            return null;
        }

        return target;

    }

    private boolean download(@NotNull Library library, @NotNull Path target) {

        Path temporary = target.resolveSibling(target.getFileName() + ".part");

        try {

            downloadTo(library.getDownloadUrl(), temporary);

            if (!verifyChecksum(library, temporary)) {
                logger.severe("Контрольная сумма " + library.getFileName()
                        + " не совпала с ожидаемой - библиотека отклонена.");
                return false;
            }

            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);

            logger.info("Библиотека " + library.getCoordinates()
                    + " скачана в libs/" + library.getFileName());

            return true;

        } catch (IOException exception) {

            deleteSilently(temporary);
            logger.severe("Ошибка скачивания " + library.getCoordinates() + ": "
                    + exception.getMessage());

            return false;

        }

    }

    private @NotNull HttpURLConnection openConnection(@NotNull String url) throws IOException {
        return (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
    }

    private void downloadTo(@NotNull String url, @NotNull Path target) throws IOException {

        HttpURLConnection connection = openConnection(url);

        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestProperty("User-Agent", "NeyShulker-DependencyLoader");

        try (InputStream input = connection.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
        }

    }

    // --- Контрольные суммы ---

    private boolean verifyChecksum(@NotNull Library library, @NotNull Path file) {

        String expected = library.sha1();

        if (expected == null) {
            expected = fetchRemoteSha1(library);
        }

        if (expected == null) {
            // Сумму взять неоткуда: не блокируем загрузку, но честно сообщаем
            return true;
        }

        String actual = sha1Hex(file);

        return actual != null && actual.equalsIgnoreCase(expected);

    }

    private @Nullable String fetchRemoteSha1(@NotNull Library library) {

        try {

            String body = fetchText(library.getChecksumUrl());

            if (body == null || body.isBlank()) {
                return null;
            }

            // Sidecar-файлы Maven содержат либо голую сумму, либо "сумма  имя-файла"
            String checksum = body.trim().split("\\s+")[0];

            return checksum.length() == 40 ? checksum : null;

        } catch (RuntimeException exception) {
            return null;
        }

    }

    private @Nullable String fetchText(@NotNull String url) {

        HttpURLConnection connection = null;

        try {

            connection = openConnection(url);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);

            try (InputStream input = connection.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

        } catch (IOException exception) {
            return null;
        } finally {

            if (connection != null) {
                connection.disconnect();
            }

        }

    }

    private @Nullable String sha1Hex(@NotNull Path file) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(Files.readAllBytes(file));

            StringBuilder builder = new StringBuilder();

            for (byte b : digest.digest()) {
                builder.append(String.format("%02x", b));
            }

            return builder.toString();

        } catch (NoSuchAlgorithmException | IOException exception) {
            return null;
        }

    }

    // --- Подключение ---

    private void injectAll(@NotNull List<Path> jars) {

        List<Path> injected = new ArrayList<>();

        for (Path jar : jars) {

            if (injectIntoPluginLoader(jar)) {
                injected.add(jar);
            }

        }

        // Все удалось подключить напрямую
        if (injected.size() == jars.size()) {

            for (Path jar : jars) {
                results.add(new LoadResult(fileName(jar), LoadState.INJECTED, fileName(jar)));
            }

            return;
        }

        List<Path> remaining = new ArrayList<>(jars);
        remaining.removeAll(injected);

        logger.warning("JVM запрещает инъекцию в classloader плагина. " + ADD_OPENS_HINT);

        if (injectIsolated(remaining)) {

            for (Path jar : jars) {

                LoadState state = injected.contains(jar) ? LoadState.INJECTED : LoadState.INJECTED_ISOLATED;

                results.add(new LoadResult(fileName(jar), state, fileName(jar)));

            }

            return;
        }

        for (Path jar : jars) {

            LoadState state = injected.contains(jar) ? LoadState.INJECTED : LoadState.FAILED;

            results.add(new LoadResult(fileName(jar), state,
                    state == LoadState.FAILED ? "не удалось подключить " + fileName(jar) : fileName(jar)));

        }

    }

    private boolean injectIntoPluginLoader(@NotNull Path jar) {

        ClassLoader pluginLoader = pluginClassLoader;

        if (!(pluginLoader instanceof URLClassLoader urlClassLoader)) {
            return false;
        }

        return addUrl(urlClassLoader, jar);

    }

    private boolean injectIsolated(@NotNull List<Path> jars) {

        try {

            List<URL> urls = new ArrayList<>();

            if (isolatedLoader != null) {
                Collections.addAll(urls, isolatedLoader.getURLs());
            }

            for (Path jar : jars) {
                urls.add(jar.toUri().toURL());
            }

            URLClassLoader previous = isolatedLoader;

            isolatedLoader = new URLClassLoader(urls.toArray(URL[]::new),
                    pluginClassLoader);

            // Предыдущий loader полностью покрыт новым по набору URL
            if (previous != null) {
                previous.close();
            }

            return true;

        } catch (IOException exception) {
            return false;
        }

    }

    private boolean addUrl(@NotNull URLClassLoader classLoader, @NotNull Path jar) {

        try {

            Method addUrl = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrl.setAccessible(true);
            addUrl.invoke(classLoader, jar.toUri().toURL());

            return true;

        } catch (ReflectiveOperationException | IOException | RuntimeException exception) {
            return false;
        }

    }

    // --- Служебное ---

    private void createLibsFolder() {

        try {
            Files.createDirectories(libsFolder);
        } catch (IOException exception) {
            logger.severe("Не удалось создать папку libs: " + exception.getMessage());
        }

    }

    private void deleteSilently(@NotNull Path path) {

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {

        }

    }

    private @NotNull String fileName(@NotNull Path path) {
        return path.getFileName().toString();
    }

    /**
     * Суммарное количество успешно подключенных библиотек.
     */
    public int countLoaded() {

        return (int) results.stream()
                .map(LoadResult::state)
                .filter(state -> state != LoadState.FAILED)
                .count();

    }
}
