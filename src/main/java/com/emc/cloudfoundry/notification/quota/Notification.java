package com.emc.cloudfoundry.notification.quota;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;

import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

@Entity
@Table(name = "notifications")
public class Notification {

	@EmbeddedId
	private NotificationPk notificationId;

	@Column(nullable = false)
	private String email;

	@Lob
	@Column(nullable = false)
	private byte[] message;

	@Type(type = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
	@Column(nullable = false)
	private DateTime lastSent;

	public Notification() {
	}

	public Notification(NotificationPk notificationId, String email) {
		super();
		this.notificationId = notificationId;
		this.email = email;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public byte[] getMessage() {
		return message;
	}

	public void setMessage(byte[] message) {
		this.message = message;
	}

	public DateTime getLastSent() {
		return lastSent;
	}

	public void setLastSent(DateTime lastSent) {
		this.lastSent = lastSent;
	}

	public NotificationPk getNotificationId() {
		return notificationId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((email == null) ? 0 : email.hashCode());
		result = prime * result + ((lastSent == null) ? 0 : lastSent.hashCode());
		result = prime * result + ((message == null) ? 0 : message.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Notification other = (Notification) obj;
		if (email == null) {
			if (other.email != null)
				return false;
		} else if (!email.equals(other.email))
			return false;
		if (lastSent == null) {
			if (other.lastSent != null)
				return false;
		} else if (!lastSent.equals(other.lastSent))
			return false;
		if (message == null) {
			if (other.message != null)
				return false;
		} else if (!message.equals(other.message))
			return false;
		return true;
	}
}
