package com.emc.cloudfoundry.notification.quota;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.List;

import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.HttpProxyConfiguration;
import org.cloudfoundry.client.lib.RestLogCallback;
import org.cloudfoundry.client.lib.RestLogEntry;
import org.cloudfoundry.client.lib.domain.ApplicationStats;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudOrganization;
import org.cloudfoundry.client.lib.domain.CloudQuota;
import org.cloudfoundry.client.lib.domain.CloudService;
import org.cloudfoundry.client.lib.domain.CloudServiceOffering;
import org.cloudfoundry.client.lib.domain.CloudServicePlan;
import org.cloudfoundry.client.lib.domain.CloudSpace;
import org.cloudfoundry.client.lib.domain.InstanceStats;
import org.cloudfoundry.client.lib.tokens.TokensFile;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.DefaultOAuth2RefreshToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

@SpringBootApplication
public class NotificationQuotaApplication {

	@Parameter(names = { "-t", "--target" }, description = "Cloud Foundry target URL", required = true)
	private String target;

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

	public static void main(String[] args) {
		SpringApplication.run(NotificationQuotaApplication.class, args);
		NotificationQuotaApplication sample = new NotificationQuotaApplication();
		new JCommander(sample, args);
		sample.run();
	}

	private void run() {
		validateArgs();

		setupDebugLogging();

		CloudCredentials credentials = getCloudCredentials();
		CloudFoundryClient client = getCloudFoundryClient(credentials);

		displayCloudInfo(client);
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
		// out("\nInfo:");
		// out(client.getCloudInfo().getName());
		// out(client.getCloudInfo().getVersion());
		// out(client.getCloudInfo().getDescription());
		//
		// out("\nUsage:");
		// out(formatMBytes(client.getCloudInfo().getUsage().getTotalMemory()));
		// out(formatMBytes(client.getCloudInfo().getLimits().getMaxTotalMemory()));
		// out(client.getCloudInfo().getLimits().getMaxApps() + " apps");
		//
		// out("\nSpaces:");
		// for (CloudSpace space : client.getSpaces()) {
		// out(space.getName() + ":" + space.getOrganization().getName());
		// }
		//
		// out("\nOrgs:");
		int appCount = 0;
		int appInstanceCount = 0;
		for (CloudOrganization organization : client.getOrganizations()) {
			CloudOrganization org = client.getOrgByName(organization.getName(), true);
			// out(org.getName());
			int quotaMemoryLimit = Long.valueOf(org.getQuota().getMemoryLimit()).intValue();
			int memoryUsed = Long.valueOf(client.getMemoryUsageForOrg(org.getMeta().getGuid())).intValue();
			if (org.getQuota() != null) {
				out("Org " + org.getName() + " is using " + formatMBytes(memoryUsed) + " of "
						+ formatMBytes(quotaMemoryLimit) + ".");
				// out("\tQuota:");
				// out("\t\t" + org.getQuota().getName());
				// out("\t\tMemory Limit: " + org.getQuota().getMemoryLimit());
			}
			// }
			for (CloudSpace space : client.getSpaces()) {
				if (space.getOrganization().getName().equals(org.getName())) {
					// out("\nApplications:");
					int consumed = 0;
					for (CloudApplication app : client.getApplications()) {
						if (app.getSpace().getName().equals(space.getName())) {

							// out(app.getName() + ":");
							// out("\t" + app.getStaging().getBuildpackUrl());
							// out("\t" + app.getStaging().getCommand());
							// if (!app.getServices().isEmpty()) {
							// out("\tBound Services:");
							// for (String serviceName : app.getServices()) {
							// out("\t\t" + serviceName);
							// }
							// }
							int instances = app.getInstances();
							int memory = app.getMemory();
							consumed += (instances * memory);
							appCount++;
							appInstanceCount = appInstanceCount + instances;
							// out("\t" + formatMBytes(app.getMemory()));
							// ApplicationStats stats = client.getApplicationStats(app.getName());
							// for (InstanceStats instanceStats : stats.getRecords()) {
							// out("\t" + instanceStats.getUris());
							// out("\t" + instanceStats.getHost());
							// out("\t" + instanceStats.getPort());
							// out("\t" + instanceStats.getDiskQuota());
							// out("\tMemory: " + formatMBytes(Long.valueOf(instanceStats.getMemQuota()).intValue()));
							// out("\t" + instanceStats.getFdsQuota());
							// out("\t" + instanceStats.getUptime());
							//
							// InstanceStats.Usage usage = instanceStats.getUsage();
							// out("\t" + usage);
							// out("\t" + usage.getDisk());
							// out("\tMem Usage:" + formatMBytes(usage.getMem()));
							// out("\t" + usage.getTime().getTime());
							// }
						}
					}
					out("\tSpace " + space.getName() + " is using " + (consumed) + "M memory ("
							+ (100 * consumed / quotaMemoryLimit) + "%) of org quota");
				}
			}
		}
		out("You are running " + appCount + " apps in all orgs, with a total of " + appInstanceCount + " instances");
		// out("\nServices:");
		// for (CloudService service : client.getServices()) {
		// out(service.getName() + ":");
		// out("\t" + service.getLabel());
		// out("\t" + service.getPlan());
		// }
		//
		// out("\nService Offerings:");
		// for (CloudServiceOffering offering : client.getServiceOfferings()) {
		// out(offering.getLabel() + ":");
		// final String s = "\tPlans:";
		// out(s);
		// for (CloudServicePlan plan : offering.getCloudServicePlans()) {
		// out("\t\t" + plan.getName());
		// }
		// out("\t" + offering.getDescription());
		// }
		//
		// out("\nQuotas:");
		// for (CloudQuota quota : client.getQuotas()) {
		// out("Quota " + quota.getName() + " has a memory limit of " +
		// formatMBytes(Long.valueOf(quota.getMemoryLimit()).intValue()));
		// }
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
