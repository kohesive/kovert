package uy.kohesive.kovert.vertx.sample.services

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AbstractUser
import io.vertx.ext.auth.AuthProvider
import uy.klutter.vertx.json.jsonObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.*

interface AuthService  {
    public fun apiKeyToUser(apiKey: String): User?
    public fun userFromLogin(username: String, password: String): User?
}

class MockAuthService: AuthService {
    companion object Injektables: InjektModule {
        override fun InjektRegistrar.registerInjectables() {
            addSingletonFactory<AuthService> { MockAuthService() }
        }
    }

    override public fun apiKeyToUser(apiKey: String): User? = mockData_validApiKeys.get(apiKey)

    override public fun userFromLogin(username: String, password: String): User? {
        // let's just let anything log in
        return mockData_validUsers.firstOrNull { it.username == username }
    }
}

// simply auth provider for Vertx using our auth service, see: http://vertx.io/docs/vertx-auth-common/java/
class SimpleUserAuthProvider(val authService: AuthService = Injekt.get()): AuthProvider {
    override fun authenticate(authInfo: JsonObject, resultHandler: Handler<AsyncResult<io.vertx.ext.auth.User>>) {
        val username = authInfo.getString("username")
        val password = authInfo.getString("password")

        if (username == null || password == null) {
            resultHandler.handle(Future.failedFuture("authInfo must contain username and password"))
        }

        val user = authService.userFromLogin(username, password)
        if (user != null) {
            resultHandler.handle(Future.succeededFuture(user))
        } else {
            resultHandler.handle(Future.failedFuture("Failure in authentication"))
        }
    }

}

// somewhat mockedout user class that works with cluster serialization in Vert.x
class User constructor () : AbstractUser() {
    lateinit var username: String

    constructor (username: String): this() {
        this.username = username
    }

    override fun doIsPermitted(permission: String, resultHandler: Handler<AsyncResult<Boolean>>) {
        resultHandler.handle(Future.succeededFuture(true))
    }

    override fun setAuthProvider(authProvider: AuthProvider) {
        // noop
    }

    override fun principal(): JsonObject? {
        return jsonObject {
            put("username", username)
        }
    }

    override fun writeToBuffer(buff: Buffer) {
        super.writeToBuffer(buff)
        val bytes = username.toByteArray(Charsets.UTF_8)
        buff.appendInt(bytes.size);
        buff.appendBytes(bytes);
    }

    override fun readFromBuffer(pos: Int, buff: Buffer): Int {
        var newPos = super.readFromBuffer(pos, buff)
        val len = buff.getInt(newPos)
        newPos += 4
        val bytes = buff.getBytes(newPos, newPos + len)
        username = bytes.toString(Charsets.UTF_8)
        newPos += len
        return newPos
    }
}