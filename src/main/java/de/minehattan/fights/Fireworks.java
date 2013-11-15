package de.minehattan.fights;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    private final List<Type> type = Arrays.asList(Type.values());
    private final List<Color> colors = new ArrayList<Color>();

    private Random random = new Random();

    public Fireworks() {
        //Reflection to get all predefined colors, even if new ones are added with newer minecraft versions.
        Field[] colourFields = Color.class.getFields();
        for (Field field : colourFields) {
            if (field.getType().isAssignableFrom(Color.class)) {
                try {
                    colors.add((Color) field.get(this));
                } catch (IllegalArgumentException e) {
                    CommandBook.logger().warning("Failed to add color-field " + field.getName() + ", ignoring it.");
                } catch (IllegalAccessException e) {
                    CommandBook.logger().warning("Failed to add color-field " + field.getName() + ", ignoring it.");
                }
            }
        }

    }

    private void firework(Location loc) {
        Firework firework1 = loc.getWorld().spawn(
                loc.clone().add(random.nextInt(FIREWORK_OFFSET), 0, random.nextInt(FIREWORK_OFFSET)), Firework.class);
        Firework firework2 = loc.getWorld().spawn(
                loc.clone().subtract(random.nextInt(FIREWORK_OFFSET), 0, random.nextInt(FIREWORK_OFFSET)),
                Firework.class);
        FireworkMeta data1 = (FireworkMeta) firework1.getFireworkMeta();
        FireworkMeta data2 = (FireworkMeta) firework2.getFireworkMeta();

        data1.addEffect(FireworkEffect.builder().withColor(getRandomColors(3))
                .with(type.get(random.nextInt(type.size()))).flicker((random).nextBoolean()).build());
        data2.addEffect(FireworkEffect.builder().withColor(getRandomColors(3))
                .with(type.get(random.nextInt(type.size()))).flicker((random).nextBoolean()).build());
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

    private Color[] getRandomColors(int count) {
        Color[] ret = new Color[count];
        for (int i = 0; i < count; i++) {
            ret[i] = (colors.get(random.nextInt(colors.size())));
        }
        return ret;
    }
}
