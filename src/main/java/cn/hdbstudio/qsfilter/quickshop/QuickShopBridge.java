package cn.hdbstudio.qsfilter.quickshop;

import cn.hdbstudio.qsfilter.filter.FilteredShop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * QuickShop-Hikari API 桥接层。
 * 使用 MethodHandle + privateLookupIn 彻底绕过 Paper 反射重写器。
 */
public class QuickShopBridge {

    private final Logger logger;
    private boolean available = false;

    private Object cachedShopManager;
    private MethodHandle getAllShopsHandle;

    public QuickShopBridge(Logger logger) {
        this.logger = logger;
        try {
            var qsPlugin = Bukkit.getPluginManager().getPlugin("QuickShop-Hikari");
            if (qsPlugin == null || !qsPlugin.isEnabled()) {
                logger.warning("QuickShop-Hikari 插件未找到或未启用");
                return;
            }

            // 用 QS 插件的 ClassLoader 加载类（绕过 Paper remapper）
            ClassLoader qsLoader = qsPlugin.getClass().getClassLoader();
            var apiClass = qsLoader.loadClass("com.ghostchu.quickshop.api.QuickShopAPI");
            var lookup = MethodHandles.lookup();

            // privateLookupIn — 获取对 QS 模块的完全访问权限
            var privLookup = MethodHandles.privateLookupIn(apiClass, lookup);

            // QuickShopAPI.getInstance()
            var getInstanceHandle = privLookup.findStatic(apiClass, "getInstance",
                    MethodType.methodType(apiClass));
            var quickShopApi = getInstanceHandle.invoke();

            // quickShopApi.getShopManager() → 返回类型用 apiClass 的方法签名真实类型
            var getShopManagerHandle = privLookup.findVirtual(apiClass, "getShopManager",
                    MethodType.methodType(
                            qsLoader.loadClass("com.ghostchu.quickshop.api.ShopManager")));
            this.cachedShopManager = getShopManagerHandle.invoke(quickShopApi);

            // shopManager.getAllShops()
            var smLookup = MethodHandles.privateLookupIn(cachedShopManager.getClass(), lookup);
            this.getAllShopsHandle = smLookup.findVirtual(cachedShopManager.getClass(), "getAllShops",
                    MethodType.methodType(List.class));

            this.available = true;
            logger.info("QuickShop-Hikari API 桥接成功");
        } catch (Throwable e) {
            logger.log(Level.WARNING, "无法桥接 QuickShop-Hikari API", e);
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
            var allShops = (List<?>) getAllShopsHandle.invoke(cachedShopManager);
            for (Object shop : allShops) {
                FilteredShop fs = convertShop(shop);
                if (fs != null) result.add(fs);
            }
        } catch (Throwable e) {
            logger.warning("获取商店列表失败: " + e.getMessage());
        }
        return result;
    }

    private FilteredShop convertShop(Object shop) {
        try {
            var shopClass = shop.getClass();
            var lookup = MethodHandles.privateLookupIn(shopClass, MethodHandles.lookup());

            long shopId = (long) findGetter(lookup, shopClass, "getShopId", long.class).invoke(shop);
            UUID ownerUuid = (UUID) findGetter(lookup, shopClass, "getOwner", UUID.class).invoke(shop);
            Location loc = (Location) findGetter(lookup, shopClass, "getLocation", Location.class).invoke(shop);
            ItemStack item = (ItemStack) findGetter(lookup, shopClass, "getItem", ItemStack.class).invoke(shop);
            double price = (double) findGetter(lookup, shopClass, "getPrice", double.class).invoke(shop);
            Object shopTypeObj = findGetter(lookup, shopClass, "getShopType", Object.class).invoke(shop);
            int stackingAmount = (int) findGetter(lookup, shopClass, "getShopStackingAmount", int.class).invoke(shop);

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
        } catch (Throwable e) {
            logger.fine("转换商店数据失败: " + e.getMessage());
            return null;
        }
    }

    private static MethodHandle findGetter(MethodHandles.Lookup lookup, Class<?> clazz,
                                           String methodName, Class<?> returnType)
            throws NoSuchMethodException, IllegalAccessException {
        return lookup.findVirtual(clazz, methodName, MethodType.methodType(returnType));
    }
}
