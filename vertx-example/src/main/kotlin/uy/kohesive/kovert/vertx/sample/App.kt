package uy.kohesive.kovert.vertx.sample

import com.typesafe.config.Config
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import nl.komponents.kovenant.Promise
import uy.klutter.config.typesafe.ReferenceConfig
import uy.klutter.config.typesafe.jdk7.FileConfig
import uy.klutter.config.typesafe.loadConfig
import uy.klutter.core.jdk.mustEndWith
import uy.klutter.core.jdk.mustNotEndWith
import uy.klutter.core.jdk.mustStartWith
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.config.typesafe.KonfigAndInjektMain
import uy.kohesive.injekt.config.typesafe.KonfigRegistrar
import uy.kohesive.kovert.core.HttpVerb
import uy.kohesive.kovert.core.KovertConfig
import uy.kohesive.kovert.vertx.VertxWithSlf4jInjektables
import uy.kohesive.kovert.vertx.bindController
import uy.kohesive.kovert.vertx.boot.KovertVerticleModule
import uy.kohesive.kovert.vertx.boot.KovertVertx
import uy.kohesive.kovert.vertx.boot.KovertVertxModule
import uy.kohesive.kovert.vertx.boot.VertxDeployment
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.platform.platformStatic

public class App(val configFile: Path) {
    companion object {
            platformStatic public fun main(args: Array<String>) {
                if (args.size() != 1) {
                    println("Invalid usage.  ConfigFile parameter is required!")
                    println()
                    println("  usage:  App <configFile>")
                    println()
                    println("There is a sample config file in web/sample.conf under the root of this sample project")
                    println()
                    System.exit(-1)
                }
                App(Paths.get(args[0])).start()
            }
        }

    // injection setup is done in a nested object to control the order of instantiation AFTER the configFile member is available
    val injektMain = object : KonfigAndInjektMain() {
        override fun configFactory(): Config {
            return loadConfig(FileConfig(configFile), ReferenceConfig())
        }

        override fun KonfigRegistrar.registerConfigurables() {
            // configuration for launching vertx, we could also pass configuration in directly to the constructor of KovertVertx
            importModule("kovert.vertx", KovertVertxModule)
            // configuration for kovert as a verticle, we could also pass configuration in directly to the constructor of KovertVerticle
            importModule("kovert.server", KovertVerticleModule)
        }

        override fun InjektRegistrar.registerInjectables() {
            // includes jackson ObjectMapper to match compatibility with Vertx, app logging via Vertx facade to Slf4j
            importModule(VertxWithSlf4jInjektables)
            // everything Kovert wants
            importModule(KovertVertxModule)
            importModule(KovertVerticleModule)
            // our controllers like services
            importModule(PeopleService.Companion)
            importModule(CompanyService.Companion)
        }
    }

    public fun start(): Promise<VertxDeployment, Exception> {
        // we use the pattern findSomething so add that as a alias for HTTP GET
        KovertConfig.addVerbAlias("find", HttpVerb.GET)
        val apiMountPoint = "api"

        val initControllers = fun Router.(): Unit {
            // bind the controller classes
            bindController(PeopleController(), apiMountPoint)
            bindController(CompanyController(), apiMountPoint)
        }
        val configFileLocation = configFile.getParent() // make things relative to the config file location
        return KovertVertx(workingDir = configFileLocation).startVertx(routerInit = initControllers)
    }
}

// we have a simple RestContext object that wraps routingContext but does nothing with it
class RestContext(private val routingContext: RoutingContext)
