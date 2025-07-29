package com.glean.proxy.filters;

import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.net.InetSocketAddress;
import java.util.List;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;

/**
 * Combines multiple HTTP filters into a single filter, executing them in sequence. Returns the
 * first non-null response from any filter in the chain, or null if no filter returns a response.
 */
public class CompositeFilter extends HttpFiltersAdapter {

  private final List<HttpFilters> filters;

  public CompositeFilter(HttpRequest originalRequest, List<HttpFilters> filters) {
    super(originalRequest);
    this.filters = filters;
  }

  @Override
  public HttpResponse clientToProxyRequest(HttpObject httpObject) {
    for (HttpFilters filter : filters) {
      HttpResponse response = filter.clientToProxyRequest(httpObject);
      if (response != null) {
        return response;
      }
    }
    return null;
  }

  @Override
  public void proxyToServerResolutionSucceeded(
      String serverHostAndPort, InetSocketAddress resolvedRemoteAddress) {
    for (HttpFilters filter : filters) {
      filter.proxyToServerResolutionSucceeded(serverHostAndPort, resolvedRemoteAddress);
    }
  }

  @Override
  public HttpResponse proxyToServerRequest(HttpObject httpObject) {
    for (HttpFilters filter : filters) {
      HttpResponse response = filter.proxyToServerRequest(httpObject);
      if (response != null) {
        return response;
      }
    }
    return null;
  }
}
