package com.glean.proxy.filters.helpers;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class HttpClientFactory {
  private static CloseableHttpClient client;

  public static synchronized CloseableHttpClient getHttpClient() {
    if (client == null) {
      PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
      cm.setMaxTotal(500); // Set max total connections
      cm.setDefaultMaxPerRoute(200); // Set max connections per route

      // Set timeouts
      int connectionTimeoutMs = 20000; // milliseconds
      int socketTimeoutMs = 5000; // milliseconds
      int connectionRequestTimeoutMs = 5000; // milliseconds

      RequestConfig requestConfig =
          RequestConfig.custom()
              .setConnectTimeout(connectionTimeoutMs)
              .setSocketTimeout(socketTimeoutMs)
              .setConnectionRequestTimeout(connectionRequestTimeoutMs)
              .build();

      // Apply the configuration to the HttpClient
      client =
          HttpClients.custom()
              .setDefaultRequestConfig(requestConfig)
              .setConnectionManager(cm)
              .build();
    }
    return client;
  }
}
