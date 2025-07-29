package com.glean.proxy.filters;

import com.glean.proxy.filters.helpers.ShellCommandRunner;
import com.glean.proxy.schemas.CheckConnectivityResponse;
import com.glean.proxy.schemas.CheckConnectivityResponse.Status;
import com.glean.proxy.schemas.GetRouteResponse;
import com.glean.proxy.schemas.ProcessResponse;
import com.glean.proxy.schemas.ResolveHostResponse;
import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.littleshoot.proxy.HttpFiltersAdapter;

/**
 * Runs debug commands on the proxy server. All requests sent to /proxy_debug will be handled by
 * this filter.
 */
public class ProxyDebugFilter extends HttpFiltersAdapter {
  private static final Gson gson = new Gson();
  private static final Logger logger = Logger.getLogger(IpAddressRequestFilter.class.getName());

  private static Pattern nicPattern = Pattern.compile("dev\\s+(\\S+)");
  private static Pattern nicAddressPattern = Pattern.compile("src\\s+(\\S+)");

  public ProxyDebugFilter(HttpRequest originalRequest) {
    super(originalRequest);
  }

  private HttpResponse createResponse(HttpResponseStatus status, String content) {
    ByteBuf buffer = Unpooled.wrappedBuffer(content.getBytes(StandardCharsets.UTF_8));
    HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
    HttpHeaders.setContentLength(response, buffer.readableBytes());
    HttpHeaders.setHeader(response, HttpHeaders.Names.CONTENT_TYPE, "application/json");
    return response;
  }

  private HttpResponse resolveHost() {

    ResolveHostResponse response = new ResolveHostResponse();
    Map<String, List<String>> params = new QueryStringDecoder(originalRequest.uri()).parameters();
    if (!params.containsKey("hostname") || params.get("hostname").isEmpty()) {
      return createResponse(
          HttpResponseStatus.BAD_REQUEST, "Host name not provided. Use hostname query parameter");
    }
    String hostname = params.get("hostname").get(0);
    response.hostName = hostname;
    String ipAddress;
    try {
      InetAddress inetAddress = InetAddress.getByName(hostname);
      ipAddress = inetAddress.getHostAddress();
    } catch (UnknownHostException e) {
      response.resolvedBy = ResolveHostResponse.ResolvedBy.NONE;
      return createResponse(HttpResponseStatus.OK, gson.toJson(response));
    }
    response.ipAddress = ipAddress;
    response.resolvedBy = ResolveHostResponse.ResolvedBy.NONE;
    try {
      ProcessResponse processResponse =
          ShellCommandRunner.runCommand(Arrays.asList("nslookup", hostname));
      logger.info(
          String.format(
              "nslookup command for hostname: %s, exit code: %d, output: %s, error: %s",
              hostname,
              processResponse.exitCode,
              processResponse.outputString,
              processResponse.errorString));
      response.nsLookupErrorString = processResponse.errorString;
      if (processResponse.exitCode == 0) {
        response.resolvedBy = ResolveHostResponse.ResolvedBy.NAMESERVER;
        Pattern pattern = Pattern.compile("Server:\\s+([\\d.]+)");
        Matcher matcher = pattern.matcher(processResponse.outputString);
        if (matcher.find()) {
          response.nameserverUsed = matcher.group(1);
        }
      } else {
        response.resolvedBy = ResolveHostResponse.ResolvedBy.IP_HOST_MAPPING;
      }
      return createResponse(HttpResponseStatus.OK, gson.toJson(response));
    } catch (InterruptedException | IOException e) {
      return createResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  private HttpResponse getRoute() {
    GetRouteResponse response = new GetRouteResponse();
    Map<String, List<String>> params = new QueryStringDecoder(originalRequest.uri()).parameters();
    if (!params.containsKey("ip") || params.get("ip").isEmpty()) {
      return createResponse(
          HttpResponseStatus.BAD_REQUEST, "IP address not provided. Use ip query parameter");
    }
    String ip = params.get("ip").get(0);
    ProcessResponse processResponse;
    try {
      processResponse = ShellCommandRunner.runCommand(Arrays.asList("ip", "route", "get", ip));
    } catch (InterruptedException | IOException e) {
      logger.warning(
          String.format(
              "Error running ip route get command for ip: %s, error: %s", ip, e.getMessage()));
      return createResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    if (processResponse.exitCode != 0) {
      logger.warning(
          String.format(
              "Route not found for ip: %s. Process exited with exit code %d, error string %s",
              ip, processResponse.exitCode, processResponse.errorString));
      response.routeFound = false;
      response.failureReason = GetRouteResponse.FailureReason.SHELL_COMMAND_FAILED;
      response.errorString =
          String.format(
              "Shell command failed with exit code: %d and error %s",
              processResponse.exitCode, processResponse.errorString);
      return createResponse(HttpResponseStatus.OK, gson.toJson(response));
    }
    logger.info(
        String.format("Route found for ip: %s. Output: %s", ip, processResponse.outputString));

    Matcher nicMatcher = nicPattern.matcher(processResponse.outputString);
    Matcher nicAddressMatcher = nicAddressPattern.matcher(processResponse.outputString);

    if (nicMatcher.find() && nicAddressMatcher.find()) {
      response.nic = nicMatcher.group(1);
      if (response.nic.equals("eth0")) {
        response.routedToward = GetRouteResponse.RequestDirection.OUTBOUND;
      } else if (response.nic.equals("eth1")) {
        response.routedToward = GetRouteResponse.RequestDirection.INBOUND;
      } else {
        response.routedToward = GetRouteResponse.RequestDirection.UNKNOWN;
      }
      response.nicAddress = nicAddressMatcher.group(1);
      response.routeFound = true;
      return createResponse(HttpResponseStatus.OK, gson.toJson(response));
    }
    response.routeFound = false;
    response.failureReason = GetRouteResponse.FailureReason.UNABLE_TO_PARSE_NIC_INFO;
    response.errorString =
        String.format("Could not parse NIC info from output: %s", processResponse.outputString);
    return createResponse(HttpResponseStatus.OK, gson.toJson(response));
  }

  private HttpResponse checkConnectivity() {
    Map<String, List<String>> params = new QueryStringDecoder(originalRequest.uri()).parameters();
    if (!params.containsKey("hostname") || params.get("hostname").isEmpty()) {
      return createResponse(
          HttpResponseStatus.BAD_REQUEST, "Host name not provided. Use hostname query parameter");
    }
    final String hostname = params.get("hostname").get(0);

    if (!params.containsKey("port") || params.get("port").isEmpty()) {
      return createResponse(
          HttpResponseStatus.BAD_REQUEST, "Port not provided. Use port query parameter");
    }
    final int port;
    try {
      port = Integer.parseInt(params.get("port").get(0));
    } catch (NumberFormatException e) {
      return createResponse(HttpResponseStatus.BAD_REQUEST, "port must be an integer.");
    }
    if (port < 1) {
      return createResponse(HttpResponseStatus.BAD_REQUEST, "port must be positive.");
    }

    if (!params.containsKey("timeoutSecs") || params.get("timeoutSecs").isEmpty()) {
      return createResponse(
          HttpResponseStatus.BAD_REQUEST, "Timeout not provided. Use timeoutSecs query parameter");
    }
    final int timeoutSecs;
    try {
      timeoutSecs = Integer.parseInt(params.get("timeoutSecs").get(0));
    } catch (NumberFormatException e) {
      return createResponse(HttpResponseStatus.BAD_REQUEST, "timeoutSecs must be an integer.");
    }
    if (timeoutSecs < 1) {
      return createResponse(HttpResponseStatus.BAD_REQUEST, "timeoutSecs must be positive.");
    }
    final Duration timeout = Duration.ofSeconds(timeoutSecs);

    final CheckConnectivityResponse response = new CheckConnectivityResponse();
    response.hostname = hostname;
    response.port = port;
    response.timeoutSecs = timeout.getSeconds();

    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(hostname, port), (int) timeout.toMillis());
      response.status = Status.OK;
    } catch (SocketTimeoutException e) {
      response.status = Status.TIMEOUT;
      response.message =
          "TIMEOUT usually means the IP address is incorrect, the VPN is down, or the customer's"
              + " firewall is blocking traffic.";
    } catch (Exception e) {
      response.status = Status.ERROR;
      response.message = e.getMessage();
    }

    return createResponse(HttpResponseStatus.OK, gson.toJson(response));
  }

  @Override
  public HttpResponse clientToProxyRequest(HttpObject httpObject) {
    if (originalRequest.uri().startsWith("/proxy_debug/resolve_host")) {
      return resolveHost();
    }
    if (originalRequest.uri().startsWith("/proxy_debug/ip/route")) {
      return getRoute();
    }
    if (originalRequest.uri().startsWith("/proxy_debug/connectivity")) {
      return checkConnectivity();
    }
    return createResponse(HttpResponseStatus.BAD_REQUEST, "Invalid URI");
  }
}
