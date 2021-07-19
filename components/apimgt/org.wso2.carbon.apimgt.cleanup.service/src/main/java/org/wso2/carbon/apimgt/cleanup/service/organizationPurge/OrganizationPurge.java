package org.wso2.carbon.apimgt.cleanup.service.organizationPurge;

import javax.ws.rs.core.Response;

public interface OrganizationPurge {
      Response deleteOrganization (String organizationId);
}
