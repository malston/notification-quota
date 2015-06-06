package com.emc.cloudfoundry.notification.quota;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.stringtemplate.v4.ST;

import com.sendgrid.SendGrid;
import com.sendgrid.SendGridException;

/**
 * A MailInviteService implementation that sends email invites asynchronously in a separate thread.
 * Relies on Spring's @Async support enabled by AspectJ for the asynchronous behavior.
 * Uses a http://www.stringtemplate.org/ to generate the notification mail text from a template.
 */
@Service
public class AsyncMailNotificationService implements NotificationService {

	private final MailSender mailSender;

	private final SendGrid sendGrid;
	
	/**
	 * Creates the AsyncMailNotificationService.
	 * @param mailSender the object that actually does the mail delivery using the JavaMail API.
	 */
	@Autowired
	public AsyncMailNotificationService(MailSender mailSender, SendGrid sendGrid) {
		this.mailSender = mailSender;
		this.sendGrid = sendGrid;
	}
	
	@Override
	public void sendNotification(String from, List<String> to, String messageBody) {
		ST textTemplate = new ST(from);
		textTemplate.add("account", from);
		ST bodyTemplate = new ST(messageBody);
		for (String user : to) {
			bodyTemplate.add("user", user);
			textTemplate.add("body",  bodyTemplate.render());
			send(from, user, textTemplate.render());
		}
	}

	@Override
	public void sendSendGridNotification(String from, List<String> to, String messageBody) {
		SendGrid.Email email = new SendGrid.Email();
		String[] toArr = new String[to.size()];
		email.setTo(to.toArray(toArr));
		email.setFrom(from);
		email.setSubject("PCF org about to exeed quota");
		ST textTemplate = new ST(from);
		textTemplate.add("account", from);
		ST bodyTemplate = new ST(messageBody);
		for (String user : to) {
			bodyTemplate.add("user", user);
			textTemplate.add("body",  bodyTemplate.render());
			email.setText(textTemplate.render());
			try {
				sendGrid.send(email);
			} catch (SendGridException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	// internal helpers
	
	@Async
	@Transactional
	private void send(String from, String to, String text) {
		SimpleMailMessage mailMessage = createMailMessage(from, to, text);
		mailSender.send(mailMessage);
	}
	
	private SimpleMailMessage createMailMessage(String from, String to, String text) {
		SimpleMailMessage mailMessage = new SimpleMailMessage();
		mailMessage.setFrom(from);
		mailMessage.setTo(to);
		mailMessage.setSubject("Pivotal CF Quota Alert");
		mailMessage.setText(text);
		return mailMessage;
	}
	
}