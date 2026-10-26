# Moderation 0.3.13 GitHub artifact upload fix

The screenshot shows Gradle Build SUCCEEDED but upload-artifact attempted to upload a hard-coded 0.3.12 JAR path. The workflow now has the new 0.3.13 artifact label and uploads build/libs/*.jar. That is an upload fix, not a Java compile fix.
