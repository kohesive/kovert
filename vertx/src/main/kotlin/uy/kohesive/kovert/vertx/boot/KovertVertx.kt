package uy.kohesive.kovert.vertx.boot

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.global.KodeinGlobalAware
import com.github.salomonbrys.kodein.global.global
import com.github.salomonbrys.kodein.instance
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.logging.Logger
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import uy.klutter.config.typesafe.kodein.ConfigModule
import uy.klutter.core.common.notExists
import uy.klutter.core.common.verifiedBy
import uy.klutter.vertx.WrappedThrowableException
import uy.klutter.vertx.vertx
import uy.klutter.vertx.vertxCluster
import java.net.InetAddress
import java.nio.file.Path
import java.nio.file.Paths

object KodeinKovertVertx {
    val configModule = Kodein.ConfigModule {
        bind<VertxConfig>() fromConfig (it)
    }

    val module = Kodein.Module {
        // other bindings
    }
}

class KovertVertx private constructor() {
    companion object {
        val LOG: Logger = io.vertx.core.logging.LoggerFactory.getLogger(KovertVertx::class.java)

        /**
         * Returns a Promise<Vertx, Exception> representing the started Vertx instance
         */
        fun start(kodein: Kodein = Kodein.global, vertxCfg: VertxConfig = kodein.instance(), workingDir: Path? = null, vertxOptionsInit: VertxOptions.() -> Unit = {}): Promise<Vertx, Exception> {
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

                val hazelcastConfig = HazelcastClusterManager().loadConfig()
                hazelcastConfig.groupConfig.setName(vertxCfg.clusterName).setPassword(vertxCfg.clusterPass)
                if (vertxCfg.forceLocalClusterOnly) {
                    val loopback = InetAddress.getLoopbackAddress().hostAddress
                    hazelcastConfig.networkConfig.interfaces.setInterfaces(setOf(loopback))
                    hazelcastConfig.networkConfig.join.multicastConfig.setEnabled(false)
                    hazelcastConfig.networkConfig.join.tcpIpConfig.setEnabled(true)
                    hazelcastConfig.networkConfig.join.tcpIpConfig.setMembers(listOf(loopback))
                }
                LOG.trace(hazelcastConfig.toString())
                val vertxOptions = VertxOptions().setWorkerPoolSize(vertxCfg.workerThreadPoolSize.coerceIn((numCores * 2)..(numCores * 128)))
                        .setClustered(vertxCfg.clustered)
                        .setClusterManager(HazelcastClusterManager(hazelcastConfig))

                with(vertxOptions) { vertxOptionsInit() }

                val startupPromise = if (vertxOptions.isClustered()) vertxCluster(vertxOptions) else vertx(vertxOptions)
                startupPromise success { vertx ->
                    deferred.resolve(vertx)
                } fail { failureException ->
                    LOG.error("Vertx deployment failed due to ${failureException.message}", failureException)
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


data class VertxConfig(val clustered: Boolean = true,
                       val clusterName: String,
                       val clusterPass: String,
                       val workerThreadPoolSize: Int = Runtime.getRuntime().availableProcessors() * 2,
                       val fileCaching: FileCacheConfig,
                       val forceLocalClusterOnly: Boolean = false)

data class FileCacheConfig(val enableCache: Boolean, val cacheBaseDir: String?)

