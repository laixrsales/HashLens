package br.ufv.hashlens.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Relógio de teste que só anda quando o teste manda ([advance]). */
class MutableClock(var instant: Instant) : Clock() {
    fun advance(duration: Duration) {
        instant = instant.plus(duration)
    }

    override fun instant(): Instant = instant

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this
}
