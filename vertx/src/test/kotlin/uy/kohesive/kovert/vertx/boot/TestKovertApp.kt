package uy.kohesive.kovert.vertx.boot.test

import com.github.salomonbrys.kodein.*
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.bind
import org.junit.Test
import uy.klutter.config.typesafe.PathConfig
import uy.klutter.config.typesafe.kodein.ConfigModule
import uy.klutter.config.typesafe.kodein.importConfig
import uy.klutter.config.typesafe.loadConfig
import uy.klutter.vertx.kodein.KodeinVertx
import uy.klutter.vertx.promiseClose
import uy.klutter.vertx.promiseUndeploy
import uy.kohesive.kovert.core.HttpErrorBadRequest
import uy.kohesive.kovert.core.HttpErrorNotFound
import uy.kohesive.kovert.core.HttpVerb
import uy.kohesive.kovert.core.KovertConfig
import uy.kohesive.kovert.vertx.InterceptDispatch
import uy.kohesive.kovert.vertx.bindController
import uy.kohesive.kovert.vertx.boot.*
import uy.kohesive.kovert.vertx.test.testServer
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*


class TestKovertApp {
    @Test fun testApp() {
        val testConf: Path = Paths.get(this.javaClass.getClassLoader().getResource("test.conf").toURI())!!
        val kodein = Kodein {
            bind<Path>("CONFIGFILE") with singleton { testConf }
            import(KovertApp.makeKodeinModule(testConf))
        }
        println("KODEIN BINDINGS:\n${kodein.container.bindings.description}")

        val deployment = KovertApp(kodein).start().get()
        try {
            val client = deployment.vertx.createHttpClient(HttpClientOptions().setDefaultHost("localhost").setDefaultPort(kodein.instance<KovertVerticleConfig>().listeners.first().port))

            val frankJson = """{"id":1,"name":"Frank","age":30}"""
            client.testServer(HttpMethod.GET, "api/person/1", assertResponse = frankJson)
            client.testServer(HttpMethod.GET, "api/person/id/1", assertResponse = frankJson)
            client.testServer(HttpMethod.GET, "api/person/name/frank", assertResponse = """[$frankJson]""")

            client.testServer(HttpMethod.GET, "api/person/id/1991991", assertStatus = 404)
            client.testServer(HttpMethod.GET, "api/person/name/doogie", assertStatus = 404)

            client.testServer(HttpMethod.GET, "/index.html", assertResponse = """<html><head></head><body>Shhhh!</body></html>""")

            client.testServer(HttpMethod.GET, "/something/funky", assertResponse = "\"Funky\"")

            val collokiaJson = """{"name":"Collokia","country":"Uruguay"}"""
            client.testServer(HttpMethod.GET, "api/company/Collokia", assertResponse = """{"status":"OK","data":$collokiaJson}""")
            client.testServer(HttpMethod.GET, "api/company/name/Collokia", assertResponse = """{"status":"OK","data":$collokiaJson}""")
            client.testServer(HttpMethod.GET, "api/company/country/Uruguay", assertResponse = """{"status":"OK","data":[$collokiaJson,{"name":"Bremeld","country":"Uruguay"}]}""")

        } finally {
            deployment.vertx.promiseUndeploy(deployment.deploymentId).get()
            deployment.vertx.promiseClose().get()
        }
    }
}

class KovertApp(override val kodein: Kodein): KodeinAware {
    companion object {
        fun makeKodeinModule(configFile: Path) = Kodein.Module {
            importConfig(loadConfig(PathConfig(configFile))) {
                import("kovert.vertx", KodeinKovertVertx.configModule)
                import("kovert.server", KovertVerticleModule.configModule)
                import("companyData", CompanyService.configModule)
            }

            // includes jackson ObjectMapper to match compatibility with Vertx, app logging via Vertx facade to Slf4j
            import(KodeinVertx.moduleWithLoggingToSlf4j)
            // Kovert boot
            import(KodeinKovertVertx.module)
            import(KovertVerticleModule.module)
            // Our custom services
            import(PeopleService.kodeinModule)
            import(CompanyService.kodeinModule)
        }
    }
    fun start(): Promise<VertxDeployment, Exception> {
        KovertConfig.addVerbAlias("find", HttpVerb.GET)

        val initControllers = fun Router.(): Unit {
            bindController(PeopleController(instance()), "/api/person")
            bindController(CompanyController(instance()), "/api/company")
            bindController(RootController(), "/")
        }

        val deferred = deferred<VertxDeployment, Exception>()
        val configFile: Path = kodein.instance("CONFIGFILE")
        KovertVertx.start(kodein, workingDir = configFile.parent) bind { vertx ->
            KovertVerticle.deploy(vertx, kodein, routerInit = initControllers) success { deployId ->
                deferred.resolve(VertxDeployment(vertx, deployId))
            }
        } fail { error ->
            deferred.reject(error)
        }

        return deferred.promise

    }
}

data class VertxDeployment(val vertx: Vertx, val deploymentId: String)

class PeopleController(val peopleService: PeopleService) {
    fun RestContext.getById(id: Int): Person = peopleService.findPersonById(id) ?: throw HttpErrorNotFound()
    fun RestContext.findWithId(id: Int): Person = peopleService.findPersonById(id) ?: throw HttpErrorNotFound()
    fun RestContext.findWithName(name: String): List<Person> {
        val found = peopleService.findPersonsByName(name)
        if (found.isEmpty()) throw HttpErrorNotFound()
        return found
    }

    fun RestContext.putById(id: Int, person: Person): StandardizedResponse {
        if (id != person.id) throw HttpErrorBadRequest()
        peopleService.writePerson(person); return StandardizedResponse()
    }
}

class CompanyController(val companyService: CompanyService) : InterceptDispatch<RestContext> {
    override fun RestContext.interceptDispatch(member: Any, dispatcher: () -> Any?): Any? {
        // don't do this type of response, REST calls should have status in header, not body
        return StandardizedResponse(data = dispatcher())
    }

    fun RestContext.getByName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()
    fun RestContext.findWithName(name: String): Company = companyService.findCompanyByName(name) ?: throw HttpErrorNotFound()
    fun RestContext.findWithCountry(country: String): List<Company> {
        val found = companyService.findCompaniesByCountry(country)
        if (found.isEmpty()) throw HttpErrorNotFound()
        return found
    }
}

class RootController() {
    fun RestContext.getSomethingFunky(): String = "\"Funky\""
}

class RestContext(private val routingContext: RoutingContext)

data class StandardizedResponse(val status: String = "OK", val data: Any? = null)

class PeopleService {
    companion object {
        val kodeinModule = Kodein.Module {
            bind<PeopleService>() with singleton { PeopleService() }
        }
    }

    private val people = listOf(
            Person(1, "Frank", 30),
            Person(2, "Domingo", 19),
            Person(3, "Mariana", 22),
            Person(4, "Lucia", 31)
    ).associateByTo(HashMap()) { it.id }

    fun findPersonById(id: Int): Person? = people.get(id)
    fun findPersonsByName(name: String): List<Person> = people.values.filter { it.name.equals(name, ignoreCase = true) }
    fun writePerson(newPerson: Person): Unit {
        people.put(newPerson.id, newPerson)
    }
}

class CompanyService(val companyData: CompanyConfig, val employees: PeopleService) {
    companion object {
        val configModule = Kodein.ConfigModule {
            bind<CompanyConfig>() fromConfig(it)
        }


        val kodeinModule = Kodein.Module {
            bind<CompanyService>() with singleton { CompanyService(instance(), instance()) }
        }
    }

    fun findCompanyByName(name: String): Company? = companyData.defaultCompanies.firstOrNull { it.name.equals(name, ignoreCase = true) }
    fun findCompaniesByCountry(country: String): List<Company> = companyData.defaultCompanies.filter { it.country.equals(country, ignoreCase = true) }
}

data class CompanyConfig(val defaultCompanies: List<Company>)
data class Company(val name: String, val country: String)

data class Person(val id: Int, val name: String, val age: Int)
