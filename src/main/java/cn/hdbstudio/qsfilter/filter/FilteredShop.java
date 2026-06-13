package cn.hdbstudio.qsfilter.filter;

/**
 * 经过过滤引擎处理后的商店数据。
 * 字段对应 QuickShop API: getShopId / getOwner / getLocation / getItem / getPrice / getShopType / getShopStackingAmount
 */
public class FilteredShop {

    private final long shopId;
    private final String ownerUuid;     // getOwner() → UUID
    private final String ownerName;      // UUID 对应的玩家名
    private final String world;
    private final int x, y, z;           // getLocation()
    private final String material;       // getItem() → ItemStack.getType()
    private final String itemName;       // getItem() → ItemStack 的 displayName
    private final double price;           // getPrice() → 单价
    private final String shopType;       // getShopType() → SELLING / BUYING
    private final int stackingAmount;    // getShopStackingAmount() → 每次交易数量

    // 过滤相关字段
    private double weightedAvgPrice;     // 加权历史均价
    private double priceRatio;           // 当前价格 / 加权均价
    private boolean priceReasonable;     // 价格是否合理

    public FilteredShop(long shopId, String ownerUuid, String ownerName,
                        String world, int x, int y, int z,
                        String material, String itemName, double price,
                        String shopType, int stackingAmount) {
        this.shopId = shopId;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.material = material;
        this.itemName = itemName;
        this.price = price;
        this.shopType = shopType;
        this.stackingAmount = stackingAmount;
    }

    // ---- Getters / Setters ----

    public long getShopId() { return shopId; }
    public String getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getMaterial() { return material; }
    public String getItemName() { return itemName; }
    public double getPrice() { return price; }
    public String getShopType() { return shopType; }
    public int getStackingAmount() { return stackingAmount; }

    /**
     * 物品 ID — 只保留 "minecraft:" 后的字符，全小写。
     * 例: DIAMOND → diamond, ACACIA_BOAT → acacia_boat
     */
    public String getItemId() {
        String id = material.toLowerCase();
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        return id;
    }

    // 过滤相关
    public double getWeightedAvgPrice() { return weightedAvgPrice; }
    public void setWeightedAvgPrice(double v) { this.weightedAvgPrice = v; }
    public double getPriceRatio() { return priceRatio; }
    public void setPriceRatio(double v) { this.priceRatio = v; }
    public boolean isPriceReasonable() { return priceReasonable; }
    public void setPriceReasonable(boolean v) { this.priceReasonable = v; }
}
