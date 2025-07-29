package com.glean.proxy.filters.helpers;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CachedNicResolver implements NicResolver {
  private final NicResolver delegate;

  private static final int MAX_CACHE_SIZE = 1000;

  private static final ConcurrentHashMap<String, String> routeCache =
      new ConcurrentHashMap<>(
          new LinkedHashMap<String, String>(
              MAX_CACHE_SIZE, 0.75f, true /* maintain Access Order */) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
              return size() > MAX_CACHE_SIZE;
            }
          });

  public CachedNicResolver(NicResolver delegate) {
    this.delegate = delegate;
  }

  public String resolve(String hostIp) throws InterruptedException, IOException {
    if (routeCache.containsKey(hostIp)) {
      return routeCache.get(hostIp);
    }
    String nic = delegate.resolve(hostIp);
    routeCache.put(hostIp, nic);
    return nic;
  }
}
