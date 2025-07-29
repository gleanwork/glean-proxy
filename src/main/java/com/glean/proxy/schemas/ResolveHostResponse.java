package com.glean.proxy.schemas;

// Given a hostname, this class encapsulates the response of resolving the hostname to an IP
// address.
public class ResolveHostResponse {
  public ResolvedBy resolvedBy;
  public String hostName;
  public String ipAddress;
  public String nameserverUsed;
  public String nsLookupErrorString;

  public enum ResolvedBy {
    NONE,
    IP_HOST_MAPPING,
    NAMESERVER
  }
}
