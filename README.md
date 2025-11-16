# Chat server

## Features

- Supports multithreading for multiple clients
- Data persistance with SQLite
- Multiple rooms

## Running

With the source code and Maven at hand, the server can be started:
```sh
mvn exec:java -Dexec.mainClass=com.example.server.ChatServer
```

Start the client:
```sh
mvn exec:java -Dexec.mainClass=com.example.client.ChatClient
```
