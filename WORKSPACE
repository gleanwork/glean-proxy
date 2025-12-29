workspace(name = "com_github_gleanwork_glean-proxy")

load("@aspect_bazel_lib//lib:repositories.bzl", "aspect_bazel_lib_dependencies", "aspect_bazel_lib_register_toolchains")
load("@aspect_rules_js//js:repositories.bzl", "rules_js_dependencies")
load("@aspect_rules_lint//format:repositories.bzl", "rules_lint_dependencies")
load("@aspect_rules_lint//lint:pmd.bzl", "fetch_pmd")
load("@bazel_features//:deps.bzl", "bazel_features_deps")
load("@bazel_gazelle//:deps.bzl", "gazelle_dependencies")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@contrib_rules_jvm//:gazelle_setup.bzl", "contrib_rules_jvm_gazelle_setup")

# Fetches the contrib_rules_jvm dependencies.
# If you want to have a different version of some dependency,
# you should fetch it *before* calling this.
load("@contrib_rules_jvm//:repositories.bzl", "contrib_rules_jvm_deps", "contrib_rules_jvm_gazelle_deps")

# Now ensure that the downloaded deps are properly configured
load("@contrib_rules_jvm//:setup.bzl", "contrib_rules_jvm_setup")
load("@io_bazel_rules_go//go:deps.bzl", "go_register_toolchains", "go_rules_dependencies")
load("@maven//:defs.bzl", "pinned_maven_install")
load("@rules_java//java:repositories.bzl", "rules_java_dependencies", "rules_java_toolchains")
load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")
load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")
load("@rules_oci//oci:dependencies.bzl", "rules_oci_dependencies")
load("@rules_oci//oci:pull.bzl", "oci_pull")
load("@rules_oci//oci:repositories.bzl", "oci_register_toolchains")
load("@rules_pkg//:deps.bzl", "rules_pkg_dependencies")
load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies")

http_archive(
    name = "platforms",
    sha256 = "218efe8ee736d26a3572663b374a253c012b716d8af0c07e842e82f238a0a7ee",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/platforms/releases/download/0.0.10/platforms-0.0.10.tar.gz",
        "https://github.com/bazelbuild/platforms/releases/download/0.0.10/platforms-0.0.10.tar.gz",
    ],
)

http_archive(
    name = "bazel_features",
    sha256 = "3a900b9d62f19c6168c41694268e93cca355d1d35fae48c6d13eb7947026a35b",
    strip_prefix = "bazel_features-1.16.0",
    url = "https://github.com/bazel-contrib/bazel_features/releases/download/v1.16.0/bazel_features-v1.16.0.tar.gz",
)

bazel_features_deps()

http_archive(
    name = "rules_pkg",
    sha256 = "d250924a2ecc5176808fc4c25d5cf5e9e79e6346d79d5ab1c493e289e722d1d0",
    urls = [
        "https://github.com/bazelbuild/rules_pkg/releases/download/0.10.1/rules_pkg-0.10.1.tar.gz",
    ],
)

rules_pkg_dependencies()

http_archive(
    name = "aspect_rules_lint",
    patch_args = ["-p1"],
    patches = ["//tools/lint:rules_lint_pmd7.patch"],
    sha256 = "7d5feef9ad85f0ba78cc5757a9478f8fa99c58a8cabc1660d610b291dc242e9b",
    strip_prefix = "rules_lint-1.0.2",
    url = "https://github.com/aspect-build/rules_lint/releases/download/v1.0.2/rules_lint-v1.0.2.tar.gz",
)

rules_lint_dependencies()

fetch_pmd()

http_archive(
    name = "aspect_bazel_lib",
    sha256 = "688354ee6beeba7194243d73eb0992b9a12e8edeeeec5b6544f4b531a3112237",
    strip_prefix = "bazel-lib-2.8.1",
    url = "https://github.com/aspect-build/bazel-lib/releases/download/v2.8.1/bazel-lib-v2.8.1.tar.gz",
)

aspect_bazel_lib_dependencies()

aspect_bazel_lib_register_toolchains()

http_archive(
    name = "io_bazel_rules_go",
    sha256 = "b78f77458e77162f45b4564d6b20b6f92f56431ed59eaaab09e7819d1d850313",
    urls = [
        "https://mirror.bazel.build/github.com/bazel-contrib/rules_go/releases/download/v0.53.0/rules_go-v0.53.0.zip",
        "https://github.com/bazel-contrib/rules_go/releases/download/v0.53.0/rules_go-v0.53.0.zip",
    ],
)

go_rules_dependencies()

go_register_toolchains(go_version = "1.23.7")

http_archive(
    name = "bazel_gazelle",
    sha256 = "32938bda16e6700063035479063d9d24c60eda8d79fd4739563f50d331cb3209",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/bazel-gazelle/releases/download/v0.35.0/bazel-gazelle-v0.35.0.tar.gz",
        "https://github.com/bazelbuild/bazel-gazelle/releases/download/v0.35.0/bazel-gazelle-v0.35.0.tar.gz",
    ],
)

gazelle_dependencies(go_sdk = "go_sdk")

http_archive(
    name = "rules_proto",
    sha256 = "303e86e722a520f6f326a50b41cfc16b98fe6d1955ce46642a5b7a67c11c0f5d",
    strip_prefix = "rules_proto-6.0.0",
    url = "https://github.com/bazelbuild/rules_proto/releases/download/6.0.0/rules_proto-6.0.0.tar.gz",
)

rules_proto_dependencies()

http_archive(
    name = "rules_java",
    sha256 = "f8ae9ed3887df02f40de9f4f7ac3873e6dd7a471f9cddf63952538b94b59aeb3",
    urls = [
        "https://github.com/bazelbuild/rules_java/releases/download/7.6.1/rules_java-7.6.1.tar.gz",
    ],
)

rules_java_dependencies()

rules_java_toolchains()

# XCODE upgrade to 16.3 with MacOS 15.4 breaks bazel builds on Mac , we try to pin the ZLIB version to a later one, since
# Bazel is still lagging and we bring in Zlib transitively
zlib_version = "1.3.1"

zlib_sha256 = "9a93b2b7dfdac77ceba5a558a580e74667dd6fede4585b91eefb60f03b72df23"

http_archive(
    name = "zlib",
    build_file = "@com_google_protobuf//:third_party/zlib.BUILD",
    sha256 = zlib_sha256,
    strip_prefix = "zlib-%s" % zlib_version,
    urls = ["https://github.com/madler/zlib/releases/download/v{v}/zlib-{v}.tar.gz".format(v = zlib_version)],
)

http_archive(
    name = "contrib_rules_jvm",
    patch_args = ["-p1"],
    patches = ["//tools/rules/jvm:rules_jvm.patch"],
    sha256 = "e6cd8f54b7491fb3caea1e78c2c740b88c73c7a43150ec8a826ae347cc332fc7",
    strip_prefix = "rules_jvm-0.27.0",
    url = "https://github.com/bazel-contrib/rules_jvm/releases/download/v0.27.0/rules_jvm-v0.27.0.tar.gz",
)

contrib_rules_jvm_deps()

contrib_rules_jvm_gazelle_deps()

contrib_rules_jvm_setup()

contrib_rules_jvm_gazelle_setup()

RULES_JVM_EXTERNAL_TAG = "6.1"

RULES_JVM_EXTERNAL_SHA = "08ea921df02ffe9924123b0686dc04fd0ff875710bfadb7ad42badb931b0fd50"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/releases/download/%s/rules_jvm_external-%s.tar.gz" % (RULES_JVM_EXTERNAL_TAG, RULES_JVM_EXTERNAL_TAG),
)

rules_jvm_external_deps()

rules_jvm_external_setup()

http_archive(
    name = "aspect_rules_js",
    sha256 = "83e5af4d17385d1c3268c31ae217dbfc8525aa7bcf52508dc6864baffc8b9501",
    strip_prefix = "rules_js-2.3.7",
    url = "https://github.com/aspect-build/rules_js/releases/download/v2.3.7/rules_js-v2.3.7.tar.gz",
)

# This pulls in nodejs env and other aspect bazel lib set up for us.
# https://github.com/aspect-build/rules_js/blob/066df77bd0dd6247ffd9ec728bbdcf6f6203f2e0/js/repositories.bzl#L21
rules_js_dependencies()

maven_install(
    artifacts = [
        "org.assertj:assertj-core:3.24.2",
        "com.google.code.gson:gson:2.10.1",
        # This was downgraded, so we can keep netty at 4.1.*
        "io.github.littleproxy:littleproxy:2.4.0",
        "io.netty:netty-codec-http:4.1.127.Final",
        "com.google.cloud:google-cloud-monitoring:3.52.0",
        "org.apache.httpcomponents:httpclient:4.5.14",
        "com.uber.nullaway:nullaway:0.10.9",
        "org.mockito:mockito-core:5.12.0",
        "org.slf4j:slf4j-jdk14:2.0.13",
        "io.opentelemetry:opentelemetry-api:1.44.1",
        "io.opentelemetry:opentelemetry-sdk:1.44.1",
        "io.opentelemetry:opentelemetry-sdk-common:1.44.1",
        "io.opentelemetry:opentelemetry-sdk-metrics:1.44.1",
        "io.opentelemetry:opentelemetry-exporter-otlp:1.44.1",
        "io.opentelemetry.semconv:opentelemetry-semconv:1.28.0-alpha",
        "com.google.cloud.opentelemetry:exporter-metrics:0.33.0",
    ],
    fetch_sources = True,
    maven_install_json = "//:maven_install.json",
    repositories = [
        "https://repo1.maven.org/maven2",
    ],
)

pinned_maven_install()

http_archive(
    name = "rules_oci",
    sha256 = "acbf8f40e062f707f8754e914dcb0013803c6e5e3679d3e05b571a9f5c7e0b43",
    strip_prefix = "rules_oci-2.0.1",
    url = "https://github.com/bazel-contrib/rules_oci/releases/download/v2.0.1/rules_oci-v2.0.1.tar.gz",
)

rules_oci_dependencies()

oci_register_toolchains(name = "oci")

oci_pull(
    name = "distroless_java17",
    digest = "sha256:eba3112cc48f46e4eac153191f229baa7bd1895f9d6219b497699b803fd4b4a2",
    image = "gcr.io/distroless/java17-debian12",
    platforms = [
        "linux/amd64",
        "linux/arm64/v8",
    ],
)
