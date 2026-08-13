package com.glean.proxy.filters;

import com.glean.proxy.filters.helpers.CachedNicResolver;
import com.glean.proxy.filters.helpers.IpRouteNicResolver;
import com.glean.proxy.filters.helpers.NicResolver;
import com.glean.proxy.filters.helpers.Utils;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ipfilter.IpFilterRuleType;
import io.netty.handler.ipfilter.IpSubnetFilterRule;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.littleshoot.proxy.HttpFiltersAdapter;

/**
 * If the request originated internally and the address resolves to an internal VPC endpoint, we
 * should block it. We shouldn't be hitting any internal services via the proxy.
 */
public class GCPDisallowInternalAddressFilter extends HttpFiltersAdapter {

  private final String proxyType;
  private NicResolver nicResolver;
  private final String gkeServiceIpRange;
  private final List<IpSubnetFilterRule> privateIpAllowlist;

  private HttpResponse response;

  public GCPDisallowInternalAddressFilter(
      HttpRequest originalRequest,
      String proxyType,
      String gkeServiceIpRange,
      List<String> privateIpAllowlistCidrs) {
    super(originalRequest);
    this.proxyType = proxyType;
    this.nicResolver = new CachedNicResolver(new IpRouteNicResolver());
    this.gkeServiceIpRange = gkeServiceIpRange;
    this.privateIpAllowlist = buildAllowlistRules(privateIpAllowlistCidrs);
    this.response = null;
  }

  public GCPDisallowInternalAddressFilter(
      HttpRequest originalRequest, String proxyType, String gkeServiceIpRange) {
    this(originalRequest, proxyType, gkeServiceIpRange, Collections.emptyList());
  }

  protected GCPDisallowInternalAddressFilter(
      HttpRequest originalRequest,
      String proxyType,
      NicResolver nicResolver,
      String gkeServiceIpRange,
      List<String> privateIpAllowlistCidrs) {
    this(originalRequest, proxyType, gkeServiceIpRange, privateIpAllowlistCidrs);
    this.nicResolver = nicResolver;
  }

  protected GCPDisallowInternalAddressFilter(
      HttpRequest originalRequest,
      String proxyType,
      NicResolver nicResolver,
      String gkeServiceIpRange) {
    this(originalRequest, proxyType, nicResolver, gkeServiceIpRange, Collections.emptyList());
  }

  // This runs after proxyToServerResolutionSucceeded which updates the response variable if
  // required.
  @Override
  public HttpResponse proxyToServerRequest(HttpObject httpObject) {
    return this.response;
  }

  @Override
  public void proxyToServerResolutionSucceeded(
      String serverHostAndPort, InetSocketAddress resolvedRemoteAddress) {
    blockRequestIfPrivateAddressIsRoutedTowardsInternalService(resolvedRemoteAddress);
    super.proxyToServerResolutionSucceeded(serverHostAndPort, resolvedRemoteAddress);
  }

  // Update response variable if we want to return early and break the connection
  private void blockRequestIfPrivateAddressIsRoutedTowardsInternalService(
      InetSocketAddress address) {
    InetAddress inetAddress = address.getAddress();
    String resolvedIp = inetAddress.getHostAddress();
    boolean isPrivateIp = isPrivateAddress(inetAddress);

    switch (proxyType) {
      case "STANDALONE":
        if (isPrivateIp && !isAllowlisted(resolvedIp)) {
          response =
              Utils.createHTTPResponse(
                  String.format("Ingress Route not allowed For Private IPs: %s", resolvedIp),
                  HttpResponseStatus.FORBIDDEN);
        }
        break;
      case "SHARED_VPC":
      case "TRANSIT":
        String nic;
        try {
          nic = nicResolver.resolve(resolvedIp);
        } catch (Exception e) {
          response =
              Utils.createHTTPResponse(
                  String.format("Could not resolve route: %s", e.getMessage()),
                  HttpResponseStatus.UNAUTHORIZED);
          return;
        }
        // if routed towards an internal service and its a private ip, Do not allow
        // unless the IP is in the allowlist
        if (nic.equals("eth1") && isPrivateIp && !isAllowlisted(resolvedIp)) {
          response =
              Utils.createHTTPResponse(
                  String.format("Ingress Route not allowed For Private IPs: %s", resolvedIp),
                  HttpResponseStatus.FORBIDDEN);
        }
        break;
      default:
        response =
            Utils.createHTTPResponse(
                String.format("Unknown proxy type: %s", proxyType),
                HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private boolean isAllowlisted(String ipAddress) {
    if (privateIpAllowlist.isEmpty()) {
      return false;
    }
    InetSocketAddress socketAddress = new InetSocketAddress(ipAddress, 0);
    for (IpSubnetFilterRule rule : privateIpAllowlist) {
      if (rule.matches(socketAddress)) {
        return true;
      }
    }
    return false;
  }

  private static List<IpSubnetFilterRule> buildAllowlistRules(List<String> cidrs) {
    if (cidrs == null || cidrs.isEmpty()) {
      return Collections.emptyList();
    }
    List<IpSubnetFilterRule> rules = new ArrayList<>(cidrs.size());
    for (String cidr : cidrs) {
      String trimmed = cidr.trim();
      if (!trimmed.isEmpty()) {
        rules.add(new IpSubnetFilterRule(trimmed, IpFilterRuleType.ACCEPT));
      }
    }
    return Collections.unmodifiableList(rules);
  }

  private boolean isPrivateAddress(InetAddress address) {
    // Loopback address covers 127.0.0.1 or ::1
    // Site-local address covers 10.x.x.x, 172.16.x.x to 172.31.x.x, 192.168.x.x
    // Link-local address covers 169.254.x.x or fe80::/10
    return address.isLoopbackAddress()
        || address.isSiteLocalAddress()
        || address.isLinkLocalAddress()
        || isGoogleManagedIp(address.getHostAddress());
  }

  private boolean isGoogleManagedIp(String ipAddress) {
    if (ipAddress == null || ipAddress.isEmpty()) {
      return false;
    }
    return new IpSubnetFilterRule(gkeServiceIpRange, IpFilterRuleType.ACCEPT)
        .matches(new InetSocketAddress(ipAddress, 0));
  }
}
