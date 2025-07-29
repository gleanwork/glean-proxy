package com.glean.proxy.filters.helpers;

import com.glean.proxy.schemas.ProcessResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IpRouteNicResolver implements NicResolver {
  private static final Pattern NIC_PATTERN = Pattern.compile("\\bdev\\s+(\\S+)");

  public String resolve(String hostIP) throws InterruptedException, IOException {
    ProcessResponse response =
        ShellCommandRunner.runCommand(Arrays.asList("ip", "route", "get", hostIP));
    return parseNICFromRouteOutput(response.outputString);
  }

  private String parseNICFromRouteOutput(String routeOutput) {
    Matcher matcher = NIC_PATTERN.matcher(routeOutput);
    if (matcher.find()) {
      return matcher.group(1); // The NIC (e.g., eth0, eth1) will be the first captured group
    }
    throw new RuntimeException("NIC not found in route output");
  }
}
