package cn.hdbstudio.qsfilter.api;

import cn.hdbstudio.qsfilter.config.PluginConfig;
import cn.hdbstudio.qsfilter.crypto.EncryptionUtil;
import cn.hdbstudio.qsfilter.filter.FilteredShop;
import cn.hdbstudio.qsfilter.filter.PriceFilterEngine;
import cn.hdbstudio.qsfilter.quickshop.QuickShopBridge;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.Context;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 嵌入式 HTTP API 服务器。
 *
 * API 信封格式:
 *   加密模式: { "version": 1, "encrypted": true, "iv": "...", "data": "..." }
 *   明文模式: { "version": 1, "encrypted": false, "data": { ... } }
 */
public class ApiHttpServer {

    private final PluginConfig config;
    private final PriceFilterEngine filterEngine;
    private final EncryptionUtil encryptionUtil;
    private final QuickShopBridge quickShopBridge;
    private final ObjectMapper objectMapper;
    private final Logger logger;
    private Javalin app;

    public ApiHttpServer(PluginConfig config, PriceFilterEngine filterEngine,
                         EncryptionUtil encryptionUtil, QuickShopBridge quickShopBridge) {
        this.config = config;
        this.filterEngine = filterEngine;
        this.encryptionUtil = encryptionUtil;
        this.quickShopBridge = quickShopBridge;
        this.objectMapper = new ObjectMapper();
        this.logger = Logger.getLogger("QSFilter-API");
    }

    public void start() {
        app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
        });

        // CORS
        if (config.getCorsOrigins() != null) {
            for (String origin : config.getCorsOrigins()) {
                app.before(ctx -> {
                    ctx.header("Access-Control-Allow-Origin", origin);
                    ctx.header("Access-Control-Allow-Methods", "GET, OPTIONS");
                    ctx.header("Access-Control-Allow-Headers", "Content-Type");
                });
            }
        }

        // 注册路由
        registerRoutes();

        // 启动
        app.start(config.getHttpHost(), config.getHttpPort());
    }

    public void stop() {
        if (app != null) {
            app.stop();
        }
    }

    public boolean isRunning() {
        return app != null;
    }

    private void registerRoutes() {
        // 健康检查（永远不加密）
        app.get("/api/health", this::handleHealth);

        // 获取过滤后的商店列表
        app.get("/api/shops", this::handleGetShops);

        // 搜索商店
        app.get("/api/shops/search", this::handleSearchShops);

        // 获取统计信息
        app.get("/api/stats", this::handleGetStats);

        // 获取特定物品的价格历史
        app.get("/api/price-history", this::handlePriceHistory);

        // OPTIONS (CORS preflight)
        app.options("/api/*", ctx -> ctx.status(204));
    }

    // ======================== 处理器 ========================

    private void handleHealth(Context ctx) {
        ctx.contentType(ContentType.APPLICATION_JSON);
        ctx.result("{\"status\":\"ok\",\"qs_available\":" + quickShopBridge.isAvailable() + "}");
    }

    private void handleGetShops(Context ctx) {
        String sort = ctx.queryParam("sort");     // price, amount, ratio
        String order = ctx.queryParam("order");   // asc, desc
        String type = ctx.queryParam("type");     // SELLING, BUYING
        String world = ctx.queryParam("world");   // 世界名称
        boolean showAll = "true".equalsIgnoreCase(ctx.queryParam("show_all"));

        List<FilteredShop> shops = showAll ? filterEngine.getAllShops() : filterEngine.getFilteredShops();

        // 过滤
        if (type != null) {
            shops = shops.stream()
                    .filter(s -> s.getShopType().equalsIgnoreCase(type))
                    .collect(Collectors.toList());
        }
        if (world != null) {
            shops = shops.stream()
                    .filter(s -> s.getWorld().equalsIgnoreCase(world))
                    .collect(Collectors.toList());
        }

        // 排序
        if (sort != null) {
            Comparator<FilteredShop> comparator = switch (sort.toLowerCase()) {
                case "price" -> Comparator.comparingDouble(FilteredShop::getPrice);
                case "amount" -> Comparator.comparingInt(FilteredShop::getStackingAmount);
                case "ratio" -> Comparator.comparingDouble(FilteredShop::getPriceRatio);
                default -> Comparator.comparingDouble(FilteredShop::getPrice);
            };
            if ("desc".equalsIgnoreCase(order)) {
                comparator = comparator.reversed();
            }
            shops = shops.stream().sorted(comparator).collect(Collectors.toList());
        }

        // 分页
        int page = parseIntOrDefault(ctx.queryParam("page"), 1);
        int limit = Math.min(parseIntOrDefault(ctx.queryParam("limit"), 50), 200);
        int fromIndex = (page - 1) * limit;
        int toIndex = Math.min(fromIndex + limit, shops.size());

        List<FilteredShop> paged = (fromIndex < shops.size())
                ? shops.subList(fromIndex, toIndex)
                : List.of();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("total", shops.size());
        result.put("show_all", showAll);
        result.put("page", page);
        result.put("limit", limit);
        result.putArray("shops").addAll(shopsToJsonArray(paged));

        sendApiResponse(ctx, result);
    }

    private void handleSearchShops(Context ctx) {
        String keyword = ctx.queryParam("keyword");
        if (keyword == null || keyword.isEmpty()) {
            ctx.status(400).result("{\"error\":\"缺少 keyword 参数\"}");
            return;
        }

        List<FilteredShop> shops = filterEngine.search(keyword);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("keyword", keyword);
        result.put("total", shops.size());
        result.putArray("shops").addAll(shopsToJsonArray(shops));

        sendApiResponse(ctx, result);
    }

    private void handleGetStats(Context ctx) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("total_shops", filterEngine.getTotalShopCount());
        result.put("filtered_shops", filterEngine.getFilteredShopCount());
        result.put("filter_enabled", config.isFilterEnabled());
        result.put("min_ratio", config.getMinRatio());
        result.put("max_ratio", config.getMaxRatio());
        result.put("last_refresh", filterEngine.getLastRefreshTime());
        result.put("qs_available", quickShopBridge.isAvailable());

        sendApiResponse(ctx, result);
    }

    private void handlePriceHistory(Context ctx) {
        String material = ctx.queryParam("material");
        if (material == null || material.isEmpty()) {
            ctx.status(400).result("{\"error\":\"缺少 material 参数\"}");
            return;
        }

        // This would require a new repository method to get price history list
        // For now, return a placeholder
        ObjectNode result = objectMapper.createObjectNode();
        result.put("material", material);
        result.put("message", "价格历史查询功能待实现");

        sendApiResponse(ctx, result);
    }

    // ======================== 工具方法 ========================

    private ArrayNode shopsToJsonArray(List<FilteredShop> shops) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (FilteredShop s : shops) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("shop_id", s.getShopId());
            node.put("owner_uuid", s.getOwnerUuid());
            node.put("owner_name", s.getOwnerName());
            node.put("world", s.getWorld());
            node.put("x", s.getX());
            node.put("y", s.getY());
            node.put("z", s.getZ());
            node.put("material", s.getMaterial());
            node.put("item_name", s.getItemName());
            node.put("price", s.getPrice());
            node.put("item_id", s.getItemId());
            node.put("stacking_amount", s.getStackingAmount());
            node.put("shop_type", s.getShopType());
            node.put("weighted_avg_price", s.getWeightedAvgPrice());
            node.put("price_ratio", Math.round(s.getPriceRatio() * 100.0) / 100.0);
            node.put("price_reasonable", s.isPriceReasonable());
            arr.add(node);
        }
        return arr;
    }

    /**
     * 发送 API 响应（明文 JSON，不加密）。
     */
    private void sendApiResponse(Context ctx, ObjectNode payload) {
        ctx.contentType(ContentType.APPLICATION_JSON);
        try {
            ctx.result(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            ctx.status(500).result("{\"error\":\"序列化失败\"}");
        }
    }

    private int parseIntOrDefault(String val, int defaultVal) {
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
