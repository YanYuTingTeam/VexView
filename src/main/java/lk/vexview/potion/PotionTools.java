/*
 * Decompiled with CFR 0.152.
 */
package lk.vexview.potion;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import lk.vexview.VexConfig;
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

    /** Creates the pool. Must run during preInit, before the Forge registries freeze. */
    public static void m0() {
        if (!s4.isEmpty() || !s5.isEmpty()) {
            return;
        }
        for (int n = 0; n < 32; ++n) {
            s4.add(new CustomPotion("", "", false, 0xFFFFFF));
            s5.add(new CustomPotion("", "", true, 0xFFFFFF));
        }
    }

    /** Slot for the given potion name, re-pointed at the image the packet described. */
    private static CustomPotion m4(String string, String string2, boolean bl) {
        PotionTools.m0();
        String string3 = (bl ? "B:" : "G:") + string2;
        CustomPotion customPotion = s6.get(string3);
        if (customPotion == null) {
            List<CustomPotion> list = bl ? s5 : s4;
            customPotion = !list.isEmpty() ? list.remove(list.size() - 1) : (bl ? s5.get(0) : s4.get(0));
            s6.put(string3, customPotion);
        }
        customPotion.m2(string, string2);
        return customPotion;
    }

    public static void m1(String string) {
        VexView.m2(() -> {
            InputStream inputStream;
            HttpURLConnection httpURLConnection;
            block4: {
                FileInputStream fileInputStream;
                try {
                    if (BufferImageLib.s4.containsKey(string)) return true;
                }
                catch (Exception exception) {
                    return false;
                }
                if (!string.contains("[local]")) break block4;
                File file = new File(VexConfig.s1, string.replace("[local]", ""));
                FileInputStream fileInputStream2 = fileInputStream = new FileInputStream(file);
                BufferImageLib.m8(string, ImageIO.read(fileInputStream2));
                ((InputStream)fileInputStream2).close();
                return true;
            }
            URL uRL = new URL(string);
            HttpURLConnection httpURLConnection2 = httpURLConnection = (HttpURLConnection)uRL.openConnection();
            httpURLConnection2.setRequestMethod("GET");
            httpURLConnection2.setConnectTimeout(3000);
            InputStream inputStream2 = inputStream = httpURLConnection2.getInputStream();
            BufferImageLib.m8(string, ImageIO.read(inputStream2));
            inputStream2.close();
            return true;
        });
    }

    private static /* synthetic */ void m2(String string, String string2, int n, int n2, boolean bl) {
        PotionTools.m1(string);
        CustomPotion customPotion = PotionTools.m4(string, string2, bl);
        Minecraft.getMinecraft().thePlayer.removePotionEffectClient(customPotion.getId());
        Minecraft.getMinecraft().thePlayer.addPotionEffect(new PotionEffect(customPotion.getId(), n, n2));
    }

    public static void m3(String string) {
        String[] stringArray = string.split("<&>");
        PotionTools.m2(stringArray[0], stringArray[1], Integer.valueOf(stringArray[2]), Integer.valueOf(stringArray[3]), Boolean.valueOf(stringArray[4]));
    }
}
