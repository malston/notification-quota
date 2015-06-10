package com.emc.cloudfoundry.notification.quota;

import org.springframework.data.repository.CrudRepository;

public interface NotificationRepository extends CrudRepository<Notification, NotificationPk> {

}
