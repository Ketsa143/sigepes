package com.sigepes.backend.rms;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * HU-17 · R5 · Un hilo dedicado al ajuste del RMS: no compite con las peticiones de la API ni con
 * las exportaciones, y dos cálculos nunca corren a la vez.
 */
@Configuration
class RmsConfig {

    @Bean(name = "rmsExecutor")
    TaskExecutor rmsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("rms-");
        executor.initialize();
        return executor;
    }

    /** Un cálculo pendiente por un reinicio (o encolado por el seed DEMO) se retoma al arrancar. */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    void resumePending(ApplicationReadyEvent event) {
        RmsService service = event.getApplicationContext().getBean(RmsService.class);
        TaskExecutor executor = event.getApplicationContext().getBean("rmsExecutor", TaskExecutor.class);
        executor.execute(service::runPending);
    }
}
