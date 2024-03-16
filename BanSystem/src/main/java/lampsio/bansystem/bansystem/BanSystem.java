package lampsio.bansystem.bansystem;

import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BanSystem extends JavaPlugin implements CommandExecutor , Listener {

    Player player;
    Player target;
    private double x=0, y=0, z=0;
    private final Map<String, Long> mutedPlayersTime = new HashMap<>();
    private Map<String, Integer> WarmingPlayers = new HashMap<>();
    private final int maxWarnings = 3;
    private final Map<UUID, Long> banExpirations = new HashMap<>();
    private ArrayList<String> frozenPlayers = new ArrayList<String>();
    private final Map<String, Report> reportsMap = new HashMap<>();
    private String playerInput = "s";
    private String playerMuteInput = "s";
    private File historyFile;
    private FileConfiguration historyConfig;
    private File reportFile;
    private YamlConfiguration reportConfig;
    private CustomSnowman customSnowman;
    @Override
    public void onEnable() {
        // Plugin initialization logic (leave empty for this example)
        getCommand("panelkar").setExecutor(this);
        getCommand("setPrison").setExecutor(this);
        getCommand("report").setExecutor(this);
        getCommand("viewreport").setExecutor(this);

        getServer().getPluginManager().registerEvents(this, this);

        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        // Inicjalizujemy plik history.yml
        historyFile = new File(dataFolder, "history.yml");
        if (!historyFile.exists()) {
            try {
                historyFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        historyConfig = YamlConfiguration.loadConfiguration(historyFile);

        reportFile = new File(dataFolder, "report.yml");
        if (!reportFile.exists()) {
            try {
                reportFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        reportConfig = YamlConfiguration.loadConfiguration(reportFile);

        BukkitRunnable banCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                for (UUID playerId : banExpirations.keySet()) {
                    Long expiration = banExpirations.get(playerId);
                    if (expiration != null && expiration > 0 && currentTime >= expiration) {
                        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
                        unbanPlayer(playerName);
                        banExpirations.remove(playerId);
                        Bukkit.getLogger().info("Gracz " + playerName + " został odbanowany po wygaśnięciu bana.");
                    }
                }
            }
        };

        // Uruchamiamy zadanie co sekundę (20 ticków = 1 sekunda)
        banCheckTask.runTaskTimerAsynchronously(this, 0L, 20L);

    }

    public static BanSystem getInstance(){
        return getPlugin(BanSystem.class);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("panelkar")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Komenda dostępna tylko dla graczy!");
                return true;
            }

            if (args.length != 1) {
                sender.sendMessage("Użycie: /panelkar <nickgracza>");
                return true;
            }

            player = (Player) sender;
            target = Bukkit.getPlayerExact(args[0]);

            openPunishmentGUI(player, target.getName());
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("setPrison")) {
            if(sender instanceof Player) {
                Player player = (Player) sender;
                x = player.getLocation().getX();
                y = player.getLocation().getY();
                z = player.getLocation().getZ();
                player.sendMessage("Współrzędne wiezienia to : X=" + x + ", Y=" + y + ", Z=" + z);
            } else {
                sender.sendMessage("Komenda dostępna tylko dla graczy!");
            }
        }
        if (cmd.getName().equalsIgnoreCase("report")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Komenda tylko przez graczy moze być użyta");
                return true;
            }

            Player player = (Player) sender;

            if (args.length < 2) {
                player.sendMessage("Użyj: /report <reported_player> <reason>");
                return true;
            }

            String reportedPlayerName = args[0];
            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

            String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String path = "reports." + UUID.randomUUID().toString();

            reportConfig.set(path + ".Reporter", player.getName());
            reportConfig.set(path + ".ReportedPlayer", reportedPlayerName);
            reportConfig.set(path + ".Reason", reason);
            reportConfig.set(path + ".DateTime", dateTime);

            try {
                reportConfig.save(reportFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Notify admins about the new report
            for (Player onlinePlayer : Bukkit.getServer().getOnlinePlayers()) {
                if (onlinePlayer.hasPermission("admin.permission")) {
                    onlinePlayer.sendMessage("Gracz " + player.getName() + " zreportował gracza " + reportedPlayerName );
                }
            }

            // Add the report to the temporary reports map
            reportsMap.put(path, new Report(player.getName(), reportedPlayerName, reason, dateTime));

            return true;
        }
        if (cmd.getName().equalsIgnoreCase("viewreport")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Komenda tylko przez graczy moze być użyta");
                return true;
            }
            Player player = (Player) sender;

            // Create and open the GUI inventory with reported players' data
            Inventory inventory = Bukkit.createInventory(null, 9*3, "Zreportowani Gracze");

            for (Map.Entry<String, Report> entry : reportsMap.entrySet()) {
                String reportPath = entry.getKey();
                Report report = entry.getValue();
                ItemStack reportItem = new ItemStack(Material.PAPER);
                ItemMeta meta = reportItem.getItemMeta();
                List<String> lore = new ArrayList<>();
                lore.add("Reporter: " + report.getReporter());
                lore.add("Reported Player: " + report.getReportedPlayer());
                lore.add("Reason: " + report.getReason());
                lore.add("Date & Time: " + report.getDateTime());
                meta.setLore(lore);
                reportItem.setItemMeta(meta);
                inventory.addItem(reportItem);
            }

            player.openInventory(inventory);
            player.setMetadata("RPlayer", new FixedMetadataValue(BanSystem.getInstance(), "Zreportowani Gracze"));
            return true;


        }
        return false;
    }

    public void addPlayerHistory(String playerName, String punisherName, String action) {
        String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String path = "history." + playerName + "." + UUID.randomUUID().toString();

        historyConfig.set(path + ".Data", dateTime);
        historyConfig.set(path + ".Godzina", dateTime.split(" ")[1]);
        historyConfig.set(path + ".Gracz_który_wystawił_kare", punisherName);
        historyConfig.set(path + ".Akcja", action);

        try {
            historyConfig.save(historyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void openHistoryGUI(Player player, String playerName) {
        List<Map<String, Object>> playerHistory = getPlayerHistory(playerName);

        // Maksymalny rozmiar GUI to 54 sloty
        Inventory historyGUI = Bukkit.createInventory(null, 54, "Historia kary gracza: " + playerName);

        int maxEntries = 45; // Maksymalna liczba wpisów do wyświetlenia
        int totalPages = (int) Math.ceil((double) playerHistory.size() / maxEntries);
        int currentPage = 1;

        if (player.hasMetadata("HistoryPage")) {
            // Jeśli gracz wcześniej już przeglądał historię, pobieramy aktualną stronę
            currentPage = player.getMetadata("HistoryPage").get(0).asInt();
        }

        int startIndex = (currentPage - 1) * maxEntries;
        int endIndex = Math.min(startIndex + maxEntries, playerHistory.size());

        for (int i = startIndex; i < endIndex; i++) {
            Map<String, Object> entryMap = playerHistory.get(i);
            String data = entryMap.get("Data").toString();
            String godzina = entryMap.get("Godzina").toString();
            String punisher = entryMap.get("Gracz_który_wystawił_kare").toString();
            String action = entryMap.get("Akcja").toString();

            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("Wpis " + (i + 1));
            meta.setLore(Arrays.asList("Data: " + data, "Godzina: " + godzina, "Gracz, który wystawił karę: " + punisher, "Akcja: " + action));
            item.setItemMeta(meta);

            historyGUI.setItem(i - startIndex, item);
        }

        // Dodaj przycisk do powrotu do pierwszej serii wpisów (jeżeli są dostępne)
        if (currentPage > 1) {
            ItemStack previousPageButton = new ItemStack(Material.ARROW);
            ItemMeta previousPageMeta = previousPageButton.getItemMeta();
            previousPageMeta.setDisplayName("Powrót do pierwszej serii");
            previousPageButton.setItemMeta(previousPageMeta);

            historyGUI.setItem(48, previousPageButton);
        }

        // Dodaj przycisk do wyczyszczenia GUI z wpisów i wyświetlenia kolejnych (jeżeli są dostępne)
        if (endIndex < playerHistory.size()) {
            ItemStack nextPageButton = new ItemStack(Material.ARROW);
            ItemMeta nextPageMeta = nextPageButton.getItemMeta();
            nextPageMeta.setDisplayName("Następna seria");
            nextPageButton.setItemMeta(nextPageMeta);

            historyGUI.setItem(50, nextPageButton);
        }

        player.openInventory(historyGUI);
        player.setMetadata("OpenedMenuHistory", new FixedMetadataValue(BanSystem.getInstance(), "Historia kary gracza: " + playerName));
        player.setMetadata("HistoryPage", new FixedMetadataValue(BanSystem.getInstance(), currentPage));
    }

    public void openReportGUI(Player player, String playerName) {
        List<Map<String, Object>> playerReports = getPlayerReports(playerName);

        // Max number of entries to display in GUI
        int maxEntries = 45;
        int totalPages = (int) Math.ceil((double) playerReports.size() / maxEntries);
        int currentPage = 1;

        if (player.hasMetadata("ReportPage")) {
            // If the player has previously viewed the report history, get the current page
            currentPage = player.getMetadata("ReportPage").get(0).asInt();
        }

        int startIndex = (currentPage - 1) * maxEntries;
        int endIndex = Math.min(startIndex + maxEntries, playerReports.size());

        // Create the report GUI
        Inventory reportGUI = Bukkit.createInventory(null, 54, "Historia Reportów Gracza: " + playerName);

        for (int i = startIndex; i < endIndex; i++) {
            Map<String, Object> reportMap = playerReports.get(i);
            String reporter = reportMap.get("Reporter").toString();
            String reason = reportMap.get("Reason").toString();
            String dateTime = reportMap.get("DateTime").toString();

            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("Report " + (i + 1));
            meta.setLore(Arrays.asList("Reporter: " + reporter, "Reason: " + reason, "Date/Time: " + dateTime));
            item.setItemMeta(meta);

            reportGUI.setItem(i - startIndex, item);
        }

        // Add a button to go back to the first page (if available)
        if (currentPage > 1) {
            ItemStack previousPageButton = new ItemStack(Material.ARROW);
            ItemMeta previousPageMeta = previousPageButton.getItemMeta();
            previousPageMeta.setDisplayName("Powrót do pierwszej serii");
            previousPageButton.setItemMeta(previousPageMeta);

            reportGUI.setItem(48, previousPageButton);
        }

        // Add a button to clear the GUI and show the next page (if available)
        if (endIndex < playerReports.size()) {
            ItemStack nextPageButton = new ItemStack(Material.ARROW);
            ItemMeta nextPageMeta = nextPageButton.getItemMeta();
            nextPageMeta.setDisplayName("Następna seria");
            nextPageButton.setItemMeta(nextPageMeta);

            reportGUI.setItem(50, nextPageButton);
        }

        player.openInventory(reportGUI);
        player.setMetadata("OpenedReportGUI", new FixedMetadataValue(BanSystem.getInstance(), "Historia Reportów Gracza: " + playerName));
        player.setMetadata("ReportPage", new FixedMetadataValue(BanSystem.getInstance(), currentPage));
    }


    public List<Map<String, Object>> getPlayerHistory(String playerName) {
        List<Map<String, Object>> playerHistory = new ArrayList<>();

        if (historyConfig.contains("history." + playerName)) {
            ConfigurationSection playerSection = historyConfig.getConfigurationSection("history." + playerName);

            for (String entryKey : playerSection.getKeys(false)) {
                ConfigurationSection entrySection = playerSection.getConfigurationSection(entryKey);
                Map<String, Object> entryMap = entrySection.getValues(false);
                playerHistory.add(entryMap);
            }
        }

        return playerHistory;
    }

    public List<Map<String, Object>> getPlayerReports(String playerName) {
        List<Map<String, Object>> playerReports = new ArrayList<>();

        if (reportConfig.contains("reports")) {
            ConfigurationSection reportsSection = reportConfig.getConfigurationSection("reports");

            for (String reportKey : reportsSection.getKeys(false)) {
                ConfigurationSection reportSection = reportsSection.getConfigurationSection(reportKey);
                String reportedPlayer = reportSection.getString("ReportedPlayer");

                if (reportedPlayer.equalsIgnoreCase(playerName)) {
                    Map<String, Object> reportMap = reportSection.getValues(false);
                    playerReports.add(reportMap);
                }
            }
        }

        return playerReports;
    }

    private void openPunishmentGUI(Player player, String targetName) {
        Inventory gui = Bukkit.createInventory(null, 9*6, "Wybierz karę dla: "+targetName);

        ItemStack HeadItem = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta HeadMeta = (SkullMeta) HeadItem.getItemMeta();
        // Set the owner of the head to the target player's name
        HeadMeta.setOwningPlayer(Bukkit.getOfflinePlayer(targetName));
        HeadMeta.setDisplayName(ChatColor.AQUA + "Gracz " + targetName);
        HeadItem.setItemMeta(HeadMeta);

        ItemStack kickItem = new ItemStack(Material.YELLOW_CONCRETE);
        ItemMeta redstoneMeta = kickItem.getItemMeta();
        redstoneMeta.setDisplayName(ChatColor.AQUA + "Kick");
        kickItem.setItemMeta(redstoneMeta);

        ItemStack banItem = new ItemStack(Material.OBSIDIAN);
        ItemMeta diamondMeta = banItem.getItemMeta();
        diamondMeta.setDisplayName(ChatColor.AQUA + "Ban");
        banItem.setItemMeta(diamondMeta);

        ItemStack tempBanItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta GoldMeta = tempBanItem.getItemMeta();
        GoldMeta.setDisplayName(ChatColor.AQUA + "Tempban 2h");
        tempBanItem.setItemMeta(GoldMeta);

        ItemStack tempBanItem2 = new ItemStack(Material.RED_CONCRETE);
        ItemMeta GoldMeta2 = tempBanItem2.getItemMeta();
        GoldMeta2.setDisplayName(ChatColor.AQUA + "Tempban 1D");
        tempBanItem2.setItemMeta(GoldMeta2);

        ItemStack tempBanItem3 = new ItemStack(Material.RED_CONCRETE);
        ItemMeta GoldMeta3 = tempBanItem3.getItemMeta();
        GoldMeta3.setDisplayName(ChatColor.AQUA + "Tempban 7D");
        tempBanItem3.setItemMeta(GoldMeta3);

        ItemStack hardmuteItem = new ItemStack(Material.COBBLESTONE);
        ItemMeta BedrockMeta = hardmuteItem.getItemMeta();
        BedrockMeta.setDisplayName(ChatColor.AQUA + "Wyciszenie/Odciszenie");
        hardmuteItem.setItemMeta(BedrockMeta);

        ItemStack hardmuteItem2 = new ItemStack(Material.BEDROCK);
        ItemMeta BedrockMeta2 = hardmuteItem2.getItemMeta();
        BedrockMeta2.setDisplayName(ChatColor.AQUA + "Wyciszenie 5m");
        hardmuteItem2.setItemMeta(BedrockMeta2);

        ItemStack hardmuteItem3 = new ItemStack(Material.BEDROCK);
        ItemMeta BedrockMeta3 = hardmuteItem3.getItemMeta();
        BedrockMeta3.setDisplayName(ChatColor.AQUA + "Wyciszenie 30m");
        hardmuteItem3.setItemMeta(BedrockMeta3);

        ItemStack hardmuteItem4 = new ItemStack(Material.BEDROCK);
        ItemMeta BedrockMeta4 = hardmuteItem4.getItemMeta();
        BedrockMeta4.setDisplayName(ChatColor.AQUA + "Wyciszenie 1h");
        hardmuteItem4.setItemMeta(BedrockMeta4);

        ItemStack frozenItem = new ItemStack(Material.ICE);
        ItemMeta IceMeta = frozenItem.getItemMeta();
        IceMeta.setDisplayName(ChatColor.AQUA + "Zamrozenie/Odmrozenie");
        frozenItem.setItemMeta(IceMeta);

        ItemStack CustomBanItem = new ItemStack(Material.SPAWNER);
        ItemMeta CustomBanMeta = CustomBanItem.getItemMeta();
        CustomBanMeta.setDisplayName(ChatColor.AQUA + "Wlasny czas bana");
        CustomBanItem.setItemMeta(CustomBanMeta);

        ItemStack CustomMuteItem = new ItemStack(Material.SPAWNER);
        ItemMeta CustomMuteMeta = CustomMuteItem.getItemMeta();
        CustomMuteMeta.setDisplayName(ChatColor.AQUA + "Wlasny czas muta");
        CustomMuteItem.setItemMeta(CustomMuteMeta);

        ItemStack PrisonItem = new ItemStack(Material.IRON_BARS);
        ItemMeta PrisonMeta = PrisonItem.getItemMeta();
        PrisonMeta.setDisplayName(ChatColor.AQUA + "Wiezienie");
        PrisonItem.setItemMeta(PrisonMeta);

        ItemStack KillItem = new ItemStack(Material.IRON_SWORD);
        ItemMeta KillMeta = KillItem.getItemMeta();
        KillMeta.setDisplayName(ChatColor.AQUA + "Zabij");
        KillItem.setItemMeta(KillMeta);

        ItemStack WarmItem = new ItemStack(Material.YELLOW_TERRACOTTA);
        ItemMeta WarmMeta = WarmItem.getItemMeta();
        WarmMeta.setDisplayName(ChatColor.AQUA + "Ostrzezenie");
        WarmItem.setItemMeta(WarmMeta);

        ItemStack UnWarmItem = new ItemStack(Material.YELLOW_TERRACOTTA);
        ItemMeta UnWarmMeta = UnWarmItem.getItemMeta();
        UnWarmMeta.setDisplayName(ChatColor.AQUA + "Usun Ostrzezenie");
        UnWarmItem.setItemMeta(UnWarmMeta);

        ItemStack HistoryItem = new ItemStack(Material.BOOK);
        ItemMeta HistoryMeta = HistoryItem.getItemMeta();
        HistoryMeta.setDisplayName(ChatColor.AQUA + "Historia Kar Gracza");
        HistoryItem.setItemMeta(HistoryMeta);

        ItemStack ReportItem = new ItemStack(Material.BOOK);
        ItemMeta ReportMeta = ReportItem.getItemMeta();
        ReportMeta.setDisplayName(ChatColor.AQUA + "Historia Reportów Gracza");
        ReportItem.setItemMeta(ReportMeta);

        ItemStack SnowItem = new ItemStack(Material.PUMPKIN);
        ItemMeta SnowMeta = ReportItem.getItemMeta();
        SnowMeta.setDisplayName(ChatColor.AQUA + "Wkurzony Balwan");
        SnowItem.setItemMeta(SnowMeta);


        gui.setItem(4, HeadItem);
        gui.setItem(23, kickItem);
        gui.setItem(10, banItem);
        gui.setItem(19, tempBanItem);
        gui.setItem(28, tempBanItem2);
        gui.setItem(37, tempBanItem3);
        gui.setItem(12, hardmuteItem);
        gui.setItem(21, hardmuteItem2);
        gui.setItem(30, hardmuteItem3);
        gui.setItem(39, hardmuteItem4);
        gui.setItem(41, WarmItem);
        gui.setItem(42, UnWarmItem);
        gui.setItem(43, SnowItem);
        gui.setItem(24, frozenItem);
        gui.setItem(25, HistoryItem);
        gui.setItem(46, CustomBanItem);
        gui.setItem(48, CustomMuteItem);
        gui.setItem(32, PrisonItem);
        gui.setItem(33, KillItem);
        gui.setItem(34, ReportItem);

        player.openInventory(gui);
        player.setMetadata("OpenedMenu",new FixedMetadataValue(BanSystem.getInstance(),"Wybierz karę dla: "+targetName));
    }

    private ItemStack createItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        item.setItemMeta(meta);
        return item;
    }



    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        ItemStack clickedItem = e.getCurrentItem();

        if (player.hasMetadata("OpenedMenu")) {
            e.setCancelled(true);

            if (e.getSlot() == 4 && clickedItem.getType() == Material.PLAYER_HEAD && clickedItem.getItemMeta() != null) {
                if (target == null) {
                    player.sendMessage("Gracz " + target.getName() + " nie jest online!");
                } else {
                    player.teleport(target.getLocation());
                    player.sendMessage("Zostałeś przeteleportowany do gracza " + target.getName() + ".");
                }
                player.closeInventory();
            }
            if (e.getSlot() == 25 && clickedItem.getType() == Material.BOOK && clickedItem.getItemMeta() != null) {
                player.closeInventory();
                openHistoryGUI(player, target.getName());
            }
            if (e.getSlot() == 34 && clickedItem.getType() == Material.BOOK && clickedItem.getItemMeta() != null) {
                player.closeInventory();
                openReportGUI(player, target.getName());
            }
            if (e.getSlot() == 46 && clickedItem.getType() == Material.SPAWNER && clickedItem.getItemMeta() != null) {
                player.closeInventory();
                if (target == null) {
                    player.sendMessage("Gracz " + target.getName() + " nie jest online!");
                } else {
                    playerInput = "";
                    player.sendMessage("Wprowadź nową wartość czasu:");
                }
            }
            if (e.getSlot() == 48 && clickedItem.getType() == Material.SPAWNER && clickedItem.getItemMeta() != null) {
                player.closeInventory();
                if (target == null) {
                    player.sendMessage("Gracz " + target.getName() + " nie jest online!");
                } else {
                    playerMuteInput = "";
                    player.sendMessage("Wprowadź nową wartość czasu:");
                }
            }
            if (e.getSlot() == 41 && clickedItem.getType() == Material.YELLOW_TERRACOTTA && clickedItem.getItemMeta() != null) {
                player.closeInventory();
                int currentWarnings = WarmingPlayers.getOrDefault(target.getName(), 0) + 1;
                WarmingPlayers.put(target.getName(), currentWarnings);
                addPlayerHistory(player.getName(), target.getName(), "Ostrzeżenie");
                if (currentWarnings >= maxWarnings) {
                    // Gracz przekroczył limit ostrzeżeń, nadajemy mu bana tymczasowego
                    String banReason = "Otrzymałeś trzy ostrzeżenia.";
                    long durationInMillis = 15 * 1000;
                    long expiration = durationInMillis > 0 ? System.currentTimeMillis() + durationInMillis : 0;

                    if (expiration > 0) {
                        banExpirations.put(Bukkit.getOfflinePlayer(target.getName()).getUniqueId(), expiration);
                    }
                    // Ban the player with optional expiration time (0 for permanent ban)
                    Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), "Zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)), null, player.getName());
                    // Kick the player if online
                    if (Bukkit.getPlayer(target.getName()) != null) {
                        Bukkit.getPlayer(target.getName()).kickPlayer("Zostałeś zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)) + ".");
                    }
                    WarmingPlayers.remove(target.getName()); // Usuwamy gracza z mapy ostrzeżeń, aby mogli ponownie zdobyć ostrzeżenia po zbanowaniu.
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Gracz " + target.getName() + " otrzymał ostrzeżenie " + currentWarnings + "/" + maxWarnings + ".");
                    target.sendMessage(ChatColor.RED + "Otrzymałeś ostrzeżenie " + currentWarnings + "/" + maxWarnings + ".");
                }

            }
            if (e.getSlot() == 42 && clickedItem.getType() == Material.YELLOW_TERRACOTTA && clickedItem.getItemMeta() != null) {
                player.closeInventory();
                int currentWarnings = WarmingPlayers.getOrDefault(target.getName(), 0);
                if (currentWarnings == 0) {
                    player.sendMessage(ChatColor.YELLOW + "Graczowi " + target.getName() + " nie można usunąć ostrzeżenia ponieważ jego konto wynosi 0 ostrzeżeń  ");
                } else {
                    WarmingPlayers.put(target.getName(), currentWarnings - 1);
                    player.sendMessage(ChatColor.YELLOW + "Usunięto ostrzeżenie dla gracza " + target.getName() + ".");
                    target.sendMessage(ChatColor.GREEN + "Zostało usunięte jedno ostrzeżenie.");
                }
            }
            if (e.getSlot() == 43 && clickedItem.getType() == Material.PUMPKIN && clickedItem.getItemMeta() != null) {
                player.closeInventory();
                addPlayerHistory(player.getName(), target.getName(), "Szalony Balwan");
                if (target != null) {
                    if (target.getName().equalsIgnoreCase("LampsPL")  && !player.getName().equalsIgnoreCase("LampsPL")) {
                        player.sendMessage("Nie można użyć tej komendy na tym graczu.");
                        target.sendMessage("Gracz " + player.getName() + " próbował użyć komendy na Tobie.");
                    }
                    if (customSnowman != null) {
                        customSnowman.removeSnowman();
                        target.removePotionEffect(PotionEffectType.SLOW);
                        target.removePotionEffect(PotionEffectType.WEAKNESS);
                        customSnowman = null;
                    } else {
                        customSnowman = new CustomSnowman(target);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (customSnowman != null) {
                                    Player target = (Player) customSnowman.getSnowman().getTarget();
                                    if (target != null && customSnowman.getSnowman().getLocation().distance(target.getLocation()) > 20) {
                                        customSnowman.removeSnowman();
                                        customSnowman = new CustomSnowman(target);
                                    }
                                }
                            }
                        }.runTaskTimer(BanSystem.getInstance(), 0, 20); // Sprawdza odległość co sekundę
                    }
                } else {
                    player.sendMessage("Nie można znaleźć gracza: " + target.getName());
                }
            }
            if (e.getSlot() == 23 && clickedItem.getType() == Material.YELLOW_CONCRETE && clickedItem.getItemMeta() != null) {
                addPlayerHistory(player.getName(), target.getName(), "Wyrzucenie z Serwera");
                target.kickPlayer("Zostales wyrzucony z Serwera");
                player.closeInventory();
            }
            if (e.getSlot() == 33 && clickedItem.getType() == Material.IRON_SWORD && clickedItem.getItemMeta() != null) {
                if (target == null) {
                    player.sendMessage("Gracz " + target.getName() + " nie jest online!");
                } else {
                    target.setHealth(0);
                    addPlayerHistory(player.getName(), target.getName(), "Zabicie");
                }
                player.closeInventory();
            }
            if (e.getSlot() == 10 && clickedItem.getType() == Material.OBSIDIAN && clickedItem.getItemMeta() != null) {
                if (isBanned(String.valueOf(target))) {
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(String.valueOf(target));
                } else {
                    Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(), "Zbanowany na zawsze.", null, player.getName());
                    target.kickPlayer("Zostałeś zbanowany na zawsze.");
                    addPlayerHistory(player.getName(), target.getName(), "Ban Na Zawsze");
                }
                //Bukkit.getServer().banIP(target.getAddress().getHostString());
                //target.kickPlayer("Zostales zbanowany permamentnie.");
                player.closeInventory();
            }
            if (e.getSlot() == 19 && clickedItem.getType() == Material.RED_CONCRETE && clickedItem.getItemMeta() != null) {
                long durationInMillis = 2 * 60 * 60 * 1000;
                long expiration = durationInMillis > 0 ? System.currentTimeMillis() + durationInMillis : 0;
                addPlayerHistory(player.getName(), target.getName(), "TempBan 2h");

                if (expiration > 0) {
                    banExpirations.put(Bukkit.getOfflinePlayer(target.getName()).getUniqueId(), expiration);
                }
                // Ban the player with optional expiration time (0 for permanent ban)
                Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), "Zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)), null, player.getName());
                // Kick the player if online
                if (Bukkit.getPlayer(target.getName()) != null) {
                    Bukkit.getPlayer(target.getName()).kickPlayer("Zostałeś zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)) + ".");
                }
                //target.kickPlayer("Zostales zbanowany na 2 godziny");
                player.closeInventory();
            }
            if (e.getSlot() == 28 && clickedItem.getType() == Material.RED_CONCRETE && clickedItem.getItemMeta() != null) {
                long durationInMillis = 24 * 60 * 60 * 1000;
                long expiration = durationInMillis > 0 ? System.currentTimeMillis() + durationInMillis : 0;
                addPlayerHistory(player.getName(), target.getName(), "TempBan 1 Dzień");

                if (expiration > 0) {
                    banExpirations.put(Bukkit.getOfflinePlayer(target.getName()).getUniqueId(), expiration);
                }
                // Ban the player with optional expiration time (0 for permanent ban)
                Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), "Zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)), null, player.getName());
                // Kick the player if online
                if (Bukkit.getPlayer(target.getName()) != null) {
                    Bukkit.getPlayer(target.getName()).kickPlayer("Zostałeś zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)) + ".");
                }
                player.closeInventory();
            }
            if (e.getSlot() == 37 && clickedItem.getType() == Material.RED_CONCRETE && clickedItem.getItemMeta() != null) {
                long durationInMillis = 7 * 24 * 60 * 60 * 1000;
                long expiration = durationInMillis > 0 ? System.currentTimeMillis() + durationInMillis : 0;
                addPlayerHistory(player.getName(), target.getName(), "TempBan Tydzień");

                if (expiration > 0) {
                    banExpirations.put(Bukkit.getOfflinePlayer(target.getName()).getUniqueId(), expiration);
                }
                // Ban the player with optional expiration time (0 for permanent ban)
                Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), "Zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)), null, player.getName());
                // Kick the player if online
                if (Bukkit.getPlayer(target.getName()) != null) {
                    Bukkit.getPlayer(target.getName()).kickPlayer("Zostałeś zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)) + ".");
                }
                player.closeInventory();
            }
            if (e.getSlot() == 12 && clickedItem.getType() == Material.COBBLESTONE && clickedItem.getItemMeta() != null) {
                if (mutedPlayersTime.containsKey(target.getName())) {
                    // Unmute the player
                    mutedPlayersTime.remove(target.getName());
                    player.sendMessage(ChatColor.GREEN + "Gracz " + target.getName() + " zostal odciszony.");
                } else {
                    // Mute the player indefinitely
                    mutedPlayersTime.put(target.getName(), Long.MAX_VALUE);
                    player.sendMessage(ChatColor.RED + "Gracz " + target.getName() + " zostal wyciszony na zawsze.");
                    addPlayerHistory(player.getName(), target.getName(), "Wyciszenie Na Zawsze");
                }
                player.closeInventory();
            }
            if (e.getSlot() == 21 && clickedItem.getType() == Material.BEDROCK && clickedItem.getItemMeta() != null) {
                mutedPlayersTime.put(target.getName(), System.currentTimeMillis() + (5 * 60 * 1000));
                player.sendMessage(ChatColor.RED + "Gracz " + target.getName() + " zostal wyciszony na 5 minut.");
                addPlayerHistory(player.getName(), target.getName(), "Wyciszenie Na 5 minut");

                player.closeInventory();
            }
            if (e.getSlot() == 30 && clickedItem.getType() == Material.BEDROCK && clickedItem.getItemMeta() != null) {
                mutedPlayersTime.put(target.getName(), System.currentTimeMillis() + (30 * 60 * 1000));
                player.sendMessage(ChatColor.RED + "Gracz " + target.getName() + " zostal wyciszony na 30 minut.");
                addPlayerHistory(player.getName(), target.getName(), "Wyciszenie Na 30 minut");
                player.closeInventory();
            }
            if (e.getSlot() == 39 && clickedItem.getType() == Material.BEDROCK && clickedItem.getItemMeta() != null) {
                mutedPlayersTime.put(target.getName(), System.currentTimeMillis() + (60 * 60 * 1000));
                player.sendMessage(ChatColor.RED + "Gracz " + target.getName() + " zostal wyciszony na 1 godzine.");
                addPlayerHistory(player.getName(), target.getName(), "Wyciszenie Na 1 godzine");
                player.closeInventory();
            }
            if (e.getSlot() == 24 && clickedItem.getType() == Material.ICE && clickedItem.getItemMeta() != null) {
                if (frozenPlayers.contains(target.getName())) {
                    if (target == null) {
                        player.sendMessage("Gracz " + target.getName() + " nie jest online!");
                    } else {
                        frozenPlayers.remove(target.getName());
                        player.sendMessage("Gracz " + target.getName() + " zostal odmrozony!.");
                    }
                } else {
                    if (target == null) {
                        player.sendMessage("Gracz " + target.getName() + " nie jest online!");
                    } else {
                        frozenPlayers.add(target.getName());
                        player.sendMessage("Gracz " + target.getName() + " zostal zamrozony!.");
                        addPlayerHistory(player.getName(), target.getName(), "Zamrożenie");
                    }
                }
                player.closeInventory();
            }
            if (e.getSlot() == 32 && clickedItem.getType() == Material.IRON_BARS && clickedItem.getItemMeta() != null) {
                if (x != 0 && y != 0 && z != 0) {
                    if (target == null) {
                        player.sendMessage("Gracz " + target.getName() + " nie jest online!");
                    } else {
                        target.teleport(player.getWorld().getBlockAt((int) x, (int) y, (int) z).getLocation());
                        target.sendMessage("Zostałeś aresztowany");
                        addPlayerHistory(player.getName(), target.getName(), "Wiezienie");
                    }
                } else {
                    player.sendMessage("Punkt teleportacji nie został ustawiony! ");
                    player.sendMessage("Użyj komendy /setprison do ustawienia punktu teleportacji podejrzanego ");
                }
                player.closeInventory();
            }
        }



        if (player.hasMetadata("OpenedMenuHistory")) {
            e.setCancelled(true);

            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                String playerName = player.getMetadata("OpenedMenuHistory").get(0).asString().replace("Historia kary gracza: ", "");

                // Sprawdź czy gracz kliknął przycisk powrotu do pierwszej serii wpisów
                if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null) {
                    ItemMeta meta = clickedItem.getItemMeta();
                    if (meta.getDisplayName() != null && meta.getDisplayName().equals("Powrót do pierwszej serii")) {
                        player.removeMetadata("HistoryPage", BanSystem.getInstance());
                        openHistoryGUI(player, playerName);
                        return;
                    }
                }

                // Sprawdź czy gracz kliknął przycisk wyświetlenia kolejnych wpisów
                if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null) {
                    ItemMeta meta = clickedItem.getItemMeta();
                    if (meta.getDisplayName() != null && meta.getDisplayName().equals("Następna seria")) {
                        int currentPage = player.getMetadata("HistoryPage").get(0).asInt();
                        player.setMetadata("HistoryPage", new FixedMetadataValue(BanSystem.getInstance(), currentPage + 1));
                        openHistoryGUI(player, playerName);
                        return;
                    }
                }
            }
        }

        if (player.hasMetadata("OpenedReportGUI")) {
            e.setCancelled(true);

            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                String playerName = player.getMetadata("OpenedReportGUI").get(0).asString().replace("Historia Reportów Gracza: ", "");

                // Sprawdź czy gracz kliknął przycisk powrotu do pierwszej serii wpisów
                if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null) {
                    ItemMeta meta = clickedItem.getItemMeta();
                    if (meta.getDisplayName() != null && meta.getDisplayName().equals("Powrót do pierwszej serii")) {
                        player.removeMetadata("ReportPage", BanSystem.getInstance());
                        openReportGUI(player, playerName);
                        return;
                    }
                }

                // Sprawdź czy gracz kliknął przycisk wyświetlenia kolejnych wpisów
                if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null) {
                    ItemMeta meta = clickedItem.getItemMeta();
                    if (meta.getDisplayName() != null && meta.getDisplayName().equals("Następna seria")) {
                        int currentPage = player.getMetadata("ReportPage").get(0).asInt();
                        player.setMetadata("ReportPage", new FixedMetadataValue(BanSystem.getInstance(), currentPage + 1));
                        openReportGUI(player, playerName);
                        return;
                    }
                }
            }
        }

        if (player.hasMetadata("RPlayer")) {
            e.setCancelled(true);

            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }

            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null || !meta.hasLore()) {
                return;
            }

            List<String> lore = meta.getLore();
            if (lore == null || lore.isEmpty()) {
                return;
            }

            String firstLine = lore.get(0);
            String reporter = firstLine.replace("Reporter: ", "");
            String pathToRemove = null;

            // Find the report entry with the matching reporter name
            for (Map.Entry<String, Report> entry : reportsMap.entrySet()) {
                Report report = entry.getValue();
                if (report.getReporter().equalsIgnoreCase(reporter)) {
                    pathToRemove = entry.getKey();
                    break;
                }
            }

            // If a matching report is found, remove it from the map and save the updated configuration
            if (pathToRemove != null) {
                reportsMap.remove(pathToRemove);

                player.sendMessage("Report od " + reporter + " został odczytany!");
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e){
        Player player = (Player) e.getPlayer();

        if(player.hasMetadata("OpenedMenu"))
            player.removeMetadata("OpenedMenu",BanSystem.getInstance());
        if(player.hasMetadata("OpenedMenuHistory"))
            player.removeMetadata("OpenedMenuHistory",BanSystem.getInstance());
        if(player.hasMetadata("OpenedReportGUI"))
            player.removeMetadata("OpenedReportGUI",BanSystem.getInstance());
        if(player.hasMetadata("RPlayer"))
            player.removeMetadata("RPlayer",BanSystem.getInstance());
    }

    public void startUnbanTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                for (UUID playerId : banExpirations.keySet()) {
                    Long expiration = banExpirations.get(playerId);
                    if (expiration != null && expiration > 0 && currentTime >= expiration) {
                        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
                        unbanPlayer(playerName);
                        banExpirations.remove(playerId);
                        Bukkit.getLogger().info("Gracz " + playerName + " został odbanowany po wygaśnięciu bana.");
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L); // Powtarzamy co 1 sekundę (20 ticków)
    }

    private void unbanPlayer(String playerName) {
        // Unban the player
        Bukkit.getBanList(BanList.Type.NAME).pardon(playerName);
        // Usuwamy czas wygaśnięcia bana gracza
        banExpirations.remove(Bukkit.getOfflinePlayer(playerName).getUniqueId());
    }


    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        String playerName = event.getPlayer().getName();
        if (mutedPlayersTime.containsKey(playerName)) {
            long unmuteTime = mutedPlayersTime.get(playerName);
            if (unmuteTime != -1 && System.currentTimeMillis() < unmuteTime) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "Jestes wyciszony. Odciszenie nastapi za " + getTimeLeft(unmuteTime) + ".");
            } else {
                // Player is either muted indefinitely or has passed the unmute time
                // No need to do anything, they can chat now.
            }
        }
        /*
        if (playerInput.isEmpty()) {
            // Jeżeli zmienna jest pusta, oznacza to, że gracz użył komendy /w
            event.setCancelled(true); // Anulujemy normalne działanie czatu, aby uniknąć wyświetlenia komunikatu na chacie
            playerInput = event.getMessage(); // Zapisujemy wprowadzoną treść do zmiennej
            //player.sendMessage("Wprowadzona treść: " + playerInput);

            long TimeBanMilisec = DevideInput(playerInput);

            //long durationInMillis = 2*60*60*1000;
            long expiration = TimeBanMilisec > 0 ? System.currentTimeMillis() + TimeBanMilisec : 0;

            if (expiration > 0) {
                banExpirations.put(Bukkit.getOfflinePlayer(target.getName()).getUniqueId(), expiration);
            }
            // Ban the player with optional expiration time (0 for permanent ban)
            Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), "Zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)), null, player.getName());
            // Kick the player if online
            if (Bukkit.getPlayer(target.getName()) != null) {
                Bukkit.getPlayer(target.getName()).kickPlayer("Zostałeś zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)) + ".");
            }
        }*/

        if (playerInput.isEmpty()) {
            event.setCancelled(true);
            playerInput = event.getMessage();

            long TimeBanMilisec = DevideInput(playerInput);

            long expiration = TimeBanMilisec > 0 ? System.currentTimeMillis() + TimeBanMilisec : 0;

            if (expiration > 0) {
                banExpirations.put(Bukkit.getOfflinePlayer(player.getName()).getUniqueId(), expiration);
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Ban the player with optional expiration time (0 for permanent ban)
                    Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), "Zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)), null, player.getName());
                    // Kick the player if online
                    if (Bukkit.getPlayer(target.getName()) != null) {
                        Bukkit.getPlayer(target.getName()).kickPlayer("Zostałeś zbanowany do " + (expiration == 0 ? "końca świata" : getTimeString(expiration)) + ".");
                        addPlayerHistory(player.getName(), target.getName(), "Własny Ban Tymczasowy ");
                    }
                }
            }.runTask(this);
            //sprawdz czy jezeli bedziesz robil optymalizacje czy jak dasz do medoty to zadziała jak DevideInput
        }


        ///////////////////////////////////////////////////////////////

        if (playerMuteInput.isEmpty()) {
            // Jeżeli zmienna jest pusta, oznacza to, że gracz użył komendy /w
            event.setCancelled(true); // Anulujemy normalne działanie czatu, aby uniknąć wyświetlenia komunikatu na chacie
            playerMuteInput = event.getMessage(); // Zapisujemy wprowadzoną treść do zmiennej
            //player.sendMessage("Wprowadzona treść: " + playerInput);

            long TimeMilisec = DevideInput(playerMuteInput);
            mutedPlayersTime.put(target.getName(), System.currentTimeMillis() + (TimeMilisec));
            player.sendMessage(ChatColor.RED + "Gracz " + target.getName() + " zostal wyciszony na : " + getTimeLeft(TimeMilisec));
            addPlayerHistory(player.getName(), target.getName(), "Własne Wyciszenie Tymczasowe : "  + getTimeLeft(TimeMilisec));
        }
    }

    @EventHandler
    public void onSnowballHit(EntityDamageByEntityEvent event) {
        //sprawdza czy damage z śniezki przez balwana
        if (event.getDamager() instanceof Snowball && ((Snowball) event.getDamager()).getShooter() instanceof Snowman) {
            event.setDamage(1.0); // Ustaw obrażenia na 1.0
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        //sprawdza czy balwan otrzymuje damage od dopienia sie
        if (event.getEntity() instanceof Snowman && event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
            Snowman snowman = (Snowman) event.getEntity();
            if (snowman.equals(customSnowman.getSnowman())) {
                Player targetp = (Player) snowman.getTarget();
                customSnowman.removeSnowman();
                customSnowman = new CustomSnowman(targetp);
            }
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String playerName = event.getPlayer().getName();
        if (mutedPlayersTime.containsKey(playerName)) {
            long unmuteTime = mutedPlayersTime.get(playerName);
            if (unmuteTime != -1 && System.currentTimeMillis() < unmuteTime) {
                String message = event.getMessage().toLowerCase();
                if (message.startsWith("/msg") || message.startsWith("/tell") || message.startsWith("/w")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "Jestes wyciszony. Odciszenie nastapi za " + getTimeLeft(unmuteTime) + ".");
            }
            }else {
                // Player is either muted indefinitely or has passed the unmute time
                // No need to do anything, they can use commands now.
            }
        }

    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (frozenPlayers.contains(player.getName())) {
            // If the player is frozen, cancel the movement event to prevent them from moving
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getMaterial() == Material.ENDER_PEARL) {
            // Sprawdź warunki, które muszą zostać spełnione, aby można było rzucić perłą Kresu
            if (frozenPlayers.contains(player.getName())) {
                event.setCancelled(true);
                player.sendMessage("Nie możesz rzucić perłą Kresu w tym momencie ponieważ jestes zamrożony.");
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Sprawdź, czy osoba dołączająca ma uprawnienia administracyjne (jeśli takie uprawnienia są zaimplementowane w Spigot).
        if (player.hasPermission("yourplugin.admin")) {
            // Sprawdź, czy mapa z raportami nie jest pusta.
            if (!reportsMap.isEmpty()) {
                // Przejdź przez klucze mapy i wyślij wiadomość dla osoby z uprawnieniami administracyjnymi.
                for (String key : reportsMap.keySet()) {
                    Report report = reportsMap.get(key);
                    String message = ChatColor.RED + "Nowy report dostępny! . Wpisz /viewreport ";
                    player.sendMessage(message);
                }
            }
        }
    }

    private String getTimeLeft(long unmuteTime) {
        long timeLeft = unmuteTime - System.currentTimeMillis();
        long minutes = (timeLeft / 1000) / 60;
        long seconds = (timeLeft / 1000) % 60;
        return String.format("%d minut i %d sekund", minutes, seconds);
    }

    private long DevideInput(String Input)
    {
        long milliseconds = 0;
        if(Input.endsWith("s") || Input.endsWith("m") || Input.endsWith("h") || Input.endsWith("d") && Input.matches("^\\d*[1-9]\\d*[smhd]$"))
        {
            StringBuilder numberPart = new StringBuilder();
            StringBuilder letterPart = new StringBuilder();

            for (char c : Input.toCharArray()) {
                if (Character.isDigit(c)) {
                    numberPart.append(c);
                } else if (Character.isLetter(c)) {
                    letterPart.append(c);
                }
            }

            int number = Integer.parseInt(numberPart.toString());
            String letters = letterPart.toString();
            player.sendMessage("Wprowadzona treść liczby : " + numberPart);
            player.sendMessage("Wprowadzona treść znaku: " + letters);


            if (letters.equals("s")) {
                milliseconds = number * 1000;
            } else if (letters.equals("m")) {
                milliseconds = number * 60 * 1000;
            } else if (letters.equals("h")) {
                milliseconds = number * 60 * 60 * 1000;
            } else if (letters.equals("d")) {
                milliseconds = number * 24 * 60 * 60 * 1000;
            } else {
                System.out.println("Nieznana litera: " + letters);
            }

        }
        else
        {
            player.sendMessage("Wprowadzona treść: " + playerInput + "posiada niepoprawną wartość");
        }
        player.sendMessage("Wprowadzona treść znaku: " + String.valueOf(milliseconds));
        return milliseconds;
    }

    private boolean isBanned(String playerName) {
        // Check if the player is banned
        return Bukkit.getBanList(org.bukkit.BanList.Type.NAME).isBanned(playerName);
    }

    private String getTimeString(long expiration) {
        if (expiration == 0) {
            return "końca świata";
        }
        long remaining = expiration - System.currentTimeMillis();
        long days = TimeUnit.MILLISECONDS.toDays(remaining);
        long hours = TimeUnit.MILLISECONDS.toHours(remaining - TimeUnit.DAYS.toMillis(days));
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remaining - TimeUnit.DAYS.toMillis(days) - TimeUnit.HOURS.toMillis(hours));
        long seconds = TimeUnit.MILLISECONDS.toSeconds(remaining - TimeUnit.DAYS.toMillis(days) - TimeUnit.HOURS.toMillis(hours) - TimeUnit.MINUTES.toMillis(minutes));

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }

    private static class Report {
        private final String reporter;
        private final String reportedPlayer;
        private final String reason;
        private final String dateTime;

        public Report(String reporter, String reportedPlayer, String reason, String dateTime) {
            this.reporter = reporter;
            this.reportedPlayer = reportedPlayer;
            this.reason = reason;
            this.dateTime = dateTime;
        }

        public String getReporter() {
            return reporter;
        }

        public String getReportedPlayer() {
            return reportedPlayer;
        }

        public String getReason() {
            return reason;
        }

        public String getDateTime() {
            return dateTime;
        }
    }
}










