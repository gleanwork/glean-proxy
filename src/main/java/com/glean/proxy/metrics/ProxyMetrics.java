package com.glean.proxy.metrics;

import com.glean.proxy.metrics.ProxyMetricsExporter.MetricKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Metrics collector for proxy requests.
 * Uses manual in-memory tracking instead of OpenCensus views.
 */
public class ProxyMetrics {
  private static final Logger logger = Logger.getLogger(ProxyMetrics.class.getName());
  private static ProxyMetrics instance;

  // Manual metric tracking (thread-safe)
  private final ConcurrentHashMap<MetricKey, MetricData> metrics = new ConcurrentHashMap<>();

  public static synchronized ProxyMetrics getInstance() {
    if (instance == null) {
      instance = new ProxyMetrics();
    }
    return instance;
  }

  public ConcurrentHashMap<MetricKey, MetricData> getMetrics() {
    return metrics;
  }

  public void recordRequest(int statusCode, String method, long latencyMs) {
    try {
      MetricKey key = new MetricKey(String.valueOf(statusCode), method);
      MetricData data = metrics.computeIfAbsent(key, k -> new MetricData(k.getStatusCode(), k.getMethod()));
      data.record(latencyMs);
    } catch (Exception e) {
      logger.severe("Failed to record metrics: " + e.getMessage());
    }
  }
}
