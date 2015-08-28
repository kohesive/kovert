package uy.kohesive.kovert.core

import java.math.BigDecimal
import java.util.*


internal fun isSimpleDataType(type: Class<*>) = knownSimpleTypes.any { type.isAssignableFrom(it) } || simpleTypeNames.contains(type.getName())


@suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal val knownSimpleTypes = linkedListOf(javaClass<Boolean>(), javaClass<Number>(), javaClass<String>(),
        javaClass<Date>(),
        javaClass<java.lang.Byte>(), javaClass<java.lang.Short>(), javaClass<java.lang.Integer>(), javaClass<java.lang.Long>(),
        javaClass<java.lang.Float>(), javaClass<java.lang.Double>(),  javaClass<BigDecimal>())

internal val simpleTypeNames = setOf("byte", "char", "short", "int", "long", "float", "double", "string", "boolean")
