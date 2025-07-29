# GleanProxy

An HTTP proxy built on [LittleProxy](https://github.com/LittleProxy/LittleProxy) and [Netty](https://netty.io) that serves as an alternative to GCP/AWS NAT Gateway.

## Table of Contents

- [Installation and Usage](#installation-and-usage)
- [Configuration](#configuration)
- [Running with Docker](#running-with-docker)
- [Running with Bazel](#running-with-bazel)
- [Contributing](#contributing)
- [License](#license)

## Installation and Usage

Add GleanProxy as a maven dependency as follows:
```xml
<dependency>
    <groupId>com.glean</groupId>
    <artifactId>proxy</artifactId>
    <version>1.0.1</version>
</dependency>
```

Then run it:

```java
import com.glean.proxy.ProxyNetworking;

public class ProxyMain {
  public static void main(String[] args) {
    final int port = Integer.parseInt(args[0]);
    ProxyNetworking.builder().build().run(port);
  }
}
```

You can specify a custom `ThreadPoolConfiguration`:

```java
import org.littleshoot.proxy.impl.ThreadPoolConfiguration;

ThreadPoolConfiguration config =
        new ThreadPoolConfiguration()
            .withAcceptorThreads(4)
            .withClientToProxyWorkerThreads(6)
            .withProxyToServerWorkerThreads(8);

ProxyNetworking.builder().withThreadPoolConfiguration(config).build().run(port);
```

Or a `FilterConfiguration`, which you can add custom filters to:

```java
FilterConfiguration.registerCustomFilter(
  AwsCustomFilter.class.getSimpleName(),
  () -> {
    String someConfig = System.getenv("SOME_CONFIG_VARIABLE");
    return (request, ctx) -> new AwsCustomFilter(request, ctx, someConfig);
  }
);

FilterConfiguration filters = FilterConfiguration.fromFilterNames(
    /* AWS Filters */ List.of("AwsCustomFilter"),
    /* GCP Filters */ List.of("GCPDisallowInternalAddressFilter"),
    /* Cross-platform Filters */ List.of("UpgradeRequestFilter", "IpAddressRequestFilter"),
    /* Debug Filters */ List.of("ProxyDebugFilter"));

ProxyNetworking.builder().withFilterConfiguration(filters).build().run(port);
```

## Configuration

GleanProxy is configured through environment variables. By default, the set of filters we use at Glean are enabled. Only environment variables for currently active filters are necessary.

### Required Environment Variables

| Variable | Description | Default |
|---------------------|-------------|---------|
| `CLOUD_PLATFORM` | Cloud platform (`AWS` or `GOOGLE`) | None |

### Filter Configuration Variables

| Variable | Description | Default |
|---------------------|-------------|---------|
| `AWS_FILTERS` | Comma-separated list of filters to enable on AWS | `AwsDisallowInternalAddressForTransitVpc,AwsFilterEgressTrafficByDomain` |
| `GOOGLE_FILTERS` | Filters to enable on GCP | `GCPDisallowInternalAddressFilter` |
| `CROSS_PLATFORM_FILTERS` | Filters to enable on all platforms | `IpAddressRequestFilter,UpgradeRequestFilter` |
| `DEBUG_FILTERS` | Filters to enable for the debug endpoint | `IpAddressRequestFilter,ProxyDebugFilter` |

### Filter-Specific Environment Variables

**AwsDisallowInternalAddressForTransitVpc**
| Variable | Description | Default |
|---------------------|-------------|---------|
| `WEBHOOK_TARGET` | URL of internal VPC endpoint to block | None |

**AwsFilterEgressTrafficByDomain**
| Variable | Description | Default |
|---------------------|-------------|---------|
| `ENFORCE_ALLOWED_EGRESS_DOMAINS` | Enable/disable egress domain filtering (true/false) | `false` |
| `ALLOWED_EGRESS_DOMAINS` | Comma-separated list of allowed egress domains | `""` |

**GCPDisallowInternalAddressFilter**
| Variable | Description | Default |
|---------------------|-------------|---------|
| `PROXY_TYPE` | `STANDALONE`, `SHARED_VPC`, or `TRANSIT` | None |
| `GKE_SERVICE_IP_RANGE` | IP range for GKE service IPs on GCP | None |

**IpAddressRequestFilter**
| Variable | Description | Default |
|---------------------|-------------|---------|
| `ALLOWED_PROXY_ADDRESS` | IP address allowed to use the proxy | None |
| `ALLOWED_PROXY_ADDRESS_TYPE` | `REMOTE` or `LOCAL` | None |

**UpgradeRequestFilter**
| Variable | Description | Default |
|---------------------|-------------|---------|
| `UPGRADE_HTTP_REQUESTS` | Upgrade HTTP requests to HTTPS (true/false) | `false` |

## Running with Docker

You can run GleanProxy with Docker using an OCI image tarball:

```bash
export CLOUD_PLATFORM=GOOGLE
export AWS_FILTERS=
export GOOGLE_FILTERS=
export CROSS_PLATFORM_FILTERS=
export DEBUG_FILTERS=

curl -L https://github.com/gleanwork/glean-proxy/releases/latest/download/proxy_image_tarball.tar | docker load

docker run \
  -e CLOUD_PLATFORM \
  -e AWS_FILTERS \
  -e GOOGLE_FILTERS= \
  -e CROSS_PLATFORM_FILTERS \
  -e DEBUG_FILTERS \
  -p 8080:8080 \
  glean-proxy:latest 8080
```

You can also build/load the Docker image from source:

```bash
bazel run //:load_proxy_image

docker run \
  -e CLOUD_PLATFORM \
  -e AWS_FILTERS \
  -e GOOGLE_FILTERS= \
  -e CROSS_PLATFORM_FILTERS \
  -e DEBUG_FILTERS \
  -p 8080:8080 \
  glean-proxy:latest 8080
```

## Running with Bazel

### Dependencies
Java 17+ \
Bazel 7 (we use 7.4.1)

```bash
export CLOUD_PLATFORM=AWS
export AWS_FILTERS=
export GOOGLE_FILTERS=
export CROSS_PLATFORM_FILTERS=
export DEBUG_FILTERS=

bazel run //src/main/java/com/glean/proxy:ProxyMain 8080

# Health check
curl localhost:8080/liveness_check

# Proxy a request
curl --proxy localhost:8080 https://www.glean.com
```

## Contributing

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and guidelines.

## License

[<u>MIT</u>](/LICENSE)
