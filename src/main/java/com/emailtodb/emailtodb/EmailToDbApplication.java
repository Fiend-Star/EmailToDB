package com.emailtodb.emailtodb;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for EmailToDB
 * Enables AOP, scheduling, and metrics for comprehensive monitoring
 */
@SpringBootApplication
@EnableAspectJAutoProxy
@EnableScheduling
public class EmailToDbApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailToDbApplication.class, args);
    }
    
    /**
     * Customizes the MeterRegistry with application metadata
     */
    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "EmailToDB")
                .commonTags("environment", "${spring.profiles.active:production}");
    }
    
    /**
     * Adds support for @Timed annotation for method-level metrics
     */
    @Bean
    TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
