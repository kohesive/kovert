package uy.kohesive.kovert.vertx.sample

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.global.KodeinGlobalAware
import com.github.salomonbrys.kodein.global.global
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.lazyKodein
import com.github.salomonbrys.kodein.withClass
import freemarker.cache.TemplateNameFormat
import freemarker.template.Configuration
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BasicAuthHandler
import nl.komponents.kovenant.functional.bind
import org.slf4j.Logger
import uy.klutter.config.typesafe.PathConfig
import uy.klutter.config.typesafe.ReferenceConfig
import uy.klutter.config.typesafe.kodein.importConfig
import uy.klutter.config.typesafe.loadConfig
import uy.klutter.vertx.kodein.KodeinVertx
import uy.kohesive.kovert.core.HttpVerb
import uy.kohesive.kovert.core.KovertConfig
import uy.kohesive.kovert.template.freemarker.KovertFreemarkerTemplateEngine
import uy.kohesive.kovert.vertx.bindController
import uy.kohesive.kovert.vertx.boot.*
import uy.kohesive.kovert.vertx.sample.api.CompanyRestController
import uy.kohesive.kovert.vertx.sample.api.PeopleRestController
import uy.kohesive.kovert.vertx.sample.services.KodeinAuthService
import uy.kohesive.kovert.vertx.sample.services.KodeinCompanyService
import uy.kohesive.kovert.vertx.sample.services.KodeinPeopleService
import uy.kohesive.kovert.vertx.sample.services.SimpleUserAuthProvider
import uy.kohesive.kovert.vertx.sample.web.AppWebController
import uy.kohesive.kovert.vertx.sample.web.PublicWebController
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class App(val configFile: Path) : KodeinGlobalAware {
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

    val LOG: Logger = withClass().instance()

    fun start() {
        // injection starting point, including configuration loading and delegating to other modules
        Kodein.global.addImport(Kodein.Module {
            importConfig(loadConfig(PathConfig(configFile), ReferenceConfig())) {
                import("kovert.vertx", KodeinKovertVertx.configModule)
                import("kovert.server", KovertVerticleModule.configModule)
                bind<FreemarkerLocationCfg>() fromConfig ("kovert.freemarker")
            }

            // includes jackson ObjectMapper to match compatibility with Vertx, app logging via Vertx facade to Slf4j
            import(KodeinVertx.moduleWithLoggingToSlf4j)
            // Kovert boot
            import(KodeinKovertVertx.module)
            import(KovertVerticleModule.module)
            // Our custom services
            import(KodeinAuthService.module)
            import(KodeinPeopleService.module)
            import(KodeinCompanyService.module)
        })

        // we use the pattern findSomething so add that as a alias for HTTP GET
        KovertConfig.addVerbAlias("find", HttpVerb.GET)
        KovertConfig.addVerbAlias("view", HttpVerb.GET)
        KovertConfig.addVerbAlias("index", HttpVerb.GET)
        KovertConfig.addVerbAlias("do", HttpVerb.GET)
        KovertConfig.addVerbAlias("submit", HttpVerb.POST)

        val configFileLocation = configFile.getParent() // make things relative to the config file location

        val freemarkerEngine: Configuration = run {
            val locationConfig: FreemarkerLocationCfg = kodein.instance()
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
