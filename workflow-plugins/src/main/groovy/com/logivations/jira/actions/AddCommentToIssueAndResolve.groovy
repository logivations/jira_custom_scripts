

import com.atlassian.jira.bc.issue.IssueService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.ResolutionManager
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.ofbiz.FieldMap
import com.atlassian.jira.user.util.UserManager
import com.opensymphony.workflow.spi.WorkflowEntry
import org.apache.log4j.Level
import org.ofbiz.core.entity.GenericValue

log.setLevel(Level.DEBUG)
Issue issue = issue

def userManager = (UserManager)  ComponentAccessor.getUserManager()

def jiraUser = "kateryna.shuflat"

def loggedInUser = userManager.getUserByKey(jiraUser)
ComponentAccessor.jiraAuthenticationContext.setLoggedInUser(loggedInUser)


if (issue.summary == "Error in Adidas Server Log" && issue.description.contains("FileNotFoundException: /home/tomcat/from_manh/")) {
    IssueService issueService = ComponentAccessor.getIssueService()
    def issueInputParameters = issueService.newIssueInputParameters()
    def resolutionManager = ComponentAccessor.getComponent(ResolutionManager)
    issueInputParameters.with {
        setComment("Issue was automatically resolved by post function script")
        setResolutionId(resolutionManager.getResolutionByName("Done").id)
    }
    List<GenericValue> workflowEntries = ComponentAccessor.getOfBizDelegator().findByAnd("OSWorkflowEntry", FieldMap.build("id", issue.getWorkflowId()))
    for (GenericValue workflowEntry : workflowEntries) {
        if (workflowEntry.getInteger("state") == null || "0".equals(workflowEntry.getInteger("state").toString())) {
            workflowEntry.set("state", new Integer(WorkflowEntry.ACTIVATED));
            workflowEntry.store()
        }
    }
    // Action ID = 5 (Resolve Issue)
    IssueService.TransitionValidationResult validationResult = issueService.validateTransition(loggedInUser, issue.id, 5, issueInputParameters)
    if (validationResult.isValid()) {
        issueService.transition(loggedInUser, validationResult)
    } else {
        log.error validationResult.getErrorCollection().getErrorMessages()
        log.warn validationResult.getWarningCollection().getWarnings()
    }
}
