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

import com.google.common.collect.Lists;
import com.sk89q.commandbook.CommandBook;
import com.sk89q.commandbook.FreezeComponent;
import com.sk89q.commandbook.kits.Kit;
import com.sk89q.commandbook.kits.KitsComponent;
import com.sk89q.commandbook.session.SessionComponent;
import com.sk89q.commandbook.session.SessionFactory;
import com.sk89q.commandbook.util.ChatUtil;
import com.sk89q.commandbook.util.InputUtil;
import com.sk89q.commandbook.util.entity.player.PlayerUtil;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.sk89q.minecraft.util.commands.NestedCommand;
import com.zachsthings.libcomponents.ComponentInformation;
import com.zachsthings.libcomponents.Depend;
import com.zachsthings.libcomponents.InjectComponent;
import com.zachsthings.libcomponents.bukkit.BukkitComponent;
import com.zachsthings.libcomponents.config.ConfigurationBase;
import com.zachsthings.libcomponents.config.Setting;

import org.apache.commons.lang.text.StrBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Central entry-point for Fights.
 */
@Depend(components = {SessionComponent.class, KitsComponent.class, FreezeComponent.class})
@ComponentInformation(friendlyName = "Fights", desc = "Contains various comments to mange PvP fights")
public class Fights extends BukkitComponent {

  private static final int DEFAULT_COUNTDOWN = 5;

  private LocalConfiguration config;
  private final Random random = new Random();
  private FightFactory fightFactory;
  private BukkitTask countdownTask;

  @InjectComponent
  private static SessionComponent sessions;
  @InjectComponent
  private static KitsComponent kits;
  @InjectComponent
  private static FreezeComponent freeze;

  @Override
  public void enable() {
    config = configure(new LocalConfiguration());
    registerCommands(TopLevelCommand.class);

    // setup the fight-session
    sessions.registerSessionFactory(FightSession.class, new SessionFactory<FightSession>() {
      @Override
      public FightSession createSession(CommandSender user) {
        return new FightSession();
      }
    });

    // setup the fight factory
    fightFactory = new FightFactory(this);
    CommandBook.registerEvents(fightFactory);
    configure(fightFactory);

  }

  @Override
  public void disable() {
    fightFactory.stopAllFights();
  }

  @Override
  public void reload() {
    super.reload();
    configure(config);
    configure(fightFactory);
  }

  /**
   * Broadcasts the given message on the server.
   *
   * @param message the message
   */
  public void broadcast(String message) {
    CommandBook.server().broadcastMessage(ChatColor.GRAY + "[Fights] " + ChatColor.AQUA + message);
  }

  /**
   * Gets a random weapon from the configured weapon-list.
   *
   * @return a random weapon
   */
  private String getRandomWeapon() {
    return (String) config.diceList.toArray()[random.nextInt(config.diceList.size())];
  }

  /**
   * The configuration.
   */
  private static class LocalConfiguration extends ConfigurationBase {

    @Setting("messages.alradyFighting")
    private String alreadyFightingMessage = "%player% kämpft bereits!";
    @Setting("messages.countdownRunning")
    private String countdownRunningMessage = "Es läuft bereits ein Countdown!";
    @Setting("messages.noCountdownRunning")
    private String
        noCountdownRunningMessage =
        "Aktuell läuft kein globaler Countdown. 'Nutze /fight stop <Spieler>' um einen Kampf anzubrechen.";
    @Setting("messages.countdownStopped")
    private String countdownStoppedMessage = "Der Countdown wurde gestoppt!";
    @Setting("messages.dice")
    private String diceMessage = "Der nächste Kampf wird mit %weapon% geführt.";
    @Setting("messages.fight")
    private String fightMessage = "Kämpft!";
    @Setting("messages.fightAgainstHisself")
    private String fightAgainstMessage = "%player% kann nicht gegen sich selber kämpfen!";
    @Setting("messages.figthStoppedCmd")
    private String fightStoppedCmdMessage = "%player%s Kampf wurde abgebrochen!";
    @Setting("messages.kitNotExist")
    private String kitNotExistMessage = "Kit %weapon% existiert nicht, überprüfe deine Konfiguration!";
    @Setting("messages.notFighting")
    private String notFightingMessage = "%player% kämpft aktuell nicht!";
    @Setting("messages.nextFight")
    private String nextFightMessage = "Es kämpfen:";
    @Setting("messages.shuffleUneven")
    private String shuffleUnevenMessage = "Es kann nur eine gerade Anzahl von Kämpfern gegeneinander antreten.";
    @Setting("dice-list")
    private Set<String> diceList = new HashSet<String>(Arrays.asList("Schwert", "Bogen"));
  }

  /**
   * The top-level commands.
   */
  public class TopLevelCommand {

    /**
     * The {@code fight} command.
     *
     * @param args   the command-arguments
     * @param sender the CommandSender who initiated the command
     */
    @Command(aliases = {"fight", "f"}, desc = "Central command to manage fights")
    @NestedCommand(FightCommands.class)
    public void fightCmd(CommandContext args, CommandSender sender) {
    }
  }

  /**
   * All subcommands of the {@code fight} command.
   */
  public class FightCommands {

    /**
     * Starts a fight.
     *
     * @param args   the command-arguments
     * @param sender the CommandSender who initiated the command
     * @throws CommandException if the command is cancelled
     */
    @Command(aliases = {
        "set"}, usage = "[-c #] <player1> <player2> <...>", desc = "Starts a fight between multiple players", flags =
        "dlc:f", min = 2)
    @CommandPermissions({"fights.set"})
    public void set(CommandContext args, CommandSender sender) throws CommandException {
      Location
          lightning =
          args.hasFlag('l') ? PlayerUtil.checkPlayer(sender).getTargetBlock(null, 40).getLocation() : null;
      int count = DEFAULT_COUNTDOWN;

      if (args.hasFlag('c')) {
        CommandBook.inst().checkPermission(sender, "fights.countdown.custom");
        count = args.getFlagInteger('c');
      }

      // check if all given players are valid
      ArrayList<Player> fighters = new ArrayList<Player>();
      for (String targetName : args.getParsedSlice(0)) {
        Player player = InputUtil.PlayerParser.matchSinglePlayer(sender, targetName);
        if (fighters.contains(player.getName())) {
          throw new CommandException(config.fightAgainstMessage.replaceAll("%player%", player.getName()));
        }
        if (fightFactory.isFighting(player)) {
          throw new CommandException(config.alreadyFightingMessage.replaceAll("%player%", player.getName()));
        }
        fighters.add(player);
      }

      // get the weaponKit, if needed
      Kit weaponKit = null;
      if (args.hasFlag('d')) {
        String weapon = getRandomWeapon();
        weaponKit = kits.getKitManager().getKit(weapon);
        if (weaponKit == null) {
          throw new CommandException(config.kitNotExistMessage.replaceAll("%weapon%", weapon));
        }
        // no chance to stop now
        broadcast(config.diceMessage.replaceAll("%weapon%", weapon));
      }
      fightFactory.registerFight(count, lightning, args.hasFlag('f'), weaponKit, fighters);
    }

    /**
     * Stops a fight.
     *
     * @param args   the command-arguments
     * @param sender the CommandSender who initiated the command
     * @throws CommandException if the command is cancelled
     */
    @Command(aliases = {"stop"}, usage = "<player>", desc = "Stops running fights and countdowns", max = 1)
    @CommandPermissions({"fights.stop"})
    public void stop(CommandContext args, CommandSender sender) throws CommandException {
      if (args.argsLength() == 0) {
        if (countdownTask == null) {
          throw new CommandException(config.noCountdownRunningMessage);
        }
        countdownTask.cancel();
        countdownTask = null;
        broadcast(config.countdownStoppedMessage);
      } else {
        Player player = InputUtil.PlayerParser.matchSinglePlayer(sender, args.getString(0));
        if (!fightFactory.isFighting(player)) {
          throw new CommandException(config.notFightingMessage.replaceAll("%player%", ChatUtil.toName(player)));
        }
        fightFactory.stopFight(player);
        sender.sendMessage(
            ChatColor.AQUA + config.fightStoppedCmdMessage.replaceAll("%player%", ChatUtil.toName(player)));
      }
    }

    /**
     * Shuffles players into groups.
     *
     * @param args   the command-arguments
     * @param sender the CommandSender who initiated the command
     * @throws CommandException if the command is cancelled
     */
    @Command(aliases = {"shuffle", "tournament"}, flags = "sw", usage = "<player> <player> ...", desc =
        "Shuffles the given " + "player into groups")
    @CommandPermissions({"fights.shuffle"})
    public void shuffle(CommandContext args, CommandSender sender) throws CommandException {
      int groupSize = args.getFlagInteger('s', 2);
      if (!args.hasFlag('w') && args.argsLength() % groupSize != 0) {
        //no wildcarts allowed so groups must be even
        throw new CommandException(config.shuffleUnevenMessage);
      }
      ArrayList<String> players = new ArrayList<String>();
      for (String player : args.getSlice(0)) {
        players.add(ChatUtil.toName(InputUtil.PlayerParser.matchSinglePlayer(sender, player)));
      }
      Collections.shuffle(players, random);

      StrBuilder builder = new StrBuilder();
      builder.append(config.nextFightMessage);
      builder.append(' ');

      int groupNumber = 1;
      for (List<String> group : Lists.partition(players, groupSize)) {
        builder.append(groupNumber++);
        builder.append(". ");
        builder.appendWithSeparators(group, " vs. ");
        builder.appendSeparator(System.getProperty("line.separator"));
      }

      broadcast(builder.toString());
    }

    /**
     * Chooses a random weapon.
     *
     * @param args   the command-arguments
     * @param sender the CommandSender who initiated the command
     */
    @Command(aliases = {"dice"}, desc = "Roll the dice to chose between various weapons", max = 0)
    @CommandPermissions({"fights.dice"})
    public void dice(CommandContext args, CommandSender sender) {
      broadcast(config.diceMessage.replaceAll("%weapon%", getRandomWeapon()));
    }

    /**
     * Starts a countdown.
     *
     * @param args   the command-arguments
     * @param sender the CommandSender who initiated the command
     * @throws CommandException if the command is cancelled
     */
    @Command(aliases = {"countdown", "count"}, desc = "Counts down from the given number", flags = "lc:", max = 0)
    @CommandPermissions({"fights.countdown"})
    public void countdown(CommandContext args, CommandSender sender) throws CommandException {
      if (countdownTask != null) {
        throw new CommandException(config.countdownRunningMessage);
      }
      Location
          lightning =
          args.hasFlag('l') ? PlayerUtil.checkPlayer(sender).getTargetBlock(null, 40).getLocation() : null;
      int count = DEFAULT_COUNTDOWN;
      if (args.hasFlag('c')) {
        CommandBook.inst().checkPermission(sender, "fights.countdown.custom");
        count = args.getFlagInteger('c');
      }
      countdownTask = new Countdown(count, lightning, countdownTask).runTaskTimer(CommandBook.inst(), 20, 20);
    }
  }

  /**
   * Gets the session component.
   *
   * @return the session component
   */
  public SessionComponent getSessions() {
    return sessions;
  }

  /**
   * Gets the freezecomponent.
   *
   * @return the freeze component
   */
  public FreezeComponent getFreeze() {
    return freeze;
  }

  /**
   * A simple countdown.
   */
  public class Countdown extends BukkitRunnable {

    private int count;
    private final Location lightning;
    @SuppressWarnings("unused")
    private BukkitTask task;

    /**
     * Initializes this countdown.
     *
     * @param count     the count
     * @param lightning if set, a lightning strikes this location when the countdown finishes - can be {@code null}
     * @param task      the task reference of this countdown
     */
    public Countdown(int count, @Nullable Location lightning, BukkitTask task) {
      this.count = count;
      this.lightning = lightning;
      this.task = task;
    }

    @Override
    public void run() {
      if (count > 0) {
        broadcast(count-- + "...");
      } else {
        broadcast(config.fightMessage);
        if (lightning != null) {
          lightning.getWorld().strikeLightningEffect(lightning);
        }
        onFinish();
        cancel();
      }
    }

    /**
     * Called when the countdown finishes regularly.
     */
    protected void onFinish() {
      // classes extending this class can add specific methods here
    }

    /**
     * Called when the countdown is cancelled.
     */
    protected void onCancel() {
      // classes extending this class can add specific methods here
    }

    @Override
    public void cancel() {
      onCancel();
      task = null;
      super.cancel();
    }
  }
}
