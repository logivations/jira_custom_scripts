package com.logivations.jira.actions

import java.util.regex.Matcher
import java.util.regex.Pattern

String issueSummary = issue.getSummary()
Pattern pattern = Pattern.compile("\\p{IsCyrillic}+")
Matcher matcher = pattern.matcher(issueSummary)
if(matcher.find()){
    return false
}
return true