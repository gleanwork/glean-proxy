package com.glean.proxy;

import com.glean.proxy.filters.CompositeFilter;
import com.glean.proxy.filters.HttpNotFoundFilter;
import com.glean.proxy.filters.InvalidCloudPlatformFilter;
import com.glean.proxy.filters.LegacyRequestFilter;
import com.glean.proxy.filters.LivenessCheckRequestFilter;
import com.glean.proxy.filters.helpers.OnPremisesProxy;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;

public class DynamicHttpFiltersSourceAdapter extends HttpFiltersSourceAdapter {

  private static final Logger logger =
      Logger.getLogger(DynamicHttpFiltersSourceAdapter.class.getName());
  private final OnPremisesProxy legacyProxy = OnPremisesProxy.fromEnvironment();
  private final String cloudPlatform = System.getenv("CLOUD_PLATFORM");

  private final List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> awsFilters;
  private final List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> gcpFilters;
  private final List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>>
      crossPlatformFilters;
  private final List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> debugFilters;

  public DynamicHttpFiltersSourceAdapter(FilterConfiguration config) {
    awsFilters = new ArrayList<>(config.awsFilters());
    gcpFilters = new ArrayList<>(config.gcpFilters());
    crossPlatformFilters = new ArrayList<>(config.crossPlatformFilters());
    debugFilters = new ArrayList<>(config.debugFilters());
  }

  // Aggregate the request body so filters can operate on a FullHttpRequest. Requests larger than
  // this cap are rejected with HTTP 413 by Netty's HttpObjectAggregator before any filter runs.
  // Override the default 5 MB cap via MAX_REQUEST_BUFFER_BYTES — required for endpoints like
  // /api/index/v1/indexdocuments where customers send payloads well over 5 MB.
  private static final int DEFAULT_MAX_REQUEST_BUFFER_BYTES = 5 * 1024 * 1024; // 5 MB

  private static final int MAX_REQUEST_BUFFER_BYTES = resolveMaxRequestBufferBytes();

  private static int resolveMaxRequestBufferBytes() {
    String raw = System.getenv("MAX_REQUEST_BUFFER_BYTES");
    if (raw == null || raw.isBlank()) {
      return DEFAULT_MAX_REQUEST_BUFFER_BYTES;
    }
    try {
      int parsed = Integer.parseInt(raw.trim());
      if (parsed <= 0) {
        logger.warning(
            "MAX_REQUEST_BUFFER_BYTES must be positive; got "
                + parsed
                + ". Falling back to default "
                + DEFAULT_MAX_REQUEST_BUFFER_BYTES);
        return DEFAULT_MAX_REQUEST_BUFFER_BYTES;
      }
      return parsed;
    } catch (NumberFormatException e) {
      logger.warning(
          "MAX_REQUEST_BUFFER_BYTES is not a valid integer: '"
              + raw
              + "'. Falling back to default "
              + DEFAULT_MAX_REQUEST_BUFFER_BYTES);
      return DEFAULT_MAX_REQUEST_BUFFER_BYTES;
    }
  }

  @Override
  public int getMaximumRequestBufferSizeInBytes() {
    return MAX_REQUEST_BUFFER_BYTES;
  }

  @Override
  public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
    logger.info("Proxying URI: " + originalRequest.uri());

    if (originalRequest.uri().startsWith("/liveness_check")) {
      return new LivenessCheckRequestFilter(originalRequest);
    } else if (originalRequest.uri().startsWith("/proxy_debug")) {
      List<HttpFilters> filters =
          debugFilters.stream()
              .map(filterConstructor -> filterConstructor.apply(originalRequest, ctx))
              .collect(Collectors.toCollection(ArrayList::new));
      return new CompositeFilter(originalRequest, filters);
    } else if (originalRequest.uri().startsWith("/proxy")) {
      if (legacyProxy != null) {
        return new LegacyRequestFilter(originalRequest, legacyProxy);
      } else {
        logger.fine("Using HttpNotFoundFilter as legacy proxy is null");
        return new HttpNotFoundFilter(originalRequest);
      }
    }

    return switch (cloudPlatform) {
      case "AWS" -> buildPlatformCompositeFilter(originalRequest, ctx, awsFilters);
      case "GOOGLE" -> buildPlatformCompositeFilter(originalRequest, ctx, gcpFilters);
      default ->
          new InvalidCloudPlatformFilter(
              originalRequest, String.format("Invalid cloud platform: %s", cloudPlatform));
    };
  }

  private CompositeFilter buildPlatformCompositeFilter(
      HttpRequest originalRequest,
      ChannelHandlerContext ctx,
      List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> platformFilters) {
    List<HttpFilters> filters =
        Stream.concat(crossPlatformFilters.stream(), platformFilters.stream())
            .map(filterConstructor -> filterConstructor.apply(originalRequest, ctx))
            .collect(Collectors.toCollection(ArrayList::new));

    return new CompositeFilter(originalRequest, filters);
  }
}
