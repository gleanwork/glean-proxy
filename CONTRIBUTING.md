# Contributing to @gleanwork/glean-proxy

Thank you for your interest in contributing to Glean's HTTP Proxy! This document provides guidelines and instructions for development.

## Table of Contents

- [Development Setup](#development-setup)
- [Making Changes](#making-changes)
- [Code Style](#code-style)
- [Testing](#testing)
- [Documentation](#documentation)
- [File Structure](#file-structure)
- [Adding a New Filter](#adding-a-new-filter)
- [Need Help?](#need-help)

## Development Setup

1. Clone the repository:

```bash
git clone https://github.com/gleanwork/glean-proxy.git
cd glean-proxy
```

2. Ensure `java`, `bazel`, and `pip` are installed. We use Java 17.0.5 and Bazel 7.4.1.

3. Install precommit hooks:

```bash
pip install pre-commit
pre-commit install
```

4. Build the project:

```bash
bazel build //src/main/java/com/glean/proxy:proxy
```

5. Run tests:

```bash
bazel test //src/test/java/com/glean/proxy/...
```

## Making Changes

1. Fork the repository
2. Install precommit hooks: `pre-commit install`
3. Create your feature branch: `git checkout -b feature/my-feature`
4. Commit your changes: `git commit -am 'Add new feature'`
5. Push to the branch: `git push origin feature/my-feature`
6. Submit a pull request

## Code Style

- Use Java for all new code
- Follow the existing code style (enforced by pre-commit hooks)
- Write tests for new functionality

## Testing

- Add unit tests for new features
- Ensure all tests pass before submitting a pull request
- Use the provided test utilities and fixtures

## Documentation

- Update documentation for any changed functionality
- Keep the README.md up to date

## File Structure

The Java codebase is organized as follows:

```
src/
├── main/java/com/glean/proxy/
│   ├── filters/               # Filters that can be applied to proxied requests
│   │   └── helpers/           # Helper classes for filters
│   ├── schemas/               # API response schema classes
│   ├── ProxyMain.java
│   ├── FilterConfiguration.java
│   └── ...
└── test/java/com/glean/proxy/ # Mirrors `main` package
    ├── filters/
    │   └── helpers/
    ├── test_utils/
    └── ...
```

## Adding a New Filter

To add a new HTTP filter to GleanProxy, do the following:

### 1. Create a New Filter Class

Create your filter class in `src/main/java/com/glean/proxy/filters/`, extending `HttpFiltersAdapter` from LittleProxy:

```java
import org.littleshoot.proxy.HttpFiltersAdapter;

public class MyCustomFilter extends HttpFiltersAdapter {
    @Override
    public HttpResponse clientToProxyRequest(HttpObject httpObject) {
        // Your filter logic here
        return null; // Return null to let the request through
    }
}
```

### 2. Register the Filter in FilterConfiguration

Add your filter to the `filterRegistry` in `FilterConfiguration.java`:

```java
MyCustomFilter.class.getSimpleName(),
() -> {
    String someConfig = System.getenv("MY_CUSTOM_CONFIG");
    return (request, ctx) -> new MyCustomFilter(request, someConfig);
}
```
To enable your filter, add it to the desired `<platform>_FILTERS` environment variable, for example: `GOOGLE_FILTERS=MyCustomFilter`.

### 3. Update Configuration Documentation

Add your filter's config variables to `README.md` (if applicable):

```markdown
**MyCustomFilter**
| Variable | Description | Default |
|----------|-------------|---------|
| `MY_CUSTOM_CONFIG` | Custom configuration variable | None |
```

## Need Help?

- Issues: [GitHub Issues](https://github.com/gleanwork/glean-proxy/issues)
- Email: [support@glean.com](mailto:support@glean.com)
