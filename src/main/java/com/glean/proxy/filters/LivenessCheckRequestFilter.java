package com.glean.proxy.filters;

import com.glean.proxy.filters.helpers.Utils;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.littleshoot.proxy.HttpFiltersAdapter;

/** Returns HTTP 200 OK for all requests. */
public class LivenessCheckRequestFilter extends HttpFiltersAdapter {
  public LivenessCheckRequestFilter(HttpRequest originalRequest) {
    super(originalRequest);
  }

  @Override
  public HttpResponse clientToProxyRequest(HttpObject httpObject) {
    return Utils.createHTTPResponse("OK", HttpResponseStatus.OK);
  }
}
