package com.emc.cloudfoundry.notification.quota;

import java.util.List;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sendgrid.SendGrid;
import com.sendgrid.SendGridException;

/**
 * A NotificationService implementation that sends email notifications asynchronously in a separate thread.
 * Relies on Spring's @Async support enabled by AspectJ for the asynchronous behavior.
 * Uses a http://www.stringtemplate.org/ to generate the notification mail text from a template.
 */
@Service
public class AsyncMailNotificationService implements NotificationService {

	private final MailSender mailSender;

	private final SendGrid sendGrid;
	
	private final NotificationRepository notificationRepository;

	private final Integer numberOfHoursBeforeResend;
	
	private final boolean useSendGrid;
	
	private final String subject;
	
	/**
	 * Creates the AsyncMailNotificationService.
	 * @param mailSender the object that actually does the mail delivery using the JavaMail API.
	 */
	@Autowired
	public AsyncMailNotificationService(Environment environment, MailSender mailSender, SendGrid sendGrid, NotificationRepository notificationRepository) {
		this.mailSender = mailSender;
		this.sendGrid = sendGrid;
		this.notificationRepository = notificationRepository;
		this.numberOfHoursBeforeResend = environment.getProperty("numberOfHoursBeforeResend", Integer.class);
		this.useSendGrid = (environment.getProperty("mail.host").equals("")) ? true : false;
		this.subject = environment.getProperty("mail.subject");
	}
	
	@Override
	public void sendNotification(String from, List<String> to, String message) {
		for (String user : to) {
			Notification notification = notificationRepository.findByEmail(user);
			boolean shouldNotify = true;
			if (notification != null) {
				shouldNotify = notification.getLastSent().plusHours(numberOfHoursBeforeResend).isBefore(DateTime.now());
			} else {
				notification = new Notification();
				notification.setEmail(user);
			}
			if (shouldNotify) {
				System.out.println("Sending notification: " + message + " to : " + notification.getEmail() + " last sent at " + notification.getLastSent() + " shouldResend: " + shouldNotify);
				if (useSendGrid)
					try {
						sendGrid(from, user, message, notification);
					} catch (SendGridException e) {
						throw new NotificationException(e.getMessage(), e.getCause());
					}
				else
					send(from, user, message, notification);
			}
		}
	}

	// internal helpers

	@Async
	@Transactional
	private void send(String from, String to, String text, Notification notification) {
		SimpleMailMessage mailMessage = createMailMessage(from, to, text);
		mailSender.send(mailMessage);
		notification.setLastSent(DateTime.now());
		notification.setMessage(text.getBytes());
		notificationRepository.save(notification);
	}
	
	@Async
	@Transactional
	private void sendGrid(String from, String to, String text, Notification notification) throws SendGridException {
		SendGrid.Email mailMessage = createSendGridMessage(from, to, text);
		sendGrid.send(mailMessage);
		notification.setLastSent(DateTime.now());
		notification.setMessage(text.getBytes());
		notificationRepository.save(notification);
	}
	
	private SimpleMailMessage createMailMessage(String from, String to, String text) {
		SimpleMailMessage mailMessage = new SimpleMailMessage();
		mailMessage.setFrom(from);
		mailMessage.setTo(to);
		mailMessage.setSubject(subject);
		mailMessage.setText(text);
		return mailMessage;
	}
	
	private SendGrid.Email createSendGridMessage(String from, String to, String text) {
		SendGrid.Email mailMessage = new SendGrid.Email();
		mailMessage.setFrom(from);
		mailMessage.setTo(new String[] { to });
		mailMessage.setSubject(subject);
		mailMessage.setText(text);
		return mailMessage;
	}	
}