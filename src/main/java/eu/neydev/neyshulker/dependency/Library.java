package eu.neydev.neyshulker.dependency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Описание runtime-библиотеки из секции libraries: в plugin.yml.
 *
 * Формат координат: groupId:artifactId:version[@repository][#sha1]
 * Примеры:
 *   com.zaxxer:HikariCP:5.1.0
 *   com.zaxxer:HikariCP:5.1.0@https://my.repo/maven2/
 *   com.zaxxer:HikariCP:5.1.0#9b3d4c2a1f0e5d6c7b8a998877665544332211aa
 *
 * @param groupId    группа Maven
 * @param artifactId артефакт Maven
 * @param version    версия
 * @param repository репозиторий (по умолчанию Maven Central)
 * @param sha1       ожидаемая контрольная сумма (null - проверять по sidecar-файлу .sha1)
 */
public record Library(@NotNull String groupId,
                      @NotNull String artifactId,
                      @NotNull String version,
                      @Nullable String repository,
                      @Nullable String sha1) {

    public static final String DEFAULT_REPOSITORY = "https://repo1.maven.org/maven2/";

    /**
     * Разбирает строку координат из plugin.yml.
     *
     * @param coordinate строка вида groupId:artifactId:version[@repo][#sha1]
     * @return библиотека или null, если формат не распознан
     */
    public static @Nullable Library parse(@Nullable String coordinate) {

        if (coordinate == null || coordinate.isBlank()) {
            return null;
        }

        String rest = coordinate.trim();
        String sha1 = null;

        int hashIndex = rest.indexOf('#');

        if (hashIndex >= 0) {
            sha1 = rest.substring(hashIndex + 1).trim();
            rest = rest.substring(0, hashIndex);
        }

        String repository = null;
        int atIndex = rest.indexOf('@');

        if (atIndex >= 0) {

            repository = rest.substring(atIndex + 1).trim();
            rest = rest.substring(0, atIndex);

            // Пустой модификатор репозитория - ошибка формата, а не "репозиторий по умолчанию"
            if (repository.isEmpty()) {
                return null;
            }

        }

        String[] parts = rest.split(":");

        if (parts.length != 3) {
            return null;
        }

        String groupId = parts[0].trim();
        String artifactId = parts[1].trim();
        String version = parts[2].trim();

        if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
            return null;
        }

        if (sha1 != null && !isSha1Hex(sha1)) {
            return null;
        }

        return new Library(groupId, artifactId, version, repository, sha1);

    }

    public @NotNull String getCoordinates() {
        return groupId + ":" + artifactId + ":" + version;
    }

    public @NotNull String getFileName() {
        return artifactId + "-" + version + ".jar";
    }

    /**
     * Собирает прямой URL до jar в репозитории.
     */
    public @NotNull String getDownloadUrl() {

        String base = repository == null ? DEFAULT_REPOSITORY : repository;

        if (!base.endsWith("/")) {
            base += "/";
        }

        return base
                + groupId.replace('.', '/') + '/'
                + artifactId + '/'
                + version + '/'
                + getFileName();

    }

    /**
     * URL sidecar-файла с контрольной суммой.
     */
    public @NotNull String getChecksumUrl() {
        return getDownloadUrl() + ".sha1";
    }

    private static boolean isSha1Hex(@NotNull String value) {

        if (value.length() != 40) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {

            char c = Character.toLowerCase(value.charAt(i));

            if (!Character.isDigit(c) && (c < 'a' || c > 'f')) {
                return false;
            }

        }

        return true;

    }
}
