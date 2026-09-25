package eu.neydev.neyshulker.util;

import eu.neydev.neyshulker.model.TransferResult;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка транзакционного ядра переноса предметов.
 * Главный инвариант: суммарное количество предметов до и после операции совпадает.
 * Именно нарушение этого инварианта приводило к дюпу в старой версии плагина.
 */
class ItemStackTransactionTest {

    @Test
    @DisplayName("Перенос в пустой слот сохраняет общее количество предметов")
    void moveIntoEmptySlot() {

        ItemStack source = new FakeItemStack(Material.DIAMOND, 32);
        TransferResult result = ItemStackTransaction.move(source, null, 64);

        assertEquals(32, result.transferred());
        assertNull(result.source());
        assertEquals(32, result.destination().getAmount());

    }

    @Test
    @DisplayName("Перенос в неполный стек дозаполняет его до предела")
    void moveIntoPartialStack() {

        ItemStack source = new FakeItemStack(Material.DIAMOND, 40);
        ItemStack destination = new FakeItemStack(Material.DIAMOND, 50);

        TransferResult result = ItemStackTransaction.move(source, destination, 64);

        assertEquals(14, result.transferred());
        assertEquals(26, result.source().getAmount());
        assertEquals(64, result.destination().getAmount());

    }

    @Test
    @DisplayName("Полный стек ничего не принимает")
    void moveIntoFullStack() {

        ItemStack source = new FakeItemStack(Material.DIAMOND, 10);
        ItemStack destination = new FakeItemStack(Material.DIAMOND, 64);

        TransferResult result = ItemStackTransaction.move(source, destination, 64);

        assertEquals(0, result.transferred());
        assertEquals(10, result.source().getAmount());
        assertEquals(64, result.destination().getAmount());

    }

    @Test
    @DisplayName("Разные предметы не смешиваются")
    void moveDifferentMaterials() {

        ItemStack source = new FakeItemStack(Material.DIAMOND, 10);
        ItemStack destination = new FakeItemStack(Material.GOLD_INGOT, 10);

        TransferResult result = ItemStackTransaction.move(source, destination, 64);

        assertEquals(0, result.transferred());
        assertEquals(Material.GOLD_INGOT, result.destination().getType());

    }

    @Test
    @DisplayName("Ограничение maxAmount учитывается при переносе одного предмета")
    void moveSingleItem() {

        ItemStack source = new FakeItemStack(Material.DIAMOND, 32);
        ItemStack destination = new FakeItemStack(Material.DIAMOND, 10);

        TransferResult result = ItemStackTransaction.move(source, destination, 1);

        assertEquals(1, result.transferred());
        assertEquals(31, result.source().getAmount());
        assertEquals(11, result.destination().getAmount());

    }

    @Test
    @DisplayName("Исходные предметы не изменяются - транзакцию можно отменить")
    void sourceIsNeverMutated() {

        ItemStack source = new FakeItemStack(Material.DIAMOND, 32);
        ItemStack destination = new FakeItemStack(Material.DIAMOND, 10);

        ItemStack sourceReference = source;
        ItemStackTransaction.move(source, destination, 64);

        assertSame(sourceReference, source);
        assertEquals(32, source.getAmount());
        assertEquals(10, destination.getAmount());

    }

    @Test
    @DisplayName("Вставка сначала дозаполняет стеки, затем занимает пустые слоты")
    void insertFillsPartialStacksFirst() {

        ItemStack[] slots = new ItemStack[3];
        slots[0] = new FakeItemStack(Material.DIAMOND, 60);
        slots[2] = new FakeItemStack(Material.DIAMOND, 60);

        ItemStack rest = ItemStackTransaction.insert(slots, new FakeItemStack(Material.DIAMOND, 20), 3);

        assertNull(rest);
        assertEquals(64, slots[0].getAmount());
        assertEquals(64, slots[2].getAmount());

    }

    @Test
    @DisplayName("Вставка возвращает остаток, если места не хватило")
    void insertReturnsRest() {

        ItemStack[] slots = new ItemStack[2];
        slots[0] = new FakeItemStack(Material.DIAMOND, 64);
        slots[1] = new FakeItemStack(Material.DIAMOND, 64);

        ItemStack rest = ItemStackTransaction.insert(slots, new FakeItemStack(Material.DIAMOND, 5), 2);
        assertEquals(5, rest.getAmount());

    }

    @Test
    @DisplayName("Максимальный размер стека не превышается (не стакающиеся предметы)")
    void insertRespectsMaxStackSize() {

        ItemStack[] slots = new ItemStack[2];

        ItemStack rest = ItemStackTransaction.insert(slots,
                new FakeItemStack(Material.DIAMOND_SWORD, 3, 1), 2);

        assertEquals(1, rest.getAmount());
        assertEquals(1, slots[0].getAmount());
        assertEquals(1, slots[1].getAmount());

    }

    @RepeatedTest(200)
    @DisplayName("Фазовый инвариант: предметы не появляются из воздуха и не исчезают")
    void insertNeverCreatesOrLosesItems(RepetitionInfo repetition) {

        // Сид = номер повторения: упавшую итерацию можно воспроизвести
        // точным прогоном @RepeatedTest с тем же индексом
        long seed = repetition.getCurrentRepetition();
        Random random = new Random(seed);
        ItemStack[] slots = new ItemStack[5];

        int initialTotal = 0;

        for (int i = 0; i < slots.length; i++) {

            if (random.nextBoolean()) {
                continue;
            }

            int maxStack = random.nextBoolean() ? 64 : 16;
            int amount = 1 + random.nextInt(maxStack);

            slots[i] = new FakeItemStack(Material.DIAMOND, amount, maxStack);
            initialTotal += amount;

        }

        int maxStack = random.nextBoolean() ? 64 : 16;
        ItemStack incoming = new FakeItemStack(Material.DIAMOND, 1 + random.nextInt(maxStack), maxStack);
        int incomingAmount = incoming.getAmount();

        ItemStack rest = ItemStackTransaction.insert(slots, incoming, slots.length);
        int finalTotal = rest == null ? 0 : rest.getAmount();

        for (ItemStack slot : slots) {
            if (slot != null) {
                finalTotal += slot.getAmount();
                assertTrue(slot.getAmount() <= slot.getMaxStackSize(), "Превышен размер стека");
            }
        }

        assertEquals(initialTotal + incomingAmount, finalTotal,
                "Нарушен баланс предметов (seed=" + seed + ")");

    }

    @RepeatedTest(200)
    @DisplayName("Фазовый инвариант переноса между слотами")
    void moveNeverCreatesOrLosesItems(RepetitionInfo repetition) {

        long seed = repetition.getCurrentRepetition();
        Random random = new Random(seed);

        int maxStack = random.nextBoolean() ? 64 : 16;
        int sourceAmount = 1 + random.nextInt(maxStack);
        int destinationAmount = random.nextInt(maxStack + 1);

        ItemStack source = new FakeItemStack(Material.DIAMOND, sourceAmount, maxStack);
        ItemStack destination = destinationAmount == 0
                ? null
                : new FakeItemStack(Material.DIAMOND, destinationAmount, maxStack);

        TransferResult result = ItemStackTransaction.move(source, destination, 64);

        // transferred - это часть итогового стека назначения, отдельно его не считаем
        int total = amountOf(result.source()) + amountOf(result.destination());

        assertEquals(sourceAmount + destinationAmount, total,
                "Нарушен баланс предметов (seed=" + seed + ")");
        assertTrue(result.transferred() <= sourceAmount, "Перемещено больше, чем было");
        assertTrue(amountOf(result.source()) <= maxStack, "Источник превышает размер стека");
        assertTrue(amountOf(result.destination()) <= maxStack, "Назначение превышает размер стека");

        if (result.source() == null) {
            assertEquals(sourceAmount, result.transferred(), "Стек исчез не полностью");
        }

    }

    private int amountOf(ItemStack itemStack) {
        return itemStack == null ? 0 : itemStack.getAmount();
    }

}
