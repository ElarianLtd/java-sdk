# Elarian

>
> The SDK provides convenient access to the Elarian APIs from applications written in Kotlin, Java, and Scala.
>
> ***Project Status: Still under ACTIVE DEVELOPMENT, APIs are unstable and may change at any time until release of v1.0.0.***

## Documentation
Take a look at the [API docs here](https://developers.elarian.com). For detailed info on this SDK, see the [reference](https://elarianltd.github.io/java-sdk/).

## Install

You can depend on the [.jar]() through Maven:
```xml
<dependency>
  <groupId>com.elarian</groupId>
  <artifactId>elarian-core</artifactId>
  <version>[VERSION]</version>
</dependency>
```
or sbt:

```
libraryDependencies += "com.elarian" % "elarian-core" % "[VERSION]"
```

or Gradle:
```groovy
dependencies{
  implementation 'com.elarian:elarian-core:[VERSION]'
}
```

## Usage

The SDK needs to be initialized with your API key, which you get from the [dashboard](https://account.elarian.com).

```java
import com.elarian.*;
import com.elarian.model.*;

// ...

String appId = "test_app";
String orgId = "test_org";
String apiKey = "test_api_key";

Elarian app = new Elarian(apiKey, orgId, appId);

app.connect(new ConnectionListener() {
    @Override
    public void onPending() {
        log.info("Pending...");
    }

    @Override
    public void onConnecting() {
        log.info("Connecting...");
    }

    @Override
    public void onClosed() {
        log.info("Connection Closed");
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("Failed to connect: " + throwable.getMessage());
    }

    @Override
    public void onConnected() {
        log.info("Connected!");
        Tag tag = new Tag("some-key", "some-value");
        MessagingChannel channel = new MessagingChannel("2020", MessagingChannel.Channel.SMS);
        Message message = new Message(new MessageBody("This is a test"));
        app.sendMessageByTag(tag, channel, message)
                .subscribe(
                        res -> log.info(res.description),
                        err -> err.printStackTrace()
                );
    }
});
try {
    Thread.currentThread().join();
} catch (InterruptedException e) {
    e.printStackTrace();
}
```

See [examples](elarian-examples/java) for more usage examples.

## Development

See [SDK Spec](https://github.com/ElarianLtd/sdk-spec) for reference.

## Issues

If you find a bug, please file an issue on [our issue tracker on GitHub](https://github.com/ElarianLtd/java-sdk/issues).
