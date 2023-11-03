autoUpdateDependencies {
    component(name: 'platform-utils', projectProperty: 'pl-utils-version')
    pullRequest = true
    createJiraIssue = true
    component {
        name 'myapp'
        projectProperty 'my-app.version'
    }
}