param(
    [string]$SourceRoot = (Join-Path $PSScriptRoot '../src/main/java'),
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin/javac.exe' } else { 'javac' }
$java = if ($JavaHome) { Join-Path $JavaHome 'bin/java.exe' } else { 'java' }
$source = (Resolve-Path -LiteralPath $SourceRoot).Path
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$scratch = Join-Path $tempRoot ('vex-entity-test-' + [guid]::NewGuid().ToString('N'))

function Write-Stub([string]$path, [string]$code) {
    $file = Join-Path $scratch $path
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($file))
    [IO.File]::WriteAllText($file, $code, [Text.UTF8Encoding]::new($false))
}

try {
    $factory = Join-Path $source 'lk/vexview/entity/VexEntityGeter.java'
    $player = Join-Path $source 'lk/vexview/entity/VexPlayerEntity.java'
    $renderer = Join-Path $source 'lk/vexview/entity/VexEntityRender.java'

    Write-Stub 'com/mojang/authlib/GameProfile.java' @'
package com.mojang.authlib;
import java.util.UUID;
public final class GameProfile {
    private final String name;
    public GameProfile(UUID id, String name) { this.name = name; }
    public String getName() { return name; }
}
'@
    Write-Stub 'net/minecraft/world/World.java' @'
package net.minecraft.world;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
public class World {
    public final List<Entity> loadedEntityList = new ArrayList<>();
}
'@
    Write-Stub 'net/minecraft/client/multiplayer/WorldClient.java' 'package net.minecraft.client.multiplayer; public class WorldClient extends net.minecraft.world.World {}'
    Write-Stub 'net/minecraft/entity/Entity.java' @'
package net.minecraft.entity;
import net.minecraft.world.World;
public class Entity {
    public final World worldObj;
    public Entity(World world) { this.worldObj = world; }
    public void setRotationYawHead(float value) {}
    public void setRenderYawOffset(float value) {}
}
'@
    Write-Stub 'net/minecraft/entity/EntityLivingBase.java' @'
package net.minecraft.entity;
public class EntityLivingBase extends Entity {
    public float renderYawOffset, rotationYaw, rotationPitch, rotationYawHead, prevRotationYawHead;
    public EntityLivingBase(net.minecraft.world.World world) { super(world); }
}
'@
    Write-Stub 'net/minecraft/client/entity/AbstractClientPlayer.java' @'
package net.minecraft.client.entity;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
public abstract class AbstractClientPlayer extends EntityLivingBase {
    private final GameProfile profile;
    public AbstractClientPlayer(World world, GameProfile profile) { super(world); this.profile = profile; }
    public GameProfile getGameProfile() { return this.profile; }
}
'@
    Write-Stub 'net/minecraft/item/ItemStack.java' 'package net.minecraft.item; public class ItemStack {}'

    $factoryText = [IO.File]::ReadAllText($factory)
    $imports = [regex]::Matches($factoryText, '(?m)^import (net\.minecraft\.entity(?:\.[A-Za-z]+)*\.)(Entity[A-Za-z]+);')
    foreach ($entry in $imports) {
        $pkg = $entry.Groups[1].Value.TrimEnd('.')
        $name = $entry.Groups[2].Value
        Write-Stub (($pkg.Replace('.', '/') + '/' + $name + '.java')) @"
package $pkg;
public class $name extends net.minecraft.entity.Entity {
    public $name(net.minecraft.world.World world) { super(world); }
}
"@
    }

    Write-Stub 'net/minecraft/client/Minecraft.java' @'
package net.minecraft.client;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.entity.RenderManager;
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public WorldClient theWorld;
    public static Minecraft getMinecraft() { return INSTANCE; }
    public RenderManager getRenderManager() { throw new AssertionError("No renderer expected for a missing entity"); }
}
'@
    Write-Stub 'net/minecraft/client/renderer/GlStateManager.java' @'
package net.minecraft.client.renderer;
public class GlStateManager {
    public static void enableColorMaterial() { throw new AssertionError("Missing entity touched GL state"); }
    public static void pushMatrix() {}
    public static void translate(float x, float y, float z) {}
    public static void scale(float x, float y, float z) {}
    public static void rotate(float a, float x, float y, float z) {}
    public static void popMatrix() {}
    public static void disableRescaleNormal() {}
    public static void setActiveTexture(int texture) {}
    public static void disableTexture2D() {}
}
'@
    Write-Stub 'net/minecraft/client/renderer/OpenGlHelper.java' 'package net.minecraft.client.renderer; public class OpenGlHelper { public static int lightmapTexUnit, defaultTexUnit; }'
    Write-Stub 'net/minecraft/client/renderer/RenderHelper.java' @'
package net.minecraft.client.renderer;
public class RenderHelper {
    public static void enableStandardItemLighting() {}
    public static void disableStandardItemLighting() {}
}
'@
    Write-Stub 'net/minecraft/client/renderer/entity/RendererLivingEntity.java' 'package net.minecraft.client.renderer.entity; public class RendererLivingEntity {}'
    Write-Stub 'net/minecraft/client/renderer/entity/RenderManager.java' @'
package net.minecraft.client.renderer.entity;
import net.minecraft.entity.Entity;
public class RenderManager {
    public void setPlayerViewY(float value) {}
    public void setRenderShadow(boolean enabled) {}
    public Object getEntityRenderObject(Entity entity) { return null; }
    public void doRenderEntity(Entity entity, double x, double y, double z, float yaw, float partialTicks, boolean debug) {}
}
'@
    Write-Stub 'EntityPreviewTest.java' @'
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import lk.vexview.entity.VexEntityGeter;
import lk.vexview.entity.VexEntityRender;
import lk.vexview.entity.VexPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityHorse;

public class EntityPreviewTest {
    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "preview");
        check(VexEntityGeter.m1(profile) == null, "No named preview before joining");
        check(VexEntityGeter.m2("COW") == null, "No entity preview before joining");
        VexEntityRender.m2(0, 0, 1, 0, 0, null, false);
        VexEntityRender.m3(0, 0, 1, null, true);

        WorldClient first = new WorldClient();
        minecraft.theWorld = first;
        Entity cow = VexEntityGeter.m2("COW");
        check(cow instanceof EntityCow && cow.worldObj == first, "First preview uses the joined world");
        VexPlayerEntity loaded = new VexPlayerEntity(first, profile);
        first.loadedEntityList.add(loaded);
        check(VexEntityGeter.m1(profile) == loaded, "Named preview reuses the current loaded player");

        WorldClient next = new WorldClient();
        minecraft.theWorld = next;
        check(VexEntityGeter.m2("COW").worldObj == next, "New entity preview uses the new world");
        Entity donkey = VexEntityGeter.m2("DONKEY");
        check(donkey instanceof EntityHorse && donkey.worldObj == next, "Existing fallback keeps its type");
        check(VexEntityGeter.m2("AREA_EFFECT_CLOUD") == null, "Unsupported type keeps its null fallback");
        check(VexEntityGeter.m2("PLAYER").worldObj == next, "Generic player preview uses the new world");
        Entity replacement = VexEntityGeter.m1(profile);
        check(replacement != loaded && replacement.worldObj == next, "Named preview ignores the previous world");
        check(new VexPlayerEntity(first).worldObj == first, "Player preview constructor honors its argument");

        minecraft.theWorld = null;
        check(VexEntityGeter.m1(profile) == null && VexEntityGeter.m2("COW") == null,
            "No previews after leaving");
        System.out.println("PASS: entity preview world lifecycle and null rendering");
    }
}
'@

    $classes = Join-Path $scratch 'classes'
    [void][IO.Directory]::CreateDirectory($classes)
    $stubs = @(Get-ChildItem -LiteralPath $scratch -Filter '*.java' -Recurse | ForEach-Object FullName)
    & $javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -d $classes $factory $player $renderer @stubs
    if ($LASTEXITCODE -ne 0) { throw 'Entity preview test compilation failed' }
    & $java -cp $classes EntityPreviewTest
    if ($LASTEXITCODE -ne 0) { throw 'Entity preview regression test failed' }
} finally {
    if (Test-Path -LiteralPath $scratch) {
        $resolved = (Resolve-Path -LiteralPath $scratch).Path
        if ([IO.Path]::GetDirectoryName($resolved) -eq $tempRoot.TrimEnd('\', '/') -and
            [IO.Path]::GetFileName($resolved) -match '^vex-entity-test-[a-f0-9]{32}$') {
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
}
