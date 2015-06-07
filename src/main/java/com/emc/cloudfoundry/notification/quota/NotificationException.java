package com.emc.cloudfoundry.notification.quota;

@SuppressWarnings("serial")
public class NotificationException extends RuntimeException {

    /**
     * Constructs an <code>NotificationException</code> with the specified
     * message.
     *
     * @param msg the detail message
     */
    public NotificationException(String msg) {
        super(msg);
    }

    /**
     * Constructs an <code>NotificationException</code> with the specified
     * message and root cause.
     *
     * @param msg the detail message
     * @param t root cause
     */
    public NotificationException(String msg, Throwable t) {
        super(msg, t);
    }

}
