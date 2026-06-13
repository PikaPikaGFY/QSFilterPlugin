package cn.hdbstudio.qsfilter.quickshop;

import cn.hdbstudio.qsfilter.filter.FilteredShop;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * QuickShop-Hikari API 桥接层。
 * 使用 compileOnly quickshop-api，直接调用官方 API。
 */
public class QuickShopBridge {

    private final Logger logger;
    private QuickShopAPI qsApi;
    private boolean available = false;

    public QuickShopBridge(Logger logger) {
        this.logger = logger;
        try {
            var qsPlugin = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
            if (qsPlugin == null || !qsPlugin.isEnabled()) {
                logger.warning("QuickShop-Hikari 插件未找到或未启用");
                return;
            }

            this.qsApi = QuickShopAPI.getInstance();
            if (qsApi == null || qsApi.getShopManager() == null) {
                logger.warning("QuickShopAPI 或 ShopManager 不可用");
                return;
            }

            this.available = true;
            logger.info("QuickShop-Hikari API 桥接成功");
        } catch (Throwable e) {
            logger.warning("无法桥接 QuickShop-Hikari API: " + e.getMessage());
            this.available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public List<FilteredShop> getAllShops() {
        List<FilteredShop> result = new ArrayList<>();
        if (!available) return result;

        try {
            for (Shop shop : qsApi.getShopManager().getAllShops()) {
                FilteredShop fs = convertShop(shop);
                if (fs != null) result.add(fs);
            }
        } catch (Exception e) {
            logger.warning("获取商店列表失败: " + e.getMessage());
        }
        return result;
    }

    private FilteredShop convertShop(Shop shop) {
        try {
            long shopId = shop.getShopId();

            // getOwner() 返回 QUser，取 UniqueId
            UUID ownerUuid = shop.getOwner().getUniqueId();
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
            String ownerName = owner.getName() != null ? owner.getName() : ownerUuid.toString();

            var loc = shop.getLocation();
            String world = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";

            ItemStack item = shop.getItem();
            String material = item.getType().name();
            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : material;

            double price = shop.getPrice();
            int stackingAmount = shop.getShopStackingAmount();

            // 使用 isSelling() 判断类型（ShopType 枚举已废弃）
            String shopType = shop.isSelling() ? "SELLING" : "BUYING";

            return new FilteredShop(
                    shopId, ownerUuid.toString(), ownerName,
                    world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    material, itemName, price,
                    shopType, stackingAmount
            );
        } catch (Exception e) {
            logger.fine("转换商店数据失败: " + e.getMessage());
            return null;
        }
    }
}
