autoUpdateDependencies {
    component(name: 'platform-commons', projectProperty: 'pl-commons-version', versionRange: "(1,)")
    component {
        name 'appserver'
        projectProperty 'as-server.version'
        versionRange = "[1,)"
    }
}
