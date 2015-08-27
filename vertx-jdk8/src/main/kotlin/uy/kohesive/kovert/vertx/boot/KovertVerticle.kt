package uy.kohesive.kovert.vertx.boot

import io.vertx.core.AbstractVerticle
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.logging.Logger
import io.vertx.core.net.JksOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.ClusteredSessionStore
import io.vertx.ext.web.sstore.LocalSessionStore
import nl.komponents.kovenant.async
import uy.klutter.core.common.initializedBy
import uy.klutter.core.jdk.mustEndWith
import uy.klutter.core.jdk.mustNotEndWith
import uy.klutter.core.jdk.mustStartWith
import uy.klutter.core.jdk.nullIfBlank
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.config.typesafe.KonfigModule
import uy.kohesive.injekt.config.typesafe.KonfigRegistrar
import uy.kohesive.kovert.vertx.bindController
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

public object KovertVerticleModule : KonfigModule, InjektModule {
    override fun KonfigRegistrar.registerConfigurables() {
        bindClassAtConfigRoot<KovertVerticleConfig>()
    }

    override fun InjektRegistrar.registerInjectables() {

    }
}

public class KovertVerticle(val routerInit: Router.()->Unit, val cfg: KovertVerticleConfig = Injekt.get(), val onListenerReady: (KovertVerticle)->Unit = {}) : AbstractVerticle() {
    val LOG: Logger = io.vertx.core.logging.LoggerFactory.getLogger(this.javaClass)

    override fun start() {
        LOG.warn("API Verticle starting")

        val cookieHandler = CookieHandler.create()
        fun cookieHandlerFactory() = cookieHandler

        val sessionStore = if (vertx.isClustered()) ClusteredSessionStore.create(vertx) else LocalSessionStore.create(vertx)
        val sessionHandler = SessionHandler.create(sessionStore).setSessionTimeout(TimeUnit.HOURS.toMillis(cfg.sessionTimeoutInHours.toLong())).setNagHttps(false)
        fun sessionHandlerFactory() = sessionHandler

        async {
            val appRouter = Router.router(vertx) initializedBy { router ->
                router.route().handler(LoggerHandler.create())
                router.route().handler(cookieHandlerFactory())
                router.route().handler(sessionHandlerFactory())

                router.routerInit()
            }

            val assetRouter = appRouter initializedBy { router ->
                cfg.publicDirs.forEach { mountPoint ->
                    val mountPath = mountPoint.mountAt.mustStartWith('/').mustNotEndWith('/')
                    val mountRoute = if (mountPath.isBlank() || mountPath == "/") {
                        router.route()
                    } else {
                        router.route(mountPath)
                    }
                    LOG.info("Mounting static asset ${mountPoint.dir} at route ${mountPath}")
                    val mountHandler = StaticHandler.create(mountPoint.dir)
                    mountRoute.handler(mountHandler)
                }
            }

            // appRouter.mountSubRouter("/", assetRouter)

            cfg.listeners.forEach { listenCfg ->
                val httpOptions = HttpServerOptions()
                val scheme = if (listenCfg.ssl != null && listenCfg.ssl.enabled) {
                    httpOptions.setSsl(true).setKeyStoreOptions(JksOptions()
                            .setPath(listenCfg.ssl.keyStorePath!!)
                            .setPassword(listenCfg.ssl.keyStorePassword.nullIfBlank()))
                    "HTTPS"
                } else {
                    "HTTP"
                }
                vertx.createHttpServer(httpOptions).requestHandler { appRouter.accept(it) }.listen(listenCfg.port)
                LOG.warn("${scheme}:${listenCfg.port} server started and ready.")

                onListenerReady(this)
            }

        }.success {
            LOG.warn("API Verticle successfully started")
        }.fail {  ex ->
            LOG.error("API Verticle failed to start, fatal error.  ${ex.getMessage()}", ex)
            // terminate the app
            throw RuntimeException("Fatal startup error", ex)
        }
    }
}

public data class KovertVerticleConfig(val listeners: List<HttpListenerConfig>,
                                       val publicDirs: List<DirMountConfig>,
                                       val sessionTimeoutInHours: Int)
public data class HttpListenerConfig(val host: String, val port: Int, val ssl: HttpSslConfig?)
public data class HttpSslConfig(val enabled: Boolean, val keyStorePath: String?, val keyStorePassword: String?)
public data class DirMountConfig(val mountAt: String, val dir: String)


