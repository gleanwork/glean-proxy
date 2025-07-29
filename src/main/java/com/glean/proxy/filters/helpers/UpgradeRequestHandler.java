package com.glean.proxy.filters.helpers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

public class UpgradeRequestHandler {

  private static final Logger LOGGER = Logger.getLogger(UpgradeRequestHandler.class.getName());
  public static final List<String> HEADERS_TO_COPY =
      Arrays.asList(HttpHeaders.AUTHORIZATION, HttpHeaders.USER_AGENT, HttpHeaders.ACCEPT);

  /**
   * Forwards a Netty HTTP request to an upgraded URI using Apache HttpClient, copying select
   * headers and handling the response. If an error occurs during forwarding, it returns a 500.
   *
   * @param nettyRequest the incoming Netty HttpRequest to be forwarded
   * @param upgradedUri the HTTPS target URI to which the request should be sent
   * @param proxyHeader the value to use for the User-Agent header if not present
   * @return the Netty HttpResponse received from the upgraded URI, or a 500 error response on
   *     failure
   */
  public static HttpResponse sendUpgradedRequest(
      HttpRequest nettyRequest, String upgradedUri, String proxyHeader) {
    HttpRequestBase apacheRequest = createApacheRequest(nettyRequest, upgradedUri, proxyHeader);
    CloseableHttpClient httpClient = HttpClientFactory.getHttpClient();
    try {
      org.apache.http.HttpResponse response = httpClient.execute(apacheRequest);
      return copyApacheResponseToNetty(response);
    } catch (Exception e) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      String stackTrace = sw.toString(); // stack trace as a string
      LOGGER.severe("Failed to get response: " + stackTrace);
      DefaultFullHttpResponse response =
          new DefaultFullHttpResponse(
              HttpVersion.HTTP_1_1,
              HttpResponseStatus.INTERNAL_SERVER_ERROR,
              Unpooled.copiedBuffer("Server error during request forwarding", CharsetUtil.UTF_8));
      response.headers().set("Content-Type", "text/plain; charset=UTF-8");
      return response;
    }
  }

  private static HttpResponse copyApacheResponseToNetty(org.apache.http.HttpResponse apacheResponse)
      throws IOException {
    int statusCode = apacheResponse.getStatusLine().getStatusCode();
    HttpVersion version;
    try {
      version = HttpVersion.valueOf(apacheResponse.getProtocolVersion().toString());
    } catch (IllegalArgumentException e) {
      version = HttpVersion.HTTP_1_1;
    }

    DefaultFullHttpResponse nettyResponse =
        new DefaultFullHttpResponse(
            version,
            new HttpResponseStatus(statusCode, apacheResponse.getStatusLine().getReasonPhrase()));

    for (Header header : apacheResponse.getAllHeaders()) {
      nettyResponse.headers().add(header.getName(), header.getValue());
    }

    HttpEntity entity = apacheResponse.getEntity();
    if (entity != null) {
      byte[] contentBytes = EntityUtils.toByteArray(entity);
      ByteBuf contentBuf = Unpooled.wrappedBuffer(contentBytes);
      try {
        nettyResponse.content().writeBytes(contentBuf);
      } finally {
        contentBuf.release();
      }
    }
    return nettyResponse;
  }

  /**
   * Method to create an Apache HttpRequestBase object based on the HttpMethod. Only supports GET,
   * POST, and PUT.
   */
  private static HttpRequestBase createApacheRequest(
      HttpRequest nettyRequest, String uri, String proxyHeader) {
    HttpMethod method = nettyRequest.method();
    LOGGER.info("Creating Apache request for URI: " + uri + " with method: " + method);
    HttpRequestBase apacheRequest =
        switch (method.name()) {
          case "GET" -> new HttpGet(uri);
          case "POST", "PUT" -> getHttpEntityEnclosingRequestBase(nettyRequest, method, uri);
          default -> null;
        };
    if (apacheRequest == null) {
      LOGGER.severe("Unsupported HTTP method: " + method);
      return null;
    }
    HEADERS_TO_COPY.forEach(
        headerKey -> {
          if (nettyRequest.headers().contains(headerKey)) {
            apacheRequest.addHeader(headerKey, nettyRequest.headers().get(headerKey));
          }
        });

    // Write default header if user-agent header doesn't exist for logging purpose.
    Header userAgent = apacheRequest.getFirstHeader(HttpHeaders.USER_AGENT);
    if (userAgent == null) {
      apacheRequest.addHeader(HttpHeaders.USER_AGENT, proxyHeader);
    }
    return apacheRequest;
  }

  /**
   * Creates an HttpEntityEnclosingRequestBase for POST and PUT methods using the provided Netty
   * request and URI. Other methods are not handled.
   */
  private static HttpEntityEnclosingRequestBase getHttpEntityEnclosingRequestBase(
      HttpRequest nettyRequest, HttpMethod method, String uri) {
    // Create the appropriate request type based on the method
    HttpEntityEnclosingRequestBase requestWithEntity =
        method.equals(HttpMethod.POST) ? new HttpPost(uri) : new HttpPut(uri);
    if (nettyRequest instanceof HttpContent) {
      HttpContent httpContent = (HttpContent) nettyRequest;
      ByteBuf contentBuf = httpContent.content();
      InputStream inputStream = new ByteBufInputStream(contentBuf);
      InputStreamEntity streamEntity =
          new InputStreamEntity(
              inputStream,
              contentBuf.readableBytes(),
              ContentType.create(
                  nettyRequest
                      .headers()
                      .get(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream")));
      requestWithEntity.setEntity(streamEntity);
    }
    return requestWithEntity;
  }
}
