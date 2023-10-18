# slskd-downloader

Given a target album, `slskd-downloader` attempts to find users on the Soulseek network who have a matching set of 
files and initiate a download for that album.

### Building

This project requires
- Java 17
- Apache Maven

To build, enter the root directory of this repository and run

```bash
$ mvn clean package
```

The application will then be available as a 'fat JAR' at `target/slskd-downloader-<version>.jar`. You can run it by 
running

```bash
$ java -jar target/slskd-downloader-<version>.jar
```

### Configuring

This application is configured using environment variables.

TODO: env vars table