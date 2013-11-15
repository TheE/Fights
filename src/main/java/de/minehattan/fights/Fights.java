package de.minehattan.fights;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.sk89q.commandbook.CommandBook;
import com.sk89q.commandbook.FreezeComponent;
import com.sk89q.commandbook.kits.Kit;
import com.sk89q.commandbook.kits.KitsComponent;
import com.sk89q.commandbook.session.SessionComponent;
import com.sk89q.commandbook.session.SessionFactory;
import com.sk89q.commandbook.util.PlayerUtil;
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

@Depend(components = { SessionComponent.class, KitsComponent.class, FreezeComponent.class })
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

    public void broadcast(String message) {
        CommandBook.server().broadcastMessage(ChatColor.GRAY + "[Fights] " + ChatColor.AQUA + message);
    }

    private String chooseWeapon() {
        return (String) config.diceList.toArray()[random.nextInt(config.diceList.size())];
    }

    private static class LocalConfiguration extends ConfigurationBase {
        @Setting("messages.alradyFighting")
        public String alreadyFightingMessage = "%player% kämpft bereits!";
        @Setting("messages.countdownRunning")
        public String countdownRunningMessage = "Es läuft bereits ein Countdown!";
        @Setting("messages.noCountdownRunning")
        public String noCountdownRunningMessage = "Aktuell läuft kein globaler Countdown. 'Nutze /fight stop <Spieler>' um einen Kampf anzubrechen.";
        @Setting("messages.countdownStopped")
        public String countdownStoppedMessage = "Der Countdown wurde gestoppt!";
        @Setting("messages.dice")
        public String diceMessage = "Der nächste Kampf wird mit %weapon% geführt.";
        @Setting("messages.fight")
        public String fightMessage = "Kämpft!";
        @Setting("messages.fightAgainstHisself")
        public String fightAgainstMessage = "%player% kann nicht gegen sich selber kämpfen!";
        @Setting("messages.figthStoppedCmd")
        public String fightStoppedCmdMessage = "%player%s Kampf wurde abgebrochen!";
        @Setting("messages.kitNotExist")
        public String kitNotExistMessage = "Kit %weapon% existiert nicht, überprüfe deine Konfiguration!";
        @Setting("messages.notFighting")
        public String notFightingMessage = "%player% kämpft aktuell nicht!";
        @Setting("messages.nextFight")
        public String nextFightMessage = "Es kämpfen:";
        @Setting("messages.shuffleUneven")
        public String shuffleUnevenMessage = "Es kann nur eine gerade Anzahl von Kämpfern gegeneinander antreten.";
        @Setting("dice-list")
        public Set<String> diceList = new HashSet<String>(Arrays.asList("Schwert", "Bogen"));
    }

    public class TopLevelCommand {
        @Command(aliases = { "fight", "f" }, desc = "Central command to manage fights")
        @NestedCommand(Commands.class)
        public void fightCmd(CommandContext args, CommandSender sender) {
        }
    }

    public class Commands {
        @Command(aliases = { "set" }, usage = "[-c #] <player1> <player2> <...>", desc = "Starts a fight between multiple players", flags = "dlc:f", min = 2)
        @CommandPermissions({ "fights.set" })
        public void set(CommandContext args, CommandSender sender) throws CommandException {
            Location lightning = args.hasFlag('l') ? PlayerUtil.checkPlayer(sender).getEyeLocation() : null;
            int count = DEFAULT_COUNTDOWN;

            if (args.hasFlag('c')) {
                CommandBook.inst().checkPermission(sender, "fights.countdown.custom");
                count = args.getFlagInteger('c');
            }

            // check if all given players are valid
            ArrayList<Player> fighters = new ArrayList<Player>();
            for (String targetName : args.getParsedSlice(0)) { // 1 + flagLength
                Player player = PlayerUtil.matchSinglePlayer(sender, targetName);
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
                String weapon = chooseWeapon();
                weaponKit = kits.getKitManager().getKit(weapon);
                if (weaponKit == null) {
                    throw new CommandException(config.kitNotExistMessage.replaceAll("%weapon%", weapon));
                }
                // no chance to stop now
                broadcast(config.diceMessage.replaceAll("%weapon%", weapon));
            }
            fightFactory.registerFight(count, lightning, args.hasFlag('f'), weaponKit, fighters);
        }

        @Command(aliases = { "stop" }, usage = "<player>", desc = "Stops running fights and countdowns", max = 1)
        @CommandPermissions({ "fights.stop" })
        public void stop(CommandContext args, CommandSender sender) throws CommandException {
            if (args.argsLength() == 0) {
                if (countdownTask == null) {
                    throw new CommandException(config.noCountdownRunningMessage);
                }
                countdownTask.cancel();
                countdownTask = null;
                broadcast(config.countdownStoppedMessage);
            } else {
                Player player = PlayerUtil.matchSinglePlayer(sender, args.getString(0));
                if (!fightFactory.isFighting(player)) {
                    throw new CommandException(config.notFightingMessage.replaceAll("%player%",
                            PlayerUtil.toName(player)));
                }
                fightFactory.stopFight(player);
                sender.sendMessage(ChatColor.AQUA
                        + config.fightStoppedCmdMessage.replaceAll("%player%", PlayerUtil.toName(player)));
            }
        }

        @Command(aliases = { "shuffle", "tournament" }, desc = "Shuffles the given player into groups of two")
        @CommandPermissions({ "fights.shuffle" })
        public void shuffle(CommandContext args, CommandSender sender) throws CommandException {
            if (args.argsLength() % 2 != 0) {
                throw new CommandException(config.shuffleUnevenMessage);
            }
            ArrayList<String> players = new ArrayList<String>();
            for (String player : args.getSlice(0)) {
                players.add(PlayerUtil.toName(PlayerUtil.matchSinglePlayer(sender, player)));
            }
            Collections.shuffle(players, random);

            StringBuilder builder = new StringBuilder();

            for (int i = 0; i < players.size(); i = i + 2) {
                builder.append(players.get(i));
                builder.append(" vs. ");
                builder.append(players.get(i + 1));
                builder.append(", ");
            }

            broadcast(config.nextFightMessage + " " + builder.toString());
        }

        @Command(aliases = { "dice" }, desc = "Roll the dice to chose between various weapons", max = 0)
        @CommandPermissions({ "fights.dice" })
        public void dice(CommandContext args, CommandSender sender) throws CommandException {
            broadcast(config.diceMessage.replaceAll("%weapon%", chooseWeapon()));
        }

        @Command(aliases = { "countdown", "count" }, desc = "Counts down from the given number", flags = "lc:", max = 0)
        @CommandPermissions({ "fights.countdown" })
        public void countdown(CommandContext args, CommandSender sender) throws CommandException {
            if (countdownTask != null) {
                throw new CommandException(config.countdownRunningMessage);
            }
            Location lightning = args.hasFlag('l') ? PlayerUtil.checkPlayer(sender).getEyeLocation() : null;
            int count = DEFAULT_COUNTDOWN;
            if (args.hasFlag('c')) {
                CommandBook.inst().checkPermission(sender, "fights.countdown.custom");
                count = args.getFlagInteger('c');
            }
            countdownTask = new Countdown(count, lightning, countdownTask).runTaskTimer(CommandBook.inst(), 20, 20);
        }
    }

    public SessionComponent getSessions() {
        return sessions;
    }

    public FreezeComponent getFreeze() {
        return freeze;
    }

    public class Countdown extends BukkitRunnable {
        private int count;
        private final Location lightning;
        @SuppressWarnings("unused")
        private BukkitTask task;

        public Countdown(int count, Location lightning, BukkitTask task) {
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

        public void onFinish() {
            // classes extending this class can add specific methods here
        }

        public void onCancel() {
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