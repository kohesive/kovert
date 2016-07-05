package uy.kohesive.kovert.vertx

import io.vertx.ext.web.Router
import uy.kohesive.kovert.core.PrefixAsVerbWithSuccessStatus
import uy.kohesive.kovert.vertx.internal.bindControllerController


fun Router.bindController(kotlinClassAsController: Any, atPath: String, verbAliases: List<PrefixAsVerbWithSuccessStatus> = emptyList()) {
    bindControllerController(this, kotlinClassAsController, atPath, verbAliases)
}