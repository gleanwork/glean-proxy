package com.glean.proxy.filters.helpers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OnPremisesProxyTest {

  @Test
  public void testProxy() throws Exception {
    final OnPremisesProxy proxy = new OnPremisesProxy("http://webhook_target", false);
    assertEquals(
        "http://webhook_target/github/events", proxy.getTargetURL("/proxy/webhook/github/events"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testProxy_throws() throws Exception {
    final OnPremisesProxy proxy = new OnPremisesProxy("http://webhook_target", false);
    proxy.getTargetURL("/proxy/other/github/events");
    proxy.getTargetURL("/proxy/crawl/events");
  }

  @Test
  public void testProxy_allowAllIngress() throws Exception {
    final OnPremisesProxy proxy = new OnPremisesProxy("http://webhook_target", true);
    assertEquals(
        "http://webhook_target/api/v1/search?actas=123",
        proxy.getTargetURL("/proxy/api/v1/search?actas=123"));
    assertEquals(
        "http://webhook_target/rest/api/index", proxy.getTargetURL("/proxy/rest/api/index"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testProxy_allowAllIngress_throws() throws Exception {
    final OnPremisesProxy proxy = new OnPremisesProxy("http://webhook_target", true);
    proxy.getTargetURL("/proxy/other/github/events");
  }
}
