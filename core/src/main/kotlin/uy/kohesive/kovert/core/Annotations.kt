package uy.kohesive.kovert.core

import java.lang.annotation.ElementType
import java.lang.annotation.*
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

public enum class HttpVerb { GET, POST, PUT, DELETE, PATCH, HEAD }


/**
 * Alias a HTTP verb to a list of method prefixes (camel case, first segment of the method name)
 */
annotation(retention = AnnotationRetention.RUNTIME)
target(AnnotationTarget.CLASSIFIER)
public class VerbAlias(val prefix: String, val verb: HttpVerb, val successStatusCode: Int = 200)

annotation(retention = AnnotationRetention.RUNTIME)
target(AnnotationTarget.CLASSIFIER)
public class VerbAliases(vararg val value: VerbAlias)

/**
 * Override prefix with a specific HTTP Verb.  By default the prefix part of the method name is skipped and ingored for both the verb and not included in the path.
 */
annotation(retention = AnnotationRetention.RUNTIME)
target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
public class Verb(val verb: HttpVerb, val successStatusCode: Int = 200, val skipPrefix: Boolean = true)

/**
 * Override camel case parsing to generate location by providing a specific location (can include path params "/something/:parmName/other").  The HTTP Verb is stil
 * extracted from the method name using aliases unless a specific Verb annotation is included.
 */
annotation(retention = AnnotationRetention.RUNTIME)
target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
public class Location(val path: String)