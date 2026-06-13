package cn.hdbstudio.qsfilter.quickshop;

import cn.hdbstudio.qsfilter.QSFilterPlugin;
import cn.hdbstudio.qsfilter.storage.TransactionRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.util.logging.Logger;

/**
 * 监听 QuickShop 交易事件，记录每条成交数据到数据库。
 * 通过反射注册，兼容多个 QS 版本的事件类名。
 */
public class TransactionListener {

    private static final String[] EVENT_CLASS_NAMES = {
            "com.ghostchu.quickshop.api.event.ShopSuccessPurchaseEvent",
            "com.ghostchu.quickshop.api.event.ShopPurchaseEvent",
            "com.ghostchu.quickshop.api.event.PurchaseEvent",
            "com.ghostchu.quickshop.api.economy.ShopPurchaseEvent",
            "com.ghostchu.quickshop.api.shop.ShopSuccessPurchaseEvent",
    };

    private final TransactionRepository transactionRepository;
    private final Logger logger;

    public TransactionListener(QSFilterPlugin plugin, TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
        this.logger = plugin.getLogger();

        registerQuickShopEvents(plugin);
    }

    private void registerQuickShopEvents(QSFilterPlugin plugin) {
        for (String className : EVENT_CLASS_NAMES) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends org.bukkit.event.Event> eventClass =
                        (Class<? extends org.bukkit.event.Event>) Class.forName(className);
                // 动态注册事件监听
                var pm = plugin.getServer().getPluginManager();
                pm.registerEvent(eventClass, new Listener() {}, EventPriority.MONITOR,
                        (listener, event) -> handlePurchase(event),
                        plugin, true);
                logger.info("QuickShop 交易事件监听已注册: " + eventClass.getSimpleName());
                return; // 成功注册第一个就退出
            } catch (ClassNotFoundException ignored) {
            }
        }
        logger.warning("无法找到 QuickShop 交易事件类（尝试了 " + EVENT_CLASS_NAMES.length + " 个候选），交易记录功能不可用");
        logger.warning("QuickShop-Hikari 版本: " + getQuickShopVersion());
    }

    private void handlePurchase(Object event) {
        try {
            var eventClass = event.getClass();
            Object shop;

            // 获取商店对象 (getShop())
            try {
                shop = eventClass.getDeclaredMethod("getShop").invoke(event);
            } catch (NoSuchMethodException e) {
                // 某些版本可能是 getPurchasedShop()
                shop = eventClass.getDeclaredMethod("getPurchasedShop").invoke(event);
            }

            var shopClass = shop.getClass();

            // 物品信息
            var itemStack = (org.bukkit.inventory.ItemStack)
                    shopClass.getDeclaredMethod("getItem").invoke(shop);
            String material = itemStack.getType().name();
            String itemName = itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()
                    ? itemStack.getItemMeta().getDisplayName()
                    : material;

            // 价格
            double price = (double) shopClass.getDeclaredMethod("getPrice").invoke(shop);

            // 购买数量
            int amount = 1;
            try {
                amount = (int) eventClass.getDeclaredMethod("getAmount").invoke(event);
            } catch (NoSuchMethodException ignored) {
            }

            transactionRepository.insert(material, itemName, price, amount);
            logger.fine("记录交易: " + itemName + " x" + amount + " @ " + price);

        } catch (Exception e) {
            logger.fine("处理交易事件失败: " + e.getMessage());
        }
    }

    private String getQuickShopVersion() {
        try {
            var plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
            if (plugin != null) {
                return plugin.getDescription().getVersion();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }
}
