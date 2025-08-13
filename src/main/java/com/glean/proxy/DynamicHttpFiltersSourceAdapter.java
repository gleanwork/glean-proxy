package com.glean.proxy;

import com.glean.proxy.filters.CompositeFilter;
import com.glean.proxy.filters.InvalidCloudPlatformFilter;
import com.glean.proxy.filters.LivenessCheckRequestFilter;
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

  // Adding a 5 MB buffer to ensure the HttpRequest is aggregated. Without this, the request may be
  // received in chunks. This is necessary because we handle proxy-to-server connections ourselves
  // for requests coming from the customer. By enabling aggregation, we avoid having to manually
  // handle chunked requests. Note that the requests with size greater than 5 MB will automatically
  // be rejected at an upper layer.
  @Override
  public int getMaximumRequestBufferSizeInBytes() {
    return 5 * 1024 * 1024; // 5 mb
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
