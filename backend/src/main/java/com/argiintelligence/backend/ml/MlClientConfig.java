package com.argiintelligence.backend.ml;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(MlProperties.class)
class MlClientConfig {

    @Bean
    MlClient mlClient(MlProperties properties, JsonMapper jsonMapper) {
        // HTTP/1.1 only: the JDK client otherwise sends an "Upgrade: h2c" request, and uvicorn (which serves the
        // ML service) drops the POST body of such a request, so every prediction would fail with 422.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new MlClient(restClient, jsonMapper);
    }
}
