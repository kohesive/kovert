package uy.kohesive.kovert.vertx

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AbstractUser
import io.vertx.ext.auth.AuthProvider
import io.vertx.ext.auth.User
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.CookieHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.handler.UserSessionHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.task
import nl.komponents.kovenant.then
import org.junit.Before
import org.junit.Test
import uy.klutter.core.common.with
import uy.klutter.vertx.json.jsonArrayFromList
import uy.klutter.vertx.json.jsonObject
import uy.klutter.vertx.promiseResult
import uy.kohesive.kovert.core.*
import uy.kohesive.kovert.vertx.test.testServer
import java.util.concurrent.TimeUnit


class TestAuth : AbstractKovertTest() {

    @Before
    override fun beforeTest() {
        super.beforeTest()
    }

    @Test fun testAuthOnRoutes() {
        val authService = MockAuthService()
        val authProvider = MockUserAuthProvider(authService)

        _router.route().handler(CookieHandler.create())
        _router.route().handler(SessionHandler.create(LocalSessionStore.create(_vertx)).setSessionTimeout(TimeUnit.HOURS.toMillis(1L)).setNagHttps(false))

        _router.route().handler(UserSessionHandler.create(authProvider))
        _router.bindController(MockAdminController(), "/admin")
        _router.bindController(MockAuthController(authProvider), "/api")

        fun logout(cookie: String?): String? {
            return _client.testServer(HttpMethod.POST, "/api/logout", assertStatus = 200, cookie = cookie)
        }

        fun login(user: MockUser, cookie: String?): String {
            val newCookie = _client.testServer(HttpMethod.POST, "/api/login/${user.apikey}",
                    assertResponse = """{"username":"${user.username}","permissions":[${user.permissions.map { """"$it"""" }.joinToString(",")}]}""",
                    assertContentType = "application/json",
                    cookie = cookie)
            return newCookie ?: throw Exception("Missing cookie!")
        }

        var cookie: String? = logout(null)
        cookie = login(authService.user1, cookie)
        _client.testServer(HttpMethod.GET, "/api/open/data1", assertResponse = """{"what":"openData1"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/open/data2", assertResponse = """{"what":"openData2"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/data", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/user/data", assertResponse = """{"what":"someUserData"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/important/data", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/stuff", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/admin/data", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/important/data", assertStatus = 403, cookie = cookie)


        cookie = logout(cookie)
        cookie = login(authService.user2, cookie)
        _client.testServer(HttpMethod.GET, "/api/open/data1", assertResponse = """{"what":"openData1"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/open/data2", assertResponse = """{"what":"openData2"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/data", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/user/data", assertResponse = """{"what":"someUserData"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/important/data", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/stuff", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/admin/data", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/important/data", assertStatus = 403, cookie = cookie)


        cookie = logout(cookie)
        cookie = login(authService.user3, cookie)
        _client.testServer(HttpMethod.GET, "/api/open/data1", assertResponse = """{"what":"openData1"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/open/data2", assertResponse = """{"what":"openData2"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/data", assertResponse = """{"what":"someAdminData"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/user/data", assertResponse = """{"what":"someUserData"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/important/data", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/stuff", assertResponse = """{"what":"someAdminStuff"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/admin/data", assertResponse = """{"what":"someAdminData"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/important/data", assertStatus = 403, cookie = cookie)


        cookie = logout(cookie)
        cookie = login(authService.user4, cookie)
        _client.testServer(HttpMethod.GET, "/api/open/data1", assertResponse = """{"what":"openData1"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/open/data2", assertResponse = """{"what":"openData2"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/data", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/user/data", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/important/data", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/stuff", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/admin/data", assertStatus = 403, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/important/data", assertStatus = 403, cookie = cookie)


        cookie = logout(cookie)
        cookie = login(authService.user5, cookie)
        _client.testServer(HttpMethod.GET, "/api/open/data1", assertResponse = """{"what":"openData1"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/open/data2", assertResponse = """{"what":"openData2"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/data", assertResponse = """{"what":"someAdminData"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/user/data", assertResponse = """{"what":"someUserData"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/important/data", assertResponse = """{"what":"someImportantData"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/stuff", assertResponse = """{"what":"someAdminStuff"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/admin/data", assertResponse = """{"what":"someAdminData"}""", cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/important/data", assertResponse = """{"what":"someImportantData"}""", cookie = cookie)

        cookie = logout(cookie)
        // unlogged in user, no session
        cookie = _client.testServer(HttpMethod.GET, "/api/open/data1", assertStatus = 401, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/open/data2", assertStatus = 401, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/data", assertStatus = 401, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/user/data", assertStatus = 401, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/important/data", assertStatus = 401, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/api/some/admin/stuff", assertStatus = 401, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/admin/data", assertStatus = 401, cookie = cookie)
        _client.testServer(HttpMethod.GET, "/admin/some/important/data", assertStatus = 401, cookie = cookie)


    }
}

internal class MockAuthController(val authProvider: MockUserAuthProvider) {
    fun PublicAccess.postLoginByApikey(apikey: String): Promise<JsonObject, Exception> {
        val deferred = deferred<User, Exception>()
        val newUser = authProvider.authenticate(jsonObject { put("apiKey", apikey) }, promiseResult(deferred))
        return deferred.promise.then { authUser ->
            loginUser(authUser)
            authUser.principal()
        }
    }

    fun PublicAccess.postLogout(): Promise<Unit, Exception> {
        return task {
            if (isLoggedIn()) upgradeToSecured().logout()
        }
    }

    @Authority() // context is PublicAccess but letting Authority ensure a user exists
    fun PublicAccess.getOpenData1(): MockResponse {
        return MockResponse("openData1")
    }

    fun UserSessionSecured.getOpenData2(): MockResponse {
        return MockResponse("openData2")
    }

    @Authority("role:admin")
    fun UserSessionSecured.getSomeAdminData(): MockResponse {
        return MockResponse("someAdminData")
    }

    @Authority("role:admin", "role:viewUser", "role:viewCompany")
    fun UserSessionSecured.getSomeUserData(): MockResponse {
        return MockResponse("someUserData")
    }

    data class MockResponse(val what: String)

    @Authority("role:admin", "resource:importantData", mode = AuthorityMode.ALL)
    @Verb(HttpVerb.GET, skipPrefix = false)
    val someImportantData = fun UserSessionSecured.(): MockResponse {
        return MockResponse("someImportantData")
    }

    // inherit the role:admin from the context
    fun AdminSessionSecured.getSomeAdminStuff(): MockResponse {
        return MockResponse("someAdminStuff")
    }
}

@Authority("role:admin")
internal class MockAdminController() {
    fun UserSessionSecured.getSomeAdminData(): MockResponse {
        return MockResponse("someAdminData")
    }

    @Authority("resource:importantData")
    fun UserSessionSecured.getSomeImportantData(): MockResponse {
        return MockResponse("someImportantData")
    }

    data class MockResponse(val what: String)
}


class MockAuthService {
    val user1 = MockUser("user.one", "key:one", listOf("role:viewUser"))
    val user2 = MockUser("user.two", "key:two", listOf("role:viewCompany"))
    val user3 = MockUser("user.three", "key:three", listOf("role:admin"))
    val user4 = MockUser("user.four", "key:four", listOf())
    val user5 = MockUser("user.five", "key:five", listOf("role:viewUser", "resource:importantData", "role:admin", "role:viewCompany"))

    private val mockData_validUsers: List<MockUser> = listOf(user1, user2, user3, user4, user5)

    fun apiKeyToUser(apiKey: String): User? = mockData_validUsers.firstOrNull { it.apikey == apiKey }

    fun userFromLogin(username: String, password: String): User? {
        return mockData_validUsers.firstOrNull { it.username == username }
    }
}

// simply auth provider for Vertx using our auth service, see: http://vertx.io/docs/vertx-auth-common/java/
class MockUserAuthProvider(val authService: MockAuthService) : AuthProvider {
    override fun authenticate(authInfo: JsonObject, resultHandler: Handler<AsyncResult<io.vertx.ext.auth.User>>) {
        val username: String? = authInfo.getString("username")
        val password: String? = authInfo.getString("password")
        val apiKey: String? = authInfo.getString("apiKey")

        if (apiKey == null && (username == null || password == null)) {
            resultHandler.handle(Future.failedFuture("authInfo must contain username and password // or apiKey"))
        }

        val user = if (apiKey != null) authService.apiKeyToUser(apiKey) else authService.userFromLogin(username!!, password!!)
        if (user != null) {
            resultHandler.handle(Future.succeededFuture(user))
        } else {
            resultHandler.handle(Future.failedFuture("Failure in authentication"))
        }
    }

}

// somewhat mockedout user class that works with cluster serialization in Vert.x
class MockUser constructor() : AbstractUser() {
    lateinit var username: String
    lateinit var apikey: String
    lateinit var permissions: List<String>
    var myAuthProvider: AuthProvider? = null

    constructor (username: String, apikey: String, permissions: List<String>) : this() {
        this.username = username
        this.apikey = apikey
        this.permissions = permissions
    }

    override fun doIsPermitted(permission: String, resultHandler: Handler<AsyncResult<Boolean>>) {
        resultHandler.handle(Future.succeededFuture(permission in permissions))
    }

    override fun setAuthProvider(authProvider: AuthProvider) {
        this.myAuthProvider = authProvider
    }

    override fun principal(): JsonObject? {
        return jsonObject {
            put("username", username)
            put("permissions", jsonArrayFromList(permissions))
        }
    }

    override fun writeToBuffer(buff: Buffer) {
        super.writeToBuffer(buff)
        username.with {
            val bytes = this.toByteArray(Charsets.UTF_8)
            buff.appendInt(bytes.size)
            buff.appendBytes(bytes)
        }
        apikey.with {
            val bytes = this.toByteArray(Charsets.UTF_8)
            buff.appendInt(bytes.size)
            buff.appendBytes(bytes)
        }
        buff.appendInt(permissions.size)
        permissions.forEach { permission ->
            permission.with {
                val bytes = this.toByteArray(Charsets.UTF_8)
                buff.appendInt(bytes.size)
                buff.appendBytes(bytes)
            }
        }
    }

    override fun readFromBuffer(pos: Int, buff: Buffer): Int {
        var newPos = super.readFromBuffer(pos, buff)

        val usernameLen = buff.getInt(newPos)
        newPos += 4
        val usernameBytes = buff.getBytes(newPos, newPos + usernameLen)
        username = usernameBytes.toString(Charsets.UTF_8)
        newPos += usernameLen

        val apikeyLen = buff.getInt(newPos)
        newPos += 4
        val apikeyBytes = buff.getBytes(newPos, newPos + apikeyLen)
        apikey = apikeyBytes.toString(Charsets.UTF_8)
        newPos += apikeyLen

        val numPerms = buff.getInt(newPos)
        newPos += 4
        val tempPermList = arrayListOf<String>()
        (1..numPerms).forEach {
            val permLen = buff.getInt(newPos)
            newPos += 4
            val permBytes = buff.getBytes(newPos, newPos + permLen)
            tempPermList.add(permBytes.toString(Charsets.UTF_8))
            newPos += permLen
        }
        permissions = tempPermList
        return newPos
    }
}

class PublicAccess(private val routingContext: RoutingContext) {
    // maybe logged in, is ok if not
    val user: User? = routingContext.user()

    fun isLoggedIn(): Boolean = user != null

    // allow access to functions for logged in user on public pages, if logged in, otherwise this will blow up with exception
    fun upgradeToSecured(): UserSessionSecured = UserSessionSecured(routingContext)

    fun loginUser(user: User) = routingContext.setUser(user)
}

open class UserSessionSecured(private val routingContext: RoutingContext) {
    // must be logged in, if not, bad!! (the AuthHandler should already prevent getting this far)
    val user: User = routingContext.user() as? User ?: throw HttpErrorUnauthorized()

    fun logout() {
        routingContext.clearUser()
        routingContext.session().destroy()
    }
}

@Authority("role:admin")
class AdminSessionSecured(routingContext: RoutingContext) : UserSessionSecured(routingContext)




