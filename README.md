# Kovert

The invisible REST (and soon for web) framework.  Kovert is a simple framework that binds your Kotlin classes into your
Vert.x (and soon Undertow) routers.  It does not try to replace or rebuild these frameworks and only handles the task
of providing the "last mile" binding to your controllers.  From a fairly normal looking Kotlin class, Kovert can infer
the route path and parameters.  This is an experiment to see how far we can get without looking like JAX-RS.  If you
want that, see [Vertx-Nubes](https://github.com/aesteve/vertx-nubes) for Vert-x or [Kikaha](http://kikaha.skullabs.io) for Undertow.

You are expected to startup, configure and deploy a verticle in which you then call Kovert to bind a controller to a route.  
Kovert does provide `KovertVertx` and `KovertVerticle` classes that can bootstrap a base application, but almost all use 
cases beyond testing will provide and control vertx directly.  Kovert contains helper classes for starting Vertx, and 
uses [Kovenant](http://kovenant.komponents.nl) promises including ensuring that the dispatcher for Kovenant is shared with 
the worker dispatching in Vert.x so that Vert.x context is maintained on dispatch threads, and callbacks come as expected
by Vert.x as well.  Kovert also adds builder classes for Vert.x JSON objects, can add a singleton to [Injekt](https://github.com/kohesive/injekt) for the Jackson JSON mapper that is both compatible with Kotlin, and with Vert.x
JSON objects.  And Kovert can add and unify logging between Vert.x and SLF4j including setting up the Injekt logger factory.

### Learn by Example

For a full sample, view the [sample REST application](vertx-example/) to see an example that uses the helper classes `KovertVertx` and `KovertVerticle` to startup and run a vertx server using a configuration file.  More information is 
available in the sample.

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

### More Examples

View the sample application, and the unit tests for more combinations and examples of the previously described topics.  

### Road Map (random order)

* Undertow support as alternative to Vert.x
* Configurable clauses in method names for substitution patterns (i.e. "By", "In", "With" are substitution patterns)
* View support (annotation that renders the result as a model, plus a return type of ViewAndModel for cases that may use different views from the same method)
* With View support, people will want to ask for the HREF from a given controller method, should be able to provide that in Kotlin M13, or can provide using the `val getSomeThing = fun MyContext.(param: String): MyObject { ... }` form of declaring a controller method already since `MyClass::getSomething` can reference that value, whereas in the other form, it is not referenceable in M12.  
* Data binding to support injekt for parameters not available in the incoming data (query, form, path, body)
