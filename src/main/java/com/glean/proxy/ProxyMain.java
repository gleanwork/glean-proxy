package com.glean.proxy;

import java.util.logging.Logger;

public class ProxyMain {
  private static final Logger logger = Logger.getLogger(ProxyMain.class.getName());

  public static void main(String[] args) {
    final int port = Integer.parseInt(args[0]);
    logger.info("Starting proxy at " + port);
    ProxyNetworking.builder().build().run(port);
  }
}
