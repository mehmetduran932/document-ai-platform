package com.documentai.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * LLM completions can legitimately take a while (frontier "reasoning" models especially, or a
 * small model on CPU-only hardware), but must still be bounded - an unconfigured RestClient has
 * no timeout at all and would hang the request thread indefinitely if a provider stalls.
 * Configurable since the right bound depends heavily on the deployment (a cloud API vs. a small
 * local model on a 2-vCPU box can differ by an order of magnitude).
 */
@Configuration
public class AnswerRestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    public RestClient answerRestClient(
            @Value("${app.answer.read-timeout-seconds:120}") long readTimeoutSeconds) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
