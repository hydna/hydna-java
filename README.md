Java bindings for Hydna
=======================

These are the official Java bindings for the Hydna Platform (www.hydna.com).

The product is currently in beta. It is well tested, but please report any
issue that may occur.

Please see usage examples in "src/main/java/com/hydna/examples".

**Please note, this implementation is NOT thread-safe**


##Installation

The library and examples can be built using `make`:
    
      $ make

Verify by running the "Hello world" demo:

      $ make hello


##Examples
This software includes the four examples, "HelloWorld", "Listener", "MultipleChannels" and "Signals".

### HelloWorld.java
A simple "send, wait and receive" example.

### Listener.java
Sets up a receive-loop that waits for data and signals.

### MultipleChannels.java
Sets up two channels and waits for data.

### Signals.java
Pings the server and waits for a "pong". Please note that your behavior
need to answer with a "pong" for this demo to work.


Bugs, issues and suggestions
----------------------------
Please use Githubs issue system at https://github.com/hydna/hydna-java/issues


Copyright
---------
Hydna AB (c) 2010-2013 
