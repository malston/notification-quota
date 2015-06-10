# notification-quota
Notifies org manager whenever their quota reaches a certain threshold.

This application uses a fork of the [cf-java-client](https://github.com/malston/cf-java-client) that is referenced in the pom file using the wonderful [jitpack](https://jitpack.io/) tool. The fork provides additional client support for getting the organization memory usage by calling the `/v2/organizations/{guid}/memory_usage` endpoint.

## Prequisites
You have to set the `PCF_USERNAME` to a user that has permissions to view all orgs and read the quotas from each org. Check the [UAA](http://docs.cloudfoundry.org/adminguide/uaa-user-management.html) documentation to make sure the user has the right permissions.

## Build and Deploy

Run locally
```
export PCF_SPACE=<space>
export PCF_USERNAME=<username>
export PCF_TARGET=https://api.<system_domain>
export PCF_ORG=<org>
export PCF_UAA_TARGET=http://uaa.<system_domain>
export PCF_PASSWORD=<password>
export SKIP_SSL_VALIDATION=true
```
```
mvn clean package; java -jar target/notification-quota-0.0.1-SNAPSHOT.jar
```

Run on cloud
Update the environment variables in the manifest.yml
```
cf create-service p-mysql 1000mb-dev notification-quota-db
```
```
cf push
cf logs notification-quota
```
