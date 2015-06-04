# notification-quota
Notifies org manager whenever their quota reaches a certain threshold.

## Prerequisites
This application uses a fork of the [cf-java-client](https://github.com/malston/cf-java-client) that you must build and deploy to your Maven repository. The fork provides additional client support for getting the organization memory usage by calling the `/v2/organizations/{guid}/memory_usage` endpoint.

