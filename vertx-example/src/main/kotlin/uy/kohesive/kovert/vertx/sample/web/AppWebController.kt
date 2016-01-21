package uy.kohesive.kovert.vertx.sample.web

import uy.kohesive.kovert.core.HttpRedirect
import uy.kohesive.kovert.core.Rendered
import uy.kohesive.kovert.vertx.sample.services.User


public class AppWebController {
    @Rendered("app/home.html.ftl")
    val index = fun UserSessionSecured.(): BaseAppModel {
        return BaseAppModel(user)
    }

    // TODO: problem here with return type of Unit, should be allowed.  ALso for rendered as EmptyModel
    // TODO: move EmptyModel into Kovert core
    val doLogout = fun PublicAccess.(): String {
        try {
            if (user != null) upgradeToSecured().logout()
        }
        catch (ex: Throwable) {
            // eat exceptions during logout, why let it fail?!?
        }
        finally {
            throw HttpRedirect("/login") // TODO: change to use a reference lookup to the public web controller login page
        }
    }
}

open class BaseAppModel(val user: User)