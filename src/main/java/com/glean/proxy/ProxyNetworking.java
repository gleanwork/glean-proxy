package com.glean.proxy;

import java.net.InetSocketAddress;
import java.util.logging.Logger;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.littleshoot.proxy.impl.ThreadPoolConfiguration;

public class ProxyNetworking {
  private static final Logger logger = Logger.getLogger(ProxyNetworking.class.getName());

  protected final ThreadPoolConfiguration threadPoolConfiguration;
  protected final FilterConfiguration filterConfiguration;

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
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private ThreadPoolConfiguration threadPoolConfiguration;
    private FilterConfiguration filterConfiguration;

    public Builder withThreadPoolConfiguration(ThreadPoolConfiguration threadPoolConfiguration) {
      this.threadPoolConfiguration = threadPoolConfiguration;
      return this;
    }

    public Builder withFilterConfiguration(FilterConfiguration filterConfiguration) {
      this.filterConfiguration = filterConfiguration;
      return this;
    }

    public ProxyNetworking build() {
      if (threadPoolConfiguration == null) {
        threadPoolConfiguration = createThreadPoolConfigurationFromEnvironment();
      }
      if (filterConfiguration == null) {
        filterConfiguration = FilterConfiguration.fromEnvironment();
      }
      return new ProxyNetworking(this);
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
