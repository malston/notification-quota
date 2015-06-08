package org.cloudfoundry.client.lib.domain;

import java.util.List;

/**
 * @author Olivier Orand
 */
public class CloudUser extends CloudEntity{
	
	private String username;
	
	private List<CloudOrganization> organizations;
	
	private List<CloudOrganization> managedOrganizations;

	public CloudUser(Meta meta, String name, List<CloudOrganization> organizations) {
		super(meta, name);
		this.organizations = organizations;
	}

	public CloudUser(Meta meta, String name, String username, List<CloudOrganization> organizations, List<CloudOrganization> managedOrganizations) {
		super(meta, name);
		this.organizations = organizations;
		this.managedOrganizations = managedOrganizations;
	}
	
	public List<CloudOrganization> getOrganizations() {
		return organizations;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}
//
//	public List<String> getOrganizationRoles() {
//		return organizationRoles;
//	}
//
//	public void setOrganizationRoles(List<String> organizationRoles) {
//		this.organizationRoles = organizationRoles;
//	}

	public List<CloudOrganization> getManagedOrganizations() {
		return managedOrganizations;
	}

	public void setManagedOrganizations(List<CloudOrganization> managedOrganizations) {
		this.managedOrganizations = managedOrganizations;
	}

}
