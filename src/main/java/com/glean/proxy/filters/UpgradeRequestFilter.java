package com.glean.proxy.filters;

import com.glean.proxy.filters.helpers.UpgradeRequestHandler;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.littleshoot.proxy.HttpFiltersAdapter;

/** A filter to upgrade HTTP requests to HTTPS. */
public class UpgradeRequestFilter extends HttpFiltersAdapter {
  private final boolean upgradeHttpRequests;
  private final String originalUri;
  private static final String PROXY_HEADER = "Egress-Proxy";

  public UpgradeRequestFilter(HttpRequest originalRequest, boolean upgradeHttpRequests) {
    super(originalRequest);
    this.originalUri = originalRequest.uri();
    this.upgradeHttpRequests = upgradeHttpRequests;
  }

  @Override
  public HttpResponse proxyToServerRequest(HttpObject httpObject) {
    if (!upgradeHttpRequests || !(httpObject instanceof HttpRequest)) {
      return null;
    }
    final HttpRequest httpsRequest = (HttpRequest) httpObject;
    if (!originalUri.startsWith("http://")) {
      return null;
    }
    String upgradedUri = originalUri.replaceFirst("^http://", "https://");
    return UpgradeRequestHandler.sendUpgradedRequest(httpsRequest, upgradedUri, PROXY_HEADER);
  }
}
