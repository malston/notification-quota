package com.emc.cloudfoundry.notification.quota;

import java.util.List;

import com.sendgrid.SendGridException;

public interface NotificationService {

	void sendNotification(String from, List<String> to, String messageBody);

	void sendSendGridNotification(String from, List<String> to, String messageBody) throws SendGridException;

}
