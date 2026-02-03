package com.glean.proxy.filters;

import com.glean.proxy.filters.helpers.OnPremisesProxy;
import com.glean.proxy.filters.helpers.UpgradeRequestHandler;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;
import org.littleshoot.proxy.HttpFiltersAdapter;

/**
 * Filters requests to /proxy endpoints in on-premises proxy configurations, rewriting webhook URIs
 * to a configured target and handling optional HTTP-to-HTTPS upgrades via manual forwarding.
 */
public class LegacyRequestFilter extends HttpFiltersAdapter {
  private static final Logger LOGGER = Logger.getLogger(LegacyRequestFilter.class.getName());
  private final OnPremisesProxy legacyProxy;
  private static final String PROXY_HEADER = "OnPrem-Proxy";

  public LegacyRequestFilter(HttpRequest originalRequest, OnPremisesProxy legacyProxy) {
    super(originalRequest);
    this.legacyProxy = legacyProxy;
  }

  @Override
  public HttpResponse clientToProxyRequest(HttpObject httpObject) {
    if (httpObject instanceof HttpRequest) {
      final HttpRequest httpRequest = (HttpRequest) httpObject;
      final String[] splitPath = httpRequest.uri().split("/", 4);
      if (splitPath.length < 3) {
        LOGGER.severe(String.format("Unexpected legacy request format: %s", httpRequest.uri()));
        return createBadRequestResponse("Bad Request to URI: " + httpRequest.uri());
      }

      final String intent = splitPath[2];

      boolean isIntentAllowed = switch (intent) {
        case "webhook" -> true;
        case "api", "rest" -> legacyProxy.isApiIngressAllowed();
        default -> false;
      };

      if (!isIntentAllowed) {
        LOGGER.severe(String.format("Unknown or disallowed intent: %s (allowApiIngress=%s)",
            intent, legacyProxy.isApiIngressAllowed()));
        return createBadRequestResponse("Bad Request to URI: " + httpRequest.uri());
      }

      final String path = httpRequest.uri();

      final String target = legacyProxy.getTargetURL(path);

      final URI uri;
      try {
        uri = new URI(target);
      } catch (URISyntaxException e) {
        LOGGER.severe(e.getMessage());
        return createBadRequestResponse("Bad URI: " + target);
      }
      httpRequest.setUri(target);
      httpRequest.headers().remove("Host");
      httpRequest.headers().add("Host", uri.getHost());
      return null;
    }
    return createBadRequestResponse("Invalid Request");
  }

  @Override
  public HttpResponse proxyToServerRequest(HttpObject httpObject) {
    if (!(httpObject instanceof HttpRequest)) {
      return createBadRequestResponse("Invalid Request");
    }
    final HttpRequest originalRequest = (HttpRequest) httpObject;
    // Protocol upgrade is not needed. LittleProxy can take care of pure HTTP requests.
    if (!legacyProxy.shouldUpgradeProtocol()) {
      return null;
    }
    String upgradedUri = legacyProxy.getTargetURLForUpgradedProtocol(originalRequest.uri());
    return UpgradeRequestHandler.sendUpgradedRequest(originalRequest, upgradedUri, PROXY_HEADER);
  }

  private HttpResponse createBadRequestResponse(String message) {
    DefaultFullHttpResponse response =
        new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.BAD_REQUEST,
            Unpooled.copiedBuffer(message, CharsetUtil.UTF_8));
    response.headers().set("Content-Type", "text/plain; charset=UTF-8");
    response.headers().set("Content-Length", response.content().readableBytes());
    return response;
  }
}
