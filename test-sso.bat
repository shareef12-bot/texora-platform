@echo off
REM Runs the full integration test suite for sso-service — this is what
REM actually exercises the real endpoints end-to-end (login flow, token
REM issuance, application registration, etc.) using Testcontainers.
REM Needs Docker Desktop running.

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

mvn -pl 02-services/sec-service -am verify > sso-test-log.txt 2>&1
notepad sec-test-log.txt