# SkullPlugin

> Get skulls of players.

## Features

- Obtain custom player heads using the `/skull` or `/head` command.
- Configurable XP cost for obtaining skulls.
- Asynchronous UUID and texture data fetching to minimize server lag.

## Usage

### Requirements

- [Paper](https://papermc.io) or a compatible fork.
- [CommandAPI](https://commandapi.jorel.dev) JAR added to the server's plugins directory.

### Building from source

To build the plugin from source, you will need Gradle and Java 21. Follow these steps:

1. **Clone the Repository**:
   ```sh
   git clone https://github.com/walker84837/SkullPlugin.git
   cd SkullPlugin
   ```

2. **Build the code**:
   ```sh
   ./gradlew build
   ```

3. **Get the JAR**: The built jar file will be located in the `build/libs` directory.

### Configuration

In the `config.yml` file, you'll get to see some options. Here is information about them.

|Value|Description|Default|
|---|---|---|
|`xp-cost`|The amount of XP (in points) required to be given a skull|100|

### Commands

- `/skull <username>`: Obtains the skull of the specified player. `/head` also works as an alias.

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

### Roadmap

- [ ] Further improvements to the plugin logic
- [ ] Running the main API logic in a different thread
  - Probably use a thread pool for better performance when under load.
- [ ] More...

### Support

If you encounter any issues or have questions, please open an [issue](https://github.com/walker84837/SkullPlugin/issues).

## Acknowledgements

- [CommandAPI](https://github.com/CommandAPI/CommandAPI) for command handling.
- [Minetools.eu API](https://api.minetools.eu) for fallback UUID and texture data.

## License

This plugin is licensed under the [Mozilla Public License 2.0](LICENSE).
