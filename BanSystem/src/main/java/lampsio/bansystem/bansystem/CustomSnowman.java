package lampsio.bansystem.bansystem;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
public class CustomSnowman {

    private Snowman snowman;

    public CustomSnowman(Player target) {
        spawnSnowman(target);
    }

    public void spawnSnowman(Player target) {
        Location location = target.getLocation();
        snowman = location.getWorld().spawn(location, Snowman.class);
        snowman.setCustomName("SzalonyBalwan");
        snowman.setTarget(target);
        snowman.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255));
        snowman.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2));
        snowman.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,Integer.MAX_VALUE,2));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 5));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 1));
    }

    public Snowman getSnowman() {
        return snowman;
    }

    public void removeSnowman() {
        snowman.remove();
    }
}
