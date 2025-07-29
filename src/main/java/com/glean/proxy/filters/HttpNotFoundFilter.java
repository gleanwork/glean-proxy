package com.glean.proxy.filters;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.littleshoot.proxy.HttpFiltersAdapter;

/** Returns HTTP 404 Not Found for all requests. */
public class HttpNotFoundFilter extends HttpFiltersAdapter {

  public HttpNotFoundFilter(HttpRequest originalRequest) {
    super(originalRequest);
  }

  @Override
  public HttpResponse clientToProxyRequest(HttpObject httpObject) {
    return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
  }
}
