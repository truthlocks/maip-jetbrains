# Truthlocks MAIP for JetBrains IDEs

Create and inspect Truthlocks machine-action receipts from your IDE. Register agents,
check receipt signatures, view trust scores, and download workspace audit exports.
Your API key is stored in the IDE's PasswordSafe; it is not written to settings XML.

## Build status and requirements

Version 1.1.0 is undergoing hosted and IDE acceptance checks before publication.
The plugin descriptor is restored and the distributable ZIP builds successfully.
Publication and full interactive acceptance are not established by a successful build.
The build targets Java 17 and IntelliJ Platform 2024.1, with declared build compatibility
241 through 243.*. Newer IDE versions need separate compatibility validation.

## Developer commands

With a compatible Gradle installation, run from this package directory:

```sh
gradle test
gradle buildPlugin
```

The ZIP is written to `build/distributions/maip-jetbrains-1.1.0.zip`. This checkout
does not include a Gradle wrapper; Gradle 8.7 is used for validation.

## Configuration contract

| Setting | Purpose |
| --- | --- |
| API URL | `https://sandbox-api.truthlocks.com/v1` for sandbox; `https://api.truthlocks.com/v1` for approved production access. |
| API key | Workspace credential stored using the IDE PasswordSafe service. |
| Tenant ID | Legacy metadata; does not authorize workspace access. |
| Agent ID | Machine identity associated with requested receipts. |
| Auto receipt toggles | Off by default. Enable each hook only after reviewing which metadata it sends. |
| Timeout | Request timeout in milliseconds; source default 30000. |
| Max retries | Up to three retries for reads. Writes are not automatically replayed; redirects are not followed. |

## Integration lifecycle

1. Install the ZIP through **Settings > Plugins > Install Plugin from Disk** and restart.
2. Open **Settings > Tools > MAIP**. Use an existing workspace key or the sandbox
   registration button. Complete email verification; production access requires onboarding.
3. Match the API URL to the key's environment, then apply the settings.
4. Register an agent from the MAIP actions and set its returned `maip:...` identifier
   as your Default Agent ID. Workspace identity is derived from the key.
5. Select a saved file, run **Create Receipt**, then verify the returned public receipt ID.
6. Open the MAIP tool window to refresh agents, receipts and trust scores.

The equivalent verification request is:

```powershell
$env:TRUTHLOCKS_API_KEY = '<your sandbox key>'
$receiptId = '<public receipt ID returned by creation>'
Invoke-RestMethod -Uri ('https://sandbox-api.truthlocks.com/v1/agent-receipts/verify/' + [Uri]::EscapeDataString($receiptId)) -Headers @{ 'X-API-Key' = $env:TRUTHLOCKS_API_KEY }
```

Check `valid`, `signature_verified`, and `verdict`. A `VALID` result must also have
both booleans set to `true`. Never paste a real key into a support report or repository.

## Evidence semantics

- Manual file receipts hash saved file bytes with SHA-256; content is not uploaded.
- Commit receipts identify the current completed Git commit and include its message,
  author name and project-relative changed paths. These fields are sent to your workspace.
- Save hooks capture editor text encoded as UTF-8 before saving. This is labelled
  `editor_text_utf8`; it is not a claim about the eventual disk byte encoding.
- Build hooks record successful build events. They do not claim an artifact digest
  when no output artifact was selected and hashed. Aborted or failed builds are skipped.
- The algorithm selector records a request as metadata. The registered agent's actual
  server-side signing key determines the algorithm; a selector does not rotate that key.
- Delegation trees show relationships and do not independently verify delegation signatures.
- Trust charts show available scores for up to 100 agents. Missing scores are excluded;
  this sample must not be presented as a complete workspace risk assessment.
- Audit exports use server-side jobs and workspace policy. Dates include the complete
  selected end day in UTC, up to 366 days. The plugin checks the returned SHA-256 digest
  when supplied. If processing exceeds a minute, use Console audit exports to download it.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| Authentication or environment error | Check the key, workspace permissions and sandbox/production URL pairing. |
| No agents or receipts | Refresh and check API connectivity; the IDE displays a limited result page. |
| Export denied or unavailable | Check audit permissions and workspace export policy in Console. |
| UNVERIFIABLE receipt | Retain that verdict; missing historical signing material cannot be treated as valid. |
| Plugin incompatible | Check the declared 241–243.* platform range; newer IDEs need separate validation. |
| Credentials missing | Configure PasswordSafe; do not add a plaintext key to XML. |

Documentation: [Truthlocks developer docs](https://docs.truthlocks.com).
Source: [truthlocks/maip-jetbrains](https://github.com/truthlocks/maip-jetbrains).
Support: support@truthlocks.com. Include the IDE/plugin version and sanitized errors.
