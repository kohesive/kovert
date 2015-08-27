package uy.kohesive.kovert.vertx

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.Router
import uy.kohesive.kovert.core.HttpVerb
import uy.kohesive.kovert.core.PrefixAsVerbWithSuccessStatus
import uy.kohesive.kovert.vertx.internal.bindControllerController


public fun Router.bindController(kotlinClassAsController: Any, atPath: String, verbAliases: List<PrefixAsVerbWithSuccessStatus> = emptyList()) {
    bindControllerController(this, kotlinClassAsController, atPath, verbAliases)
}