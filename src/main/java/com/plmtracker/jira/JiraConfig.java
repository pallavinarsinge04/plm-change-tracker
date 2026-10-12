package com.plmtracker.jira;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JiraProperties.class)
public class JiraConfig {
}
