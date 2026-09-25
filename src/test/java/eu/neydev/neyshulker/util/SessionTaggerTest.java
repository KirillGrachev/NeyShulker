package eu.neydev.neyshulker.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка метки сессии в PersistentDataContainer: чтение, запись, снятие
 * и null-безопасность на предметах без меты.
 */
class SessionTaggerTest {

    private static final NamespacedKey KEY = NamespacedKey.fromString("neyshulker:session");

    /**
     * Мок предмета с рабочей цепочкой meta -> PDC: get возвращает то,
     * что записал set (в пределах одного контейнера).
     */
    private ItemStack taggedCapableItem() {

        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        java.util.Map<NamespacedKey, String> store = new java.util.HashMap<>();

        when(item.clone()).thenReturn(item);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);

        org.mockito.Mockito.doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(pdc).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), any(String.class));

        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(invocation -> store.get(invocation.getArgument(0, NamespacedKey.class)));

        org.mockito.Mockito.doAnswer(invocation -> {
            store.remove(invocation.getArgument(0, NamespacedKey.class));
            return null;
        }).when(pdc).remove(any(NamespacedKey.class));

        return item;

    }

    @Test
    @DisplayName("Метка пишется и читается обратно")
    void tagThenRead() {

        UUID sessionId = UUID.randomUUID();
        ItemStack item = taggedCapableItem();

        ItemStack tagged = SessionTagger.tag(item, sessionId);

        assertEquals(sessionId, SessionTagger.sessionIdOf(tagged));
        assertTrue(SessionTagger.isSession(tagged, sessionId));
        assertFalse(SessionTagger.isSession(tagged, UUID.randomUUID()));
        verify(item).setItemMeta(any(ItemMeta.class));

    }

    @Test
    @DisplayName("strip снимает метку")
    void stripRemovesTag() {

        UUID sessionId = UUID.randomUUID();
        ItemStack tagged = SessionTagger.tag(taggedCapableItem(), sessionId);

        assertEquals(sessionId, SessionTagger.sessionIdOf(tagged));

        ItemStack stripped = SessionTagger.strip(tagged);

        assertNull(SessionTagger.sessionIdOf(stripped));
        assertFalse(SessionTagger.isSession(stripped, sessionId));

    }

    @Test
    @DisplayName("Предмет без меты трактуется как немеченый, без падений")
    void metalessItemsAreSafe() {

        ItemStack fake = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);

        assertNull(SessionTagger.sessionIdOf(fake));
        assertFalse(SessionTagger.isSession(fake, UUID.randomUUID()));
        assertNull(SessionTagger.sessionIdOf(null));
        assertNull(SessionTagger.tag(null, UUID.randomUUID()));

        // tag/strip на предмете без меты не падают и возвращают предмет
        org.junit.jupiter.api.Assertions.assertNotNull(SessionTagger.tag(fake, UUID.randomUUID()));
        org.junit.jupiter.api.Assertions.assertSame(fake, SessionTagger.strip(fake));

    }

    @Test
    @DisplayName("Битая строка метки не роняет чтение")
    void brokenTagValueIsIgnored() {

        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenReturn("не-uuid");

        assertNull(SessionTagger.sessionIdOf(item));
        verify(item, never()).setItemMeta(any());

    }


    @Test
    @DisplayName("Падающий getItemMeta трактуется как отсутствие метки")
    void throwingMetaIsTreatedAsUntagged() {

        ItemStack broken = mock(ItemStack.class);

        when(broken.getItemMeta()).thenThrow(new IllegalStateException("no server"));

        assertNull(SessionTagger.sessionIdOf(broken));
        assertFalse(SessionTagger.isSession(broken, UUID.randomUUID()));

    }

}
