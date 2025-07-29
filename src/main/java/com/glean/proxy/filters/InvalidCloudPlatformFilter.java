package com.glean.proxy.filters;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import org.littleshoot.proxy.HttpFiltersAdapter;

/**
 * Returns an internal server error with a custom message when the cloud platform is unsupported.
 */
public class InvalidCloudPlatformFilter extends HttpFiltersAdapter {

  private final String message;

  public InvalidCloudPlatformFilter(HttpRequest originalRequest, String message) {
    super(originalRequest);
    this.message = message;
  }

  @Override
  public HttpResponse clientToProxyRequest(HttpObject httpObject) {
    ByteBuf buffer = Unpooled.wrappedBuffer(message.getBytes(StandardCharsets.UTF_8));
    return new DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, buffer);
  }
}
