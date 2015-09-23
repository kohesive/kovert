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

