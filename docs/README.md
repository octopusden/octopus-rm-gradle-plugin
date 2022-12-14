## HOW TO USE

To generate documentation locally, run:
````
./gradlew asciidoctor 
    -PimplLoggingCore="implementation \"com.example.platform:platform-commons-logging-core:\${project['platform-commons.version']}\"
````
and check generated html files in the build/docs/asciidoc directory
