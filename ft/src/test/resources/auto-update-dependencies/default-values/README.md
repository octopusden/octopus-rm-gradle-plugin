autoUpdateDependencies {
    component(name: 'platform-commons', projectProperty: 'pl-commons-version')
    pullRequest = true
    createJiraIssue = true
    component {
        name 'appserver'
        projectProperty 'as-server.version'
    }
}