package uy.kohesive.kovert.core

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Default HTTP to method prefix name mathing
 */
internal val defaultVerbAliases = mapOf(HttpVerb.GET to arrayOf("get", "list", "view", "edit"),
        HttpVerb.POST to arrayOf("post"),
        HttpVerb.PUT to arrayOf("put"),
        HttpVerb.DELETE to arrayOf("delete", "remove"),
        HttpVerb.PATCH to arrayOf("patch"))


public enum class HttpVerb { GET, POST, PUT, DELETE, PATCH, HEAD }


/**
 * Alias a HTTP verb to a list of method prefixes (camel case, first segment of the method name)
 */
Retention(RetentionPolicy.RUNTIME)
Target(ElementType.TYPE)
public annotation class VerbAliases(val verb: HttpVerb, vararg val prefixes: String)

/**
 * Override prefix with a specific HTTP Verb.  By default the prefix part of the method name is skipped and ingored for both the verb and not included in the path.
 */
Retention(RetentionPolicy.RUNTIME)
Target(ElementType.FIELD, ElementType.METHOD)
public annotation class Verb(val verb: HttpVerb, val skipPrefix: Boolean = true)

/**
 * Override camel case parsing to generate location by providing a specific location (can include path params "/something/:parmName/other").  The HTTP Verb is stil
 * extracted from the method name using aliases unless a specific Verb annotation is included.
 */
Retention(RetentionPolicy.RUNTIME)
Target(ElementType.FIELD, ElementType.METHOD)
public annotation class Location(val path: String)