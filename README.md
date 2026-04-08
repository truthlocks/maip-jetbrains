# MAIP JetBrains Plugin

MAIP (Machine-Actionable Integrity Protocol) plugin for JetBrains IDEs. Enterprise-grade machine identity, cryptographic receipts, trust scores, agent management, and delegation chains.

## Supported IDEs

- IntelliJ IDEA (2024.1+)
- PyCharm
- WebStorm
- GoLand
- PhpStorm
- Rider
- CLion
- RubyMine
- DataGrip

## Features

- **Receipts** -- Create, verify, and browse integrity receipts for code artifacts
- **Trust Scores** -- Real-time trust scoring dashboard for machine agents
- **Agent Management** -- Register, suspend, and revoke machine agents
- **Auto-Receipts** -- Automatically generate receipts on VCS commits, file saves, and builds
- **Delegation Chains** -- Inspect and audit delegation chains for any receipt
- **Code Inspection** -- Detect agent IDs in source code and display trust information inline
- **Audit Export** -- Export comprehensive audit reports in JSON and CSV formats
- **Tool Window** -- Dedicated MAIP panel with agent, receipt, and trust dashboard tabs
- **Right-Click Context** -- Create receipts from the editor context menu

## Installation

### From JetBrains Marketplace

1. Open **Settings** > **Plugins** > **Marketplace**
2. Search for **MAIP**
3. Click **Install**
4. Restart your IDE

### From Disk

1. Download the latest release `.zip` from [GitHub Releases](https://github.com/truthlocks/maip-jetbrains/releases)
2. Open **Settings** > **Plugins** > gear icon > **Install Plugin from Disk**
3. Select the downloaded `.zip`
4. Restart your IDE

## Configuration

After installation, configure the plugin:

1. Open **Settings** > **Tools** > **MAIP**
2. Set your **API URL** (default: `https://api.truthlocks.com/v1/machine-identity`)
3. Set your **API Key**
4. Set your **Tenant ID**
5. Optionally set an **Agent ID** for auto-registration

## Usage

### Menu Actions

All actions are available under **Tools** > **MAIP**:

| Action              | Description                              |
|---------------------|------------------------------------------|
| Register Agent      | Register a new machine agent             |
| Create Receipt      | Create a receipt for the current file    |
| Verify Receipt      | Verify a receipt by ID                   |
| Show Trust Score    | Show trust score for an agent            |
| Export Audit Report | Export audit report in JSON or CSV       |

### Tool Window

The MAIP tool window (right sidebar) contains three tabs:

- **Agents** -- View and manage registered agents
- **Receipts** -- Browse and verify receipts
- **Trust Dashboard** -- Monitor trust scores across agents

### Auto-Receipts

The plugin can automatically create receipts on:

- **VCS Commits** -- Triggered via Git integration
- **File Saves** -- Triggered on document save
- **Builds** -- Triggered on build completion

Configure auto-receipt behavior in **Settings** > **Tools** > **MAIP**.

### Code Inspection

The plugin includes a code inspection that detects MAIP agent IDs in source code and shows their trust score inline. Enable or disable via **Settings** > **Editor** > **Inspections** > **MAIP**.

## Building from Source

### Requirements

- JDK 17+
- Gradle 8+ (or use the Gradle wrapper)

### Build

```bash
./gradlew buildPlugin
```

The plugin `.zip` will be in `build/distributions/`.

### Run in Development IDE

```bash
./gradlew runIde
```

### Run Tests

```bash
./gradlew test
```

## Project Structure

```
src/
  main/
    kotlin/com/truthlocks/maip/jetbrains/
      MAIPPlugin.kt                  -- Plugin entry point
      actions/                       -- Menu and context actions
      client/                        -- API client and config
      icons/                         -- Plugin icons
      inspections/                   -- Code inspections
      listeners/                     -- Git, file save, build listeners
      notifications/                 -- Notification helpers
      services/                      -- Project services
      settings/                      -- Settings UI and persistence
      toolwindow/                    -- Tool window panels
    resources/META-INF/
      plugin.xml                     -- Plugin descriptor
      git-integration.xml            -- Git integration config
  test/
    kotlin/com/truthlocks/maip/jetbrains/
      client/MAIPApiClientTest.kt
      services/ReceiptServiceTest.kt
      actions/CreateReceiptActionTest.kt
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
