package eu.neydev.neyshulker.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Утилита для работы с шалкер-боксами как с предметами.
 * Все методы безопасны к null и не изменяют переданные предметы.
 */
public final class ShulkerUtil {

    public static final int SHULKER_SIZE = 27;

    private static final Set<Material> SHULKER_BOXES = EnumSet.noneOf(Material.class);

    static {

        SHULKER_BOXES.add(Material.SHULKER_BOX);
        SHULKER_BOXES.add(Material.WHITE_SHULKER_BOX);
        SHULKER_BOXES.add(Material.ORANGE_SHULKER_BOX);
        SHULKER_BOXES.add(Material.MAGENTA_SHULKER_BOX);
        SHULKER_BOXES.add(Material.LIGHT_BLUE_SHULKER_BOX);
        SHULKER_BOXES.add(Material.YELLOW_SHULKER_BOX);
        SHULKER_BOXES.add(Material.LIME_SHULKER_BOX);
        SHULKER_BOXES.add(Material.PINK_SHULKER_BOX);
        SHULKER_BOXES.add(Material.GRAY_SHULKER_BOX);
        SHULKER_BOXES.add(Material.LIGHT_GRAY_SHULKER_BOX);
        SHULKER_BOXES.add(Material.CYAN_SHULKER_BOX);
        SHULKER_BOXES.add(Material.PURPLE_SHULKER_BOX);
        SHULKER_BOXES.add(Material.BLUE_SHULKER_BOX);
        SHULKER_BOXES.add(Material.BROWN_SHULKER_BOX);
        SHULKER_BOXES.add(Material.GREEN_SHULKER_BOX);
        SHULKER_BOXES.add(Material.RED_SHULKER_BOX);
        SHULKER_BOXES.add(Material.BLACK_SHULKER_BOX);

    }

    private ShulkerUtil() {

    }

    /**
     * Проверяет, является ли материал шалкер-боксом.
     *
     * @param material проверяемый материал
     * @return true если материал - шалкер-бокс
     */
    public static boolean isShulkerBox(@Nullable Material material) {
        return material != null && SHULKER_BOXES.contains(material);
    }

    /**
     * Проверяет, является ли предмет шалкер-боксом.
     *
     * @param itemStack проверяемый предмет
     * @return true если предмет - шалкер-бокс
     */
    public static boolean isShulkerBox(@Nullable ItemStack itemStack) {
        return itemStack != null && isShulkerBox(itemStack.getType());
    }

    /**
     * Проверяет, что предмет является пустым (null, AIR или нулевое количество).
     *
     * @param itemStack проверяемый предмет
     * @return true если предмет отсутствует
     */
    public static boolean isEmpty(@Nullable ItemStack itemStack) {

        if (itemStack == null || itemStack.getAmount() <= 0) {
            return true;
        }

        // Сравнение по имени материала: не требует реестров Bukkit
        return itemStack.getType().name().endsWith("_AIR")
                || itemStack.getType() == Material.AIR;

    }

    /**
     * Читает содержимое шалкер-бокса из метаданных предмета.
     *
     * @param shulker предмет шалкер-бокса
     * @return массив из 27 слотов (может содержать null) или null, если чтение невозможно
     */
    public static ItemStack @Nullable [] readContents(@Nullable ItemStack shulker) {

        org.bukkit.block.ShulkerBox box = readBox(shulker);

        if (box == null) {
            return null;
        }

        ItemStack[] source = box.getSnapshotInventory().getContents();
        ItemStack[] contents = new ItemStack[SHULKER_SIZE];

        for (int i = 0; i < SHULKER_SIZE && i < source.length; i++) {
            contents[i] = source[i] == null ? null : source[i].clone();
        }

        return contents;

    }

    /**
     * Записывает содержимое в метаданные шалкер-бокса.
     * Исходный предмет не изменяется - метод возвращает новый.
     *
     * @param shulker  предмет шалкер-бокса
     * @param contents новое содержимое (до 27 слотов)
     * @return новый предмет с сохраненным содержимым или null при ошибке
     */
    public static @Nullable ItemStack writeContents(@NotNull ItemStack shulker,
                                                    ItemStack @Nullable [] contents) {

        if (!isShulkerBox(shulker)) {
            return null;
        }

        ItemStack result = shulker.clone();
        ItemMeta itemMeta = result.getItemMeta();

        if (!(itemMeta instanceof BlockStateMeta blockStateMeta)) {
            return null;
        }

        if (!(blockStateMeta.getBlockState() instanceof org.bukkit.block.ShulkerBox box)) {
            return null;
        }

        org.bukkit.inventory.Inventory boxInventory = box.getSnapshotInventory();
        boxInventory.clear();

        if (contents != null) {

            for (int i = 0; i < contents.length && i < SHULKER_SIZE; i++) {

                ItemStack itemStack = contents[i];

                if (!isEmpty(itemStack)) {
                    boxInventory.setItem(i, itemStack.clone());
                }

            }

        }

        blockStateMeta.setBlockState(box);
        result.setItemMeta(blockStateMeta);

        return result;

    }

    /**
     * Считает количество свободных слотов в шалкер-боксе.
     *
     * @param shulker предмет шалкер-бокса
     * @return число свободных слотов (0 если предмет не шалкер-бокс)
     */
    public static int countFreeSlots(@Nullable ItemStack shulker) {

        ItemStack[] contents = readContents(shulker);

        if (contents == null) {
            return 0;
        }

        int freeSlots = 0;

        for (ItemStack itemStack : contents) {
            if (isEmpty(itemStack)) {
                freeSlots++;
            }
        }

        return freeSlots;

    }

    /**
     * Считает общее количество предметов в шалкер-боксе.
     *
     * @param shulker предмет шалкер-бокса
     * @return суммарный размер всех стопок
     */
    public static int countItems(@Nullable ItemStack shulker) {

        ItemStack[] contents = readContents(shulker);

        if (contents == null) {
            return 0;
        }

        int amount = 0;

        for (ItemStack itemStack : contents) {
            if (!isEmpty(itemStack)) {
                amount += itemStack.getAmount();
            }
        }

        return amount;

    }

    /**
     * Возвращает отображаемое имя шалкер-бокса.
     *
     * @param shulker предмет шалкер-бокса
     * @return имя предмета или имя материала, если имя не задано
     */
    public static @NotNull String getShulkerName(@NotNull ItemStack shulker) {

        ItemMeta itemMeta = shulker.getItemMeta();

        if (itemMeta != null && itemMeta.hasDisplayName()) {
            return itemMeta.getDisplayName();
        }

        return prettifyMaterial(shulker.getType());

    }

    /**
     * Приводит имя материала к читаемому виду (WHITE_SHULKER_BOX -> White Shulker Box).
     *
     * @param material материал предмета
     * @return форматированное имя
     */
    public static @NotNull String prettifyMaterial(@NotNull Material material) {

        String[] words = material.name().toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {

            if (word.isEmpty()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(' ');
            }

            result.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());

        }

        return result.toString();

    }

    /**
     * Достает состояние шалкер-бокса из метаданных предмета.
     *
     * @param shulker предмет шалкер-бокса
     * @return состояние блока или null
     */
    private static org.bukkit.block.ShulkerBox readBox(@Nullable ItemStack shulker) {

        if (!isShulkerBox(shulker)) {
            return null;
        }

        ItemMeta itemMeta = shulker.getItemMeta();

        if (!(itemMeta instanceof BlockStateMeta blockStateMeta)) {
            return null;
        }

        if (blockStateMeta.getBlockState() instanceof org.bukkit.block.ShulkerBox box) {
            return box;
        }

        return null;

    }
}
