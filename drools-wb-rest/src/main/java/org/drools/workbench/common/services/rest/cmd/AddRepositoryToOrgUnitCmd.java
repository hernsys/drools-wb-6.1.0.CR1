package org.drools.workbench.common.services.rest.cmd;

import org.drools.workbench.common.services.rest.JobRequestHelper;
import org.kie.internal.executor.api.CommandContext;
import org.kie.workbench.common.services.shared.rest.AddRepositoryToOrganizationalUnitRequest;
import org.kie.workbench.common.services.shared.rest.JobRequest;
import org.kie.workbench.common.services.shared.rest.JobResult;
import org.kie.workbench.common.services.shared.rest.JobStatus;

public class AddRepositoryToOrgUnitCmd extends AbstractJobCommand {

    @Override
    public JobResult internalExecute(CommandContext ctx, JobRequest request) throws Exception {
        JobRequestHelper helper = getHelper(ctx);
        AddRepositoryToOrganizationalUnitRequest jobRequest = (AddRepositoryToOrganizationalUnitRequest) request;

        JobResult result = null;
        try { 
            result = helper.addRepositoryToOrganizationalUnit( jobRequest.getJobId(), jobRequest.getOrganizationalUnitName(), jobRequest.getRepositoryName() );
        } finally { 
            JobStatus status = result != null ? result.getStatus() : JobStatus.SERVER_ERROR;
            logger.debug( "-----addRepositoryToOrganizationalUnit--- , OrganizationalUnit name: {}, repository name: {} [{}]", 
                    jobRequest.getOrganizationalUnitName(), jobRequest.getRepositoryName(), status );
        }
        return result;
    }
}
