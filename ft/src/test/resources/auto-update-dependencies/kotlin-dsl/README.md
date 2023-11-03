autoUpdateDependencies {
    pullRequest = false
    component(name: 'platform-utils', projectProperty: 'pl-utils-version')
    component {
        name 'myapp'
        projectProperty 'my-app.version'
    }
}
