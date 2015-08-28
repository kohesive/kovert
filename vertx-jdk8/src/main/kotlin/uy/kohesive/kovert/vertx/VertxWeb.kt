package uy.kohesive.kovert.vertx

import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.Session
import uy.klutter.core.jdk.mustStartWith
import java.net.URI


/**
 * Put values into session, but nulls act as removes (in Vert.x clustered, sometimes this causes a failure to put nulls)
 */
public fun Session.putSafely(key: String, value: Any?) {
    if (value == null) {
        remove(key)
    } else {
        put(key, value)
    }
}

/**
 * Here for balance with putSafely
 */
public fun Session.removeSafely(key: String): Any? {
    return remove<Any?>(key)
}

/**
 * Extract unencoded path?query#hash from URL and return as a string.
 */
private fun pathPlusParmsOfUrl(original: URI): String {
    val path = original.getRawPath() let { if (it.isNullOrBlank()) "" else it.mustStartWith('/') }
    val query = original.getRawQuery() let { if (it.isNullOrBlank()) "" else it.mustStartWith('?') }
    val fragment = original.getRawFragment() let { if (it.isNullOrBlank()) "" else it.mustStartWith('#')}
    return "$path$query$fragment"
}

/**
 * Return this routed URL as fully qualified external accessible URL
 */
public fun RoutingContext.externalizeUrl(): String {
    return externalizeUrl(pathPlusParmsOfUrl(URI(request().absoluteURI())))
}

/**
 * Return the specified URL as fully qualified external accessible URL.  This will substitute the values of proxy/load
 * balancer using headers:
 *     X-Forwarded-Proto
 *     X-Forwarded-Host
 *     X-Forwarded-Port
 *
 * This works with URL's that are:
 *
 * - partial without host, for example "/something/here" or "under/me"  resolving to this server's values
 * - partial with host/port, for example "//somehost.com:8983/solr" would add the same scheme (http/https) as this server's to match
 * - full, URL's that are fully qualified are retured untouched, so they are safe to pass to this function ("http://...", "https://...")
 *
 * Url's that will cause unknown results, those of the form "somehost.com:8983/solr" might be treated as relative paths on current server
 */
public fun RoutingContext.externalizeUrl(url: String): String {
    val requestUri: URI = URI(request().absoluteURI()) // fallback values for scheme/host/port  ... and for relative paths

    val requestScheme: String = run {
        return@run request().getHeader("X-Forwarded-Proto") let { scheme: String? ->
            if (scheme == null || scheme.isEmpty()) {
                requestUri.getScheme()
            } else {
                scheme
            }
        }
    }

    val requestHost: String = run {
        return@run request().getHeader("X-Forwarded-Host") let inner@ { host: String? ->
            val hostWithPossiblePort = if (host == null || host.isEmpty()) {
                requestUri.getHost()
            } else {
                host
            }

            return@inner hostWithPossiblePort.substringBefore(':')
        }
    }


    val requestPort = run  {
        val defaultPort = requestUri.getPort() let inner@ { explicitPort ->
            return@inner if (explicitPort == 0) {
                if ("https" == requestScheme) 443 else 80
            } else {
                explicitPort
            }
        }

        return@run request().getHeader("X-Forwarded-Port") let inner@ {  proxyOrLoadBalancerPort ->
            val finalPort = if (proxyOrLoadBalancerPort.isNullOrBlank()) {
                defaultPort
            } else {
                proxyOrLoadBalancerPort
            }

            return@inner if (requestScheme == "https" && finalPort == "443") {
                ""
            } else if (requestScheme == "http" && finalPort == "80") {
                ""
            } else {
                ":$finalPort"
            }
        }
    }

    val finalUrl = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "$requestScheme://$url"
        url.startsWith("/") ->"$requestScheme://$requestHost$requestPort$url"
        else -> {
            val newUri = requestUri.resolve(url)
            val restOfUrl = pathPlusParmsOfUrl(newUri)
            "$requestScheme://$requestHost$requestPort$restOfUrl"
        }
    }
    return finalUrl
}
