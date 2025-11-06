package com.glean.proxy;

import java.net.InetSocketAddress;
import java.util.Queue;
import java.util.Set;
import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.impl.ClientDetails;
import java.util.logging.Logger;
import org.littleshoot.proxy.ChainedProxyManager;
import io.netty.handler.codec.http.HttpRequest;


public class ChainedProxyConfiguration {
    private static final Logger logger = Logger.getLogger(ChainedProxyConfiguration.class.getName());
    
    public static ChainedProxyManager fromEnvironment() {
        String forwardProxyHost = System.getenv("FORWARD_PROXY_HOST");
        String forwardProxyPort = System.getenv("FORWARD_PROXY_PORT");
        String dataSourceHostsEnv = System.getenv("FORWARD_PROXY_DATA_SOURCE_HOSTS");
        if (forwardProxyHost == null || forwardProxyHost.isEmpty() ||
            forwardProxyPort == null || forwardProxyPort.isEmpty() ||
            dataSourceHostsEnv == null || dataSourceHostsEnv.isEmpty()) {
            return (HttpRequest httpRequest,
            Queue<ChainedProxy> chainedProxies,
            ClientDetails clientDetails) -> {
                chainedProxies.add(ChainedProxyAdapter.FALLBACK_TO_DIRECT_CONNECTION);
            };
        }
        InetSocketAddress forwardProxy = new InetSocketAddress(forwardProxyHost, Integer.parseInt(forwardProxyPort)); 
        Set<String> dataSourceHosts = Set.of(dataSourceHostsEnv.split(","));
        
        ChainedProxyManager chainedProxyManager = (HttpRequest httpRequest,
        Queue<ChainedProxy> chainedProxies,
        ClientDetails clientDetails) -> {
            String hostHeader = httpRequest.headers().get("Host");
            if (hostHeader == null) {
                chainedProxies.add(ChainedProxyAdapter.FALLBACK_TO_DIRECT_CONNECTION);
                logger.severe("No host header found, falling back to direct connection");
                return;
            }
            String host = hostHeader.split(":")[0];
            
            if (dataSourceHosts.contains(host)) {
                chainedProxies.add(new ChainedProxyAdapter() {
                    @Override
                    public InetSocketAddress getChainedProxyAddress() {
                        return forwardProxy;
                    }
                      
                    @Override
                    public String getUsername() {
                        return System.getenv("FORWARD_PROXY_USERNAME");
                    }
                    
                    @Override
                    public String getPassword() {
                        return System.getenv("FORWARD_PROXY_PASSWORD");
                    }
                });
            } else {
                chainedProxies.add(ChainedProxyAdapter.FALLBACK_TO_DIRECT_CONNECTION);
            }
        };
        return chainedProxyManager;
    }
}
