package com.glean.proxy.filters.helpers;

import com.google.common.annotations.VisibleForTesting;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OnPremisesProxy {
  private static final Pattern WEBHOOK_TARGET_PATH_PATTERN =
      Pattern.compile("^/proxy/webhook/(.*)");
  private static final Pattern API_PROXY_TARGET_PATH_PATTERN =
      Pattern.compile("^/proxy/(api|rest/api)/(.*)");
  private static final Pattern CRAWL_TARGET_PATH_PATTERN =
      Pattern.compile("^/proxy/crawl/([^/]+)/(.*)");

  public enum Intent {
    WEBHOOK,
  }

  // This target must be HTTP. If https endpoint is provided, we set protocolUpgrade and upgrade
  // protocol before request is made to server.
  private final String webhookTarget;
  private final boolean allowApiIngress;

  private final boolean protocolUpgrade;
  private static final Logger logger = Logger.getLogger(OnPremisesProxy.class.getName());

  public static OnPremisesProxy fromEnvironment() {
    final String webhookTarget = System.getenv("WEBHOOK_TARGET");
    if (webhookTarget == null || webhookTarget.isEmpty()) {
      logger.severe("WEBHOOK_TARGET not specified");
      return null;
    }
    boolean shouldAllowIngress = "true".equalsIgnoreCase(System.getenv("ALLOW_API_INGRESS"));
    return new OnPremisesProxy(webhookTarget, shouldAllowIngress);
  }

  @VisibleForTesting
  OnPremisesProxy(String webhookTarget, boolean allowApiIngress) {
    this.allowApiIngress = allowApiIngress;
    this.protocolUpgrade = webhookTarget.startsWith("https://");
    this.webhookTarget = webhookTarget.replaceFirst("^https://", "http://");
  }

  public boolean shouldUpgradeProtocol() {
    return protocolUpgrade;
  }

  public boolean isApiIngressAllowed() {
    return allowApiIngress;
  }

  public String getTargetURLForUpgradedProtocol(String pathWithPrefix) {
    return String.format(
        "%s%s", webhookTarget.replaceFirst("^http://", "https://"), pathWithPrefix);
  }

  public String getTargetURL(String requestURI) {
    // /proxy/webhook/github/events
    Matcher webhookPathMatcher = WEBHOOK_TARGET_PATH_PATTERN.matcher(requestURI);
    if (webhookPathMatcher.matches()) {
      String targetPath = webhookPathMatcher.group(1);
      return String.format("%s/%s", webhookTarget, targetPath);
    }
    if (allowApiIngress) {
      Matcher apiPathMatcher = API_PROXY_TARGET_PATH_PATTERN.matcher(requestURI);
      if (apiPathMatcher.find()) {
        String apiType = apiPathMatcher.group(1); // This will be either 'api' or 'rest/api'
        String path =
            apiPathMatcher.group(2); // This is the rest of the path after 'api' or 'rest/api'
        return String.format("%s/%s/%s", webhookTarget, apiType, path);
      }
    }
    throw new IllegalArgumentException(
        String.format("Unexpected request URI format for webhook intent: %s", requestURI));
  }
}
