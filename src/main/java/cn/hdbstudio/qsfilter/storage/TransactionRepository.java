package cn.hdbstudio.qsfilter.storage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 交易记录数据库操作。
 */
public class TransactionRepository {

    private final DatabaseManager db;

    public TransactionRepository(DatabaseManager db) {
        this.db = db;
    }

    /**
     * 插入一条交易记录。
     */
    public void insert(String material, String itemName, double price, int amount) {
        String sql = "INSERT INTO transactions (material, item_name, price, amount) VALUES (?, ?, ?, ?)";
        try (var ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, material);
            ps.setString(2, itemName);
            ps.setDouble(3, price);
            ps.setInt(4, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("插入交易记录失败", e);
        }
    }

    /**
     * 计算指定物品的加权历史均价。
     * 公式: Σ(price × amount) / Σ(amount)
     *
     * @return [weightedAvgPrice, totalAmount, recordCount]
     */
    public WeightedPriceResult getWeightedAvgPrice(String material) {
        String sql = """
            SELECT
                SUM(price * amount) AS total_price_amount,
                SUM(amount)         AS total_amount,
                COUNT(*)            AS record_count
            FROM transactions
            WHERE material = ?
        """;
        try (var ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, material);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    double sumPriceAmount = rs.getDouble("total_price_amount");
                    int sumAmount = rs.getInt("total_amount");
                    int count = rs.getInt("record_count");

                    if (sumAmount > 0) {
                        double avg = sumPriceAmount / sumAmount;
                        return new WeightedPriceResult(avg, sumAmount, count);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询加权均价失败: " + material, e);
        }
        return new WeightedPriceResult(0, 0, 0);
    }

    /**
     * 获取所有物品的加权均价（批量查询，用于高效刷新）。
     */
    public List<MaterialWeightedPrice> getAllWeightedPrices() {
        String sql = """
            SELECT
                material,
                SUM(price * amount) AS total_price_amount,
                SUM(amount)         AS total_amount,
                COUNT(*)            AS record_count
            FROM transactions
            GROUP BY material
        """;
        List<MaterialWeightedPrice> results = new ArrayList<>();
        try (var stmt = db.getConnection().createStatement();
             var rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String material = rs.getString("material");
                double sumPriceAmount = rs.getDouble("total_price_amount");
                int sumAmount = rs.getInt("total_amount");
                int count = rs.getInt("record_count");

                if (sumAmount > 0) {
                    double avg = sumPriceAmount / sumAmount;
                    results.add(new MaterialWeightedPrice(material, avg, sumAmount, count));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("批量查询加权均价失败", e);
        }
        return results;
    }

    public int countAll() {
        try (var stmt = db.getConnection().createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM transactions")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    // ---- Record types ----

    public record WeightedPriceResult(double avgPrice, int totalAmount, int recordCount) {}

    public record MaterialWeightedPrice(String material, double avgPrice, int totalAmount, int recordCount) {}
}
