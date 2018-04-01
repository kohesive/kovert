package uy.kohesive.kovert.vertx.test

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.RoutingContext
import nl.komponents.kovenant.Promise
import org.junit.Before
import org.junit.Test
import uy.kohesive.kovert.core.*
import uy.kohesive.kovert.vertx.AbstractKovertTest
import uy.kohesive.kovert.vertx.bindController

class TestRendering : AbstractKovertTest() {

    @Before
    override fun beforeTest() {
        KovertConfig.registerTemplateEngine(MockViewEngine("1"), ".mock1")
        KovertConfig.registerTemplateEngine(MockViewEngine("2"), "mock2", "xhtml")
        super.beforeTest()
    }

    @Test
    fun testAnnotatedRenderMethods() {
        val controller = MockController1()
        _router.bindController(controller, "/view")

        _client.testServer(
            HttpMethod.GET,
            "/view/test/mock/view1",
            assertResponse = """mocked:1 -- [test.mock1] -- Model1(name=Fred, age=33)""",
            assertContentType = "text/html"
        )
        _client.testServer(
            HttpMethod.GET,
            "/view/test/mock/view2",
            assertResponse = """mocked:1 -- [test.mock1] -- Model1(name=Fred, age=33)""",
            assertContentType = "text/html"
        )

        _client.testServer(HttpMethod.GET, "/view/test/mock/view/fail", 500)
    }

    @Test
    fun testAnnotatedRenderMemberWithFunction() {
        val controller = MockController1()
        _router.bindController(controller, "/view")

        _client.testServer(
            HttpMethod.GET,
            "/view/test/mock/view/slithie",
            assertResponse = """mocked:1 -- [test.mock1] -- Model1(name=Slithie, age=21)""",
            assertContentType = "text/html"
        )
    }

    @Test
    fun testDynamicRenderMethods() {
        val controller = MockController1()
        _router.bindController(controller, "/view")

        _client.testServer(
            HttpMethod.GET,
            "/view/test/returned/mock/view1",
            assertResponse = """mocked:1 -- [test.mock1] -- Model1(name=David, age=20)""",
            assertContentType = "text/html"
        )
        _client.testServer(
            HttpMethod.GET,
            "/view/test/returned/mock/view2",
            assertResponse = """mocked:1 -- [test.mock1] -- Model1(name=David, age=20)""",
            assertContentType = "text/html"
        )
    }

    @Test
    fun testBadEngineSelectionInAnnotation() {
        val controller = MockController1()
        _router.bindController(controller, "/view")

        _client.testServer(
            HttpMethod.GET,
            "/view/test/bad/engine1",
            404
        ) // it will not exist, logged as error on binding time
    }

    @Test
    fun testBadEngineSelectionDynamically() {
        val controller = MockController1()
        _router.bindController(controller, "/view")

        _client.testServer(HttpMethod.GET, "/view/test/bad/engine2", 500) // doesn't known until the request hits
    }

    @Test
    fun testContentTypeOverriding() {
        val controller = MockController1()
        _router.bindController(controller, "/view")

        _client.testServer(
            HttpMethod.GET,
            "/view/test/mock/view1/and/content/type/override",
            assertResponse = """mocked:1 -- [test.mock1] -- Model1(name=Danny, age=99)""",
            assertContentType = "overriden/type"
        )
        _client.testServer(
            HttpMethod.GET,
            "/view/test/mock/view2/and/content/type/from/engine",
            assertResponse = """mocked:2 -- [test.mock2] -- Model1(name=Danny, age=99)""",
            assertContentType = "xhtml"
        )
        _client.testServer(
            HttpMethod.GET,
            "/view/test/mock/view2/and/content/type/from/engine/overridden",
            assertResponse = """mocked:2 -- [test.mock2] -- Model1(name=Danny, age=99)""",
            assertContentType = "overriden/type2"
        )

    }

}

internal open class MockViewEngine(val id: String) : TemplateEngine {
    override fun render(template: String, model: Any): String {
        return "mocked:$id -- [$template] -- ${model}"
    }

    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean = other is MockViewEngine && other.id == id
}

internal class MockController1 {
    @Rendered("test.mock1")
    fun RoutingContext.getTestMockView1(): Model1 = Model1("Fred", 33)

    @Rendered("test.mock1")
    fun RoutingContext.getTestMockView2(): Promise<Model1, Exception> = Promise.ofSuccess(Model1("Fred", 33))

    @Rendered("test.mock1")
    fun RoutingContext.getTestMockViewFail(): Promise<Model1, Exception> = Promise.ofFail(Exception("Failed"))

    @Rendered
    fun RoutingContext.getTestReturnedMockView1(): ModelAndRenderTemplate<Model1> =
        ModelAndRenderTemplate(Model1("David", 20), "test.mock1")

    @Rendered
    fun RoutingContext.getTestReturnedMockView2(): Promise<ModelAndRenderTemplate<Model1>, Exception> =
        Promise.ofSuccess(ModelAndRenderTemplate(Model1("David", 20), "test.mock1"))

    @Rendered("bad.engine")
    fun RoutingContext.getTestBadEngine1(): Model1 = Model1("Bad", 0)

    @Rendered
    fun RoutingContext.getTestBadEngine2(): ModelAndRenderTemplate<Model1> =
        ModelAndRenderTemplate(Model1("Bad", 0), "bad.engine")

    @Rendered("test.mock1", contentType = "overriden/type")
    fun RoutingContext.getTestMockView1AndContentTypeOverride(): Model1 = Model1("Danny", 99)

    @Rendered("test.mock2")
    fun RoutingContext.getTestMockView2AndContentTypeFromEngine(): Model1 = Model1("Danny", 99)

    @Rendered("test.mock2", contentType = "overriden/type2")
    fun RoutingContext.getTestMockView2AndContentTypeFromEngineOverridden(): Model1 = Model1("Danny", 99)

    @Rendered("test.mock1")
    @Verb(HttpVerb.GET, skipPrefix = false)
    val testMockViewSlithie = fun RoutingContext.(): Model1 = Model1("Slithie", 21)
}

internal data class Model1(val name: String, val age: Int)