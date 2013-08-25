package de.minehattan.fights;

import java.util.Random;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.meta.FireworkMeta;

import com.sk89q.commandbook.CommandBook;

public class Fireworks {
    private static final int FIREWORK_REPEAT = 5;
    private static final int FIREWORK_OFFSET = 6;

    private final Type[] type = Type.values();
    private final Color[] colour = Color.class.getEnumConstants();

    private  Random random = new Random();

    private void firework(Location loc) {
        Firework firework1 = loc.getWorld().spawn(
                loc.clone().add(random.nextInt(FIREWORK_OFFSET), 0, random.nextInt(FIREWORK_OFFSET)), Firework.class);
        Firework firework2 = loc.getWorld().spawn(
                loc.clone().subtract(random.nextInt(FIREWORK_OFFSET), 0, random.nextInt(FIREWORK_OFFSET)),
                Firework.class);
        FireworkMeta data1 = (FireworkMeta) firework1.getFireworkMeta();
        FireworkMeta data2 = (FireworkMeta) firework2.getFireworkMeta();
        data1.addEffects(FireworkEffect.builder().withColor(colour[(random).nextInt(colour.length - 1)])
                .withColor(colour[(random).nextInt(colour.length - 1)])
                .withColor(colour[(random).nextInt(colour.length - 1)]).with(type[(random).nextInt(type.length - 1)])
                .trail((random).nextBoolean()).flicker((random).nextBoolean()).build());
        data2.addEffects(FireworkEffect.builder().withColor(colour[(random).nextInt(colour.length - 1)])
                .withColor(colour[(random).nextInt(colour.length - 1)])
                .withColor(colour[(random).nextInt(colour.length - 1)]).with(type[(random).nextInt(type.length - 1)])
                .trail((random).nextBoolean()).flicker((random).nextBoolean()).build());
        data1.setPower((random).nextInt(2) + 2);
        data2.setPower((random).nextInt(2) + 2);
        firework1.setFireworkMeta(data1);
        firework2.setFireworkMeta(data2);
    }

    public void showFirework(final Location loc) {
        for (int i = 0; i < FIREWORK_REPEAT; i++) {
            CommandBook.server().getScheduler().scheduleSyncDelayedTask(CommandBook.inst(), new Runnable() {
                @Override
                public void run() {
                    firework(loc);
                }
            }, 5L + i * 4);
        }
    }
}
