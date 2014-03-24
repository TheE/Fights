package de.minehattan.fights;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.sk89q.commandbook.CommandBook;
import com.sk89q.commandbook.kits.Kit;
import com.sk89q.commandbook.util.ChatUtil;
import com.zachsthings.libcomponents.config.ConfigurationBase;
import com.zachsthings.libcomponents.config.Setting;

import de.minehattan.fights.Fights.Countdown;

public class FightFactory extends ConfigurationBase implements Listener {

    @Setting("messages.nextFight")
    public String nextFightMessage = "Es kämpfen:";
    @Setting("messages.won")
    public String wonMessage = "%player% hat diese Runde gewonnen!";
    @Setting("messages.figthStopped")
    public String fightStoppedMessage = "Der Kampf '%fight%' wurde abgebrochen.";

    private final Map<String, Fight> fightMap = new HashMap<String, Fight>();
    private final Fights component;
    private final Scoreboard fightBoard = Bukkit.getScoreboardManager().getNewScoreboard();
    private final Objective objective;
    private final Fireworks fireworks = new Fireworks();

    public FightFactory(Fights component) {
        this.component = component;

        // TODO use main scorboard here and only set objectivs?
        objective = fightBoard.registerNewObjective("Fights", "health");
        objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
        objective.setDisplayName(ChatColor.RED + "❤");
    }

    /**
     * Returns whether the given player is fighting.
     * 
     * @param player
     *            the player
     * @return true if the player is fighting, false if not
     */
    public boolean isFighting(Player player) {
        return fightMap.containsKey(player.getName());
    }

    /**
     * Registers a new fight
     * 
     * @param count
     *            count for the countdown that runs before the fight is started
     * @param lightning
     *            location for the countdown's lightning strike - use null to
     *            disable
     * @param playFirework
     *            whether a firework should be played when the fight is over
     * @param weapons
     *            the kit with the weapons - use null if this fight should not
     *            distribute any weapons (and therefore does not interfere with
     *            the player's inventory)
     * @param fighters
     *            a list with all fighting players
     * @return the registered fight
     */
    public Fight registerFight(int count, Location lightning, boolean playFirework, Kit weapons, List<Player> fighters) {
        return new Fight(count, lightning, playFirework, weapons, fighters);
    }

    /**
     * Manually stops the fight of the given player
     * 
     * @param player
     *            the player
     */
    public void stopFight(Player player) {
        Fight fight = fightMap.get(player.getName());
        fight.endFight();

        component.broadcast(fightStoppedMessage.replace("%fight%", StringUtils.join(fight.getFighters(), " vs. ")));
    }

    /**
     * Manually stops all running fights
     */
    public void stopAllFights() {
        for (Fight fight : fightMap.values()) {
            fight.endFight();
        }
    }

    /**
     * Called on player respawn
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!component.getSessions().getSession(FightSession.class, player).needsRespawn()) {
            return;
        }
        event.setRespawnLocation(component.getSessions().getSession(FightSession.class, player).getRespawnLoc());
        component.getSessions().getSession(FightSession.class, player).resetRespawn();

        restoreInventory(player);
    }

    /**
     * Restores the saved inventory of the given player if needed.
     * 
     * @param player
     *            the player
     */
    private void restoreInventory(Player player) {
        if (component.getSessions().getSession(FightSession.class, player).needsInventoryRestore()) {
            player.getInventory().clear();
            component.getSessions().getSession(FightSession.class, player).RestoreInventory(player);
        }
        component.getSessions().getSession(FightSession.class, player).resetInventory();
    }

    public class Fight implements Listener {
        private final List<String> fighters = new ArrayList<String>();
        private final boolean showFirework;
        private final Kit weapons;

        private BukkitTask countdownTask;

        public Fight(int count, Location lightning, boolean showFirework, Kit weapons, List<Player> fighters) {
            this.showFirework = showFirework;
            this.weapons = weapons;

            // register this fight's events
            CommandBook.registerEvents(this);

            // store the fight under it's participants
            for (Player player : fighters) {
                // max health and food-level, remove all active effects
                player.setHealth(20);
                player.setFoodLevel(20);
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }

                // set the scoreboard
                player.setScoreboard(fightBoard);

                // initialize the fight-session
                component.getSessions().getSession(FightSession.class, player).initialize(player, weapons != null);

                // clean inventory and distribute the weapons
                if (weapons != null) {
                    player.getInventory().clear();
                    player.getInventory().setArmorContents(null);
                    weapons.distribute(player);
                }

                // add the player's name to the fighter-list and store this
                // fight under its name
                String name = player.getName();
                this.fighters.add(name);
                fightMap.put(name, this);
            }

            // announce the fight
            component.broadcast(nextFightMessage + " " + StringUtils.join(this.fighters, " vs. "));

            // run countdown if needed
            if (count > 0) {
                countdownTask = new FightCountdown(component, this, count, lightning, countdownTask).runTaskTimer(
                        CommandBook.inst(), 20, 20);
            }
        }

        public void endFight() {
            // stop running countdown, if any
            if (countdownTask != null) {
                countdownTask.cancel();
            }

            // unregister this fight's events
            HandlerList.unregisterAll(this);

            if (isOver()) { // normal way of ending a fight - announce winner
                Player winner = CommandBook.server().getPlayerExact(fighters.get(0));
                component.broadcast(wonMessage.replace("%player%", ChatUtil.toName(winner)));
                if (showFirework) {
                    fireworks.showFirework(winner.getLocation());
                }
                winner.teleport(component.getSessions().getSession(FightSession.class, winner).getRespawnLoc());
            }

            for (String fighter : fighters) {
                // remove the stored reference
                fightMap.remove(fighter);

                // cleanup the player
                Player player = CommandBook.server().getPlayerExact(fighter);
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                player.setHealth(player.getMaxHealth());

                if (weapons != null) {
                    restoreInventory(player);
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void PlayerDeathEvent(PlayerDeathEvent event) {
            Player victim = event.getEntity();
            if (!fighters.contains(victim.getName())) {
                return;
            }
            // remove the player
            fighters.remove(victim.getName());
            fightMap.remove(victim.getName());

            // make him keep its xp
            event.setKeepLevel(true);
            event.setDroppedExp(0);

            // reset the scoreboard - everything else must be done on respawn
            victim.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

            // remove drops only if this fight manages player's inventory
            if (weapons != null) {
                event.getDrops().clear();
            }
            // if the fight is over, end it
            if (isOver()) {
                endFight();
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPlayerQuit(PlayerQuitEvent event) {
            Player player = event.getPlayer();
            if (!fighters.contains(player.getName())) {
                return;
            }
            // remove the player
            fighters.remove(player.getName());
            fightMap.remove(player.getName());

            // cleanup the player
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            player.setHealth(player.getMaxHealth());
            restoreInventory(player);

            // if the fight is over, end it
            if (isOver()) {
                endFight();
            }
        }

        public List<String> getFighters() {
            return Collections.unmodifiableList(fighters);
        }

        private boolean isOver() {
            return fighters.size() <= 1;
        }
    }

    /**
     * Custom countdown used before a fight is started - will freeze an unfreeze
     * players
     */
    public class FightCountdown extends Countdown {
        private final Fight fight;

        public FightCountdown(Fights fights, Fight fight, int count, Location lightning, BukkitTask task) {
            fights.super(count, lightning, task);
            this.fight = fight;

            for (String name : fight.getFighters()) {
                Player player = CommandBook.server().getPlayerExact(name);
                component.getFreeze().freezePlayer(player);
            }
        }

        @Override
        public void onCancel() {
            for (String name : fight.getFighters()) {
                Player player = CommandBook.server().getPlayerExact(name);
                component.getFreeze().unfreezePlayer(player);
            }
        }

    }

}
