package com.glean.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.glean.proxy.filters.AwsDisallowInternalAddressForTransitVpc;
import com.glean.proxy.filters.ProxyDebugFilter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.littleshoot.proxy.HttpFilters;

public class FilterConfigurationTest {

  private HttpRequest mockRequest;
  private ChannelHandlerContext mockContext;
  private Supplier<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> testFilterSupplier;

  @Before
  public void setUp() {
    mockRequest = mock(HttpRequest.class);
    mockContext = mock(ChannelHandlerContext.class);
    testFilterSupplier = () -> (request, ctx) -> mock(HttpFilters.class);
  }

  @Test
  public void testConstructor_initializesAllFilterLists() {
    List<BiFunction<HttpRequest, ChannelHandlerContext, HttpFilters>> filters =
        Collections.singletonList(testFilterSupplier.get());

    FilterConfiguration config = new FilterConfiguration(filters, filters, filters, filters);

    assertThat(config.awsFilters()).hasSize(1);
    assertThat(config.gcpFilters()).hasSize(1);
    assertThat(config.crossPlatformFilters()).hasSize(1);
    assertThat(config.debugFilters()).hasSize(1);
  }

  @Test
  public void testRegisterCustomFilter_canBeUsedInConfiguration() {
    String customFilterName = "TestCustomFilter";

    FilterConfiguration.registerCustomFilter(customFilterName, testFilterSupplier);

    FilterConfiguration config =
        FilterConfiguration.fromFilterNames(
            Collections.singletonList(customFilterName),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());

    assertThat(config.awsFilters()).hasSize(1);
    assertThat(config.awsFilters().get(0).apply(mockRequest, mockContext)).isNotNull();
  }

  @Test
  public void testFromFilterNames_ignoresUnknownFilters() {
    FilterConfiguration config =
        FilterConfiguration.fromFilterNames(
            Arrays.asList(
                "UnknownFilter", AwsDisallowInternalAddressForTransitVpc.class.getSimpleName()),
            Collections.singletonList("another_unknown_filter"),
            Collections.emptyList(),
            Collections.emptyList());

    assertThat(config.awsFilters()).hasSize(1);
    assertThat(config.gcpFilters()).isEmpty();
  }

  @Test
  public void testFromFilterNames_ignoresEmptyValues() {
    FilterConfiguration config =
        FilterConfiguration.fromFilterNames(
            Collections.emptyList(),
            Collections.emptyList(),
            Arrays.asList("", "  ", ProxyDebugFilter.class.getSimpleName(), ""),
            Collections.emptyList());

    assertThat(config.crossPlatformFilters()).hasSize(1);
  }

  @Test
  public void testRegisterCustomFilter_allowsMultipleRegistrations() {
    String filter1Name = "CustomFilter1";
    String filter2Name = "CustomFilter2";

    FilterConfiguration.registerCustomFilter(filter1Name, testFilterSupplier);
    FilterConfiguration.registerCustomFilter(filter2Name, testFilterSupplier);

    FilterConfiguration config =
        FilterConfiguration.fromFilterNames(
            Arrays.asList(filter1Name, filter2Name),
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList());

    assertThat(config.awsFilters()).hasSize(2);
  }
}
