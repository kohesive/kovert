package uy.kohesive.kovert.vertx.boot

import com.hazelcast.config
import com.hazelcast.config.GroupConfig
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.logging.Logger
import io.vertx.ext.web.Router
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import uy.klutter.core.common.verifiedBy
import uy.klutter.core.jdk7.notExists
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.config.typesafe.KonfigModule
import uy.kohesive.injekt.config.typesafe.KonfigRegistrar
import uy.kohesive.kovert.vertx.*
import java.nio.file.*

public object KovertVertxModule : KonfigModule, InjektModule {
    override fun KonfigRegistrar.registerConfigurables() {
        bindClassAtConfigRoot<VertxConfig>()
    }

    override fun InjektRegistrar.registerInjectables() {

    }
}

public class KovertVertx private constructor () {
    companion object {
        val LOG: Logger = io.vertx.core.logging.LoggerFactory.getLogger(this.javaClass)

        /**
         * Returns a Promise<Vertx, Exception> representing the started Vertx instance
         */
        public fun start(vertxCfg: VertxConfig = Injekt.get(), workingDir: Path? = null, vertxOptionsInit: VertxOptions.() -> Unit = {}): Promise<Vertx, Exception> {
            LOG.warn("Starting Vertx")

            val deferred = deferred<Vertx, Exception>()

            try {
                System.setProperty("vertx.disableFileCPResolving", "true")
                System.setProperty("vertx.disableFileCaching", vertxCfg.fileCaching.enableCache.not().toString())
                if (!vertxCfg.fileCaching.cacheBaseDir.isNullOrBlank()) {
                    System.setProperty("vertx.cacheDirBase", vertxCfg.fileCaching.cacheBaseDir)
                }
                val calculatedWorkingDir = (workingDir ?: Paths.get(System.getProperty("vertx.cwd", "."))).toAbsolutePath() verifiedBy { path ->
                    if (path.notExists()) {
                        throw Exception("Working directory was specified as ${path.toString()}, but does not exist.")
                    }
                }
                if (System.getProperty("vertx.cwd") == null) {
                    System.setProperty("vertx.cwd", calculatedWorkingDir.toString())
                }

                val numCores = Runtime.getRuntime().availableProcessors()

                val vertxOptions = VertxOptions().setWorkerPoolSize(vertxCfg.workerThreadPoolSize.coerceIn((numCores * 2)..(numCores * 128)))
                        .setClustered(vertxCfg.clustered)
                        .setClusterManager(HazelcastClusterManager(config.Config().setGroupConfig(GroupConfig(vertxCfg.clusterName, vertxCfg.clusterPass))))

                with (vertxOptions) { vertxOptionsInit() }

                val startupPromise = if (vertxOptions.isClustered()) vertxCluster(vertxOptions) else vertx(vertxOptions)
                startupPromise success { vertx ->
                    deferred.resolve(vertx)
                } fail { failureException ->
                    LOG.error("Vertx deployment failed due to ${failureException.getMessage()}", failureException)
                    deferred.reject(failureException)
                }
            } catch (ex: Exception) {
                deferred.reject(ex)
            } catch (ex: Throwable) {
                deferred.reject(WrappedThrowableException(ex))
            }
            return deferred.promise
        }
    }
}


public data class VertxConfig(val clustered: Boolean = true,
                              val clusterName: String,
                              val clusterPass: String,
                              val workerThreadPoolSize: Int = Runtime.getRuntime().availableProcessors() * 2,
                              val fileCaching: FileCacheConfig)

public data class FileCacheConfig(val enableCache: Boolean, val cacheBaseDir: String?)

