package com.glean.proxy.test_utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TestUtils {
  @SuppressWarnings({"unchecked"})
  public static void updateEnv(String name, String val) throws ReflectiveOperationException {
    Map<String, String> env = System.getenv();
    Field field = env.getClass().getDeclaredField("m");
    field.setAccessible(true);
    ((Map<String, String>) field.get(env)).put(name, val);
  }

  @SuppressWarnings({"unchecked"})
  public static void removeEnv(String name) throws ReflectiveOperationException {
    Map<String, String> env = System.getenv();
    Field field = env.getClass().getDeclaredField("m");
    field.setAccessible(true);
    ((Map<String, String>) field.get(env)).remove(name);
  }

  public static void verifyHttpResponse(HttpResponse response, String message, int statusCode) {
    DefaultFullHttpResponse httpResponse = (DefaultFullHttpResponse) response;
    int actualStatusCode = httpResponse.status().code();
    String actualMessage = httpResponse.content().toString(StandardCharsets.UTF_8);
    assertThat(actualStatusCode).isEqualTo(statusCode);
    assertThat(actualMessage).isEqualTo(message);
  }
}
