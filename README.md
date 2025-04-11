# Faros AI - IntelliJ Plugin

This is the IntelliJ IDEA plugin for Faros AI, tracking coding productivity and AI assistance metrics.

## Features

- Tracks AI-powered auto-completions and hand-written characters
- Displays metrics in a dedicated tool window
- Provides statistics on coding productivity
- Integrates with Git to track repository and branch information

## Installation

**From JetBrains Marketplace:**
1. In IntelliJ IDEA, go to Settings/Preferences → Plugins
2. Click on "Marketplace"
3. Search for "Faros AI"
4. Click "Install"

**Manual Installation:**
1. Download the latest release `.jar` file
2. In IntelliJ IDEA, go to Settings/Preferences → Plugins
3. Click on the gear icon and select "Install Plugin from Disk..."
4. Select the downloaded `.jar` file

## Configuration

1. Go to Settings/Preferences → Tools → Faros AI
2. Enter your API Key and other required information
3. Click "Apply" to save the settings

## Building from Source

```bash
# Clone the repository
git clone https://github.com/faros-ai/faros-intellij-plugin.git
cd faros-intellij-plugin

# Build the plugin
./gradlew buildPlugin
```

The built plugin can be found in `build/distributions/`.

## Development

### Prerequisites

- JDK 17 or higher
- IntelliJ IDEA Community/Ultimate (2023.1 or newer)
- Gradle 8.0 or higher
- Kotlin 1.9.0 or higher

### Project Structure

- `src/main/kotlin/ai/faros/intellij/` - Plugin source code
  - `model/` - Data models
  - `services/` - Application and project services
  - `ui/` - User interface components
  - `util/` - Utility classes
- `src/main/resources/` - Resources like icons and plugin configuration

### Technology Stack

- Kotlin - Primary programming language
- IntelliJ Platform Plugin SDK - For integrating with IntelliJ IDEA
- Gradle - For building the plugin
- Gson - For JSON serialization/deserialization

## License

This project is licensed under the terms of the LICENSE file included in the repository.

## Support

For support, feature requests, or bug reports, please open an issue on the GitHub repository or contact support@faros.ai. 