package com.glean.proxy.filters.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AllowedEgressDomains {
  public final HashSet<String> individual;
  public final HashSet<String> prefixWildcard;
  public final ArrayList<Pattern> wildcard;

  private Pattern convertWildcardDomainToPattern(String wildcardDomain) {
    // Escape '.' and '-', and replace '*' with '[^.]+' for regex
    // [^.]+ means match any character except '.', one or more times
    String regexDomain =
        wildcardDomain.replace(".", "\\.").replace("-", "\\-").replace("*", "[^.]+");
    return Pattern.compile(regexDomain);
  }

  public AllowedEgressDomains(String domainsString) {
    if (domainsString == null || domainsString.isEmpty()) {
      individual = new HashSet<>();
      prefixWildcard = new HashSet<>();
      wildcard = new ArrayList<>();
      return;
    }

    Predicate<String> isIndividualDomain = domain -> !domain.contains("*");
    Predicate<String> isPrefixWildcardDomain =
        domain -> domain.startsWith("*.") && domain.lastIndexOf("*") == 0;
    Predicate<String> isWildcardDomain =
        isIndividualDomain.negate().and(isPrefixWildcardDomain.negate());

    String[] domains = domainsString.split(",");
    individual =
        Arrays.stream(domains)
            .map(String::trim)
            .filter(isIndividualDomain)
            .map(String::toLowerCase)
            .filter(domain -> !domain.isEmpty())
            .collect(Collectors.toCollection(HashSet::new));
    prefixWildcard =
        Arrays.stream(domains)
            .map(String::trim)
            .filter(isPrefixWildcardDomain)
            .map(domain -> domain.substring(2).toLowerCase())
            .filter(domain -> !domain.isEmpty())
            .collect(Collectors.toCollection(HashSet::new));
    wildcard =
        Arrays.stream(domains)
            .map(String::trim)
            .filter(isWildcardDomain)
            .map(String::toLowerCase)
            .filter(domain -> !domain.isEmpty())
            .map(this::convertWildcardDomainToPattern)
            .collect(Collectors.toCollection(ArrayList::new));
  }
}
