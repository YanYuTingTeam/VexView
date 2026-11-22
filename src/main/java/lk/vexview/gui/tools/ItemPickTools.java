/*
 * Decompiled with CFR 0.152.
 */
package lk.vexview.gui.tools;

import java.util.Iterator;
import lk.vexview.gui.VexCustomGui;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class ItemPickTools {
    public static ItemStack s1 = null;

    public static void m1(int n, int n2, int n3, Container container, VexCustomGui vexCustomGui) {
        Iterator iterator = container.inventorySlots.iterator();
        while (iterator.hasNext()) {
            Slot slot;
            Slot slot2 = slot = (Slot)iterator.next();
            if (!vexCustomGui.isPointInRegion(slot2.xDisplayPosition, slot2.yDisplayPosition, 16, 16, n, n2)) continue;
            if (n3 == 0) {
                ItemPickTools.m3(slot, container);
                continue;
            }
            if (n3 != 1) continue;
            ItemPickTools.m2(slot, container);
        }
    }

    static void m2(Slot slot, Container container) {
        if (s1 != null && !ItemPickTools.m4(s1, null)) {
            if (s1.stackSize > 1) {
                if (ItemPickTools.m4(slot.getStack(), null)) {
                    s1.stackSize = s1.stackSize - 1;
                    Slot slot2 = slot;
                    slot2.putStack(s1.copy());
                    slot2.getStack().stackSize = 1;
                    return;
                }
                if (ItemPickTools.m4(slot.getStack(), s1) && slot.getStack().stackSize + 1 <= 64) {
                    if (s1.stackSize - 1 > 0) {
                        s1.stackSize = s1.stackSize - 1;
                        slot.getStack().stackSize = slot.getStack().stackSize + 1;
                        return;
                    }
                    if (s1.stackSize == 1) {
                        s1 = null;
                        slot.getStack().stackSize = slot.getStack().stackSize + 1;
                        return;
                    }
                }
            }
        } else if (!ItemPickTools.m4(slot.getStack(), null) && slot.getStack().stackSize > 1) {
            Slot slot3 = slot;
            int n = slot3.getStack().stackSize;
            Slot slot4 = slot;
            slot3.getStack().stackSize = slot4.getStack().stackSize / 2;
            s1 = slot4.getStack().copy();
            s1.stackSize = n - slot.getStack().stackSize;
        }
    }

    static void m3(Slot slot, Container container) {
        if (s1 == null) {
            Slot slot2 = slot;
            s1 = slot2.getStack().copy();
            slot2.putStack(null);
            return;
        }
        if (ItemPickTools.m4(slot.getStack(), s1) && !slot.getStack().isItemStackDamageable() && !s1.isItemStackDamageable()) {
            if (slot.getStack().stackSize < 64) {
                if (slot.getStack().stackSize + s1.stackSize > 64) {
                    Slot slot3 = slot;
                    int n = slot3.getStack().stackSize;
                    slot3.getStack().stackSize = s1.stackSize;
                    s1.stackSize = n;
                    return;
                }
                slot.getStack().stackSize = slot.getStack().stackSize + s1.stackSize;
                s1 = null;
                return;
            }
        } else {
            Slot slot4 = slot;
            ItemStack itemStack = slot4.getStack().copy();
            slot4.putStack(s1.copy());
            s1 = itemStack;
        }
    }

    /*
     * 1.12.2 compared against the ItemStack.EMPTY sentinel; 1.8.9's equivalent of an empty
     * stack is null, so the sentinel comparison is reproduced explicitly.  Every call site
     * that used to pass ItemStack.EMPTY now passes null.
     */
    public static boolean m4(ItemStack itemStack, ItemStack itemStack2) {
        boolean bl = itemStack == null;
        boolean bl2 = itemStack2 == null;
        if (bl || bl2) {
            return bl && bl2;
        }
        if (itemStack.hasTagCompound()) {
            if (itemStack2.hasTagCompound()) {
                return itemStack.getTagCompound().equals((Object)itemStack2.getTagCompound());
            }
            return false;
        }
        if (itemStack2.hasTagCompound()) {
            return false;
        }
        return itemStack.getItem().getUnlocalizedName().equals(itemStack2.getItem().getUnlocalizedName());
    }
}

