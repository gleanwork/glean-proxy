package com.glean.proxy;

import com.glean.proxy.filters.AwsDisallowInternalAddressForTransitVpc;
import com.glean.proxy.filters.AwsFilterEgressTrafficByDomain;
import com.glean.proxy.filters.GCPDisallowInternalAddressFilter;
import com.glean.proxy.filters.IpAddressRequestFilter;
import com.glean.proxy.filters.IpAddressRequestFilter.ALLOWED_PROXY_ADDRESS_TYPE;
import com.glean.proxy.filters.ProxyDebugFilter;
import com.glean.proxy.filters.ProxyMetricsFilter;
import com.glean.proxy.filters.UpgradeRequestFilter;
import com.glean.proxy.filters.helpers.AllowedEgressDomains;
import com.glean.proxy.metrics.ProxyMetrics;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Logger;
import org.littleshoot.proxy.HttpFilters;

public record FilterConfiguration(
    List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> awsFilters,
    List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> gcpFilters,
    List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> crossPlatformFilters,
    List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> debugFilters) {

  private static final Logger logger = Logger.getLogger(FilterConfiguration.class.getName());

  private static final Map<
          String, Supplier<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>>>
      filterRegistry =
          new HashMap<>(
              Map.of(

                  // AWS filters
                  AwsDisallowInternalAddressForTransitVpc.class.getSimpleName(),
                  () -> {
                    String webhookTarget = System.getenv("WEBHOOK_TARGET");
                    return (request, ctx) ->
                        new AwsDisallowInternalAddressForTransitVpc(request, webhookTarget);
                  },
                  AwsFilterEgressTrafficByDomain.class.getSimpleName(),
                  () -> {
                    boolean enforceAllowedEgressDomains =
                        "true".equalsIgnoreCase(System.getenv("ENFORCE_ALLOWED_EGRESS_DOMAINS"));
                    AllowedEgressDomains allowedEgressDomains =
                        new AllowedEgressDomains(System.getenv("ALLOWED_EGRESS_DOMAINS"));
                    return (request, ctx) ->
                        new AwsFilterEgressTrafficByDomain(
                            request, enforceAllowedEgressDomains, allowedEgressDomains);
                  },

                  // GCP filters
                  GCPDisallowInternalAddressFilter.class.getSimpleName(),
                  () -> {
                    String proxyType = System.getenv("PROXY_TYPE");
                    String gkeServiceIpRange = System.getenv("GKE_SERVICE_IP_RANGE");
                    return (request, ctx) ->
                        new GCPDisallowInternalAddressFilter(request, proxyType, gkeServiceIpRange);
                  },

                  // Cross-platform filters
                  IpAddressRequestFilter.class.getSimpleName(),
                  () -> {
                    String requiredIpAddress = System.getenv("ALLOWED_PROXY_ADDRESS");
                    ALLOWED_PROXY_ADDRESS_TYPE allowedProxyAddressType =
                        ALLOWED_PROXY_ADDRESS_TYPE.valueOf(
                            System.getenv("ALLOWED_PROXY_ADDRESS_TYPE"));
                    return (request, ctx) ->
                        new IpAddressRequestFilter(
                            request, ctx, requiredIpAddress, allowedProxyAddressType);
                  },
                  UpgradeRequestFilter.class.getSimpleName(),
                  () -> {
                    boolean upgradeHttpRequests =
                        "true".equalsIgnoreCase(System.getenv("UPGRADE_HTTP_REQUESTS"));
                    return (request, ctx) -> new UpgradeRequestFilter(request, upgradeHttpRequests);
                  },
                  ProxyMetricsFilter.class.getSimpleName(),
                  () -> {
                    final ProxyMetrics metrics = ProxyMetrics.getInstance();
                    return (request, ctx) -> new ProxyMetricsFilter(request, metrics);
                  },

                  // Debug filters
                  ProxyDebugFilter.class.getSimpleName(),
                  () -> (request, ctx) -> new ProxyDebugFilter(request)));

  /**
   * Can be called before creating a FilterConfiguration to add custom filters. Example usage:
   *
   * <p>FilterConfiguration.registerCustomFilter("AwsCustomFilter", () -> {return (request, ctx) ->
   * new AwsCustomFilter(request); });
   */
  public static void registerCustomFilter(
      String name,
      Supplier<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> filterSupplier) {
    filterRegistry.put(name, filterSupplier);
  }

  public static FilterConfiguration fromFilterNames(
      List<String> awsFilters,
      List<String> gcpFilters,
      List<String> crossPlatformFilters,
      List<String> debugFilters) {
    return new FilterConfiguration(
        buildFilters(awsFilters),
        buildFilters(gcpFilters),
        buildFilters(crossPlatformFilters),
        buildFilters(debugFilters));
  }

  public static FilterConfiguration fromEnvironment() {
    String defaultAwsFilters =
        String.join(
            ",",
            AwsDisallowInternalAddressForTransitVpc.class.getSimpleName(),
            AwsFilterEgressTrafficByDomain.class.getSimpleName());
    String awsFiltersString = System.getenv().getOrDefault("AWS_FILTERS", defaultAwsFilters);

    String defaultGcpFilters =
        String.join(",", GCPDisallowInternalAddressFilter.class.getSimpleName());
    String gcpFiltersString = System.getenv().getOrDefault("GOOGLE_FILTERS", defaultGcpFilters);

    String defaultCrossPlatformFilters =
        String.join(
            ",",
            IpAddressRequestFilter.class.getSimpleName(),
            UpgradeRequestFilter.class.getSimpleName(),
            ProxyMetricsFilter.class.getSimpleName());
    String crossPlatformFiltersString =
        System.getenv().getOrDefault("CROSS_PLATFORM_FILTERS", defaultCrossPlatformFilters);

    String defaultDebugFilters =
        String.join(
            ",",
            IpAddressRequestFilter.class.getSimpleName(),
            ProxyDebugFilter.class.getSimpleName());
    String debugFiltersString = System.getenv().getOrDefault("DEBUG_FILTERS", defaultDebugFilters);

    return fromFilterNames(
        Arrays.asList(awsFiltersString.split(",")),
        Arrays.asList(gcpFiltersString.split(",")),
        Arrays.asList(crossPlatformFiltersString.split(",")),
        Arrays.asList(debugFiltersString.split(",")));
  }

  private static List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> buildFilters(
      List<String> filterNames) {
    List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> filters = new ArrayList<>();

    for (String filterName : filterNames) {
      String trimmedName = filterName.trim();
      if (trimmedName.isEmpty()) {
        continue;
      }
      Supplier<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> filterSupplier =
          filterRegistry.get(trimmedName);
      if (filterSupplier != null) {
        filters.add(filterSupplier.get());
      } else {
        logger.warning("Unknown filter: " + trimmedName);
      }
    }

    return filters;
  }
}
