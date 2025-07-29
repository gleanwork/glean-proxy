package com.glean.proxy.filters.helpers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;

public class Utils {

  public static HttpResponse createHTTPResponse(String message, HttpResponseStatus status) {
    ByteBuf buffer = Unpooled.wrappedBuffer(message.getBytes(StandardCharsets.UTF_8));
    HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
    HttpHeaders.setContentLength(response, buffer.readableBytes());
    HttpHeaders.setHeader(response, HttpHeaders.Names.CONTENT_TYPE, "text/plain");
    return response;
  }
}
