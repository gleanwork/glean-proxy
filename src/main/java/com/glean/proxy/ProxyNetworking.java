package com.glean.proxy;

import com.glean.proxy.metrics.ProxyMetrics;
import com.glean.proxy.metrics.ProxyMetricsExporter;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.logging.Logger;
import org.littleshoot.proxy.ChainedProxyManager;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.littleshoot.proxy.impl.ThreadPoolConfiguration;

public class ProxyNetworking {
  private static final Logger logger = Logger.getLogger(ProxyNetworking.class.getName());
  private static ProxyMetricsExporter metricsExporter;

  protected final ThreadPoolConfiguration threadPoolConfiguration;
  protected final FilterConfiguration filterConfiguration;
  protected final ChainedProxyManager chainedProxyManager;

  public void run(int port) {
    DynamicHttpFiltersSourceAdapter filtersSource =
        new DynamicHttpFiltersSourceAdapter(filterConfiguration);

    final HttpProxyServer server =
        DefaultHttpProxyServer.bootstrap()
            .withAddress(new InetSocketAddress(port))
            .withConnectTimeout(1200000) // millis
            .withIdleConnectionTimeout(1200) // seconds
            .withThreadPoolConfiguration(threadPoolConfiguration)
            .withFiltersSource(filtersSource)
            .withChainProxyManager(chainedProxyManager)
            .start();
    try {
      Thread.sleep(Long.MAX_VALUE);
    } catch (InterruptedException e) {
      logger.info("Interrupted");
    }
    server.abort();
  }

  private ProxyNetworking(Builder builder) {
    threadPoolConfiguration = builder.threadPoolConfiguration;
    filterConfiguration = builder.filterConfiguration;
    chainedProxyManager = builder.chainedProxyManager;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private ThreadPoolConfiguration threadPoolConfiguration;
    private FilterConfiguration filterConfiguration;
    private ChainedProxyManager chainedProxyManager;
    
    public Builder withThreadPoolConfiguration(ThreadPoolConfiguration threadPoolConfiguration) {
      this.threadPoolConfiguration = threadPoolConfiguration;
      return this;
    }

    public Builder withFilterConfiguration(FilterConfiguration filterConfiguration) {
      this.filterConfiguration = filterConfiguration;
      return this;
    }

    public Builder withChainedProxyManager(ChainedProxyManager chainedProxyManager) {
      this.chainedProxyManager = chainedProxyManager;
      return this;
    }

    public ProxyNetworking build() {
      if (threadPoolConfiguration == null) {
        threadPoolConfiguration = createThreadPoolConfigurationFromEnvironment();
      }
      if (filterConfiguration == null) {
        filterConfiguration = FilterConfiguration.fromEnvironment();
      }
      if (chainedProxyManager == null) {
        chainedProxyManager = ChainedProxyConfiguration.fromEnvironment();
      }

      // Initialize metrics exporter
      initializeMetricsExporter();

      return new ProxyNetworking(this);
    }
  }

  private static void initializeMetricsExporter() {
    String gcpProjectId = System.getenv("GCP_PROJECT_ID");
    String enableMetricsExport = System.getenv("ENABLE_METRICS_EXPORT");

    // Always initialize ProxyMetrics for local tracking
    ProxyMetrics metrics = ProxyMetrics.getInstance();

    // Check if metrics export is explicitly disabled
    if (enableMetricsExport != null &&
        (enableMetricsExport.equalsIgnoreCase("false") || enableMetricsExport.equals("0"))) {
      logger.info("Metrics export disabled via ENABLE_METRICS_EXPORT. Metrics will be tracked locally only.");
      return;
    }

    if (gcpProjectId == null || gcpProjectId.isEmpty()) {
      logger.warning("GCP_PROJECT_ID not set. Metrics will be tracked but not exported to Cloud Monitoring.");
      return;
    }

    try {
      logger.info("Initializing ProxyMetricsExporter for project: " + gcpProjectId);
      metricsExporter = new ProxyMetricsExporter(gcpProjectId, Clock.systemUTC(), metrics.getMetrics());
    } catch (Exception e) {
      logger.severe("Failed to initialize ProxyMetricsExporter: " + e.getMessage());
    }
  }

  private static ThreadPoolConfiguration createThreadPoolConfigurationFromEnvironment() {
    int numAcceptorThreads =
        Integer.parseInt(System.getenv().getOrDefault("NUM_ACCEPTOR_THREADS", "4"));
    int numClientToProxyWorkerThreads =
        Integer.parseInt(System.getenv().getOrDefault("NUM_CLIENT_TO_PROXY_WORKER_THREADS", "16"));
    int numProxyToServerWorkerThreads =
        Integer.parseInt(System.getenv().getOrDefault("NUM_PROXY_TO_SERVER_WORKER_THREADS", "16"));

    final ThreadPoolConfiguration config = new ThreadPoolConfiguration();
    config.withAcceptorThreads(numAcceptorThreads);
    config.withClientToProxyWorkerThreads(numClientToProxyWorkerThreads);
    config.withProxyToServerWorkerThreads(numProxyToServerWorkerThreads);

    logger.info(
        String.format(
            "Thread pool configuration: acceptorThreads = %d, clientToProxyWorkerThreads = %d,"
                + " proxyToServerWorkerThreads = %d",
            config.getAcceptorThreads(),
            config.getClientToProxyWorkerThreads(),
            config.getProxyToServerWorkerThreads()));

    return config;
  }
}
