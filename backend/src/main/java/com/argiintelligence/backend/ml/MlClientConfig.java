package com.argiintelligence.backend.ml;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(MlServiceProperties.class)
class MlClientConfig {

    @Bean
    MlClient mlClient(MlServiceProperties props) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build());
        factory.setReadTimeout(props.readTimeout());
        return new MlClient(RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build());
    }
}
