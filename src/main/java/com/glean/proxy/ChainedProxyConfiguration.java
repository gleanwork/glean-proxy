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
        if (System.getenv("FORWARD_PROXY_HOST") == null || System.getenv("FORWARD_PROXY_PORT") == null || System.getenv("FORWARD_PROXY_DATA_SOURCE_HOSTS") == null) {
            return (HttpRequest httpRequest,
            Queue<ChainedProxy> chainedProxies,
            ClientDetails clientDetails) -> {
                chainedProxies.add(ChainedProxyAdapter.FALLBACK_TO_DIRECT_CONNECTION);
            };
        }
        InetSocketAddress forwardProxy = new InetSocketAddress(System.getenv("FORWARD_PROXY_HOST"), Integer.parseInt(System.getenv("FORWARD_PROXY_PORT"))); 
        Set<String> dataSourceHosts = Set.of(System.getenv("FORWARD_PROXY_DATA_SOURCE_HOSTS").split(","));
        
        ChainedProxyManager chainedProxyManager = (HttpRequest httpRequest,
        Queue<ChainedProxy> chainedProxies,
        ClientDetails clientDetails) -> {
            String hostHeader = httpRequest.headers().get("Host");
            String host = hostHeader != null ? hostHeader.split(":")[0] : "";
            
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
