package com.glean.proxy.filters;

import com.glean.proxy.filters.helpers.AllowedEgressDomains;
import com.glean.proxy.filters.helpers.Utils;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.URI;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.littleshoot.proxy.HttpFiltersAdapter;

/** A filter to only allow egress traffic to explicitly configured domains. */
public class AwsFilterEgressTrafficByDomain extends HttpFiltersAdapter {
  private HttpResponse response = null;

  private static final Logger logger =
      Logger.getLogger(AwsFilterEgressTrafficByDomain.class.getName());

  public AwsFilterEgressTrafficByDomain(
      HttpRequest originalRequest,
      boolean enforceAllowedEgressDomains,
      AllowedEgressDomains allowedEgressDomains) {
    super(originalRequest);

    if (enforceAllowedEgressDomains) {
      try {
        String uri = originalRequest.uri();
        String host =
            originalRequest.method() == HttpMethod.CONNECT
                ?
                // the URI for CONNECT requests are formatted host:port
                uri.split(":")[0].trim().toLowerCase()
                : new URI(uri).getHost().toLowerCase();

        if (!shouldAllowEgressTraffic(host, allowedEgressDomains)) {
          logger.warning("Blocking egress request to forbidden host: " + uri);
          response = Utils.createHTTPResponse("Forbidden", HttpResponseStatus.FORBIDDEN);
        }
      } catch (Exception e) {
        logger.severe("Error parsing URI: " + e.getMessage());
        response = Utils.createHTTPResponse("Bad Request", HttpResponseStatus.BAD_REQUEST);
      }
    }
  }

  @Override
  public HttpResponse clientToProxyRequest(HttpObject httpObject) {
    return response;
  }

  private boolean shouldAllowEgressTraffic(String host, AllowedEgressDomains allowedEgressDomains) {
    if (allowedEgressDomains.individual.contains(host)) {
      return true;
    }

    // Check if any domain suffix (other than the TLD) is wildcarded
    int tldIndex = host.lastIndexOf('.');
    for (int i = tldIndex - 1; i >= 0; i--) {
      if (host.charAt(i) == '.') {
        String domainSuffix = host.substring(i + 1);
        if (allowedEgressDomains.prefixWildcard.contains(domainSuffix)) {
          return true;
        }
      }
    }

    // Check if the host matches any non-prefix wildcard patterns
    for (Pattern wildcardPattern : allowedEgressDomains.wildcard) {
      if (wildcardPattern.matcher(host).matches()) {
        return true;
      }
    }

    return false;
  }
}
