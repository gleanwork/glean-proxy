package com.glean.proxy.filters;

import com.glean.proxy.filters.helpers.Utils;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.littleshoot.proxy.HttpFiltersAdapter;

/**
 * If the request originated internally and the address resolves to an internal VPC endpoint, we
 * should block it. We shouldn't be hitting any internal services via the proxy.
 */
public class AwsDisallowInternalAddressForTransitVpc extends HttpFiltersAdapter {
  private final String vpcConnectorEndpoint;
  private HttpResponse response;

  public AwsDisallowInternalAddressForTransitVpc(
      HttpRequest originalRequest, String webhookTarget) {
    super(originalRequest);
    this.response = null;
    try {
      URI uri = new URI(webhookTarget);
      vpcConnectorEndpoint = uri.getHost();
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void proxyToServerResolutionSucceeded(
      String serverHostAndPort, InetSocketAddress resolvedRemoteAddress) {
    String urlIp = resolvedRemoteAddress.getAddress().getHostAddress();
    if (urlIp.equals(vpcConnectorEndpoint)) {
      response = Utils.createHTTPResponse("Forbidden", HttpResponseStatus.FORBIDDEN);
    }
    super.proxyToServerResolutionSucceeded(serverHostAndPort, resolvedRemoteAddress);
  }

  @Override
  public HttpResponse proxyToServerRequest(HttpObject httpObject) {
    return this.response;
  }
}
