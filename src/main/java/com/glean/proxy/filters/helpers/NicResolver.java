package com.glean.proxy.filters.helpers;

import java.io.IOException;

public interface NicResolver {
  /**
   * returns the NIC towards which requests to hostIp will be routed
   *
   * @param hostIp
   * @return nic
   * @throws InterruptedException
   * @throws IOException
   */
  String resolve(String hostIp) throws InterruptedException, IOException;
}
