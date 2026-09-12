package tr.pazarci;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"deprecation", "null"})
public final class PazarciPlugin extends JavaPlugin implements Listener {
    private final Map<String, Market> markets = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedMarkets = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider != null) economy = provider.getProvider();
        loadMarkets();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Pazarci aktif. Vault ekonomi: " + (economy != null));
    }

    @Override
    public void onDisable() {
        saveMarkets();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChestInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof Chest)) return;
        Player player = event.getPlayer();
        String key = key(event.getClickedBlock());
        Market market = markets.get(key);
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && market == null) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) { player.sendMessage(color("&cElinde satilacak bir esya olmali.")); event.setCancelled(true); return; }
            if (pending.putIfAbsent(player.getUniqueId(), new Pending(PendingType.SETUP, key, hand.clone())) == null) {
                player.sendMessage(color(getConfig().getString("messages.setup-question")));
                scheduleTimeout(player.getUniqueId(), 10);
            }
            event.setCancelled(true);
        }
        if (market != null && event.getAction() == Action.LEFT_CLICK_BLOCK && market.mode() == Mode.SELL) {
            event.setCancelled(true);
            if (pending.putIfAbsent(player.getUniqueId(), new Pending(PendingType.BUY, key, market.item())) == null) {
                player.sendMessage(color(getConfig().getString("messages.buy-question")));
                scheduleTimeout(player.getUniqueId(), 5);
            }
        }
        if (market != null && event.getAction() == Action.LEFT_CLICK_BLOCK && market.mode() == Mode.BUY) {
            event.setCancelled(true);
            if (pending.putIfAbsent(player.getUniqueId(), new Pending(PendingType.SELL, key, market.item())) == null) {
                player.sendMessage(color(getConfig().getString("messages.sell-question")));
                scheduleTimeout(player.getUniqueId(), 5);
            }
        }
        if (market != null && event.getAction() == Action.RIGHT_CLICK_BLOCK && !market.owner().equals(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof Sign)) return;
        String signKey = key(event.getClickedBlock());
        Market market = markets.values().stream().filter(value -> signKey.equals(value.signKey())).findFirst().orElse(null);
        if (market == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (market.owner().equals(player.getUniqueId())) { selectedMarkets.put(player.getUniqueId(), market.chestKey()); showPanel(player, market); } else showInfo(player, market);
        } else if (event.getAction() == Action.LEFT_CLICK_BLOCK && market.mode() == Mode.SELL) {
            if (pending.putIfAbsent(player.getUniqueId(), new Pending(PendingType.BUY, market.chestKey(), market.item())) == null) {
                player.sendMessage(color(getConfig().getString("messages.buy-question")));
                scheduleTimeout(player.getUniqueId(), 5);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Pending action = pending.remove(event.getPlayer().getUniqueId());
        if (action == null) return;
        event.setCancelled(true);
        String text = event.getMessage().trim();
        Bukkit.getScheduler().runTask(this, () -> complete(event.getPlayer(), action, text));
    }

    private void complete(Player player, Pending action, String text) {
        int amount;
        try { amount = Integer.parseInt(text); if (amount <= 0) throw new NumberFormatException(); }
        catch (NumberFormatException exception) { player.sendMessage(color(getConfig().getString("messages.invalid-number"))); return; }
        if (action.type() == PendingType.SETUP) createMarket(player, action, amount);
        else if (action.type() == PendingType.BUY) buy(player, action, amount);
        else sell(player, action, amount);
    }

    private void createMarket(Player player, Pending action, int price) {
        if (markets.containsKey(action.chestKey())) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!sameItem(hand, action.item())) return;
        String signKey = createSign(action.chestKey(), player);
        if (signKey == null) { player.sendMessage(color("&cSandigin oyuncuya bakan yuzunde tabela icin yer yok.")); return; }
        Market market = new Market(action.chestKey(), player.getUniqueId(), action.item().clone(), price, Mode.SELL, signKey);
        synchronized (lock(action.chestKey())) {
            if (markets.putIfAbsent(action.chestKey(), market) == null) { updateSign(market); saveMarkets(); player.sendMessage(color("&aPazar kuruldu.")); }
        }
    }

    private void buy(Player player, Pending action, int amount) {
        Market market = markets.get(action.chestKey());
        if (market == null) return;
        synchronized (lock(market.chestKey())) {
            Inventory inventory = chestInventory(market.chestKey());
            if (count(inventory, market.item()) < amount) { player.sendMessage(color("&cStokta yeterli urun yok.")); return; }
            double total = market.price() * amount;
            if (economy == null || !economy.has(player, total)) { player.sendMessage(color("&cBakiyen yetersiz.")); return; }
            if (!economy.withdrawPlayer(player, total).transactionSuccess()) return;
            if (!economy.depositPlayer(Bukkit.getOfflinePlayer(market.owner()), total).transactionSuccess()) { economy.depositPlayer(player, total); return; }
            remove(inventory, market.item(), amount);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(copy(market.item(), amount));
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            updateSign(market);
            player.sendMessage(color("&aAlis tamamlandi. Toplam: &e" + total));
        }
    }

    private void sell(Player player, Pending action, int amount) {
        Market market = markets.get(action.chestKey());
        if (market == null || market.mode() != Mode.BUY) return;
        synchronized (lock(market.chestKey())) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!sameItem(hand, market.item()) || hand.getAmount() < amount) { player.sendMessage(color("&cElinde yeterli urun yok.")); return; }
            double total = market.price() * amount;
            OfflinePlayer owner = Bukkit.getOfflinePlayer(market.owner());
            if (economy == null || !economy.has(owner, total)) { player.sendMessage(color("&cPazar sahibinin bakiyesi yetersiz.")); return; }
            Inventory inventory = chestInventory(market.chestKey());
            ItemStack[] before = Arrays.stream(inventory.getContents())
                    .map(item -> item == null ? null : item.clone())
                    .toArray(ItemStack[]::new);
            hand.setAmount(hand.getAmount() - amount);
            Map<Integer, ItemStack> leftovers = inventory.addItem(copy(market.item(), amount));
            if (!leftovers.isEmpty()) {
                inventory.setContents(before);
                hand.setAmount(hand.getAmount() + amount);
                player.sendMessage(color("&cPazar sandiginda yeterli yer yok."));
                return;
            }
            if (!economy.withdrawPlayer(owner, total).transactionSuccess()) {
                remove(inventory, market.item(), amount);
                hand.setAmount(hand.getAmount() + amount);
                return;
            }
            if (!economy.depositPlayer(player, total).transactionSuccess()) {
                economy.depositPlayer(owner, total);
                remove(inventory, market.item(), amount);
                hand.setAmount(hand.getAmount() + amount);
                return;
            }
            updateSign(market);
            player.sendMessage(color("&aSatis tamamlandi. Kazanc: &e" + total));
        }
    }

    private void showInfo(Player player, Market market) { player.sendMessage(color("&6Pazar &7| &f" + market.item().getType() + " &7| Fiyat: &e" + market.price() + " &7| Stok: &e" + stock(market))); }
    private void showPanel(Player player, Market market) { player.sendMessage(color("&6Pazar paneli &7| &e/pazar kapat &7| &e/pazar al &7| &e/pazar sat &7| &e/pazar fiyat <sayi>")); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        String chestKey = selectedMarkets.get(player.getUniqueId());
        Market market = chestKey == null ? null : markets.get(chestKey);
        if (market == null || !market.owner().equals(player.getUniqueId())) { player.sendMessage(color("&cOnce bir pazar tabelasina sag tiklamalisin.")); return true; }
        if (args.length == 0) return true;
        if (args[0].equalsIgnoreCase("kapat")) { markets.remove(chestKey); selectedMarkets.remove(player.getUniqueId()); saveMarkets(); player.sendMessage(color("&aPazar kapatildi.")); return true; }
        if (args[0].equalsIgnoreCase("al") || args[0].equalsIgnoreCase("sat")) {
            Mode mode = args[0].equalsIgnoreCase("al") ? Mode.BUY : Mode.SELL;
            Market changed = new Market(market.chestKey(), market.owner(), market.item(), market.price(), mode, market.signKey());
            markets.put(chestKey, changed); updateSign(changed); saveMarkets(); player.sendMessage(color("&aPazar modu degisti: &e" + mode.display)); return true;
        }
        if (args[0].equalsIgnoreCase("fiyat") && args.length > 1) {
            try {
                int price = Integer.parseInt(args[1]);
                if (price <= 0) throw new NumberFormatException();
                Market changed = new Market(market.chestKey(), market.owner(), market.item(), price, market.mode(), market.signKey());
                markets.put(chestKey, changed); updateSign(changed); saveMarkets(); player.sendMessage(color("&aFiyat guncellendi."));
            } catch (NumberFormatException exception) { player.sendMessage(color("&cFiyat pozitif bir tam sayi olmali.")); }
        }
        return true;
    }

    private void updateSign(Market market) {
        if (!(block(market.signKey()).getState() instanceof Sign sign)) return;
        int stock = stock(market);
        String ownerName = Bukkit.getOfflinePlayer(market.owner()).getName();
        if (ownerName == null) ownerName = "Bilinmiyor";
        sign.getSide(org.bukkit.block.sign.Side.FRONT).setLine(0, "[PAZAR]");
        sign.getSide(org.bukkit.block.sign.Side.FRONT).setLine(1, shorten(market.mode().display + ":" + ownerName));
        sign.getSide(org.bukkit.block.sign.Side.FRONT).setLine(2, shorten(market.item().getType().name()));
        sign.getSide(org.bukkit.block.sign.Side.FRONT).setLine(3, stock == 0 ? "Stokta yok" : shorten(market.price() + " TL / " + stock));
        sign.update(true, false);
    }

    private String shorten(String text) { return text.substring(0, Math.min(15, text.length())); }

    private void loadMarkets() {
        File file = new File(getDataFolder(), "markets.yml");
        if (!file.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection("markets");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String path = "markets." + id;
            try {
                String chestKey = data.getString(path + ".chest");
                String signKey = data.getString(path + ".sign");
                String ownerText = data.getString(path + ".owner");
                ItemStack item = data.getItemStack(path + ".item");
                Mode mode = Mode.valueOf(data.getString(path + ".mode", Mode.SELL.name()));
                int price = data.getInt(path + ".price");
                if (chestKey == null || signKey == null || ownerText == null || item == null || price <= 0) continue;
                if (Bukkit.getWorld(chestKey.split(":", 2)[0]) == null) continue;
                if (!(block(chestKey).getState() instanceof Chest) || !(block(signKey).getState() instanceof Sign)) continue;
                markets.put(chestKey, new Market(chestKey, UUID.fromString(ownerText), item, price, mode, signKey));
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Gecersiz pazar kaydi atlandi: " + id);
            }
        }
    }

    private void saveMarkets() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Pazar veri klasoru olusturulamadi.");
            return;
        }
        YamlConfiguration data = new YamlConfiguration();
        int index = 0;
        for (Market market : markets.values()) {
            String path = "markets." + index++;
            data.set(path + ".chest", market.chestKey());
            data.set(path + ".sign", market.signKey());
            data.set(path + ".owner", market.owner().toString());
            data.set(path + ".item", market.item());
            data.set(path + ".price", market.price());
            data.set(path + ".mode", market.mode().name());
        }
        try {
            data.save(new File(getDataFolder(), "markets.yml"));
        } catch (IOException exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Pazarlar kaydedilemedi.", exception);
        }
    }

    private int stock(Market market) { return count(chestInventory(market.chestKey()), market.item()); }
    private void scheduleTimeout(UUID uuid, int seconds) { Bukkit.getScheduler().runTaskLater(this, () -> { Pending action = pending.get(uuid); if (action != null) { pending.remove(uuid, action); Player player = Bukkit.getPlayer(uuid); if (player != null) player.sendMessage(color(getConfig().getString("messages.timeout"))); } }, seconds * 20L); }
    private Object lock(String key) { return locks.computeIfAbsent(key, ignored -> new Object()); }
    private String key(Block block) { return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ(); }
    private String createSign(String chestKey, Player player) {
        Block chest = block(chestKey);
        BlockFace face = faceTowardPlayer(chest, player);
        Block signBlock = chest.getRelative(face);
        if (!signBlock.getType().isAir()) return null;
        signBlock.setType(Material.OAK_WALL_SIGN, false);
        WallSign wallSign = (WallSign) signBlock.getBlockData();
        wallSign.setFacing(face);
        signBlock.setBlockData(wallSign, false);
        return key(signBlock);
    }

    private BlockFace faceTowardPlayer(Block chest, Player player) {
        int xDistance = player.getLocation().getBlockX() - chest.getX();
        int zDistance = player.getLocation().getBlockZ() - chest.getZ();
        if (Math.abs(xDistance) >= Math.abs(zDistance)) return xDistance >= 0 ? BlockFace.EAST : BlockFace.WEST;
        return zDistance >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }
    private Block block(String value) { String[] split = value.split(":", 4); return Bukkit.getWorld(split[0]).getBlockAt(Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3])); }
    private Inventory chestInventory(String key) { return ((Chest) block(key).getState()).getInventory(); }
    private int count(Inventory inventory, ItemStack wanted) { int total = 0; for (ItemStack item : inventory.getContents()) if (sameItem(item, wanted)) total += item.getAmount(); return total; }
    private void remove(Inventory inventory, ItemStack wanted, int amount) { for (int i = 0; i < inventory.getSize() && amount > 0; i++) { ItemStack item = inventory.getItem(i); if (!sameItem(item, wanted)) continue; int take = Math.min(amount, item.getAmount()); item.setAmount(item.getAmount() - take); amount -= take; if (item.getAmount() == 0) inventory.setItem(i, null); } }
    private boolean sameItem(ItemStack left, ItemStack right) { return left != null && right != null && left.getType() != Material.AIR && left.isSimilar(right); }
    private ItemStack copy(ItemStack item, int amount) { ItemStack copy = item.clone(); copy.setAmount(amount); return copy; }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text); }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        String brokenKey = key(event.getBlock());
        if (markets.containsKey(brokenKey) || markets.values().stream().anyMatch(market -> brokenKey.equals(market.signKey()))) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(color("&cPazar sandigi veya tabelasi kirilamaz."));
        }
    }

    private record Market(String chestKey, UUID owner, ItemStack item, int price, Mode mode, String signKey) { }
    private record Pending(PendingType type, String chestKey, ItemStack item) { }
    private enum PendingType { SETUP, BUY, SELL }
    private enum Mode { BUY("ALIM"), SELL("SATIS"); private final String display; Mode(String display) { this.display = display; } }
}