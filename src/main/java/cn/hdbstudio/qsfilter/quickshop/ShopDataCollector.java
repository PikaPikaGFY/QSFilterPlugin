package cn.hdbstudio.qsfilter.quickshop;

import cn.hdbstudio.qsfilter.QSFilterPlugin;
import cn.hdbstudio.qsfilter.config.PluginConfig;
import cn.hdbstudio.qsfilter.filter.PriceFilterEngine;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

/**
 * 定时从 QuickShop 拉取所有商店数据，交由过滤引擎处理后存入缓存。
 */
public class ShopDataCollector {

    private final QSFilterPlugin plugin;
    private final QuickShopBridge quickShopBridge;
    private final PriceFilterEngine priceFilterEngine;
    private final Logger logger;
    private BukkitTask task;
    private volatile int intervalSeconds;

    public ShopDataCollector(QSFilterPlugin plugin, QuickShopBridge bridge,
                             PriceFilterEngine engine, PluginConfig config) {
        this.plugin = plugin;
        this.quickShopBridge = bridge;
        this.priceFilterEngine = engine;
        this.logger = plugin.getLogger();
        this.intervalSeconds = config.getRefreshIntervalSeconds();
    }

    public void start() {
        if (!quickShopBridge.isAvailable()) {
            logger.warning("QuickShop 不可用，跳过数据采集");
            return;
        }

        long ticks = intervalSeconds * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::collect, 100L, ticks);
        logger.info("数据采集器已启动 (间隔 " + intervalSeconds + " 秒)");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    public void updateInterval(int seconds) {
        this.intervalSeconds = seconds;
        if (task != null) {
            task.cancel();
            long ticks = seconds * 20L;
            task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::collect, 100L, ticks);
            logger.info("数据采集间隔已更新为 " + seconds + " 秒");
        }
    }

    private void collect() {
        try {
            long start = System.currentTimeMillis();
            var allShops = quickShopBridge.getAllShops();
            priceFilterEngine.processAndCache(allShops);
            long elapsed = System.currentTimeMillis() - start;
            logger.fine("数据采集完成: " + allShops.size() + " 个商店, 耗时 " + elapsed + "ms");
        } catch (Exception e) {
            logger.warning("数据采集异常: " + e.getMessage());
        }
    }
}
