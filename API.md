# QSFilterPlugin API 文档

## 1. 连接信息

| 项目 | 值 |
|------|-----|
| 协议 | HTTP |
| 地址 | `http://<服务器IP>:8765` |
| 格式 | JSON（明文，不加密） |
| 编码 | UTF-8 |

---

## 2. 端点列表

### 2.1 健康检查

```
GET /api/health
```

**响应:**
```json
{
  "status": "ok",
  "qs_available": true
}
```
- `qs_available`: `true` 表示已连接到 QuickShop，数据正常

---

### 2.2 商店列表

```
GET /api/shops
```

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `sort` | string | 否 | 排序字段：`price`（价格）、`amount`（数量）、`ratio`（价格比） |
| `order` | string | 否 | 排序方向：`asc`（升序）、`desc`（降序） |
| `type` | string | 否 | 商店类型：`SELLING`（出售）、`BUYING`（收購） |
| `world` | string | 否 | 按世界名称过滤 |
| `show_all` | string | 否 | `"true"` 显示全部（含被价格过滤的），默认只显示过滤后 |
| `page` | int | 否 | 页码，默认 1 |
| `limit` | int | 否 | 每页数量，默认 50，最大 200 |

**示例:**
```
GET /api/shops?sort=price&order=asc&type=SELLING&page=1&limit=20
GET /api/shops?show_all=true
```

**响应:**
```json
{
  "total": 42,
  "show_all": false,
  "page": 1,
  "limit": 20,
  "shops": [
    {
      "shop_id": 1,
      "owner_uuid": "7d0e1c13-26bd-472f-a54c-bea19c0b953e",
      "owner_name": "lavaFreeze",
      "world": "world",
      "x": 100,
      "y": 64,
      "z": -200,
      "material": "DIAMOND",
      "item_name": "钻石",
      "item_id": "diamond",
      "price": 50.0,
      "stacking_amount": 64,
      "shop_type": "SELLING",
      "weighted_avg_price": 48.3,
      "price_ratio": 1.04,
      "price_reasonable": true
    }
  ]
}
```

---

### 2.3 搜索商店

```
GET /api/shops/search
```

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `keyword` | string | **是** | 搜索关键词（匹配物品名、材质、店主名） |

**示例:**
```
GET /api/shops/search?keyword=钻石
```

**响应格式与 `/api/shops` 相同。**

---

### 2.4 统计信息

```
GET /api/stats
```

**响应:**
```json
{
  "total_shops": 100,
  "filtered_shops": 85,
  "filter_enabled": true,
  "min_ratio": 0.6,
  "max_ratio": 1.5,
  "last_refresh": 1700000000000,
  "qs_available": true
}
```

- `total_shops`: 全量商店数
- `filtered_shops`: 过滤后保留数
- `filter_enabled`: 过滤是否启用
- `min_ratio` / `max_ratio`: 价格合理度区间
- `last_refresh`: 上次数据刷新时间戳 (ms)

---

## 3. 字段说明

每条商店数据 (`shops[]` 数组中的元素) 的字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `shop_id` | long | 商店唯一 ID |
| `owner_uuid` | string | 店主 UUID |
| `owner_name` | string | 店主名称 |
| `world` | string | 所在世界 |
| `x` | int | X 坐标 |
| `y` | int | Y 坐标 |
| `z` | int | Z 坐标 |
| `material` | string | 物品材质（如 `DIAMOND`、`ACACIA_BOAT`） |
| `item_name` | string | 物品显示名称 |
| `item_id` | string | 物品 ID，去 `minecraft:` 前缀后全小写（如 `diamond`、`acacia_boat`） |
| `price` | double | 单价 |
| `stacking_amount` | int | 每次交易数量 |
| `shop_type` | string | `SELLING`（出售）或 `BUYING`（收購） |
| `weighted_avg_price` | double | 该物品的加权历史均价 |
| `price_ratio` | double | 当前价格 / 加权均价 |
| `price_reasonable` | boolean | 价格是否合理（比值在 `min_ratio` ~ `max_ratio` 之间） |

---

## 4. 价格过滤说明

插件基于 QuickShop 交易记录计算每个物品的**加权历史均价**:

```
加权历史均价 = Σ(成交价 × 采购量) / Σ(采购量)
价格合理度   = 当前定价 / 加权历史均价
```

- 比值在 `0.6 ~ 1.5` 之间 → `price_reasonable: true`，默认显示
- 超出区间 → 视为异常定价（天价/恶意低价），默认过滤
- 默认 `/api/shops` 只返回合理价格的商店
- 想看全部请加 `?show_all=true`
- 区间可在 `config.yml` 中调整

---

## 5. 错误码

| HTTP 状态码 | 说明 |
|-------------|------|
| 200 | 成功 |
| 400 | 参数缺失（如搜索缺少 keyword） |
| 500 | 服务器内部错误 |

---

## 6. 注意事项

- 数据每 **60 秒**（可配）从 QuickShop 同步一次，非实时
- 新上架物品无历史交易数据时，默认视为合理价格（不误杀）
- CORS 已开放，支持跨域请求
- 端口和过滤参数可在 `plugins/QSFilterPlugin/config.yml` 中修改
- `/api/health` 不经过过滤引擎，始终可用
