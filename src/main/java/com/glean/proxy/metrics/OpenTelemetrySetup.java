package com.glean.proxy.metrics;

import com.google.cloud.opentelemetry.metric.GoogleCloudMetricExporter;
import com.google.cloud.opentelemetry.metric.MetricConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;
import java.io.Closeable;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Initializes and configures OpenTelemetry with GCP Cloud Monitoring exporter.
 */
public class OpenTelemetrySetup implements Closeable {
  private static final Logger logger = Logger.getLogger(OpenTelemetrySetup.class.getName());
  private static final String INSTRUMENTATION_SCOPE_NAME = "glean-proxy";

  private final OpenTelemetrySdk openTelemetry;
  private final SdkMeterProvider meterProvider;

  public OpenTelemetrySetup(String projectId) {
    try {
      logger.info("Initializing OpenTelemetry for project: " + projectId);

      // Create resource with service name
      Resource resource = Resource.getDefault()
          .merge(Resource.builder()
              .put(ResourceAttributes.SERVICE_NAME, "glean-proxy")
              .put(ResourceAttributes.SERVICE_NAMESPACE, "glean")
              .build());

      // Create GCP Cloud Monitoring exporter
      MetricConfiguration metricConfiguration = MetricConfiguration.builder()
          .setProjectId(projectId)
          .setPrefix("custom.googleapis.com")  // Use custom prefix instead of default workload.googleapis.com
          .build();
      MetricExporter metricExporter =
          GoogleCloudMetricExporter.createWithConfiguration(metricConfiguration);

      // Create periodic metric reader (exports every 60 seconds)
      PeriodicMetricReader metricReader = PeriodicMetricReader.builder(metricExporter)
          .setInterval(Duration.ofSeconds(60))
          .build();

      // Create meter provider
      this.meterProvider = SdkMeterProvider.builder()
          .setResource(resource)
          .registerMetricReader(metricReader)
          .build();

      // Build OpenTelemetry SDK
      this.openTelemetry = OpenTelemetrySdk.builder()
          .setMeterProvider(meterProvider)
          .build();


    } catch (Exception e) {
      logger.severe("Failed to initialize OpenTelemetry: " + e.getMessage());
    }
  }

  public Meter getMeter() {
    return openTelemetry.getMeter(INSTRUMENTATION_SCOPE_NAME);
  }

  public OpenTelemetry getOpenTelemetry() {
    return openTelemetry;
  }

  @Override
  public void close() {
    logger.info("Shutting down OpenTelemetry...");
    if (meterProvider != null) {
      meterProvider.close();
    }
    logger.info("OpenTelemetry shut down");
  }
}
