package cn.hdbstudio.qsfilter.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

public class PluginConfig {

    private final String httpHost;
    private final int httpPort;
    private final List<String> corsOrigins;

    private final boolean filterEnabled;
    private final double minRatio;
    private final double maxRatio;
    private final int minRecordCount;

    private final boolean encryptionEnabled;
    private byte[] secretKey; // mutable for auto-generation

    private final int refreshIntervalSeconds;

    private final String lfApiUrl;

    public PluginConfig(YamlConfiguration yaml) {
        this.httpHost = yaml.getString("http.host", "0.0.0.0");
        this.httpPort = yaml.getInt("http.port", 8765);
        this.corsOrigins = yaml.getStringList("http.cors-allowed-origins");

        this.filterEnabled = yaml.getBoolean("filter.enabled", true);
        this.minRatio = yaml.getDouble("filter.min-ratio", 0.6);
        this.maxRatio = yaml.getDouble("filter.max-ratio", 1.5);
        this.minRecordCount = yaml.getInt("filter.min-record-count", 3);

        this.encryptionEnabled = yaml.getBoolean("encryption.enabled", true);
        String keyStr = yaml.getString("encryption.secret-key", "");
        this.secretKey = (keyStr != null && !keyStr.isEmpty())
                ? java.util.Base64.getDecoder().decode(keyStr)
                : null;

        this.refreshIntervalSeconds = yaml.getInt("data-collection.refresh-interval-seconds", 60);

        this.lfApiUrl = yaml.getString("lf-api-url", "");
    }

    public static PluginConfig load(File dataFolder) {
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            // 如果 config.yml 不存在，从 jar 中复制默认配置
            // saveDefaultConfig 会在主类中处理，这里直接读取
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        return new PluginConfig(yaml);
    }

    public void saveSecretKey(File dataFolder, byte[] key) {
        File configFile = new File(dataFolder, "config.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        yaml.set("encryption.secret-key", java.util.Base64.getEncoder().encodeToString(key));
        try {
            yaml.save(configFile);
        } catch (Exception e) {
            throw new RuntimeException("无法保存密钥到配置文件", e);
        }
    }

    // ---- Getters ----

    public String getHttpHost() { return httpHost; }
    public int getHttpPort() { return httpPort; }
    public List<String> getCorsOrigins() { return corsOrigins; }

    public boolean isFilterEnabled() { return filterEnabled; }
    public double getMinRatio() { return minRatio; }
    public double getMaxRatio() { return maxRatio; }
    public int getMinRecordCount() { return minRecordCount; }

    public boolean isEncryptionEnabled() { return encryptionEnabled; }
    public byte[] getSecretKey() { return secretKey; }
    public void setSecretKey(byte[] key) { this.secretKey = key; }

    public int getRefreshIntervalSeconds() { return refreshIntervalSeconds; }

    /** /qsfilter lf 的前端 API 地址。为空时自动使用本地 http://host:port */
    public String getLfApiUrl() { return lfApiUrl; }
}
