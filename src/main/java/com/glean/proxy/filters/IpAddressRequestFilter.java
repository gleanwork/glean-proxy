package com.glean.proxy.filters;

import com.glean.proxy.filters.helpers.Utils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Logger;
import org.littleshoot.proxy.HttpFiltersAdapter;

/** Filters requests based on client IP address, only allowing connections from specific IPs. */
public class IpAddressRequestFilter extends HttpFiltersAdapter {
  public enum ALLOWED_PROXY_ADDRESS_TYPE {
    REMOTE,
    LOCAL
  }

  private final String requiredIpAddress;
  private final ALLOWED_PROXY_ADDRESS_TYPE allowedProxyAddressType;
  private static final String LOCALHOST = "127.0.0.1";
  private static final Logger logger = Logger.getLogger(IpAddressRequestFilter.class.getName());

  public IpAddressRequestFilter(
      HttpRequest originalRequest,
      ChannelHandlerContext ctx,
      String requiredIpAddress,
      // On AWS, there's only one nic attached to the proxy, so we can't use local address to
      // differentiate requests
      ALLOWED_PROXY_ADDRESS_TYPE allowedProxyAddressType) {
    super(originalRequest, ctx);
    this.requiredIpAddress = requiredIpAddress;
    this.allowedProxyAddressType = allowedProxyAddressType;
  }

  @Override
  public HttpResponse clientToProxyRequest(HttpObject httpObject) {
    final SocketAddress incomingSocketAddress =
        ALLOWED_PROXY_ADDRESS_TYPE.REMOTE.equals(this.allowedProxyAddressType)
            ? ctx.channel().remoteAddress()
            : ctx.channel().localAddress();
    if (incomingSocketAddress instanceof InetSocketAddress inetAddress) {
      final String incomingHostAddress = inetAddress.getAddress().getHostAddress();
      final boolean shouldAccept =
          requiredIpAddress.equals(incomingHostAddress) || LOCALHOST.equals(incomingHostAddress);
      logger.fine(
          String.format(
              "Incoming address: %s, host ip accepted: %s", incomingSocketAddress, shouldAccept));
      if (shouldAccept) {
        return null;
      } else {
        logger.warning(String.format("Rejected proxy request from: %s", inetAddress));
      }
    } else {
      logger.warning(
          String.format(
              "Received incoming request from unexpected socket type: %s, %s",
              incomingSocketAddress.getClass().getName(), incomingSocketAddress));
    }
    return Utils.createHTTPResponse("UNAUTHORIZED", HttpResponseStatus.UNAUTHORIZED);
  }
}
