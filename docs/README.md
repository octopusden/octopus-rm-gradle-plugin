## HOW TO USE

To generate documentation locally, run:
````
./gradlew asciidoctor 
    -PimplLoggingCore="implementation \"com.example.platform:platform-utils-logging-core:\${project['platform-utils.version']}\"
````
and check generated html files in the build/docs/asciidoc directory
