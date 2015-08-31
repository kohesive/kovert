package uy.kohesive.kovert.vertx.boot

import io.vertx.core.AbstractVerticle
import io.vertx.core.Verticle
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.logging.Logger
import io.vertx.core.net.JksOptions
import io.vertx.ext.web.*
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import uy.klutter.core.common.*
import uy.klutter.core.jdk.*
import uy.klutter.vertx.promiseDeployVerticle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.config.typesafe.KonfigModule
import uy.kohesive.injekt.config.typesafe.KonfigRegistrar
import java.util.concurrent.TimeUnit

public object KovertVerticleModule : KonfigModule, InjektModule {
    override fun KonfigRegistrar.registerConfigurables() {
        bindClassAtConfigRoot<KovertVerticleConfig>()
    }

    override fun InjektRegistrar.registerInjectables() {

    }
}

public class KovertVerticle private constructor (val cfg: KovertVerticleConfig, val routerInit: Router.() -> Unit, val onListenerReady: (String)->Unit) : AbstractVerticle() {
    companion object {
        val LOG: Logger = io.vertx.core.logging.LoggerFactory.getLogger(this.javaClass)

        /**
         * Deploys a KovertVerticle into a Vertx instance and returns a Promise representing the deployment ID.
         * The HTTP listeners are active before the promise completes.
         */
        public fun deploy(vertx: Vertx, cfg: KovertVerticleConfig = Injekt.get(), routerInit: Router.() -> Unit): Promise<String, Exception> {
            val deferred = deferred<String, Exception>()
            val completeThePromise = fun(id: String): Unit {
                LOG.warn("KovertVerticle is listening and ready.")
                deferred.resolve(id)
            }

            vertx.promiseDeployVerticle(KovertVerticle(cfg ,routerInit, completeThePromise)) success { deploymentId ->
                LOG.warn("KovertVerticle deployed as ${deploymentId}")
            } fail { failureException ->
                LOG.error("Vertx deployment failed due to ${failureException.getMessage()}", failureException)
                deferred.reject(failureException)
            }
            return deferred.promise
        }
    }

    override fun start() {
        LOG.warn("API Verticle starting")

        val cookieHandler = CookieHandler.create()
        fun cookieHandlerFactory() = cookieHandler

        val sessionStore = if (vertx.isClustered()) ClusteredSessionStore.create(vertx) else LocalSessionStore.create(vertx)
        val sessionHandler = SessionHandler.create(sessionStore).setSessionTimeout(TimeUnit.HOURS.toMillis(cfg.sessionTimeoutInHours.toLong())).setNagHttps(false)

        try {
            val appRouter = Router.router(vertx) initializedBy { router ->
                router.route().handler(LoggerHandler.create())
                router.route().handler(cookieHandlerFactory())
                router.route().handler(sessionHandler)

                // give the user a chance to bind more routes, for example their controllers
                router.routerInit()
            }

            appRouter initializedBy { router ->
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


