package app.gridlink.core.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * VTODO reading: the component the event parser used to drop on the floor.
 *
 * The first fixture is the shape that motivated the work — a weekly all-day reminder on a real
 * CalDAV server, invisible in the app because nothing ever looked for BEGIN:VTODO.
 */
class ICalendarStreamTaskTest {

    private val dublin: ZoneId = ZoneId.of("Europe/Dublin")

    private fun wrap(body: String) = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n$body\r\nEND:VCALENDAR\r\n"

    @Test fun aWeeklyAllDayReminderParses() {
        val tasks = ICalendarStream.parseTasks(
            wrap(
                """
                BEGIN:VTODO
                UID:bins-1
                DTSTAMP:20261101T090000Z
                DTSTART;VALUE=DATE:20261105
                DUE;VALUE=DATE:20261105
                RRULE:FREQ=WEEKLY
                SUMMARY:Put the bins out
                END:VTODO
                """.trimIndent().replace("\n", "\r\n"),
            ),
            dublin,
        )
        assertEquals(1, tasks.size)
        val task = tasks.single()
        assertEquals("bins-1", task.uid)
        assertEquals("Put the bins out", task.summary)
        assertEquals(LocalDateTime.of(2026, 11, 5, 0, 0), task.due)
        assertEquals(LocalDateTime.of(2026, 11, 5, 0, 0), task.start)
        assertTrue(task.allDay)
        assertEquals("FREQ=WEEKLY", task.rrule)
        assertEquals(ParsedCalendarTask.STATUS_NEEDS_ACTION, task.status)
        assertTrue(!task.completed && !task.cancelled)
    }

    @Test fun aTimedTaskKeepsItsFieldsAndAlarm() {
        val tasks = ICalendarStream.parseTasks(
            wrap(
                """
                BEGIN:VTODO
                UID:vet-1
                DTSTART;TZID=Europe/Dublin:20261110T093000
                DUE;TZID=Europe/Dublin:20261110T093000
                STATUS:IN-PROCESS
                PERCENT-COMPLETE:40
                PRIORITY:1
                DESCRIPTION:Ask about the booster
                LOCATION:Vet surgery
                BEGIN:VALARM
                ACTION:DISPLAY
                TRIGGER:-PT30M
                END:VALARM
                END:VTODO
                """.trimIndent().replace("\n", "\r\n"),
            ),
            dublin,
        )
        val task = tasks.single()
        assertEquals(LocalDateTime.of(2026, 11, 10, 9, 30), task.due)
        assertEquals(dublin, task.zone)
        assertTrue(!task.allDay)
        assertEquals(ParsedCalendarTask.STATUS_IN_PROCESS, task.status)
        assertEquals(40, task.percentComplete)
        assertEquals(1, task.priority)
        assertEquals("Ask about the booster", task.description)
        assertEquals("Vet surgery", task.location)
        assertEquals(listOf(30), task.reminders)
    }

    @Test fun aCompletedTaskReadsAsCompleted() {
        val tasks = ICalendarStream.parseTasks(
            wrap(
                """
                BEGIN:VTODO
                UID:done-1
                DUE:20261102T120000Z
                STATUS:COMPLETED
                COMPLETED:20261102T114500Z
                SUMMARY:Renew the dog licence
                END:VTODO
                """.trimIndent().replace("\n", "\r\n"),
            ),
            dublin,
        )
        val task = tasks.single()
        assertTrue(task.completed)
        // COMPLETED is a UTC stamp; in November Dublin is on GMT, so the wall time matches.
        assertEquals(LocalDateTime.of(2026, 11, 2, 11, 45), task.completedAt)
    }

    @Test fun eventsAndTasksInOneFileStayApart() {
        val raw = wrap(
            """
            BEGIN:VEVENT
            UID:event-1
            DTSTART:20261106T100000
            SUMMARY:A meeting
            END:VEVENT
            BEGIN:VTODO
            UID:task-1
            DUE;VALUE=DATE:20261106
            SUMMARY:A chore
            END:VTODO
            """.trimIndent().replace("\n", "\r\n"),
        )
        assertEquals(listOf("event-1"), ICalendarStream.parse(raw, dublin).map { it.uid })
        assertEquals(listOf("task-1"), ICalendarStream.parseTasks(raw, dublin).map { it.uid })
    }

    @Test fun aSomedayTaskIsKeptWithoutADue() {
        val tasks = ICalendarStream.parseTasks(
            wrap(
                """
                BEGIN:VTODO
                UID:someday-1
                SUMMARY:Paint the shed
                END:VTODO
                """.trimIndent().replace("\n", "\r\n"),
            ),
            dublin,
        )
        val task = tasks.single()
        assertNull(task.due)
        assertNull(task.start)
        assertNull(task.anchor)
    }
}
