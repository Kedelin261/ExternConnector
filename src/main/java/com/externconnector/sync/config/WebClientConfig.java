package com.externconnector.sync.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${http.client.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${http.client.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Value("${http.client.write-timeout-ms:10000}")
    private int writeTimeoutMs;

    private HttpClient baseHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutMs, TimeUnit.MILLISECONDS)));
    }

    @Bean("linearWebClient")
    public WebClient linearWebClient(LinearProperties props) {
        return WebClient.builder()
                .baseUrl(props.api().url())
                .clientConnector(new ReactorClientHttpConnector(baseHttpClient()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Authorization", props.api().key())
                .filter(loggingFilter("Linear"))
                .filter(errorHandlingFilter())
                .build();
    }

    @Bean("clickUpWebClient")
    public WebClient clickUpWebClient(ClickUpProperties props) {
        return WebClient.builder()
                .baseUrl(props.api().url())
                .clientConnector(new ReactorClientHttpConnector(baseHttpClient()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Authorization", props.api().key())
                .filter(loggingFilter("ClickUp"))
                .filter(errorHandlingFilter())
                .build();
    }

    private ExchangeFilterFunction loggingFilter(String platform) {
        return ExchangeFilterFunction.ofRequestProcessor(req -> {
            log.debug("[{}] --> {} {}", platform, req.method(), req.url());
            return Mono.just(req);
        });
    }

    private ExchangeFilterFunction errorHandlingFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(resp -> {
            if (resp.statusCode().isError()) {
                log.warn("HTTP {} response: {}", resp.statusCode().value(), resp.statusCode());
            }
            return Mono.just(resp);
        });
    }
}
