package com.glean.proxy.metrics;

import com.google.api.Distribution;
import com.google.api.Distribution.BucketOptions;
import com.google.api.Distribution.BucketOptions.Explicit;
import com.google.api.Metric;
import com.google.api.MonitoredResource;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TypedValue;
import com.google.protobuf.Timestamp;
import java.io.Closeable;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Periodically exports proxy metrics to GCP Cloud Monitoring.
 */
public class ProxyMetricsExporter implements Closeable {
  private static final Logger logger = Logger.getLogger(ProxyMetricsExporter.class.getName());
  private static final long EXPORT_INTERVAL_MS = 60000; // 60 seconds

  private final String projectId;
  private final Clock clock;
  private final Timer exportTimer;
  private final ConcurrentHashMap<MetricKey, MetricData> metrics;
  private MetricServiceClient metricServiceClient;

  public ProxyMetricsExporter(
      String projectId, Clock clock, ConcurrentHashMap<MetricKey, MetricData> metrics) {
    this.projectId = projectId;
    this.clock = clock;
    this.metrics = metrics;
    this.exportTimer = new Timer("ProxyMetricsExporter", true);

    // Initialize GCP Monitoring client
    try {
      this.metricServiceClient = MetricServiceClient.create();
      logger.info("MetricServiceClient created successfully");
    } catch (Exception e) {
      logger.severe("Failed to create MetricServiceClient: " + e.getMessage());
      e.printStackTrace();
    }

    // Schedule periodic exports
    exportTimer.scheduleAtFixedRate(
        new TimerTask() {
          @Override
          public void run() {
            exportMetrics();
          }
        },
        EXPORT_INTERVAL_MS,
        EXPORT_INTERVAL_MS);

    logger.info(
        String.format(
            "ProxyMetricsExporter initialized for project %s, exporting every %d seconds",
            projectId, EXPORT_INTERVAL_MS / 1000));
  }

  private void exportMetrics() {
    if (metricServiceClient == null) {
      logger.warning("MetricServiceClient not initialized, skipping export");
      return;
    }

    try {
      Instant now = clock.instant();
      List<TimeSeries> timeSeriesList = new ArrayList<>();

      // Snapshot and reset metrics
      Map<MetricKey, MetricData> snapshot = new ConcurrentHashMap<>(metrics);
      int totalMetrics = 0;

      for (Map.Entry<MetricKey, MetricData> entry : snapshot.entrySet()) {
        MetricKey key = entry.getKey();
        MetricData data = entry.getValue();

        long count = data.getCount();
        if (count == 0) {
          continue; // Skip empty metrics
        }

        totalMetrics++;

        // Create time series for request count
        timeSeriesList.add(createCountTimeSeries(key, data, now));

        // Create time series for request latency (distribution)
        timeSeriesList.add(createLatencyTimeSeries(key, data, now));

        // Reset the metric after export
        MetricData originalData = metrics.get(key);
        if (originalData != null) {
          originalData.reset();
        }
      }

      if (timeSeriesList.isEmpty()) {
        logger.fine("No metrics to export");
        return;
      }

      // Send to GCP Cloud Monitoring
      ProjectName projectName = ProjectName.of(projectId);
      CreateTimeSeriesRequest request =
          CreateTimeSeriesRequest.newBuilder()
              .setName(projectName.toString())
              .addAllTimeSeries(timeSeriesList)
              .build();

      metricServiceClient.createTimeSeries(request);

      logger.info(
          String.format(
              "Successfully exported %d metrics (%d time series) to Cloud Monitoring",
              totalMetrics, timeSeriesList.size()));

    } catch (Exception e) {
      logger.severe("Failed to export metrics: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private TimeSeries createCountTimeSeries(MetricKey key, MetricData data, Instant now) {
    Metric metric =
        Metric.newBuilder()
            .setType("custom.googleapis.com/proxy/request_count")
            .putLabels("status_code", key.getStatusCode())
            .putLabels("method", key.getMethod())
            .build();

    MonitoredResource resource =
        MonitoredResource.newBuilder().setType("global").putLabels("project_id", projectId).build();

    Timestamp timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
    TimeInterval interval =
        TimeInterval.newBuilder().setEndTime(timestamp).setStartTime(timestamp).build();

    Point point =
        Point.newBuilder()
            .setInterval(interval)
            .setValue(TypedValue.newBuilder().setInt64Value(data.getCount()).build())
            .build();

    return TimeSeries.newBuilder()
        .setMetric(metric)
        .setResource(resource)
        .setValueType(com.google.api.MetricDescriptor.ValueType.INT64)
        .setMetricKind(com.google.api.MetricDescriptor.MetricKind.GAUGE)
        .addPoints(point)
        .build();
  }

  private TimeSeries createLatencyTimeSeries(MetricKey key, MetricData data, Instant now) {
    Metric metric =
        Metric.newBuilder()
            .setType("custom.googleapis.com/proxy/request_latency_distribution")
            .putLabels("status_code", key.getStatusCode())
            .putLabels("method", key.getMethod())
            .build();

    MonitoredResource resource =
        MonitoredResource.newBuilder().setType("global").putLabels("project_id", projectId).build();

    Timestamp timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build();
    TimeInterval interval =
        TimeInterval.newBuilder().setEndTime(timestamp).setStartTime(timestamp).build();

    // Create distribution with explicit bucket boundaries
    // Convert double[] to List<Double> for BucketOptions
    List<Double> bounds = Arrays.stream(MetricData.BUCKET_BOUNDS)
        .boxed()
        .collect(Collectors.toList());

    BucketOptions bucketOptions =
        BucketOptions.newBuilder()
            .setExplicitBuckets(Explicit.newBuilder().addAllBounds(bounds).build())
            .build();

    // Get bucket counts from data
    long[] bucketCounts = data.getBucketCounts();

    Distribution.Builder distributionBuilder =
        Distribution.newBuilder()
            .setCount(data.getCount())
            .setMean(data.getAvgLatencyMs())
            .setBucketOptions(bucketOptions);

    // Add bucket counts (convert long[] to List<Long>)
    for (long count : bucketCounts) {
      distributionBuilder.addBucketCounts(count);
    }

    Distribution distribution = distributionBuilder.build();

    Point point =
        Point.newBuilder()
            .setInterval(interval)
            .setValue(TypedValue.newBuilder().setDistributionValue(distribution).build())
            .build();

    return TimeSeries.newBuilder()
        .setMetric(metric)
        .setResource(resource)
        .setValueType(com.google.api.MetricDescriptor.ValueType.DISTRIBUTION)
        .setMetricKind(com.google.api.MetricDescriptor.MetricKind.GAUGE)
        .addPoints(point)
        .build();
  }

  @Override
  public void close() {
    logger.info("Shutting down ProxyMetricsExporter...");
    exportTimer.cancel();

    // Do final export
    exportMetrics();

    if (metricServiceClient != null) {
      metricServiceClient.close();
    }
    logger.info("ProxyMetricsExporter shut down");
  }

  /**
   * Key for grouping metrics by status code and method.
   */
  public static class MetricKey {
    private final String statusCode;
    private final String method;

    public MetricKey(String statusCode, String method) {
      this.statusCode = statusCode;
      this.method = method;
    }

    public String getStatusCode() {
      return statusCode;
    }

    public String getMethod() {
      return method;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MetricKey that = (MetricKey) o;
      return statusCode.equals(that.statusCode) && method.equals(that.method);
    }

    @Override
    public int hashCode() {
      return 31 * statusCode.hashCode() + method.hashCode();
    }

    @Override
    public String toString() {
      return String.format("MetricKey{status=%s, method=%s}", statusCode, method);
    }
  }
}
