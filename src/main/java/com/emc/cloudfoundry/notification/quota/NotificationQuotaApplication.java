package com.emc.cloudfoundry.notification.quota;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
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
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.token.grant.password.ResourceOwnerPasswordResourceDetails;
import org.springframework.security.oauth2.common.AuthenticationScheme;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.DefaultOAuth2RefreshToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.sendgrid.SendGridException;

@SpringBootApplication
@EnableJpaRepositories
public class NotificationQuotaApplication {

	@Parameter(names = { "-t", "--target" }, description = "Cloud Foundry target URL", required = true)
	private String target;

	@Parameter(names = { "-ut", "--uaa" }, description = "UAA target URL", required = true)
	private String uaaTarget;

	@Parameter(names = { "-s", "--space" }, description = "Cloud Foundry space to target", required = true)
	private String spaceName;

	@Parameter(names = { "-o", "--organization" }, description = "Cloud Foundry organization to target")
	private String orgName;

	@Parameter(names = { "-u", "--username" }, description = "Username for login")
	private String username;

	@Parameter(names = { "-p", "--password" }, description = "Password for login")
	private String password;

	@Parameter(names = { "-a", "--accessToken" }, description = "OAuth access token")
	private String accessToken;

	@Parameter(names = { "-r", "--refreshToken" }, description = "OAuth refresh token")
	private String refreshToken;

	@Parameter(names = { "-ci", "--clientID" }, description = "OAuth client ID")
	private String clientID;

	@Parameter(names = { "-cs", "--clientSecret" }, description = "OAuth client secret")
	private String clientSecret;

	@Parameter(names = { "-tc", "--trustSelfSignedCerts" }, description = "Trust self-signed SSL certificates")
	private boolean trustSelfSignedCerts;

	@Parameter(names = { "-v", "--verbose" }, description = "Enable logging of requests and responses")
	private boolean verbose;

	@Parameter(names = { "-d", "--debug" }, description = "Enable debug logging of requests and responses")
	private boolean debug;
	
	@Autowired
	private Environment environment;
	
	@Autowired
	private NotificationService notificationService;

	public static void main(String[] args) {
		ApplicationContext context = SpringApplication.run(NotificationQuotaApplication.class, args);
		NotificationQuotaApplication application = context.getBean(NotificationQuotaApplication.class);
		new JCommander(application, args);
//		application.run();
	}
	
	@Scheduled(initialDelay = 2000, fixedRateString = "${pollingFrequency}")
	public void checkQuota() {
		CloudFoundryClient client = getCloudFoundryClient();
		UaaUserOperations uaaUserClient = getUaaUserClient();
		
		displayCloudInfo(client);
		
		for (CloudOrganization organization : client.getOrganizations()) {
			CloudOrganization org = client.getOrgByName(organization.getName(), true);
			if (org.getQuota() != null) {
				int memoryLimit = Long.valueOf(org.getQuota().getMemoryLimit()).intValue();
				int memoryUsed = Long.valueOf(client.getMemoryUsageForOrg(org.getMeta().getGuid())).intValue();
				int percentUsed = 100 * memoryUsed / memoryLimit;
				out("Org " + org.getName() + " is using " + formatMBytes(memoryUsed) + " of "
						+ formatMBytes(memoryLimit) + ".");
				if (percentUsed >= Integer.valueOf(environment.getProperty("threshold"))) {
					out("That is " + percentUsed + "% of their quota.");
					List<CloudUser> users = client.getOrgManagers(org.getMeta().getGuid());
					List<String> emailTos = new ArrayList<String>();
					if (users != null) {
						for (CloudUser user : users) {
							FilterRequest request = new FilterRequestBuilder().equals("id", user.getMeta().getGuid().toString()).build();
							ScimUser scimUser = uaaUserClient.getUsers(request).getResources().iterator().next();
							out("Sending email notification to Org Manager [" + scimUser.getGivenName() + " " + scimUser.getFamilyName() + "] of Org [" + org.getName() + "] with email ["+ scimUser.getPrimaryEmail() + "].");
							if (scimUser.getPrimaryEmail() != null) {
								emailTos.add(scimUser.getPrimaryEmail());
							}
						}
					}
					if (!emailTos.isEmpty() ) {
						try {
							notificationService.sendSendGridNotification("malston@pivotal.io", emailTos, "Test");
						} catch (SendGridException e) {
							error("Email could not be sent: " + e.getMessage());
						}
					}
				}
			}
		}
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

	private void run() {
		validateArgs();

		setupDebugLogging();

		CloudFoundryClient client = getCloudFoundryClient();
		
		displayCloudInfo(client);
	}

	private CloudFoundryClient getCloudFoundryClient() {
		CloudCredentials credentials = getCloudCredentials();
		
		return getCloudFoundryClient(credentials);
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

	private void displayCloudInfo(CloudFoundryClient client) {
		int appCount = 0;
		int appInstanceCount = 0;
		for (CloudOrganization organization : client.getOrganizations()) {
			CloudOrganization org = client.getOrgByName(organization.getName(), true);
			int quotaMemoryLimit = Long.valueOf(org.getQuota().getMemoryLimit()).intValue();
			int memoryUsed = Long.valueOf(client.getMemoryUsageForOrg(org.getMeta().getGuid())).intValue();
			if (org.getQuota() != null) {
				out("Org " + org.getName() + " is using " + formatMBytes(memoryUsed) + " of "
						+ formatMBytes(quotaMemoryLimit) + ".");
			}
			int quotaUsed = 0;
			for (CloudSpace space : client.getSpaces()) {
				if (space.getOrganization().getName().equals(org.getName())) {
					int consumed = 0;
					for (CloudApplication app : client.getApplications()) {
						if (app.getSpace().getName().equals(space.getName())) {
							int instances = app.getInstances();
							int memory = app.getMemory();
							consumed += (instances * memory);
							appCount++;
							appInstanceCount = appInstanceCount + instances;
						}
					}
					quotaUsed = 100 * consumed / quotaMemoryLimit;
					out("\tSpace " + space.getName() + " is using " + (consumed) + "M memory ("
							+ quotaUsed + "%) of org quota");
				}
			}
		}
		out("You are running " + appCount + " apps in all orgs, with a total of " + appInstanceCount + " instances");
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
