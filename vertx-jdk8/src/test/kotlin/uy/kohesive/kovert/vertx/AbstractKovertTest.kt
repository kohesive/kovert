package uy.kohesive.kovert.vertx

import com.fasterxml.jackson.databind.SerializationFeature
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.Json
import io.vertx.ext.unit.TestContext
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import org.junit.After
import org.junit.Before
import uy.klutter.vertx.vertx
import uy.kohesive.kovert.core.KovertConfig
import java.util.concurrent.CountDownLatch
import kotlin.properties.Delegates

abstract class AbstractKovertTest {
    protected var _vertx: Vertx by Delegates.notNull()
    protected var _server: HttpServer by Delegates.notNull()
    protected var _client: HttpClient by Delegates.notNull()
    protected var _router: Router by Delegates.notNull()
    protected val _serverPort: Int = 18080

    @Before
    open public fun beforeTest() {
        KovertConfig.reportStackTracesOnExceptions = false
        Json.mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        Json.mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        Json.prettyMapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        Json.prettyMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);

        _vertx = vertx().get()  // use Kotlin wrapper to make sure Kovenent is setup to dispatch with vert.x nicely
        _router = Router.router(_vertx)
        _router.route().handler(BodyHandler.create())
        _server = _vertx.createHttpServer(HttpServerOptions().setPort(_serverPort).setHost("localhost"))
        _client = _vertx.createHttpClient(HttpClientOptions().setDefaultHost("localhost").setDefaultPort(_serverPort))

        val latch = CountDownLatch(1);
        _server.requestHandler { _router.accept(it) }.listen { latch.countDown() }
        latch.await()
    }

    @After
    open public fun afterTest() {
        KovertConfig.reportStackTracesOnExceptions = false

        _client.close()
        val latch = CountDownLatch(1);
        _server.close {
            latch.countDown()
        }
        latch.await()
    }
}