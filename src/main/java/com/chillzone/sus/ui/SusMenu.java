package com.chillzone.sus.ui;

import com.chillzone.sus.data.SusRecord;
import com.chillzone.sus.data.SusStore;
import com.chillzone.sus.permission.Permissions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.SimpleContainer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SusMenu extends AbstractContainerMenu {
    private static final int SIZE = 54;
    private static final int PLAYER_SLOTS_PER_PAGE = 45; // top five rows only
    private static final int[] LOCATION_SLOTS = {
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final SimpleContainer container;
    private final ServerPlayer viewer;
    private final SusStore store;
    private final Map<Integer, UUID> playerSlots = new HashMap<>();
    private final Map<Integer, SusRecord.FlagLocation> locationSlots = new HashMap<>();
    private final UUID focused;
    private final String focusedName;
    private final int page;
    private int pageCount = 1;

    private SusMenu(int syncId, Inventory inv, ServerPlayer viewer, SusStore store,
                    UUID focused, String focusedName, int page) {
        super(MenuType.GENERIC_9x6, syncId);
        this.viewer = viewer;
        this.store = store;
        this.focused = focused;
        this.focusedName = focusedName;
        this.page = Math.max(0, page);
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
        openPage(player, store, 0);
    }

    private static void openPage(ServerPlayer player, SusStore store, int page) {
        player.openMenu(new SimpleMenuProvider(
            (syncId, inv, p) -> new SusMenu(syncId, inv, player, store, null, null, page),
            Component.literal("SUS - Suspect List")
        ));
    }

    public static void openPlayer(ServerPlayer staff, UUID uuid, String name, SusStore store) {
        staff.openMenu(new SimpleMenuProvider(
            (syncId, inv, p) -> new SusMenu(syncId, inv, staff, store, uuid, name, 0),
            Component.literal("SUS - " + (name == null ? "Unknown Player" : name))
        ));
    }

    public static void openPlayer(ServerPlayer staff, ServerPlayer target, SusStore store) {
        openPlayer(staff, target.getUUID(), target.getGameProfile().name(), store);
    }

    private void build() {
        // Intentionally no decorative glass. Empty slots stay empty.
        if (focused == null) buildList();
        else buildFocused();
    }

    private void buildList() {
        List<SusRecord> suspects = new ArrayList<>();
        for (SusRecord r : store.all()) {
            if (r != null && r.uuid != null && r.hasActivity()) suspects.add(r);
        }

        suspects.sort(Comparator
            .comparingInt(SusRecord::totalFlags).reversed()
            .thenComparingLong(r -> -r.lastActivityEpochMs()));

        pageCount = Math.max(1, (suspects.size() + PLAYER_SLOTS_PER_PAGE - 1) / PLAYER_SLOTS_PER_PAGE);
        int safePage = Math.min(page, pageCount - 1);
        int from = safePage * PLAYER_SLOTS_PER_PAGE;
        int to = Math.min(suspects.size(), from + PLAYER_SLOTS_PER_PAGE);

        for (int i = from; i < to; i++) {
            int slot = i - from; // exact top-left -> right -> next row order, slots 0..44
            SusRecord r = suspects.get(i);
            ItemStack head = named(new ItemStack(Items.PLAYER_HEAD), Component.literal(displayName(r)));
            head.set(DataComponents.LORE, new ItemLore(playerSummaryLore(r)));
            container.setItem(slot, head);
            playerSlots.put(slot, r.uuid);
        }

        if (suspects.isEmpty()) {
            ItemStack good = named(new ItemStack(Items.EMERALD), Component.literal("No active SUS evidence"));
            good.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Anti-cheat and ore evidence will appear here when recorded.")
            )));
            container.setItem(22, good);
        }

        if (safePage > 0) {
            container.setItem(45, named(new ItemStack(Items.ARROW), Component.literal("Previous Page")));
        }

        ItemStack info = named(new ItemStack(Items.BOOK), Component.literal("SUS - Page " + (safePage + 1) + "/" + pageCount));
        info.set(DataComponents.LORE, new ItemLore(List.of(
            Component.literal("Top 5 rows: suspect/player heads"),
            Component.literal("Bottom row: controls only"),
            Component.literal("AntiFlight is evidence-only; staff decide punishment."),
            Component.literal("Click a player to inspect their evidence." )
        )));
        container.setItem(49, info);

        if (safePage + 1 < pageCount) {
            container.setItem(53, named(new ItemStack(Items.ARROW), Component.literal("Next Page")));
        }
    }

    private void buildFocused() {
        SusRecord r = store.get(focused);
        String name = r != null && r.lastKnownName != null ? r.lastKnownName
            : (focusedName == null ? "Unknown Player" : focusedName);

        ItemStack head = named(new ItemStack(Items.PLAYER_HEAD), Component.literal(name));
        if (r != null) head.set(DataComponents.LORE, new ItemLore(playerSummaryLore(r)));
        container.setItem(4, head); // one full row above the old slot 13

        // Evidence categories.
        SusRecord.ActivityCase fly = r == null ? new SusRecord.ActivityCase() : r.fly;
        SusRecord.ActivityCase speed = r == null ? new SusRecord.ActivityCase() : r.speed;
        SusRecord.ActivityCase elytra = r == null ? new SusRecord.ActivityCase() : r.elytra;
        SusRecord.OreCase diamond = r == null ? new SusRecord.OreCase() : r.diamond;
        SusRecord.OreCase debris = r == null ? new SusRecord.OreCase() : r.debris;

        container.setItem(10, evidenceItem(new ItemStack(Items.FEATHER), "Fly Activity", flyLore(fly)));
        container.setItem(12, evidenceItem(new ItemStack(Items.SUGAR), "Speed Activity", speedLore(speed)));
        container.setItem(14, evidenceItem(new ItemStack(Items.ELYTRA), "Elytra Activity", elytraLore(elytra)));
        container.setItem(16, evidenceItem(new ItemStack(Items.DIAMOND_PICKAXE), "X-Ray / Ore Activity", xrayLore(diamond, debris, r)));

        ServerPlayer target = viewer.level().getServer().getPlayerList().getPlayer(focused);

        // Existing live-player actions moved one row upward from the old layout.
        if (target != null && Permissions.has(viewer, Permissions.TELEPORT)) {
            container.setItem(20, named(new ItemStack(Items.ENDER_PEARL), Component.literal("Teleport to Player")));
        } else {
            container.setItem(20, unavailable("Teleport to Player", "Player is offline"));
        }

        if (target != null && Permissions.has(viewer, Permissions.SPECTATE)) {
            container.setItem(22, named(new ItemStack(Items.ENDER_EYE), Component.literal("Spectate Player")));
        } else {
            container.setItem(22, unavailable("Spectate Player", "Player is offline"));
        }

        if (Permissions.has(viewer, Permissions.CLEAR)) {
            ItemStack clear = named(new ItemStack(Items.BUCKET), Component.literal("Clear /sus"));
            clear.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Clears ALL evidence for this player."),
                Component.literal("Also clears all saved flag locations.")
            )));
            container.setItem(24, clear);
        }

        // 14 teleportable evidence locations: two rows of seven, directly above
        // the bottom navigation row.
        if (r != null && r.flagLocations != null) {
            int count = Math.min(LOCATION_SLOTS.length, r.flagLocations.size());
            for (int i = 0; i < count; i++) {
                SusRecord.FlagLocation loc = r.flagLocations.get(i);
                int slot = LOCATION_SLOTS[i];
                ItemStack marker = named(new ItemStack(Items.COMPASS), Component.literal("Flag Location #" + (i + 1)));
                marker.set(DataComponents.LORE, new ItemLore(locationLore(loc)));
                container.setItem(slot, marker);
                locationSlots.put(slot, loc);
            }
        }

        container.setItem(49, named(new ItemStack(Items.ARROW), Component.literal("Back to SUS List")));
    }

    private static ItemStack evidenceItem(ItemStack stack, String name, List<Component> lore) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack unavailable(String name, String reason) {
        ItemStack item = named(new ItemStack(Items.BARRIER), Component.literal(name + " - Unavailable"));
        item.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(reason))));
        return item;
    }

    private static List<Component> playerSummaryLore(SusRecord r) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Total Flags: " + r.totalFlags()));
        lore.add(Component.literal("Fly: " + r.fly.flags));
        lore.add(Component.literal("Speed: " + r.speed.flags));
        lore.add(Component.literal("Elytra: " + r.elytra.flags));
        lore.add(Component.literal("X-Ray/Ore: " + (r.diamond.activeFlags + r.debris.activeFlags)
            + " flags | Diamond " + r.diamond.suspicionScore + "/30 | Debris " + r.debris.suspicionScore + "/30"));
        lore.add(Component.literal("Saved Locations: " + (r.flagLocations == null ? 0 : r.flagLocations.size()) + "/14"));
        lore.add(Component.literal("Last Activity: " + timeAgo(r.lastActivityEpochMs())));
        lore.add(Component.literal(""));
        lore.add(Component.literal("Click to investigate"));
        return lore;
    }

    private static List<Component> flyLore(SusRecord.ActivityCase c) {
        List<Component> lore = commonMovementLore(c);
        lore.add(0, Component.literal("Flags: " + c.flags));
        lore.add(1, Component.literal("Last Detection: " + timeAgo(c.lastFlagEpochMs)));
        lore.add(2, Component.literal("Last Reason: " + prettyReason(c.lastReason)));
        lore.add(Component.literal("Elytra Equipped: " + yesNo(c.lastElytraEquipped)));
        lore.add(Component.literal("Elytra Active: " + yesNo(c.lastElytraActive)));
        lore.add(Component.literal("Creative: " + yesNo(c.lastCreative)));
        lore.add(Component.literal("Spectator: " + yesNo(c.lastSpectator)));
        lore.add(Component.literal("On Ground: " + yesNo(c.lastOnGround)));
        lore.add(Component.literal("Height Above Ground: " + height(c.lastHeightAboveGround)));
        return lore;
    }

    private static List<Component> speedLore(SusRecord.ActivityCase c) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Flags: " + c.flags));
        lore.add(Component.literal("Last Detection: " + timeAgo(c.lastFlagEpochMs)));
        lore.add(Component.literal("Last Reason: " + prettyReason(c.lastReason)));
        lore.add(Component.literal("Horizontal Speed: " + fmt(c.lastHorizontalBps) + " blocks/s"));
        lore.add(Component.literal("Vertical Speed: " + fmt(c.lastVerticalBps) + " blocks/s"));
        lore.add(Component.literal("Check Actual: " + fmt(c.lastActual)));
        lore.add(Component.literal("Check Allowed: " + fmt(c.lastAllowed)));
        lore.add(Component.literal("Movement Effects: " + c.lastMovementEffects));
        lore.add(Component.literal("Recent Wind Charges (5s): " + c.lastRecentWindCharges));
        lore.add(Component.literal("Recent Rockets (5s): " + c.lastRecentRockets));
        lore.add(Component.literal("Recent Launch/Impulse: " + yesNo(c.lastRecentImpulse)));
        lore.add(Component.literal("Recently Hit/Knockback: " + yesNo(c.lastRecentlyHurt)));
        lore.add(Component.literal("Nearby Boats: " + c.lastNearbyBoats));
        lore.add(Component.literal("Nearby Entities: " + c.lastNearbyEntities));
        lore.add(Component.literal("Vehicle: " + c.lastVehicle));
        return lore;
    }

    private static List<Component> elytraLore(SusRecord.ActivityCase c) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Flags: " + c.flags));
        lore.add(Component.literal("Last Detection: " + timeAgo(c.lastFlagEpochMs)));
        lore.add(Component.literal("Last Reason: " + prettyReason(c.lastReason)));
        lore.add(Component.literal("Horizontal Speed: " + fmt(c.lastHorizontalBps) + " blocks/s"));
        lore.add(Component.literal("Vertical Speed: " + fmt(c.lastVerticalBps) + " blocks/s"));
        lore.add(Component.literal("Pitch: " + fmt(c.lastPitch) + " degrees"));
        lore.add(Component.literal("Near-Vertical Ascent: " + yesNo(c.lastNearVerticalAscent)));
        lore.add(Component.literal("Check Actual: " + fmt(c.lastActual)));
        lore.add(Component.literal("Check Allowed/Reference: " + fmt(c.lastAllowed)));
        lore.add(Component.literal("Recent Rockets (5s): " + c.lastRecentRockets));
        lore.add(Component.literal("Recent Wind Charges (5s): " + c.lastRecentWindCharges));
        lore.add(Component.literal("Recent Launch/Impulse: " + yesNo(c.lastRecentImpulse)));
        lore.add(Component.literal("Nearby Boats: " + c.lastNearbyBoats));
        lore.add(Component.literal("Nearby Entities: " + c.lastNearbyEntities));
        lore.add(Component.literal("Movement Effects: " + c.lastMovementEffects));
        return lore;
    }

    private static List<Component> commonMovementLore(SusRecord.ActivityCase c) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Horizontal Speed: " + fmt(c.lastHorizontalBps) + " blocks/s"));
        lore.add(Component.literal("Vertical Speed: " + fmt(c.lastVerticalBps) + " blocks/s"));
        lore.add(Component.literal("Check Actual: " + fmt(c.lastActual)));
        lore.add(Component.literal("Check Allowed: " + fmt(c.lastAllowed)));
        lore.add(Component.literal("Movement Effects: " + c.lastMovementEffects));
        lore.add(Component.literal("Recent Wind Charges (5s): " + c.lastRecentWindCharges));
        lore.add(Component.literal("Recent Rockets (5s): " + c.lastRecentRockets));
        lore.add(Component.literal("Recent Launch/Impulse: " + yesNo(c.lastRecentImpulse)));
        lore.add(Component.literal("Recently Hit/Knockback: " + yesNo(c.lastRecentlyHurt)));
        lore.add(Component.literal("Nearby Boats: " + c.lastNearbyBoats));
        lore.add(Component.literal("Nearby Entities: " + c.lastNearbyEntities));
        lore.add(Component.literal("Vehicle: " + c.lastVehicle));
        return lore;
    }

    private static List<Component> xrayLore(SusRecord.OreCase diamond, SusRecord.OreCase debris, SusRecord r) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Evidence only - staff review the saved mining locations."));
        lore.add(Component.literal(""));
        lore.add(Component.literal("Diamond"));
        lore.add(Component.literal("Flags: " + diamond.activeFlags + " | Score: " + diamond.suspicionScore + "/30"));
        lore.add(Component.literal("Mined: " + diamond.oreMined + " | Separate Veins: " + diamond.separateVeins));
        lore.add(Component.literal("Avg Blocks Between Veins: " + blocks(diamond.averageBlocksBetweenVeins())));
        lore.add(Component.literal("Tunnel-Like: " + diamond.tunnelLikeVeins + " | Cave-Exposed: " + diamond.caveExposedVeins));
        lore.add(Component.literal("Unusual Events: " + diamond.unusualOreEvents));
        lore.add(Component.literal("Last Flag: " + timeAgo(diamond.lastFlagEpochMs)));
        lore.add(Component.literal(""));
        lore.add(Component.literal("Ancient Debris"));
        lore.add(Component.literal("Flags: " + debris.activeFlags + " | Score: " + debris.suspicionScore + "/30"));
        lore.add(Component.literal("Mined: " + debris.oreMined + " | Separate Veins: " + debris.separateVeins));
        lore.add(Component.literal("Avg Blocks Between Veins: " + blocks(debris.averageBlocksBetweenVeins())));
        lore.add(Component.literal("Tunnel-Like: " + debris.tunnelLikeVeins + " | Cave-Exposed: " + debris.caveExposedVeins));
        lore.add(Component.literal("Unusual Events: " + debris.unusualOreEvents));
        lore.add(Component.literal("Last Flag: " + timeAgo(debris.lastFlagEpochMs)));
        lore.add(Component.literal(""));
        long oreLocations = r.flagLocations == null ? 0 : r.flagLocations.stream()
            .filter(loc -> "diamond".equals(loc.category) || "debris".equals(loc.category))
            .count();
        lore.add(Component.literal("Saved X-Ray/Ore Locations: " + oreLocations));
        return lore;
    }

    private static List<Component> locationLore(SusRecord.FlagLocation loc) {
        String category = loc.category == null ? "Unknown" : switch (loc.category) {
            case "fly" -> "Fly";
            case "speed" -> "Speed";
            case "elytra" -> "Elytra";
            case "diamond" -> "Diamond / X-Ray";
            case "debris" -> "Ancient Debris / X-Ray";
            default -> loc.category;
        };
        return List.of(
            Component.literal("Category: " + category),
            Component.literal("Reason: " + prettyReason(loc.reason)),
            Component.literal("World: " + safe(loc.world)),
            Component.literal("X: " + one(loc.x) + "  Y: " + one(loc.y) + "  Z: " + one(loc.z)),
            Component.literal("When: " + timeAgo(loc.timestamp)),
            Component.literal("Actual / Allowed: " + fmt(loc.actual) + " / " + fmt(loc.allowed)),
            Component.literal("Pitch: " + fmt(loc.pitch) + " degrees"),
            Component.literal(""),
            Component.literal("Click to teleport to this evidence location")
        );
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, net.minecraft.world.entity.player.Player player) {
        if (slotId < 0 || slotId >= SIZE) return;

        if (focused == null) {
            UUID uuid = playerSlots.get(slotId);
            if (uuid != null) {
                SusRecord r = store.get(uuid);
                openPlayer(viewer, uuid, r == null ? "Unknown Player" : r.lastKnownName, store);
                return;
            }
            if (slotId == 45 && page > 0) {
                openPage(viewer, store, page - 1);
                return;
            }
            if (slotId == 53 && page + 1 < pageCount) {
                openPage(viewer, store, page + 1);
            }
            return;
        }

        ServerPlayer target = viewer.level().getServer().getPlayerList().getPlayer(focused);

        if (slotId == 20 && Permissions.has(viewer, Permissions.TELEPORT)) {
            if (target == null) {
                viewer.sendSystemMessage(Component.literal("That player is offline."));
                return;
            }
            viewer.teleportTo(
                target.level(), target.getX(), target.getY(), target.getZ(), Set.of(),
                target.getYRot(), target.getXRot(), false
            );
            viewer.closeContainer();
            viewer.sendSystemMessage(Component.literal("Teleported to " + target.getGameProfile().name() + "."));
            return;
        }

        if (slotId == 22 && Permissions.has(viewer, Permissions.SPECTATE)) {
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

        if (slotId == 24 && Permissions.has(viewer, Permissions.CLEAR)) {
            String name = target != null ? target.getGameProfile().name()
                : Optional.ofNullable(store.get(focused)).map(x -> x.lastKnownName).orElse("player");
            store.clearActive(focused, name);
            store.save(viewer.level().getServer());
            viewer.sendSystemMessage(Component.literal("Cleared ALL SUS evidence and saved locations for " + name + "."));
            open(viewer, store);
            return;
        }

        SusRecord.FlagLocation location = locationSlots.get(slotId);
        if (location != null && Permissions.has(viewer, Permissions.TELEPORT)) {
            ServerLevel destination = null;
            for (ServerLevel level : viewer.level().getServer().getAllLevels()) {
                if (level.dimension().identifier().toString().equals(location.world)) {
                    destination = level;
                    break;
                }
            }
            if (destination == null) {
                viewer.sendSystemMessage(Component.literal("That evidence world is not currently available."));
                return;
            }
            viewer.teleportTo(
                destination,
                location.x, location.y + 0.5, location.z,
                Set.of(), viewer.getYRot(), viewer.getXRot(), false
            );
            viewer.closeContainer();
            viewer.sendSystemMessage(Component.literal("Teleported to saved SUS evidence location."));
            return;
        }

        if (slotId == 49) open(viewer, store);
    }

    private static ItemStack named(ItemStack stack, Component name) {
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    private static String displayName(SusRecord r) {
        return r.lastKnownName == null || r.lastKnownName.isBlank() ? r.uuid.toString() : r.lastKnownName;
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private static String height(int blocks) {
        return blocks >= 65 ? ">64 blocks" : blocks + " blocks";
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String one(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String safe(String text) {
        return text == null || text.isBlank() ? "Unknown" : text;
    }

    private static String prettyReason(String reason) {
        if (reason == null || reason.isBlank()) return "None";
        String cleaned = reason.replace('_', ' ');
        StringBuilder out = new StringBuilder();
        boolean cap = true;
        for (char ch : cleaned.toCharArray()) {
            if (cap && Character.isLetter(ch)) {
                out.append(Character.toUpperCase(ch));
                cap = false;
            } else {
                out.append(ch);
            }
            if (ch == ' ') cap = true;
        }
        return out.toString();
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

    private static String blocks(double v) {
        return v < 0 ? "N/A" : String.format(Locale.ROOT, "%.1f", v);
    }

    private static String duration(long ms) {
        if (ms < 0) return "N/A";
        long s = ms / 1000;
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return true;
    }
}
