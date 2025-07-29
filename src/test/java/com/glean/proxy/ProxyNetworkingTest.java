package com.glean.proxy;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import org.junit.Test;
import org.littleshoot.proxy.impl.ThreadPoolConfiguration;
import org.mockito.MockedStatic;

public class ProxyNetworkingTest {

  @Test
  public void testBuilder_isConfigurable() {
    ThreadPoolConfiguration threadPoolConfig = new ThreadPoolConfiguration().withAcceptorThreads(4);
    FilterConfiguration filterConfig = mock(FilterConfiguration.class);

    ProxyNetworking result =
        ProxyNetworking.builder()
            .withThreadPoolConfiguration(threadPoolConfig)
            .withFilterConfiguration(filterConfig)
            .build();

    assertNotNull(result);
  }

  @Test
  public void testBuilder_initializesDefaultConfigurations() {
    try (MockedStatic<FilterConfiguration> mocked = mockStatic(FilterConfiguration.class)) {
      mocked.when(FilterConfiguration::fromEnvironment).thenReturn(mock(FilterConfiguration.class));
      ProxyNetworking result = ProxyNetworking.builder().build();

      assertNotNull(result);
      assertNotNull(result.threadPoolConfiguration);
      assertNotNull(result.filterConfiguration);
    }
  }
}
