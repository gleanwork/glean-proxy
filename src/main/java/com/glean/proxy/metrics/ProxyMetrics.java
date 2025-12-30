package com.glean.proxy.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.logging.Logger;

/**
 * Metrics collector for proxy requests using OpenTelemetry.
 * Records request counts and latency distributions with labels for status code and method.
 */
public class ProxyMetrics {
  private static final Logger logger = Logger.getLogger(ProxyMetrics.class.getName());
  private static ProxyMetrics instance;

  // Attribute keys for labels
  private static final AttributeKey<String> STATUS_CODE = AttributeKey.stringKey("status_code");
  private static final AttributeKey<String> METHOD = AttributeKey.stringKey("method");

  private final LongCounter requestCounter;
  private final DoubleHistogram latencyHistogram;

  private ProxyMetrics(Meter meter) {
    this.requestCounter = meter
        .counterBuilder("proxy/request_count")
        .setDescription("Total number of proxy requests")
        .setUnit("1")
        .build();

    this.latencyHistogram = meter
        .histogramBuilder("proxy/request_latency")
        .setDescription("Latency of proxy requests in milliseconds")
        .setUnit("ms")
        .build();

    logger.info("ProxyMetrics initialized with OpenTelemetry");
  }

  public static synchronized void initialize(Meter meter) {
    if (instance == null) {
      instance = new ProxyMetrics(meter);
    }
  }

  public static synchronized ProxyMetrics getInstance() {
    if (instance == null) {
      throw new IllegalStateException("ProxyMetrics not initialized. Call initialize(Meter) first.");
    }
    return instance;
  }

  public void recordRequest(int statusCode, String method, long latencyMs) {
    try {
      Attributes attributes = Attributes.builder()
          .put(STATUS_CODE, String.valueOf(statusCode))
          .put(METHOD, method)
          .build();

      requestCounter.add(1, attributes);
      latencyHistogram.record(latencyMs, attributes);

    } catch (Exception e) {
      logger.severe("Failed to record metrics: " + e.getMessage());
    }
  }
}
