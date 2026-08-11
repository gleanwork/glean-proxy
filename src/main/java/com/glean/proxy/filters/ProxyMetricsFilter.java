package com.glean.proxy.filters;

import com.glean.proxy.metrics.ProxyMetrics;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.util.logging.Logger;
import org.littleshoot.proxy.HttpFiltersAdapter;

/**
 * Filter that records metrics for proxy requests using OpenCensus.
 * Tracks request counts and latencies, labeled by HTTP method and success/failure status.
 */
public class ProxyMetricsFilter extends HttpFiltersAdapter {
  private static final Logger logger = Logger.getLogger(ProxyMetricsFilter.class.getName());

  private long requestStartTime;
  private final ProxyMetrics metrics;

  public ProxyMetricsFilter(HttpRequest originalRequest, ProxyMetrics metrics) {
    super(originalRequest);
    this.metrics = metrics;
  }

  @Override
  public HttpResponse clientToProxyRequest(HttpObject httpObject) {
    requestStartTime = System.currentTimeMillis();
    return null;
  }

  @Override
  public HttpObject proxyToClientResponse(HttpObject httpObject) {
    if (httpObject instanceof HttpResponse) {
      HttpResponse response = (HttpResponse) httpObject;
      long latencyMs = System.currentTimeMillis() - requestStartTime;
      String method = originalRequest.method().name();
      int statusCode = response.status().code();

      metrics.recordRequest(statusCode, method, latencyMs);

      logger.finest(
          String.format(
              "Proxy request: method=%s, status=%d, latency=%dms",
              method, statusCode, latencyMs));
    }
    return httpObject;
  }
}
