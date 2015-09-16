package uy.kohesive.kovert.core

import uy.kohesive.kovert.core.reflect.erasedType
import uy.kohesive.kovert.core.reflect.isAssignableFrom
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType


internal fun isSimpleDataType(type: Class<*>) = knownSimpleTypes.any { type.isAssignableFrom(it) } || simpleTypeNames.contains(type.getName())
internal fun <T: Any> isSimpleDataType(type: KClass<T>) = knownSimpleTypes.any { type.isAssignableFrom(it) } || simpleTypeNames.contains(type.qualifiedName)
internal fun isSimpleDataType(type: KType) = knownSimpleTypes.any { type.isAssignableFrom(it) } || simpleTypeNames.contains(type.javaType.erasedType().name)

@suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal val knownSimpleTypes = linkedListOf(Boolean::class.java, Number::class.java, String::class.java,
        Date::class.java,
        java.lang.Byte::class.java, java.lang.Short::class.java, java.lang.Integer::class.java, java.lang.Long::class.java,
        java.lang.Float::class.java, java.lang.Double::class.java,  BigDecimal::class.java)

internal val simpleTypeNames = setOf("byte", "char", "short", "int", "long", "float", "double", "string", "boolean")
