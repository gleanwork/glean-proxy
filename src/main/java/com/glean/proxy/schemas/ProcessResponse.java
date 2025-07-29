package com.glean.proxy.schemas;

// This class will contain the response of running a shell command on the proxy server.
public class ProcessResponse {
  public int exitCode;
  public String outputString;
  public String errorString;
}
