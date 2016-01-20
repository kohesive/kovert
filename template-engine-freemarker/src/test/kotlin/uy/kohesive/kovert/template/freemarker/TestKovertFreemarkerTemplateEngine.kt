package uy.kohesive.kovert.template.freemarker.test

import freemarker.cache.TemplateNameFormat
import freemarker.template.Configuration
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.RoutingContext
import org.junit.Before
import org.junit.Test
import uy.kohesive.kovert.core.KovertConfig
import uy.kohesive.kovert.core.Rendered
import uy.kohesive.kovert.template.freemarker.KovertFreemarkerTemplateEngine
import uy.kohesive.kovert.vertx.AbstractKovertTest
import uy.kohesive.kovert.vertx.bindController
import uy.kohesive.kovert.vertx.test.testServer

public class TestKovertFreemarkerTemplateEngine : AbstractKovertTest() {

    @Before
    override public fun beforeTest() {
        KovertConfig.registerTemplateEngine(KovertFreemarkerTemplateEngine(FreeMarker.engine), ".html.ftl", "text/html")
        super.beforeTest()
    }

    @Test public fun testFreemarker() {
        val controller = RenderController()
        _router.bindController(controller, "/app")

        _client.testServer(HttpMethod.GET, "/app/search/people", assertResponse = """
<html>
<body>
    <div>Frank: 88</div>
    <div>Dave: 20</div>
</body>
</html>""", assertContentType = "text/html")
    }
}

internal class RenderController {
    @Rendered("test-people-search.html.ftl")
    fun RoutingContext.getSearchPeople(): PeopleResults {
        return PeopleResults(listOf(Person("Frank", 88), Person("Dave", 20)))
    }
}

internal data class PeopleResults(val persons: List<Person>)
internal data class Person(val name: String, val age: Int)


// configured Freemarker
internal object FreeMarker {
    val engine: Configuration = run {
        val cfg = Configuration(Configuration.VERSION_2_3_22)
        cfg.setDefaultEncoding("UTF-8")
        cfg.setTemplateNameFormat(TemplateNameFormat.DEFAULT_2_4_0)
        // cfg.setObjectWrapper(JodaAwareObjectWrapper())

        // TODO: dev mode only
        cfg.setClassLoaderForTemplateLoading(ClassLoader.getSystemClassLoader(), "templates")
        // alternatively, some locatinn on disk
        //  cfg.setDirectoryForTemplateLoading(File("src/main/resources/templates"))

        // TODO: dev mode only
        cfg.setTemplateExceptionHandler(freemarker.template.TemplateExceptionHandler.HTML_DEBUG_HANDLER)
        // alternatively, RETHROW_HANDLER better for prod
        cfg
    }
}

