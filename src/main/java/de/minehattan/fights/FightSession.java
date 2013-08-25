package de.minehattan.fights;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.sk89q.commandbook.session.PersistentSession;
import com.zachsthings.libcomponents.config.Setting;

public class FightSession extends PersistentSession {

    private Location respawnLoc = null;

    @Setting("items.inventory")
    private Set<Map<String, Object>> inventoryItems = null;
    @Setting("items.armor.helmet")
    public Map<String, Object> helmet = null;
    @Setting("items.armor.chestplate")
    public Map<String, Object> chestplate = null;
    @Setting("items.armor.leggins")
    public Map<String, Object> leggings = null;
    @Setting("items.armor.boots")
    public Map<String, Object> boots = null;

    protected FightSession() {
        super(THIRTY_MINUTES);
    }

    public void initialize(Player player, boolean storeInventory) {
        if (storeInventory) {
            inventoryItems = serializeItemStacks(player.getInventory().getContents());

            if (player.getInventory().getHelmet() != null) {
                helmet = player.getInventory().getHelmet().serialize();
            }
            if (player.getInventory().getChestplate() != null) {
                chestplate = player.getInventory().getChestplate().serialize();
            }
            if (player.getInventory().getLeggings() != null) {
                leggings = player.getInventory().getLeggings().serialize();
            }
            if (player.getInventory().getBoots() != null) {
                boots = player.getInventory().getBoots().serialize();
            }
        }
        respawnLoc = player.getLocation().clone();
    }

    public void resetInventory() {
        inventoryItems = null;
        helmet = null;
        chestplate = null;
        leggings = null;
        boots = null;
    }

    public void resetRespawn() {
        respawnLoc = null;
    }

    public void RestoreInventory(Player player) {
        player.sendMessage(ChatColor.GOLD + "Your inventory has been restored.");
        player.getInventory().setContents(deserializeItemStacks(inventoryItems));

        if (helmet != null)
            player.getInventory().setHelmet(ItemStack.deserialize(helmet));
        if (chestplate != null)
            player.getInventory().setChestplate(ItemStack.deserialize(chestplate));
        if (leggings != null)
            player.getInventory().setLeggings(ItemStack.deserialize(leggings));
        if (boots != null)
            player.getInventory().setBoots(ItemStack.deserialize(boots));
    }

    public Location getRespawnLoc() {
        return respawnLoc;
    }

    public boolean needsRespawn() {
        return respawnLoc != null;
    }

    public boolean needsInventoryRestore() {
        return (inventoryItems != null && inventoryItems.size() > 0) || helmet != null || chestplate != null
                || leggings != null || boots != null;
    }

    @Override
    public Player getOwner() {
        CommandSender sender = super.getOwner();
        return sender instanceof Player ? (Player) sender : null;
    }

    private Set<Map<String, Object>> serializeItemStacks(ItemStack[] input) {
        if (input == null) {
            return null;
        }
        Set<Map<String, Object>> output = new HashSet<Map<String, Object>>();
        for (int i = 0; i < input.length; i++) {
            output.add(input[i] != null ? input[i].serialize() : null);
        }
        return output;
    }

    private ItemStack[] deserializeItemStacks(Set<Map<String, Object>> input) {
        if (input == null) {
            return null;
        }
        ItemStack[] output = new ItemStack[input.size()];
        int i = 0;
        for (Map<String, Object> inputItem : input) {
            output[i] = inputItem != null ? ItemStack.deserialize(inputItem) : null;
            i++;
        }
        return output;
    }
}
