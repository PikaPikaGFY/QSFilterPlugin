package cn.hdbstudio.qsfilter.quickshop;

import cn.hdbstudio.qsfilter.filter.FilteredShop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * QuickShop-Hikari API 桥接层。
 * 全部通过反射访问，无需编译期依赖 QuickShop API。
 *
 * 对应 QS Shop 对象的方法:
 *   getShopId()             → long
 *   getOwner()              → UUID
 *   getLocation()           → Location
 *   getItem()               → ItemStack
 *   getPrice()              → double
 *   getShopType()           → ShopType (SELLING / BUYING)
 *   getShopStackingAmount() → int
 */
public class QuickShopBridge {

    private final Logger logger;
    private boolean available = false;

    // 缓存的反射 Method 对象，避免每次调用 getDeclaredMethod
    private Object cachedShopManager;
    private java.lang.reflect.Method getAllShopsMethod;

    public QuickShopBridge(Logger logger) {
        this.logger = logger;
        try {
            var qsPlugin = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
            if (qsPlugin != null && qsPlugin.isEnabled()) {
                var apiClass = Class.forName("com.ghostchu.quickshop.api.QuickShopAPI");
                var getInstanceMethod = apiClass.getDeclaredMethod("getInstance");
                var quickShopApi = getInstanceMethod.invoke(null);

                // 缓存 ShopManager 和 getAllShops 方法
                var shopManagerMethod = quickShopApi.getClass().getDeclaredMethod("getShopManager");
                this.cachedShopManager = shopManagerMethod.invoke(quickShopApi);
                this.getAllShopsMethod = cachedShopManager.getClass().getDeclaredMethod("getAllShops");
                this.getAllShopsMethod.setAccessible(true);

                this.available = true;
                logger.info("QuickShop-Hikari API 桥接成功");
            } else {
                logger.warning("QuickShop-Hikari 插件未找到或未启用");
            }
        } catch (Exception e) {
            logger.warning("无法桥接 QuickShop-Hikari API: " + e.getMessage());
            this.available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    @SuppressWarnings("unchecked")
    public List<FilteredShop> getAllShops() {
        List<FilteredShop> result = new ArrayList<>();
        if (!available) return result;

        try {
            var allShops = (List<?>) getAllShopsMethod.invoke(cachedShopManager);
            for (Object shop : allShops) {
                FilteredShop fs = convertShop(shop);
                if (fs != null) result.add(fs);
            }
        } catch (Exception e) {
            logger.warning("获取商店列表失败: " + e.getMessage());
        }
        return result;
    }

    private FilteredShop convertShop(Object shop) {
        try {
            var shopClass = shop.getClass();

            long shopId = (long) invokeGetter(shopClass, shop, "getShopId");
            UUID ownerUuid = (UUID) invokeGetter(shopClass, shop, "getOwner");
            Location loc = (Location) invokeGetter(shopClass, shop, "getLocation");
            ItemStack item = (ItemStack) invokeGetter(shopClass, shop, "getItem");
            double price = (double) invokeGetter(shopClass, shop, "getPrice");
            Object shopTypeObj = invokeGetter(shopClass, shop, "getShopType");
            int stackingAmount = (int) invokeGetter(shopClass, shop, "getShopStackingAmount");

            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
            String ownerName = owner.getName() != null ? owner.getName() : ownerUuid.toString();

            String material = item.getType().name();
            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : material;
            String shopType = shopTypeObj.toString();

            return new FilteredShop(
                    shopId, ownerUuid.toString(), ownerName,
                    loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    material, itemName, price,
                    shopType, stackingAmount
            );
        } catch (Exception e) {
            logger.fine("转换商店数据失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 通过 getDeclaredMethod 调用 getter（避免 Paper 反射重写器扫描父类加载无关依赖）。
     */
    private Object invokeGetter(Class<?> clazz, Object target, String methodName) throws Exception {
        var method = clazz.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }
}
