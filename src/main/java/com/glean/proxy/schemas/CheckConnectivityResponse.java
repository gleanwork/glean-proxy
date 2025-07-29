package com.glean.proxy.schemas;

public class CheckConnectivityResponse {
  public String hostname;
  public int port;
  public long timeoutSecs;

  public enum Status {
    UNKNOWN,
    OK,
    ERROR,
    TIMEOUT,
  }

  public Status status = Status.UNKNOWN;
  public String message;
}
