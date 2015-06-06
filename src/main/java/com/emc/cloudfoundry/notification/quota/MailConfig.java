package com.emc.cloudfoundry.notification.quota;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.sendgrid.SendGrid;

/**
 * Mail sender configuration for sending emails.
 * Relies on the JavaMail API and Spring's JavaMail support.
 */
@Configuration
public class MailConfig {

	@Autowired
	private Environment environment;

	/**
	 * The Java Mail sender.
	 * It's not generally expected for mail sending to work in embedded mode.
	 * Since this mail sender is always invoked asynchronously, this won't cause problems for the developer.
	 */
	@Bean
	public JavaMailSender mailSender() {
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setDefaultEncoding("UTF-8");
		mailSender.setHost(environment.getProperty("mail.host"));
		mailSender.setPort(environment.getProperty("mail.port", Integer.class, 25));
		mailSender.setUsername(environment.getProperty("mail.username"));
		mailSender.setPassword(environment.getProperty("mail.password"));
		Properties properties = new Properties();
		properties.put("mail.smtp.auth", environment.getProperty("mail.smtp.auth", Boolean.class, false));
		properties.put("mail.smtp.starttls.enable", environment.getProperty("mail.smtp.starttls.enable", Boolean.class, false));
		mailSender.setJavaMailProperties(properties);
		return mailSender;
	}
	
	@Bean
	public SendGrid sendGrid() {
		return new SendGrid(environment.getProperty("mail.username"), environment.getProperty("mail.password"));
	}
	
}