package com.glean.proxy.filters.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

public class CachedNicResolverTest {
  NicResolver mockedDelegate;
  CachedNicResolver resolver;

  @Before
  public void setUp() throws Exception {
    mockedDelegate = mock(NicResolver.class);
    resolver = new CachedNicResolver(mockedDelegate);
  }

  @Test
  public void testResolve_IPNotInCache() throws Exception {
    when(mockedDelegate.resolve("10.0.2.2")).thenReturn("eth1");
    String nic = resolver.resolve("10.0.2.2");
    assertThat(nic).isEqualTo("eth1");
  }

  @Test
  public void testResolve_IPInCache() throws Exception {
    when(mockedDelegate.resolve("10.0.2.3")).thenReturn("eth0");
    String nic = resolver.resolve("10.0.2.3");
    assertThat(nic).isEqualTo("eth0");
    String nic1 = resolver.resolve("10.0.2.3");
    assertThat(nic1).isEqualTo("eth0");
    // function should be called once due to caching
    verify(mockedDelegate, times(1)).resolve("10.0.2.3");
  }
}
