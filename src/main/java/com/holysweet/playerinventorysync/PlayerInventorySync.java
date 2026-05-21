package com.holysweet.playerinventorysync;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(
        modid = PlayerInventorySync.MODID,
        name = PlayerInventorySync.NAME,
        version = PlayerInventorySync.VERSION,
        acceptedMinecraftVersions = "[1.12.2]"
)
public final class PlayerInventorySync {

    public static final String MODID = "playerinventorysync";
    public static final String NAME = "Player Inventory Sync";
    public static final String VERSION = "1.0.0";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        System.out.println("[PlayerInventorySync] Init reached.");
        MinecraftForge.EVENT_BUS.register(new ServerResyncs());
    }
}