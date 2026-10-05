# Security policy

## Supported versions

Only the latest minor version receives security fixes.

## Reporting a vulnerability

**Please do not open a public issue.** Report it privately instead: *Security → Report a vulnerability* on this repository ([GitHub private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)).

Please include:
- the affected version;
- the steps to reproduce;
- the impact you see.

You will get an answer within a week. A fix is released before any public disclosure, which is coordinated with you.

## Scope

This library handles deployment secrets. These are in scope:
- the sealing of `{cipher}` values;
- the rendered copies that hold decrypted values;
- the registry credentials kept in the deployer's `DOCKER_CONFIG`.
