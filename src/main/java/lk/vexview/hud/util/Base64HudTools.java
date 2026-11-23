/*
 * Decompiled with CFR 0.152.
 */
package lk.vexview.hud.util;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import lk.vexview.VexView;
import lk.vexview.engine.BufferImageLib;
import lk.vexview.net.Base64ImagePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public class Base64HudTools {
    private static final Base64ImagePackets<NetworkManager> IMAGES = VexView.base64ImagePackets(BufferImageLib::m5);

    public static void m1(String string) {
        NetHandlerPlayClient active = Minecraft.getMinecraft().getNetHandler();
        if (active != null) {
            IMAGES.receive(active.getNetworkManager(), string);
        }
    }

    public static void disconnect(NetworkManager manager) {
        IMAGES.disconnect(manager);
    }

    @SubscribeEvent
    public void onClientPacket(FMLNetworkEvent.ClientCustomPacketEvent event) {
        if (event.handler instanceof NetHandlerPlayClient
                && ((NetHandlerPlayClient) event.handler).getNetworkManager() == event.manager
                && event.manager.isChannelOpen()) {
            ByteBuf payload = event.packet.payload();
            String text = payload.toString(payload.readerIndex(), payload.readableBytes(), StandardCharsets.UTF_8);
            IMAGES.receive(event.manager, text);
        }
    }
}


