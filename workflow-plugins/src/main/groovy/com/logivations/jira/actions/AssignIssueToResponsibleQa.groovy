package com.logivations.jira.actions

import com.atlassian.jira.bc.projectroles.ProjectRoleService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.MutableIssue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.project.Project
import com.atlassian.jira.security.roles.ProjectRole
import com.atlassian.jira.security.roles.ProjectRoleManager
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.util.SimpleErrorCollection

MutableIssue issue = issue

String jiraUser = "bohdan.petrovskyy"

// get 'Responsible QA' custom field
CustomField responsibleQaCustomField = ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_11804")
ApplicationUser responsibleQaValue = (ApplicationUser) issue.getCustomFieldValue(responsibleQaCustomField)
if (responsibleQaValue != null && !responsibleQaValue.name.contains("Not Available")) {
	issue.setAssignee(responsibleQaValue)
} else {
	def projectRoleService = ComponentAccessor.getComponent(ProjectRoleService)
	def projectRoleManager = ComponentAccessor.getComponent(ProjectRoleManager)
	ProjectRole projectRoleObject = projectRoleManager.getProjectRole("Lead QA")
	Project project = issue.getProjectObject()
	def errorCollection = new SimpleErrorCollection()
	def applicationUser = ComponentAccessor.getUserManager().getUserByKey(jiraUser)
	def actors = projectRoleService.getProjectRoleActors(applicationUser, projectRoleObject, project, errorCollection)
	if (actors == null) {
		log.warn "projectRoleObject = " + projectRoleObject
		log.warn "errorCollection = " + errorCollection
		log.warn "project = " + project
		return
	}
	ApplicationUser leadQaUser = (ApplicationUser) actors.getUsers().toArray()[0]
	issue.setAssignee(leadQaUser)
}

