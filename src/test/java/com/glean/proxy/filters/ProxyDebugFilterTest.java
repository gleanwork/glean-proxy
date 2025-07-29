package com.glean.proxy.filters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.glean.proxy.filters.helpers.ShellCommandRunner;
import com.glean.proxy.schemas.GetRouteResponse;
import com.glean.proxy.schemas.ProcessResponse;
import com.glean.proxy.schemas.ResolveHostResponse;
import com.google.gson.Gson;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class ProxyDebugFilterTest {

  private static MockedStatic<InetAddress> mockInetAddress;
  private static HttpRequest httpRequest;
  private static ProxyDebugFilter proxyDebugFilter;
  private static final Gson gson = new Gson();
  private static MockedStatic<ShellCommandRunner> mockShellCommandRunner;

  @Before
  public void setUp() {
    mockShellCommandRunner = mockStatic(ShellCommandRunner.class);
    mockInetAddress = mockStatic(InetAddress.class);
    httpRequest = mock(HttpRequest.class);
    proxyDebugFilter = new ProxyDebugFilter(httpRequest);
  }

  @Test
  public void testResolveHost() throws Exception {
    InetAddress inetAddress = mock(InetAddress.class);
    when(inetAddress.getHostAddress()).thenReturn("8.8.8.8");
    when(InetAddress.getByName("google.com")).thenReturn(inetAddress);
    when(httpRequest.uri()).thenReturn("/proxy_debug/resolve_host?hostname=google.com");
    ProcessResponse processResponse = new ProcessResponse();
    processResponse.exitCode = 0;
    processResponse.outputString =
        "Server:\t\t1.2.3.4\n"
            + "Address:\tfe80::d42f:caff:fe8e:3964%15#53\n"
            + "\n"
            + "Non-authoritative answer:\n"
            + "Name:\tgoogle.com\n"
            + "Address: 142.250.71.14";
    processResponse.errorString = "";
    mockShellCommandRunner
        .when(() -> ShellCommandRunner.runCommand(Arrays.asList("nslookup", "google.com")))
        .thenReturn(processResponse);

    DefaultFullHttpResponse response =
        (DefaultFullHttpResponse) proxyDebugFilter.clientToProxyRequest(httpRequest);
    String responseString = response.content().toString(StandardCharsets.UTF_8);
    ResolveHostResponse resolveHostResponse =
        gson.fromJson(responseString, ResolveHostResponse.class);
    assertTrue(response.getStatus().equals(HttpResponseStatus.OK));
    assertTrue(resolveHostResponse.resolvedBy.equals(ResolveHostResponse.ResolvedBy.NAMESERVER));
    assertTrue(resolveHostResponse.ipAddress.equals("8.8.8.8"));
    assertTrue(resolveHostResponse.hostName.equals("google.com"));
    assertTrue(resolveHostResponse.nsLookupErrorString.equals(processResponse.errorString));
    assertTrue(resolveHostResponse.nameserverUsed.equals("1.2.3.4"));
  }

  @Test
  public void testResolveHost_HostNameNotFound() throws Exception {
    when(httpRequest.uri()).thenReturn("/proxy_debug/resolve_host");
    DefaultFullHttpResponse response =
        (DefaultFullHttpResponse) proxyDebugFilter.clientToProxyRequest(httpRequest);
    String responseString = response.content().toString(StandardCharsets.UTF_8);
    assertTrue(response.getStatus().equals(HttpResponseStatus.BAD_REQUEST));
    assertTrue(responseString.equals("Host name not provided. Use hostname query parameter"));
  }

  @Test
  public void testResolveHost_HostNameNotFoundInetAddress() throws Exception {
    when(httpRequest.uri()).thenReturn("/proxy_debug/resolve_host?hostname=google.com");
    when(InetAddress.getByName("google.com")).thenThrow(new UnknownHostException());
    DefaultFullHttpResponse response =
        (DefaultFullHttpResponse) proxyDebugFilter.clientToProxyRequest(httpRequest);
    String responseString = response.content().toString(StandardCharsets.UTF_8);
    ResolveHostResponse resolveHostResponse =
        gson.fromJson(responseString, ResolveHostResponse.class);
    assertTrue(response.getStatus().equals(HttpResponseStatus.OK));
    assertTrue(resolveHostResponse.resolvedBy.equals(ResolveHostResponse.ResolvedBy.NONE));
  }

  @Test
  public void testResolveHost_nslookupExitCode1() throws Exception {
    InetAddress inetAddress = mock(InetAddress.class);
    when(inetAddress.getHostAddress()).thenReturn("8.8.8.8");
    when(InetAddress.getByName("google.com")).thenReturn(inetAddress);
    when(httpRequest.uri()).thenReturn("/proxy_debug/resolve_host?hostname=google.com");
    ProcessResponse processResponse = new ProcessResponse();
    processResponse.exitCode = 1;
    processResponse.outputString =
        "Server:\t\t2405:201:d017:1b::c0a8:1d01\n"
            + "Address:\t2405:201:d017:1b::c0a8:1d01#53\n"
            + "\n"
            + "** server can't find google.comr: NXDOMAIN";
    processResponse.errorString = "";
    mockShellCommandRunner
        .when(() -> ShellCommandRunner.runCommand(Arrays.asList("nslookup", "google.com")))
        .thenReturn(processResponse);
    DefaultFullHttpResponse response =
        (DefaultFullHttpResponse) proxyDebugFilter.clientToProxyRequest(httpRequest);
    String responseString = response.content().toString(StandardCharsets.UTF_8);
    ResolveHostResponse resolveHostResponse =
        gson.fromJson(responseString, ResolveHostResponse.class);
    assertTrue(response.getStatus().equals(HttpResponseStatus.OK));
    assertTrue(resolveHostResponse.nsLookupErrorString.equals(processResponse.errorString));
    assertTrue(
        resolveHostResponse.resolvedBy.equals(ResolveHostResponse.ResolvedBy.IP_HOST_MAPPING));
  }

  @Test
  public void testResolveHost_nslookupThrowsException() throws Exception {
    InetAddress inetAddress = mock(InetAddress.class);
    when(inetAddress.getHostAddress()).thenReturn("8.8.8.8");
    when(InetAddress.getByName("google.com")).thenReturn(inetAddress);
    when(httpRequest.uri()).thenReturn("/proxy_debug/resolve_host?hostname=google.com");
    mockShellCommandRunner
        .when(() -> ShellCommandRunner.runCommand(Arrays.asList("nslookup", "google.com")))
        .thenThrow(new InterruptedException("Cannot resolve"));
    DefaultFullHttpResponse response =
        (DefaultFullHttpResponse) proxyDebugFilter.clientToProxyRequest(httpRequest);
    String responseString = response.content().toString(StandardCharsets.UTF_8);
    assertTrue(response.getStatus().equals(HttpResponseStatus.INTERNAL_SERVER_ERROR));
    assertTrue(responseString.equals("Cannot resolve"));
  }

  @Test
  public void testGetRoute() throws Exception {
    when(httpRequest.uri()).thenReturn("/proxy_debug/ip/route?ip=8.8.8.8");
    ProcessResponse processResponse = new ProcessResponse();
    processResponse.exitCode = 0;
    processResponse.outputString =
        "192.168.1.100 via 192.168.1.1 dev eth0 src 192.168.1.2 uid 1000";
    processResponse.errorString = "";
    mockShellCommandRunner
        .when(() -> ShellCommandRunner.runCommand(Arrays.asList("ip", "route", "get", "8.8.8.8")))
        .thenReturn(processResponse);
    DefaultFullHttpResponse response =
        (DefaultFullHttpResponse) proxyDebugFilter.clientToProxyRequest(httpRequest);
    String responseString = response.content().toString(StandardCharsets.UTF_8);
    GetRouteResponse getRouteResponse = gson.fromJson(responseString, GetRouteResponse.class);
    assertTrue(response.getStatus().equals(HttpResponseStatus.OK));
    assertTrue(getRouteResponse.nic.equals("eth0"));
    assertTrue(getRouteResponse.nicAddress.equals("192.168.1.2"));
  }

  @Test
  public void testGetRoute_IPAddressNotProvided() {
    when(httpRequest.uri()).thenReturn("/proxy_debug/ip/route");
    DefaultFullHttpResponse response =
        (DefaultFullHttpResponse) proxyDebugFilter.clientToProxyRequest(httpRequest);
    String responseString = response.content().toString(StandardCharsets.UTF_8);
    assertTrue(responseString.equals("IP address not provided. Use ip query parameter"));
    assertTrue(response.getStatus().equals(HttpResponseStatus.BAD_REQUEST));
  }

  @Test
  public void testGetRoute_exitCode1() throws Exception {
    when(httpRequest.uri()).thenReturn("/proxy_debug/ip/route?ip=8.8.8.8");
    ProcessResponse processResponse = new ProcessResponse();
    processResponse.exitCode = 1;
    processResponse.outputString = "";
    processResponse.errorString = "no route found";
    mockShellCommandRunner
        .when(() -> ShellCommandRunner.runCommand(Arrays.asList("ip", "route", "get", "8.8.8.8")))
        .thenReturn(processResponse);
    DefaultFullHttpResponse response =
        (DefaultFullHttpResponse) proxyDebugFilter.clientToProxyRequest(httpRequest);
    String responseString = response.content().toString(StandardCharsets.UTF_8);
    GetRouteResponse getRouteResponse = gson.fromJson(responseString, GetRouteResponse.class);
    assertTrue(response.getStatus().equals(HttpResponseStatus.OK));
    assertFalse(getRouteResponse.routeFound);
    assertTrue(
        getRouteResponse.failureReason.equals(GetRouteResponse.FailureReason.SHELL_COMMAND_FAILED));
  }

  @After
  public void tearDown() {
    mockInetAddress.close();
    mockShellCommandRunner.close();
  }
}
