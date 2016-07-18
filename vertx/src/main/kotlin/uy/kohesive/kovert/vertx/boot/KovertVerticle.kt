package uy.kohesive.kovert.vertx.boot

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.global.global
import com.github.salomonbrys.kodein.instance
import io.vertx.core.AbstractVerticle
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.logging.Logger
import io.vertx.core.net.JksOptions
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.ClusteredSessionStore
import io.vertx.ext.web.sstore.LocalSessionStore
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import uy.klutter.config.typesafe.kodein.ConfigModule
import uy.klutter.core.common.*
import uy.klutter.vertx.promiseDeployVerticle
import java.util.concurrent.TimeUnit
import javax.swing.text.html.Option

object KovertVerticleModule {
    val configModule = Kodein.ConfigModule {
        bind<KovertVerticleConfig>() fromConfig (it)
    }

    val module = Kodein.Module {
        // other bindings
    }
}

class KovertVerticle private constructor(val cfg: KovertVerticleConfig, val customization: KovertVerticleCustomization = KovertVerticleCustomization(), val routerInit: Router.() -> Unit, val onListenerReady: (String) -> Unit) : AbstractVerticle() {
    companion object {
        val LOG: Logger = io.vertx.core.logging.LoggerFactory.getLogger(KovertVerticle::class.java)

        /**
         * Deploys a KovertVerticle into a Vertx instance and returns a Promise representing the deployment ID.
         * The HTTP listeners are active before the promise completes.
         */
        fun deploy(vertx: Vertx, kodein: Kodein = Kodein.global, cfg: KovertVerticleConfig = kodein.instance(), customization: KovertVerticleCustomization = KovertVerticleCustomization(), routerInit: Router.() -> Unit): Promise<String, Exception> {
            val deferred = deferred<String, Exception>()
            val completeThePromise = fun(id: String): Unit {
                LOG.warn("KovertVerticle is listening and ready.")
                deferred.resolve(id)
            }

            vertx.promiseDeployVerticle(KovertVerticle(cfg, customization, routerInit, completeThePromise)) success { deploymentId ->
                LOG.warn("KovertVerticle deployed as ${deploymentId}")
            } fail { failureException ->
                LOG.error("Vertx deployment failed due to ${failureException.message}", failureException)
                deferred.reject(failureException)
            }
            return deferred.promise
        }
    }

    override fun start() {
        LOG.warn("API Verticle starting")
        cfg.verify(LOG)
        customization.verify(LOG)

        val cookieHandler = customization.cookieHandler ?: CookieHandler.create()

        val sessionStore = if (vertx.isClustered()) ClusteredSessionStore.create(vertx) else LocalSessionStore.create(vertx)
        val sessionHandler = SessionHandler.create(sessionStore).setSessionTimeout(TimeUnit.HOURS.toMillis(cfg.sessionTimeoutInHours.toLong())).setNagHttps(false)

        val loggerHandler = customization.loggingHandler ?: LoggerHandler.create()

        try {
            val appRouter = Router.router(vertx) initializedBy { router ->
                fun applyHandlerToRoutePrefixes(handle: Handler<RoutingContext>, routePrefixes: OptionalHandlerRoutePrefixes, methods: List<HttpMethod> = emptyList()) {
                    val temp = when (routePrefixes) {
                        is OptionalHandlerRoutePrefixes.InstallOnAllRoutes -> {
                            listOf(router.route())
                        }
                        is OptionalHandlerRoutePrefixes.InstallOnSpecificRoutes -> {
                            routePrefixes.prefixes.map { router.route(it.mustStartWith('/').mustEndWith("/*")) }
                        }
                    }
                    temp.forEach { route -> methods.forEach { method -> route.method(method) } }
                    temp.forEach { route -> route.handler(handle) }
                }

                applyHandlerToRoutePrefixes(loggerHandler, customization.loggingHandlerRoutePrefixes)

                // setup CORS early, so that it prevents other code from running that shouldn't on CORS preflight checks
                if (customization.corsHandler != null) {
                    applyHandlerToRoutePrefixes(customization.corsHandler, customization.corsHandlerRoutePrefixes)
                }

                // body handle needs to be very early, or there is a chance the body is received before it is setup to consume it
                val bodyHandler = customization.bodyHandler ?: BodyHandler.create().setBodyLimit(customization?.bodyHandlerSizeLimit ?: DEFAULT_BODY_HANDLER_LIMIT)
                applyHandlerToRoutePrefixes(bodyHandler, customization.bodyHandlerRoutePrefixes,
                        listOf(HttpMethod.PUT, HttpMethod.POST, HttpMethod.DELETE, HttpMethod.PATCH))

                // TODO: we shouldn't waste effort put cookies on API routes
                applyHandlerToRoutePrefixes(cookieHandler, customization.cookieHandlerRoutePrefixes)
                // TODO: same for session handling, we are creating a new session for each API call (because most callers drop cookies on the floor)
                applyHandlerToRoutePrefixes(sessionHandler, customization.sessionHandlerRoutePrefixes,
                        listOf(HttpMethod.GET, HttpMethod.PUT, HttpMethod.POST, HttpMethod.DELETE, HttpMethod.PATCH))

                // TODO: which of the auth providers should be in the API routes, vs. Web + public

                // authentication
                if (customization.authProvider != null) {
                    val userSessionHandler = UserSessionHandler.create(customization.authProvider)
                    applyHandlerToRoutePrefixes(userSessionHandler, customization.authHandlerRoutePrefixes,
                            listOf(HttpMethod.GET, HttpMethod.PUT, HttpMethod.POST, HttpMethod.DELETE, HttpMethod.PATCH))
                }

                // auth handler (checks login, if not does something)
                if (customization.authHandler != null) {
                    applyHandlerToRoutePrefixes(customization.authHandler, customization.authHandlerRoutePrefixes)
                }

                // give the user a chance to bind more routes, for example their controllers
                router.routerInit()

                cfg.publicDirs.forEach { mountPoint ->
                    val mountPath = mountPoint.mountAt.mustStartWith('/').mustNotEndWith('/')
                    val mountRoute = if (mountPath.isBlank() || mountPath == "/") {
                        router.route()
                    } else {
                        router.route(mountPath)
                    }
                    LOG.info("Mounting static asset ${mountPoint.dir} at route ${mountPath}")
                    val mountHandler = StaticHandler.create(mountPoint.dir)
                    // TODO: allow configuration of cache control headers for StaticHandler
                    mountRoute.handler(mountHandler)
                }
            }

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
            }

            LOG.warn("API Verticle successfully started")
            onListenerReady(deploymentID())
        } catch (ex: Throwable) {
            LOG.error("API Verticle failed to start, fatal error.  ${ex.message}", ex)
            // terminate the app
            throw RuntimeException("Fatal startup error", ex)
        }
    }
}

internal val DEFAULT_BODY_HANDLER_LIMIT: Long = 32 * 1024

sealed class OptionalHandlerRoutePrefixes {
    class InstallOnAllRoutes(): OptionalHandlerRoutePrefixes()
    open class InstallOnSpecificRoutes(val prefixes: List<String>): OptionalHandlerRoutePrefixes()
    class InstallOnNoRoutes(): InstallOnSpecificRoutes(emptyList())

    companion object {
        fun none() = InstallOnNoRoutes()
        fun all() = InstallOnAllRoutes()
        fun prefixedBy(prefixes: List<String>) = InstallOnSpecificRoutes(prefixes)
    }
}
data class KovertVerticleCustomization(
        val cookieHandler: CookieHandler? = null,
        val cookieHandlerRoutePrefixes: OptionalHandlerRoutePrefixes = OptionalHandlerRoutePrefixes.all(),
        val sessionHandlerRoutePrefixes: OptionalHandlerRoutePrefixes = OptionalHandlerRoutePrefixes.all(),
        val bodyHandler: BodyHandler? = null,
        val bodyHandlerRoutePrefixes: OptionalHandlerRoutePrefixes = OptionalHandlerRoutePrefixes.all(),
        val bodyHandlerSizeLimit: Long = DEFAULT_BODY_HANDLER_LIMIT,
        val corsHandler: CorsHandler? = null,
        val corsHandlerRoutePrefixes: OptionalHandlerRoutePrefixes = OptionalHandlerRoutePrefixes.all(),
        val authProvider: AuthProvider? = null,
        val authHandler: AuthHandler? = null,
        val authHandlerRoutePrefixes: OptionalHandlerRoutePrefixes = OptionalHandlerRoutePrefixes.all(),
        val loggingHandler: Handler<RoutingContext>? = null,
        val loggingHandlerRoutePrefixes: OptionalHandlerRoutePrefixes = OptionalHandlerRoutePrefixes.all()) {
    fun verify(LOG: Logger) {
        // TODO: check that the right combination of parameters exist or throw exception
    }
}

data class KovertVerticleConfig(val listeners: List<HttpListenerConfig>,
                                val publicDirs: List<DirMountConfig>,
                                val sessionTimeoutInHours: Int) {
    fun verify(LOG: Logger) {
        // TODO: check that the right combination of parameters exist or throw exception
    }
}

data class HttpListenerConfig(val host: String, val port: Int, val ssl: HttpSslConfig?)
data class HttpSslConfig(val enabled: Boolean, val keyStorePath: String?, val keyStorePassword: String?)
data class DirMountConfig(val mountAt: String, val dir: String)


