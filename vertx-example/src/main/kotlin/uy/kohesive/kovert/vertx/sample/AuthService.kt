package uy.kohesive.kovert.vertx.sample

import uy.kohesive.injekt.api.*

interface AuthService  {
    public fun apiKeyToUser(apiKey: String): User?
}

class MockAuthService: AuthService {
    companion object Injektables: InjektModule {
        override fun InjektRegistrar.registerInjectables() {
            addSingletonFactory<AuthService> { MockAuthService() }
        }
    }

    override public fun apiKeyToUser(apiKey: String): User? = mockData_validApiKeys.get(apiKey)
}


data class User(val id: Int, val username: String)