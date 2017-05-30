ms-nos
======

A [simple network operating system](http://msnos.io) for Microservices (java implementation). This is a work in progress, even if the first public stable release is available, however you are welcome to start using it right now, everything works. Any queries, please open [an issue](https://github.com/workshare/ms-nos/issues) 

[![Built with Maven](http://maven.apache.org/images/logos/maven-feather.png)](http://maven.apache.org/)  [![Build Status](https://travis-ci.org/workshare/ms-nos.svg?branch=master)](https://travis-ci.org/workshare/ms-nos)
[![security status](https://www.meterian.io/badge/gh/bbossola/ms-nos/security)](https://www.meterian.io/report/gh/bbossola/ms-nos)
[![stability status](http://www.meterian.com/badge/gh/bbossola/ms-nos/stability)](http://local.meterian.com/report/gh/bbossola/ms-nos)


<!--
[![Coverage Status](https://coveralls.io/repos/bbossola/ms-nos/badge.png)](https://coveralls.io/r/workshare/ms-nos)
--> 

## Summary
MSNOS is a library built in order to mantain and survive a microservice based architecture :) Every microservice will surface spontaneously in a cloud, without the need of any application configuration. An application level healtcheck is available to internal monitor the health on the services in the cloud.
More on the website at http://msnos.io

#### What is the context?
- you use a microservice architecture
- microservice are distributed across the world
- each microservice provide different APIs
- several instances of the same microservices are available

#### What do we achieve?
- new microservices capable to serve certain APIs are automatically recognized and used by every other microservice
- if any microservice become unhealthy it stops receiving calls
- no single point of failures 
- the load is distributed torugh advanced strategies (load, location, availability...)
- add and/or remove of a new microservices is fully transparent

#### How do I publish a new microservice?
The microservice will "join" a cloud and then publish his own APIs to the cloud: every other microservice will automatically discover them. Sample code:
```
  // create a cloud and join it
  cloud = new Microcloud(new Cloud(params.uuid(), params.signature()));
  self = new Microservice(params.name());
  self.join(cloud);

  // publish to the cloud myendpoints
  RestApi[] { apis = new RestApi[] {
    new RestApi("/hello", port),
    new RestApi("/wassup", port),
    new RestApi("/health", port).asHealthCheck(),
    new RestApi("/msnos", port, Type.MSNOS_HTTP),
  };
  self.publish(apis);
```

#### How do a microservice find the best API to use?
Each node in an msnos powered system mantains a full list of all available APIs and it's capable to select the best one for your call using strategies based on location, load and availability. Sample code:
```
  api = cloud.find(self, '/hello')
```

#### How do I expose some APIs to the external world?
A [fast http non-blocking i/o proxy](https://github.com/workshare/ms-nos-proxy) is available out of the box, and it will automatically expose any public API of the cloud to the external world

#### How does all this work?
An internal messaging system is used internally, basedon UDP and HTTP. An [http relay](https://github.com/workshare/ms-nos-www) needs to be installed in case your cloud is distribuited across different networks. Advanced microservices can expose an endpoint to accept MSNOS messages directly, as you can see in the [java example](https://github.com/workshare/ms-nos-usvc-client) provided, and briefly in this sample code: 

```
  public void handle(HttpExchange exchange) throws IOException {
    Reader reader = new BufferedReader(...);
    try {
      Message message = serializer.fromReader(reader, Message.class);
      cloud.process(message, Endpoint.Type.HTTP);
    } finally {
      reader.close();
    }
    exchange.sendResponseHeaders(200, 0);
    exchange.getResponseBody().close();
  }
```
As set of pre-build endpoints, in the form of jar dependencies, will be provided for the most common Java implementation (JavaSE, JavaEE, Jetty, Netty) and languages (.NET, Ruby)

#### Are there working examples?
The [proxy](https://github.com/workshare/ms-nos-proxy) itself is a working example, but there's a also a [much simple client](https://github.com/workshare/ms-nos-usvc-client) that you can checkout and look at. Examples in other languages will be provided as soon as we will upgrade a microservice sin the same technology.

#### Are other languages supported?
The protocol is completely language agnostic (heck, it's json!) and we build microservices in a lot of different languages. A Ruby implementation is in the work, a .NET binary will be realeased (as soon as it clears QA) and a Javascript version will be available soon.

## Commmon Q&A
Those are most of the common question we are asked when presenting our solution to the public: it's quite possibile you will find the answer to your question here, but if you don't please feel free to [open an issue](https://github.com/workshare/ms-nos/issues)

#### Why don't you simply use a dynamic DNS to manage discovery?
Well, first of all a DNS entry does not provide us enough granularity, as we want to tall about API endpoints, not machines names. Even when this is acceptable, there are other very good reasons to avoid doing that, such as:
We consider it a naive approach
- the clients will have poll forchanges as there’s no push protocol for DNS
- any DNS suffer from propagation delays, which is also non deterministic
- the routing will be effectively random, as there's no way for a DNS to dynamically assess the status of an API endpoint 
- aggressive DNS caching may take place by client libraries or even applications at startup (i.e. Nginx / HAProxy) thus making ineffective anything done at the DNS level 

#### Why don't you use ${other-framework}?
Well, there are different answer for each of them, and such frameworks are becoming more popular by the minute! Anyway, some common issues we found so far:
- they do not have native API level granularity (i.e. Consul,  Zookeper)
- they do not have a push model (i.e. Zookeper, dynamic DNS)
- they have a single point of failre and are not fully distributed (Smartstack)
- they are missing basic necessary routing mechanism (i.e. geolocation, session stickiness)
- they are not language agnostic or very difficult to code against (i.e. Zookeper)
- they are proprietary

### Where do I find detailed technical documentation
Please open the [wiki](https://github.com/workshare/ms-nos/wiki) and of course please feel free to checkout or fork the code

## License
Released under the MIT License.  See the [LICENSE](LICENSE) file for further details.

