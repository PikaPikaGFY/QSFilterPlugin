package cn.hdbstudio.qsfilter.quickshop;

import cn.hdbstudio.qsfilter.QSFilterPlugin;
import cn.hdbstudio.qsfilter.storage.TransactionRepository;
import com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent;
import com.ghostchu.quickshop.api.shop.Shop;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.logging.Logger;

/**
 * 监听 QuickShop 交易事件，记录交易数据到数据库。
 * 只记录 SELLING 商店的成交事件（收購不參與加权均价计算）。
 */
public class TransactionListener implements Listener {

    private final TransactionRepository transactionRepository;
    private final Logger logger;
    private boolean registered;

    public TransactionListener(QSFilterPlugin plugin, TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
        this.logger = plugin.getLogger();
        this.registered = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        logger.info("QuickShop 交易事件监听器已注册 (ShopSuccessPurchaseEvent)");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopSuccessPurchase(ShopSuccessPurchaseEvent event) {
        try {
            Shop shop = event.getShop();
            // 只记录 SELLING（出售）商店的成交 — BUYING（收购）不参与均价计算
            if (!shop.isSelling()) {
                return;
            }

            var itemStack = shop.getItem();
            String material = itemStack.getType().name();
            String itemName = itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()
                    ? itemStack.getItemMeta().getDisplayName()
                    : material;

            int amount = event.getAmount();
            // 单件实际成交价 = 净收入 / 数量
            double price = amount > 0 ? event.getBalance() / amount : shop.getPrice();

            transactionRepository.insert(material, itemName, price, amount);
            logger.fine("记录交易: " + itemName + " x" + amount + " @ " + String.format("%.2f", price));
        } catch (Exception e) {
            logger.fine("处理交易事件失败: " + e.getMessage());
        }
    }
}
