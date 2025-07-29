package com.glean.proxy.schemas;

public class GetRouteResponse {
  public boolean routeFound;
  public RequestDirection routedToward;

  // the nic card name that the route is pointing to e.g. eth0
  public String nic;

  // the ip address of the network interface card e.g. 10.28.0.1
  public String nicAddress;
  public FailureReason failureReason;
  public String errorString;

  public enum RequestDirection {
    UNKNOWN,
    INBOUND,
    OUTBOUND,
  }

  public enum FailureReason {
    NONE,
    SHELL_COMMAND_FAILED,
    UNABLE_TO_PARSE_NIC_INFO,
  }
}
