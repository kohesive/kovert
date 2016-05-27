package uy.kohesive.kovert.vertx.test

import io.vertx.core.http.HttpMethod
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.RoutingContext
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import uy.klutter.core.jdk8.utcNow
import uy.kohesive.kovert.core.*
import uy.kohesive.kovert.vertx.*
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@RunWith(VertxUnitRunner::class) class TestVertxControllerBinding : AbstractKovertTest() {

    @Test fun testOneControllerWithAllTraits() {
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


    @Test fun testOneControllerWithAllTraitsFails() {
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

    @Test fun testBodyReturnedWithErrorCode() {
        val controller = OneControllerWithAllTraits()
        _router.bindController(controller, "/one")

        KovertConfig.reportStackTracesOnExceptions = true

        _client.testServer(HttpMethod.GET, "/one/but/fail409/string/body", 409, assertResponse = "String Body")
        assertTrue(controller.aFailure)

        controller.reset()

        _client.testServer(HttpMethod.GET, "/one/but/fail409/json/body", 409, assertResponse = """{"status":"error","reason":"Not valid thingy"}""")
        assertTrue(controller.aFailure)
    }

    @Test fun testRoutingContextNaturally() {
        _router.bindController(OneControllerWithAllTraits(), "/one")
        _router.bindController(ContextTestController(), "/two")

        _client.testServer(HttpMethod.GET, "/one/no/special/context", 200, assertResponse = "success")
        _client.testServer(HttpMethod.GET, "/two/no/special/context", 200, assertResponse = "success")

    }

    @Test fun testOneControllerWithAllTraitsRedirects() {
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

    @Test fun testOneControllerWithNullableParm() {
        _router.bindController(OneControllerWithAllTraits(), "/one")
        _client.testServer(HttpMethod.GET, "/one/missing/parameter?parm2=happy", assertResponse = "null happy")

    }

    @Test fun testOneControllerWithDefaultableParm() {
        _router.bindController(OneControllerWithAllTraits(), "/one")
        _client.testServer(HttpMethod.GET, "/one/missing/defaultable?parm2=happy", assertResponse = "sad happy Defaulted")

        _client.testServer(HttpMethod.GET, "/one/missing/defaultable?parm2=happy&parm3.contents=notDefaulted", assertResponse = "sad happy notDefaulted")
    }

    // getOtherTypesIncludingNullables
    @Test fun testOneControllerWithOtherParamTypes() {
        _router.bindController(OneControllerWithAllTraits(), "/one")
        _client.testServer(HttpMethod.GET, "/one/other/types/including/nullables?parm3=TWO", assertResponse = "null null TWO null")
        _client.testServer(HttpMethod.GET, "/one/other/types/including/nullables?parm1=true&parm2=fish&parm3=TWO&parm4=THREE", assertResponse = "true fish TWO THREE")
    }

    @Test fun testOneControllerWithAllTraitsPromisedResult() {
        val controller = OneControllerWithAllTraits()
        _router.bindController(controller, "/one")

        _client.testServer(HttpMethod.GET, "/one/promise/results", assertResponse = "I promised, I delivered")

        controller.reset()

        _client.testServer(HttpMethod.GET, "/one/promise/error", 403)
        assertTrue(controller.aFailure)
    }


    @Test fun testJsonResponses() {
        val controller = JsonController()
        _router.bindController(controller, "/api")

        _client.testServer(HttpMethod.GET, "/api/people", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""", assertContentType = "application/json")

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

    @Test fun testVerbAlisesMore() {
        val controller = JsonControllerManyAliases()
        _router.bindController(controller, "/verby")

        _client.testServer(HttpMethod.GET, "verby/people1", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""", assertStatus = 200)
        _client.testServer(HttpMethod.GET, "verby/people2", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""", assertStatus = 200)


        _client.testServer(HttpMethod.GET, "verby/person1", assertStatus = 200, assertResponse = """{"name":"Fred","age":30}""")
        _client.testServer(HttpMethod.PUT, "verby/person1", writeJson = """{ "name": "Fred", "age": 30 }""", assertStatus = 201, assertResponse = """{"name":"Fred","age":30}""")
        _client.testServer(HttpMethod.POST, "verby/person1", writeJson = """{ "name": "Fred", "age": 30 }""", assertStatus = 200, assertResponse = """{"name":"Fred","age":30}""")

    }

    @Test fun testAltContentTypeWithEncoding() {
        val controller = JsonControllerManyAliases()
        _router.bindController(controller, "/verby")

        _client.testServerAltContentType(HttpMethod.PUT, "verby/person1", writeJson = """{ "name": "Fred", "age": 30 }""", assertStatus = 201, assertResponse = """{"name":"Fred","age":30}""")
        _client.testServerAltContentType(HttpMethod.POST, "verby/person1", writeJson = """{ "name": "Fred", "age": 30 }""", assertStatus = 200, assertResponse = """{"name":"Fred","age":30}""")
    }

    @Test fun testOtherAnnotations() {
        val controller = AnnotationsInsideController()
        _router.bindController(controller, "/api")

        _client.testServer(HttpMethod.GET, "/api/what/is/this/method1", assertResponse = """{"status":"OK"}""")
        _client.testServer(HttpMethod.GET, "/api/what/is/this/method2", assertResponse = """{"status":"OK"}""")
        _client.testServer(HttpMethod.GET, "/api/what/is/this/method3", assertResponse = """{"status":"OK"}""")
        _client.testServer(HttpMethod.GET, "/api/what/is/this/method4", assertResponse = """{"status":"OK"}""")
        _client.testServer(HttpMethod.GET, "/api/what/is/this/method5/MAYBE", assertResponse = """{"status":"MAYBE"}""")
    }

    @Test fun testParameterBinding() {
        val controller = ParameterBindingController()
        _router.bindController(controller, "/api")

        _client.testServer(HttpMethod.GET, "/api/something/having/simple/parameters?parm1=20&parm2=Fred&parm3=true", assertResponse = """20, Fred, true""")
        _client.testServer(HttpMethod.GET, "/api/something/having/complex/parameter?parm1.name=Fred&parm1.age=30", assertResponse = """{"name":"Fred","age":30}""")
        _client.testServer(HttpMethod.GET, "/api/something/having/two/complex/parameters?parm1.name=Fred&parm1.age=30&parm2.name=Tom&parm2.age=20", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""")
    }

    @Test fun testJsonBody() {
        val controller = ParameterBindingController()
        _router.bindController(controller, "/api")

        // json body
        _client.testServer(HttpMethod.PUT, "/api/something/as/json", writeJson = """{ "name": "Fred", "age": 30 }""", assertResponse = """{"name":"Fred","age":30}""")

        _client.testServer(HttpMethod.PUT, "/api/something/as/json/and/parameters?parm1.name=Tom&parm1.age=20", writeJson = """{ "name": "Fred", "age": 30 }""", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""")

        _client.testServer(HttpMethod.PUT, "/api/something/as/json/and/parameters?parm2.name=Fred&parm2.age=30", writeJson = """{ "name": "Tom", "age": 20 }""", assertResponse = """[{"name":"Fred","age":30},{"name":"Tom","age":20}]""")

    }

    @Test fun testMemberVarFunctions() {
        val controller = MemberVarController()
        _router.bindController(controller, "/api")

        _client.testServer(HttpMethod.GET, "/api/first/test", assertResponse = "FirstTest")
        _client.testServer(HttpMethod.GET, "/api/second/test?parm=99", assertResponse = "SecondTest 99")
        _client.testServer(HttpMethod.POST, "/api/third/test?parm=dog", assertResponse = "ThirdTest dog")
    }

    @Ignore("Need to figure out why the jackson bindings for Instant sometimes go bonkers")
    @Test fun testSpecialTypes() {
        val controller = ControllerWithSpecialTypes()
        _router.bindController(controller, "/api")

        val i = utcNow()
        _client.testServer(HttpMethod.GET, "/api/thing/${i.toEpochMilli()}", assertResponse = "{${i.toEpochMilli()}}")

    }

    @Test fun testNotFoundBadUrl() {
        val controller = ControllerWithSpecialTypes()
        _router.bindController(controller, "/api")

        _client.testServer(HttpMethod.GET, "/api/thingyNotHere", 404)
    }
}

class MemberVarController() {
    val getFirstTest = fun TwoContext.(): String = "FirstTest"
    val getSecondTest = fun TwoContext.(parm: Int): String = "SecondTest ${parm}"

    @Location("third/test")
    @Verb(HttpVerb.POST) val getThirdyBaby = fun TwoContext.(parm: String): String = "ThirdTest ${parm}"
}

class OneControllerWithAllTraits : InterceptRequest, InterceptDispatch<Any>, InterceptRequestFailure, ContextFactory<OneContext> {
    var aRequest: Boolean = false
    var aDispatch: Boolean = false
    var aDispatchMember: Any? = null
    var aFailure: Boolean = false
    var aFailureException: Throwable? = null
    var aFailureCode: Int = 0
    var aContextCreated: Boolean = false

    fun reset() {
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

    fun OneContext.get(): String {
        return "Hello"
    }

    fun TwoContext.getTwo(): String {
        return "Bye"
    }

    fun TwoContext.getTwoThree(): String {
        return "dunno"
    }

    fun OneContext.getButFail500(): String {
        // make an error 500
        throw RuntimeException("Drat")
    }

    fun OneContext.getButFail403(): String {
        throw HttpErrorForbidden()
    }

    fun OneContext.getButFail401(): String {
        throw HttpErrorUnauthorized()
    }

    fun OneContext.getButFail400(): String {
        throw HttpErrorBadRequest()
    }

    fun OneContext.getButFail404(): String {
        throw HttpErrorNotFound()
    }

    fun OneContext.getButFail409StringBody(): String {
        throw HttpErrorCodeWithBody("Invalid Request", 409, "String Body")
    }

    fun OneContext.getButFail409JsonBody(): String {
        throw HttpErrorCodeWithBody("Invalid Request", 409, CustomErrorBody())
    }

    data class CustomErrorBody(val status: String = "error", val reason: String = "Not valid thingy")

    fun OneContext.getThatHasRedirect(): String {
        throw HttpRedirect("/one/two")
    }

    fun OneContext.getNothingAndFail(): Unit {
        // will fail, because no return type, must redirect and doesn't
    }

    fun OneContext.getNothingAndRedirect(): Unit {
        throw HttpRedirect("/one/two")
    }

    fun OneContext.putReturnNothingIsOk(): Unit {

    }

    fun OneContext.getPromiseResults(): Promise<String, Exception> {
        return task { "I promised, I delivered" }
    }

    fun OneContext.getPromiseError(): Promise<String, Exception> {
        return task { throw HttpErrorForbidden() }
    }

    fun OneContext.getMissingParameter(parm1: String?, parm2: String): String {
        return "${parm1} ${parm2}"
    }

    data class DefaultableClass(val contents: String)

    fun OneContext.getMissingDefaultable(parm1: String = "sad", parm2: String, parm3: DefaultableClass = DefaultableClass("Defaulted")): String {
        return "${parm1} ${parm2} ${parm3.contents}"
    }

    enum class FooFoo { ONE, TWO, THREE }
    fun OneContext.getOtherTypesIncludingNullables(parm1: Boolean?, parm2: String?, parm3: FooFoo, parm4: FooFoo?): String {
        return "${parm1} ${parm2} ${parm3} ${parm4}"
    }

    fun RoutingContext.getNoSpecialContext(): String {
        return "success"
    }

}

class ContextTestController {
    fun RoutingContext.getNoSpecialContext(): String {
        return "success"
    }
}

data class OneContext(private val context: RoutingContext)
data class TwoContext(private val context: RoutingContext)

data class Person(val name: String, val age: Int)
data class RestResponse(val status: String = "OK")

@VerbAlias("find", HttpVerb.GET) class JsonController {
    fun OneContext.listPeople(): List<Person> {
        return listOf(Person("Fred", 30), Person("Tom", 20))
    }

    fun OneContext.findPeopleNamedByName(name: String): List<Person> {
        val people = listOf(Person("Fred", 30), Person("Tom", 20))
        val matchingPersons = people.groupBy { it.name }.map { it.key to it.value }.toMap().get(name)
        if (matchingPersons == null || matchingPersons.size == 0) throw HttpErrorNotFound()
        return matchingPersons
    }

    fun OneContext.findPeopleWithAge(age: Int): List<Person> {
        val people = listOf(Person("Fred", 30), Person("Tom", 20))
        val matchingPersons = people.groupBy { it.age }.map { it.key to it.value }.toMap().get(age)
        if (matchingPersons == null || matchingPersons.size == 0) throw HttpErrorNotFound()
        return matchingPersons
    }

    fun OneContext.findPeopleNamedByNameWithAge(name: String, age: Int): List<Person> {
        val people = listOf(Person("Fred", 30), Person("Tom", 20))
        val matchingPersons = people.groupBy { it.name }.map { it.key to it.value }.toMap().get(name)?.filter { it.age == age }
        if (matchingPersons == null || matchingPersons.size == 0) throw HttpErrorNotFound()
        return matchingPersons
    }

    fun OneContext.findPeople2_Named_ByName_Age_ByAge(name: String, age: Int): List<Person> {
        return findPeopleNamedByNameWithAge(name, age)
    }
}

class ControllerWithSpecialTypes {
    fun OneContext.getThingByDate(date: Instant): Instant {
        return date
    }
}


@VerbAliases(VerbAlias("find", HttpVerb.GET), VerbAlias("search", HttpVerb.GET), VerbAlias("add", HttpVerb.PUT, 201)) class JsonControllerManyAliases {
    fun OneContext.findPeople1(): List<Person> {
        return listOf(Person("Fred", 30), Person("Tom", 20))
    }

    fun OneContext.searchPeople2(): List<Person> {
        return listOf(Person("Fred", 30), Person("Tom", 20))
    }

    fun OneContext.addPerson1(person: Person): Person {
        return person
    }

    fun OneContext.postPerson1(person: Person): Person {
        return person
    }

    fun OneContext.getPerson1(): Person {
        return Person("Fred", 30)
    }
}

class AnnotationsInsideController {
    @Verb(HttpVerb.GET, skipPrefix = false)
    fun OneContext.whatIsThisMethod1(): RestResponse = RestResponse()

    @Verb(HttpVerb.GET, skipPrefix = true)
    fun OneContext.skipWhatIsThisMethod2(): RestResponse = RestResponse()

    @Location("what/is/this/method3")
    fun OneContext.getMethod3(): RestResponse = RestResponse()

    @Verb(HttpVerb.GET)
    @Location("what/is/this/method4")
    fun OneContext.method4(): RestResponse = RestResponse()

    @Location("what/is/this/method5/:status")
    fun OneContext.getMethod5(status: String): RestResponse = RestResponse(status)
}

class ParameterBindingController {
    fun OneContext.getSomethingHavingSimpleParameters(parm1: Int, parm2: String, parm3: Boolean): String {
        return "$parm1, $parm2, $parm3"
    }

    fun OneContext.getSomethingHavingComplexParameter(parm1: Person): Person = parm1

    fun OneContext.getSomethingHavingTwoComplexParameters(parm1: Person, parm2: Person): List<Person> = listOf(parm1, parm2)

    fun OneContext.putSomethingAsJson(parm1: Person): Person = parm1

    fun OneContext.putSomethingAsJsonAndParameters(parm1: Person, parm2: Person): List<Person> = listOf(parm2, parm1)
}
