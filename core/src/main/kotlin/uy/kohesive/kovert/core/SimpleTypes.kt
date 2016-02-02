package uy.kohesive.kovert.core

import uy.klutter.reflect.erasedType
import uy.klutter.reflect.full.isAssignableFrom
import uy.klutter.reflect.isAssignableFrom
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType


public fun isSimpleDataType(type: Class<*>) = knownSimpleTypes.any { type.isAssignableFrom(it) } || simpleTypeNames.contains(type.getName())
public fun <T: Any> isSimpleDataType(type: KClass<T>) = knownSimpleTypes.any { type.isAssignableFrom(it) } || simpleTypeNames.contains(type.qualifiedName ?: "")
public fun isSimpleDataType(type: KType) = knownSimpleTypes.any { type.isAssignableFrom(it) } || simpleTypeNames.contains(type.javaType.erasedType().name)

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
public val knownSimpleTypes = mutableListOf<kotlin.reflect.KClass<out kotlin.Any>>(Boolean::class, Number::class, String::class,
        Date::class,
        java.lang.Byte::class, java.lang.Short::class, java.lang.Integer::class, java.lang.Long::class,
        java.lang.Float::class, java.lang.Double::class,  BigDecimal::class)

internal val simpleTypeNames = setOf("byte", "char", "short", "int", "long", "float", "double", "string", "boolean")
