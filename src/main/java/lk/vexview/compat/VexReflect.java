package lk.vexview.compat;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Timer;

/**
 * Access helpers for members that Minecraft 1.8.9 keeps private.
 *
 * The 1.12.2 source used {@code Minecraft#getRenderPartialTicks()} and
 * {@code GuiScreen#selectedButton} directly; neither is public API in 1.8.9.
 *
 * The fields are located <b>by type</b>, not by name: at runtime the mod runs
 * against SRG-named members ({@code field_71428_T}, {@code field_146290_a}) while
 * the development workspace uses MCP names, and looking a field up by one of those
 * names breaks in the other namespace.  Searching {@code Minecraft} for its single
 * {@link Timer} field, and {@code GuiScreen} for its single {@link GuiButton} field,
 * is namespace independent.
 */
public final class VexReflect {

    private static Timer timer;
    private static boolean timerUnavailable;

    private VexReflect() {
    }

    /** Frame interpolation factor, identical to {@code Minecraft#getRenderPartialTicks()}. */
    public static float partialTicks() {
        Timer cached = timer;
        if (cached == null) {
            if (timerUnavailable) {
                return 0.0F;
            }
            cached = (Timer) readField(Minecraft.class, Minecraft.getMinecraft(), Timer.class);
            if (cached == null) {
                timerUnavailable = true;
                return 0.0F;
            }
            timer = cached;
        }
        return cached.renderPartialTicks;
    }

    /** Replaces {@code this.selectedButton = button;} which 1.8.9 keeps private. */
    public static void setSelectedButton(GuiScreen screen, GuiButton button) {
        writeField(GuiScreen.class, screen, GuiButton.class, button);
    }

    // ---------------------------------------------------------------- helpers

    private static Object readField(Class<?> owner, Object instance, Class<?> fieldType) {
        try {
            Field field = findField(owner, fieldType);
            return field == null ? null : field.get(instance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeField(Class<?> owner, Object instance, Class<?> fieldType, Object value) {
        try {
            Field field = findField(owner, fieldType);
            if (field != null) {
                field.set(instance, value);
            }
        } catch (Throwable ignored) {
            // Nothing sensible to do: the click simply will not be drawn as pressed.
        }
    }

    /** First declared field of the given type, walking up superclasses. */
    private static Field findField(Class<?> owner, Class<?> fieldType) {
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                if (field.getType() == fieldType) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        return null;
    }
}
