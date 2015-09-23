package uy.kohesive.kovert.vertx.test

import io.vertx.core.Vertx
import io.vertx.core.http.*
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.async
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uy.klutter.vertx.vertx
import uy.kohesive.kovert.core.*
import uy.kohesive.kovert.vertx.*
import java.util.concurrent.CountDownLatch
import kotlin.properties.Delegates
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@RunWith(VertxUnitRunner::class)
public class TestVertxBinding {
    var _vertx: Vertx by Delegates.notNull()
    var _server: HttpServer by Delegates.notNull()
    var _client: HttpClient by Delegates.notNull()
    var _router: Router by Delegates.notNull()
    val _serverPort: Int = 18080

    @Before
    public fun beforeTest(context: TestContext) {
        KovertConfig.reportStackTracesOnExceptions = false

        _vertx = vertx().get()  // use Kotlin wrapper to make sure Kovenent is setup to dispatch with vert.x nicely
        _router = Router.router(_vertx)
        _server = _vertx.createHttpServer(HttpServerOptions().setPort(_serverPort).setHost("localhost"))
        _client = _vertx.createHttpClient(HttpClientOptions().setDefaultHost("localhost").setDefaultPort(_serverPort))

        val latch = CountDownLatch(1);
        _server.requestHandler { _router.accept(it) }.listen { latch.countDown() }
        latch.await()
    }

    @After
    public fun afterTest() {
        KovertConfig.reportStackTracesOnExceptions = false

        _client.close()
        val latch = CountDownLatch(1);
        _server.close {
            latch.countDown()
        }
        latch.await()
    }

    @Test public fun testOneControllerWithAllTraits() {
        val controller = OneControllerWithAllTraits()
        _router.bindController(controller, "/one")

        // with context factory used
        _client.testServer(HttpMethod.GET, "/one", 200, "Hello")
        assertTrue(controller.aRequest)
        assertTrue(controller.aDispatch)
        assertNotNull(controller.aDispatchMember)
        assertFalse(controller.aFailure)
        assertTrue(controller.aContextCreated)

        controller.reset()

        // no context factory used, extends different context
        _client.testServer(HttpMethod.GET, "/one/two", 200, "Bye")
        assertTrue(controller.aRequest)
        assertTrue(controller.aDispatch)
        assertNotNull(controller.aDispatchMember)
        assertFalse(controller.aFailure)
        assertFalse(controller.aContextCreated)

        // one path is below another
        _client.testServer(HttpMethod.GET, "/one/two/three", 200, "dunno")
        assertTrue(controller.aRequest)
        assertTrue(controller.aDispatch)
        assertNotNull(controller.aDispatchMember)
        assertFalse(controller.aFailure)
        assertFalse(controller.aContextCreated)
    }


    @Test public fun testOneControllerWithAllTraitsFails() {
        val controller = OneControllerWithAllTraits()
        _router.bindController(controller, "/one")

        KovertConfig.reportStackTracesOnExceptions = true

        _client.testServer(HttpMethod.GET, "/one/but/fail500", 500)
        assertTrue(controller.aRequest)
        assertTrue(controller.aDispatch)
        assertNotNull(controller.aDispatchMember)
        assertTrue(controller.aFailure)
        assertTrue(controller.aContextCreated)

        controller.reset()

        _client.testServer(HttpMethod.GET, "/one/but/fail403", 403)
        assertTrue(controller.aFailure)

        controller.reset()

        _client.testServer(HttpMethod.GET, "/one/but/fail401", 401)
        assertTrue(controller.aFailure)

        controller.reset()

        _client.testServer(HttpMethod.GET, "/one/but/fail400", 400)
        assertTrue(controller.aFailure)

        controller.reset()

        _client.testServer(HttpMethod.GET, "/one/but/fail404", 404)
        assertTrue(controller.aFailure)
    }

    @Test public fun testRoutingContextNaturally() {
        _router.bindController(OneControllerWithAllTraits(), "/one")
        _router.bindController(ContextTestController(), "/two")

        _client.testServer(HttpMethod.GET, "/one/no/special/context", 200, assertResponse = "success")
        _client.testServer(HttpMethod.GET, "/two/no/special/context", 200, assertResponse = "success")

    }

    @Test public fun testOneControllerWithAllTraitsRedirects() {
        val controller = OneControllerWithAllTraits()
        _router.bindController(controller, "/one")

        _client.testServer(HttpMethod.GET, "/one/that/has/redirect", 302)
        assertTrue(controller.aRequest)
        assertTrue(controller.aDispatch)
        assertNotNull(controller.aDispatchMember)
        assertFalse(controller.aFailure)
        assertTrue(controller.aContextCreated)

        controller.reset()

        _client.testServer(HttpMethod.GET, "/one/nothing/and/fail", 500)
        assertTrue(controller.aFailure)

        controller.reset()

        _client.testServer(HttpMethod.GET, "/one/nothing/and/redirect", 302)
        assertFalse(controller.aFailure)

        _client.testServer(HttpMethod.PUT, "/one/return/nothing/is/ok", 200)

    }

    @Test public fun testOneControllerWithNullableParm() {
        _router.bindController(OneControllerWithAllTraits(), "/one")
        _client.testServer(HttpMethod.GET, "/one/missing/parameter?parm2=happy", assertResponse = "null happy")

    }

    @Test public fun testOneControllerWithAllTraitsPromisedResult() {
        val controller = OneControllerWithAllTraits()
        _router.bindController(controller, "/one")

        _client.testServer(HttpMethod.GET, "/one/promise/results", assertResponse = "I promised, I delivered")

        controller.reset()

        _client.testServer(HttpMethod.GET, "/one/promise/error", 403)
        assertTrue(controller.aFailure)
    }


    @Test public fun testJsonResponses() {
        val controller = JsonController()
        _router.bindController(controller, "/api")

        _client.testServer(HttpMethod.GET, "/api/people", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""")

        _client.testServer(HttpMethod.GET, "/api/people/named/Fred", assertResponse = """[{"name":"Fred","age":30}]""")
        _client.testServer(HttpMethod.GET, "/api/people/named/Tom", assertResponse = """[{"name":"Tom","age":20}]""")
        _client.testServer(HttpMethod.GET, "/api/people/named/Xyz", 404)

        _client.testServer(HttpMethod.GET, "/api/people/age/30", assertResponse = """[{"name":"Fred","age":30}]""")
        _client.testServer(HttpMethod.GET, "/api/people/age/20", assertResponse = """[{"name":"Tom","age":20}]""")
        _client.testServer(HttpMethod.GET, "/api/people/age/18", 404)


        _client.testServer(HttpMethod.GET, "/api/people/named/Fred/age/30", assertResponse = """[{"name":"Fred","age":30}]""")
        _client.testServer(HttpMethod.GET, "/api/people/named/Tom/age/20", assertResponse = """[{"name":"Tom","age":20}]""")

        _client.testServer(HttpMethod.GET, "/api/people2/named/Fred/age/30", assertResponse = """[{"name":"Fred","age":30}]""")
        _client.testServer(HttpMethod.GET, "/api/people2/named/Tom/age/20", assertResponse = """[{"name":"Tom","age":20}]""")
    }

    @Test public fun testVerbAlisesMore() {
        val controller = JsonControllerManyAliases()
        _router.bindController(controller, "/verby")

        _client.testServer(HttpMethod.GET, "verby/people1", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""", assertStatus = 200)
        _client.testServer(HttpMethod.GET, "verby/people2", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""", assertStatus = 200)

        _client.testServer(HttpMethod.PUT, "verby/person1", writeJson = """{ "name": "Fred", "age": 30 }""", assertStatus = 201, assertResponse = """{"name":"Fred","age":30}""")

    }

    @Test public fun testOtherAnnotations() {
        val controller = AnnotationsInsideController()
        _router.bindController(controller, "/api")

        _client.testServer(HttpMethod.GET, "/api/what/is/this/method1", assertResponse = """{"status":"OK"}""")
        _client.testServer(HttpMethod.GET, "/api/what/is/this/method2", assertResponse = """{"status":"OK"}""")
        _client.testServer(HttpMethod.GET, "/api/what/is/this/method3", assertResponse = """{"status":"OK"}""")
        _client.testServer(HttpMethod.GET, "/api/what/is/this/method4", assertResponse = """{"status":"OK"}""")
        _client.testServer(HttpMethod.GET, "/api/what/is/this/method5/MAYBE", assertResponse = """{"status":"MAYBE"}""")
    }

    @Test public fun testParameterBinding() {
        val controller = ParameterBindingController()
        _router.bindController(controller, "/api")

        _client.testServer(HttpMethod.GET, "/api/something/having/simple/parameters?parm1=20&parm2=Fred&parm3=true", assertResponse = """20, Fred, true""")
        _client.testServer(HttpMethod.GET, "/api/something/having/complex/parameter?parm1.name=Fred&parm1.age=30", assertResponse = """{"name":"Fred","age":30}""")
        _client.testServer(HttpMethod.GET, "/api/something/having/two/complex/parameters?parm1.name=Fred&parm1.age=30&parm2.name=Tom&parm2.age=20", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""")
    }

    @Test public fun testJsonBody() {
        val controller = ParameterBindingController()
        _router.bindController(controller, "/api")

        // json body
        _client.testServer(HttpMethod.PUT, "/api/something/as/json", writeJson = """{ "name": "Fred", "age": 30 }""", assertResponse = """{"name":"Fred","age":30}""")

        _client.testServer(HttpMethod.PUT, "/api/something/as/json/and/parameters?parm1.name=Tom&parm1.age=20", writeJson = """{ "name": "Fred", "age": 30 }""", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""")

        _client.testServer(HttpMethod.PUT, "/api/something/as/json/and/parameters?parm2.name=Fred&parm2.age=30", writeJson = """{ "name": "Tom", "age": 20 }""", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""")

    }

    @Test public fun testMemberVarFunctions() {
        val controller = MemberVarController()
        _router.bindController(controller, "/api")

        _client.testServer(HttpMethod.GET, "/api/first/test", assertResponse = "FirstTest")
        _client.testServer(HttpMethod.GET, "/api/second/test?parm=99", assertResponse = "SecondTest 99")
        _client.testServer(HttpMethod.POST, "/api/third/test?parm=dog", assertResponse = "ThirdTest dog")
    }
}

public class MemberVarController() {
    public val getFirstTest = fun TwoContext.(): String = "FirstTest"
    public val getSecondTest = fun TwoContext.(parm: Int): String = "SecondTest ${parm}"

    @Location("third/test")
    @Verb(HttpVerb.POST)
    public val getThirdyBaby = fun TwoContext.(parm: String): String = "ThirdTest ${parm}"
}

public class OneControllerWithAllTraits : InterceptRequest, InterceptDispatch<Any>, InterceptRequestFailure, ContextFactory<OneContext> {
    var aRequest: Boolean = false
    var aDispatch: Boolean = false
    var aDispatchMember: Any? = null
    var aFailure: Boolean = false
    var aFailureException: Throwable? = null
    var aFailureCode: Int = 0
    var aContextCreated: Boolean = false

    public fun reset() {
        aRequest = false
        aDispatch = false
        aDispatchMember = null
        aFailure = false
        aFailureException = null
        aFailureCode = 0
        aContextCreated = false
    }

    override fun interceptRequest(rawContext: RoutingContext, nextHandler: () -> Unit) {
        aRequest = true
        nextHandler()
    }

    override fun Any.interceptDispatch(member: Any, dispatcher: () -> Any?): Any? {
        aDispatch = true
        aDispatchMember = member
        return dispatcher()
    }

    override fun interceptFailure(rawContext: RoutingContext, nextHandler: () -> Unit) {
        aFailure = true
        aFailureException = rawContext.failure()
        aFailureCode = rawContext.statusCode()
        nextHandler()
    }

    override fun createContext(routingContext: RoutingContext): OneContext {
        aContextCreated = true
        return OneContext(routingContext)
    }

    public fun OneContext.get(): String {
        return "Hello"
    }

    public fun TwoContext.getTwo(): String {
        return "Bye"
    }

    public fun TwoContext.getTwoThree(): String {
        return "dunno"
    }

    public fun OneContext.getButFail500(): String {
        // make an error 500
        throw RuntimeException("Drat")
    }

    public fun OneContext.getButFail403(): String {
        throw HttpErrorForbidden()
    }

    public fun OneContext.getButFail401(): String {
        throw HttpErrorUnauthorized()
    }

    public fun OneContext.getButFail400(): String {
        throw HttpErrorBadRequest()
    }

    public fun OneContext.getButFail404(): String {
        throw HttpErrorNotFound()
    }

    public fun OneContext.getThatHasRedirect(): String {
        throw HttpRedirect("/one/two")
    }

    public fun OneContext.getNothingAndFail(): Unit {
        // will fail, because no return type, must redirect and doesn't
    }

    public fun OneContext.getNothingAndRedirect(): Unit {
        throw HttpRedirect("/one/two")
    }

    public fun OneContext.putReturnNothingIsOk(): Unit {

    }

    public fun OneContext.getPromiseResults(): Promise<String, Exception> {
        return async { "I promised, I delivered" }
    }

    public fun OneContext.getPromiseError(): Promise<String, Exception> {
        return async { throw HttpErrorForbidden() }
    }

    public fun OneContext.getMissingParameter(parm1: String?, parm2: String): String {
        return "${parm1} ${parm2}"
    }

    public fun RoutingContext.getNoSpecialContext(): String {
        return "success"
    }

}

public class ContextTestController {
    public fun RoutingContext.getNoSpecialContext(): String {
        return "success"
    }
}

public data class OneContext(private val context: RoutingContext)
public data class TwoContext(private val context: RoutingContext)

public data class Person(val name: String, val age: Int)
public data class RestResponse(val status: String = "OK")

@VerbAlias("find", HttpVerb.GET)
public class JsonController {
    public fun OneContext.listPeople(): List<Person> {
        return listOf(Person("Fred", 30), Person("Tom", 20))
    }

    public fun OneContext.findPeopleNamedByName(name: String): List<Person> {
        val people = listOf(Person("Fred", 30), Person("Tom", 20))
        val matchingPersons = people.groupBy { it.name }.map { it.getKey() to it.getValue() }.toMap().get(name)
        if (matchingPersons == null || matchingPersons.size() == 0) throw HttpErrorNotFound()
        return matchingPersons
    }

    public fun OneContext.findPeopleWithAge(age: Int): List<Person> {
        val people = listOf(Person("Fred", 30), Person("Tom", 20))
        val matchingPersons = people.groupBy { it.age }.map { it.getKey() to it.getValue() }.toMap().get(age)
        if (matchingPersons == null || matchingPersons.size() == 0) throw HttpErrorNotFound()
        return matchingPersons
    }

    public fun OneContext.findPeopleNamedByNameWithAge(name: String, age: Int): List<Person> {
        val people = listOf(Person("Fred", 30), Person("Tom", 20))
        val matchingPersons = people.groupBy { it.name }.map { it.getKey() to it.getValue() }.toMap().get(name)?.filter { it.age == age }
        if (matchingPersons == null || matchingPersons.size() == 0) throw HttpErrorNotFound()
        return matchingPersons
    }

    public fun OneContext.findPeople2_Named_ByName_Age_ByAge(name: String, age: Int): List<Person> {
        return findPeopleNamedByNameWithAge(name, age)
    }
}


@VerbAliases(VerbAlias("find", HttpVerb.GET),VerbAlias("search", HttpVerb.GET),VerbAlias("add", HttpVerb.PUT, 201))
public class JsonControllerManyAliases {
    public fun OneContext.findPeople1(): List<Person> {
        return listOf(Person("Fred", 30), Person("Tom", 20))
    }
    public fun OneContext.searchPeople2(): List<Person> {
        return listOf(Person("Fred", 30), Person("Tom", 20))
    }
    public fun OneContext.addPerson1(person: Person): Person {
        return person
    }
}

public class AnnotationsInsideController {
    @Verb(HttpVerb.GET, skipPrefix = false)
    public fun OneContext.whatIsThisMethod1(): RestResponse = RestResponse()

    @Verb(HttpVerb.GET, skipPrefix = true) // already the default
    public fun OneContext.skipWhatIsThisMethod2(): RestResponse = RestResponse()

    @Location("what/is/this/method3")
    public fun OneContext.getMethod3(): RestResponse = RestResponse()

    @Verb(HttpVerb.GET)
    @Location("what/is/this/method4")
    public fun OneContext.method4(): RestResponse = RestResponse()

    @Location("what/is/this/method5/:status")
    public fun OneContext.getMethod5(status: String): RestResponse = RestResponse(status)
}

public class ParameterBindingController {
    public fun OneContext.getSomethingHavingSimpleParameters(parm1: Int, parm2: String, parm3: Boolean): String {
        return "$parm1, $parm2, $parm3"
    }

    public fun OneContext.getSomethingHavingComplexParameter(parm1: Person): Person = parm1

    public fun OneContext.getSomethingHavingTwoComplexParameters(parm1: Person, parm2: Person): List<Person> = listOf(parm1, parm2)

    public fun OneContext.putSomethingAsJson(parm1: Person): Person = parm1

    public fun OneContext.putSomethingAsJsonAndParameters(parm1: Person, parm2: Person): List<Person> = listOf(parm2, parm1)
}
