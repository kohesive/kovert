package uy.kohesive.kovert.template.handlebars.test

import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.io.ClassPathTemplateLoader
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.RoutingContext
import org.junit.Before
import org.junit.Test
import uy.kohesive.kovert.core.KovertConfig
import uy.kohesive.kovert.core.Rendered
import uy.kohesive.kovert.template.handlebars.KovertHandlebarsTemplateEngine
import uy.kohesive.kovert.vertx.AbstractKovertTest
import uy.kohesive.kovert.vertx.bindController
import uy.kohesive.kovert.vertx.test.testServer

public class TestKovertHandlebarsTemplateEngine : AbstractKovertTest() {

    @Before
    override public fun beforeTest() {
        val loader = ClassPathTemplateLoader()
        loader.setPrefix("/templates")
        val handlebars = Handlebars(loader)
        KovertConfig.registerTemplateEngine(KovertHandlebarsTemplateEngine(handlebars), ".html.hbs", "text/html")
        super.beforeTest()
    }

    @Test public fun testHandlebars() {
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
    @Rendered("test-people-search.html.hbs")
    fun RoutingContext.getSearchPeople(): PeopleResults {
        return PeopleResults(listOf(Person("Frank", 88), Person("Dave", 20)))
    }
}

internal data class PeopleResults(val persons: List<Person>)
internal data class Person(val name: String, val age: Int)
