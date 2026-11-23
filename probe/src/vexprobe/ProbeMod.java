package vexprobe;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.zip.GZIPOutputStream;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

/**
 * Verification probe. NOT shipped with the mod.
 *
 * Feeds synthetic VexView server packets into the real client channel so the whole
 * "server packet -> gzip -> JSON -> dispatch -> render" pipeline of the ported mod is
 * exercised inside a running Minecraft 1.8.9 client, and asserts the resulting client state.
 */
@Mod(modid = "vexprobe", name = "VexView Port Probe", version = "1.0",
     clientSideOnly = true, acceptableRemoteVersions = "*")
public class ProbeMod {

    private static final String GUI_JSON =
            "{\"inventory\":\"10:10\",\"base\":\"[gui][local]bg.png<&>-1<&>-1<&>256<&>256<&>220<&>160<&>true<&>false"
            + "<#>[txt]12<&>10<&>1.0<&>VexView 1.8.9 port<&>GUI pipeline OK"
            + "<#>[but][local]bg.png<&>70<&>60<&>80<&>18<&>[local]bg.png<&>0<&>[local]bg.png<&>OK\"}";

    /** {tick, packet_type, packet_sub_type, packet_data} */
    private static final Object[][] PLAN = new Object[][]{
        {60, "announce", "NORMAL", "[VexProbe] announce ok"},
        {62, "potion", "", "vexview/textures/normal.png<&>VexPotion<&>6000<&>1<&>false"},
        {64, "gamemenu", "", "x<##>https://www.vexview.top<##>烟雨庭<##>Update log line one<#>Update log line two<#>third line"},
        {70, "effects", "lightning", "0<&>64<&>0<&>1.0<&>1.0<&>1.0"},
        {260, "gui", "end", GUI_JSON},
    };

    private int ticks;
    private boolean joined;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("[vexprobe] armed");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!this.joined) {
            if (this.ticks < 120) {
                ++this.ticks;
                return;
            }
            this.joined = true;
            System.out.println("[vexprobe] auto-joining world");
            minecraft.launchIntegratedServer("New World", "New World", null);
            this.ticks = 0;
            return;
        }
        if (minecraft.theWorld == null || minecraft.thePlayer == null) {
            return;
        }
        ++this.ticks;
        if (this.ticks == 1) {
            // keep the test deterministic: no mobs killing the test subject mid-run
            minecraft.theWorld.setWorldTime(1000L);
            minecraft.theWorld.getWorldInfo().setRaining(false);
            minecraft.theWorld.getWorldInfo().setDifficulty(net.minecraft.world.EnumDifficulty.PEACEFUL);
        }
        if (minecraft.thePlayer.getHealth() < minecraft.thePlayer.getMaxHealth()) {
            minecraft.thePlayer.setHealth(minecraft.thePlayer.getMaxHealth());
        }
        for (Object[] step : PLAN) {
            if (((Integer) step[0]).intValue() == this.ticks) {
                this.send((String) step[1], (String) step[2], (String) step[3]);
            }
        }
        if (this.ticks == 110) {
            this.dumpEffects("after potion packet");
        }
        if (this.ticks == 70) {
            this.dumpGameMenu("after gamemenu packet");
        }
        if (this.ticks == 90) {
            this.dumpEffectsQueue();
        }
        if (this.ticks == 200) {
            System.out.println("[vexprobe] opening in-game menu");
            minecraft.displayGuiScreen(new net.minecraft.client.gui.GuiIngameMenu());
        }
        if (this.ticks == 230) {
            GuiScreen screen = minecraft.currentScreen;
            System.out.println("[vexprobe] ASSERT menuScreen=" + (screen == null ? "null" : screen.getClass().getName()));
        }
        if (this.ticks == 340 || this.ticks == 700) {
            this.probeAnnouncements(this.ticks);
        }
        if (this.ticks >= 400 && this.ticks <= 410) {
            this.probeReflect(this.ticks);
        }
        if (this.ticks == 300) {
            GuiScreen screen = minecraft.currentScreen;
            System.out.println("[vexprobe] ASSERT currentScreen=" + (screen == null ? "null" : screen.getClass().getName()));
        }
    }

    /** VexReflect.partialTicks() must interpolate (not always 0) and AnnGui must be registered once. */
    private void probeReflect(int tick) {
        try {
            Class<?> reflect = Class.forName("lk.vexview.compat.VexReflect");
            Object value = reflect.getMethod("partialTicks").invoke(null);
            String busInfo = "";
            if (tick == 400) {
                Object bus = net.minecraftforge.common.MinecraftForge.EVENT_BUS;
                java.lang.reflect.Field listeners = net.minecraftforge.fml.common.eventhandler.EventBus.class
                        .getDeclaredField("listeners");
                listeners.setAccessible(true);
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) listeners.get(bus);
                int annGui = 0;
                for (Object owner : map.keySet()) {
                    if (owner != null && owner.getClass().getName().startsWith("lk.vexview.")) {
                        ++annGui;
                    }
                }
                busInfo = " vexviewHandlers=" + annGui;
            }
            System.out.println("[vexprobe] tick " + tick + " partialTicks=" + value + busInfo);
        } catch (Throwable t) {
            System.out.println("[vexprobe] reflect probe failed: " + t);
        }
    }

    /** Reads VexView's pending-effect queue: proves the `effects`/lightning packet was accepted. */
    private void dumpEffectsQueue() {
        try {
            Class<?> render = Class.forName("lk.vexview.effect.CustomRender");
            java.lang.reflect.Field queue = render.getDeclaredField("s1");
            queue.setAccessible(true);
            java.util.List<?> list = (java.util.List<?>) queue.get(null);
            System.out.println("[vexprobe] lightning effects queued=" + list.size());
        } catch (Throwable t) {
            System.out.println("[vexprobe] effect probe failed: " + t);
        }
    }

    /** Reads the parsed game-menu broadcast lines reflectively: proves the <##>/<#> protocol works. */
    private void dumpGameMenu(String when) {
        try {
            Class<?> menu = Class.forName("lk.vexview.ui.menu.VexGameMenu");
            java.lang.reflect.Field lines = menu.getDeclaredField("s6");
            lines.setAccessible(true);
            java.util.List<?> list = (java.util.List<?>) lines.get(null);
            java.lang.reflect.Field url = menu.getDeclaredField("s3");
            url.setAccessible(true);
            java.lang.reflect.Field title = menu.getDeclaredField("s4");
            title.setAccessible(true);
            System.out.println("[vexprobe] gamemenu " + when + ": lines=" + list.size() + " " + list
                    + " url=" + url.get(null) + " title=" + title.get(null));
        } catch (Throwable t) {
            System.out.println("[vexprobe] gamemenu probe failed: " + t);
        }
    }

    /** Reads VexView's announcement state reflectively: proves the HUD handler actually runs. */
    private void probeAnnouncements(int tick) {
        try {
            Class<?> annGui = Class.forName("lk.vexview.announcement.AnnGui");
            java.lang.reflect.Field listField = annGui.getDeclaredField("s2");
            listField.setAccessible(true);
            java.util.List<?> list = (java.util.List<?>) listField.get(null);
            StringBuilder sb = new StringBuilder();
            for (Object announcement : list) {
                java.lang.reflect.Field counter = announcement.getClass().getDeclaredField("f3");
                counter.setAccessible(true);
                sb.append(' ').append(counter.getInt(announcement));
            }
            System.out.println("[vexprobe] tick " + tick + " announcements=" + list.size() + " animationFrames:" + sb);
        } catch (Throwable t) {
            System.out.println("[vexprobe] announcement probe failed: " + t);
        }
    }

    private void dumpEffects(String when) {
        Minecraft minecraft = Minecraft.getMinecraft();
        Collection<PotionEffect> effects = minecraft.thePlayer.getActivePotionEffects();
        System.out.println("[vexprobe] effects " + when + ": count=" + effects.size());
        for (PotionEffect effect : effects) {
            int id = effect.getPotionID();
            Potion potion = Potion.potionTypes[id];
            System.out.println("[vexprobe]   id=" + id
                    + " potionClass=" + (potion == null ? "null" : potion.getClass().getName())
                    + " duration=" + effect.getDuration()
                    + " amplifier=" + effect.getAmplifier()
                    + " bad=" + (potion != null && potion.isBadEffect()));
        }
    }

    private void send(String type, String subType, String data) {
        try {
            String json = "{\"packet_type\":\"" + type + "\",\"packet_sub_type\":\"" + subType
                    + "\",\"packet_data\":\"" + escape(data) + "\"}";
            byte[] payload = gzip(json);
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            FMLProxyPacket packet = new FMLProxyPacket(new PacketBuffer(buf), "VexView");
            FMLNetworkEvent.ClientCustomPacketEvent event
                    = new FMLNetworkEvent.ClientCustomPacketEvent(new NetworkManager(EnumPacketDirection.CLIENTBOUND), packet);
            System.out.println("[vexprobe] tick " + this.ticks + " -> " + type + "/" + subType);
            MinecraftForge.EVENT_BUS.post(event);
        } catch (Throwable t) {
            System.out.println("[vexprobe] FAILED " + type + "/" + subType);
            t.printStackTrace();
        }
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static byte[] gzip(String s) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gz = new GZIPOutputStream(out);
        gz.write(s.getBytes("UTF-8"));
        gz.close();
        return out.toByteArray();
    }
}
