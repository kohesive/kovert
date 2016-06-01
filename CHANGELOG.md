=========================================================
2015-05-31 v0.13.1
=========================================================

* Fix loading of Hazelcast cluster default configuration for vert.x

=========================================================
2015-05-27 v0.13.0
=========================================================

* Kotlin to 1.0.2
* Injekt to 1.16.1
* Klutter to 1.18.0
* Fix allowing nullable booleans as type for parameters of controller methods
* Enums can now be used as type for parameters of controller methods
* Default values can now be used in parameters of controller methods (simple and complex types)
* Support throwing HttpErrorCodeWithBody to return a body with error code (Object as "application/json" or String as "text/html")

=========================================================
2015-02-15 v0.12.0
=========================================================

Release for Kotlin 1.0.0

* Kotlin to 1.0.0
* Injekt to 1.14.0
* Klutter to 0.15.0
* Kovenant to 3.0.0

=========================================================
2015-02-10 v0.11.0-RC-1050 release
=========================================================

Mostly updated due to Injekt change, possible small breaking changes with scope

* Injekt to 1.13.0-RC-1050
* Klutter to 0.14.0-RC-1050
* SLF4J API to 1.7.14

=========================================================
2015-02-09 v0.10.3-RC-1050 release
=========================================================

* Klutter to 0.13.4-RC-1050
* Jackson-Kotlin module to 2.6.5-RC-1050
* Kotlin to 1.0.0-rc-1050
* Kovenant to 3.0.0-rc.1050

=========================================================
2015-02-04 v0.10.3-RC-1036 release
=========================================================

* Klutter to 0.13.4-RC-1036

=========================================================
2015-02-04 v0.10.2-RC-1036 release
=========================================================

This is interim release for Kotlin 1.0 public release candidates, view support is in progress (see breaking changes below in 0.10.0)

* Kotlin to 1.0.0-rc-1036
* Injekt to 1.12.0-RC-1036
* Klutter to 0.13.3-RC-1036
* Kovenant to 3.0.0-rc.1036

=========================================================
2015-02-02 v0.10.0-RC-1025 release
=========================================================

This is interim release for Kotlin 1.0 RC release candidate compatibility, view support is in progress and only partial in this release

* Added View rendering for FreeMarker and Handlebars (see README for docs)
* BREAKING:  Changed default for setting `KovertConfig.autoAddBodyHandlersOnPutPostPatch` to `false` and now is readonly, it will be removed soon since body handler needs to be added very early, this doesn't work reliably.
* BREAKING:  `KovertVerticleConfig` now requires setting a list of path prefixes that need a body handler
* BREAKING:  `KovertVerticleConfig` now can have a CORS handler set and route prefixes, which will be added to the verticle in the correct routing positino
* Kotlin to 1.0.0-rc-1025
* Injekt to 1.12.0-RC-1025
* Klutter to 0.13.0-RC-1025
* SLF4J to 1.7.13

=========================================================
2015-12-30 v0.9.1 release
=========================================================

* Kotlin to 1.0.0-beta-4584
* Injekt to 1.10.1
* Klutter to 0.11.3

=========================================================
2015-12-23 v0.9.0 release
=========================================================

* Kotlin to 1.0.0-beta-4583
* Injekt to 1.10.0
* Klutter to 0.11.1 (including Jackson Kotlin module 2.6.4-1)
* Kovenant to 3.0.0-beta.4

=========================================================
2015-12-08 v0.8.0 release
=========================================================

* Vertx to 3.2.0
* Injekt to 1.9.0
* Klutter to 0.10.0

=========================================================
2015-12-08 v0.7.0 release
=========================================================

* Kotlin to 1.0.0-beta-3595
* Injekt to 1.8.4
* Klutter to 0.9.0
* Kovenant to 3.0.0-beta.3

=========================================================
2015-11-09 v0.6.3 release
=========================================================

* Kotlin to 1.0.0-beta-2423
* Injekt to 1.8.3
* Klutter to 0.8.4

=========================================================
2015-11-09 v0.6.2 release
=========================================================

* Kotlin to 1.0.0-beta-2422
* Injekt to 1.8.2
* Klutter to 0.8.2

=========================================================
2015-11-09 v0.6.1 release
=========================================================

* Kotlin to 1.0.0-beta-1103
* Injekt to 1.8.1
* Klutter to 0.8.1
* Vert.x to 3.1.0

=========================================================
2015-10-06 v0.6.0 release
=========================================================

* Kotlin to 1.0.0-beta-1038
* Kovenant to 2.9.0
* Injekt to 1.8.0
* Klutter to 0.8.0

=========================================================
2015-10-06 v0.5.1 release
=========================================================

* Kotlin to 0.14.451
* Injekt to 1.7.1
* Klutter to 0.7.1

=========================================================
2015-10-01 v0.5.0 release
=========================================================

* Update to Kotlin M14
* Update to Kovenant 2.7.0
* Update to Klutter 0.7.0 and use reflection helpers from there, dropping the local version

=========================================================
2015-09-24 v0.4.12 release
=========================================================

Improve local-only cluster settings for KovertVertx and hazelcast (use tcp instead of multicast, set to loopback)
Local only clustering is false by default

=========================================================
2015-09-24 v0.4.11 release
=========================================================

Add configuration option for KovertVertx that can force networking to localhost for clustering (mac osx might still violate this and broadcast all interfaces)
Add loading of default hazelcast cluster xml from vertx when using KovertVertx and clustering mode
Sample App now has more realistic logging configuration, separate app.log, error.log and access.log

=========================================================
2015-09-24 v0.4.10 release
=========================================================

Allow `application/json;...` for content type to auto bind post/patch/put data such as `application/json; charset=utf-8`
Move logback.xml out of resources into logback-test.xml in test resources only
Update to Klutter 0.6.5

=========================================================
2015-09-23 v0.4.9 release
=========================================================

Add `KovertConfig.autoAddBodyHandlersOnPutPostPatch` setting (default true) to control the body handler being added by Kovert to routes that require it
Update to Klutter 0.6.4

=========================================================
2015-09-23 v0.4.8 release
=========================================================

Change session handler  in KovertVerticle to only be active on GET, POST, PUT, PATCH, DELETE so it doesn't use or generate new session on OPTION calls

=========================================================
2015-09-23 v0.4.7 release
=========================================================

Allow reporting of stack traces on route failures via `KovertConfig.reportStackTracesOnExceptions = true`

=========================================================
2015-09-21 v0.4.6 release
=========================================================

Update Klutter to 0.6.3 with vert.x dispatching changes from Kovenant.
Added more Java JDK 8 data types allowed as bound parameter types (java.time.*)

=========================================================
2015-09-21 v0.4.5 release
=========================================================

Update Jackson Kotlin module to 2.6.2-1 for emergency bug fix and Kotlin M13
Update Klutter to 0.6.2
Update Injekt to 1.6.1

=========================================================
2015-09-21 v0.4.4 release
=========================================================

Fix RoutingContext when used as the receiver type for a controller method AND you have a context factory, it was causing the method to be ignored.

=========================================================
2015-09-21 v0.4.3 release
=========================================================

Handle Unit return from POST/PUT/PATCH/DELETE as ending the response with only sending back headers.

=========================================================
2015-09-17 v0.4.2 release
=========================================================

Fix support for missing parameters for nullable constructor parameters.

=========================================================
2015-09-17 v0.4.1 release
=========================================================

Update to later Klutter 1.6.0

=========================================================
2015-09-16 v0.4.0 release
=========================================================

Support for Kotlin M13
Now allows controller methods as members of type functions `public val findUserData = fun ApiContext.(): UserData { return ... }`

=========================================================
2015-08-31 v0.3.2 release
=========================================================

Take new version of Klutter-vertx 0.3.3 since it fixes a shutdown bug.
Take new versino of Injekt 1.4.0 to stay current with API changes.

=========================================================
2015-08-31 v0.3.1 release
=========================================================

Exception handler was putting full exception text into the HTTP status message header.  That isn't a good practice, it
now puts a generic failure message since any real error is already logged on the server, and should not be exposed to
the calling client.


=========================================================
2015-08-31 v0.3.0 release
=========================================================

Moved part of the Vert.x base library to Klutter, makes more sense there for the helper style parts of the library.  This
breaks imports for those functions which are now under `uy.klutter.vertx3`.  Some of the helpers have been improved there.

=========================================================
2015-08-28 0.1.0 release
=========================================================

First release, ready for use as a REST backend

2015-08-28 0.1.0 release

First release, ready for use as a REST backend

