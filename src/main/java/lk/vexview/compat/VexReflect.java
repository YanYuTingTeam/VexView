package lk.vexview.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Timer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

/**
 * Access helpers for members that Minecraft 1.8.9 keeps private.
 *
 * The 1.12.2 source used {@code Minecraft#getRenderPartialTicks()} and
 * {@code GuiScreen#selectedButton} directly.  Neither exists as public API in 1.8.9,
 * so both are reached through Forge's SRG-aware reflection helper, which keeps the
 * behaviour identical while staying correct after the MCP -> SRG reobfuscation step.
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
            try {
                cached = (Timer) ObfuscationReflectionHelper.getPrivateValue(Minecraft.class,
                        Minecraft.getMinecraft(), "timer");
            } catch (Throwable ignored) {
                cached = null;
            }
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
        try {
            ObfuscationReflectionHelper.setPrivateValue(GuiScreen.class, screen, button, "selectedButton");
        } catch (Throwable ignored) {
            // Nothing sensible to do: the click simply will not be drawn as pressed.
        }
    }
}
