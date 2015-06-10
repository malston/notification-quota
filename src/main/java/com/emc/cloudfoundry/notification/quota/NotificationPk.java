package com.emc.cloudfoundry.notification.quota;

import java.io.Serializable;

import javax.persistence.Embeddable;

@Embeddable
public class NotificationPk implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String orgGuId;
	private String userGuid;

	public NotificationPk() {
	}

	public NotificationPk(String orgGuId, String userGuid) {
		this.orgGuId = orgGuId;
		this.userGuid = userGuid;
	}

	public String getOrgGuId() {
		return orgGuId;
	}

	public void setOrgGuId(String orgGuId) {
		this.orgGuId = orgGuId;
	}

	public String getUserGuid() {
		return userGuid;
	}

	public void setUserGuid(String userGuid) {
		this.userGuid = userGuid;
	}

}
