package com.glean.proxy.filters;

import static com.glean.proxy.test_utils.TestUtils.verifyHttpResponse;

import com.glean.proxy.filters.helpers.AllowedEgressDomains;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Assert;
import org.junit.Test;

public class AwsFilterEgressTrafficByDomainTest {

  @Test
  public void test_AllowRequest() {
    AllowedEgressDomains allowedDomains =
        new AllowedEgressDomains(
            "glean.com,\n" + "*.temu.com ,api.*.example.com, *.bruh.* , *.*.a.io");

    HttpRequest[] requestsThatShouldBeAllowedWhenEnforcing = {
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, " glean.com:443 "),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://subdomain.temu.com"),
      new DefaultHttpRequest(
          HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "subdomain.subdomain2.temu.com:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "api.v1.example.com:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "api.a2.example.com:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "app.bruh.io:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "just.right.a.io:443"),
    };

    HttpRequest[] requestsThatShouldBeAllowedWhenNotEnforcing = {
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "https://glean.com/hi "),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, " microsoft.com:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://app.microsoft.com/some")
    };

    for (HttpRequest request : requestsThatShouldBeAllowedWhenEnforcing) {
      AwsFilterEgressTrafficByDomain filter =
          new AwsFilterEgressTrafficByDomain(request, true, allowedDomains);
      Assert.assertNull(filter.clientToProxyRequest(request));
    }

    for (HttpRequest request : requestsThatShouldBeAllowedWhenNotEnforcing) {
      AwsFilterEgressTrafficByDomain filter =
          new AwsFilterEgressTrafficByDomain(request, false, allowedDomains);
      Assert.assertNull(filter.clientToProxyRequest(request));
    }
  }

  @Test
  public void test_BlockRequest() {
    AllowedEgressDomains allowedDomains =
        new AllowedEgressDomains(
            " glean.com  ,*.temu.com  \n"
                + ", *.app.glean.com,api.*.example.com,*.bruh.* , *.*.a.io");

    HttpRequest[] requestsThatShouldBeBlocked = {
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "https://microsoft.com/hi"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "temu.com:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "app.glean.com:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "subdomain.glean.com:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "bruh.com:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "com.bruh:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "web.v1.example.com:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "api.bruh-2.com:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "a.io:443"),
      new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "z.a.io:443"),
      new DefaultHttpRequest(
          HttpVersion.HTTP_1_1, HttpMethod.CONNECT, "too.many.subdomains.a.io:443"),
    };

    for (HttpRequest request : requestsThatShouldBeBlocked) {
      AwsFilterEgressTrafficByDomain filter =
          new AwsFilterEgressTrafficByDomain(request, true, allowedDomains);
      verifyHttpResponse(filter.clientToProxyRequest(request), "Forbidden", 403);
    }
  }
}
