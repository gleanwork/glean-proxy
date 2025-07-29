package com.glean.proxy.filters.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mockStatic;

import com.glean.proxy.schemas.ProcessResponse;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class IpRouteNicResolverTest {
  MockedStatic<ShellCommandRunner> mockedShellCommandRunner;
  IpRouteNicResolver resolver;

  @Before
  public void setUp() throws Exception {
    mockedShellCommandRunner = mockStatic(ShellCommandRunner.class);
    resolver = new IpRouteNicResolver();
  }

  @Test
  public void testResolve() throws Exception {
    ProcessResponse processResponse = new ProcessResponse();
    processResponse.outputString =
        "10.64.32.2 via 10.128.0.1 dev eth1 src 10.128.0.3 uid 20162\n" + "    cache";
    mockedShellCommandRunner
        .when(
            () -> ShellCommandRunner.runCommand(Arrays.asList("ip", "route", "get", "10.64.32.2")))
        .thenReturn(processResponse);

    assertThat(resolver.resolve("10.64.32.2")).isEqualTo("eth1");
  }

  @Test
  public void testResolve_nullOutput() throws Exception {
    ProcessResponse processResponse = new ProcessResponse();
    processResponse.outputString = null;
    mockedShellCommandRunner
        .when(
            () -> ShellCommandRunner.runCommand(Arrays.asList("ip", "route", "get", "10.64.32.2")))
        .thenReturn(processResponse);
    assertThrows(RuntimeException.class, () -> resolver.resolve("10.64.32.2"));
  }

  @After
  public void tearDown() throws Exception {
    mockedShellCommandRunner.close();
  }
}
