[![Kotlin M13](https://img.shields.io/badge/Kotlin-M13%20%40%200.13.1513-blue.svg)](http://kotlinlang.org) [![Maven Version](https://img.shields.io/maven-central/v/uy.kohesive.kovert/kovert-core.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22uy.kohesive.kovert%22) [![CircleCI branch](https://img.shields.io/circleci/project/kohesive/kovert/master.svg)](https://circleci.com/gh/kohesive/kovert/tree/master) [![Issues](https://img.shields.io/github/issues/kohesive/kovert.svg)](https://github.com/kohesive/kovert/issues?q=is%3Aopen) [![DUB](https://img.shields.io/dub/l/vibe-d.svg)](https://github.com/kohesive/kovert/blob/master/LICENSE)

# Kovert

The invisible REST (and soon for web) framework.  It is "invisible" since it does not invade your code, not even with annotations.  

Kovert is a simple framework that binds your Kotlin classes into your Vert.x 3 (and soon Undertow) routers.  It does not try to replace or rebuild these frameworks and only handles the task of providing the "last mile" binding to your controllers.  From a fairly normal looking Kotlin class, Kovert can infer the route path and parameters.  

This is an experiment to see how far we can get without looking like JAX-RS.  If you want tons of JAX-RS annotations instead of happy Kovert inference, please use [Vertx-Nubes](https://github.com/aesteve/vertx-nubes) for Vert-x and [Kikaha](http://kikaha.skullabs.io) for Undertow, or take a peek at [SparkJava](http://sparkjava.com).

For starting an application with Kovert, you have two options:

* Configure, Startup Vert-x, deploy a Vert-x verticle, add your routes with Vert-x Web, and _then_ ask Kovert to bind a controller to an existing route.  For that sentence to make sense, you should be familiar with [Vertx-Web](http://vertx.io/docs/vertx-web/java/) and the basics of [Vertx](http://vertx.io/docs/vertx-core/java/)
* Alternatively, if you just want to get started without knowing too much, [Kovert provides `KovertVertx` and `KovertVerticle` classes](#vertx-and-kovertverticle-startup) that can bootstrap a base application, but this acts more as an example starting point from which you should build your own.

In addition, Kovert uses [Klutter/Vertx3](https://github.com/klutter/klutter/tree/master/vertx3) module which contains helper classes for working with Vert-x that use [Kovenant](http://kovenant.komponents.nl) promises -- including ensuring that the dispatcher for Kovenant is unified with the thread dispatching in Vert.x so that Vert.x context is maintained on dispatch threads, and callbacks come as expected by Vert.x as well.  There are additional helpers for Vert.x JSON objects, the logging facade, web, and integration with [Injekt](#injekt), and more.

#### Maven Dependnecy (Vert.x Version, requires JDK 8)

Include the dependency in your Gradle / Maven projects that are compatible with Kotlin M13 version
`0.13.1513`

**Gradle:**
```
compile "uy.kohesive.kovert:kovert-vertx:0.4.+"
```

**Maven:**
```
<dependency>
    <groupId>uy.kohesive.kovert</groupId>
    <artifactId>kovert-vertx</artifactId>
    <version>[0.4.0,0.5.0)</version>
</dependency>
```

### Learn by Example

For a full sample, view the [sample REST application](vertx-example/src/main/kotlin/uy/kohesive/kovert/vertx/sample/) to see an example that uses the helper classes `KovertVertx` and `KovertVerticle` to startup and run a vertx server using a configuration file.  

It helps if you are familiar with [Injekt](https://github.com/kohesive/injekt) to completely understand the sample.  Injekt is not required for use of Kovert and can be bypassed, but it helps!  The sample also uses small libraries from [Klutter](https://github.com/klutter/klutter) such as to control configuration loading with Typesafe Config.

### Binding a Controller

To bind a controller is simple.  When you have a route object, call the extension method `bindController`:

```kotlin
route.bindController(MyControllerClass(), "/api/mystuff")
```

A controller class is any class that contains methods that are extension functions on a class that you wish to be
your dispatch context.  So you can either decide that dispatch context is the raw `RoutingContext` of Vert.x, or you can isolate your code from the raw Vert.x and use simple classes that wrap elements of the `RoutingContext` to make
them available in a type-safe way.  Any class that has a 1 parameter constructor of type `RoutingContext` can be a context class, or `RoutingContext` itself.  An example of a custom context:

```kotlin
class RestContext(private val routingContext: RoutingContext) {
    public val user: User by Delegates.lazy { routingContext.user() }
}
```

Or one that uses an API key to validate the request using an Auth service provided by Injekt (really you should build a Vert.x auth service, this is just for an example of a context that can reject a request instead of using an intercept):

```kotlin
class ApiKeySecured(private val routingContext: RoutingContext) {
    public val user: User =  Injekt.get<AuthService>()
                 .apiKeyToUser(routingContext.request().getHeader(HttpHeaders.AUTHORIZATION.toString()) ?: "") 
                 ?: throw HttpErrorUnauthorized()
}
```

With the dispatch object selected, write your controller.  Here is the CompanyController from the sample app:

```kotlin
class CompanyController(val companyService: CompanyService = Injekt.get()) {
    public fun ApiKeySecured.getCompanyByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    public fun ApiKeySecured.putCompanyByName(name: String, company: Company): Company {
        if (!name.equals(company.name, ignoreCase = true)) {
            throw HttpErrorBadRequest()
        }
        companyService.upsertCompany(company)
        return company
    }

    public fun ApiKeySecured.listCompanyByNameEmployees(name: String): List<Person> {
        return companyService.listEmployeesOfCompany(name) ?: throw HttpErrorNotFound()
    }

    public fun ApiKeySecured.findCompaniesNamedByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    public fun ApiKeySecured.findCompaniesLocatedInCountry(country: String): List<Company> {
        val found = companyService.findCompaniesByCountry(country)
        if (found.isEmpty()) throw HttpErrorNotFound()
        return found
    }

    public fun ApiKeySecured.getCompaniesSearch(name: String?, country: String?): Promise<Set<Company>, Exception> {
        return async {
            val byName: List<Company> = name.whenNotNull { companyService.findCompanyByName(name!!) }.whenNotNull { listOf(it) } ?: emptyList()
            val byCountry: List<Company> = country.whenNotNull { companyService.findCompaniesByCountry(country!!) } ?: emptyList()
            (byName + byCountry).toSet()
        }
    }
}
```

Great, that class looks like it contains normal Kotlin methods, just that they are extension methods on `RestContext` class.  That means each method has access to values of the context and only those values.  You can actually have a different context class for each method if you want, and the correct context will be created for dispatching to that method.  A context for public methods, one for logged in, another for temporary state during a process flow, etc.

When binding the controller class, HTTP verb and path names are derived from method names unless you use [special annotations](#annotations) to override the path, path parameters, HTTP verb and success status code.  Kovert is designed to avoid using those annotations altogether, and they should appear ONLY in special cases.  

### Infering HTTP Verb and Path

So without any annotations, how does Kovert decide the path names and parameters?!?

First, it camelCase parses the method name with the first part indicating the HTTP verb, and the rest being segments of the path.  When it encounters special words, it creates path parameters.  The camel case parsing rules are:

```kotlin
// thisIsATestOfSplitting = this is a test of splitting
// AndWhatAboutThis = and what about this
// aURIIsPresent = a uri is present
// SomethingBySomething = something :something
// something20BySomething30 = something20 :something30
// 20ThisAndThat = 20 this and that
// 20thisAndThat = 20this and that
// What_about_underscores = what about underscores
// 20_ThisAndThat_And_What = 20 this and that and what
// 20________thisAndThat__What = 20 this and that what
```

Using the prefix part of the method, a HTTP verb is inferred.  Obviously prefixes of "get", "put", "post", "delete", "patch" will generate a route that is for the HTTP verb of the same name.  You can see in `KovertConfig` that other aliases are defined such as "list" and "view" for HTTP GET, and "remove" also works same as HTTP DELETE.  You can change the alias list in `KovertConfig` using the `addVerbAlias` or `removeVerbAlias` methods.  You can also specify aliases in the `bindController` method as an optional parameter, or as annotations `@VerbAliases` and `@VerbAlias` on your controller class.  The sample application modifies `KovertConfig` to add "find" as an alias to HTPT GET:

```kotlin
KovertConfig.addVerbAlias("find", HttpVerb.GET)
```

[Other annotations can be used on classes](#annotations) to tune their behavior and override the path, path parameters and HTTP verbs.

All routing path and parameter decisions are logged to the current logger, so  you can easily see the results of the `bindController` method.  The example above, would generate these paths when bound at "/api" route:

|Method|Verb|Path (w/parameters)|
|------|----|-------------------|
|`getCompanyByName(name: String)`|GET|`api/company/:name`|
|`putCompanyByName(name: String)`|PUT|`api/company/:name`|
|`listCompanyByNameEmployees(name: String)`|GET|`api/company/:name/employees`|
|`findCompaniesNamedByName(name: String)`|GET|`api/companies/named/:name`|
|`findCompaniesLocatedInCountry(country: String)`|GET|`api/companies/located/:country`|
|`getCompaniesSearch(name: String, country: String)`|GET|`api/companies/search?name=xyz&country=abc`|

Which I can confirm by viewing my log output (notice it logs from my controller class):

```
11:41:20.880 [vert.x-eventloop-thread-2] INFO  u.k.k.vertx.sample.CompanyController - Binding getCompanyByName to HTTP GET:200 /api/company/:name w/context ApiKeySecured
11:41:20.882 [vert.x-eventloop-thread-2] INFO  u.k.k.vertx.sample.CompanyController - Binding listCompanyByNameEmployees to HTTP GET:200 /api/company/:name/employees w/context ApiKeySecured
11:41:20.883 [vert.x-eventloop-thread-2] INFO  u.k.k.vertx.sample.CompanyController - Binding findCompaniesNamedByName to HTTP GET:200 /api/companies/named/:name w/context ApiKeySecured
11:41:20.884 [vert.x-eventloop-thread-2] INFO  u.k.k.vertx.sample.CompanyController - Binding putCompanyByName to HTTP PUT:200 /api/company/:name w/context ApiKeySecured
11:41:20.885 [vert.x-eventloop-thread-2] INFO  u.k.k.vertx.sample.CompanyController - Binding findCompaniesLocatedInCountry to HTTP GET:200 /api/companies/located/:country w/context ApiKeySecured
11:41:20.886 [vert.x-eventloop-thread-2] INFO  u.k.k.vertx.sample.CompanyController - Binding getCompaniesSearch to HTTP GET:200 /api/companies/search w/context ApiKeySecured
```

### Path Parameters

Previously, we mentioned that you can use special words to create path parameters, here they are:

|word|description|example|result|
|----|-----------|-------|------|
|By|next word is path parameter|`getCompanyByName(name:String)`|HTTP&nbsp;GET&nbsp;company/:name|
|In|same as By|`getCompaniesInCountry(country:String)`|HTTP&nbsp;GET&nbsp;companies/:country|
|With|next word is path segment and then repeated as path parameter|`getPersonWithName(name:String)`|HTTP&nbsp;GET&nbsp;person/name/:name|

The parameter name will then be bound into your method parameters if one of them has a mathing name.  Optional parameters should be nullable.

Soon, we will allow configuring additional replacement rules for word patterns so you can customize the behavior.

### Query and Form Parameters

Just add the parameter to your method signature, and it is looked for in the path parameters, query and form parameters.  Nothing is needed.  You can do simple parameters such as:

```kotlin
public fun MyContext.getPeopleByName(name: String): List<People>
```

Or you can use complex parameter such as an object:

```kotlin
public fun MyContext.getPeopleByQuery(query: Query): List<People>
```

In this case, it must receive parameters prefixed by `query.` such as `query.text`, `query.name`, `query.country` to fill in the values of the `Query` object in this example.

### Body as JSON

If a parameter is not satisfied from path, query, or form parameters, and it has `Content-Type` of `application/json` then if it is a complex parameter type it will bound from the body of the request using Jackson data binding.  

You can freely mix all parameter types, and the body will only be used if the others do not provide values for a parameter and it will only be used once.  An error will result if a complex parameter exists that cannot be satsified from the request.

### JSON Response

Any non-String return type becomes JSON automatically using Jackson to serialize the result.  This includes classes, lists, maps, and anything else Jackson can detect and serialize as JSON.

### Async and Longer Running Handlers 

Returning a Kovenant Promise will unwrap the promise when completed and use the resulting value as the response object.  This allows async methods.  EVERYTHING that isn't immediately resonsive should use Promises otherwise you block Vert.x IO thread, whereas a Promise dispathces on the Vert.x worker thread.  In the sample application, you can see in the company controller that the query method uses a promise return type and returns an `async {}` block of code.  You can also create a `Deferred` instead with more control over your Promise.

```kotlin
public fun RestContext.getCompaniesSearch(name: String?, country: String?): Promise<Set<Company>, Exception> {
    return async {
        val byName: List<Company> = name.whenNotNull { companyService.findCompanyByName(name!!) }.whenNotNull { listOf(it) } ?: emptyList()
        val byCountry: List<Company> = country.whenNotNull { companyService.findCompaniesByCountry(country!!) } ?: emptyList()
        (byName + byCountry).toSet()
    }
}
```

When using Kovenant promises, please see the section below about Vert.x + Kovenant.

### HTML Views 

If you want to render things as HTML, use a return type of String.  That will set the `Content-Type` as HTML and return the string. Soon, view support will come allowing you to add a view annotation that will use the result as a model to render a view given pluggable engines.  Promises can also be returned for views allowing async rendering.  More on that soon... 

### Redirects

For a redirect, just throw an `HttpRedirect(toUrl)` exception from anywhere in your controller to cause a redirect.  Our rational is that redirects are rare, or exception cases so we didn't want to force a standardized return type (such as ActionResult) just for these special cases, which typically never occur in REST api's and rarely in web frontend controllers.

### HTTP Errors

The following prebuilt exceptions are available to return HTTP errors.  Any other exception will always result in an HTTP status code 500.

```kotlin
open class HttpErrorUnauthorized() : HttpErrorCode("unauthorized", 401)
open class HttpErrorForbidden() : HttpErrorCode("forbidden", 403)
open class HttpErrorBadRequest() : HttpErrorCode("bad request", 400)
open class HttpErrorNotFound() : HttpErrorCode("not found", 404)

open class HttpErrorCode(message: String, val code: Int = 500, causedBy: Throwable? = null)
```

### Intercepts

You can intercept by putting another Vert.x handler before you bind the controller and that handler will be called before the controller, and it can decide whether the next handler is called or not.  Or, a controller can implement traits to intercept requests, failures, dispatching and also to create a custom context object factory.  See [VertxTraits.kt](vertx-jdk8/src/main/kotlin/uy/kohesive/kovert/vertx/VertxTraits.kt) for more information.

### Annotations

|Name|Where|Purpose|
|----|-----|-------|
|@VerbAlias|Controller|Set one method prefix alias to be used only by this controller|
|@VerbAliases|Controller|Set a list of method perfix aliases to be used only by this controller|
|@Location|Method|Set a specific path for a method, ignoring the method name other than for the prefix to infer the HTTP Verb.  Path parameters should be prefixed by a `:` such as `my/path/with/:param`|
|@Verb|Method|Set the HTTP Verb and default status success code for a method, optionally skipping part of the method name when infering the path|

If is typical to use `@Location` and `@Verb` together on a method, although they can be used individually.

If you use the `@Verb` annotation on a method, by default the prefix of the method name is parsed and thrown away so it really can be anything.  Or if you want to use the prefix as the first path segment you may use the skipPrefix parameter with value `false` such as `@Verb(HttpVerb.GET, skipPrefix = false) public fun SomeContext.someHappyMethod(): MyResult` would bind to `some/happy/method` whereas `skipPrefix = true` would bind to `happy/method`.

### Kovert Helpers

#### Vert.x + Kovenant Promises

For using Vert.x with [Kovenant](http://kovenant.komponents.nl) promises, you should launch Vert.x using one of the [Klutter/Vertx3](https://github.com/klutter/klutter/tree/master/vertx3) helper functions.  If you are NOT using these methods, then call `VertxInit.ensure()` before using your first Kovenant promise, and before using anything that involves data binding with Kovert.  Otherwise, using a helper startup function will do this for you automatically.  Note that you can also use the prettier `async { }` instead of Vert.x `executeBlocking()` when using Kovenant integration.

See [Klutter/Vertx3](https://github.com/klutter/klutter/tree/master/vertx3) for all Vert.x helper functions include JSON, Vertx-Web, Logging and Injekt modules.

#### Vert.x and KovertVerticle startup

Really, you should configure and launch Vert.x yourself (use helpers above, Klutter for config loading, etc.).  But to act as both a sample, and a quick start helper, There are two classes you can use to startup Vert.x enabled for everything described in this documentation.  Or use these as a samples to write your own:

*  [KovertVertx.kt](vertx-jdk8/src/main/kotlin/uy/kohesive/kovert/vertx/boot/KovertVertx.kt) 
*  [KovertVerticle.kt](vertx-jdk8/src/main/kotlin/uy/kohesive/kovert/vertx/boot/KovertVerticle.kt) 

The sample application [App.kt](vertx-example/src/main/kotlin/uy/kohesive/kovert/vertx/sample/App.kt) shows one use of these classes.

#### Injekt

Both for setting up JSON integrated with Kotlin, JDK 8 and Vert.x; and for integrated logging you may import Injekt modules.

Importing module `VertxInjektables` will provide an `ObjectMapper` singleton available for Jackson data binding that is shared with Vert.x, and Kovenant will be initialized correctly so that promises and async calls work in conjunction with Vert.x thread dispathcing, and you will have a logger factory configured routing any of your injected logging calls through the Vertx logging Facade.  Alterantively you can import the `VertxWithSlf4jInjektables` module for the same benefits, although your logging factory will be setup to be direct to SLF4j for application code.

See the sample application [App.kt](vertx-example/src/main/kotlin/uy/kohesive/kovert/vertx/sample/App.kt) which uses Injekt for configuration, `KovertVertx` and `KovertVerticle` classes to launch Vert.x and Kovert, data binding, logging and providing services.

### More Examples

View the [sample application](vertx-example/src/main/kotlin/uy/kohesive/kovert/vertx/sample/), and the [unit tests](vertx-jdk8/src/test/kotlin/uy/kohesive/kovert/vertx/) for more combinations of the previously described topics.  

### Road Map (random order)

* Undertow support as alternative to Vert.x
* SparkJava support as alternative to Vert.x and Undertow
* Configurable clauses in method names for substitution patterns (i.e. "By", "In", "With" are substitution patterns)
* View support (annotation that renders the result as a model, plus a return type of ViewAndModel for cases that may use different views from the same method)
* With View support, people will want to ask for the HREF from a given controller method, should be able to provide that in Kotlin M13, or can provide using the `val getSomeThing = fun MyContext.(param: String): MyObject { ... }` form of declaring a controller method already since `MyClass::getSomething` can reference that value, whereas in the other form, it is not referenceable in M12.  
* Data binding to support injekt for parameters not available in the incoming data (query, form, path, body)
* Ignore annotation for extension methods in controller that are not desired
