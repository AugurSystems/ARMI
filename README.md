# Asynchronous Remote Method Invocation (ARMI)
ARMI (pronounced "army") is an alternative to Java's built-in RMI, initially developed to work across NAT.  ARMI also adds support for asynchronous messaging.  It's small... The entire ARMI library currently fits in a 32 kb jar.

# Synchronous
When a program calls a method, it will wait (block) until the method returns with a value, or void. Remote methods work the same way. So a client application will block until the method on the remote server returns. This call/response scenario is called synchronous.

All method calls are synchronous by nature. Do not confuse this term with the synchronized modifier in Java. The word synchronous means "as the same time". So if you think of a method from the perspective of the caller, the method's return value is received by the caller at the same time the call is made, because time essentially stands still for the caller (the CPU no longer executes the caller's code) while the method is executing.

Sometimes this blocking nature of method calls is inconvenient. For example, what if your client application is supposed to display ever-changing data from a server, for example, stock quotes? To keep your client viewer up-to-date, you must periodically call a method on the server to get updates, probably every few seconds. This is a form of polling, also called client-pull.

If you ask the server for an update too often, then the server will often have nothing new to give you. Each time that happens, it wastes a method call, and all the associated network and CPU resources. But if you poll less often, then your client will often receive old news. Neither situation is ideal. It would be better if the server could automatically update your client immediately, only when new data is available. 

# Asynchronous
In asynchronous communication, the client subscribes to receive responses from the server by calling a remote method on the server, just once. This initial method call immediately returns normally (synchronously). The subscribed responses will come later (asynchronously).

In that initial method call, the client tells the server where to send response(s). In a stock viewer example, we might want responses to go to a StockListener.handleUpdate() method on the client. At some time later, the server can publish data by calling that method. This is also called also known as a subscribe/publish model, or server-push.


## Can RMI Be Asynchronous?
You might wonder, "Can't I do this in RMI?" While server-push works fine with RMI in the lab, it fails in the real world, mainly due to NAT firewalls in the network that prevent connections in that direction. In general, a client can initiate communication with a server, but not vice versa. RMI does not provide any way around that limitation.

# ARMI Advantages
## No Port Chaos
ARMI solves the server-push problem by keeping the client's original socket open, as a permanent two-way communications link. NAT firewalls permit the client-to-server connection, and then ARMI uses the connection for server-to-client calls too.  That same socket is reused for all subsequent ARMI communications between the two JVMs, including other method calls and responses. So ARMI avoids a core problem of RMI: port management.

Every RMI Remote object (anything that extends UnicastRemoteObject) sets up its own network server socket port. This makes it difficult to configure firewalls to permit RMI traffic; there are so many ports to manage.  Instead of all that, ARMI multiplexes all method calls onto a single socket. And that socket's port number is fixed, so there's no need for a registry.

## Performance 
Every time you call an RMI remote method, a new socket connection is set up and torn down. ARMI avoids that overhead cost by maintaining one socket. (You might worry that ARMI's one socket might cause multiple calls to queue up, but ARMI handles each call in a separate thread.)

## No Stubs
In RMI, stub classes are generated by an RMI compiler that you must run for each of your remote server classes. Then you must include those stubs in your client jar files. (Each stub handles the network connections and serialization when your client wants to call methods on the remote server object.) ARMI simply eliminates this unnecessary complexity. No stubs.


## Asynchronous Data
In the subscribe/publish model (asynchronous mode), all kinds of data may be thrown at the ARMI hub. Your data-generating code does not need to know if anyone is listening, which keeps your code simple. You can just publish data, and ARMI routes is to the right subscribers.
Types and Flavors

You label published data with a type (for example "Stock Quote"), and optionally a flavor (for example, stock ticker symbol "IBM"). When each client subscribes, it specifies a preferred type and optionally a flavor. When new data matches a type and flavor, ARMI automatically routes it to all matching subscribers.

## Filters
The subscriber can also specify a filter so that ARMI can be even more selective about which data is sent across the network. The filter is just an object that implements ARMI's Filter interface. The ARMI server will use the filter to further test all data that already matched the subscribed type and flavor. For example, a client might subscribe to the "Stock Quote" data type, with a null flavor (match all ticker symbols). Then a filter might contain a list of several stocks to match.

Since a filter is Java code that you write, you can get very fancy. For example, a complex filter might perform a statistical analysis or reference an external database before approving data.

# Code Examples

## Call a Regular (Synchronous) Method
```java
import com.augursystems.armi.*;
// ...
Armi armi = new Armi(); // Instantiate an ARMI server

// Let's call market.getMarketIndex("DJX", "20") on a remote ARMI server...
/** An array for the method's arguments */
Serializable[] params = new Serializable[] { "DJX", "20" }; 
/** The remote call */
Serializable value = armi.call(remoteServer,"Market", "getMarketIndex", params); 
System.out.println("The Dow Jones index is" + value);
```
 
## Register a Remote Service
```java
armi.acceptRemoteClients(null, Armi.DEFAULT_PORT); // Turn on the server.

// Create your ARMI service object, e.g. a model of a stock market.
// No "Remote" interface necessary; all public methods are automatically accessible...
StockMarket market = new StockMarket();
// Register your service with ARMI...
armi.registerService("Market", market);
// "Market" is an instance name that remote callers can reference
```

## Register a Client to Receive Asynchronous Data
```java
// Subscribers must implement the Client.handlePacket() interface method
Client client = new Client()
{
  public void handlePacket(final Packet p)
  {
    try
    {
      // Let ARMI de-serialize (unmarshal) the object for you
      Object quote = p.decodeInstance(); 
      System.out.println("Received stock quote: "+ quote);
    }
    catch (Exception e) { System.out.println("Problem unmarshalling a quote: "+e; }
  }
}

// Point to the remote server; use default port...
HostPort remoteServer = new HostPort("192.168.1.111");
// Ask the remoteServer to send "StockQuote" data types, with any flavor (null), 
// and no filter (null), to our client.
armi.subscribeRemote(remoteServer, StockQuote.class.getName(), null, null, client);
```

## Publish Data
```java
// The following code would presumably be running on the remote server that 
// publishes data for clients.  Creates a datum representing a stock ticker value.
StockQuote quote = new StockQuote("ACME", "$8.94"); 

// Publish the data so the server can transmit it to any subscribers...
armi.transmit(quote, "NASDAQ");
// The data type is automatically set to the class name via quote.getClass(), e.g. 
// "com.bank.myapp.StockQuote" The flavor is specified as the "NASDAQ" stock exchange, 
// which may be a useful classification.
```
