/*
 * Decompiled with CFR 0.152.
 */
package lk.vexview.potion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lk.vexview.VexView;
import lk.vexview.engine.BufferImageLib;
import lk.vexview.potion.CustomPotion;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.PotionEffect;

public class PotionTools {
    /*
     * Forge 1.8.9 freezes the potion registry after preInit, so the slots are created once
     * from VexView#preload and only re-pointed at a new icon/name later.
     */
    private static final List<CustomPotion> s4 = new ArrayList<CustomPotion>();          // free beneficial slots
    private static final List<CustomPotion> s5 = new ArrayList<CustomPotion>();          // free harmful slots
    private static final Map<String, CustomPotion> s6 = new HashMap<String, CustomPotion>(); // "B:/G:" + name -> slot
    private static boolean poolInitialized;

    /** Creates the pool. Must run during preInit, before the Forge registries freeze. */
    public static synchronized void m0() {
        if (poolInitialized) {
            return;
        }
        for (int n = 0; n < 32; ++n) {
            s4.add(new CustomPotion("", "", false, 0xFFFFFF));
            s5.add(new CustomPotion("", "", true, 0xFFFFFF));
        }
        poolInitialized = true;
    }

    /** Returns the named slot, or null if no slot of the requested kind is available. */
    private static synchronized CustomPotion m4(String string, String string2, boolean bl) {
        String string3 = (bl ? "B:" : "G:") + string2;
        CustomPotion customPotion = s6.get(string3);
        if (customPotion == null) {
            List<CustomPotion> list = bl ? s5 : s4;
            if (list.isEmpty()) {
                return null;
            }
            customPotion = list.remove(list.size() - 1);
            s6.put(string3, customPotion);
        }
        customPotion.m2(string, string2);
        return customPotion;
    }

    public static void m1(String string) {
        net.minecraft.network.NetworkManager owner = VexView.imageConnection();
        VexView.m2(() -> {
            if (BufferImageLib.s4.containsKey(string)) return true;
            java.awt.image.BufferedImage image = BufferImageLib.readImage(string, lk.vexview.engine.ImageReadLimits::read);
            if (image == null) return false;
            BufferImageLib.storeImageForConnection(string, image, owner);
            return true;
        });
    }

    private static /* synthetic */ void m2(String string, String string2, int n, int n2, boolean bl) {
        CustomPotion customPotion = PotionTools.m4(string, string2, bl);
        if (customPotion == null) {
            return;
        }
        PotionTools.m1(string);
        Minecraft.getMinecraft().thePlayer.removePotionEffectClient(customPotion.getId());
        Minecraft.getMinecraft().thePlayer.addPotionEffect(new PotionEffect(customPotion.getId(), n, n2));
    }

    public static void m3(String string) {
        String[] stringArray = string.split("<&>");
        PotionTools.m2(stringArray[0], stringArray[1], Integer.valueOf(stringArray[2]), Integer.valueOf(stringArray[3]), Boolean.valueOf(stringArray[4]));
    }
}
