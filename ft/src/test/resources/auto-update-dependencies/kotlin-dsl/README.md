autoUpdateDependencies {
    pullRequest = false
    component(name: 'platform-commons', projectProperty: 'pl-commons-version')
    component {
        name 'appserver'
        projectProperty 'as-server.version'
    }
}
