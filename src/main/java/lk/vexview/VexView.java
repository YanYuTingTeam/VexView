/*
 * Decompiled with CFR 0.152.
 */
package lk.vexview;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;
import lk.vexview.VexConfig;
import lk.vexview.announcement.AnnGui;
import lk.vexview.auto.connect.AutoConnect;
import lk.vexview.chat.ChatTools;
import lk.vexview.effect.CustomRender;
import lk.vexview.engine.ImageConnectionGuard;
import lk.vexview.flowview.FlowTools;
import lk.vexview.gui.tools.Base64Tools;
import lk.vexview.hud.HUDViewer;
import lk.vexview.hud.MinecraftHUD;
import lk.vexview.hud.util.Base64HudTools;
import lk.vexview.inventory.InventoryTools;
import lk.vexview.net.Base64ImagePackets;
import lk.vexview.net.NetPacketManager;
import lk.vexview.net.packet.PacketKeyPress;
import lk.vexview.scoreboard.NewScoreBroad;
import lk.vexview.tag.TagView;
import lk.vexview.tooltip.ToolTipRender;
import lk.vexview.tutorial.GuideListener;
import lk.vexview.ui.menu.VexGameMenu;
import lk.vexview.ui.verification.ConnectGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

@Mod(modid="vexview", name="VexView", version="2.7.3", acceptedMinecraftVersions="[1.8,1.12.2]")
public class VexView {
    static ThreadPoolExecutor s1;
    public static final String s2 = "vexview";
    public static final KeyBinding s3;
    public static final String s4 = "2.7.3";
    static ScheduledExecutorService s5;
    static ThreadPoolExecutor s6;
    private static final AtomicLong lastImageQueueWarning = new AtomicLong();
    private static Logger s7;
    public static boolean s8;
    public static FMLEventChannel s9;
    public static FMLEventChannel s10;
    @Mod.Instance(value="vexview")
    public static VexView s11;
    public static FMLEventChannel s12;
    public static final String s13 = "VexView";
    public static FMLEventChannel s14;
    private static final ImageConnectionGuard<NetworkManager> imageConnections = new ImageConnectionGuard<>(() -> {
        NetHandlerPlayClient active = Minecraft.getMinecraft().getNetHandler();
        return active == null ? null : active.getNetworkManager();
    }, NetworkManager::isChannelOpen, task -> Minecraft.getMinecraft().addScheduledTask(task));

    static {
        s8 = false;
        s5 = Executors.newScheduledThreadPool(10);
        s1 = (ThreadPoolExecutor)Executors.newFixedThreadPool(10);
        s6 = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(512));
        s3 = new KeyBinding(I18n.format((String)"key.scoreboard", (Object[])new Object[0]), 49, I18n.format((String)"key.scoreboard.title", (Object[])new Object[0]));
    }

    public static void m1(Runnable runnable) {
        try {
            s1.execute(runnable);
            return;
        }
        catch (Exception exception) {
            exception.printStackTrace();
            return;
        }
    }

    @SubscribeEvent
    public void onClientPacket(FMLNetworkEvent.ClientCustomPacketEvent clientCustomPacketEvent) throws IOException {
        byte[] data = new byte[clientCustomPacketEvent.packet.payload().readableBytes()];
        clientCustomPacketEvent.packet.payload().getBytes(0, data);
        NetworkManager previous = imageConnections.enter(clientCustomPacketEvent.manager);
        try {
            NetPacketManager.m5(VexView.m3(data));
        } finally {
            imageConnections.leave(previous);
        }
    }

    public static NetworkManager imageConnection() {
        return imageConnections.capture();
    }

    public static boolean isImageConnectionCurrent(NetworkManager source) {
        return imageConnections.isCurrent(source);
    }

    public static void onImageConnection(NetworkManager source, Runnable action) {
        imageConnections.run(source, action);
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Base64Tools.disconnect(event.manager);
        Base64HudTools.disconnect(event.manager);
        lk.vexview.engine.BufferImageLib.clearBase64Textures(event.manager);
    }

    public static Base64ImagePackets<NetworkManager> base64ImagePackets(BiConsumer<String, BufferedImage> upload) {
        return new Base64ImagePackets<>(s1::execute, task -> Minecraft.getMinecraft().addScheduledTask(task),
            connection -> {
                NetHandlerPlayClient active = Minecraft.getMinecraft().getNetHandler();
                return active != null && active.getNetworkManager() == connection && connection.isChannelOpen();
            }, upload);
    }

    public static Future<Boolean> m2(Callable<Boolean> callable) {
        try {
            return s6.submit(callable);
        }
        catch (RejectedExecutionException exception) {
            long now = System.currentTimeMillis();
            long last = lastImageQueueWarning.get();
            if (now - last >= 5000 && lastImageQueueWarning.compareAndSet(last, now) && s7 != null) {
                s7.warn("Image loading queue is full; some images were not loaded.");
            }
            return null;
        }
        catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @Mod.EventHandler
    public void preload(FMLPreInitializationEvent fMLPreInitializationEvent) {
        lk.vexview.potion.PotionTools.m0();
        // 1.8.9 merges the two event buses: FMLCommonHandler#bus() returns MinecraftForge.EVENT_BUS
        // (verified in the 11.15.1.1902 bytecode), so registering on both used to create a *second*
        // handler instance for every class below - each event then ran twice (double animation
        // frames, duplicate GUI-open handling, duplicate packets). One registration is correct.
        MinecraftForge.EVENT_BUS.register((Object)this);
        MinecraftForge.EVENT_BUS.register((Object)new HUDViewer());
        MinecraftForge.EVENT_BUS.register((Object)new InventoryTools());
        MinecraftForge.EVENT_BUS.register((Object)new ConnectGui());
        MinecraftForge.EVENT_BUS.register((Object)new VexGameMenu());
        MinecraftForge.EVENT_BUS.register((Object)new AutoConnect());
        MinecraftForge.EVENT_BUS.register((Object)new FlowTools());
        MinecraftForge.EVENT_BUS.register((Object)new AnnGui());
        MinecraftForge.EVENT_BUS.register((Object)new NewScoreBroad());
        MinecraftForge.EVENT_BUS.register((Object)new MinecraftHUD());
        MinecraftForge.EVENT_BUS.register((Object)new GuideListener());
        MinecraftForge.EVENT_BUS.register((Object)new TagView());
        MinecraftForge.EVENT_BUS.register((Object)new CustomRender());
        MinecraftForge.EVENT_BUS.register((Object)new ChatTools());
        MinecraftForge.EVENT_BUS.register((Object)new ToolTipRender());
        s9 = NetworkRegistry.INSTANCE.newEventDrivenChannel(s13);
        s9.register((Object)this);
        s10 = NetworkRegistry.INSTANCE.newEventDrivenChannel("Base64VexView");
        s10.register((Object)new Base64Tools());
        s14 = NetworkRegistry.INSTANCE.newEventDrivenChannel("HudBase64VexView");
        s14.register((Object)new Base64HudTools());
        s12 = NetworkRegistry.INSTANCE.newEventDrivenChannel("ChatVexView");
        s12.register((Object)new ChatTools());
        FMLPreInitializationEvent fMLPreInitializationEvent2 = fMLPreInitializationEvent;
        s7 = fMLPreInitializationEvent2.getModLog();
        VexConfig.m1(fMLPreInitializationEvent2);
        File file = new File(VexConfig.s1);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    private static /* synthetic */ String m3(byte[] byArray) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byArray);
        GZIPInputStream gZIPInputStream = new GZIPInputStream(byteArrayInputStream);
        byte[] byArray2 = new byte[256];
        int n = 0;
        GZIPInputStream gZIPInputStream2 = gZIPInputStream;
        while ((n = gZIPInputStream2.read(byArray2)) >= 0) {
            gZIPInputStream2 = gZIPInputStream;
            byteArrayOutputStream.write(byArray2, 0, n);
        }
        return byteArrayOutputStream.toString("utf-8");
    }

    @SubscribeEvent
    public void keyListener(InputEvent.KeyInputEvent keyInputEvent) {
        if (Keyboard.getEventKey() == 30 || Keyboard.getEventKey() == 31 || Keyboard.getEventKey() == 32 || Keyboard.getEventKey() == 17 || Keyboard.getEventKey() == 57) {
            return;
        }
        String string = new StringBuilder().insert(0, "KeyBoardPress:").append(Keyboard.getEventKey()).append(":").append(Keyboard.getEventKeyState() ? 1 : 0).append(":NOGUI").toString();
        NetPacketManager.m4(new PacketKeyPress(string));
    }

    public static void m4(Runnable runnable, long l, long l2, TimeUnit timeUnit) {
        try {
            s5.scheduleAtFixedRate(runnable, l, l2, timeUnit);
            return;
        }
        catch (Exception exception) {
            exception.printStackTrace();
            return;
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent fMLInitializationEvent) {
        ClientRegistry.registerKeyBinding((KeyBinding)s3);
    }
}

