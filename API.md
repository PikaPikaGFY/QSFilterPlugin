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
| `type` | string | 否 | 商店类型：`SELLING`（出售）、`BUYING`（收购） |
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

### 2.4 物品价格查询

```
GET /api/price/{item_id}
```

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `item_id` | path | **是** | 物品 ID，小写（如 `diamond`、`acacia_boat`） |

这是 `/qsfilter lf <物品ID>` 和 Web 前端的共用接口。

**示例:**
```
GET /api/price/diamond
GET /api/price/acacia_boat
```

**有数据时 (200):**
```json
{
  "material": "DIAMOND",
  "item_id": "diamond",
  "weighted_avg_price": 48.52,
  "record_count": 156,
  "total_sold_amount": 9840,
  "current_shop_count": 12,
  "selling_shop_count": 8,
  "selling_min_price": 42.0,
  "selling_max_price": 55.0,
  "selling_avg_price": 49.0,
  "buying_shop_count": 4,
  "buying_min_price": 30.0,
  "buying_max_price": 40.0,
  "buying_avg_price": 35.5
}
```

**无数据时 (200):**
```json
{
  "material": "DIAMOND",
  "item_id": "diamond",
  "weighted_avg_price": -1,
  "record_count": 0,
  "total_sold_amount": 0,
  "current_shop_count": 0,
  "selling_shop_count": 0,
  "selling_min_price": -1,
  "selling_max_price": -1,
  "selling_avg_price": -1,
  "buying_shop_count": 0,
  "buying_min_price": -1,
  "buying_max_price": -1,
  "buying_avg_price": -1
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `material` | string | 物品材质原名（大写） |
| `item_id` | string | 物品 ID（小写） |
| `weighted_avg_price` | number | 加权历史均价 `Σ(成交价×数量) / Σ数量`，无数据时 -1 |
| `record_count` | int | 该物品的历史成交笔数 |
| `total_sold_amount` | int | 该物品历史累计售出总数量 |
| `current_shop_count` | int | 该物品当前挂牌的商店数量 |
| `current_min_price` | number | 当前挂牌最低价，无数据时 -1 |
| `current_max_price` | number | 当前挂牌最高价，无数据时 -1 |
| `current_avg_price` | number | 当前挂牌均价，无数据时 -1 |

---

### 2.5 统计信息

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
| `shop_type` | string | `SELLING`（出售）或 `BUYING`（收购） |
| `weighted_avg_price` | double | 该物品的加权历史均价 |
| `price_ratio` | double | 当前价格 / 加权均价 |
| `price_reasonable` | boolean | 价格是否合理 |

---

## 4. 价格过滤说明

### 4.1 核心公式

插件基于 QuickShop 历史交易记录的 **SELLING（出售）** 成交数据计算每个物品的加权均价：

```
加权历史均价 = Σ(单次成交价 × 单次采购量) / Σ(所有历史采购量)
价格合理度比值 = 当前定价 / 加权历史均价
```

### 4.2 过滤规则

- 比值在 `0.6 ~ 1.5` 之间（可配） → `price_reasonable: true`，API 默认显示
- 超出区间 → 视为异常定价（天价宝库 / 恶意低价），默认过滤
- `BUYING`（收购）商店 → **不参与加权均价计算，也不被过滤**，始终 `price_reasonable: true`
- 交易记录不足 `min-record-count`（默认 3 笔）的物品 → 视作数据不足，不过滤
- 默认 `/api/shops` 只返回合理价格的商店
- 想看全部请加 `?show_all=true`
- 区间和最小记录数可在 `config.yml` 中调整

### 4.3 配置项

```yaml
filter:
  enabled: true          # 是否启用过滤
  min-ratio: 0.6         # 价格合理度下限
  max-ratio: 1.5         # 价格合理度上限
  min-record-count: 3    # 最少交易记录笔数（不足则不过滤）
```

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
- 新上架物品 / 交易记录不足的物品默认视为合理价格（不误杀）
- CORS 已开放，支持跨域请求
- 端口和过滤参数可在 `plugins/QSFilterPlugin/config.yml` 中修改
- `/api/health` 不经过过滤引擎，始终可用
- `/api/price/{item_id}` 是 `/qsfilter lf` 命令和 Web 前端的共用数据源

---

## 7. 前端 WebSQL 对接

### 7.1 架构

前端可以自行建立 WebSQL 数据库，插件通过 HTTP 将数据提供给前端消费。

```
┌──────────────┐  定时拉取 /api/shops   ┌──────────────────┐
│  前端 WebSQL  │ ◄─────────────────── │  QSFilterPlugin   │
│ (Wubiepcu)   │                       │  (插件, port 8765) │
└──────┬───────┘                       └──────────────────┘
       │ /api/price/{item_id}
       ▲
       │ HTTP GET
┌──────┴───────┐
│ /qsfilter lf │
└──────────────┘
```

### 7.2 对接步骤

**步骤 1：前端定时拉取商店数据**

定时调用（建议每 60 秒）：
```
GET http://<MC服务器IP>:8765/api/shops?show_all=true&limit=200
```

解析 `shops[]` 数组写入前端 WebSQL。每条商店的 `weighted_avg_price`、`price_ratio`、`price_reasonable` 字段插件已经算好，前端直接用。

**步骤 2：前端实现 `/api/price/{item_id}`**

从 WebSQL 查询指定物品，返回格式与插件本地 API 一致，见 [2.4 物品价格查询](#24-物品价格查询)。

**步骤 3：配置插件指向前端**

在 MC 服务器 `plugins/QSFilterPlugin/config.yml` 中设置：
```yaml
# /qsfilter lf 的前端 API 地址（指向 Web 服务器，留空则用本地 http.host:http.port）
lf-api-url: "http://<前端服务器IP>:<端口>"
```

| `lf-api-url` 值 | `/qsfilter lf` 请求目标 |
|---|---|
| 空（默认） | `http://<http.host>:<http.port>/api/price/<itemId>` |
| `https://web.example.com` | `https://web.example.com/api/price/<itemId>` |

### 7.3 前端 WebSQL 建议表结构

```sql
-- 商店快照表（每次全量刷新）
CREATE TABLE shops (
    shop_id       INTEGER PRIMARY KEY,
    owner_uuid    TEXT,
    owner_name    TEXT,
    world         TEXT,
    x             INTEGER, y INTEGER, z INTEGER,
    material      TEXT,
    item_name     TEXT,
    item_id       TEXT,
    price         REAL,
    stacking_amount INTEGER,
    shop_type     TEXT,
    weighted_avg_price REAL,
    price_ratio   REAL,
    price_reasonable INTEGER
);

-- 物品统计快照
CREATE TABLE price_stats (
    material      TEXT PRIMARY KEY,
    record_count  INTEGER,
    total_sold_amount INTEGER,
    selling_count INTEGER,
    selling_min   REAL, selling_max REAL, selling_avg REAL,
    buying_count  INTEGER,
    buying_min    REAL, buying_max REAL, buying_avg REAL
);
```

---

## 8. WebHook 推送

插件在商店变更时**主动 POST** 到前端，无需前端轮询。

### 8.1 配置

```yaml
webhook:
  url: "http://<前端服务器IP>:<端口>"
```

留空则禁用。配置后 `/qsfilter condition` 会显示 WebHook 状态。

### 8.2 端点（由前端实现）

#### POST /webhook/shop-create

商店新建时推送，POST body 为完整的商店 JSON（格式同 `/api/shops` 的 shop 对象）。

```json
{
  "shop_id": 42,
  "owner_uuid": "a1b2c3d4-...",
  "owner_name": "Steve",
  "world": "world",
  "x": 100, "y": 64, "z": -200,
  "material": "DIAMOND",
  "item_name": "钻石",
  "item_id": "diamond",
  "price": 50.0,
  "stacking_amount": 64,
  "shop_type": "SELLING"
}
```

#### POST /webhook/shop-delete

商店删除时推送：

```json
{
  "shop_id": 42
}
```

#### POST /webhook/shop-price

商店价格变动时推送：

```json
{
  "shop_id": 42,
  "old_price": 50.0,
  "new_price": 60.0,
  "shop": { ... 当前完整商店数据 ... }
}
```

#### POST /webhook/shop-type

商店类型变更（出售↔收购）时推送：

```json
{
  "shop_id": 42,
  "old_type": "SELLING",
  "new_type": "BUYING",
  "shop": { ... 当前完整商店数据 ... }
}
```

### 8.3 实现要点

- 前端实现上述 4 个 `POST /webhook/*` 端点
- 插件异步发送，不阻塞游戏主线程，超时 5 秒
- 返回任意 2xx 即视为成功

### 8.4 完整数据流

```
QuickShop 事件触发
    → ShopChangeWebhook (事件监听)
    → POST http://<前端>/webhook/shop-*
    → 前端 WebSQL 即时更新，无需 60 秒轮询
```
