package com.logivations.jira.actions

import com.atlassian.greenhopper.service.rapid.view.RapidViewService
import com.atlassian.greenhopper.service.sprint.Sprint
import com.atlassian.greenhopper.service.sprint.SprintIssueService
import com.atlassian.greenhopper.service.sprint.SprintManager
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.event.issue.IssueEvent
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.UpdateIssueRequest
import com.atlassian.jira.project.version.Version
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.util.collect.MapBuilder
import com.onresolve.scriptrunner.runner.customisers.JiraAgileBean
import com.onresolve.scriptrunner.runner.customisers.WithPlugin
import org.apache.log4j.Level
import org.ofbiz.core.entity.GenericEntityException
import org.ofbiz.core.entity.GenericValue

import static com.atlassian.greenhopper.service.sprint.Sprint.State.ACTIVE
import static com.atlassian.greenhopper.service.sprint.Sprint.State.FUTURE
import static com.atlassian.jira.event.type.EventType.ISSUE_CREATED_ID
import static com.atlassian.jira.event.type.EventType.ISSUE_UPDATED_ID
import static java.util.Arrays.asList
import static java.util.EnumSet.of

log.setLevel(Level.DEBUG)

/**
 * Configurable section
 */

@WithPlugin("com.pyxis.greenhopper.jira")

@JiraAgileBean
RapidViewService rapidViewService

@JiraAgileBean
SprintIssueService sprintIssueService

@JiraAgileBean
SprintManager sprintManager


MutableIssue issue = issue

// W2MO projects: WMO, LNO, OR, PLTF, WH, FM
// RTDT id 14100
// LVSPR 10400

//def projectIdToBoardId = [10000L:7L, 12702L:45l, 12703L:7L, 12704L:46L, 12705L:48L, 12700L:47L]
def projectIdToBoardId = [12703L: [47L, 50L], 12704L: [46L], 12705L: [48L], 12700L: [47L], 13300L :[55L], 14100L : [66L], 10400 : [21L]]


def projectIds = projectIdToBoardId.keySet() as List

// has to be Jira user with right 'Schedule Issues'
String jiraUser = "administrator"

log.info "Issue processing" + issue

IssueEvent event = event
def eventTypeId = event.getEventTypeId()

// if it's an event we're interested in, log it
if (eventTypeId == ISSUE_UPDATED_ID) {
    List<GenericValue> changeItems = null;

    try {
        GenericValue changeLog = event.getChangeLog();
        changeItems = changeLog.getDelegator().findByAnd("ChangeItem", MapBuilder.build("group", changeLog.get("id")))
    } catch (GenericEntityException e) {
        System.out.println(e.getMessage());
    }

    log.info("number of changes: ${changeItems.size()}")
    changeItems.each { it ->
        GenericValue changeItem = (GenericValue) it
        String field = changeItem.getString("field")
        long projectId = issue.getProjectId()
        if (!projectIds.contains(projectId)) {
            log.info "Issue is not in one of the W2MO projects: WMO, AGVFM, LNO, OR, PLTF, WH"
            return
        }
        if (field.equalsIgnoreCase('Fix Version')) {
            log.info "Fix version changed"
            log.info changeItem
            String oldFixVersionValue = changeItem.getString("oldvalue")
            String newFixVersionValue = changeItem.getString("newvalue")
            log.info "Old value = ${oldFixVersionValue}, New value = ${newFixVersionValue}"
            handleFixVersionChangedEvent(rapidViewService, projectIdToBoardId, sprintManager, issue, sprintIssueService, jiraUser)
        } else if (field.equalsIgnoreCase('Sprint')) {
            log.info "Sprint changed"
            // the values hold every sprint of the issue, the closed ones too: "914, 923, 1211"
            Set<String> oldSprintIds = sprintIds(changeItem.getString("oldvalue"))
            Set<String> newSprintIds = sprintIds(changeItem.getString("newvalue"))
            List<String> addedSprintIds = (newSprintIds - oldSprintIds) as List
            log.info "Old sprints = ${oldSprintIds}, New sprints = ${newSprintIds}, Added = ${addedSprintIds}"
            if (addedSprintIds) {
                handleSprintChangedEvent(addedSprintIds.last(), issue, sprintManager, jiraUser, projectId)
            } else if (!hasOpenSprint(newSprintIds, sprintManager)) {
                log.info "Moved to backlog"
                updateIssueFixVersions(issue, jiraUser, null)
            }
        }

    }
} else if (eventTypeId == ISSUE_CREATED_ID) {
    handleFixVersionChangedEvent(rapidViewService, projectIdToBoardId, sprintManager, issue, sprintIssueService, jiraUser)
} else {
    log.info "Not a Create/Update Issue Event. No actions needed."
    return
}

private static Set<String> sprintIds(String changeValue) {
    return changeValue ? changeValue.tokenize(',')*.trim().findAll { it } as LinkedHashSet<String> : new LinkedHashSet<String>()
}

private static boolean hasOpenSprint(Set<String> sprintIds, SprintManager sprintManager) {
    return sprintIds.any { String id ->
        Sprint sprint = sprintManager.getSprint(Long.valueOf(id)).getValue()
        sprint && sprint.getState() in [ACTIVE, FUTURE]
    }
}

/**
 * Reverse of the sprint name built in processIssue: "<project key> <version>", or "WH-LNO <version>" for WH and LNO.
 * Returns null for a sprint of another board.
 */
private static String fixVersionNameOfSprint(String sprintName, String projectKey) {
    List<String> prefixes = ["${projectKey} ".toString()]
    if (['WH', 'LNO'].contains(projectKey)) {
        prefixes << 'WH-LNO '
    }
    String prefix = prefixes.find { sprintName.startsWith(it) }
    return prefix ? sprintName.substring(prefix.length()).trim() : null
}

private void handleSprintChangedEvent(String sprintId, MutableIssue issue, SprintManager sprintManager, String jiraUser, Long projectId) {
    Sprint sprint = sprintManager.getSprint(Long.valueOf(sprintId)).getValue()
    if (!sprint) {
        log.warn "No sprint with ID ${sprintId} found"
        return
    }
    def projectKey = issue.getProjectObject().getKey()
    String fixVersionName = fixVersionNameOfSprint(sprint.getName(), projectKey)
    if (!fixVersionName) {
        log.info "Sprint '${sprint.getName()}' does not belong to project ${projectKey}"
        return
    }
    Collection<Version> unreleasedFixVersions = ComponentAccessor.versionManager.getVersionsUnreleased(projectId, false)
    Version newFixVersion = unreleasedFixVersions.findByName(fixVersionName)
    if (!newFixVersion) {
        log.info "No unreleased Fix Version '${fixVersionName}' in project ${projectKey}"
        return
    }
    if (issue.getFixVersions()*.getName() == [newFixVersion.getName()]) {
        log.info "issue already has required Fix Version"
        return
    }
    updateIssueFixVersions(issue, jiraUser, newFixVersion)
}


private void handleFixVersionChangedEvent(RapidViewService rapidViewService, Map<Long, List<Long>> projectIdToBoardId, SprintManager sprintManager, Issue issue, SprintIssueService sprintIssueService, String jiraUser) {
    def loggedInUser = ComponentAccessor.getUserManager().getUserByKey(jiraUser)
    def projectId = issue.getProjectId()
    def rapidBoardIds = projectIdToBoardId.get(projectId)
    Collection<Sprint> boardSprints = new ArrayList<>();
    for (rapidBoardId in rapidBoardIds) {
        def view = rapidViewService.getRapidView(loggedInUser, rapidBoardId).getValue()
        if (!view) {
            log.warn("No view with this ID found")
            return
        }
        boardSprints.addAll(sprintManager.getSprintsForView(view.getId(), of(ACTIVE, FUTURE)).getValue())
    }
        log.info "board Sprints: " + boardSprints
        def fixVersion = issue.getFixVersions().find()
        if (boardSprints) {
            Sprint issueActiveSprint = sprintIssueService.getActiveOrFutureSprintForIssue(loggedInUser, issue).getValue().getOrNull()
            log.info " issue active sprint: " + issueActiveSprint
            processIssue(sprintIssueService, loggedInUser, fixVersion, issueActiveSprint, issue, boardSprints)
        }
}

private void updateIssueFixVersions(MutableIssue issue, String jiraUser, Version newFixVersion) {
    Collection<Version> versions = newFixVersion != null ? asList(newFixVersion) : new ArrayList<Version>()
    issue.setFixVersions(versions)
    log.info 'new fix versions' + issue.getFixVersions()
    def loggedInUser = ComponentAccessor.getUserManager().getUserByKey(jiraUser)
    def issueManager = ComponentAccessor.issueManager
    def updateIssueRequestBuilder = UpdateIssueRequest.builder()
    issueManager.updateIssue(loggedInUser, issue, updateIssueRequestBuilder.build())
}

private boolean processIssue(SprintIssueService sprintIssueService, ApplicationUser loggedInUser, Version fixVersion,
                             Sprint issueActiveFutureSprint, Issue issue, Collection<Sprint> activeFutureSprints) {
    if (fixVersion == null) {
        log.info "issue doesn`t have fixVersion "
        if (issueActiveFutureSprint) {
            log.info "Moving issue $issue to backlog"
            sprintIssueService.moveIssuesToBacklog(loggedInUser, [issue] as Collection)
        }
        return true
    } else {
        def  projectKey = issue.getProjectObject().getKey()
        def sprintName = """${projectKey} ${fixVersion.name}"""
        def relatedSprint = activeFutureSprints.findByName(sprintName)
        if(!relatedSprint && ['WH', 'LNO'].contains(projectKey)){
            sprintName = """WH-LNO ${fixVersion.name}"""
            relatedSprint = activeFutureSprints.findByName(sprintName)
        }
        log.info "related Sprint: " + relatedSprint
        if (relatedSprint) {
            // fix version is the same as active/future sprint
            if (!issueActiveFutureSprint || issueActiveFutureSprint.sequence != relatedSprint.sequence) {
                // if issue is not in active sprint
                log.info "Adding issue $issue to ${relatedSprint.name}"
                sprintIssueService.moveIssuesToSprint(loggedInUser, relatedSprint, [issue] as Collection)
            }
            return true
        } else {
            if (issueActiveFutureSprint) {
                log.info "Moving issue $issue to backlog"
                sprintIssueService.moveIssuesToBacklog(loggedInUser, [issue] as Collection)
                return true
            } else {
                log.info "No actions with issue $issue"
                return false
            }
        }
    }
}
