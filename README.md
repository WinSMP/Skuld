# Skuld

> Never forget where your players have been.

## Features

- Obtain custom player heads using the `/skull` command.
- Track the name of players as they join your server using `/namehistory`.
- Configurable XP cost for obtaining skulls.
- Asynchronous UUID and texture data fetching to minimize server lag.
- Support for multiple databases:
  - PostgreSQL
  - MySQL
  - SQLite

## Usage

### Requirements

- [Paper](https://papermc.io) or a compatible fork.
- A database for player history. You can choose between PostgreSQL, MySQL, or SQLite.

### Building from source

To build the plugin from source, you will need Gradle and Java 21. Follow these steps:

1. **Clone the Repository**:
   ```sh
   git clone https://github.com/WinSMP/Skuld.git
   cd Skuld
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
|`history.enabled`|Whether to enable the name history feature. If this is disabled, you do not need a database.|true|
|`database.type`|The type of database to use. Can be "sqlite", "mysql", or "postgresql".|"sqlite"|
|`database.max-connections`|The number of maximum connections to the database|10|
|`database.postgresql.name`|The name of the PostgreSQL database|"skuld_names"|
|`database.postgresql.username`|The username for the PostgreSQL database|`"postgres"`|
|`database.postgresql.password`|The password for the PostgreSQL database|`"password"`|
|`database.mysql.name`|The name of the MySQL database|"skuld_names"|
|`database.mysql.username`|The username for the MySQL database|`"root"`|
|`database.mysql.password`|The password for the MySQL database|`"password"`|

### Commands

- `/skull <username>`: Obtains the skull of the specified player.
- `/namehistory <player>`: Gets the name history of the specified player who joined on the server.

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.

### Roadmap

- Further improvements to the plugin logic
  - [X] Split code across multiple classes
- [X] Running the main API logic in a different thread
  - Probably use a thread pool for better performance when under load.
- Allow for multiple databases/caches:
  - [X] MySQL
  - [X] SQLite
  - [ ] Redis/Valkey
- [ ] More...

### Support

If you encounter any issues or have questions, please open an [issue](https://github.com/WinSMP/Skuld/issues).

## Acknowledgements

- [Minetools.eu API](https://api.minetools.eu) for fallback UUID and texture data.

## License

This plugin is licensed under the [Mozilla Public License 2.0](LICENSE).