/*
 * Copyright (C) 2012 - 2015, Fights team and contributors
 *
 * This file is part of Fights.
 *
 * Fights is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Fights is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Fights. If not, see <http://www.gnu.org/licenses/>.
 */

package de.minehattan.fights;

import com.sk89q.commandbook.session.PersistentSession;
import com.zachsthings.libcomponents.config.Setting;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * A session that stores player data related to fights.
 */
public class FightSession extends PersistentSession {

  private Location respawnLoc;

  @Setting("items.inventory")
  private Set<Map<String, Object>> inventoryItems;
  @Setting("items.armor.helmet")
  private Map<String, Object> helmet;
  @Setting("items.armor.chestplate")
  private Map<String, Object> chestplate;
  @Setting("items.armor.leggins")
  private Map<String, Object> leggings;
  @Setting("items.armor.boots")
  private Map<String, Object> boots;

  /**
   * Initializes this session.
   */
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

  /**
   * Resets the stored inventory.
   */
  public void resetInventory() {
    inventoryItems = null;
    helmet = null;
    chestplate = null;
    leggings = null;
    boots = null;
  }

  /**
   * Resets the stored respawn location.
   */
  public void resetRespawn() {
    respawnLoc = null;
  }

  public void RestoreInventory(Player player) {
    player.sendMessage(ChatColor.GOLD + "Your inventory has been restored.");
    player.getInventory().setContents(deserializeItemStacks(inventoryItems));

    if (helmet != null) {
      player.getInventory().setHelmet(ItemStack.deserialize(helmet));
    }
    if (chestplate != null) {
      player.getInventory().setChestplate(ItemStack.deserialize(chestplate));
    }
    if (leggings != null) {
      player.getInventory().setLeggings(ItemStack.deserialize(leggings));
    }
    if (boots != null) {
      player.getInventory().setBoots(ItemStack.deserialize(boots));
    }
  }

  /**
   * Gets the respawn location.
   *
   * @return th respawn location
   */
  @Nullable
  public Location getRespawnLoc() {
    return respawnLoc;
  }

  /**
   * Returns whether a respawn is needed.
   *
   * @return true if a respawn is needed
   */
  public boolean needsRespawn() {
    return respawnLoc != null;
  }

  /**
   * Returns whether an inventory restoration is needed.
   *
   * @return true if a restoration is needed
   */
  public boolean needsInventoryRestore() {
    return (inventoryItems != null && inventoryItems.size() > 0) || helmet != null || chestplate != null
           || leggings != null || boots != null;
  }

  @Override
  public Player getOwner() {
    CommandSender sender = super.getOwner();
    return sender instanceof Player ? (Player) sender : null;
  }

  /**
   * Serializes the given ItemStack array.
   *
   * @param input the ItemStack array
   * @return the serialized representation of the ItemStack array
   * @see #deserializeItemStacks(Set)
   */
  private Set<Map<String, Object>> serializeItemStacks(ItemStack[] input) {
    if (input == null) {
      return null;
    }
    Set<Map<String, Object>> output = new HashSet<Map<String, Object>>();
    for (ItemStack itemStack : input) {
      if (itemStack == null) {
        output.add(null);
      } else {
        output.add(itemStack.serialize());
      }
    }
    return output;
  }

  /**
   * Deserializes the given map into an ItemStack array.
   *
   * @param input the map
   * @return the deserialized ItemStack array
   * @see #serializeItemStacks(ItemStack[])
   */
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
