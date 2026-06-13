package cn.hdbstudio.qsfilter.quickshop;

import cn.hdbstudio.qsfilter.QSFilterPlugin;
import cn.hdbstudio.qsfilter.storage.TransactionRepository;
import com.ghostchu.quickshop.api.shop.Shop;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.logging.Logger;

/**
 * 监听 QuickShop 交易事件，记录交易数据到数据库。
 * 事件类在 quickshop-bukkit 实现模块中，API 模块不暴露，因此用 registerEvent 动态注册。
 */
public class TransactionListener {

    private static final String[] EVENT_CLASS_NAMES = {
            "com.ghostchu.quickshop.api.event.ShopSuccessPurchaseEvent",
            "com.ghostchu.quickshop.api.event.PurchaseEvent",
            "com.ghostchu.quickshop.shop.event.ShopSuccessPurchaseEvent",
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
                plugin.getServer().getPluginManager().registerEvent(
                        eventClass, new Listener() {}, EventPriority.MONITOR,
                        (listener, event) -> handlePurchase(event),
                        plugin, true);
                logger.info("QuickShop 交易事件监听已注册: " + eventClass.getSimpleName());
                return;
            } catch (ClassNotFoundException ignored) {
            }
        }
        logger.warning("未找到 QuickShop 交易事件类（尝试了 " + EVENT_CLASS_NAMES.length + " 个候选）");
    }

    private void handlePurchase(Object event) {
        try {
            var eventClass = event.getClass();

            Shop shop;
            try {
                shop = (Shop) eventClass.getMethod("getShop").invoke(event);
            } catch (NoSuchMethodException e) {
                shop = (Shop) eventClass.getMethod("getPurchasedShop").invoke(event);
            }

            var itemStack = shop.getItem();
            String material = itemStack.getType().name();
            String itemName = itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()
                    ? itemStack.getItemMeta().getDisplayName()
                    : material;

            double price = shop.getPrice();

            int amount = 1;
            try {
                amount = (int) eventClass.getMethod("getAmount").invoke(event);
            } catch (NoSuchMethodException ignored) {
            }

            transactionRepository.insert(material, itemName, price, amount);
            logger.fine("记录交易: " + itemName + " x" + amount + " @ " + price);
        } catch (Exception e) {
            logger.fine("处理交易事件失败: " + e.getMessage());
        }
    }
}
