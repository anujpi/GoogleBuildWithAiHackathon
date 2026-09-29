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
        return create(props);
    }

    /** The one way an MlClient is built; MlServiceLiveTest uses it too, so it tests this configuration. */
    static MlClient create(MlServiceProperties props) {
        // HTTP/1.1: over plain http the JDK client otherwise attempts an h2c upgrade, which uvicorn rejects (400).
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(props.connectTimeout()).build());
        factory.setReadTimeout(props.readTimeout());
        return new MlClient(RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build());
    }
}
