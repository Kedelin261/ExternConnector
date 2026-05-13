package com.externconnector.sync.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Max payload size for webhook ingestion — 1MB.
     * Prevents OOM from oversized payloads.
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<PayloadSizeLimitFilter> payloadSizeFilter() {
        var reg = new org.springframework.boot.web.servlet.FilterRegistrationBean<PayloadSizeLimitFilter>();
        reg.setFilter(new PayloadSizeLimitFilter(1_048_576)); // 1MB
        reg.addUrlPatterns("/webhooks/*");
        reg.setOrder(1);
        return reg;
    }
}
