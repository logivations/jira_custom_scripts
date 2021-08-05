package com.logivations.jira.actions

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.event.issue.IssueEvent
import com.atlassian.jira.event.type.EventType
import com.atlassian.jira.issue.ModifiedValue
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.util.DefaultIssueChangeHolder
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.util.collect.MapBuilder
import com.atlassian.mail.Email
import org.ofbiz.core.entity.GenericEntityException
import org.ofbiz.core.entity.GenericValue

MutableIssue issue = issue
IssueEvent event = event
def eventTypeId = event.getEventTypeId()

// if it's an event we're interested in, log it
if (eventTypeId.equals(EventType.ISSUE_UPDATED_ID)) {
    log.warn("Issue ${issue.getKey()} has been created at ${issue.getCreated()}.")

            List<GenericValue> changeItems = null;

    try {
        GenericValue changeLog = event.getChangeLog();
        changeItems = changeLog.getDelegator().findByAnd("ChangeItem", MapBuilder.build("group",changeLog.get("id")))
    } catch (GenericEntityException e){
        System.out.println(e.getMessage());
    }

    log.warn("number of changes: ${changeItems.size()}")
    changeItems.each {it ->
        GenericValue changeItem = (GenericValue) it
        String field = changeItem.getString("field")
        String oldvalue = changeItem.getString("oldvalue")
        String newvalue = changeItem.getString("newvalue")
        StringBuilder fullstring = new StringBuilder();
        fullstring.append("Issue ")
        fullstring.append(issue.getKey())
        fullstring.append(" field ")
        fullstring.append(field)
        fullstring.append(" has been updated from ")
        fullstring.append(oldvalue)
        fullstring.append(" to ")
        fullstring.append(newvalue)
        log.warn("changes ${fullstring.toString()}")

        if("Responsible QA".equals(field)) {
            log.warn "Change items = ${changeItem}"
        log.warn "RESPONSIBLE QA field changed !!!!!!"
            sendEmailToResponsibleQa(issue, newvalue, oldvalue)
//            changeAssignee(changeItem, issue, user)
        }
    }
}


private void sendEmailToResponsibleQa(MutableIssue issue, String newUserKey, String oldUserKey) {
///// email sending

    ApplicationUser curr_user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser()
    ApplicationUser responsibleQaUser = ComponentAccessor.getUserManager().getUserByKey(newUserKey)
    if(responsibleQaUser == null){
        log.error "User ${newUserKey} is not found"
        log.error "Resp QA User '${responsibleQaUser}' is not found"
        return
    }
    else {
        log.warn "User ${responsibleQaUser.getUsername()} found with email ${responsibleQaUser.getEmailAddress()}"
    }

// Get a pointer to the resticted fields group
    def userUtil = ComponentAccessor.getUserUtil()
    def groupManager = ComponentAccessor.getGroupManager()
    def group = groupManager.getGroup("jira-testers")

    if (userUtil.getGroupsForUser(curr_user.name).contains(group)) {
        // If the user is in the restricted groups field then show the field(s)

        def mailServerManager = ComponentAccessor.getMailServerManager()
        def mailServer = mailServerManager.getDefaultSMTPMailServer()

        if (mailServer) {
            if (responsibleQaUser) {
                def email = new Email("${responsibleQaUser.getEmailAddress()}") // Set the TO address, optionally CC and BCC
                email.setSubject("You have been selected as Responsible QA for issue: '${issue.summary}' ")
                // todo: check the subject value
                String content = "<style>\n" +
                        "b.uppercase {\n" +
                        "    text-transform: uppercase;\n" +
                        "}\n" +
                        "</style>\n" +
                        "<b>${issue.issueType.name}</b> <a href='https://lvserv01.logivations.com/browse/${issue.key}' style='color: #3b73af'><b>${issue.key}</b></a>\n" +
                        "has been reported by <a href='https://lvserv01.logivations.com/secure/ViewProfile.jspa?name=${issue.reporter.name}' style='color: #3b73af'><b><i>${issue.reporter.displayName}</i></b></a>.\n" +
                        "<p><b>Summary</b>: <a href='https://lvserv01.logivations.com/browse/${issue.key}' style='color: #3b73af'><b>${issue.summary}</b></a>\n" +
                        "<p><b>Assignee</b>: <a href='https://lvserv01.logivations.com/secure/ViewProfile.jspa?name=${issue.assignee.name}' style='color: #3b73af'><b><i>${issue.assignee.displayName}</i></b></p></a>\n" +
                        "<p><b>Priority: </b><b class=uppercase>${issue.priority.name}</b></p>\n" +
                        "<p><b>Fix Version: </b><b>${issue.fixVersions}</b></p>\n" +
                        "<p><b>Affects Version: </b><b>${issue.affectedVersions}</b></p>\n" +
                        "<p><b>Description: </b></p>\n" +
                        "<p>${issue.description}</p>"
                email.setMimeType("text/html")
                //TODO: Set email's body
                email.setBody(content)
                mailServer.send(email)
            } else {
                log.error("No user found by ${responsibleQaUser}")
            }
        } else {
            log.error("No SMTP mail server defined")
        }

    }
    else {
        // get 'Responsible QA' field
        CustomField responsibleQaCustomField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_11804")
        ApplicationUser oldResponsibleQaUser = ComponentAccessor.getUserManager().getUserByKey(oldUserKey)
        def changeHolder = new DefaultIssueChangeHolder()
        responsibleQaCustomField.updateValue(null, issue, new ModifiedValue(responsibleQaUser, oldResponsibleQaUser), changeHolder)
    }
}




