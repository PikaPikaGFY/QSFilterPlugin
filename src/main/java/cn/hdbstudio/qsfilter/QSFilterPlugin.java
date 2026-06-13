package cn.hdbstudio.qsfilter;

import cn.hdbstudio.qsfilter.config.PluginConfig;
import cn.hdbstudio.qsfilter.quickshop.QuickShopBridge;
import cn.hdbstudio.qsfilter.quickshop.ShopDataCollector;
import cn.hdbstudio.qsfilter.quickshop.TransactionListener;
import cn.hdbstudio.qsfilter.filter.FilteredShop;
import cn.hdbstudio.qsfilter.filter.PriceFilterEngine;
import cn.hdbstudio.qsfilter.storage.DatabaseManager;
import cn.hdbstudio.qsfilter.storage.TransactionRepository;
import cn.hdbstudio.qsfilter.api.ApiHttpServer;
import cn.hdbstudio.qsfilter.crypto.EncryptionUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class QSFilterPlugin extends JavaPlugin implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "lf", "condition", "about", "reload", "stats", "clearcache"
    );

    private PluginConfig pluginConfig;
    private DatabaseManager databaseManager;
    private TransactionRepository transactionRepository;
    private EncryptionUtil encryptionUtil;
    private PriceFilterEngine priceFilterEngine;
    private QuickShopBridge quickShopBridge;
    private ShopDataCollector shopDataCollector;
    private ApiHttpServer apiHttpServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pluginConfig = PluginConfig.load(getDataFolder());
        getLogger().info("配置加载完成");

        encryptionUtil = new EncryptionUtil(pluginConfig);
        getLogger().info("加密模块初始化完成 (AES-GCM)");

        File dbFile = new File(getDataFolder(), "transactions.db");
        databaseManager = new DatabaseManager(dbFile);
        transactionRepository = new TransactionRepository(databaseManager);
        getLogger().info("SQLite 数据库初始化完成");

        priceFilterEngine = new PriceFilterEngine(pluginConfig, transactionRepository);
        getLogger().info("价格过滤引擎初始化完成");

        quickShopBridge = new QuickShopBridge(getLogger());
        if (!quickShopBridge.isAvailable()) {
            getLogger().warning("未检测到 QuickShop-Hikari，插件功能受限！");
        } else {
            getLogger().info("QuickShop-Hikari 桥接成功");
            new TransactionListener(this, transactionRepository);
        }

        shopDataCollector = new ShopDataCollector(this, quickShopBridge, priceFilterEngine, pluginConfig);
        shopDataCollector.start();

        apiHttpServer = new ApiHttpServer(pluginConfig, priceFilterEngine, encryptionUtil, quickShopBridge);
        apiHttpServer.start();
        getLogger().info("HTTP API 服务器启动于 " + pluginConfig.getHttpHost() + ":" + pluginConfig.getHttpPort());

        var cmd = Objects.requireNonNull(getCommand("qsfilter"));
        cmd.setExecutor(this);
        cmd.setTabCompleter(this);

        getLogger().info("QSFilterPlugin 启动完成！");
        getLogger().info("API 端点: http://" + pluginConfig.getHttpHost() + ":" + pluginConfig.getHttpPort() + "/api/");
    }

    @Override
    public void onDisable() {
        if (apiHttpServer != null) apiHttpServer.stop();
        if (shopDataCollector != null) shopDataCollector.stop();
        if (databaseManager != null) databaseManager.close();
        getLogger().info("QSFilterPlugin 已关闭");
    }

    // ======================== 命令处理 ========================

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "lf"         -> cmdLookup(sender, args);
            case "condition"  -> cmdCondition(sender);
            case "about"      -> cmdAbout(sender);
            case "reload"     -> cmdReload(sender);
            case "stats"      -> cmdStats(sender);
            case "clearcache" -> cmdClearCache(sender);
            default -> {
                sender.sendMessage("§c[QSFilter] 未知子命令: " + args[0]);
                sendHelp(sender);
            }
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6======== QSFilterPlugin ========");
        sender.sendMessage("§e/qsfilter lf <物品ID> §7— 查询物品加权历史均价");
        sender.sendMessage("§e/qsfilter condition §7— 查看插件 API 状态");
        sender.sendMessage("§e/qsfilter about §7— 输出插件信息");
        sender.sendMessage("§e/qsfilter reload §7— 热重载配置（无需重启）");
        sender.sendMessage("§e/qsfilter stats §7— 查看统计信息");
        sender.sendMessage("§e/qsfilter clearcache §7— 清除缓存");
    }

    // --- lf ---
    private void cmdLookup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c[QSFilter] 用法: /qsfilter lf <物品ID>");
            sender.sendMessage("§7  例: /qsfilter lf diamond");
            return;
        }
        String material = args[1].toUpperCase();

        // 从缓存中查找
        double avgPrice = priceFilterEngine.getWeightedPrice(material);
        if (avgPrice < 0) {
            // 尝试从全量商店数据模糊匹配
            var matching = priceFilterEngine.getFilteredShops().stream()
                    .filter(s -> s.getItemId().equalsIgnoreCase(args[1])
                            || s.getMaterial().equalsIgnoreCase(material))
                    .collect(Collectors.toList());

            if (matching.isEmpty()) {
                matching = priceFilterEngine.getFilteredShops().stream()
                        .filter(s -> s.getItemId().contains(args[1].toLowerCase())
                                || s.getMaterial().toLowerCase().contains(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (matching.isEmpty()) {
                sender.sendMessage("§c[QSFilter] 未找到物品 " + args[1] + " 的商店数据");
            } else {
                double manualAvg = matching.stream()
                        .mapToDouble(FilteredShop::getPrice)
                        .average().orElse(0);
                sender.sendMessage("§6[QSFilter] §e" + args[1] + " §7当前商店均价: §f" + String.format("%.2f", manualAvg));
                sender.sendMessage("§7  来自 " + matching.size() + " 个商店 (无历史交易加权数据)");
            }
            return;
        }

        // 从商店数据中取样例
        var shops = priceFilterEngine.getFilteredShops().stream()
                .filter(s -> s.getMaterial().equalsIgnoreCase(material))
                .collect(Collectors.toList());

        sender.sendMessage("§6[QSFilter] §e" + args[1]);
        sender.sendMessage("§7  加权历史均价: §f" + String.format("%.2f", avgPrice));
        sender.sendMessage("§7  当前商店数: §f" + shops.size());
        if (!shops.isEmpty()) {
            double minP = shops.stream().mapToDouble(FilteredShop::getPrice).min().orElse(0);
            double maxP = shops.stream().mapToDouble(FilteredShop::getPrice).max().orElse(0);
            sender.sendMessage("§7  当前价格区间: §f" + String.format("%.2f", minP) + " ~ " + String.format("%.2f", maxP));
        }
    }

    // --- condition ---
    private void cmdCondition(CommandSender sender) {
        sender.sendMessage("§6======== API 状态 ========");
        sender.sendMessage("§7  QuickShop 桥接: " + status(quickShopBridge.isAvailable()));
        sender.sendMessage("§7  HTTP 服务器: " + status(apiHttpServer.isRunning()));
        sender.sendMessage("§7  端口: §f" + pluginConfig.getHttpPort());
        sender.sendMessage("§7  数据刷新间隔: §f" + pluginConfig.getRefreshIntervalSeconds() + "s");
        sender.sendMessage("§7  价格过滤: " + status(pluginConfig.isFilterEnabled()));
        sender.sendMessage("§7  过滤区间: §f" + pluginConfig.getMinRatio() + " ~ " + pluginConfig.getMaxRatio());
        sender.sendMessage("§7  商店总数: §f" + priceFilterEngine.getTotalShopCount());
        sender.sendMessage("§7  过滤后: §f" + priceFilterEngine.getFilteredShopCount());
        sender.sendMessage("§7  交易记录: §f" + transactionRepository.countAll());
    }

    // --- about ---
    private void cmdAbout(CommandSender sender) {
        var desc = getDescription();
        sender.sendMessage("§6======== QSFilterPlugin ========");
        sender.sendMessage("§7  版本: §f" + desc.getVersion());
        sender.sendMessage("§7  作者: §f" + String.join(", ", desc.getAuthors()));
        sender.sendMessage("§7  描述: §f" + desc.getDescription());
        sender.sendMessage("§7  源码: §fhttps://github.com/PikaPikaGFY/QSFilterPlugin");
        sender.sendMessage("§7  依赖: §fQuickShop-Hikari, Paper 1.21+");
        sender.sendMessage("§7");
        sender.sendMessage("§7  §oAPI 端点: http://<IP>:" + pluginConfig.getHttpPort() + "/api/");
    }

    // --- reload ---
    private void cmdReload(CommandSender sender) {
        pluginConfig = PluginConfig.load(getDataFolder());
        encryptionUtil.reload(pluginConfig);
        priceFilterEngine.reload(pluginConfig);
        shopDataCollector.updateInterval(pluginConfig.getRefreshIntervalSeconds());
        sender.sendMessage("§a[QSFilter] 配置已重载");
    }

    // --- stats ---
    private void cmdStats(CommandSender sender) {
        int totalShops = priceFilterEngine.getTotalShopCount();
        int filteredShops = priceFilterEngine.getFilteredShopCount();
        int transactionCount = transactionRepository.countAll();
        sender.sendMessage("§6[QSFilter] 统计信息:");
        sender.sendMessage("§7  总商店数: §f" + totalShops);
        sender.sendMessage("§7  过滤后: §f" + filteredShops);
        sender.sendMessage("§7  交易记录数: §f" + transactionCount);
    }

    // --- clearcache ---
    private void cmdClearCache(CommandSender sender) {
        priceFilterEngine.clearCache();
        sender.sendMessage("§a[QSFilter] 缓存已清除");
    }

    // ======================== Tab 补全 ========================

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && "lf".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase();
            return priceFilterEngine.getFilteredShops().stream()
                    .map(FilteredShop::getItemId)
                    .distinct()
                    .filter(id -> id.startsWith(prefix))
                    .limit(20)
                    .collect(Collectors.toList());
        }

        return List.of();
    }

    // ======================== 工具 ========================

    private String status(boolean ok) {
        return ok ? "§a● 正常" : "§c● 异常";
    }
}
