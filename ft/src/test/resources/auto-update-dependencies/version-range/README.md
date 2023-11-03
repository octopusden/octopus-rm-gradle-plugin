autoUpdateDependencies {
    component(name: 'platform-utils', projectProperty: 'pl-utils-version', versionRange: "(1,)")
    component {
        name 'myapp'
        projectProperty 'my-app.version'
        versionRange = "[1,)"
    }
}
