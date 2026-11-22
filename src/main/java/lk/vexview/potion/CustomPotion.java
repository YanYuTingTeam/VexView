/*
 * Decompiled with CFR 0.152.
 */
package lk.vexview.potion;

import java.util.Iterator;
import java.util.LinkedHashMap;
import lk.vexview.engine.BufferImageLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;

public class CustomPotion
extends Potion {
    /*
     * 1.8.9 has no Potion(boolean,int) constructor: a Potion must be registered with a
     * unique ResourceLocation, and Forge freezes its registries after preInit.  The pool in
     * PotionTools therefore creates its slots from VexView#preload and only re-points the
     * icon/name of an already registered slot afterwards.
     */
    static int s1;
    String f1;
    String f2;

    /** Re-points a pooled slot at a new icon key and display name. */
    void m2(String string, String string2) {
        this.f2 = string;
        this.f1 = string2;
    }

    public String intToRoman(int n) {
        StringBuilder stringBuilder;
        Integer n2;
        int n3;
        LinkedHashMap<Integer, String> linkedHashMap = new LinkedHashMap<Integer, String>();
        linkedHashMap.put(1000, "M");
        linkedHashMap.put(900, "CM");
        linkedHashMap.put(500, "D");
        linkedHashMap.put(400, "CD");
        linkedHashMap.put(100, "C");
        linkedHashMap.put(90, "XC");
        linkedHashMap.put(50, "L");
        linkedHashMap.put(40, "XL");
        linkedHashMap.put(10, "X");
        linkedHashMap.put(9, "IX");
        linkedHashMap.put(5, "V");
        linkedHashMap.put(4, "IV");
        linkedHashMap.put(1, "I");
        StringBuilder stringBuilder2 = new StringBuilder();
        Iterator iterator = linkedHashMap.keySet().iterator();
        while (iterator.hasNext()) {
            n2 = (Integer)iterator.next();
            n3 = n / n2;
            if (n3 == 0) continue;
            for (int n4 = n3; n4 > 0; --n4) {
                stringBuilder2.append((String)linkedHashMap.get(n2));
            }
            n -= n3 * n2;
            if (n == 0) break;
        }
        stringBuilder = stringBuilder2;
        return stringBuilder.toString();
    }

    public boolean shouldRenderInvText(PotionEffect potionEffect) {
        return false;
    }

    public void renderInventoryEffect(int n, int n2, PotionEffect potionEffect, Minecraft minecraft) {
        if (BufferImageLib.s4.containsKey(this.f2)) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(BufferImageLib.s4.get(this.f2));
            Gui.drawModalRectWithCustomSizedTexture((int)(n + 5), (int)(n2 + 5), (float)0.0f, (float)0.0f, (int)22, (int)22, (float)22.0f, (float)22.0f);
        }
        FontRenderer fontRenderer = Minecraft.getMinecraft().fontRendererObj;
        String string = "";
        if (potionEffect.getAmplifier() != 0) {
            string = this.intToRoman(potionEffect.getAmplifier() + 1);
        }
        fontRenderer.drawString((this.isBadEffect() ? "\ufffd\ufffdc" : "\ufffd\ufffdf") + this.f1 + " " + string, n + 28, n2 + 6, -1);
        fontRenderer.drawString("\ufffd\ufffd7" + CustomPotion.m1(potionEffect, 1.0f), n + 28, n2 + 15, -1);
        if (CustomPotion.m1(potionEffect, 1.0f).equals("0:00")) {
            Minecraft.getMinecraft().thePlayer.removePotionEffectClient(this.getId());
        }
        super.renderInventoryEffect(n, n2, potionEffect, minecraft);
    }

    public static String m1(PotionEffect potionEffect, float f) {
        if (potionEffect.getIsPotionDurationMax()) {
            return "**:**";
        }
        return StringUtils.ticksToElapsedTime((int)MathHelper.floor_float((float)((float)potionEffect.getDuration() * f)));
    }

    public CustomPotion(String string, String string2, boolean bl, int n) {
        super(new ResourceLocation("vexview", "custom_potion_" + (s1++)), bl, n);
        this.f1 = string2;
        this.f2 = string;
    }
}
