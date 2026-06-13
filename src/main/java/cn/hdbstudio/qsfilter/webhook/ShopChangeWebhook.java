package cn.hdbstudio.qsfilter.webhook;

import cn.hdbstudio.qsfilter.config.PluginConfig;
import com.ghostchu.quickshop.api.event.Phase;
import com.ghostchu.quickshop.api.event.management.ShopCreateEvent;
import com.ghostchu.quickshop.api.event.management.ShopDeleteEvent;
import com.ghostchu.quickshop.api.event.settings.type.ShopPriceEvent;
import com.ghostchu.quickshop.api.event.settings.type.ShopTypeEnhancedEvent;
import com.ghostchu.quickshop.api.shop.IShopType;
import com.ghostchu.quickshop.api.shop.Shop;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 监听 QuickShop 商店变更事件，通过 WebHook POST 到前端 Web 服务器。
 *
 * 监听事件:
 *   ShopCreateEvent  (POST phase) → POST /webhook/shop-create
 *   ShopDeleteEvent  (POST phase) → POST /webhook/shop-delete
 *   ShopPriceEvent   (POST phase) → POST /webhook/shop-price
 *   ShopTypeEvent    (POST phase) → POST /webhook/shop-type
 */
public class ShopChangeWebhook implements Listener {

    private final PluginConfig config;
    private final Logger logger;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ShopChangeWebhook(JavaPlugin plugin, PluginConfig config) {
        this.config = config;
        this.logger = plugin.getLogger();
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        String url = config.getWebhookUrl();
        if (url != null && !url.isEmpty()) {
            logger.info("WebHook 已启用 → " + url);
        }
    }

    // ======================== 事件处理 ========================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopCreate(ShopCreateEvent event) {
        if (event.phase() != Phase.POST) return;
        Shop shop = event.shop().orElse(null);
        if (shop == null) return;
        postAsync("shop-create", shopToJson(shop));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopDelete(ShopDeleteEvent event) {
        if (event.phase() != Phase.POST) return;
        Shop shop = event.shop().orElse(null);
        if (shop == null) return;
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("shop_id", shop.getShopId());
        postAsync("shop-delete", payload);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopPriceChange(ShopPriceEvent event) {
        if (event.phase() != Phase.POST) return;
        Shop shop = event.shop();
        if (shop == null) return;
        Double oldPrice = event.old();
        Double newPrice = event.updated();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("shop_id", shop.getShopId());
        payload.put("old_price", oldPrice != null ? oldPrice : 0);
        payload.put("new_price", newPrice != null ? newPrice : shop.getPrice());
        payload.set("shop", shopToJson(shop));
        postAsync("shop-price", payload);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopTypeChange(ShopTypeEnhancedEvent event) {
        if (event.phase() != Phase.POST) return;
        Shop shop = event.shop();
        if (shop == null) return;

        IShopType oldType = event.old();
        IShopType newType = event.updated();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("shop_id", shop.getShopId());
        payload.put("old_type", oldType != null ? oldType.identifier() : "");
        payload.put("new_type", newType != null ? newType.identifier() : "");
        payload.set("shop", shopToJson(shop));
        postAsync("shop-type", payload);
    }

    // ======================== JSON 序列化 ========================

    private ObjectNode shopToJson(Shop shop) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("shop_id", shop.getShopId());

        UUID ownerUuid = shop.getOwner().getUniqueId();
        node.put("owner_uuid", ownerUuid.toString());
        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUuid);
        node.put("owner_name", owner.getName() != null ? owner.getName() : ownerUuid.toString());

        var loc = shop.getLocation();
        node.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "unknown");
        node.put("x", loc.getBlockX());
        node.put("y", loc.getBlockY());
        node.put("z", loc.getBlockZ());

        var item = shop.getItem();
        String material = item.getType().name();
        node.put("material", material);
        node.put("item_name", item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName() : material);
        // item_id: 小写，去 minecraft: 前缀
        String itemId = material.toLowerCase();
        if (itemId.startsWith("minecraft:")) itemId = itemId.substring(10);
        node.put("item_id", itemId);

        node.put("price", shop.getPrice());
        node.put("stacking_amount", shop.getShopStackingAmount());
        node.put("shop_type", shop.isSelling() ? "SELLING" : "BUYING");

        return node;
    }

    // ======================== HTTP POST ========================

    private void postAsync(String eventType, ObjectNode payload) {
        String webhookUrl = config.getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        // 去掉末尾斜杠
        String base = webhookUrl.endsWith("/") ? webhookUrl.substring(0, webhookUrl.length() - 1) : webhookUrl;
        String targetUrl = base + "/webhook/" + eventType;

        Bukkit.getScheduler().runTaskAsynchronously(
                Bukkit.getPluginManager().getPlugin("QSFilterPlugin"),
                () -> {
                    try {
                        String json = objectMapper.writeValueAsString(payload);
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(targetUrl))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .timeout(Duration.ofSeconds(5))
                                .build();
                        HttpResponse<String> response = httpClient.send(request,
                                HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() >= 400) {
                            logger.fine("WebHook " + eventType + " 返回 " + response.statusCode());
                        } else {
                            logger.fine("WebHook " + eventType + " 已发送");
                        }
                    } catch (Exception e) {
                        logger.fine("WebHook " + eventType + " 发送失败: " + e.getMessage());
                    }
                });
    }
}
