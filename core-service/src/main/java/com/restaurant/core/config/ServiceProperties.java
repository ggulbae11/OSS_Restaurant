package com.restaurant.core.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "services")
@Getter @Setter
public class ServiceProperties {
    private String authUrl;
    private String aiUrl;
}
