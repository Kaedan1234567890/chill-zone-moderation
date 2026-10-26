package com.chillzone.sus.ui;

import com.chillzone.sus.data.SusStore;
import com.chillzone.sus.permission.Permissions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/** Confirming deletion only modifies the saved evidence snapshot store. */
public final class SavedEvidenceDeleteMenu extends AbstractContainerMenu {
    private final ServerPlayer viewer;
    private final SusStore store;
    private final UUID suspect;
    private final String suspectName;
    private final SimpleContainer inventory = new SimpleContainer(27);

    private SavedEvidenceDeleteMenu(int id, Inventory playerInventory, ServerPlayer viewer,
                                    SusStore store, UUID suspect, String suspectName) {
        super(MenuType.GENERIC_9x3, id);
        this.viewer = viewer;
        this.store = store;
        this.suspect = suspect;
        this.suspectName = suspectName;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            int i = row * 9 + col;
            addSlot(new Slot(inventory, i, 8 + col * 18, 18 + row * 18));
        }
        ItemStack cancel = new ItemStack(Items.REDSTONE);
        cancel.set(DataComponents.CUSTOM_NAME, Component.literal("Cancel - Keep Evidence"));
        inventory.setItem(11, cancel);
        ItemStack confirm = new ItemStack(Items.EMERALD);
        confirm.set(DataComponents.CUSTOM_NAME, Component.literal("Confirm - Delete Saved Evidence"));
        inventory.setItem(15, confirm);
        ItemStack warning = new ItemStack(Items.BARRIER);
        warning.set(DataComponents.CUSTOM_NAME, Component.literal("Are you sure? Deletion cannot be undone."));
        inventory.setItem(13, warning);
    }

    public static void open(ServerPlayer viewer, SusStore store, UUID suspect, String name) {
        viewer.openMenu(new SimpleMenuProvider((id, inv, p) ->
            new SavedEvidenceDeleteMenu(id, inv, viewer, store, suspect, name),
            Component.literal("Delete saved evidence?")));
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, net.minecraft.world.entity.player.Player player) {
        if (slotId == 11) {
            viewer.closeContainer();
            viewer.sendSystemMessage(Component.literal("Saved evidence deletion canceled."));
        } else if (slotId == 15) {
            if (!Permissions.has(viewer, Permissions.CLEAR)) {
                viewer.closeContainer();
                viewer.sendSystemMessage(Component.literal("You do not have permission to delete saved evidence."));
                return;
            }
            boolean deleted = store.deleteSavedSnapshot(suspect);
            viewer.closeContainer();
            viewer.sendSystemMessage(Component.literal(deleted
                ? "Deleted the saved evidence snapshot for " + (suspectName == null ? suspect : suspectName) + ". Live SUS activity was preserved."
                : "Saved evidence could not be deleted; check server logs."));
        }
    }

    @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
    @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) { return ItemStack.EMPTY; }
}
