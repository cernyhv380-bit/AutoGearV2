package com.autogear.actions;

import com.autogear.storage.SetStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Очередь исходящих пакетов. Только отправляет — локально не трогает.
 */
public final class PacketQueue {

    private static final Deque<Runnable> QUEUE = new ArrayDeque<>();
    private static int pauseTicks = 0;

    private PacketQueue() {}

    public static void enqueue(Runnable task) {
        if (task != null) QUEUE.add(task);
    }

    public static void pause(int ticks) {
        if (ticks > pauseTicks) pauseTicks = ticks;
    }

    public static void clear() {
        QUEUE.clear();
        pauseTicks = 0;
    }

    public static boolean isIdle() {
        return QUEUE.isEmpty() && pauseTicks == 0;
    }

    public static void tick(Minecraft client) {
        if (client == null || client.getConnection() == null) {
            clear();
            return;
        }
        if (pauseTicks > 0) { pauseTicks--; return; }

        int perTick = SetStorage.getPacketsPerTick();
        for (int i = 0; i < perTick && !QUEUE.isEmpty(); i++) {
            Runnable r = QUEUE.poll();
            try { r.run(); }
            catch (Exception e) { System.out.println("[AutoGear] task failed: " + e); }
        }
    }

    /** Отправляет один ServerboundSetCreativeModeSlotPacket. */
    public static void sendCreativeSlot(int packetSlot, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) return;
        if (stack == null) stack = ItemStack.EMPTY;
        mc.getConnection().send(
                new ServerboundSetCreativeModeSlotPacket(packetSlot, stack)
        );
    }
}
