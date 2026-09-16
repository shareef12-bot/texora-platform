# ldap-service

Build order: #6 of 8 (see 00-docs/build-plan)
Source LLD:  00-docs/lld/Texora_SecOps_LDAP_LLD.docx
Owning prompt: Part D, Prompt 6 in the build-plan document

Depends on: 01-platform-foundation (starter, iam-client, audit-sdk)
            SEC contract stub (real SEC once built)

Do not start this service until:
  - Platform Foundation is built and published
  - Prerequisite checks in its own prompt are satisfied (see build-plan doc)
