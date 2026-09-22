package com.chillzone.sus.ui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import com.chillzone.sus.data.SusRecord;
import com.chillzone.sus.data.SusStore;
import com.chillzone.sus.permission.Permissions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.time.Duration;
import java.util.*;

public final class SusMenu extends AbstractContainerMenu {
    private static final int SIZE = 54;
    private final SimpleContainer container;
    private final ServerPlayer viewer;
    private final SusStore store;
    private final Map<Integer, CaseKey> playerSlots = new HashMap<>();
    private final UUID focused;
    private final String focusedType;

    private SusMenu(int syncId, Inventory inv, ServerPlayer viewer, SusStore store, UUID focused, String focusedType) {
        super(MenuType.GENERIC_9x6, syncId);
        this.viewer = viewer;
        this.store = store;
        this.focused = focused;
        this.focusedType = focusedType;
        this.container = new SimpleContainer(SIZE);

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = col + row * 9;
                addSlot(new Slot(container, slot, 8 + col * 18, 18 + row * 18));
            }
        }

        build();
    }

    public static void open(ServerPlayer player, SusStore store) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (syncId, inv, p) -> new SusMenu(syncId, inv, player, store, null, null),
            Component.literal("SUS - Most Suspicious")
        ));
    }

    public static void openPlayer(ServerPlayer staff, ServerPlayer target, SusStore store, String type) {
        staff.openMenu(new net.minecraft.world.SimpleMenuProvider(
            (syncId, inv, p) -> new SusMenu(syncId, inv, staff, store, target.getUUID(), type),
            Component.literal("SUS - " + target.getGameProfile().name())
        ));
    }

    private void build() {
        ItemStack filler = named(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:gray_stained_glass_pane"))), Component.literal(" "));
        for (int i = 0; i < SIZE; i++) container.setItem(i, filler.copy());

        if (focused != null) {
            buildFocused();
        } else {
            buildList();
        }
    }

    private void buildList() {
        List<CaseEntry> entries = new ArrayList<>();
        for (SusRecord r : store.all()) {
            if (r.diamond != null && r.diamond.suspicionScore > 0) entries.add(new CaseEntry(r, "diamond", r.diamond));
            if (r.debris != null && r.debris.suspicionScore > 0) entries.add(new CaseEntry(r, "debris", r.debris));
            if (r.hasHackActivity()) entries.add(new CaseEntry(r, "hacks", null));
        }
        entries.sort(Comparator
            .comparingInt((CaseEntry e) -> activityScore(e)).reversed()
            .thenComparingLong(e -> -activityTime(e)));
        int slot=10;
        for (CaseEntry e:entries) {
            if(slot>=44)break; if(slot%9==8)slot+=2;
            ItemStack head=new ItemStack(Items.PLAYER_HEAD);
            String activity = "hacks".equals(e.type) ? "Hacks Activity" : ("diamond".equals(e.type) ? "Diamond Activity" : "Ancient Debris Activity");
            head.set(DataComponents.CUSTOM_NAME,Component.literal(e.r.lastKnownName + " - " + activity));
            head.set(DataComponents.LORE,new ItemLore(activityLore(e.r,e.c,e.type,true)));
            container.setItem(slot,head); playerSlots.put(slot,new CaseKey(e.r.uuid,e.type)); slot++;
        }
        if(playerSlots.isEmpty()){ ItemStack good=named(new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:lime_dye"))),Component.literal("No active SUS flags")); container.setItem(22,good); }
        ItemStack info=named(new ItemStack(Items.BOOK),Component.literal("How SUS works"));
        info.set(DataComponents.LORE,new ItemLore(List.of(
            Component.literal("Diamond, debris and hacks are separate activities."),
            Component.literal("Mining scores use behaviour, timing and exposure."),
            Component.literal("Hacks Activity shows GrimAC fly/speed detections."),
            Component.literal("Only relevant activity appears in each report."),
            Component.literal("SUS is an investigation signal, not proof."))));
        container.setItem(49,info);
    }

    private void buildFocused() {
        SusRecord r=store.get(focused); String name=r==null?"Unknown Player":r.lastKnownName;
        SusRecord.OreCase c=(r==null || "hacks".equals(focusedType))?null:r.ore(focusedType);
        String activity = "hacks".equals(focusedType) ? "Hacks Activity" : ("debris".equals(focusedType)?"Ancient Debris Activity":"Diamond Activity");
        ItemStack head=named(new ItemStack(Items.PLAYER_HEAD),Component.literal(name + " - " + activity));
        if(r!=null) head.set(DataComponents.LORE,new ItemLore(activityLore(r,c,focusedType,false)));
        container.setItem(13,head);
        if(Permissions.has(viewer,Permissions.TELEPORT))container.setItem(29,named(new ItemStack(Items.ENDER_PEARL),Component.literal("Teleport to Player")));
        if(Permissions.has(viewer,Permissions.SPECTATE))container.setItem(31,named(new ItemStack(Items.ENDER_EYE),Component.literal("Spectate Player")));
        if(Permissions.has(viewer,Permissions.CLEAR)){
            ItemStack clear=named(new ItemStack(Items.BUCKET),Component.literal("Clear This SUS Case"));
            clear.set(DataComponents.LORE,new ItemLore(List.of(Component.literal("Clears only this activity category."))));
            container.setItem(33,clear);
        }
        container.setItem(49,named(new ItemStack(Items.ARROW),Component.literal("Back")));
    }

    private static List<Component> activityLore(SusRecord r,SusRecord.OreCase c,String type,boolean click){
        if ("hacks".equals(type)) return hackLore(r, click);
        return miningLore(r, c, type, click);
    }

    private static List<Component> hackLore(SusRecord r, boolean click) {
        List<Component> lore=new ArrayList<>();
        lore.add(Component.literal("HACKS ACTIVITY"));
        lore.add(Component.literal("Fly Hacks: "+(r.grimFlyAttempts > 0 ? "YES" : "NO")));
        lore.add(Component.literal("Fly Attempts: "+r.grimFlyAttempts));
        lore.add(Component.literal("Blocked by Anti-Cheat: "+r.grimFlyBlocked));
        lore.add(Component.literal("Not Blocked: "+Math.max(0, r.grimFlyAttempts-r.grimFlyBlocked)));
        lore.add(Component.literal(""));
        lore.add(Component.literal("Speed Hacks: "+(r.grimSpeedAttempts > 0 ? "YES" : "NO")));
        lore.add(Component.literal("Speed Attempts: "+r.grimSpeedAttempts));
        lore.add(Component.literal("Blocked by Anti-Cheat: "+r.grimSpeedBlocked));
        lore.add(Component.literal("Not Blocked: "+Math.max(0, r.grimSpeedAttempts-r.grimSpeedBlocked)));
        lore.add(Component.literal("Last Detection: "+timeAgo(r.lastHackEpochMs())));
        if(click){lore.add(Component.literal(""));lore.add(Component.literal("Click to investigate"));}
        return lore;
    }

    private static List<Component> miningLore(SusRecord r,SusRecord.OreCase c,String type,boolean click){
        List<Component> lore=new ArrayList<>();
        if(c==null) return lore;
        boolean d="diamond".equals(type);
        lore.add(Component.literal(d?"DIAMOND ACTIVITY":"ANCIENT DEBRIS ACTIVITY"));
        lore.add(Component.literal("Suspicion Score: "+c.suspicionScore));
        lore.add(Component.literal("Status: "+c.status()));
        lore.add(Component.literal("Last Flag: "+timeAgo(c.lastFlagEpochMs)));
        lore.add(Component.literal((d?"Diamond Ore Mined: ":"Ancient Debris Mined: ")+c.oreMined));
        lore.add(Component.literal("Separate Veins: "+c.separateVeins));
        lore.add(Component.literal("Ore per Vein: "+String.format(java.util.Locale.ROOT,"%.1f",c.orePerVein())));
        lore.add(Component.literal("Avg Blocks Between Veins: "+blocks(c.averageBlocksBetweenVeins())));
        lore.add(Component.literal("Avg Time Between Veins: "+duration(c.averageIntervalMs())));
        lore.add(Component.literal("Cave-Exposed: "+c.caveExposedVeins+" ("+c.cavePercent()+"%)"));
        lore.add(Component.literal("Tunnel-Like: "+c.tunnelLikeVeins+" ("+c.tunnelPercent()+"%)"));
        lore.add(Component.literal("Unusual Events: "+c.unusualOreEvents));
        if(click){lore.add(Component.literal(""));lore.add(Component.literal("Click to investigate"));}
        return lore;
    }

    private static int activityScore(CaseEntry e) {
        return "hacks".equals(e.type) ? (e.r.grimFlyAttempts + e.r.grimSpeedAttempts) : (e.c == null ? 0 : e.c.suspicionScore);
    }

    private static long activityTime(CaseEntry e) {
        return "hacks".equals(e.type) ? e.r.lastHackEpochMs() : (e.c == null ? 0 : e.c.lastFlagEpochMs);
    }
    private static String blocks(double v){ return v<0?"N/A":String.format(java.util.Locale.ROOT,"%.1f",v); }
    private static String duration(long ms){ if(ms<0)return "N/A"; long s=ms/1000; if(s<60)return s+"s"; return (s/60)+"m "+(s%60)+"s"; }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, net.minecraft.world.entity.player.Player player) {
        if (slotId < 0 || slotId >= SIZE) return;

        if (focused == null) {
            CaseKey key = playerSlots.get(slotId);
            if (key != null) {
                ServerPlayer target = viewer.level().getServer().getPlayerList().getPlayer(key.uuid);
                if (target != null) {
                    openPlayer(viewer, target, store, key.type);
                } else {
                    viewer.sendSystemMessage(Component.literal("That player is no longer online."));
                    open(viewer, store);
                }
            }
            return;
        }

        ServerPlayer target = viewer.level().getServer().getPlayerList().getPlayer(focused);

        if (slotId == 29 && Permissions.has(viewer, Permissions.TELEPORT)) {
            if (target == null) {
                viewer.sendSystemMessage(Component.literal("That player is offline."));
                return;
            }
            viewer.teleportTo(
                target.level(),
                target.getX(), target.getY(), target.getZ(),
                java.util.Set.of(),
                target.getYRot(), target.getXRot(),
                false
            );
            viewer.closeContainer();
            viewer.sendSystemMessage(Component.literal("Teleported to " + target.getGameProfile().name() + "."));
            return;
        }

        if (slotId == 31 && Permissions.has(viewer, Permissions.SPECTATE)) {
            if (target == null) {
                viewer.sendSystemMessage(Component.literal("That player is offline."));
                return;
            }
            viewer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            viewer.setCamera(target);
            viewer.closeContainer();
            viewer.sendSystemMessage(Component.literal("Now spectating " + target.getGameProfile().name() + "."));
            return;
        }

        if (slotId == 33 && Permissions.has(viewer, Permissions.CLEAR)) {
            String name = target != null ? target.getGameProfile().name()
                : Optional.ofNullable(store.get(focused)).map(x -> x.lastKnownName).orElse("player");
            if ("hacks".equals(focusedType)) store.clearHackCase(focused, name);
            else store.clearCase(focused, name, focusedType);
            store.save(viewer.level().getServer());
            String activity = "hacks".equals(focusedType) ? "Hacks" : ("debris".equals(focusedType) ? "Ancient Debris" : "Diamond");
            viewer.sendSystemMessage(Component.literal("Cleared " + activity + " SUS case for " + name + "."));
            open(viewer, store);
            return;
        }

        if (slotId == 49) {
            open(viewer, store);
        }
    }

    private static ItemStack named(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    private static String timeAgo(long epochMs) {
        if (epochMs <= 0) return "None";
        long seconds = Math.max(0, (System.currentTimeMillis() - epochMs) / 1000L);
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days + "d ago";
    }

    private static String cleanPeriod(long ticks) {
        long seconds = ticks / 20L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        if (days > 0) return days + "d " + hours + "h";
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    private record CaseKey(UUID uuid,String type) {}
    private record CaseEntry(SusRecord r,String type,SusRecord.OreCase c) {}

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return true;
    }
}
