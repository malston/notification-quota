package com.emc.cloudfoundry.notification.quota;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.HttpProxyConfiguration;
import org.cloudfoundry.client.lib.RestLogCallback;
import org.cloudfoundry.client.lib.RestLogEntry;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudOrganization;
import org.cloudfoundry.client.lib.domain.CloudSpace;
import org.cloudfoundry.client.lib.domain.CloudUser;
import org.cloudfoundry.client.lib.tokens.TokensFile;
import org.cloudfoundry.identity.uaa.api.UaaConnectionFactory;
import org.cloudfoundry.identity.uaa.api.common.UaaConnection;
import org.cloudfoundry.identity.uaa.api.common.model.expr.FilterRequest;
import org.cloudfoundry.identity.uaa.api.common.model.expr.FilterRequestBuilder;
import org.cloudfoundry.identity.uaa.api.user.UaaUserOperations;
import org.cloudfoundry.identity.uaa.rest.SearchResults;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.token.grant.password.ResourceOwnerPasswordResourceDetails;
import org.springframework.security.oauth2.common.AuthenticationScheme;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.DefaultOAuth2RefreshToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STRawGroupDir;

@SpringBootApplication
public class Application {

	@Value("#{environment.PCF_TARGET}")
	private String target;

	@Value("#{environment.PCF_UAA_TARGET}")
	private String uaaTarget;

	@Value("#{environment.PCF_SPACE}")
	private String spaceName;

	@Value("#{environment.PCF_ORG}")
	private String orgName;

	@Value("#{environment.PCF_USERNAME}")
	private String username;

	@Value("#{environment.PCF_PASSWORD}")
	private String password;

	@Value("#{environment.PCF_UAA_ACCESS_TOKEN}")
	private String accessToken;

	@Value("#{environment.PCF_UAA_REFRESH_TOKEN}")
	private String refreshToken;

	@Value("#{environment.PCF_UAA_CLIENT_ID}")
	private String clientID;

	@Value("#{environment.PCF_UAA_CLIENT_SECRET}")
	private String clientSecret;

	@Value("#{environment.SKIP_SSL_VALIDATION}")
	private boolean trustSelfSignedCerts;

	@Value("${environment.VERBOSE:false}")
	private boolean verbose;

	@Value("${environment.DEBUG:false}")
	private boolean debug;

	@Autowired
	private Environment environment;

	@Autowired
	private NotificationService notificationService;

	public static void main(String[] args) {
		ApplicationContext context = new SpringApplicationBuilder(Application.class)
				.initializers(new WebApplicationInitializer()).application().run(args);

		Application application = context.getBean(Application.class);

		application.validateArgs();
		application.setupDebugLogging();
	}

	@Scheduled(initialDelay = 2000, fixedRateString = "${pollingFrequency}")
	public void checkQuota() {
		CloudFoundryOperations client = getCloudFoundryClient();

		for (CloudOrganization organization : client.getOrganizations()) {
			// Need to refetch an org to get all its values
			CloudOrganization org = client.getOrgByName(organization.getName(), true);
			if (org.getQuota() != null) {
				STGroup g = new STRawGroupDir("templates");
				ST notificationTemplate = g.getInstanceOf("notification");
				notificationTemplate.add("from", "The PCF Ops Team");
				UUID orgGuid = org.getMeta().getGuid();
				int memoryLimit = Long.valueOf(org.getQuota().getMemoryLimit()).intValue();
				int memoryUsed = Long.valueOf(client.getMemoryUsageForOrg(orgGuid).toString()).intValue();
				int percentUsed = 100 * memoryUsed / memoryLimit;
				out("Org " + org.getName() + " is using " + formatMBytes(memoryUsed) + " of "
						+ formatMBytes(memoryLimit) + ".");
				out("That is " + percentUsed + "% of their quota.");
				int quotaMemoryLimit = memoryLimit;
				if (percentUsed >= Integer.valueOf(environment.getProperty("threshold"))) {
					notificationTemplate.add("orgName", org.getName());
					notificationTemplate.add("memoryUsed", formatMBytes(memoryUsed));
					notificationTemplate.add("quotaMemoryLimit", formatMBytes(memoryLimit));
					notificationTemplate.add("percentUsed", percentUsed);
					List<ScimUser> owners = this.findOrgOwners(org, notificationTemplate);
					ST spaceMessageTemplate = createSpaceUsageMessage(client, org, quotaMemoryLimit);
					notificationTemplate.add("spaceQuotaBody", spaceMessageTemplate.render());
					for (ScimUser owner : owners) {
						ArrayList<String> ownerEmails = new ArrayList<String>();
						ownerEmails.add(owner.getPrimaryEmail());
						notificationService.sendNotification(orgGuid.toString(), owner.getId(), "pcfops@emc.com", ownerEmails, notificationTemplate.render());
					}
				}
			}
		}
	}

	private List<ScimUser> findOrgOwners(CloudOrganization org, ST notificationTemplate) {
		CloudFoundryOperations client = getCloudFoundryClient();
		UaaUserOperations uaaUserClient = getUaaUserClient();

		List<CloudUser> users = client.getOrgManagers(org.getMeta().getGuid());
		List<ScimUser> orgManagers = new ArrayList<ScimUser>();
		if (users != null) {
			for (CloudUser user : users) {
				out("Lookup user: '" + user.getMeta().getGuid().toString() + "' from UAA.");
				FilterRequest request = new FilterRequestBuilder().equals("id",
						user.getMeta().getGuid().toString()).build();
				SearchResults<ScimUser> results = null;
				try {
					results = uaaUserClient.getUsers(request);
				} catch (Exception e) {
					throw new NotificationException(e.getMessage(), e);
				}
				if (results != null) {
					ScimUser scimUser = results.getResources().iterator().next();
					notificationTemplate.add("givenName", scimUser.getGivenName());
					if (scimUser.getPrimaryEmail() != null) {
						orgManagers.add(scimUser);
					}
				} else {
					throw new NotificationException("Could not find user with guid: '" + user.getMeta().getGuid().toString() + "'");
				}
				// out("Sending email notification to Org Manager [" + scimUser.getGivenName() + " " +
				// scimUser.getFamilyName() + "] of Org [" + org.getName() + "] with email ["+
				// scimUser.getPrimaryEmail() + "].");
			}
		}
		return orgManagers;
	}

	private CloudFoundryOperations getCloudFoundryClient() {
		CloudCredentials credentials = getCloudCredentials();

		return getCloudFoundryClient(credentials);
	}

	private UaaUserOperations getUaaUserClient() {
		URL uaaHost = getTargetURL(uaaTarget);
		CloudCredentials cfCredentials = getCloudCredentials();
		ResourceOwnerPasswordResourceDetails credentials = new ResourceOwnerPasswordResourceDetails();
		credentials.setAccessTokenUri(uaaTarget + "/oauth/token");
		credentials.setClientAuthenticationScheme(AuthenticationScheme.header);
		credentials.setClientId(cfCredentials.getClientId());
		credentials.setClientSecret(cfCredentials.getClientSecret());
		credentials.setUsername(cfCredentials.getEmail());
		credentials.setPassword(cfCredentials.getPassword());
		UaaConnection connection = UaaConnectionFactory.getConnection(uaaHost, credentials);
		UaaUserOperations operations = connection.userOperations();
		return operations;
	}

	private void setupDebugLogging() {
		if (debug) {
			System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
			System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
			System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG");
			System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "ERROR");
		}
	}

	private CloudCredentials getCloudCredentials() {
		CloudCredentials credentials;

		if (username != null && password != null) {
			if (clientID == null) {
				credentials = new CloudCredentials(username, password);
			} else {
				credentials = new CloudCredentials(username, password, clientID, clientSecret);
			}
		} else if (accessToken != null && refreshToken != null) {
			DefaultOAuth2RefreshToken refresh = new DefaultOAuth2RefreshToken(refreshToken);
			DefaultOAuth2AccessToken access = new DefaultOAuth2AccessToken(accessToken);
			access.setRefreshToken(refresh);

			if (clientID == null) {
				credentials = new CloudCredentials(access);
			} else {
				credentials = new CloudCredentials(access, clientID, clientSecret);
			}
		} else {
			final TokensFile tokensFile = new TokensFile();
			final OAuth2AccessToken token = tokensFile.retrieveToken(getTargetURI(target));

			if (clientID == null) {
				credentials = new CloudCredentials(token);
			} else {
				credentials = new CloudCredentials(token, clientID, clientSecret);
			}
		}

		return credentials;
	}

	private CloudFoundryClient getCloudFoundryClient(CloudCredentials credentials) {
		out("Connecting to Cloud Foundry target: " + target);

		CloudFoundryClient client = new CloudFoundryClient(credentials, getTargetURL(target),
				(HttpProxyConfiguration) null, trustSelfSignedCerts);

		if (verbose) {
			client.registerRestLogListener(new SampleRestLogCallback());
		}

		if (username != null) {
			client.login();
		}

		return client;
	}

	private void validateArgs() {
		if ((username != null || password != null) && (accessToken != null || refreshToken != null)) {
			error("username/password and accessToken/refreshToken options can not be used together");
		}

		if (optionsNotPaired(username, password)) {
			error("--username and --password options must be provided together");
		}

		if (optionsNotPaired(accessToken, refreshToken)) {
			error("--accessToken and --refreshToken options must be provided together");
		}

		if (optionsNotPaired(clientID, clientSecret)) {
			error("--clientID and --clientSecret options must be provided together");
		}
	}

	private boolean optionsNotPaired(String first, String second) {
		if (first != null || second != null) {
			if (first == null || second == null) {
				return true;
			}
		}
		return false;
	}

	private ST createSpaceUsageMessage(CloudFoundryOperations client, CloudOrganization org, int quotaMemoryLimit) {
		int appCount = 0;
		int appInstanceCount = 0;
		int quotaUsed = 0;
		StringBuffer spaceBody = new StringBuffer();
		for (CloudSpace space : client.getSpaces()) {
			if (space.getOrganization().getName().equals(org.getName())) {
				int consumed = 0;
				for (CloudApplication app : client.getApplications()) {
//					if (app.getSpace().getName().equals(space.getName())
//							&& app.getSpace().getOrganization().getName().equals(org.getName())) {
					if (app.getSpace().getName().equals(space.getName())) {
						int instances = app.getInstances();
						int memory = app.getMemory();
						consumed += (instances * memory);
						appCount++;
						appInstanceCount = appInstanceCount + instances;
					}
				}
				quotaUsed = 100 * consumed / quotaMemoryLimit;
				spaceBody.append("* Space ").append(space.getName()).append(" is using ").append(consumed)
						.append("M (").append(quotaUsed).append("%) of the org's memory quota.\n");
			}
		}
		StringBuffer messageBody = new StringBuffer();
		messageBody.append("\nThere are ").append(appCount).append(" apps running inside this org with a total of ")
				.append(appInstanceCount).append(" instances.\n\n");
		messageBody.append(spaceBody);
		ST spaceMessageTemplate = new ST(messageBody.toString());
		return spaceMessageTemplate;
	}

	private URL getTargetURL(String target) {
		try {
			return getTargetURI(target).toURL();
		} catch (MalformedURLException e) {
			error("The target URL is not valid: " + e.getMessage());
		}

		return null;
	}

	private URI getTargetURI(String target) {
		try {
			return new URI(target);
		} catch (URISyntaxException e) {
			error("The target URL is not valid: " + e.getMessage());
		}

		return null;
	}

	private void out(String s) {
		System.out.println(s);
	}

	private void error(String message) {
		out(message);
		System.exit(1);
	}

	private static class SampleRestLogCallback implements RestLogCallback {
		@Override
		public void onNewLogEntry(RestLogEntry logEntry) {
			System.out.println(String.format("REQUEST: %s %s", logEntry.getMethod(), logEntry.getUri()));
			System.out.println(String.format("RESPONSE: %s %s %s", logEntry.getHttpStatus().toString(),
					logEntry.getStatus(), logEntry.getMessage()));
		}
	}

	public static String formatMBytes(int size) {
		int g = size / 1024;

		DecimalFormat dec = new DecimalFormat("0");

		if (g > 1) {
			return dec.format(g).concat("G");
		} else {
			return dec.format(size).concat("M");
		}
	}

	public static String formatBytes(double size) {
		double k = size / 1024.0;
		double m = k / 1024.0;
		double g = m / 1024.0;

		DecimalFormat dec = new DecimalFormat("0");

		if (g > 1) {
			return dec.format(g).concat("G");
		} else if (m > 1) {
			return dec.format(m).concat("M");
		} else if (k > 1) {
			return dec.format(k).concat("K");
		} else {
			return dec.format(size).concat("B");
		}
	}

}
