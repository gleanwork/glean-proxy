package com.glean.proxy.filters;

import static com.glean.proxy.test_utils.TestUtils.verifyHttpResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.netty.handler.codec.http.HttpRequest;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;

public class AwsDisallowInternalAddressForTransitVpcTest {
  HttpRequest request;

  @Before
  public void setUp() throws Exception {
    request = mock(HttpRequest.class);
  }

  @Test
  public void test_block_request() throws Exception {

    AwsDisallowInternalAddressForTransitVpc filter =
        new AwsDisallowInternalAddressForTransitVpc(request, "https://10.128.0.3");
    InetSocketAddress address = new InetSocketAddress("10.128.0.3", 8080);

    filter.proxyToServerResolutionSucceeded("10.64.32.2:8080", address);
    verifyHttpResponse(filter.proxyToServerRequest(request), "Forbidden", 403);
  }

  @Test
  public void test_Dont_block() throws Exception {
    AwsDisallowInternalAddressForTransitVpc filter =
        new AwsDisallowInternalAddressForTransitVpc(request, "https://10.128.0.3");
    InetSocketAddress address = new InetSocketAddress("10.64.0.3", 8080);

    filter.proxyToServerResolutionSucceeded("10.64.32.2:8080", address);
    assertThat(filter.proxyToServerRequest(request)).isNull();
  }
}
