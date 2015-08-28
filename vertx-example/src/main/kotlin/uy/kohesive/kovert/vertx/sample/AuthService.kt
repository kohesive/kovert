package uy.kohesive.kovert.vertx.sample

import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar

interface AuthService  {
    public fun apiKeyToUser(apiKey: String): User?
}

class MockAuthService: AuthService {
    companion object : InjektModule {
        override fun InjektRegistrar.registerInjectables() {
            addSingletonFactory<AuthService> { MockAuthService() }
        }
    }

    override public fun apiKeyToUser(apiKey: String): User? = mockData_validApiKeys.get(apiKey)
}


data class User(val id: Int, val username: String)