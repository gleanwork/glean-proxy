package com.glean.proxy.filters.helpers;

import com.glean.proxy.schemas.ProcessResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public class ShellCommandRunner {
  public static ProcessResponse runCommand(List<String> command)
      throws InterruptedException, IOException {
    ProcessResponse response = new ProcessResponse();
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    Process process = processBuilder.start();
    // We should ideally alternate between reading from the input and error streams otherwise the
    // process might hang if the error stream is full. This is fine here because we are not
    // expecting a lot of output/error.
    String outputString =
        new BufferedReader(new InputStreamReader(process.getInputStream()))
            .lines()
            .collect(Collectors.joining("\n"));
    String errorString =
        new BufferedReader(new InputStreamReader(process.getErrorStream()))
            .lines()
            .collect(Collectors.joining("\n"));
    response.outputString = outputString;
    response.errorString = errorString;
    response.exitCode = process.waitFor();
    return response;
  }
}
