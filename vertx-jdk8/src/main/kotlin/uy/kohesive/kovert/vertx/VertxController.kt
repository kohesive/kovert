package uy.kohesive.kovert.vertx

import io.vertx.ext.web.Router
import uy.kohesive.kovert.core.PrefixAsVerbWithSuccessStatus
import uy.kohesive.kovert.core.knownSimpleTypes
import uy.kohesive.kovert.vertx.internal.bindControllerController
import java.time.*
import java.time.temporal.Temporal
import kotlin.reflect.KClass

private object KovertConfigUpdateJdk8 {
    init {
        // add our JDK 8 simple types to core
        knownSimpleTypes.addAll(listOf<KClass<out Any>>(Temporal::class,
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
                OffsetTime::class))
    }

    @Suppress("NOTHING_TO_INLINE")
    public inline fun ensure() {
        // TODO: here to be sure we have intiailized anything related before using,
        //       although this function may remain empty it causes initializers on the
        //       object to run.
    }
}

public fun Router.bindController(kotlinClassAsController: Any, atPath: String, verbAliases: List<PrefixAsVerbWithSuccessStatus> = emptyList()) {
    KovertConfigUpdateJdk8.ensure()
    bindControllerController(this, kotlinClassAsController, atPath, verbAliases)
}