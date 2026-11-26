package com.vidacruz.clinical.infrastructure.config;

import com.vidacruz.clinical.domain.ports.UrgencyStateRepository;
import com.vidacruz.clinical.domain.services.GetUrgencyStateUseCase;
import com.vidacruz.clinical.domain.services.ProjectTriageEventUseCase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring of the urgency projection. Properties are bound here so both the consumer and the Redis adapter share them.
 */
@Configuration
@EnableConfigurationProperties({TriageEventsProperties.class, UrgencyStateProperties.class})
public class UrgencyConfiguration {

    @Bean
    public GetUrgencyStateUseCase getUrgencyStateUseCase(UrgencyStateRepository repository) {
        return new GetUrgencyStateUseCase(repository);
    }

    @Bean
    public ProjectTriageEventUseCase projectTriageEventUseCase(UrgencyStateRepository repository) {
        return new ProjectTriageEventUseCase(repository);
    }
}
