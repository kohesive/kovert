package uy.kohesive.kovert.vertx.sample

import com.typesafe.config.Config
import freemarker.cache.TemplateNameFormat
import freemarker.template.Configuration
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BasicAuthHandler
import nl.komponents.kovenant.functional.bind
import org.slf4j.Logger
import uy.klutter.config.typesafe.KonfigAndInjektMain
import uy.klutter.config.typesafe.*
import uy.klutter.config.typesafe.ReferenceConfig
import uy.klutter.config.typesafe.jdk7.FileConfig
import uy.klutter.config.typesafe.loadConfig
import uy.klutter.vertx.VertxWithSlf4jInjektables
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.*
import uy.kohesive.kovert.core.HttpVerb
import uy.kohesive.kovert.core.KovertConfig
import uy.kohesive.kovert.template.freemarker.KovertFreemarkerTemplateEngine
import uy.kohesive.kovert.vertx.bindController
import uy.kohesive.kovert.vertx.boot.*
import uy.kohesive.kovert.vertx.sample.api.CompanyRestController
import uy.kohesive.kovert.vertx.sample.api.PeopleRestController
import uy.kohesive.kovert.vertx.sample.services.MockAuthService
import uy.kohesive.kovert.vertx.sample.services.MockCompanyService
import uy.kohesive.kovert.vertx.sample.services.MockPeopleService
import uy.kohesive.kovert.vertx.sample.services.SimpleUserAuthProvider
import uy.kohesive.kovert.vertx.sample.web.AppWebController
import uy.kohesive.kovert.vertx.sample.web.PublicWebController
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class App(val configFile: Path) {
    companion object {
        @JvmStatic fun main(args: Array<String>) {
            if (args.size != 1) {
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

            bindClassAtConfigPath<FreemarkerLocationCfg>("kovert.freemarker")
        }

        override fun InjektRegistrar.registerInjectables() {
            // includes jackson ObjectMapper to match compatibility with Vertx, app logging via Vertx facade to Slf4j
            importModule(VertxWithSlf4jInjektables)
            // everything Kovert wants
            importModule(KovertVertxModule)
            importModule(KovertVerticleModule)
            // our controllers like to use services
            importModule(MockAuthService.Injektables)
            importModule(MockPeopleService.Injektables)
            importModule(MockCompanyService.Injektables)
        }
    }

    fun start() {
        // we use the pattern findSomething so add that as a alias for HTTP GET
        KovertConfig.addVerbAlias("find", HttpVerb.GET)
        KovertConfig.addVerbAlias("view", HttpVerb.GET)
        KovertConfig.addVerbAlias("index", HttpVerb.GET)
        KovertConfig.addVerbAlias("do", HttpVerb.GET)
        KovertConfig.addVerbAlias("submit", HttpVerb.POST)

        val configFileLocation = configFile.getParent() // make things relative to the config file location

        val freemarkerEngine: Configuration = run {
            val locationConfig = Injekt.get<FreemarkerLocationCfg>()
            val cfg = Configuration(Configuration.VERSION_2_3_23)
            cfg.setDefaultEncoding("UTF-8")
            cfg.setTemplateNameFormat(TemplateNameFormat.DEFAULT_2_4_0)
            // cfg.setObjectWrapper(JodaAwareObjectWrapper())

            // if templates are in resources dir:
            // cfg.setClassLoaderForTemplateLoading(ClassLoader.getSystemClassLoader(), "templates")
            cfg.setDirectoryForTemplateLoading(File(configFileLocation.toFile(), locationConfig.templateDir))


            cfg.setTemplateExceptionHandler(freemarker.template.TemplateExceptionHandler.HTML_DEBUG_HANDLER)
            // alternatively, RETHROW_HANDLER better for prod
            cfg
        }

        KovertConfig.registerTemplateEngine(KovertFreemarkerTemplateEngine(freemarkerEngine), ".html.ftl")

        val LOG: Logger = Injekt.logger(this)

        val apiMountPoint = "api"
        val publicMountPoint = ""
        val privateMountPoint = "app"

        val initControllers = fun Router.(): Unit {
            // bind the controller classes
            bindController(PeopleRestController(), apiMountPoint)
            bindController(CompanyRestController(), apiMountPoint)

            bindController(PublicWebController(), publicMountPoint)
            bindController(AppWebController(), privateMountPoint)
       //     bindController(CompanyWebController(), privateMountPoint)
       //     bindController(PeopleWebController(), privateMountPoint)
        }

        val authentication = SimpleUserAuthProvider()
        val bodyHandlersFor = listOf(apiMountPoint, publicMountPoint, privateMountPoint)

        val appCustomization = KovertVerticleCustomization(
                bodyHandlerRoutePrefixes = if (bodyHandlersFor.any { it.isBlank() }) emptyList() else bodyHandlersFor,
                authProvider = authentication,
                authHandler = BasicAuthHandler.create(authentication),
                authHandlerRoutePrefixes = listOf(privateMountPoint)
        )

        // startup asynchronously...
        KovertVertx.start(workingDir = configFileLocation) bind { vertx ->
            KovertVerticle.deploy(vertx, customization = appCustomization, routerInit = initControllers)
        } success { deploymentId ->
            LOG.warn("Deployment complete.")
        } fail { error ->
            LOG.error("Deployment failed!", error)
        }
    }

    class FreemarkerLocationCfg(val templateDir: String)

}
