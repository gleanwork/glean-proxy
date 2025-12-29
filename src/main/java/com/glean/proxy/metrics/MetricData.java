package com.glean.proxy.metrics;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores aggregated metric data for a specific tag combination.
 * Thread-safe using atomic operations.
 */
public class MetricData {
  // Latency bucket boundaries in milliseconds
  // Creates buckets: [0,10), [10,25), [25,50), [50,100), [100,250), [250,500),
  //                  [500,1000), [1000,2500), [2500,5000), [5000,10000), [10000,∞)
  public static final double[] BUCKET_BOUNDS = {
    0, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000
  };

  private final String statusCode;
  private final String method;
  private final AtomicLong count = new AtomicLong(0);
  private final AtomicLong totalLatencyMs = new AtomicLong(0);
  private final AtomicLong minLatencyMs = new AtomicLong(Long.MAX_VALUE);
  private final AtomicLong maxLatencyMs = new AtomicLong(0);

  // Histogram buckets: one more than bounds (for overflow bucket)
  private final AtomicLong[] bucketCounts = new AtomicLong[BUCKET_BOUNDS.length + 1];

  public MetricData(String statusCode, String method) {
    this.statusCode = statusCode;
    this.method = method;

    // Initialize bucket counters
    for (int i = 0; i < bucketCounts.length; i++) {
      bucketCounts[i] = new AtomicLong(0);
    }
  }

  public void record(long latencyMs) {
    count.incrementAndGet();
    totalLatencyMs.addAndGet(latencyMs);

    // Update histogram bucket
    int bucketIndex = findBucketIndex(latencyMs);
    bucketCounts[bucketIndex].incrementAndGet();

    // Update min
    long currentMin = minLatencyMs.get();
    while (latencyMs < currentMin) {
      if (minLatencyMs.compareAndSet(currentMin, latencyMs)) {
        break;
      }
      currentMin = minLatencyMs.get();
    }

    // Update max
    long currentMax = maxLatencyMs.get();
    while (latencyMs > currentMax) {
      if (maxLatencyMs.compareAndSet(currentMax, latencyMs)) {
        break;
      }
      currentMax = maxLatencyMs.get();
    }
  }

  private int findBucketIndex(long latencyMs) {
    for (int i = 0; i < BUCKET_BOUNDS.length; i++) {
      if (latencyMs < BUCKET_BOUNDS[i]) {
        return i;
      }
    }
    // Overflow bucket (values >= last bound)
    return BUCKET_BOUNDS.length;
  }

  public String getStatusCode() {
    return statusCode;
  }

  public String getMethod() {
    return method;
  }

  public long getCount() {
    return count.get();
  }

  public long getTotalLatencyMs() {
    return totalLatencyMs.get();
  }

  public long getMinLatencyMs() {
    long min = minLatencyMs.get();
    return min == Long.MAX_VALUE ? 0 : min;
  }

  public long getMaxLatencyMs() {
    return maxLatencyMs.get();
  }

  public double getAvgLatencyMs() {
    long c = count.get();
    return c == 0 ? 0 : (double) totalLatencyMs.get() / c;
  }

  /**
   * Get bucket counts for histogram.
   * Returns a snapshot of current bucket counts.
   */
  public long[] getBucketCounts() {
    long[] counts = new long[bucketCounts.length];
    for (int i = 0; i < bucketCounts.length; i++) {
      counts[i] = bucketCounts[i].get();
    }
    return counts;
  }

  /**
   * Reset all metrics (called after export).
   */
  public void reset() {
    count.set(0);
    totalLatencyMs.set(0);
    minLatencyMs.set(Long.MAX_VALUE);
    maxLatencyMs.set(0);

    // Reset histogram buckets
    for (AtomicLong bucket : bucketCounts) {
      bucket.set(0);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MetricData that = (MetricData) o;
    return Objects.equals(statusCode, that.statusCode) && Objects.equals(method, that.method);
  }

  @Override
  public int hashCode() {
    return Objects.hash(statusCode, method);
  }

  @Override
  public String toString() {
    return String.format(
        "MetricData{status=%s, method=%s, count=%d, avg=%.2fms, min=%dms, max=%dms}",
        statusCode, method, getCount(), getAvgLatencyMs(), getMinLatencyMs(), getMaxLatencyMs());
  }
}
