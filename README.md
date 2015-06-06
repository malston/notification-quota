# notification-quota
Notifies org manager whenever their quota reaches a certain threshold.

## Prerequisites
This application uses a fork of the [cf-java-client](https://github.com/malston/cf-java-client) that you must build and install into your local Maven repository. The fork provides additional client support for getting the organization memory usage by calling the `/v2/organizations/{guid}/memory_usage` endpoint.

## Build and Deploy

Example #1
```
mvn clean package; java -jar target/notification-quota-0.0.1-SNAPSHOT.jar -t https://api.$CF_SYSTEM_DOMAIN -s $CF_SPACE -u $CF_USER -p $CF_PASSWORD -ut http://uaa.$CF_SYSTEM_DOMAIN -tc
```

