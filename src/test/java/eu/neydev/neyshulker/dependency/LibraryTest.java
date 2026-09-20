package eu.neydev.neyshulker.dependency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка разбора координат runtime-библиотек.
 */
class LibraryTest {

    @Test
    @DisplayName("Базовые координаты разбираются с репозиторием по умолчанию")
    void parsesPlainCoordinates() {

        Library library = Library.parse("com.zaxxer:HikariCP:5.1.0");

        assertNotNull(library);
        assertEquals("com.zaxxer", library.groupId());
        assertEquals("HikariCP", library.artifactId());
        assertEquals("5.1.0", library.version());
        assertNull(library.repository());
        assertNull(library.sha1());
        assertEquals("HikariCP-5.1.0.jar", library.getFileName());
        assertTrue(library.getDownloadUrl().startsWith(Library.DEFAULT_REPOSITORY));
        assertTrue(library.getDownloadUrl().endsWith("com/zaxxer/HikariCP/5.1.0/HikariCP-5.1.0.jar"));

    }

    @Test
    @DisplayName("Репозиторий и sha1 разбираются из модификаторов")
    void parsesRepositoryAndChecksum() {

        Library library = Library.parse("org.example:lib:1.2@https://repo.example.org/maven2/"
                + "#0123456789abcdef0123456789abcdef01234567");

        assertNotNull(library);
        assertEquals("https://repo.example.org/maven2/", library.repository());
        assertEquals("0123456789abcdef0123456789abcdef01234567", library.sha1());
        assertEquals("https://repo.example.org/maven2/org/example/lib/1.2/lib-1.2.jar",
                library.getDownloadUrl());
        assertEquals(library.getDownloadUrl() + ".sha1", library.getChecksumUrl());

    }

    @Test
    @DisplayName("Репозиторий без завершающего слэша нормализуется")
    void normalizesRepositorySlash() {

        Library library = Library.parse("org.example:lib:1.2@https://repo.example.org/maven2");

        assertNotNull(library);
        assertEquals("https://repo.example.org/maven2/org/example/lib/1.2/lib-1.2.jar",
                library.getDownloadUrl());

    }

    @Test
    @DisplayName("Некорректные координаты отклоняются")
    void rejectsInvalidCoordinates() {

        assertNull(Library.parse(null));
        assertNull(Library.parse(""));
        assertNull(Library.parse("   "));
        assertNull(Library.parse("only:two"));
        assertNull(Library.parse("a:b:c:d"));
        assertNull(Library.parse(":b:1.0"));
        assertNull(Library.parse("a::1.0"));
        assertNull(Library.parse("a:b:"));
        assertNull(Library.parse("a:b:1.0#short"));
        assertNull(Library.parse("a:b:1.0@"));

    }

    @Test
    @DisplayName("Координаты собираются обратно в строку")
    void buildsCoordinatesString() {

        Library library = Library.parse("com.zaxxer:HikariCP:5.1.0");

        assertNotNull(library);
        assertEquals("com.zaxxer:HikariCP:5.1.0", library.getCoordinates());

    }
}
