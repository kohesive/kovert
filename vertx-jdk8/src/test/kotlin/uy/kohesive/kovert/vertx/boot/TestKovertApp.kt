package uy.kohesive.kovert.vertx.boot.test

import com.typesafe.config.Config
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import nl.komponents.kovenant.Promise
import org.junit.Test
import uy.klutter.config.typesafe.EnvironmentVariablesConfig
import uy.klutter.config.typesafe.ReferenceConfig
import uy.klutter.config.typesafe.SystemPropertiesConfig
import uy.klutter.config.typesafe.jdk7.FileConfig
import uy.klutter.config.typesafe.loadConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.config.typesafe.KonfigAndInjektMain
import uy.kohesive.injekt.config.typesafe.KonfigModule
import uy.kohesive.injekt.config.typesafe.KonfigRegistrar
import uy.kohesive.kovert.core.HttpErrorNotFound
import uy.kohesive.kovert.core.HttpVerb
import uy.kohesive.kovert.core.KovertConfig
import uy.kohesive.kovert.vertx.*
import uy.kohesive.kovert.vertx.boot.*
import uy.kohesive.kovert.vertx.test.testServer
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch


class TestKovertApp {
    @Test public fun testApp() {
        val testConf = Paths.get(this.javaClass.getClassLoader().getResource("test.conf").toURI())
        val deployment = KovertApp(testConf).start().get()
        try {
            val client = deployment.vertx.createHttpClient(HttpClientOptions().setDefaultHost("localhost").setDefaultPort(Injekt.get<KovertVerticleConfig>().listeners.first().port))

            val frankJson = """{"id":1,"name":"Frank","age":30}"""
            client.testServer(HttpMethod.GET, "person/1", assertResponse = frankJson)
            client.testServer(HttpMethod.GET, "person/id/1", assertResponse = frankJson)
            client.testServer(HttpMethod.GET, "person/name/frank", assertResponse = """[$frankJson]""")

            client.testServer(HttpMethod.GET, "person/id/1991991", assertStatus = 404)
            client.testServer(HttpMethod.GET, "person/name/doogie", assertStatus = 404)

            client.testServer(HttpMethod.GET, "/index.html", assertResponse = """<html><head></head><body>Shhhh!</body></html>""")

            client.testServer(HttpMethod.GET, "/something/funky", assertResponse = "\"Funky\"")

            val collokiaJson = """{"name":"Collokia","country":"Uruguay"}"""
            client.testServer(HttpMethod.GET, "company/Collokia", assertResponse = """{"status":"OK","data":$collokiaJson}""")
            client.testServer(HttpMethod.GET, "company/name/Collokia", assertResponse = """{"status":"OK","data":$collokiaJson}""")
            client.testServer(HttpMethod.GET, "company/country/Uruguay", assertResponse = """{"status":"OK","data":[$collokiaJson,{"name":"Bremeld","country":"Uruguay"}]}""")

        } finally {
            deployment.vertx.promiseUndeploy(deployment.deploymentId).get()
            deployment.vertx.promiseClose().get()
        }
    }
}

public class KovertApp(val configFile: Path) {
    //    companion object {
    //        platformStatic public fun main(args: Array<String>) {
    //            if (args.size() != 1) {
    //                println("Invalid usage.  ConfigFile parameter is required!")
    //                println()
    //                println("  usage:  KovertApp <configFile>")
    //                println()
    //                System.exit(-1)
    //            }
    //            KovertApp(Paths.get(args[0])).start()
    //        }
    //    }

    // load injektions main this way, because we need to depend on a member varaible "configFile" that must be initialized beforehand
    val injektions = object : KonfigAndInjektMain() {
        override fun configFactory(): Config {
            return loadConfig(FileConfig(configFile))
        }

        override fun KonfigRegistrar.registerConfigurables() {
            // configuration for launching vertx
            importModule("kovert.vertx", KovertVertxModule)
            // configuration for kovert as a verticle
            importModule("kovert.server", KovertVerticleModule)
            // our controller services
            importModule("companyData", CompanyService.Companion)
        }

        override fun InjektRegistrar.registerInjectables() {
            // includes jackson ObjectMapper to match compatibility with Vertx, app logging via Vertx facade to Slf4j
            importModule(VertxInjektables)
            // everything Kovert wants
            importModule(KovertVertxModule)
            importModule(KovertVerticleModule)
            // our controllers like services
            importModule(PeopleService.Companion)
            importModule(CompanyService.Companion)
        }
    }

    public fun start(): Promise<VertxDeployment, Exception> {
        KovertConfig.addVerbAlias("find", HttpVerb.GET)

        val initControllers = fun Router.(): Unit {
            bindController(PeopleController(), "/person")
            bindController(CompanyController(), "/company")
            bindController(RootController(), "/")
        }
        return KovertVertx(workingDir = configFile.getParent()).startVertx(routerInit = initControllers)
    }
}

class PeopleController(val peopleService: PeopleService = Injekt.get()) {
    public fun RestContext.getById(id: Int): Person = peopleService.findPersonById(id) ?: throw HttpErrorNotFound()
    public fun RestContext.findWithId(id: Int): Person = peopleService.findPersonById(id) ?: throw HttpErrorNotFound()
    public fun RestContext.findWithName(name: String): List<Person> {
        val found = peopleService.findPersonsByName(name)
        if (found.isEmpty()) throw HttpErrorNotFound()
        return found
    }

    public fun RestContext.putById(id: Int, person: Person): StandardizedResponse {
        peopleService.writePerson(person); return StandardizedResponse()
    }
}

class CompanyController(val companyService: CompanyService = Injekt.get()) : InterceptDispatch<RestContext> {
    override fun RestContext.interceptDispatch(member: Any, dispatcher: () -> Any?): Any? {
        // don't do this type of response, REST calls should have status in header, not body
        return StandardizedResponse(data = dispatcher())
    }

    public fun RestContext.getByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()
    public fun RestContext.findWithName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()
    public fun RestContext.findWithCountry(country: String): List<Company> {
        val found = companyService.findCompaniesByCountry(country)
        if (found.isEmpty()) throw HttpErrorNotFound()
        return found
    }
}

class RootController() {
    public fun RestContext.getSomethingFunky(): String = "\"Funky\""
}

class RestContext(private val routingContext: RoutingContext)

data class StandardizedResponse(val status: String = "OK", val data: Any? = null)

class PeopleService {
    companion object : InjektModule {
        override fun InjektRegistrar.registerInjectables() {
            addSingleton(PeopleService())
        }
    }

    private val people = hashMapOf(*listOf(
            Person(1, "Frank", 30),
            Person(2, "Domingo", 19),
            Person(3, "Mariana", 22),
            Person(4, "Lucia", 31)
    ).map { it.id to it }.toTypedArray())

    public fun findPersonById(id: Int): Person? = people.get(id)
    public fun findPersonsByName(name: String): List<Person> = people.values().filter { it.name.equals(name, ignoreCase = true) }
    public fun writePerson(newPerson: Person): Unit {
        people.put(newPerson.id, newPerson)
    }
}

class CompanyService(val companyData: CompanyConfig = Injekt.get(), val employees: PeopleService = Injekt.get()) {
    companion object : KonfigModule, InjektModule {
        override fun KonfigRegistrar.registerConfigurables() {
            bindClassAtConfigRoot<CompanyConfig>()
        }

        override fun InjektRegistrar.registerInjectables() {
            addSingleton(CompanyService())
        }
    }

    public fun findCompanyByName(name: String): Company? = companyData.defaultCompanies.firstOrNull { it.name.equals(name, ignoreCase = true) }
    public fun findCompaniesByCountry(country: String): List<Company> = companyData.defaultCompanies.filter { it.country.equals(country, ignoreCase = true) }
}

data class CompanyConfig(val defaultCompanies: List<Company>)
data class Company(val name: String, val country: String)

data class Person(val id: Int, val name: String, val age: Int)
