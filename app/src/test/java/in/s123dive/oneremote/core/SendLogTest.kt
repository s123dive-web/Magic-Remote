package `in`.s123dive.oneremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendLogTest {
    private fun entry(i: Int) = LogEntry(i.toLong(), "Hall", "TV", "VOL_UP", "IR", "NEC 0x04/0x02", "OK", 12)

    @Test
    fun keepsOnlyTheLastCapacityEntriesNewestFirst() {
        val log = SendLog(capacity = 5)
        repeat(8) { log.add(entry(it)) }
        val entries = log.entries.value
        assertEquals(5, entries.size)
        assertEquals(listOf(7L, 6L, 5L, 4L, 3L), entries.map { it.time })
    }

    @Test
    fun exportIsOldestFirstOneLinePerEntry() {
        val log = SendLog(capacity = 3)
        repeat(3) { log.add(entry(it)) }
        val lines = log.export { "t$it" }.lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("t0\tHall\tTV\tVOL_UP\tIR\tNEC 0x04/0x02\tOK\t12ms"))
        assertTrue(lines[2].startsWith("t2\t"))
    }

    @Test
    fun clearEmpties() {
        val log = SendLog(capacity = 3)
        log.add(entry(1))
        log.clear()
        assertEquals(0, log.entries.value.size)
        assertEquals("", log.export())
    }

    @Test
    fun defaultCapacityIs500() {
        val log = SendLog()
        repeat(600) { log.add(entry(it)) }
        assertEquals(500, log.entries.value.size)
    }
}
