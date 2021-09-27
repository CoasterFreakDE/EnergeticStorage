package net.seanomik.energeticstorage.gui;

import de.tr7zw.changeme.nbtapi.NBTItem;
import net.seanomik.energeticstorage.files.PlayersFile;
import net.seanomik.energeticstorage.objects.ESDrive;
import net.seanomik.energeticstorage.objects.ESSystem;
import net.seanomik.energeticstorage.utils.Reference;
import net.seanomik.energeticstorage.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static net.seanomik.energeticstorage.utils.GUIHelper.createGuiItem;

public class ESDriveGUI implements InventoryHolder, Listener {
    private final Inventory globalInv;
    private final String title = "ES Drives";

    private Map<UUID, ESSystem> openSystems = new HashMap<>();

    public ESDriveGUI() {
        globalInv = Bukkit.createInventory(this, 9, title);
    }

    @Override
    public Inventory getInventory() {
        return globalInv;
    }

    // You can call this whenever you want to put the items in
    private void initializeItems(Player player, ESSystem esSystem) {
        // Only initialize the items for the players inventory, not all of them.
        Inventory inv = player.getOpenInventory().getTopInventory();

        // Remove all the items
        inv.clear();

        inv.setItem(0, createGuiItem(Material.PAPER, "Back"));
        inv.setItem(1, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, ""));

        inv.setItem(7, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, ""));
        inv.setItem(8, createGuiItem(Material.BLACK_STAINED_GLASS_PANE, ""));

        if (openSystems.containsKey(player.getUniqueId())) {
            openSystems.replace(player.getUniqueId(), esSystem);
        } else {
            openSystems.put(player.getUniqueId(), esSystem);
        }

        for (int i = 2; i < esSystem.getESDrives().size() + 2; i++) {
            ESDrive drive = esSystem.getESDrives().get(i - 2);

            if (drive != null) {
                inv.setItem(i, drive.getDriveItem());
            }
        }
    }

    // You can open the inventory with this
    public void openInventory(Player p, ESSystem esSystem) {
        p.openInventory(globalInv);
        initializeItems(p, esSystem);

        if (openSystems.containsKey(p.getUniqueId())) {
            openSystems.replace(p.getUniqueId(), esSystem);
        } else {
            openSystems.put(p.getUniqueId(), esSystem);
        }
    }

    private enum ClickType {
        NONE,
        SWAP,
        SWAP_RIGHT_CLICK,
        INTO,
        INTO_HALF,
        OUT,
        OUT_HALF,
        SHIFT_OUT,
        SHIFT_IN,
        INVENTORY_CLICK
    }

    private ClickType findClickType(InventoryClickEvent event) {
        Inventory inventory = event.getClickedInventory();

        if (inventory == null || inventory.getHolder() == null || inventory.getHolder() != this) {
            // Check for a shift click or bottom inventory click.
            if (event.getView().getTitle().equals(title)) {
                return (event.isShiftClick()) ? ClickType.SHIFT_IN : ClickType.INVENTORY_CLICK;
            }

            return ClickType.NONE;
        }

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if ((clickedItem == null || clickedItem.getType() == Material.AIR) && (cursor == null || cursor.getType() == Material.AIR)) {
            return ClickType.NONE;
        } else if ( (clickedItem == null || clickedItem.getType() == Material.AIR) && (cursor != null || cursor.getType() != Material.AIR) ) {
            return (event.isLeftClick()) ? ClickType.INTO : ClickType.INTO_HALF;
        } else if (cursor == null || cursor.getType() == Material.AIR) {
            return (event.isShiftClick()) ? ClickType.SHIFT_OUT : (event.isLeftClick()) ? ClickType.OUT : ClickType.OUT_HALF;
        }

        return (event.isLeftClick()) ? ClickType.SWAP : ClickType.SWAP_RIGHT_CLICK;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();

        if (inventory == null || inventory.getHolder() == null || inventory.getHolder() != this) {
            return;
        } else {
            Player player = (Player) event.getPlayer();
            ESSystem openSystem = openSystems.get(player.getUniqueId());
            // Serialize null drives
            openSystem.getESDrives().removeIf(Objects::isNull);

            PlayersFile.savePlayerSystem(openSystem);

            openSystems.remove(player);
        }
    }

    // Check for clicks on items
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ClickType clickType = findClickType(event);

        if (clickType != ClickType.NONE) {
            event.setCancelled(true);

            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem(); // Will be valid if clicks an item (i.e. taking an item from the inventory)
            ItemStack cursor = event.getCursor(); // Will be valid if an item is put into the inventory
            int slot = event.getSlot();

            ESSystem esSystem = openSystems.get(player.getUniqueId());

            // Make sure no items will get copied to other players open inventory
            Inventory inv = player.getOpenInventory().getTopInventory();

            // Handle type of click.
            switch (clickType) {
                case SWAP_RIGHT_CLICK:
                    break;
                case SWAP:
                    break;
                case SHIFT_IN:
                    if (Utils.isItemValid(clickedItem) && Utils.isItemADrive(clickedItem)) {
                        event.setCancelled(true);

                        // Add the item into the player's inventory
                        int driveSlot = inv.firstEmpty();
                        ItemStack oneClicked = clickedItem.clone();
                        oneClicked.setAmount(1);
                        inv.setItem(driveSlot, oneClicked);

                        List<ESDrive> drives = esSystem.getESDrives();
                        drives.add(driveSlot - 2, new ESDrive(clickedItem));
                        esSystem.setESDrives(drives);

                        // Remove the item from the players inventory
                        clickedItem.setAmount(clickedItem.getAmount() - 1);
                    }
                    break;
                case INTO_HALF:
                case INTO:
                    if (Utils.isItemValid(cursor) && Utils.isItemADrive(cursor)) {
                        NBTItem clickedNBT = new NBTItem(cursor);

                        if (clickedNBT.hasKey("ES_Drive") && clickedNBT.getBoolean("ES_Drive")) {

                            List<ESDrive> drives = esSystem.getESDrives();
                            if (drives.contains(null)) {
                                drives.set(drives.indexOf(null), new ESDrive(cursor));
                            } else {
                                drives.add(new ESDrive(cursor));
                            }
                            esSystem.setESDrives(drives);
                            initializeItems(player, esSystem);

                            event.setCancelled(true);
                            cursor.setAmount(0);
                        }
                    }
                    break;
                case SHIFT_OUT:
                case OUT_HALF:
                case OUT:
                    if (slot == 0) { // Back button.
                        player.closeInventory();

                        Reference.ES_TERMINAL_GUI.openInventory(player, esSystem);
                    } else if (slot != 1 && slot != 7 && slot != 8) {
                        if (Utils.isItemADrive(clickedItem)) {
                            List<ESDrive> drives = esSystem.getESDrives();
                            drives.set(slot - 2, null);
                            esSystem.setESDrives(drives);

                            event.setCancelled(false);
                        }
                    }
                    break;
                case INVENTORY_CLICK:
                    event.setCancelled(false);
                    break;
            }
        }
    }
}
