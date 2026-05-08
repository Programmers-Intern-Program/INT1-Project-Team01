package back.global.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean(destroyMethod = "close")
    public CloseableHttpClient httpClient() {
        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager();

        connectionManager.setDefaultConnectionConfig(
                ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(3))
                        .build()
        );

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofSeconds(3))
                .setResponseTimeout(Timeout.ofSeconds(5))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Bean
    public RestClient restClient(CloseableHttpClient httpClient) {
        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }
}