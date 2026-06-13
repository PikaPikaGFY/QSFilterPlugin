package cn.hdbstudio.qsfilter.filter;

import cn.hdbstudio.qsfilter.config.PluginConfig;
import cn.hdbstudio.qsfilter.storage.TransactionRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 价格过滤引擎。
 * 核心算法：加权历史均价比较，过滤异常定价。
 *
 * 公式:
 *   加权历史均价 = Σ(单次成交价 × 采购量) / Σ(采购量)
 *   价格合理度比值 = 当前定价 / 加权历史均价
 *
 * 判定规则: 比值在 [minRatio, maxRatio] 区间内视为合理价格。
 */
public class PriceFilterEngine {

    private PluginConfig config;
    private final TransactionRepository transactionRepository;

    // 缓存：全量商店数据（未过滤）
    private final List<FilteredShop> allShopsCache = new CopyOnWriteArrayList<>();
    // 缓存：过滤后的商店数据
    private final List<FilteredShop> filteredShopsCache = new CopyOnWriteArrayList<>();
    // 缓存：各物品加权均价快照
    private final Map<String, Double> weightedPriceCache = new ConcurrentHashMap<>();

    private volatile long lastRefreshTime = 0;

    public PriceFilterEngine(PluginConfig config, TransactionRepository transactionRepository) {
        this.config = config;
        this.transactionRepository = transactionRepository;
    }

    public void reload(PluginConfig config) {
        this.config = config;
    }

    /**
     * 处理全量商店数据并更新过滤缓存。
     * 由 ShopDataCollector 定期调用。
     */
    public synchronized void processAndCache(List<FilteredShop> shops) {
        // 1. 批量查询所有物品的加权均价
        var priceMap = transactionRepository.getAllWeightedPrices();
        Map<String, Double> avgPriceMap = priceMap.stream()
                .collect(Collectors.toMap(
                        TransactionRepository.MaterialWeightedPrice::material,
                        TransactionRepository.MaterialWeightedPrice::avgPrice
                ));
        weightedPriceCache.putAll(avgPriceMap);

        // 2. 遍历每个商店，计算价格合理度
        List<FilteredShop> filtered = new ArrayList<>();
        for (FilteredShop shop : shops) {
            Double avgPrice = avgPriceMap.get(shop.getMaterial());

            if (avgPrice != null && avgPrice > 0) {
                double ratio = shop.getPrice() / avgPrice;
                shop.setWeightedAvgPrice(avgPrice);
                shop.setPriceRatio(ratio);

                if (config.isFilterEnabled()) {
                    boolean reasonable = ratio >= config.getMinRatio()
                            && ratio <= config.getMaxRatio();
                    shop.setPriceReasonable(reasonable);
                    if (reasonable) {
                        filtered.add(shop);
                    }
                } else {
                    // 过滤关闭时，全部视为合理
                    shop.setPriceReasonable(true);
                    filtered.add(shop);
                }
            } else {
                // 无历史数据：视为合理（新物品不应被误杀）
                shop.setWeightedAvgPrice(shop.getPrice());
                shop.setPriceRatio(1.0);
                shop.setPriceReasonable(true);
                filtered.add(shop);
            }
        }

        this.allShopsCache.clear();
        this.allShopsCache.addAll(shops);
        this.filteredShopsCache.clear();
        this.filteredShopsCache.addAll(filtered);
        this.lastRefreshTime = System.currentTimeMillis();
    }

    /**
     * 获取过滤后的商店列表。
     */
    public List<FilteredShop> getFilteredShops() {
        return new ArrayList<>(filteredShopsCache);
    }

    /**
     * 获取全量商店列表（含被过滤的）。
     */
    public List<FilteredShop> getAllShops() {
        return new ArrayList<>(allShopsCache);
    }

    /**
     * 按关键词搜索。
     */
    public List<FilteredShop> search(String keyword) {
        String lower = keyword.toLowerCase();
        return filteredShopsCache.stream()
                .filter(s -> s.getItemName().toLowerCase().contains(lower)
                        || s.getMaterial().toLowerCase().contains(lower)
                        || s.getOwnerName().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    public int getTotalShopCount() { return allShopsCache.size(); }
    public int getFilteredShopCount() { return filteredShopsCache.size(); }
    public long getLastRefreshTime() { return lastRefreshTime; }

    public void clearCache() {
        allShopsCache.clear();
        filteredShopsCache.clear();
        weightedPriceCache.clear();
    }
}
