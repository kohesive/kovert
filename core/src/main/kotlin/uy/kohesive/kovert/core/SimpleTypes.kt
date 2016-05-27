package uy.kohesive.kovert.core

import uy.klutter.reflect.erasedType
import uy.klutter.reflect.full.erasedType
import uy.klutter.reflect.full.isAssignableFrom
import uy.klutter.reflect.isAssignableFrom
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType


fun isSimpleDataType(type: Class<*>) = type.isPrimitive || knownSimpleTypes.any { type.isAssignableFrom(it) } || simpleTypeNames.contains(type.name)
fun <T: Any> isSimpleDataType(type: KClass<T>) = isSimpleDataType(type.java.erasedType())
fun isSimpleDataType(type: KType) = isSimpleDataType(type.erasedType())
fun isEnum(type: KType) = type.erasedType().isEnum

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") val knownSimpleTypes = listOf<KClass<out Any>>(
        Boolean::class,
        Number::class,
        String::class,
        Date::class,
        java.lang.Byte::class, java.lang.Short::class, java.lang.Integer::class, java.lang.Long::class,
        java.lang.Float::class, java.lang.Double::class,  BigDecimal::class)
        .map { listOf(it.javaPrimitiveType, it.javaObjectType) }
        .flatten()
        .filterNotNull()
        .toMutableList()

internal val simpleTypeNames = setOf("byte", "char", "short", "int", "long", "float", "double", "string", "boolean")
