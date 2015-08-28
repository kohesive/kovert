# Kovert

The invisible REST (and soon for web) framework.  

Kovert is a simple framework that binds your Kotlin classes into your Vert.x 3 (and soon Undertow) routers.  It does not try to replace or rebuild these frameworks and only handles the task of providing the "last mile" binding to your controllers.  From a fairly normal looking Kotlin class, Kovert can infer the route path and parameters.  

This is an experiment to see how far we can get without looking like JAX-RS.  If you want tons of JAX-RS annotations instead of happy Kovert inference, please use [Vertx-Nubes](https://github.com/aesteve/vertx-nubes) for Vert-x and [Kikaha](http://kikaha.skullabs.io) for Undertow, or take a peek at [SparkJava](http://sparkjava.com).

With Kovert, you are expected to startup, configure and deploy a Vert-x verticle in which you then ask Kovert to bind a controller to an existing route.  For that sentence to make sense, you should be familiar with [Vertx-Web](http://vertx.io/docs/vertx-web/java/) and the basics of [Vertx](http://vertx.io/docs/vertx-core/java/) 

If you just want to get started without knowing too much, Kovert does provide `KovertVertx` and `KovertVerticle` classes that can bootstrap a base application, but almost all use cases beyond testing will provide and control Vert-x directly.  

In addition, Kovert contains helper classes for starting Vert-x that use [Kovenant](http://kovenant.komponents.nl) promises -- including ensuring that the dispatcher for Kovenant is unified with the thread dispatching in Vert.x so that Vert.x context is maintained on dispatch threads, and callbacks come as expected by Vert.x as well.  There are additional Kovert helpers for JSON, web, logging, and more.  See those topics below...

### Learn by Example

For a full sample, view the [sample REST application](vertx-example/) to see an example that uses the helper classes `KovertVertx` and `KovertVerticle` to startup and run a vertx server using a configuration file.  More information is 
available in the sample.  It helps if you are familiar with [Injekt](https://github.com/kohesive/injekt) to completely understand the sample.  Injekt is not required for use of Kovert and can be bypassed, but it helps!

### Binding a Controller

To bind a controller is simple.  When you have a route object, call the extension method `bindController`:

```kotlin
route.bindController(MyControllerClass(), "/api/mystuff")
```

And the controller class is any class that contains methods that are extension functions on a class that you wish to be
your dispatch context.  So you can either decide that dispatch context is the raw `RoutingContext` of Vert.x, or it is much
better to isolate your code from the raw Vert.x and use simple classes that extract elements of the `RoutingContext` and make
them available in a type-safe way.  Any class that has a 1 parameter constructor of type `RoutingContext` can be a context class, an example:

```kotlin
class RestContext(private val routingContext: RoutingContext) {
    public val user: User by Delegates.lazy { routingContext.user() }
}
```

With the dispatch object selected, write your controller:

```kotlin
lass CompanyController(val companyService: CompanyService = Injekt.get()) {
    public fun RestContext.getCompanyByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    public fun RestContext.putCompanyByName(name: String, company: Company): Company {
        if (!name.equals(company.name, ignoreCase = true)) {
            throw HttpErrorBadRequest()
        }
        companyService.upsertCompany(company)
        return company
    }

    public fun RestContext.listCompanyByNameEmployees(name: String): List<Person> {
        return companyService.listEmployeesOfCompany(name) ?: throw HttpErrorNotFound()
    }

    public fun RestContext.findCompaniesNamedByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()

    public fun RestContext.findCompaniesLocatedInCountry(country: String): List<Company> {
        val found = companyService.findCompaniesByCountry(country)
        if (found.isEmpty()) throw HttpErrorNotFound()
        return found
    }

    public fun RestContext.getCompaniesQuery(name: String?, country: String?): Set<Company> {
        val byName: List<Company> = name.whenNotNull { companyService.findCompanyByName(name!!) }.whenNotNull { listOf(it) } ?: emptyList()
        val byCountry: List<Company> = country.whenNotNull { companyService.findCompaniesByCountry(country!!) } ?: emptyList()
        return (byName + byCountry).toSet()
    }
}
```

Ok, that looks like normal Kotlin methods that are extension methods on `RestContext` class.  That means each method has access
to values of the context and only those values.  You can actually have a different context for each method if you want, and
the correct context will be created for dispatching to that method.

With the controller class, path names are generated from the method names unless you use a `@location` annotation to specify
a specific path.  Or you use a `@verb` annotation to decide what HTTP verb and resulting success status code should be used.
Kovert is designed to avoid using those annotations, and they should appear ONLY in special cases.  So how does Kovert decide the path names?

First, it camel case parses the method name with the first part indicating the HTTP verb, and the rest being segments of the path.  When it encounters special words, it creates path parameters.  The camel case parsing rules are:

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

So let's talk first about the HTTP verb.  Obviously the method name prefixes of "get", "put", "post", "delete", "patch" will generate a route that is for the verb of the same name.  But you can see in `KovertConfig` that aliases are defined such as "list" and "view" for HTTP GET, and "remove" also works for "delete".  You can change the alias list in `KovertConfig` using the `addVerbAlias` or `removeVerbAlias` methods.  You can also specify aliases in the `bindController` method (above), or as annotations `@VerbAliases` and `@VerbAlias` on your controller class.  The sample application modifies `KovertConfig` with:

```kotlin
KovertConfig.addVerbAlias("find", HttpVerb.GET)
```

If you use the `@Verb` annotation on a method, by default the prefix of the method name is parsed and thrown away so it really can be anything.  Or you can use it as the first path segment using the skipPrefix paramter such as `@Verb(HttpVerb.GET, skipPrefix = false) public fun SomeContext.someHappyMethod(): MyResult` binding to `some/happy/method`

All routing path and parameter decisions are logged to the current logger, so  you can easily see the results of the `bindController` method.  The example above, would generate these paths when bound at "/api" route:

|Method|Verb|Path (w/parameters)|
|------|----|-------------------|
|`getCompanyByName(name: String)`|GET|`api/company/:name`|
|`putCompanyByName(name: String)`|PUT|`api/company/:name`|
|`listCompanyByNameEmployees(name: String)`|GET|`api/company/:name/employees`|
|`findCompaniesNamedByName(name: String)`|GET|`api/companies/named/:name`|
|`findCompaniesLocatedInCountry(country: String)`|GET|`api/companies/located/:country`|
|`getCompaniesQuery(name: String, country: String)`|GET|`api/companies/query?name=xyz&country=abc`|

### Path Parameters

Previously, we mentioned that you can use special words to create path parameters, here they are:

|word|description|example|result|
|----|-----------|-------|------|
|By|next word is path parameter|getCompanyByName(name: String)|HTTP GET company/:name|
|In|same as By|getCompaniesInCountry(country: String)|HTTP GET companies/:country|
|With|next word is path segment and then repeated as path parameter|getPersonWithName(name: String)|HTTP GET person/name/:name|

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

If the request body has `Content-Type` of `application/json` then after checking the path, query and form parameters for a parameter, if it is a complex parameter then the body of the document will be bound using Jackson into the object using Jackson data binding.  You can mix all parameter types, and the body will only be used if the others do not provide values for a parameter and it will only be used once.  An error will result if a complex parameter exists that cannot be satsified from the request.

Returning a Kovenant Promise will unwrap the promise when completed and use the resulting value as the response object.  This allows async methods.  Anything that isn't immediately resonsive should use promises.  In the sample application, you can see in the company controller that the query method uses a promise return type and returns an `async {}` block of code.  You can also create a `Deferred` instead with more control over your Promise.

```kotlin
public fun RestContext.getCompaniesQuery(name: String?, country: String?): Promise<Set<Company>, Exception> {
    return async {
        val byName: List<Company> = name.whenNotNull { companyService.findCompanyByName(name!!) }.whenNotNull { listOf(it) } ?: emptyList()
        val byCountry: List<Company> = country.whenNotNull { companyService.findCompaniesByCountry(country!!) } ?: emptyList()
        (byName + byCountry).toSet()
    }
}
```

When using Kovenant promises, please see the section below about Vert.x + Kovenant.

### HTML Views 

If you want to render things as HTML, use a return type of String.  That will set the `Content-Type` as HTML and return the string. Soon, view support will come allowing you to add a view annotation that will use the result as a model to render a view given pluggable engines.  More on that soon... 

### JSON Response

Any non-String return type becomes JSON automatically using Jackson to serialize the result.  This includes classes, lists, maps, and anything else Jackson can detect and serialize as JSON.

### Redirects

For a redirect, just throw an `HttpRedirect(toUrl)` exception from anywhere in your controller to cause a redirect.  Our rational is that redirects are rare, or exception cases so we didn't want to force a standardized return type (such as ActionResult) just for these special cases, which typically never occur in REST api's and rarely in web frontend controllers.

### HTTP Errors

The following prebuilt exceptions are available to return HTTP errors.  Any other exception will always result in an HTTP status code 500.

```kotlin
open class HttpErrorUnauthorized() : HttpErrorCode("unauthorized", 401)
open class HttpErrorForbidden() : HttpErrorCode("forbidden", 403)
open class HttpErrorBadRequest() : HttpErrorCode("bad request", 400)
open class HttpErrorNotFound() : HttpErrorCode("not found", 404)

open class HttpErrorCode(message: String, val code: Int = 500, causedBy: Throwable? = null) : Exception(message, causedBy) {
}
```

### Intercepts

A controller can implement traits to intercept requests, failures, dispatching and create a custom context object factory.  See [VertxTraits.kt](vertx-jdk8/src/main/kotlin/uy/kohesive/kovert/vertx/VertxTraits.kt) for more information.

### Kovert Helpers

#### Vert.x + Kovenant Promises

For using Vert.x with [Kovenant](http://kovenant.komponents.nl), you should launch Vert.x using one of the Kovert helper functions.  If not using these methods, then call `VertxInit.ensure()` before using your first Kovenant promise, and before using anything that involves data binding with Kovert.  Otherwise, using a helper startup function will do this for you automatically.  Note that you can use the prettier `async { }` instead of Vert.x `executeBlocking()` when using Kovenant integration.

See [Vertx.kt](vertx-jdk8/src/main/kotlin/uy/kohesive/kovert/vertx/Vertx.kt) for all Vert.x helper functions.

#### Vert.x Web

A few methods are availble to help using Vert.x web `Session` and `RoutingContext` including safely creating an externalized URL that takes into account proxies / load balancers.  See [VertxWeb.kt](vertx-jdk8/src/main/kotlin/uy/kohesive/kovert/vertx/VertxWeb.kt) for more.

#### JSON

Kovert also adds builder classes for Vert.x JSON objects (see [VertxJson.kt](vertx-jdk8/src/main/kotlin/uy/kohesive/kovert/vertx/VertxJson.kt) for more:

```kotlin
val json = jsonBuilder {
    // call methods on JsonObject()
}
```

Since Vert.x uses an instance of Jackson internally, you can set it up to work with Kotlin and JDK 8 classes by calling this helper method, and then use the instance `Json.mapper` shared with Vert.x for all data binding.

```kotlin
setupVertxJsonForKotlin()
```

See [VertxUtil.kt](vertx-jdk8/src/main/kotlin/uy/kohesive/kovert/vertx/VertxUtil.kt) for the implementation.

#### Logging

Both Vert-x and Hazelcast (used to cluster Vertx) log through facades.  You can setup those facades to go through SLF4j by setting system properties.  Or you can call this helper method that will configure both of those frameworks to talk to SLF4j by using:

```kotlin
setupVertxLoggingToSlf4j()
```

See [VertxUtil.kt](vertx-jdk8/src/main/kotlin/uy/kohesive/kovert/vertx/VertxUtil.kt) for the implementation.

#### Injekt

Both setting up the JSON singleton, and logging can also be done using Injekt.  If you import the Injekt module `VertxInjektables` you will have an `ObjectMapper` singleton available for Jackson data binding that is shared with Vert.x, and Kovenant will be initialized correctly so that promises and async calls work in conjunction with Vert.x thread dispathcing, and you will have a logger factory configured routing any of your injected logging calls through the Vertx logging Facade.  Alterantively you can import the `VertxWithSlf4jInjektables` module for the same benefits, although your logging factory will be setup to be direct to SLF4j for application code.

When using the `KovertVertx` and `KovertVerticle` classes to launch Vert.x and Kovert, they expect configuration objects to be present, or available for injection.  See the sample application for an example of making that configuration available through Injekt.

See [Injektables.kt](vertx-jdk8/src/main/kotlin/uy/kohesive/kovert/vertx/Injektables.kt) for the Injekt modules.

### More Examples

View the sample application, and the unit tests for more combinations and examples of the previously described topics.  

### Road Map (random order)

* Undertow support as alternative to Vert.x
* Configurable clauses in method names for substitution patterns (i.e. "By", "In", "With" are substitution patterns)
* View support (annotation that renders the result as a model, plus a return type of ViewAndModel for cases that may use different views from the same method)
* With View support, people will want to ask for the HREF from a given controller method, should be able to provide that in Kotlin M13, or can provide using the `val getSomeThing = fun MyContext.(param: String): MyObject { ... }` form of declaring a controller method already since `MyClass::getSomething` can reference that value, whereas in the other form, it is not referenceable in M12.  
* Data binding to support injekt for parameters not available in the incoming data (query, form, path, body)
