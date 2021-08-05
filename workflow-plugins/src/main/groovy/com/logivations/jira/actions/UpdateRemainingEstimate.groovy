package com.logivations.jira.actions

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.ModifiedValue
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.index.IssueIndexingService
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder
import com.atlassian.jira.util.ImportUtils
import org.apache.log4j.Level

import static com.atlassian.jira.event.type.EventDispatchOption.DO_NOT_DISPATCH

log.setLevel(Level.DEBUG)
log.warn "Event executed"
MutableIssue issue = issue

Double total = null;
def spentTime = issue.getTimeSpent()
log.warn "spentTime =" + spentTime
def originalEstimate = issue.getOriginalEstimate()
log.warn "originalEstimate =" + originalEstimate
if (spentTime != null) {
    def totalTimeRemaining = originalEstimate - spentTime
    total = totalTimeRemaining > 0 ? totalTimeRemaining / 3600 : 0
} else if (originalEstimate != null) {
    total = originalEstimate / 3600
}
log.warn "total =" + total
if (total != null) {
    log.warn "inside total block"
    CustomField w2moRemainingEstimateCustomField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_11901")
    def oldW2moRemainingEstimateValue = w2moRemainingEstimateCustomField.getValue(issue)
    log.warn oldW2moRemainingEstimateValue
    log.warn w2moRemainingEstimateCustomField

    def remainingEstimate = issue.getEstimate() / 3600
    if (total > remainingEstimate) {
        String jiraUser = "administrator"
        def loggedInUser = ComponentAccessor.getUserManager().getUserByKey(jiraUser)
        double newRemainingEstimate = total * 3600L
        issue.setEstimate((long) newRemainingEstimate)
        ComponentAccessor.getIssueManager().updateIssue(loggedInUser, issue, DO_NOT_DISPATCH, false)
    }

    def changeHolder = new DefaultIssueChangeHolder()
    w2moRemainingEstimateCustomField.updateValue(null, issue, new ModifiedValue(oldW2moRemainingEstimateValue, total), changeHolder)

    def issueIndexingService = ComponentAccessor.getComponent(IssueIndexingService)
    boolean wasIndexing = ImportUtils.isIndexIssues()
    ImportUtils.setIndexIssues(true)
    log.warn("was indexing ${wasIndexing}")
    log.warn("Reindex issue ${issue.key} ${issue.id}")
    issueIndexingService.reIndex(issue)
    log.warn("was indexing ${wasIndexing}")
    ImportUtils.setIndexIssues(wasIndexing)
}