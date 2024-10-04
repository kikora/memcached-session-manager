# memcached session manager

memcached-session-manager is a tomcat session manager that keeps sessions in memcached or Redis, for highly available, scalable and fault tolerant web applications.
It supports both sticky and non-sticky configurations. This forked version is slimmed down and is currently working with tomcat 10.x and Reddis. For sticky sessions session failover (tomcat crash)
is supported, for non-sticky sessions this is the default (a session is served by default by different tomcats for different requests).
There shall also be no single point of failure, so when a memcached fails
the session will not be lost (but either be available in tomcat or in another memcached).

## Installation and Configuration
Basically you must put the jedis jar and the memcached-session-manager jars into tomcat's lib folder.
Additionally you must set the Manager class and add some configuration attributes. This is described in detail in the
[SetupAndConfiguration wiki page](https://github.com/magro/memcached-session-manager/wiki/SetupAndConfiguration).

## Where to get help
Checkout the [wiki](https://github.com/magro/memcached-session-manager/wiki) for documentation, some may apply for this fork as well.
## How to contribute
If you want to contribute to this project you can fork this project.

## Samples
There's [sample webapp](https://github.com/magro/memcached-session-manager/tree/master/samples) that allows to run tomcat+msm in different configurations,
both sticky and non-sticky etc, just checkout the [README](https://github.com/magro/memcached-session-manager/tree/master/samples).

## License
The license is Apache 2.0, see LICENSE.txt.
