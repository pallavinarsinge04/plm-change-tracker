package com.plmtracker.plm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MiniPlmProperties.class)
public class MiniPlmConfig {
}
