package uy.kohesive.kovert.core

import uy.klutter.reflect.erasedType
import java.math.BigDecimal
import java.time.*
import java.time.temporal.Temporal
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmErasure


fun isSimpleDataType(type: Class<out Any>) = type.isPrimitive || knownSimpleTypes.any { type.isAssignableFrom(it) } || simpleTypeNames.contains(type.name)
// fun <T: Any> isSimpleDataType(type: KClass<T>) = isSimpleDataType(type.java.erasedType())
fun isSimpleDataType(type: KType) = isSimpleDataType(type.jvmErasure.java)

fun isEnum(type: KType) = type.erasedType().isEnum

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") val knownSimpleTypes = listOf<KClass<out Any>>(
        Boolean::class,
        Number::class,
        String::class,
        Date::class,
        java.lang.Byte::class,
        java.lang.Short::class,
        java.lang.Integer::class,
        java.lang.Long::class,
        java.lang.Float::class,
        java.lang.Double::class,
        BigDecimal::class,
        Temporal::class,
        OffsetDateTime::class,
        ZonedDateTime::class,
        LocalDate::class,
        LocalDateTime::class,
        Clock::class,
        Instant::class,
        Period::class,
        Year::class,
        YearMonth::class,
        MonthDay::class,
        ZoneId::class,
        ZoneOffset::class,
        LocalTime::class,
        OffsetTime::class)
        .map {
            listOf(it.javaPrimitiveType, it.javaObjectType)
        }
        .flatten()
        .filterNotNull()
        .toMutableList()

internal val simpleTypeNames = setOf("byte", "char", "short", "int", "long", "float", "double", "string", "boolean")
