/*
 * Decompiled with CFR 0.152.
 */
package lk.vexview.gui.tools;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lk.vexview.VexView;
import lk.vexview.engine.ComponentFactory;
import lk.vexview.engine.ElementSplitter;
import lk.vexview.engine.collection.HoverTextElementCollection;
import lk.vexview.gui.LoadingGui;
import lk.vexview.gui.VexCustomGui;
import lk.vexview.gui.core.VexGui;
import lk.vexview.gui.core.VexList;
import lk.vexview.gui.util.VexContainer;
import lk.vexview.net.NetPacketManager;
import lk.vexview.net.packet.PacketGuiClose;
import lk.vexview.net.packet.PacketGuiOpen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import org.json.JSONObject;

public class VexGuiBuilder {
    private static final AtomicLong guiRequests = new AtomicLong();

    public static void m3(String string) {
        Minecraft minecraft = Minecraft.getMinecraft();
        NetHandlerPlayClient connection = minecraft.getNetHandler();
        long request = guiRequests.incrementAndGet();
        VexView.m1(() -> {
            JSONObject payload = new JSONObject(string);
            Map<String, List<HoverTextElementCollection>> components =
                ElementSplitter.m1(payload.getString("base"), "<#>", "<&>", "<#&>");
            Map<String, List<HoverTextElementCollection>> scrollingList = payload.has("scrollinglist")
                ? ElementSplitter.m2(payload.getString("scrollinglist"), "<#>", "<&>", "<#&>") : null;
            String inventory = payload.has("inventory") ? payload.getString("inventory") : null;
            minecraft.addScheduledTask(() -> {
                // A later GUI request or a new connection can supersede this worker.
                if (request == guiRequests.get() && connection != null && minecraft.getNetHandler() == connection) {
                    m6(minecraft, components, scrollingList, inventory);
                }
            });
        });
    }

    private static void m6(Minecraft minecraft, Map<String, List<HoverTextElementCollection>> components,
            Map<String, List<HoverTextElementCollection>> scrollingList, String inventory) {
        HoverTextElementCollection layout = components.remove("gui").get(0);
        VexGui gui = new VexGui(layout.m3(1), layout.m3(2), layout.m3(3), layout.m3(4), layout.m3(5),
            layout.m3(6), layout.m8(0), layout.m1(7), layout.m1(8));
        gui.m2();
        components.values().forEach(list -> list.forEach(element -> gui.m1(ComponentFactory.m4(element))));
        gui.m7().forEach(component -> component.m2());
        if (inventory != null) {
            gui.m17(inventory);
        }
        gui.m9();
        if (scrollingList != null) {
            gui.f13 = buildScrollingList(scrollingList);
        }

        ItemPickTools.s1 = null;
        GuiScreen screen = minecraft.currentScreen;
        if (screen instanceof VexCustomGui) {
            VexCustomGui oldGui = (VexCustomGui) screen;
            oldGui.f4 = false;
            NetPacketManager.m4(new PacketGuiClose());
            if (!gui.f5) {
                oldGui.f3 = gui;
                oldGui.setContainerSlots(new VexContainer(gui.m4()));
                oldGui.reInit();
                oldGui.f4 = true;
                NetPacketManager.m4(new PacketGuiOpen());
                return;
            }
        }
        minecraft.displayGuiScreen(new LoadingGui(new VexCustomGui(gui, new VexContainer(gui.m4()))));
        NetPacketManager.m4(new PacketGuiOpen());
    }

    public static VexList m7(String string) {
        return buildScrollingList(ElementSplitter.m2(string, "<#>", "<&>", "<#&>"));
    }

    private static VexList buildScrollingList(Map<String, List<HoverTextElementCollection>> map) {
        HoverTextElementCollection hoverTextElementCollection = map.get("list").get(0);
        VexList vexList = new VexList(hoverTextElementCollection.m3(0), hoverTextElementCollection.m3(1), hoverTextElementCollection.m3(2), hoverTextElementCollection.m3(3), hoverTextElementCollection.m3(4));
        Map<String, List<HoverTextElementCollection>> map2 = map;
        map2.remove("list");
        map2.values().forEach(list -> list.forEach(hoverText -> vexList.m1(ComponentFactory.m4(hoverText))));
        vexList.m1().forEach(vexComponent -> vexComponent.m2());
        return vexList;
    }
}

