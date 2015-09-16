package uy.kohesive.kovert.core.reflect

import java.lang.reflect.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType


// things to help until KT-8998 or something similar makes KType nicer to use
internal fun KType.isAssignableFrom(other: KType): Boolean {
    if (this == other || this.javaType == other.javaType) return true
    return (this.javaType.erasedType()).isAssignableFrom(other.javaType.erasedType())
}

fun KType.isAssignableFrom(other: Class<*>): Boolean {
    if (this.javaType == other) return true
    return (this.javaType.erasedType()).isAssignableFrom(other)
}

fun KType.isAssignableFrom(other: KClass<*>): Boolean {
    return (this.javaType.erasedType()).isAssignableFrom(other.java)
}

fun Class<*>.isAssignableFrom(other: KType): Boolean {
    if (this == other.javaType) return true
    return this.isAssignableFrom(other.erasedType())
}

fun <T: Any> Class<*>.isAssignableFrom(other: KClass<T>): Boolean {
    if (this == other.java) return true
    return this.isAssignableFrom(other.java)
}

fun <T: Any> KClass<T>.isAssignableFrom(other: Class<*>): Boolean {
    if (this.java == other) return true
    return this.java.isAssignableFrom(other)
}

fun <T: Any, O: Any> KClass<T>.isAssignableFrom(other: KClass<O>): Boolean {
    if (this == other || this.java == other.java) return true
    return this.java.isAssignableFrom(other.java)
}

fun <T: Any> KClass<T>.isAssignableFrom(other: KType): Boolean {
    if (this.java == other.javaType) return true
    return this.java.isAssignableFrom(other.javaType.erasedType())
}

@suppress("UNCHECKED_CAST")
fun Type.erasedType(): Class<Any> {
    return when (this) {
        is Class<*> -> this as Class<Any>
        is ParameterizedType -> this.rawType.erasedType()
        is GenericArrayType -> {
            // getting the array type is a bit trickier
            val elementType = this.genericComponentType.erasedType()
            val testArray = java.lang.reflect.Array.newInstance(elementType, 0)
            testArray.javaClass
        }
        is TypeVariable<*> -> {
            // not sure yet
            throw IllegalStateException("Not sure what to do here yet")
        }
        is WildcardType -> {
            this.upperBounds[0].erasedType()
        }
        else -> throw IllegalStateException("Should not get here.")
    }
}

fun KType.erasedType(): Class<Any> = this.javaType.erasedType()