package com.holysweet.playerinventorysync;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketWindowItems;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.UUID;

/**
 * 1.12.2 fossil version.
 *
 * Server-side only:
 * - catch rejected / canceled / denied interaction paths
 * - run late, after other mods have had their say
 * - receive canceled events instead of missing the actual failure path
 * - immediately resend server-truth inventory/container state
 * - perform one tiny delayed watchdog resync after the suspect event
 *
 * No broad syncing.
 * No dimension syncing.
 * No container lifecycle syncing.
 * No persistence.
 * No magic.
 */
final class ServerResyncs {

    private static final int WATCHDOG_TICKS = 2;
    private static final int MAX_RESYNCS_PER_TICK = 3;

    private final Object2IntOpenHashMap<UUID> suspectUntilTick = new Object2IntOpenHashMap<UUID>();
    private final Object2IntOpenHashMap<UUID> resyncsThisTick = new Object2IntOpenHashMap<UUID>();

    ServerResyncs() {
        suspectUntilTick.defaultReturnValue(-1);
        resyncsThisTick.defaultReturnValue(0);
    }

    /**
     * Push server-truth back to the client:
     * - the currently open container contents
     * - the carried cursor stack
     */
    private void resyncNow(EntityPlayerMP player) {
        UUID id = player.getUniqueID();

        int count = resyncsThisTick.getInt(id);
        if (count >= MAX_RESYNCS_PER_TICK) {
            return;
        }

        Container container = player.openContainer;
        if (container == null) {
            return;
        }

        System.out.println("[PlayerInventorySync] I really am doing it: resyncing inventory reality for " + player.getName());

        player.connection.sendPacket(new SPacketWindowItems(
                container.windowId,
                container.getInventory()
        ));

        ItemStack carriedStack = player.inventory.getItemStack();

        player.connection.sendPacket(new SPacketSetSlot(
                -1,
                -1,
                carriedStack
        ));

        resyncsThisTick.put(id, count + 1);
    }

    private void markSuspect(EntityPlayerMP player) {
        int untilTick = player.ticksExisted + WATCHDOG_TICKS;
        suspectUntilTick.put(player.getUniqueID(), untilTick);
    }

    private void resyncAndWatch(EntityPlayerMP player) {
        resyncNow(player);
        markSuspect(player);
    }

    // --- TICK: rate-limit reset + tiny watchdog follow-up ----------------------------------------

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        UUID id = player.getUniqueID();

        // Per-player per-tick rate-limit reset.
        resyncsThisTick.removeInt(id);

        int untilTick = suspectUntilTick.getInt(id);

        if (untilTick != -1 && player.ticksExisted >= untilTick) {
            resyncNow(player);
            suspectUntilTick.removeInt(id);
        }
    }

    // --- FAILED / DENIED / CANCELED WORLD INTERACTIONS -------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }

        boolean canceled = event.isCanceled();
        boolean blockDenied = event.getUseBlock() == Event.Result.DENY;
        boolean itemDenied = event.getUseItem() == Event.Result.DENY;

        if (!canceled && !blockDenied && !itemDenied) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        resyncAndWatch(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }

        if (!event.isCanceled()) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        resyncAndWatch(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }

        boolean canceled = event.isCanceled();
        boolean blockDenied = event.getUseBlock() == Event.Result.DENY;

        if (!canceled && !blockDenied) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        resyncAndWatch(player);
    }
}