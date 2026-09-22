@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1
set PATH=%JAVA_HOME%\bin;%PATH%
mvn -B clean test > build-log.txt 2>&1
notepad build-log.txt