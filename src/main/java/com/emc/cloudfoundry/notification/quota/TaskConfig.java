package com.emc.cloudfoundry.notification.quota;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Asynchronous task scheduling and execution configuration.
 * We apply compile-time AspectJ-advice to enable {@link Async} methods to run in separate threads.
 * The Executor that carries out asynchronous task execution is a {@link ThreadPoolTaskExecutor}.
 * It is used to execute {@link Async} methods as well as handle asynchronous work initiated by Spring Integration. 
 */
@Configuration
@EnableAsync(mode=AdviceMode.ASPECTJ)
@EnableScheduling
public class TaskConfig implements AsyncConfigurer, SchedulingConfigurer {

	//implementing AsyncConfigurer
	@Override
	public Executor getAsyncExecutor() {
		return taskExecutor();
	}

	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return asyncUncaughtExceptionHandler();
	}
	
	//implementing SchedulingConfigurer
	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.setScheduler(scheduledThreadPoolExecutor());
	}

	@Bean
	public AsyncUncaughtExceptionHandler asyncUncaughtExceptionHandler() {
		return new SimpleAsyncUncaughtExceptionHandler();
	}

	/**
	 * The asynchronous task executor used by the Greenhouse application.
	 */
	@Bean
	public Executor taskExecutor() {
		return new ThreadPoolTaskExecutor();
	}
	
	@Bean(destroyMethod = "shutdown")
	public Executor scheduledThreadPoolExecutor() {
		return Executors.newScheduledThreadPool(25);
	}

}