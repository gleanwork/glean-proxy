package com.glean.proxy.filters;

import static com.glean.proxy.test_utils.TestUtils.verifyHttpResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.glean.proxy.filters.helpers.NicResolver;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import java.net.InetSocketAddress;
import org.junit.Before;
import org.junit.Test;

public class GCPDisallowInternalAddressFilterTest {
  // Default Google kubernetes service CIDR
  // https://cloud.google.com/kubernetes-engine/docs/concepts/alias-ips
  final String testGkeServiceIpRange = "34.118.224.0/20";
  NicResolver nicResolver;
  HttpRequest request;

  @Before
  public void setUp() throws Exception {
    nicResolver = mock(NicResolver.class);
    request = mock(HttpRequest.class);
  }

  @Test
  public void test_StandAlone_PrivateIP() throws Exception {
    InetSocketAddress address = new InetSocketAddress("10.20.30.40", 8080);
    GCPDisallowInternalAddressFilter filter =
        new GCPDisallowInternalAddressFilter(
            request, "STANDALONE", nicResolver, testGkeServiceIpRange);
    filter.proxyToServerResolutionSucceeded("10.20.30.40:8080", address);

    HttpResponse response = filter.proxyToServerRequest(mock(HttpObject.class));
    verifyHttpResponse(response, "Ingress Route not allowed For Private IPs: 10.20.30.40", 403);
  }

  @Test
  public void test_StandAlone_PublicIP() throws Exception {
    InetSocketAddress address = new InetSocketAddress("1.2.3.4", 8080);
    GCPDisallowInternalAddressFilter filter =
        new GCPDisallowInternalAddressFilter(
            request, "STANDALONE", nicResolver, testGkeServiceIpRange);
    filter.proxyToServerResolutionSucceeded("1.2.3.4:8080", address);

    assertThat(filter.proxyToServerRequest(mock(HttpObject.class))).isNull();
  }

  @Test
  public void test_Transit_private_routedInbound() throws Exception {
    InetSocketAddress address = new InetSocketAddress("10.64.32.2", 8080);
    when(nicResolver.resolve("10.64.32.2")).thenReturn("eth1");
    GCPDisallowInternalAddressFilter filter =
        new GCPDisallowInternalAddressFilter(
            request, "TRANSIT", nicResolver, testGkeServiceIpRange);
    filter.proxyToServerResolutionSucceeded("10.64.32.2:8080", address);

    HttpResponse response = filter.proxyToServerRequest(mock(HttpObject.class));
    verifyHttpResponse(response, "Ingress Route not allowed For Private IPs: 10.64.32.2", 403);
  }

  @Test
  public void test_Transit_public_routedInbound() throws Exception {
    InetSocketAddress address = new InetSocketAddress("1.20.30.40", 8080);
    when(nicResolver.resolve("1.20.30.40")).thenReturn("eth1");
    GCPDisallowInternalAddressFilter filter =
        new GCPDisallowInternalAddressFilter(
            request, "TRANSIT", nicResolver, testGkeServiceIpRange);
    filter.proxyToServerResolutionSucceeded("1.20.30.40:8080", address);

    assertThat(filter.proxyToServerRequest(mock(HttpObject.class))).isNull();
  }

  @Test
  public void test_Transit_private_routedTowardsCustomer() throws Exception {
    InetSocketAddress address = new InetSocketAddress("10.64.32.2", 8080);
    when(nicResolver.resolve("10.64.32.2")).thenReturn("eth0");
    GCPDisallowInternalAddressFilter filter =
        new GCPDisallowInternalAddressFilter(
            request, "TRANSIT", nicResolver, testGkeServiceIpRange);
    filter.proxyToServerResolutionSucceeded("10.64.32.2:8080", address);

    assertThat(filter.proxyToServerRequest(mock(HttpObject.class))).isNull();
  }

  @Test
  public void test_Transit_public_routedTowardsCustomer() throws Exception {
    InetSocketAddress address = new InetSocketAddress("1.20.30.40", 8080);
    when(nicResolver.resolve("1.20.30.40")).thenReturn("eth0");
    GCPDisallowInternalAddressFilter filter =
        new GCPDisallowInternalAddressFilter(
            request, "TRANSIT", nicResolver, testGkeServiceIpRange);
    filter.proxyToServerResolutionSucceeded("1.20.30.40:8080", address);

    assertThat(filter.proxyToServerRequest(mock(HttpObject.class))).isNull();
  }

  @Test
  public void test_unknownProxyType() throws Exception {
    InetSocketAddress address = new InetSocketAddress("10.64.32.2", 8080);
    GCPDisallowInternalAddressFilter filter =
        new GCPDisallowInternalAddressFilter(request, "TGW", nicResolver, testGkeServiceIpRange);
    filter.proxyToServerResolutionSucceeded("10.64.32.2", address);
    HttpResponse response = filter.proxyToServerRequest(mock(HttpObject.class));
    verifyHttpResponse(response, "Unknown proxy type: TGW", 500);
  }
}
