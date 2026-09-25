package eu.neydev.neyshulker.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Тестовая реализация ItemStack без сервера.
 * Позволяет проверять транзакционную логику переноса предметов в обычном JUnit.
 */
public class FakeItemStack extends ItemStack {

    private final Material material;
    private final int maxStackSize;
    private int amount;

    public FakeItemStack(@NotNull Material material, int amount) {
        this(material, amount, 64);
    }

    public FakeItemStack(@NotNull Material material, int amount, int maxStackSize) {

        this.material = material;
        this.amount = amount;
        this.maxStackSize = maxStackSize;

    }

    @Override
    public @NotNull Material getType() {
        return material;
    }

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public int getMaxStackSize() {
        return maxStackSize;
    }

    @Override
    public boolean isSimilar(@Nullable ItemStack other) {
        return other instanceof FakeItemStack fake
                && fake.material == material
                && fake.maxStackSize == maxStackSize;
    }

    @Override
    public @NotNull ItemStack clone() {
        return new FakeItemStack(material, amount, maxStackSize);
    }

    @Override
    public String toString() {
        return material.name() + " x" + amount + "/" + maxStackSize;
    }

}
