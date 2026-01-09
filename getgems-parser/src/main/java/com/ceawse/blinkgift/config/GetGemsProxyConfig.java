package com.ceawse.blinkgift.config;

import feign.Client;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.okhttp.OkHttpClient;
import lombok.Data;
import okhttp3.ConnectionPool;
import okhttp3.Protocol;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Data
@Configuration
@ConfigurationProperties(prefix = "getgems")
public class GetGemsProxyConfig {

    private String apiKey;
    private ProxyConfig proxy;

    @Data
    public static class ProxyConfig {
        private String host;
        private int port;
        private boolean enabled;
    }

    @Bean
    public Client feignClient() {
        okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder()
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS);

        if (proxy != null && proxy.isEnabled()) {
            builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxy.getHost(), proxy.getPort())));
        }

        return new OkHttpClient(builder.build());
    }

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            if (apiKey != null) {
                template.header("Authorization", apiKey);
            }
            template.header("Accept", "application/json");
            // Имитируем реальный браузер более детально
            template.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            // Connection: close иногда помогает при парсинге, чтобы не держать stale соединения,
            // но с HTTP/1.1 и ConnectionPool лучше оставить keep-alive (по умолчанию).
        };
    }

    // Добавляем Retryer, чтобы при случайном разрыве сеть пробовала еще раз (3 попытки)
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(100L, TimeUnit.SECONDS.toMillis(1L), 3);
    }
}