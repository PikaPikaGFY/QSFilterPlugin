package cn.hdbstudio.qsfilter;

import cn.hdbstudio.qsfilter.config.PluginConfig;
import cn.hdbstudio.qsfilter.quickshop.QuickShopBridge;
import cn.hdbstudio.qsfilter.quickshop.ShopDataCollector;
import cn.hdbstudio.qsfilter.quickshop.TransactionListener;
import cn.hdbstudio.qsfilter.filter.PriceFilterEngine;
import cn.hdbstudio.qsfilter.storage.DatabaseManager;
import cn.hdbstudio.qsfilter.storage.TransactionRepository;
import cn.hdbstudio.qsfilter.api.ApiHttpServer;
import cn.hdbstudio.qsfilter.crypto.EncryptionUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Objects;

public final class QSFilterPlugin extends JavaPlugin {

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
        // 1. 加载配置
        saveDefaultConfig();
        pluginConfig = PluginConfig.load(getDataFolder());
        getLogger().info("配置加载完成");

        // 2. 初始化加密工具
        encryptionUtil = new EncryptionUtil(pluginConfig);
        getLogger().info("加密模块初始化完成 (AES-GCM)");

        // 3. 初始化数据库
        File dbFile = new File(getDataFolder(), "transactions.db");
        databaseManager = new DatabaseManager(dbFile);
        transactionRepository = new TransactionRepository(databaseManager);
        getLogger().info("SQLite 数据库初始化完成");

        // 4. 初始化价格过滤器
        priceFilterEngine = new PriceFilterEngine(pluginConfig, transactionRepository);
        getLogger().info("价格过滤引擎初始化完成");

        // 5. 桥接 QuickShop
        quickShopBridge = new QuickShopBridge(getLogger());
        if (!quickShopBridge.isAvailable()) {
            getLogger().warning("未检测到 QuickShop-Hikari，插件功能受限！");
        } else {
            getLogger().info("QuickShop-Hikari 桥接成功");
            // 注册交易监听
            new TransactionListener(this, transactionRepository);
        }

        // 6. 初始化数据采集器
        shopDataCollector = new ShopDataCollector(this, quickShopBridge, priceFilterEngine, pluginConfig);
        shopDataCollector.start();

        // 7. 启动 HTTP API 服务器
        apiHttpServer = new ApiHttpServer(pluginConfig, priceFilterEngine, encryptionUtil, quickShopBridge);
        apiHttpServer.start();
        getLogger().info("HTTP API 服务器启动于 " + pluginConfig.getHttpHost() + ":" + pluginConfig.getHttpPort());

        // 8. 注册命令
        Objects.requireNonNull(getCommand("qsfilter")).setExecutor(this);

        getLogger().info("QSFilterPlugin 启动完成！");
        getLogger().info("API 端点: http://" + pluginConfig.getHttpHost() + ":" + pluginConfig.getHttpPort() + "/api/");
    }

    @Override
    public void onDisable() {
        if (apiHttpServer != null) {
            apiHttpServer.stop();
        }
        if (shopDataCollector != null) {
            shopDataCollector.stop();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("QSFilterPlugin 已关闭");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6[QSFilter] §e/qsfilter reload §7— 重载配置");
            sender.sendMessage("§6[QSFilter] §e/qsfilter stats §7— 查看统计");
            sender.sendMessage("§6[QSFilter] §e/qsfilter clearcache §7— 清除缓存");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                pluginConfig = PluginConfig.load(getDataFolder());
                encryptionUtil.reload(pluginConfig);
                priceFilterEngine.reload(pluginConfig);
                shopDataCollector.updateInterval(pluginConfig.getRefreshIntervalSeconds());
                sender.sendMessage("§a[QSFilter] 配置已重载");
            }
            case "stats" -> {
                int totalShops = priceFilterEngine.getTotalShopCount();
                int filteredShops = priceFilterEngine.getFilteredShopCount();
                int transactionCount = transactionRepository.countAll();
                sender.sendMessage("§6[QSFilter] 统计信息:");
                sender.sendMessage("§7  总商店数: §f" + totalShops);
                sender.sendMessage("§7  过滤后: §f" + filteredShops);
                sender.sendMessage("§7  交易记录数: §f" + transactionCount);
            }
            case "clearcache" -> {
                priceFilterEngine.clearCache();
                sender.sendMessage("§a[QSFilter] 缓存已清除");
            }
            default -> sender.sendMessage("§c[QSFilter] 未知命令。请使用 /qsfilter reload|stats|clearcache");
        }
        return true;
    }
}
