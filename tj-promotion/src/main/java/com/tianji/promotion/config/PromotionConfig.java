package com.tianji.promotion.config;

import com.tianji.promotion.utils.MyLockAspect;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@Slf4j
public class PromotionConfig {

    @Bean
    public Executor generateExchangeCodeExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程池大小
        executor.setCorePoolSize(2);
        // 最大线程池大小
        executor.setMaxPoolSize(5);
        // 队列大小
        executor.setQueueCapacity(200);
        // 线程名称
        executor.setThreadNamePrefix("exchange-code-handler-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 初始化
        executor.initialize();
        return executor;
    }

    @Bean
    public Executor discountSolutionExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程池大小
        executor.setCorePoolSize(12);
        // 最大线程池大小
        executor.setMaxPoolSize(12);
        // 队列大小
        executor.setQueueCapacity(99999);
        // 线程名称
        executor.setThreadNamePrefix("discount-solution-calculator-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 初始化
        executor.initialize();
        return executor;
    }
}
