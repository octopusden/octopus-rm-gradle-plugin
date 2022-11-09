Sample project which deployes the same artifact with different classifier under different BUILD_NAME.
If use default maven publication then <module>.pom will be published two times which results in promoting error.

To run this build specify parameter CLASSIFIER
-PCLASSIFIER=lib|zip|any
